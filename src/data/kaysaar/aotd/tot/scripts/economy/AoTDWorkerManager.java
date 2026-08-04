package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.apache.log4j.Logger;

/** Cooperative save barrier plus restartable, epoch-aware worker lifecycle. */
public final class AoTDWorkerManager {
    private AoTDWorkerManager() {}

    private static final Logger log = Global.getLogger(AoTDWorkerManager.class);
    private static final int THREAD_COUNT =
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
    private static final Object LOCK = new Object();
    private static final Set<Future<?>> TASKS = new HashSet<>();
    private static final ThreadLocal<WorkerContext> CURRENT_WORKER = new ThreadLocal<>();

    private static ExecutorService executor;
    private static long executorGeneration;
    private static boolean saveBarrierActive;
    private static int runningWorkers;

    private static long executorRestarts;
    private static long cancelledFutures;
    private static long staleWorkersRejected;
    private static long staleCheckpoints;
    private static long submittedTasks;

    public static Future<?> submit(String name, Runnable task) {
        return submit(name, AoTDRuntimeEpoch.captureBatch("worker:" + name), task);
    }

    public static Future<?> submit(String name, AoTDRuntimeEpoch.Stamp stamp, Runnable task) {
        return submitInternal(name, stamp, task, true);
    }

    private static Future<?> submitInternal(
            String name, AoTDRuntimeEpoch.Stamp stamp, Runnable task, boolean instrumentWorker) {
        if (task == null) throw new IllegalArgumentException("task");
        if (stamp == null) throw new IllegalArgumentException("stamp");
        if (instrumentWorker) {
            AoTDEconomySemanticBaseline.operation("worker-manager.submit", 1L);
        }

        synchronized (LOCK) {
            cleanupFinishedTasksLocked();
            if (!AoTDRuntimeEpoch.isCurrent(stamp)) {
                staleWorkersRejected++;
                return cancelledFuture();
            }
            ExecutorService activeExecutor = ensureExecutorLocked();
            long generation = executorGeneration;
            FutureTask<Void> future =
                    new FutureTask<>(
                            () -> {
                                WorkerContext context = new WorkerContext(generation, stamp);
                                CURRENT_WORKER.set(context);
                                try {
                                    enterWorker(context);
                                    if (instrumentWorker) {
                                        try (AoTDEconomySemanticBaseline.Scope scope =
                                                AoTDEconomySemanticBaseline.begin(
                                                        "worker-manager.execute", null, name)) {
                                            task.run();
                                        }
                                    } else {
                                        task.run();
                                    }
                                } catch (StaleEpochCancellation stale) {
                                    // Expected cooperative cancellation at a campaign/economy
                                    // boundary.
                                } catch (RuntimeException failure) {
                                    if (instrumentWorker) {
                                        AoTDEconomySemanticBaseline.operation(
                                                "worker-manager.crash", 1L);
                                        log.error("AoTD worker crashed: " + name, failure);
                                    }
                                    throw failure;
                                } finally {
                                    exitWorker(context);
                                    CURRENT_WORKER.remove();
                                }
                                return null;
                            });
            TASKS.add(future);
            submittedTasks++;
            activeExecutor.execute(future);
            return future;
        }
    }

    public static List<Future<?>> submitDynamicBatch(
            String name, int itemCount, int chunkSize, IntConsumer itemWork) {
        return submitDynamicBatch(
                name,
                AoTDRuntimeEpoch.captureBatch("dynamic:" + name),
                itemCount,
                chunkSize,
                itemWork);
    }

    public static List<Future<?>> submitDynamicBatch(
            String name,
            AoTDRuntimeEpoch.Stamp stamp,
            int itemCount,
            int chunkSize,
            IntConsumer itemWork) {
        if (itemCount <= 0 || itemWork == null) return Collections.emptyList();
        final int safeChunk = Math.max(1, chunkSize);
        final int jobs = Math.max(1, Math.min(THREAD_COUNT, itemCount));
        final AtomicInteger cursor = new AtomicInteger();
        final ArrayList<Future<?>> futures = new ArrayList<>(jobs);
        AoTDEconomySemanticBaseline.operation("worker-manager.dynamic-batch", 1L);
        AoTDEconomySemanticBaseline.operation("worker-manager.dynamic-items", itemCount);
        AoTDEconomySemanticBaseline.operation("worker-manager.dynamic-jobs", jobs);

        for (int worker = 0; worker < jobs; worker++) {
            final int workerIndex = worker;
            futures.add(
                    submitInternal(
                            name + " [" + workerIndex + "]",
                            stamp,
                            () -> {
                                while (true) {
                                    checkpoint();
                                    int start = cursor.getAndAdd(safeChunk);
                                    if (start >= itemCount) return;
                                    int end = Math.min(itemCount, start + safeChunk);
                                    for (int index = start; index < end; index++) {
                                        checkpoint();
                                        itemWork.accept(index);
                                    }
                                }
                            },
                            false));
        }
        return futures;
    }

    public static boolean areDone(List<Future<?>> futures) {
        if (futures == null || futures.isEmpty()) return true;
        for (Future<?> future : futures) {
            if (future != null && !future.isDone()) return false;
        }
        return true;
    }

    public static AoTDRuntimeEpoch.EpochSnapshot beginCampaign(Object economy, String reason) {
        AoTDTradeManager.invalidateInstalledRuntimeEpochState();
        AoTDRuntimeEpoch.EpochSnapshot epoch = AoTDRuntimeEpoch.advanceCampaign(economy, reason);
        restartExecutor("campaign:" + reason, true);
        MarketRegistry.clear();
        return epoch;
    }

    public static AoTDRuntimeEpoch.EpochSnapshot bindLoadedEconomy(Object economy, String reason) {
        return AoTDRuntimeEpoch.bindLoadedEconomy(economy, reason);
    }

    public static AoTDRuntimeEpoch.EpochSnapshot replaceEconomy(Object economy, String reason) {
        AoTDTradeManager.invalidateInstalledRuntimeEpochState();
        AoTDRuntimeEpoch.EpochSnapshot epoch = AoTDRuntimeEpoch.advanceEconomy(economy, reason);
        restartExecutor("economy:" + reason, true);
        MarketRegistry.clear();
        return epoch;
    }

    public static void checkpoint() {
        WorkerContext context = CURRENT_WORKER.get();
        if (context == null) return;
        synchronized (LOCK) {
            requireCurrentLocked(context);
            if (!saveBarrierActive) return;

            leaveRunningLocked(context);
            LOCK.notifyAll();
            try {
                while (saveBarrierActive) {
                    LOCK.wait();
                    requireCurrentLocked(context);
                }
                requireCurrentLocked(context);
                enterRunningLocked(context);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new StaleEpochCancellation("worker interrupted");
            }
        }
    }

    public static void beginSaveAndWait() {
        boolean interrupted = false;
        synchronized (LOCK) {
            saveBarrierActive = true;
            LOCK.notifyAll();
            while (runningWorkers > 0) {
                try {
                    LOCK.wait(250L);
                } catch (InterruptedException ex) {
                    // Saving must not continue while workers still own live campaign data.
                    // Preserve the interruption only after the cooperative barrier is safe.
                    interrupted = true;
                }
            }
            cleanupFinishedTasksLocked();
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    public static void endSave() {
        synchronized (LOCK) {
            saveBarrierActive = false;
            LOCK.notifyAll();
            cleanupFinishedTasksLocked();
        }
    }

    public static boolean isSaveBarrierActive() {
        synchronized (LOCK) {
            return saveBarrierActive;
        }
    }

    public static int getRunningWorkers() {
        synchronized (LOCK) {
            return runningWorkers;
        }
    }

    public static AoTDRuntimeEpoch.EpochSnapshot resetRuntime(String reason) {
        AoTDTradeManager.invalidateInstalledRuntimeEpochState();
        AoTDRuntimeEpoch.EpochSnapshot epoch =
                AoTDRuntimeEpoch.advanceEconomy(null, reason == null ? "runtime-reset" : reason);
        restartExecutor("reset:" + reason, true);
        MarketRegistry.clear();
        return epoch;
    }

    public static void onModEnabledState(boolean enabled) {
        if (!enabled) shutdownNow();
    }

    /** Invalidates all batches and leaves the executor lazily recreatable. */
    public static void shutdownNow() {
        AoTDTradeManager.invalidateInstalledRuntimeEpochState();
        AoTDRuntimeEpoch.advanceShutdown("worker-manager.shutdown");
        restartExecutor("shutdown", false);
        MarketRegistry.clear();
    }

    public static String statusSummary() {
        synchronized (LOCK) {
            cleanupFinishedTasksLocked();
            return "executorGeneration="
                    + executorGeneration
                    + ", executorActive="
                    + (executor != null && !executor.isShutdown())
                    + ", runningWorkers="
                    + runningWorkers
                    + ", trackedTasks="
                    + TASKS.size()
                    + ", saveBarrier="
                    + saveBarrierActive
                    + ", restarts="
                    + executorRestarts
                    + ", cancelledFutures="
                    + cancelledFutures
                    + ", staleWorkersRejected="
                    + staleWorkersRejected
                    + ", staleCheckpoints="
                    + staleCheckpoints
                    + ", submittedTasks="
                    + submittedTasks
                    + ", "
                    + AoTDRuntimeEpoch.statusSummary();
        }
    }

    private static void restartExecutor(String reason, boolean allowLazyRestart) {
        ExecutorService previous;
        synchronized (LOCK) {
            executorGeneration = nextPositive(executorGeneration);
            saveBarrierActive = false;
            runningWorkers = 0;
            for (Future<?> future : TASKS) {
                if (future != null && !future.isDone() && future.cancel(true)) {
                    cancelledFutures++;
                }
            }
            TASKS.clear();
            previous = executor;
            executor = null;
            executorRestarts++;
            LOCK.notifyAll();
        }
        if (previous != null) previous.shutdownNow();
        if (!allowLazyRestart) {
            log.info("AoTD worker executor shut down: " + reason);
        }
    }

    private static ExecutorService ensureExecutorLocked() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            long generation = executorGeneration;
            executor =
                    Executors.newFixedThreadPool(
                            THREAD_COUNT,
                            runnable -> {
                                Thread thread = new Thread(runnable, "AoTD Worker g" + generation);
                                thread.setDaemon(true);
                                return thread;
                            });
        }
        return executor;
    }

    private static void enterWorker(WorkerContext context) {
        synchronized (LOCK) {
            requireCurrentLocked(context);
            while (saveBarrierActive) {
                try {
                    LOCK.wait(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new StaleEpochCancellation("worker wait interrupted");
                }
                requireCurrentLocked(context);
            }
            enterRunningLocked(context);
        }
    }

    private static void exitWorker(WorkerContext context) {
        synchronized (LOCK) {
            leaveRunningLocked(context);
            LOCK.notifyAll();
        }
    }

    private static void enterRunningLocked(WorkerContext context) {
        if (context.running) return;
        requireCurrentLocked(context);
        context.running = true;
        runningWorkers++;
    }

    private static void leaveRunningLocked(WorkerContext context) {
        if (!context.running) return;
        context.running = false;
        if (context.executorGeneration == executorGeneration && runningWorkers > 0) {
            runningWorkers--;
        }
    }

    private static void requireCurrentLocked(WorkerContext context) {
        if (context.executorGeneration != executorGeneration
                || !AoTDRuntimeEpoch.isCurrent(context.stamp)) {
            staleCheckpoints++;
            throw new StaleEpochCancellation("stale AoTD worker " + context.stamp);
        }
    }

    private static Future<?> cancelledFuture() {
        FutureTask<Void> future = new FutureTask<>(() -> null);
        future.cancel(false);
        return future;
    }

    private static void cleanupFinishedTasksLocked() {
        TASKS.removeIf(Future::isDone);
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    private static final class WorkerContext {
        final long executorGeneration;
        final AoTDRuntimeEpoch.Stamp stamp;
        boolean running;

        WorkerContext(long executorGeneration, AoTDRuntimeEpoch.Stamp stamp) {
            this.executorGeneration = executorGeneration;
            this.stamp = stamp;
        }
    }

    private static final class StaleEpochCancellation extends CancellationException {
        StaleEpochCancellation(String message) {
            super(message);
        }
    }
}
