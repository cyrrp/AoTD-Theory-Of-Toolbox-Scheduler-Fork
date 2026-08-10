package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import com.fs.starfarer.campaign.econ.reach.ImmigrationTask;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask2;
import com.fs.starfarer.campaign.econ.reach.ReachEconomyStepper;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import sun.misc.Unsafe;

/** Standalone regression harness for save/load economy-task checkpointing. */
public final class AoTDRuntimeTaskRestartTest {
    private AoTDRuntimeTaskRestartTest() {}

    public static void main(String[] args) throws Exception {
        installSettings();
        installSector();
        verifyEveryKnownHeadStage();
        verifyMainPrecommitFullReplay();
        verifyMainPartialCommitProgress();
        verifyMainListenersOnlyProgress();
        verifyLegacyMainProgressDrops();
        verifyPartialUpdateProgress();
        verifyPartialImmigrationSerializationAndIdentityRebind();
        verifySameProcessSaveResumeAndNesting();
        verifySameProcessPartialMainReleasesTicket();
        verifySameProcessPostAndFinishCleanup();
        verifyWaitingStrayGraphIsDiscarded();
        verifySaveBeforeFirstRestartFramePreservesSuffix();
        verifyWaitingCadenceIsPreserved();
        verifyUnknownHeadUsesKnownSuffix();
        System.out.println("AoTD runtime-task restart tests passed.");
    }

    private static void verifyMainPrecommitFullReplay() throws Exception {
        AoTdMainWorkTask2 main = mainTask("FULL", false);
        setField(main, "aotdStarted", true);
        setField(main, "mtCommitIndex", 0);
        setField(main, "mtCommitDone", false);
        setField(main, "mtListenersNotified", false);
        setField(main, "mtCommitPlans", new ArrayList<>());

        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "tasks", new ArrayList<>(List.of(main)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check("FULL".equals(report.mainRestartMode), "precommit MAIN did not replay fully");
        check(
                report.plannedStages.startsWith("MAIN_WORK>UPDATE_MARKETS"),
                "precommit MAIN was dropped: " + report.plannedStages);
    }

    private static void verifyMainPartialCommitProgress() throws Exception {
        Market attempted = concreteMarket("main-attempted");
        Market remainingA = concreteMarket("main-remaining-a");
        Market remainingB = concreteMarket("main-remaining-b");
        AoTdMainWorkTask2 main = mainTask("FULL", false);
        setField(main, "aotdStarted", true);
        setField(main, "mtCommitIndex", 1);
        setField(main, "mtCommitDone", false);
        setField(main, "mtListenersNotified", false);
        setField(
                main,
                "mtCommitPlans",
                new ArrayList<>(
                        List.of(
                                marketCommitPlan(attempted),
                                marketCommitPlan(remainingA),
                                marketCommitPlan(remainingB))));

        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "tasks", new ArrayList<>(List.of(main)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check(
                "PRICE_REMAINING".equals(report.mainRestartMode),
                "partial MAIN has wrong mode: " + report.mainRestartMode);
        check(report.remainingMarketCount == 2, "partial MAIN lost remaining plan count");
        check(report.remainingMarketProgressKnown, "partial MAIN progress became unknown");
        check(
                List.of("main-remaining-a", "main-remaining-b")
                        .equals(getField(stepper, "runtimeRestartMarketIds")),
                "partial MAIN included attempted market or lost order");
        check(
                report.plannedStages.startsWith("MAIN_WORK>UPDATE_MARKETS"),
                "partial MAIN replacement was not planned");

        MainWorkTask.EconWorkParams params = new MainWorkTask.EconWorkParams();
        AoTdMainWorkTask2 replacementScope =
                AoTdMainWorkTask2.forRuntimePriceRemaining(
                        List.of(attempted, remainingA, remainingB),
                        List.of(remainingA, remainingB),
                        null,
                        params);
        check(
                List.of(remainingA, remainingB).equals(getField(replacementScope, "aotdMarkets")),
                "PRICE_REMAINING did not restrict price capture");
        check(
                List.of(attempted, remainingA, remainingB)
                        .equals(getField(replacementScope, "runtimeGlobalDataMarkets")),
                "PRICE_REMAINING lost full transient CommodityMarketData scope");

        // A second save before lazy task creation must retain the accepted mode and exact IDs.
        stepper.suspendRuntimeTasksForSave();
        XStream checkpointXStream = new XStream(new StaxDriver());
        XStream.setupDefaultSecurity(checkpointXStream);
        checkpointXStream.allowTypesByWildcard(
                new String[] {"data.kaysaar.aotd.tot.scripts.economy.**", "java.util.**"});
        String checkpointXml = checkpointXStream.toXML(stepper);
        check(checkpointXml.contains("PRICE_REMAINING"), "serialized MAIN mode missing");
        check(checkpointXml.contains("main-remaining-a"), "serialized MAIN IDs missing");
        check(
                !checkpointXml.contains("MarketPriceCommitPlan")
                        && !checkpointXml.contains("runtimeGlobalDataMarkets"),
                "serialized MAIN checkpoint retained a live/transient task graph");
        stepper.resumeRuntimeTasksAfterSave();
        AoTDEconomyReachStepper.RuntimeTaskRestartReport resaved =
                stepper.restartRuntimeTasksAfterLoad(null);
        check(
                "PRICE_REMAINING".equals(resaved.mainRestartMode)
                        && resaved.remainingMarketCount == 2,
                "save-before-first-restart-frame lost MAIN suffix");

        // A fresh restricted replacement saved after one more attempt narrows, never expands, its
        // scope. mtCommitIndex advances before apply, so even failed/stale attempts stay excluded.
        AoTdMainWorkTask2 restricted = mainTask("PRICE_REMAINING", false);
        setField(restricted, "aotdStarted", true);
        setField(restricted, "aotdMarkets", new ArrayList<>(List.of(remainingA, remainingB)));
        setField(restricted, "mtCaptureDone", true);
        setField(restricted, "mtCommitIndex", 1);
        setField(restricted, "mtCommitDone", false);
        setField(restricted, "mtListenersNotified", false);
        setField(
                restricted,
                "mtCommitPlans",
                new ArrayList<>(
                        List.of(marketCommitPlan(remainingA), marketCommitPlan(remainingB))));
        AoTDEconomyReachStepper narrowed = doingStepper();
        setField(narrowed, "tasks", new ArrayList<>(List.of(restricted)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport narrowedReport =
                narrowed.restartRuntimeTasksAfterLoad(null);
        check(
                "PRICE_REMAINING".equals(narrowedReport.mainRestartMode)
                        && narrowedReport.remainingMarketCount == 1,
                "repeated restricted MAIN did not narrow its suffix");
        check(
                List.of("main-remaining-b").equals(getField(narrowed, "runtimeRestartMarketIds")),
                "repeated restricted MAIN replayed an attempted market");
    }

    private static void verifyMainListenersOnlyProgress() throws Exception {
        AoTdMainWorkTask2 main = mainTask("FULL", false);
        setField(main, "aotdStarted", true);
        setField(main, "mtCommitIndex", 1);
        setField(main, "mtCommitDone", false);
        setField(main, "mtListenersNotified", false);
        setField(
                main,
                "mtCommitPlans",
                new ArrayList<>(List.of(marketCommitPlan(concreteMarket("main-last")))));
        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "tasks", new ArrayList<>(List.of(main)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check(
                "LISTENERS_ONLY".equals(report.mainRestartMode),
                "last attempted plan was treated as remaining work");
        check(report.remainingMarketCount == 0, "listeners-only retained market work");
        check(report.plannedStages.startsWith("MAIN_WORK>"), "listeners-only MAIN was dropped");

        AoTdMainWorkTask2 listenerScope =
                AoTdMainWorkTask2.forRuntimeListenersOnly(
                        List.of(concreteMarket("listener-global")),
                        null,
                        new MainWorkTask.EconWorkParams());
        check(
                ((List<?>) getField(listenerScope, "aotdMarkets")).isEmpty(),
                "listeners-only task retained price markets");
        check(
                ((List<?>) getField(listenerScope, "runtimeGlobalDataMarkets")).size() == 1,
                "listeners-only task would skip transient CommodityMarketData rebuild");

        AoTdMainWorkTask2 replacement = mainTask("LISTENERS_ONLY", false);
        AoTDEconomyReachStepper repeated = doingStepper();
        setField(repeated, "tasks", new ArrayList<>(List.of(replacement)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport repeatedReport =
                repeated.restartRuntimeTasksAfterLoad(null);
        check(
                "LISTENERS_ONLY".equals(repeatedReport.mainRestartMode),
                "re-saved listeners-only task lost its subphase");
    }

    private static void verifyLegacyMainProgressDrops() throws Exception {
        AoTdMainWorkTask2 legacy = mainTask("FULL", true);
        setField(legacy, "aotdStarted", true);
        setField(legacy, "mtCommitIndex", 1);
        setField(legacy, "mtCommitPlans", null);
        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "tasks", new ArrayList<>(List.of(legacy)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check("DROP".equals(report.mainRestartMode), "legacy MAIN guessed transient progress");
        check(!report.remainingMarketProgressKnown, "legacy MAIN claimed exact progress");
        check(
                "UPDATE_MARKETS>IMMIGRATION>POST_IMMIGRATION>FINISH".equals(report.plannedStages),
                "legacy MAIN fail-safe suffix changed");
    }

    private static void verifyPartialUpdateProgress() throws Exception {
        AoTDUpdateMarketAgainTask update = allocate(AoTDUpdateMarketAgainTask.class);
        setField(
                update,
                "markets",
                new ArrayList<>(
                        List.of(market("updated"), market("update-a"), market("update-b"))));
        setField(update, "marketIndex", 1);

        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "tasks", new ArrayList<>(List.of(update)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check("UPDATE_MARKETS".equals(report.restartPhase), "wrong partial Update phase");
        check(report.remainingMarketCount == 2, "partial Update lost its exact progress");
        check(report.remainingMarketProgressKnown, "partial Update progress became unknown");
        check(
                "UPDATE_MARKETS>IMMIGRATION>POST_IMMIGRATION>FINISH".equals(report.plannedStages),
                "partial Update planned the wrong suffix");
    }

    private static void verifyEveryKnownHeadStage() throws Exception {
        MainWorkTask.EconWorkParams params = new MainWorkTask.EconWorkParams();
        assertRestart(
                new MainWorkTask2(List.of(), null, params),
                "MAIN_WORK",
                "UPDATE_MARKETS>IMMIGRATION>POST_IMMIGRATION>FINISH");

        AoTDUpdateMarketAgainTask update = allocate(AoTDUpdateMarketAgainTask.class);
        setField(update, "markets", new ArrayList<MarketAPI>());
        assertRestart(
                update, "UPDATE_MARKETS", "UPDATE_MARKETS>IMMIGRATION>POST_IMMIGRATION>FINISH");

        assertRestart(
                new ImmigrationTask(List.of(), null, false),
                "IMMIGRATION",
                "IMMIGRATION>POST_IMMIGRATION>FINISH");
        assertRestart(
                new AoTDPostImmigrationTradeSnapshotTask(List.of(), "test"),
                "POST_IMMIGRATION",
                "POST_IMMIGRATION>FINISH");
        assertRestart(allocate(AoTDFinishEconomyUpdateTask.class), "FINISH", "FINISH");
    }

    private static void verifyPartialImmigrationSerializationAndIdentityRebind() throws Exception {
        MarketAPI processed = market("processed");
        MarketAPI remainingA = market("remaining-a");
        MarketAPI remainingB = market("remaining-b");
        ImmigrationTask immigration =
                new ImmigrationTask(List.of(processed, remainingA, remainingB), null, false);
        @SuppressWarnings("unchecked")
        List<MarketAPI> remaining = (List<MarketAPI>) getField(immigration, "markets");
        remaining.remove(0); // Mirrors one completed ImmigrationTask.doNextBatch().

        AoTDEconomyReachStepper original = doingStepper();
        ArrayList<MultiFrameTask> originalTasks = new ArrayList<>(List.of(immigration));
        setField(original, "tasks", originalTasks);
        original.suspendRuntimeTasksForSave();
        check(getField(original, "tasks") == null, "save image must have no runtime task list");

        XStream xstream = new XStream(new StaxDriver());
        XStream.setupDefaultSecurity(xstream);
        xstream.allowTypesByWildcard(
                new String[] {
                    "data.kaysaar.aotd.tot.scripts.economy.**",
                    "com.fs.starfarer.campaign.econ.reach.**",
                    "java.util.**"
                });
        String xml = xstream.toXML(original);
        check(!xml.contains("<tasks>"), "serialized XML retained ReachEconomyStepper.tasks");
        check(!xml.contains("startedNanos"), "serialized XML retained nano-time state");
        check(!xml.contains("<prepared"), "serialized XML retained a prepared snapshot graph");

        AoTDEconomyReachStepper.RuntimeTaskSaveReport sameProcess =
                original.resumeRuntimeTasksAfterSave();
        check(
                "resume-semantic-restart".equals(sameProcess.action),
                "same-process save restored stale Immigration inputs");
        check(getField(original, "tasks") == null, "same-process stale task graph survived");

        AoTDEconomyReachStepper loaded = (AoTDEconomyReachStepper) xstream.fromXML(xml);
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                loaded.restartRuntimeTasksAfterLoad(null);
        check("IMMIGRATION".equals(report.restartPhase), "wrong restored Immigration phase");
        check(report.remainingMarketCount == 2, "wrong remaining Immigration market count");
        check(report.remainingMarketProgressKnown, "remaining Immigration progress became unknown");
        check(report.loadGuardWasActive, "readResolve did not guard the pre-onGameLoad window");
        check(
                "IMMIGRATION>POST_IMMIGRATION>FINISH".equals(report.plannedStages),
                "partial Immigration planned the wrong suffix: " + report.plannedStages);

        MarketAPI loadedProcessed = market("processed");
        MarketAPI loadedA = market("remaining-a");
        MarketAPI loadedB = market("remaining-b");
        @SuppressWarnings("unchecked")
        List<MarketAPI> rebound =
                (List<MarketAPI>)
                        invokePrivate(
                                loaded,
                                "resolveRestartMarkets",
                                getField(loaded, "runtimeRestartPhase"),
                                List.of(loadedProcessed, loadedB, loadedA));
        check(rebound.size() == 2, "identity rebind did not retain two remaining markets");
        check(rebound.get(0) == loadedA, "first market was not rebound by stable ID/order");
        check(rebound.get(1) == loadedB, "second market was not rebound by stable ID/order");
        check(!rebound.contains(loadedProcessed), "already-processed market would advance twice");
    }

    private static void verifySameProcessSaveResumeAndNesting() throws Exception {
        AoTDEconomyReachStepper stepper = doingStepper();
        ArrayList<MultiFrameTask> exact =
                new ArrayList<>(List.of(new ImmigrationTask(List.of(), null, false)));
        setField(stepper, "tasks", exact);

        stepper.suspendRuntimeTasksForSave();
        stepper.suspendRuntimeTasksForSave();
        check(getField(stepper, "tasks") == null, "nested suspend exposed live tasks");
        AoTDEconomyReachStepper.RuntimeTaskSaveReport inner = stepper.resumeRuntimeTasksAfterSave();
        check("nested".equals(inner.restartPhase), "inner resume was not nesting-safe");
        check(getField(stepper, "tasks") == null, "inner resume restored tasks too early");
        AoTDEconomyReachStepper.RuntimeTaskSaveReport outer = stepper.resumeRuntimeTasksAfterSave();
        check(
                "resume-semantic-restart".equals(outer.action) && !outer.graphRestored,
                "outer resume did not request a fresh semantic suffix");
        check(getField(stepper, "tasks") == null, "outer resume restored stale task objects");
        check(
                "IMMIGRATION".equals(outer.restartPhase),
                "outer resume lost its Immigration checkpoint");

        // The failure callback uses the same finally-path API and must be independently idempotent.
        stepper.suspendRuntimeTasksForSave();
        AoTDEconomyReachStepper.RuntimeTaskSaveReport failureResume =
                stepper.resumeRuntimeTasksAfterSave();
        AoTDEconomyReachStepper.RuntimeTaskSaveReport duplicate =
                stepper.resumeRuntimeTasksAfterSave();
        check(
                "resume-semantic-restart".equals(failureResume.action),
                "failure resume did not preserve semantic restart");
        check("resume".equals(duplicate.action), "duplicate failure cleanup was not idempotent");
        check(getField(stepper, "tasks") == null, "duplicate failure cleanup restored stale tasks");
    }

    private static void verifySameProcessPartialMainReleasesTicket() throws Exception {
        Market attempted = concreteMarket("save-main-attempted");
        Market remaining = concreteMarket("save-main-remaining");
        MarketRegistry.clear();
        MarketRegistry.replaceAllMarkets(Map.of(remaining.getId(), remaining));
        MarketRegistry.WorkTicket ticket =
                MarketRegistry.claimMarketForPrice(
                        remaining, AoTDRuntimeEpoch.captureBatch("save-main-ticket-test"));
        check(ticket != null, "fixture could not claim a remaining MAIN price ticket");
        check(MarketRegistry.markWorkRunning(ticket), "fixture ticket did not start");
        check(MarketRegistry.markResultReady(ticket), "fixture ticket did not become ready");

        AoTdMainWorkTask2 main = mainTask("FULL", false);
        setField(main, "aotdStarted", true);
        setField(main, "mtCaptureDone", true);
        setField(main, "mtCommitIndex", 1);
        setField(main, "mtCommitDone", false);
        setField(main, "mtListenersNotified", false);
        setField(
                main,
                "mtCommitPlans",
                new ArrayList<>(
                        List.of(marketCommitPlan(attempted), marketCommitPlan(remaining, ticket))));

        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "baselineRevision", 77L);
        setField(stepper, "tasks", new ArrayList<>(List.of(main)));
        stepper.suspendRuntimeTasksForSave();
        check(
                List.of("save-main-remaining").equals(getField(stepper, "runtimeRestartMarketIds")),
                "same-process MAIN checkpoint did not capture the exact unattempted suffix");
        check(
                "PRICE_REMAINING"
                        .equals(String.valueOf(getField(stepper, "runtimeRestartMainMode"))),
                "same-process MAIN checkpoint did not retain its exact subphase");

        // Mirrors the restore coordinator advancing the market revision before plugin resume.
        MarketRegistry.markDirty(
                remaining, MarketRegistry.DIRTY_PRICE, MarketRegistry.PRIORITY_NORMAL);
        AoTDEconomyReachStepper.RuntimeTaskSaveReport report =
                stepper.resumeRuntimeTasksAfterSave();
        check(
                "resume-semantic-restart".equals(report.action)
                        && "MAIN_WORK".equals(report.restartPhase),
                "partial MAIN did not enter semantic same-process restart");
        check(getField(stepper, "tasks") == null, "old MAIN graph was restored");
        check(((Long) getField(stepper, "baselineRevision")) == 0L, "old baseline stayed open");
        check(((List<?>) getField(main, "mtCommitPlans")).isEmpty(), "old MAIN plans retained");
        check(!stateFor("save-main-remaining").busy, "old MAIN ticket stayed busy");
        check(
                MarketRegistry.needsPriceRefresh(remaining),
                "abandoned remaining MAIN ticket did not restore price work");
        MarketRegistry.WorkTicket replacement =
                MarketRegistry.claimMarketForPrice(
                        remaining, AoTDRuntimeEpoch.captureBatch("save-main-replacement-test"));
        check(replacement != null, "fresh MAIN suffix could not reclaim the remaining market");
        MarketRegistry.abandon(replacement, true);
        MarketRegistry.clear();
    }

    private static void verifySameProcessPostAndFinishCleanup() throws Exception {
        AoTDTradeManager manager = AoTDTradeManager.getInstance();
        manager.invalidateRuntimeEpochState();
        AoTDGlobalEconomyCoordinator.Boundary boundary =
                AoTDGlobalEconomyCoordinator.beginCommittedCut(
                        AoTDGlobalEconomyCoordinator.BOUNDARY_INTERNAL_TRADE, false);
        check(manager.isSettlementOpen(), "fixture did not open a global trade cut");

        FutureTask<Void> future = new FutureTask<>(() -> null);
        AoTDFinishEconomyUpdateTask finish = allocate(AoTDFinishEconomyUpdateTask.class);
        setField(finish, "futures", new ArrayList<>(List.of(future)));
        setField(finish, "boundary", boundary);
        setField(finish, "batch", boundary.cut.internalTradeBatch);

        AoTDPostImmigrationTradeSnapshotTask post =
                new AoTDPostImmigrationTradeSnapshotTask(List.of(), "save-cleanup-test");
        @SuppressWarnings("unchecked")
        List<AoTDTradeManager.PreparedSnapshot> prepared =
                (List<AoTDTradeManager.PreparedSnapshot>) getField(post, "prepared");
        prepared.add(null);

        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "baselineRevision", 88L);
        setField(stepper, "tasks", new ArrayList<>(List.of(post, finish)));
        stepper.suspendRuntimeTasksForSave();
        AoTDEconomyReachStepper.RuntimeTaskSaveReport report =
                stepper.resumeRuntimeTasksAfterSave();

        check(
                "resume-semantic-restart".equals(report.action)
                        && "POST_IMMIGRATION".equals(report.restartPhase),
                "Post/Finish save did not retain the exact semantic suffix");
        check(prepared.isEmpty() && post.isDone(), "Post retained a partial prepared cut");
        check(future.isCancelled(), "Finish worker future was not cancelled");
        check(finish.isDone(), "Finish task was not retired");
        check(getField(finish, "boundary") == null, "Finish retained its boundary handle");
        check(getField(finish, "batch") == null, "Finish retained its cut DTO");
        check(!manager.isSettlementOpen(), "Finish cleanup leaked an open trade cut");
        check(((Long) getField(stepper, "baselineRevision")) == 0L, "baseline was not ended");
    }

    private static void verifyWaitingStrayGraphIsDiscarded() throws Exception {
        FutureTask<Void> future = new FutureTask<>(() -> null);
        AoTDFinishEconomyUpdateTask finish = allocate(AoTDFinishEconomyUpdateTask.class);
        setField(finish, "futures", new ArrayList<>(List.of(future)));

        AoTDEconomyReachStepper stepper = allocate(AoTDEconomyReachStepper.class);
        setField(stepper, "state", ReachEconomyStepper.State.WAITING);
        setField(stepper, "baselineRevision", 99L);
        setField(stepper, "tasks", new ArrayList<>(List.of(finish)));
        stepper.suspendRuntimeTasksForSave();
        AoTDEconomyReachStepper.RuntimeTaskSaveReport report =
                stepper.resumeRuntimeTasksAfterSave();

        check("resume-discard".equals(report.action), "WAITING stray graph was not discarded");
        check(!report.graphRestored, "WAITING stale graph was reported restored");
        check(getField(stepper, "tasks") == null, "WAITING stale graph survived save restore");
        check(future.isCancelled() && finish.isDone(), "WAITING task resources stayed live");
        check(((Long) getField(stepper, "baselineRevision")) == 0L, "WAITING baseline stayed open");
    }

    private static void verifySaveBeforeFirstRestartFramePreservesSuffix() throws Exception {
        AoTDEconomyReachStepper stepper = doingStepper();
        setField(
                stepper,
                "tasks",
                new ArrayList<>(
                        List.of(
                                new ImmigrationTask(
                                        List.of(market("still-pending")), null, false))));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport initial =
                stepper.restartRuntimeTasksAfterLoad(null);
        check("IMMIGRATION".equals(initial.restartPhase), "fixture did not accept suffix");
        check(getField(stepper, "tasks") == null, "load restart unexpectedly created tasks");

        stepper.suspendRuntimeTasksForSave();
        AoTDEconomyReachStepper.RuntimeTaskSaveReport resumed =
                stepper.resumeRuntimeTasksAfterSave();
        check(
                "IMMIGRATION".equals(resumed.restartPhase),
                "save-before-first-frame cleared the accepted suffix");
        AoTDEconomyReachStepper.RuntimeTaskRestartReport afterSave =
                stepper.restartRuntimeTasksAfterLoad(null);
        check("IMMIGRATION".equals(afterSave.restartPhase), "suffix fell back to MAIN_WORK");
        check(afterSave.remainingMarketCount == 1, "suffix lost its pending market IDs");
    }

    private static void verifyWaitingCadenceIsPreserved() throws Exception {
        AoTDEconomyReachStepper stepper = allocate(AoTDEconomyReachStepper.class);
        setField(stepper, "state", ReachEconomyStepper.State.WAITING);
        setField(stepper, "elapsed", 1.25f);
        setField(stepper, "untilNext", 2.5f);
        setField(stepper, "iterLeft", 3);
        setField(stepper, "prevMonth", 7);
        setField(stepper, "monthEndRefreshPending", true);
        setField(stepper, "pendingPreviousMonth", 6);
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check(!report.iterationRestarted, "WAITING load manufactured an iteration");
        check(report.preservedState == ReachEconomyStepper.State.WAITING, "state changed");
        check(report.preservedElapsed == 1.25f, "elapsed cadence changed");
        check(report.preservedUntilNext == 2.5f, "untilNext cadence changed");
        check(report.preservedIterationsLeft == 3, "iteration count changed");
        check(report.preservedMonth == 7, "calendar month changed");
        check(report.preservedMonthEndRefreshPending, "month-end intent changed");
        check(report.preservedPreviousMonth == 6, "previous month changed");
    }

    private static void verifyUnknownHeadUsesKnownSuffix() throws Exception {
        AoTDEconomyReachStepper stepper = doingStepper();
        ImmigrationTask later = new ImmigrationTask(List.of(market("remaining")), null, false);
        setField(stepper, "tasks", new ArrayList<>(List.of(new UnknownTask(), later)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check(report.unknownStage, "unknown head was not diagnosed");
        check(
                "IMMIGRATION".equals(report.restartPhase),
                "unknown head fell back to full iteration");
        check(report.remainingMarketCount == 1, "known suffix progress was not retained");
    }

    private static void assertRestart(MultiFrameTask task, String phase, String suffix)
            throws Exception {
        AoTDEconomyReachStepper stepper = doingStepper();
        setField(stepper, "tasks", new ArrayList<>(List.of(task)));
        AoTDEconomyReachStepper.RuntimeTaskRestartReport report =
                stepper.restartRuntimeTasksAfterLoad(null);
        check(phase.equals(report.restartPhase), "wrong phase: " + report.summary());
        check(suffix.equals(report.plannedStages), "wrong suffix: " + report.summary());
        check(report.stageHadStarted, "head task was not recorded as started");
    }

    private static AoTDEconomyReachStepper doingStepper() throws Exception {
        AoTDEconomyReachStepper stepper = allocate(AoTDEconomyReachStepper.class);
        setField(stepper, "state", ReachEconomyStepper.State.DOING_TASKS);
        setField(stepper, "elapsed", 0.75f);
        setField(stepper, "untilNext", 2f);
        setField(stepper, "iterLeft", 2);
        setField(stepper, "prevMonth", 4);
        return stepper;
    }

    private static AoTdMainWorkTask2 mainTask(String mode, boolean legacy) throws Exception {
        AoTdMainWorkTask2 task = allocate(AoTdMainWorkTask2.class);
        setField(task, "runtimeMainCheckpointV1", !legacy);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object enumValue =
                Enum.valueOf(
                        (Class)
                                Class.forName(
                                        "data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2$RuntimeRestartMode"),
                        mode);
        setField(task, "runtimeResumeMode", enumValue);
        setField(task, "aotdMarkets", new ArrayList<MarketAPI>());
        setField(task, "mtCommitPlans", new ArrayList<>());
        return task;
    }

    private static Market concreteMarket(String id) throws Exception {
        Market market = allocate(Market.class);
        setField(market, "id", id);
        return market;
    }

    private static Object marketCommitPlan(Market market) throws Exception {
        return marketCommitPlan(market, null);
    }

    private static Object marketCommitPlan(Market market, MarketRegistry.WorkTicket ticket)
            throws Exception {
        Class<?> type =
                Class.forName(
                        "data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2$MarketPriceCommitPlan");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(ticket, market);
    }

    private static void installSettings() {
        SettingsAPI settings =
                (SettingsAPI)
                        Proxy.newProxyInstance(
                                SettingsAPI.class.getClassLoader(),
                                new Class<?>[] {SettingsAPI.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("toString")) return "TestSettings";
                                    Class<?> type = method.getReturnType();
                                    if (!type.isPrimitive()) return null;
                                    if (type == boolean.class) return false;
                                    if (type == char.class) return '\0';
                                    if (type == byte.class) return (byte) 0;
                                    if (type == short.class) return (short) 0;
                                    if (type == int.class) return 0;
                                    if (type == long.class) return 0L;
                                    if (type == float.class) return 0f;
                                    if (type == double.class) return 0d;
                                    return null;
                                });
        Global.setSettings(settings);
    }

    private static void installSector() {
        Map<String, Object> persistentData = new HashMap<>();
        SectorAPI sector =
                (SectorAPI)
                        Proxy.newProxyInstance(
                                SectorAPI.class.getClassLoader(),
                                new Class<?>[] {SectorAPI.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getPersistentData")) {
                                        return persistentData;
                                    }
                                    if (method.getName().equals("toString")) return "TestSector";
                                    Class<?> type = method.getReturnType();
                                    if (!type.isPrimitive()) return null;
                                    if (type == boolean.class) return false;
                                    if (type == char.class) return '\0';
                                    if (type == byte.class) return (byte) 0;
                                    if (type == short.class) return (short) 0;
                                    if (type == int.class) return 0;
                                    if (type == long.class) return 0L;
                                    if (type == float.class) return 0f;
                                    if (type == double.class) return 0d;
                                    return null;
                                });
        Global.setSector(sector);
    }

    private static MarketRegistry.StateSnapshot stateFor(String marketId) {
        for (MarketRegistry.StateSnapshot state : MarketRegistry.snapshotStates()) {
            if (marketId.equals(state.marketId)) return state;
        }
        throw new AssertionError("missing registry state for " + marketId);
    }

    private static MarketAPI market(String id) {
        return (MarketAPI)
                Proxy.newProxyInstance(
                        MarketAPI.class.getClassLoader(),
                        new Class<?>[] {MarketAPI.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getId")) return id;
                            if (method.getName().equals("toString")) return "Market[" + id + ']';
                            if (method.getName().equals("hashCode"))
                                return System.identityHashCode(proxy);
                            if (method.getName().equals("equals")) return proxy == args[0];
                            Class<?> type = method.getReturnType();
                            if (!type.isPrimitive()) return null;
                            if (type == boolean.class) return false;
                            if (type == char.class) return '\0';
                            if (type == byte.class) return (byte) 0;
                            if (type == short.class) return (short) 0;
                            if (type == int.class) return 0;
                            if (type == long.class) return 0L;
                            if (type == float.class) return 0f;
                            if (type == double.class) return 0d;
                            return null;
                        });
    }

    private static Object invokePrivate(Object target, String name, Object... args)
            throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length)
                continue;
            method.setAccessible(true);
            return method.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Keep walking through ReachEconomyStepper for its protected task list.
            }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class UnknownTask extends MultiFrameTask {
        @Override
        public void doNextBatch() {}

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public String getLoggingIdentifier() {
            return "UnknownRuntimeTaskTest";
        }
    }
}
