package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Cooperative worker save barrier.
 *
 * Use AoTDWorkerManager.submit() to start AoTD workers.
 * Workers should call checkpoint() inside long loops.
 *
 * beforeGameSave() should call beginSaveAndWait().
 * afterGameSave() should call endSave().
 */
public final class AoTDWorkerManager {
    private AoTDWorkerManager() {}

    private static final Logger log = Global.getLogger(AoTDWorkerManager.class);

    private static final int THREAD_COUNT = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));

    private static final Object LOCK = new Object();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT, runnable -> {
        final Thread thread = new Thread(runnable, "AoTD Worker");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<Future<?>> TASKS = Collections.synchronizedSet(new HashSet<>());

    private static boolean saveBarrierActive = false;
    private static int runningWorkers = 0;

    public static Future<?> submit(String name, Runnable task) {
        return submitInternal(name, task, true);
    }

    private static Future<?> submitInternal(
            String name, Runnable task, boolean instrumentWorker) {
        if (instrumentWorker) {
            AoTDEconomySemanticBaseline.operation("worker-manager.submit", 1L);
        }
        final Future<?> future = EXECUTOR.submit(() -> {
            enterWorker();
            try {
                if (instrumentWorker) {
                    try (AoTDEconomySemanticBaseline.Scope scope =
                                 AoTDEconomySemanticBaseline.begin(
                                         "worker-manager.execute", null, name)) {
                        task.run();
                    }
                } else {
                    // Pure price workers deliberately avoid semantic-baseline and
                    // all Starsector-facing helpers inside the worker thread.
                    task.run();
                }
            } catch (RuntimeException failure) {
                if (instrumentWorker) {
                    AoTDEconomySemanticBaseline.operation("worker-manager.crash", 1L);
                    log.error("AoTD worker crashed: " + name, failure);
                }
                // Preserve exceptional Future completion for both legacy auxiliary
                // work and pure DTO batches. Callers decide whether same-algorithm
                // sequential retry is valid; no worker failure is silently swallowed.
                throw failure;
            } finally {
                exitWorker();
            }
        });

        TASKS.add(future);
        cleanupFinishedTasks();
        return future;
    }

    /**
     * Runs a logical item array using at most the resident worker count. Work is
     * claimed through one shared cursor in small chunks, so uneven markets are
     * dynamically balanced without allocating one Runnable/Future per market.
     */
    public static List<Future<?>> submitDynamicBatch(
            String name, int itemCount, int chunkSize, IntConsumer itemWork) {
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
            futures.add(submitInternal(name + " [" + workerIndex + "]", () -> {
                while (true) {
                    checkpoint();
                    int start = cursor.getAndAdd(safeChunk);
                    if (start >= itemCount) return;
                    int end = Math.min(itemCount, start + safeChunk);
                    for (int index = start; index < end; index++) {
                        itemWork.accept(index);
                    }
                }
            }, false));
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

    private static void enterWorker() {
        synchronized (LOCK) {
            while (saveBarrierActive) {
                waitQuietly();
            }
            runningWorkers++;
        }
    }

    private static void exitWorker() {
        synchronized (LOCK) {
            runningWorkers--;
            if (runningWorkers < 0) {
                runningWorkers = 0;
            }

            LOCK.notifyAll();
        }
    }

    public static void checkpoint() {
        synchronized (LOCK) {
            if (!saveBarrierActive) {
                return;
            }

            runningWorkers--;
            if (runningWorkers < 0) {
                runningWorkers = 0;
            }

            LOCK.notifyAll();

            try {
                while (saveBarrierActive) {
                    LOCK.wait();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                runningWorkers++;
            }
        }
    }

    public static void beginSaveAndWait() {
        synchronized (LOCK) {
            saveBarrierActive = true;
            LOCK.notifyAll();

            while (runningWorkers > 0) {
                waitQuietly();
            }
        }

        cleanupFinishedTasks();
    }

    public static void endSave() {
        synchronized (LOCK) {
            saveBarrierActive = false;
            LOCK.notifyAll();
        }

        cleanupFinishedTasks();
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

    public static void shutdownNow() {
        synchronized (LOCK) {
            saveBarrierActive = false;
            LOCK.notifyAll();
        }

        EXECUTOR.shutdownNow();
        TASKS.clear();
    }

    private static void cleanupFinishedTasks() {
        synchronized (TASKS) {
            TASKS.removeIf(Future::isDone);
        }
    }

    private static void waitQuietly() {
        try {
            LOCK.wait(250L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}