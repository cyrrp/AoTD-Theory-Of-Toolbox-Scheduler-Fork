package data.kaysaar.aotd.tot.plugins;


import ashlib.data.plugins.coreui.CommandTabListener;
import ashlib.data.plugins.coreui.CommandTabMemoryManager;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.impl.SharedUnlockData;
import com.fs.starfarer.api.impl.campaign.fleets.EconomyFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.graid.StandardGroundRaidObjectivesCreator;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEventCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator;
import com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryPlugin;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.econ.Economy;
import data.campaign.submarkets.HMI_ExecMarketPlugin;
import data.campaign.submarkets.HMI_ScrapMarketPlugin;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.codex.AoTDToTIndustryEntryCodex;
import data.kaysaar.aotd.tot.industries.AoTDToolboxPopAndInfra;
import data.kaysaar.aotd.tot.intel.bar.events.AoTDDeliveryBarEventCreator;
import data.kaysaar.aotd.tot.listeners.*;
import data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpecManager;
import data.kaysaar.aotd.tot.raids.AoTDStandardGroundRaidObjectivesCreator;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.economy.AoTDGlobalEconomyCoordinator;
import data.kaysaar.aotd.tot.scripts.coreui.IndustryTooltipPlacer;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.ColonyUIListener;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.MarketContextListenerInjector;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomySemanticBaseline;
import data.kaysaar.aotd.tot.scripts.economy.AoTDWorkerManager;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDBlackMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDLocalResourcesSubmarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDMilitarySubmarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.hmi.AoTDHMI_ExecMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.hmi.AoTDHMI_ScrapMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.nex.AoTDxNexBlackMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.nex.AoTDxNexLocalResourcesSubmarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.nex.AoTDxNexMilitarySubmarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.nex.AoTDxNexOpenMarketPlugin;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDContractRewardCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.CivilianSupplyProgram;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.FuelLogisticProgram;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.PlayerIssuedSupplyContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.StrategicArmamentSupply;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards.GenericAICoreCreator;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards.GenericBlueprintCreator;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.rewards.GenericSpecialItemCreator;
import data.kaysaar.aotd.tot.scripts.trade.route.AoTDEconomyRouteManager;
import data.kaysaar.aotd.tot.strings.AoTDIndTags;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;
import data.kaysaar.aotd.tot.ui.core.CommoditiesPanelInjector;
import data.kaysaar.aotd.tot.ui.core.EconomyTabListener;
import data.kaysaar.aotd.tot.ui.core.GrandProjectLabelInjector;
import data.kaysaar.aotd.tot.ui.core.DomainTabListener;
import exerelin.campaign.submarkets.Nex_BlackMarketPlugin;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.campaign.submarkets.Nex_MilitarySubmarketPlugin;
import exerelin.campaign.submarkets.Nex_OpenMarketPlugin;
import org.apache.log4j.Logger;

import java.util.*;

import static com.fs.starfarer.api.impl.codex.CodexDataV2.*;


public class AoTDToolboxTheoryPlugin extends BaseModPlugin implements MarketContextListenerInjector {
    public static final Logger log = Global.getLogger(AoTDToolboxTheoryPlugin.class);
    public static LinkedHashMap<String,String>marketsToReplace = new LinkedHashMap<>();
    public static void loadMarketsToReplace(){
        if(Global.getSettings().getModManager().isModEnabled("nexerelin")){
            marketsToReplace.put(Nex_OpenMarketPlugin.class.getName(), AoTDxNexOpenMarketPlugin.class.getName());
            marketsToReplace.put(Nex_BlackMarketPlugin.class.getName(), AoTDxNexBlackMarketPlugin.class.getName());
            marketsToReplace.put(Nex_MilitarySubmarketPlugin.class.getName(), AoTDxNexMilitarySubmarketPlugin.class.getName());
            marketsToReplace.put(Nex_LocalResourcesSubmarketPlugin.class.getName(), AoTDxNexLocalResourcesSubmarketPlugin.class.getName());
        }
        if(Global.getSettings().getModManager().isModEnabled("HMI")){
            marketsToReplace.put(HMI_ScrapMarketPlugin.class.getName(), AoTDHMI_ScrapMarketPlugin.class.getName());
            marketsToReplace.put(HMI_ExecMarketPlugin.class.getName(), AoTDHMI_ExecMarketPlugin.class.getName());
        }
        marketsToReplace.put(OpenMarketPlugin.class.getName(), AoTDOpenMarketPlugin.class.getName());
        marketsToReplace.put(BlackMarketPlugin.class.getName(), AoTDBlackMarketPlugin.class.getName());
        marketsToReplace.put(MilitarySubmarketPlugin.class.getName(), AoTDMilitarySubmarketPlugin.class.getName());
        marketsToReplace.put(LocalResourcesSubmarketPlugin.class.getName(), AoTDLocalResourcesSubmarketPlugin.class.getName());


    }
    public static boolean isGeneratingSector = false;


    @Override
    public void onApplicationLoad() throws Exception {
        SchedulerBridge.initialize();
        SchedulerBridge.requireProductionProfile();
        AoTDContractRewardCreatorManager.addCreator(GenericBlueprintCreator.class.getName(), new GenericBlueprintCreator());
        AoTDContractRewardCreatorManager.addCreator(GenericSpecialItemCreator.class.getName(), new GenericSpecialItemCreator());
        AoTDContractRewardCreatorManager.addCreator(GenericAICoreCreator.class.getName(), new GenericAICoreCreator());
        PlayerIssuedSupplyContract creatorAPI = new PlayerIssuedSupplyContract();
        AoTDPlayerContractCreatorManager.addCreator(creatorAPI.getBaseIdForContract(),creatorAPI);

        FuelLogisticProgram creatorAPI2 = new FuelLogisticProgram();
        AoTDPlayerContractCreatorManager.addCreator(creatorAPI2.getBaseIdForContract(),creatorAPI2);

        StrategicArmamentSupply creatorAPI3 = new StrategicArmamentSupply();
        AoTDPlayerContractCreatorManager.addCreator(creatorAPI3.getBaseIdForContract(),creatorAPI3);

        CivilianSupplyProgram creatorAPI4 = new CivilianSupplyProgram();
        AoTDPlayerContractCreatorManager.addCreator(creatorAPI4.getBaseIdForContract(),creatorAPI4);

    }


    public String getIndustryStringBasedOnOrder(int positionOnList, String... ids) {
        if (ids == null || ids.length == 0) return null;

        List<IndustrySpecAPI> specs = new ArrayList<>();

        for (String id : ids) {
            IndustrySpecAPI specAPI = Global.getSettings().getIndustrySpec(id);

            if (specAPI != null) {
                specs.add(specAPI);
            }
        }

        // Sort by industry order
        specs.sort(Comparator.comparingInt(IndustrySpecAPI::getOrder));

        // Bounds check
        if (positionOnList < 0 || positionOnList >= specs.size()) {
            return null;
        }

        return specs.get(positionOnList).getId();
    }

    @Override
    public void onAboutToStartGeneratingCodex() {
        AoTDProductionSpecManager.generateSpecsForAllStuff();
        if(Global.getSettings().getGameVersion().contains("0.98a")){
            if(!Global.getSettings().getGameVersion().equals("0.98a-RC8")){
                throw  new RuntimeException("AoTD Theory of Toolbox: This version of mod for 0.98a game can only be run at exactly 0.98a-RC8 version ");
            }
            MarketAPI test = Global.getFactory().createMarket("test","test",3);
            test.addIndustry(Industries.HEAVYBATTERIES);
            test.addIndustry(Industries.REFINING);

            Industry first =test.getIndustry( getIndustryStringBasedOnOrder(0,Industries.HEAVYBATTERIES,Industries.REFINING));
            Industry second =test.getIndustry( getIndustryStringBasedOnOrder(1,Industries.HEAVYBATTERIES,Industries.REFINING));


            first.getDemand(Commodities.ALPHA_CORE).getQuantity().modifyFlat("test",20);
            second.getDemand(Commodities.ALPHA_CORE).getQuantity().modifyFlat("test",40);
            test.getCommodityData(Commodities.ALPHA_CORE).getAvailableStat().modifyFlat("test",21);

            int firstDeficit = first.getMaxDeficit(Commodities.ALPHA_CORE).two;
            int secondDeficit = second.getMaxDeficit(Commodities.ALPHA_CORE).two;

            if(firstDeficit != 0 || secondDeficit != 39){
                throw new RuntimeException(
                        "AoTD Scheduler Fork clean BaseIndustry validation failed: expected "
                                + "deficits 0/39, got " + firstDeficit + "/" + secondDeficit
                                + ". Verify that the Stage 8 StarsectorPrepatcher production "
                                + "profile is active. Keep the original game starfarer.api.jar; "
                                + "do not install the obsolete AoTD core-JAR replacement. "
                                + SchedulerBridge.statusSummary());
            }
        }
        AoTDCommodityEconSpecManager.loadSpecs();
        Economy econ = (Economy) Global.getSector().getEconomy();
        ReflectionUtilis.setPrivateVariableFromSuperclass("economy", Global.getSector(), new AoTDEconomy(econ.isSimMode(), econ));
        loadMarketsToReplace();
        Global.getSettings().getIndustrySpec(Industries.SPACEPORT).addTag(AoTDIndTags.ALWAYS_ACTIVE_NON_PENDING);
        Global.getSettings().getIndustrySpec(Industries.POPULATION).addTag(AoTDIndTags.ALWAYS_ACTIVE_NON_PENDING);
        Global.getSettings().getIndustrySpec(Industries.POPULATION).setPluginClass(AoTDToolboxPopAndInfra.class.getName());
        for (AoTDCommodityEconSpec value : AoTDCommodityEconSpecManager.specs.values()) {
            if(value.econUnitMult!=1f){
                ReflectionUtilis.invokeMethodWithAutoProjection("setEconUnit",Global.getSettings().getCommoditySpec(value.commodityId),Global.getSettings().getCommoditySpec(value.commodityId).getEconUnit()*value.econUnitMult);
            }
        }

        for (SubmarketSpecAPI allSubmarketSpec : Global.getSettings().getAllSubmarketSpecs()) {
            String clas = (String) ReflectionUtilis.invokeMethodWithAutoProjection("getScriptClass",allSubmarketSpec);
            if(marketsToReplace.containsKey(clas)){
                ReflectionUtilis.replaceFirstStringFieldWithValue(allSubmarketSpec,clas,marketsToReplace.get(clas),false);
            }
        }
        Global.getSettings().getCommoditySpec(Commodities.SHIPS).getTags().add(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS);
        Global.getSettings().getCommoditySpec(Commodities.CREW).getTags().add(AoTDTradeTags.IGNORE_SCAVENGERS);
        Global.getSettings().getCommoditySpec(Commodities.MARINES).getTags().add(AoTDTradeTags.IGNORE_SCAVENGERS);

        Global.getSettings().getCommoditySpec(Commodities.CREW).getTags().add(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS);
        Global.getSettings().getCommoditySpec(Commodities.MARINES).getTags().add(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS);

    }



    @Override
    public void onCodexDataGenerated() {
        CodexEntryV2 plugin = (CodexEntryV2) CodexDataV2.ROOT.getChildren().stream().filter(x->x.getId().equals(CodexDataV2.CAT_INDUSTRIES)).findAny().orElse(null);
//        if(plugin!=null){
//            plugin.getChildren().clear();
//            populateIndustries(plugin);
//        }


    }
    public static void populateIndustries(CodexEntryPlugin parent) {
        List<IndustrySpecAPI> specs = Global.getSettings().getAllIndustrySpecs();
        for (final IndustrySpecAPI spec : specs) {
            if (spec.hasTag(Industries.TAG_PARENT)) continue;
            if (spec.hasTag(Tags.HIDE_IN_CODEX)) continue;

            CodexEntryV2 curr = new AoTDToTIndustryEntryCodex(getIndustryEntryId(spec.getId()),
                    spec.getName(), spec.getImageName(), spec) {
                @Override
                public void createTitleForList(TooltipMakerAPI info, float width, ListMode mode) {
                    info.addPara(spec.getName(), Misc.getBasePlayerColor(), 0f);
                        //info.addPara("Industry", Misc.getGrayColor(), 0f);
                        boolean structure = spec.hasTag(Industries.TAG_STRUCTURE);
                        String type = "Industry";
                        if (structure) type = "Structure";
                        info.addPara(type, Misc.getGrayColor(), 0f);

                }

                @Override
                public boolean isVignetteIcon() {
                    return true;
                }
                @Override
                public boolean matchesTags(Set<String> tags) {
                    boolean industry = spec.hasTag(Industries.TAG_INDUSTRY);
                    boolean structure = spec.hasTag(Industries.TAG_STRUCTURE);
                    boolean station = spec.hasTag(Industries.TAG_STATION);
                    if (tags.contains(OTHER) && !industry && !structure && !station) return true;
                    if (tags.contains(INDUSTRIES) && industry) return true;
                    if (tags.contains(STRUCTURES) && structure && !station) return true;
                    if (tags.contains(STATIONS) && station) return true;
                    return false;
                }
                @Override
                public Set<String> getUnlockRelatedTags() {
                    return spec.getTags();
                }
                @Override
                public boolean isUnlockedIfRequiresUnlock() {
                    return SharedUnlockData.get().isPlayerAwareOfIndustry(spec.getId());
                }
            };
            parent.addChild(curr);
        }
    }
    @Override
    public void onNewGameAfterEconomyLoad() {
        Economy econ = (Economy) Global.getSector().getEconomy();
        ReflectionUtilis.setPrivateVariableFromSuperclass("economy", Global.getSector(), new AoTDEconomy(econ.isSimMode(), econ));
        AoTDEconomy econ2 = (AoTDEconomy) Global.getSector().getEconomy();
        econ2.runMarketAdjustmentAfterEconomyCreation();
        AoTDEconomy.runningPrePlayerEconomy = true;
        Global.getSector().removeScriptsOfClass(EconomyFleetRouteManager.class);
        Global.getSector().addScript(new AoTDEconomyRouteManager());

    }
    public static boolean afterSaveState = true;
    @Override
    public void beforeGameSave() {
        afterSaveState = false;
        AoTDEconomySemanticBaseline.flush("before-game-save");
        AoTDWorkerManager.beginSaveAndWait();
        AoTDGlobalEconomyCoordinator.flushDeliveredTimeForBoundary(
                AoTDGlobalEconomyCoordinator.BOUNDARY_SAVE);
        super.beforeGameSave();
    }

    @Override
    public void afterGameSave() {
        afterSaveState = true;
        AoTDEconomySemanticBaseline.flush("after-game-save");
        AoTDWorkerManager.endSave();
    }

    @Override
    public void onGameSaveFailed() {
        afterSaveState = true;
        AoTDEconomySemanticBaseline.flush("game-save-failed");
        AoTDWorkerManager.endSave();
    }

    @Override
    public void onDevModeF8Reload() {
        AoTDWorkerManager.resetRuntime("dev-mode-f8-reload");
        AoTDWorkerManager.bindLoadedEconomy(
                AoTDEconomy.getInstance(), "dev-mode-f8-reload-bind");
    }

    @Override
    public void onEnabled(boolean enabled) {
        AoTDWorkerManager.onModEnabledState(enabled);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        AoTDCoreUIListener.resetCampaignState();
        AoTDEconomy economy = AoTDEconomy.getInstance();
        AoTDWorkerManager.beginCampaign(economy,
                newGame ? "new-game-load" : "save-load");
        AoTDEconomySemanticBaseline.initialize();
        if (economy != null) economy.rebuildMarketRegistry();
        AoTDCommodityEconSpecManager.loadSpecs();
        if(newGame){
            CommandTabMemoryManager.getInstance().setLastCheckedTab("domain");
        }
        Global.getSector().getListenerManager().addListener(new AoTDGrandWonderBtnListener(),true);
        Global.getSector().getListenerManager().addListener(new AoTDGrandWonderDecivListener(),true);
        Global.getSector().getListenerManager().addListener(new AoTDCommodityTooltipInjector(), true);
        if(newGame){
            for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
                marketAPI.reapplyConditions();
                for (Industry industry : marketAPI.getIndustries()) {
                    if(!AoTDIndustryData.getInstance(industry.getMarket()).isPending(industry.getId())){
                        industry.reapply();
                    }
                }
            }
        }
        Global.getSector().getListenerManager().addListener(new BMOWonderBlockerListener(),true);
        Global.getSector().addTransientScript(new IndustryTooltipPlacer());
        ColonyUIListener.refresh();
        Global.getSector().getListenerManager().addListener(new DomainTabListener(),true);
        if(CommandTabMemoryManager.getInstance().getLastCheckedTab()!=null ){
            if(CommandTabMemoryManager.getInstance().getLastCheckedTab().equalsIgnoreCase("economy")||CommandTabMemoryManager.getInstance().getLastCheckedTab().equalsIgnoreCase("colonies")){
                CommandTabMemoryManager.getInstance().setLastCheckedTab("domain");
            }
        }

        Global.getSector().getListenerManager().addListener(new AoTDToobloxIndustryListener(), true);
        Global.getSector().addTransientScript(new DelayedActionScript(0.1f) {
            @Override
            public void doAction() {
                BarEventManager bar = BarEventManager.getInstance();
                if(bar.hasEventCreator(DeliveryBarEventCreator.class)){
                    bar.getCreators().removeIf(x->x instanceof DeliveryBarEventCreator);
                    bar.addEventCreator(new AoTDDeliveryBarEventCreator());
                }
                bar.getCreators().removeIf(x->{
                    if(x instanceof SpecBarEventCreator creator){
                        return creator.getSpec().getId().equals("cpm");
                    }
                    return false;
                });
            }
        });
        if(Global.getSettings().isDevMode()){
            AoTDTradeContractManager.getInstance().getCurrLevelData().addExp(9000000);

        }
        Global.getSector().getListenerManager().addListener(new AoTDTradeContractRefresh(),true);
        if(newGame){
            AoTDEconomy.getInstance().doEconomyStepOnNewGameLoad();
            AoTDEconomy.runningPrePlayerEconomy = false;
            AoTDTradeContractManager.getInstance().generateNewContractsForBrowser();

        }
        AoTDCoreUIListener listener = new AoTDCoreUIListener();
        Global.getSector().addTransientScript( listener);
        Global.getSector().getListenerManager().addListener(listener,true);
        Global.getSector().getListenerManager().removeListenerOfClass(StandardGroundRaidObjectivesCreator.class);
        Global.getSector().getListenerManager().addListener(new AoTDStandardGroundRaidObjectivesCreator(),true);
    }

    @Override
    public void reloadListenerContext() {
        ColonyUIListener.addMarketListener(new CommoditiesPanelInjector());
        ColonyUIListener.addMarketListener(new GrandProjectLabelInjector());
    }
}




