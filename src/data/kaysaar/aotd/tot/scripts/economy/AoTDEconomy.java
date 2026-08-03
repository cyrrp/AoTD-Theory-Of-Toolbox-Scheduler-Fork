package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.PrepatcherContract;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDMarketDemandData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AoTDEconomy extends Economy {
    private static final int SUPPORTED_UI_MUTATION_REASON_MASK =
            (1 << 4) | (1 << 5) | (1 << 6) | (1 << 7) | (1 << 8)
                    | (1 << 9) | (1 << 10) | (1 << 11) | (1 << 12) | (1 << 13);
    private static final int SUPPORTED_UI_REFRESH_SCOPE_MASK =
            SchedulerBridge.REFRESH_LOCAL_STATS
                    | SchedulerBridge.REFRESH_LOCAL_COMMODITIES
                    | SchedulerBridge.REFRESH_LOCAL_PRICE_STOCKPILE
                    | SchedulerBridge.REFRESH_IMMIGRATION
                    | SchedulerBridge.REFRESH_ACCESSIBILITY
                    | SchedulerBridge.REFRESH_INDUSTRY_STATE
                    | SchedulerBridge.REFRESH_LISTENER_BOUNDARY
                    | SchedulerBridge.REFRESH_AFFECTED_GLOBAL_COMMODITIES
                    | SchedulerBridge.REFRESH_GLOBAL_TOPOLOGY;
    private static final int ACTIONABLE_UI_REFRESH_SCOPE_MASK =
            SchedulerBridge.REFRESH_LOCAL_STATS
                    | SchedulerBridge.REFRESH_LOCAL_COMMODITIES
                    | SchedulerBridge.REFRESH_LOCAL_PRICE_STOCKPILE
                    | SchedulerBridge.REFRESH_IMMIGRATION
                    | SchedulerBridge.REFRESH_ACCESSIBILITY
                    | SchedulerBridge.REFRESH_INDUSTRY_STATE
                    | SchedulerBridge.REFRESH_AFFECTED_GLOBAL_COMMODITIES;
    private static final ConcurrentHashMap<String, Long> NEGATIVE_MARKET_LOOKUPS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object> MARKET_REPAIR_LOCKS =
            new ConcurrentHashMap<>();
    private static final Object MARKET_REGISTRY_LOAD_REPAIR_LOCK = new Object();
    private transient AoTDUIEconomyRefreshCoordinator uiRefreshCoordinator;
    public static boolean runningPrePlayerEconomy = false;
    public static boolean mustPruneCommodities = true;
    public static AoTDEconomy getInstance(){
        if(Global.getSector().getEconomy() instanceof AoTDEconomy){
            return (AoTDEconomy)Global.getSector().getEconomy();
        }
        return null;
    }
    public void doEconomyStepOnNewGameLoad(){
        AoTDEconomyReachStepper stepper = (AoTDEconomyReachStepper) getStepper();
        stepper.doEconomyTick();
        for (MarketAPI market : getMarkets()) {
            AoTDIndustryData data = AoTDIndustryData.getInstance(market);
            data.applyEndOfMonthChange(market);
            for (CommodityOnMarketAPI allCommodity : market.getAllCommodities()) {
                if(allCommodity instanceof AoTDCommodityOnMarket commodity){
                    commodity.getExcDefData().applyDeficitDueToSuddenChangeOfDemand(commodity);
                }
            }
        }

    }
    public MarketAPI getMarketThreadSave(String id) {
        if (id == null) return null;
        Object indexed = MarketRegistry.lookupMarket(id);
        if (indexed instanceof MarketAPI) {
            AoTDEconomySemanticBaseline.operation("economy.registry-market-lookup.hit", 1L);
            return (MarketAPI) indexed;
        }

        // A full boundary publication keeps the previous complete snapshot visible.
        // Do not mutate the registry or create a negative entry until it is READY.
        if (MarketRegistry.getRegistryLifecycle()
                == MarketRegistry.RegistryLifecycle.BUILDING) {
            AoTDEconomySemanticBaseline.operation(
                    "economy.registry-market-lookup.during-build", 1L);
            return super.getMarket(id);
        }

        long generation = MarketRegistry.getRegistryGeneration();
        Long missedAt = NEGATIVE_MARKET_LOOKUPS.get(id);
        if (missedAt != null && missedAt == generation) {
            MarketRegistry.recordNegativeLookupHit();
            AoTDEconomySemanticBaseline.operation(
                    "economy.registry-market-lookup.negative-hit", 1L);
            return null;
        }

        Object repairLock = MARKET_REPAIR_LOCKS.computeIfAbsent(id, ignored -> new Object());
        try {
            synchronized (repairLock) {
                indexed = MarketRegistry.lookupMarket(id);
                if (indexed instanceof MarketAPI) return (MarketAPI) indexed;
                if (MarketRegistry.getRegistryLifecycle()
                        == MarketRegistry.RegistryLifecycle.BUILDING) {
                    return super.getMarket(id);
                }

                generation = MarketRegistry.getRegistryGeneration();
                missedAt = NEGATIVE_MARKET_LOOKUPS.get(id);
                if (missedAt != null && missedAt == generation) {
                    MarketRegistry.recordNegativeLookupHit();
                    return null;
                }

                MarketRegistry.recordTargetedRepairAttempt();
                MarketAPI repaired = super.getMarket(id);
                if (repaired != null) {
                    MarketRegistry.registerMarket(id, repaired);
                    NEGATIVE_MARKET_LOOKUPS.remove(id);
                    MarketRegistry.recordTargetedRepairSuccess();
                    AoTDEconomySemanticBaseline.operation(
                            "economy.registry-market-lookup.repair-hit", 1L);
                    return repaired;
                }

                MarketRegistry.recordLookupMiss();
                NEGATIVE_MARKET_LOOKUPS.put(id, MarketRegistry.getRegistryGeneration());
                AoTDEconomySemanticBaseline.operation(
                        "economy.registry-market-lookup.miss", 1L);
                return null;
            }
        } finally {
            MARKET_REPAIR_LOCKS.remove(id, repairLock);
        }
    }

    /** Full rebuild is permitted only at an explicit economy/load boundary. */
    public void rebuildMarketRegistry() {
        LinkedHashMap<String, MarketAPI> complete = new LinkedHashMap<>();
        for (MarketAPI market : getMarkets()) {
            if (market != null && market.getId() != null) {
                complete.put(market.getId(), market);
            }
        }
        MarketRegistry.replaceAllMarkets(complete);
        NEGATIVE_MARKET_LOOKUPS.clear();
        MARKET_REPAIR_LOCKS.clear();
    }

    /**
     * Save loading may invoke the mod plugin before the deserialized economy has
     * restored its complete market list. Repair that early empty or partial
     * publication once a post-save callback proves that the installed economy
     * contains the restored market.
     */
    private static void repairMarketRegistryAfterLoad(MarketAPI restoredMarket) {
        if (restoredMarket == null) return;

        List<MarketAPI> restoredMarkets =
                Global.getSector().getEconomy().getMarketsCopy();
        if (MarketRegistry.getRegistryLifecycle()
                    == MarketRegistry.RegistryLifecycle.READY
                && MarketRegistry.getRegisteredMarketCount() == restoredMarkets.size()
                && MarketRegistry.lookupMarket(restoredMarket.getId()) == restoredMarket) {
            return;
        }
        synchronized (MARKET_REGISTRY_LOAD_REPAIR_LOCK) {
            restoredMarkets = Global.getSector().getEconomy().getMarketsCopy();
            if (MarketRegistry.getRegistryLifecycle()
                        == MarketRegistry.RegistryLifecycle.READY
                    && MarketRegistry.getRegisteredMarketCount() == restoredMarkets.size()
                    && MarketRegistry.lookupMarket(restoredMarket.getId()) == restoredMarket) {
                return;
            }

            LinkedHashMap<String, MarketAPI> complete = new LinkedHashMap<>();
            boolean containsRestoredMarket = false;
            for (MarketAPI market : restoredMarkets) {
                if (market == null || market.getId() == null) continue;
                complete.put(market.getId(), market);
                if (market == restoredMarket) containsRestoredMarket = true;
            }

            // Do not turn another partial load snapshot into the authoritative
            // registry. A later post-save callback will retry.
            if (complete.isEmpty() || !containsRestoredMarket) return;

            MarketRegistry.replaceAllMarkets(complete);
            NEGATIVE_MARKET_LOOKUPS.clear();
            MARKET_REPAIR_LOCKS.clear();
        }
    }
    public AoTDReachEconomy getReachEconomy(){
        return (AoTDReachEconomy) getEconomy();
    }
    public AoTDEconomy(boolean b, Economy currentEconomyToReplace) {
        super(b);
        AoTDWorkerManager.replaceEconomy(this, "AoTDEconomy-constructor");
        ArrayList<MarketAPI>current = new ArrayList<>(currentEconomyToReplace.getMarkets());
        this.setEcon(new AoTDReachEconomy());
        ReflectionUtilis.setPrivateVariableFromSuperclass("stepper",this,new AoTDEconomyReachStepper(this.getEconomy()));
        this.getMarkets().addAll(current);
        current.clear();


        this.getUpdateListeners().addAll(currentEconomyToReplace.getUpdateListeners());
        currentEconomyToReplace.getUpdateListeners().clear();
        for (MarketAPI market : getMarkets()) {
            market.clearCommodities();
            initCommodities((Market) market);
        }
        rebuildMarketRegistry();
    }


    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        runGlobalEconomyStep(econWorkParams, "global-next-step");
    }

    @Override
    public void doubleStep() {
        runGlobalEconomyStep(null, "global-double-step-1");
        runGlobalEconomyStep(null, "global-double-step-2");
    }

    private void runGlobalEconomyStep(
            MainWorkTask.EconWorkParams econWorkParams, String reason) {
        uiRefreshCoordinator().invalidate(reason);
        super.nextStep(econWorkParams);
    }

    private MainWorkTask.EconWorkParams normalizeWorkParams(
            MainWorkTask.EconWorkParams params) {
        if (params != null) return params;
        MainWorkTask.EconWorkParams normalized = new MainWorkTask.EconWorkParams();
        normalized.withIncomeAndUpkeep = false;
        normalized.withStockpileUpdate = true;
        normalized.withImmigration = true;
        return normalized;
    }

    private AoTDUIEconomyRefreshCoordinator uiRefreshCoordinator() {
        if (uiRefreshCoordinator == null) {
            uiRefreshCoordinator = new AoTDUIEconomyRefreshCoordinator();
        }
        return uiRefreshCoordinator;
    }

    private boolean isConditionOnlyOpeningMarket(MarketAPI market) {
        if (market == null || !market.isPlanetConditionMarketOnly()) return false;
        String id = market.getId();
        return id == null || MarketRegistry.lookupMarket(id) != market;
    }

    private boolean isLiveMarket(MarketAPI market) {
        if (market == null || market.getId() == null) return false;
        return MarketRegistry.lookupMarket(market.getId()) == market;
    }

    private boolean runUiMarketRefresh(
            MarketAPI market, MainWorkTask.EconWorkParams params,
            String reason, boolean allowCoalescing,
            String completionDiagnostic, long completionDetail) {
        AoTDUIEconomyRefreshCoordinator coordinator = prepareUiRefreshCoordinator();
        if (allowCoalescing && coordinator.isCurrent(market)) {
            return recordUiRefreshSkipNoThrow(coordinator);
        }

        ((Market) market).updatePrevStability();
        getReachEconomy().nextStepForUiMarket(params, market, reason);
        return recordUiRefreshCompletedNoThrow(
                coordinator, market, completionDiagnostic, completionDetail);
    }

    private AoTDUIEconomyRefreshCoordinator prepareUiRefreshCoordinator() {
        // Force optional baseline class initialization before semantic work. If
        // class initialization itself fails, the dispatcher still owns no commit
        // and Prepatcher may safely execute its preserved global fallback.
        AoTDEconomySemanticBaseline.isEnabled();
        return uiRefreshCoordinator();
    }

    private static boolean recordUiRefreshCompletedNoThrow(
            AoTDUIEconomyRefreshCoordinator coordinator, MarketAPI market,
            String diagnostic, long detail) {
        try {
            coordinator.recordCompleted(market);
            if (diagnostic == null) {
                AoTDEconomySemanticBaseline.operation(
                        "ui-economy.refresh-completed", market);
            } else {
                AoTDEconomySemanticBaseline.operation(diagnostic, detail);
            }
        } catch (Throwable ignored) {
            // The semantic local refresh has already committed.
        }
        return true;
    }

    private static boolean recordUiRefreshSkipNoThrow(
            AoTDUIEconomyRefreshCoordinator coordinator) {
        try {
            coordinator.recordSkip();
            AoTDEconomySemanticBaseline.operation(
                    "ui-economy.refresh-coalesced", 1L);
        } catch (Throwable ignored) {
            // Coalescing was already proven before this diagnostic counter.
        }
        return true;
    }

    private static boolean recordConditionOnlySkipNoThrow(
            AoTDUIEconomyRefreshCoordinator coordinator) {
        try {
            coordinator.recordConditionOnlySkip();
            AoTDEconomySemanticBaseline.operation(
                    "ui-economy.condition-only-global-step-skipped", 1L);
        } catch (Throwable ignored) {
            // The condition-only action was already accepted.
        }
        return true;
    }

    private static boolean recordSyntheticCargoSkipNoThrow(
            AoTDUIEconomyRefreshCoordinator coordinator) {
        try {
            coordinator.recordSyntheticCargoSkip();
            AoTDEconomySemanticBaseline.operation(
                    "ui-economy.synthetic-cargo-global-step-skipped", 1L);
        } catch (Throwable ignored) {
            // The synthetic-Cargo action was already accepted.
        }
        return true;
    }

    public String getUiRefreshStatusSummary() {
        return uiRefreshCoordinator().statusSummary();
    }


    @Override
    public void removeMarket(MarketAPI marketAPI) {
        uiRefreshCoordinator().invalidate("remove-market");
        long token = SchedulerBridge.beforeMarketMutation(
                marketAPI, SchedulerBridge.MUTATION_MARKET_MEMBERSHIP);
        try {
            AoTDEconomySemanticBaseline.operation("economy.remove-market", marketAPI);
            super.removeMarket(marketAPI);
            AoTDTradeManager.getInstance().removeMarket(marketAPI);
        } finally {
            try {
                SchedulerBridge.afterMarketMutation(token, marketAPI,
                        SchedulerBridge.DIRTY_STRUCTURE
                                | SchedulerBridge.DIRTY_DERIVED_ECONOMY, 0L);
            } finally {
                if (!getMarkets().contains(marketAPI)) {
                    MarketRegistry.unregisterMarket(marketAPI.getId(), marketAPI);
                }
            }
        }
    }

    @Override
    public void addMarket(MarketAPI marketAPI, boolean addJunk) {
        uiRefreshCoordinator().invalidate("add-market");
        long token = SchedulerBridge.beforeMarketMutation(
                marketAPI, SchedulerBridge.MUTATION_MARKET_MEMBERSHIP);
        try {
            AoTDEconomySemanticBaseline.operation("economy.add-market", marketAPI);
            super.addMarket(marketAPI, addJunk);
            Market market = (Market) marketAPI;
            market.clearCommodities();
            initCommodities(market);
            if(!market.hasCondition("aotd_toolbox_food_corrector")){
                market.addCondition("aotd_toolbox_food_corrector");
                market.getCondition("aotd_toolbox_food_corrector").getPlugin().apply(null);
            }
            MarketRegistry.registerMarket(marketAPI.getId(), marketAPI);
        } finally {
            SchedulerBridge.afterMarketMutation(token, marketAPI,
                    SchedulerBridge.DIRTY_STRUCTURE
                            | SchedulerBridge.DIRTY_CONDITIONS
                            | SchedulerBridge.DIRTY_DERIVED_ECONOMY, 0L);
        }
    }

    public void runMarketAdjustmentAfterEconomyCreation(){
        for (MarketAPI market : getMarkets()) {
            adjustMarketAfterEconomyCreation(market);
        }
        rebuildMarketRegistry();
    }

    private static void adjustMarketAfterEconomyCreation(MarketAPI market) {
        int reason = SchedulerBridge.MUTATION_COMMODITY_STRUCTURE;
        int dirty = SchedulerBridge.DIRTY_STRUCTURE
                | SchedulerBridge.DIRTY_DERIVED_ECONOMY
                | MarketRegistry.DIRTY_VALUE_STATE
                | MarketRegistry.DIRTY_PRICE
                | MarketRegistry.DIRTY_STOCKPILE;
        if (!market.hasCondition("aotd_toolbox_food_corrector")) {
            reason |= SchedulerBridge.MUTATION_CONDITION_STRUCTURE;
            dirty |= SchedulerBridge.DIRTY_CONDITIONS;
        }
        long token = SchedulerBridge.beforeMarketMutation(market, reason);
        try {
            market.clearCommodities();
            initCommodities((Market) market);
            if (!market.hasCondition("aotd_toolbox_food_corrector")) {
                market.addCondition("aotd_toolbox_food_corrector");
            }
        } finally {
            SchedulerBridge.afterMarketMutation(token, market, dirty, 0L);
        }
    }
    @Override
    public void tripleStep() {
        runGlobalEconomyStep(null, "global-triple-step-1");
        runGlobalEconomyStep(null, "global-triple-step-2");
        runGlobalEconomyStep(null, "global-triple-step-3");
    }

    /**
     * Executes only an exact UI action already classified by Prepatcher. The
     * normal Economy step methods above deliberately never infer UI intent.
     */
    public final boolean dispatchPrepatcherUiEconomyStep(
            int action, MarketAPI market, long detail,
            String[] affectedCommodityIds) {
        if (!SchedulerBridge.hasCapability(
                PrepatcherContract.CAPABILITY_UI_ECONOMY_DISPATCH)) {
            return false;
        }
        if (action == PrepatcherContract.UI_ECONOMY_ACTION_MARKET_OPEN) {
            if (detail != 0L || !hasNoCommodityIds(affectedCommodityIds)) return false;
            if (market != null && market.isPlanetConditionMarketOnly()) {
                if (!isConditionOnlyOpeningMarket(market)) return false;
                return recordConditionOnlySkipNoThrow(uiRefreshCoordinator());
            }
            if (!isLiveMarket(market)) return false;
            return runUiMarketRefresh(market, normalizeWorkParams(null),
                    "open-market", true, null, 0L);
        }
        if (action == PrepatcherContract.UI_ECONOMY_ACTION_CARGO) {
            if (!hasNoCommodityIds(affectedCommodityIds)) return false;
            if (detail == PrepatcherContract.UI_ECONOMY_CARGO_SYNTHETIC) {
                if (market != null) return false;
                return recordSyntheticCargoSkipNoThrow(uiRefreshCoordinator());
            }
            if (detail != PrepatcherContract.UI_ECONOMY_CARGO_LIVE_MARKET
                    || !isLiveMarket(market)) {
                return false;
            }
            return runUiMarketRefresh(market, normalizeWorkParams(null),
                    "cargo", true, null, 0L);
        }
        if (action != PrepatcherContract.UI_ECONOMY_ACTION_MARKET_MUTATION) {
            return false;
        }

        int reason = SchedulerBridge.mutationReason(detail);
        int scope = SchedulerBridge.mutationScope(detail);
        if (!SchedulerBridge.hasCapability(
                PrepatcherContract.CAPABILITY_UI_MARKET_MUTATION_REFRESH)
                || !isLiveMarket(market)
                || reason == 0
                || (reason & ~SUPPORTED_UI_MUTATION_REASON_MASK) != 0
                || scope == 0
                || (scope & ~SUPPORTED_UI_REFRESH_SCOPE_MASK) != 0
                || (scope & ACTIONABLE_UI_REFRESH_SCOPE_MASK) == 0
                || (scope & SchedulerBridge.REFRESH_GLOBAL_TOPOLOGY) != 0) {
            return false;
        }

        boolean targeted = (scope
                & SchedulerBridge.REFRESH_AFFECTED_GLOBAL_COMMODITIES) != 0;
        String[] affected = affectedCommodityIds == null
                ? new String[0] : affectedCommodityIds.clone();
        if (targeted ? !areSortedUniqueCommodityIds(affected)
                : affected.length != 0) {
            return false;
        }

        AoTDUIEconomyRefreshCoordinator coordinator = prepareUiRefreshCoordinator();
        MarketRegistry.markDirty(market, dirtyMaskForUiMutationScope(scope),
                MarketRegistry.PRIORITY_IMMEDIATE);

        if (targeted) {
            ((Market) market).updatePrevStability();
            getReachEconomy().nextStepForUiMarketMutation(
                    normalizeWorkParams(null), market, affected, scope,
                    "mutation-reason-0x" + Integer.toHexString(reason));
            return recordUiRefreshCompletedNoThrow(
                    coordinator, market,
                    "ui-economy.mutation-targeted-commodities",
                    ((long) reason << 32) | (scope & 0xffffffffL));
        }

        return runUiMarketRefresh(market, normalizeWorkParams(null),
                "mutation-reason-0x" + Integer.toHexString(reason), false,
                "ui-economy.mutation-reason-localized",
                ((long) reason << 32) | (scope & 0xffffffffL));
    }

    private static boolean hasNoCommodityIds(String[] commodityIds) {
        return commodityIds == null || commodityIds.length == 0;
    }

    private static boolean areSortedUniqueCommodityIds(String[] commodityIds) {
        if (commodityIds == null || commodityIds.length == 0) return false;
        String previous = null;
        for (String id : commodityIds) {
            if (id == null || id.isBlank()
                    || (previous != null && previous.compareTo(id) >= 0)) {
                return false;
            }
            previous = id;
        }
        return true;
    }

    private static int dirtyMaskForUiMutationScope(int scope) {
        int dirty = 0;
        if ((scope & (SchedulerBridge.REFRESH_LOCAL_STATS
                | SchedulerBridge.REFRESH_IMMIGRATION
                | SchedulerBridge.REFRESH_LOCAL_COMMODITIES)) != 0) {
            dirty |= MarketRegistry.DIRTY_VALUE_STATE
                    | SchedulerBridge.DIRTY_DERIVED_ECONOMY;
        }
        if ((scope & SchedulerBridge.REFRESH_LOCAL_PRICE_STOCKPILE) != 0) {
            dirty |= MarketRegistry.DIRTY_PRICE | MarketRegistry.DIRTY_STOCKPILE;
        }
        if ((scope & SchedulerBridge.REFRESH_ACCESSIBILITY) != 0) {
            dirty |= MarketRegistry.DIRTY_ACCESSIBILITY;
        }
        if ((scope & SchedulerBridge.REFRESH_INDUSTRY_STATE) != 0) {
            dirty |= SchedulerBridge.DIRTY_INDUSTRIES;
        }
        return dirty == 0 ? MarketRegistry.DIRTY_VALUE_STATE : dirty;
    }

    public static void pruneCommodities(){
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            pruneCommoditiesThatMightAppear((Market) market);
        }
    }

    public static void pruneCommoditiesThatMightAppear(Market market) {
        repairMarketRegistryAfterLoad(market);
        List<CommodityOnMarket> commodities = getCommodities(market);

        ensureAoTDDemandData(market);

        /*
         * Preserve already-converted AoTD commodities where possible.
         * Replace vanilla CommodityOnMarket entries and add any missing commodity specs.
         */
        Map<String, AoTDCommodityOnMarket> byId = new HashMap<>();

        for (CommodityOnMarket commodity : new ArrayList<>(commodities)) {
            if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                byId.put(aotdCommodity.getId(), aotdCommodity);
            }
        }

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            AoTDCommodityOnMarket commodity = byId.get(spec.getId());

            if (commodity == null) {
                commodity = new AoTDCommodityOnMarket(market, spec.getId());
                commodity.getSupplyDemandData();
                byId.put(spec.getId(), commodity);
            }
        }

        commodities.clear();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            commodities.add(byId.get(spec.getId()));
        }

        rebuildCommodityLookupMaps(market, commodities);
        market.getAllCommodities();
    }

    public static void initCommodities(Market market) {
        List<CommodityOnMarket> commodities = getCommodities(market);

        commodities.clear();

        ReflectionUtilis.setPrivateVariableFromSuperclass("demandData", market, new AoTDMarketDemandData(market));

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            market.getDemandData().getDemand(spec.getDemandClass());

            AoTDCommodityOnMarket commodity = new AoTDCommodityOnMarket(market, spec.getId());
            commodity.getSupplyDemandData();

            commodities.add(commodity);
        }

        rebuildCommodityLookupMaps(market, commodities);
        market.getAllCommodities();
    }

    private static void ensureAoTDDemandData(Market market) {
        Object demandData = ReflectionUtilis.getPrivateVariableFromSuperClass("demandData", market);

        if (!(demandData instanceof AoTDMarketDemandData)) {
            ReflectionUtilis.setPrivateVariableFromSuperclass("demandData", market, new AoTDMarketDemandData(market));
        }
    }

    @SuppressWarnings("unchecked")
    private static void rebuildCommodityLookupMaps(Market market, List<CommodityOnMarket> commodities) {
        Map<String, CommodityOnMarket> commodityMap = (Map<String, CommodityOnMarket>) ReflectionUtilis
            .getPrivateVariableFromSuperClass("commodityMap", market);

        if (commodityMap == null) {
            commodityMap = new HashMap<>();
            ReflectionUtilis.setPrivateVariableFromSuperclass("commodityMap", market, commodityMap);
        }

        var commoditiesByDemandClass = (Map<String, List<CommodityOnMarket>>) ReflectionUtilis.
            getPrivateVariableFromSuperClass("commoditiesByDemandClass", market);

        if (commoditiesByDemandClass == null) {
            commoditiesByDemandClass = new HashMap<>();
            ReflectionUtilis.setPrivateVariableFromSuperclass("commoditiesByDemandClass", market, commoditiesByDemandClass);
        }

        commodityMap.clear();
        commoditiesByDemandClass.clear();

        for (CommodityOnMarket commodity : commodities) {
            if (!(commodity instanceof AoTDCommodityOnMarket)) {
                /*
                 * This should never happen after prune/init, but keep this guard so
                 * getCommoditiesWithClass() cannot return vanilla CommodityOnMarket.
                 */
                continue;
            }

            commodityMap.put(commodity.getId(), commodity);

            String demandClass = ((AoTDCommodityOnMarket) commodity).getSpec().getDemandClass();
            List<CommodityOnMarket> demandClassCommodities = commoditiesByDemandClass.get(demandClass);

            if (demandClassCommodities == null) {
                demandClassCommodities = new ArrayList<>();
                commoditiesByDemandClass.put(demandClass, demandClassCommodities);
            }

            demandClassCommodities.add(commodity);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<CommodityOnMarket> getCommodities(final MarketAPI market) {
        List<CommodityOnMarket> commodities = (List<CommodityOnMarket>) ReflectionUtilis
            .getPrivateVariableFromSuperClass("commodities", market);

        if (commodities == null) {
            commodities = new ArrayList<>();
            ReflectionUtilis.setPrivateVariableFromSuperclass("commodities", market, commodities);
        }

        return commodities;
    }
}
