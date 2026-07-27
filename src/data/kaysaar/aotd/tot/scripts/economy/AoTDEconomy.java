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
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDMarketDemandData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AoTDEconomy extends Economy {
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
        MainWorkTask.EconWorkParams params = normalizeWorkParams(econWorkParams);
        AoTDUIEconomyRefreshCoordinator coordinator = uiRefreshCoordinator();
        MarketAPI openingMarket = coordinator.consumeOpeningMarket();
        String uiReason = "open-market";
        if (!isLiveMarket(openingMarket)) {
            openingMarket = Global.getSector().getCurrentlyOpenMarket();
            uiReason = "current-market";
        }
        if (isLiveMarket(openingMarket)) {
            runUiMarketRefresh(openingMarket, params, uiReason, true);
            return;
        }

        coordinator.invalidate("global-next-step");
        for (MarketAPI market : getMarkets()) ((Market) market).updatePrevStability();
        this.getEconomy().nextStep(params);
    }

    @Override
    public void doubleStep() {
        MarketAPI openMarket = Global.getSector().getCurrentlyOpenMarket();
        if (isLiveMarket(openMarket)) {
            runUiMarketRefresh(openMarket, normalizeWorkParams(null),
                    "double-step", true);
            return;
        }
        uiRefreshCoordinator().invalidate("global-double-step");
        super.nextStep();
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

    private boolean isLiveMarket(MarketAPI market) {
        if (market == null || market.getId() == null) return false;
        return MarketRegistry.lookupMarket(market.getId()) == market;
    }

    private void runUiMarketRefresh(
            MarketAPI market, MainWorkTask.EconWorkParams params,
            String reason, boolean allowCoalescing) {
        AoTDUIEconomyRefreshCoordinator coordinator = uiRefreshCoordinator();
        if (allowCoalescing && coordinator.isCurrent(market)) {
            coordinator.recordSkip();
            AoTDEconomySemanticBaseline.operation("ui-economy.refresh-coalesced", market);
            return;
        }

        ((Market) market).updatePrevStability();
        getReachEconomy().nextStepForUiMarket(params, market, reason);
        coordinator.recordCompleted(market);
        AoTDEconomySemanticBaseline.operation("ui-economy.refresh-completed", market);
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
        MarketAPI openMarket = Global.getSector().getCurrentlyOpenMarket();
        if (isLiveMarket(openMarket)) {
            runUiMarketRefresh(openMarket, normalizeWorkParams(null),
                    "triple-step", true);
            return;
        }
        uiRefreshCoordinator().invalidate("global-triple-step");
        super.nextStep();
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
