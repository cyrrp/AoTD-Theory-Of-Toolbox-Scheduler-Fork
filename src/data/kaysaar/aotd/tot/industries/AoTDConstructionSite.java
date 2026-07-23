package data.kaysaar.aotd.tot.industries;

import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import ashlib.data.plugins.ui.models.ProgressBarComponentV2;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderManager;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeManager;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;
import data.kaysaar.aotd.tot.ui.grandwonders.GrandWonderContract;

import java.awt.*;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDConstructionSite extends BaseIndustry {
    public String assignedWonder;
    GrandWonderAPI wonderAPI;

    public String getAssignedWonder() {
        return assignedWonder;
    }

    public GrandWonderAPI getWonderAPI() {
        return wonderAPI;
    }

    public void addResourcesToBeSpentOnRestoration(String resource, int value) {
        int curr = delivered.getOrDefault(resource, 0);
        delivered.put(resource, curr + value);
    }

    public float daysPassedOnConstruction;
    public String uniqueIdForContract = null;

    public float getDaysLeft() {
        return Math.max(0, (wonderAPI.getSpec().getBuildTime() - daysPassedOnConstruction));
    }

    @Override
    protected void buildingFinished() {
        long token = SchedulerBridge.beforeMarketMutation(
                market, SchedulerBridge.MUTATION_INDUSTRY_STRUCTURE);
        try {
        super.buildingFinished();
        } finally {
            SchedulerBridge.afterMarketMutation(token, market,
                    SchedulerBridge.DIRTY_STRUCTURE
                            | SchedulerBridge.DIRTY_INDUSTRIES
                            | SchedulerBridge.DIRTY_DERIVED_ECONOMY, 0L);
        }
    }

    public float getAllowedProgressOnRestoration() {
        LinkedHashMap<String, Integer> required = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : wonderAPI.getDemandCostForRestoration().entrySet()) {
            required.put(entry.getKey(), (int) (AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(entry.getValue(), true, entry.getKey())*Math.ceil(wonderAPI.getSpec().getBuildTime()/30f)));
        }

        if (required == null || required.isEmpty()) {
            return 1f;
        }

        float allowedProgress = 1f;

        for (String commodityId : required.keySet()) {
            int requiredAmount = required.get(commodityId);
            if (requiredAmount <= 0) {
                continue;
            }

            int spentAmount = delivered.getOrDefault(commodityId, 0);
            float ratio = (float) spentAmount / (float) requiredAmount;

            allowedProgress = Math.min(allowedProgress, ratio);
        }

        return Math.max(0f, Math.min(1f, allowedProgress));
    }

    LinkedHashMap<String, Integer> delivered = new LinkedHashMap<>();

    public HashMap<String, Integer> getMonthlyResNeeded() {
        HashMap<String, Integer> commodities = new HashMap<>();

        int months = Math.max(1, (int) Math.ceil((wonderAPI.getSpec().getBuildTime()-daysPassedOnConstruction) / 30f));// prevent division by 0
        LinkedHashMap<String, Integer> required = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : wonderAPI.getDemandCostForRestoration().entrySet()) {
            required.put(entry.getKey(), (int) (AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(entry.getValue(), true, entry.getKey())*Math.ceil(wonderAPI.getSpec().getBuildTime()/30f)));
        }

        required.forEach((key, totalRequired) -> {
            int alreadySpent = delivered.getOrDefault(key, 0);

            int remaining = Math.max(0, totalRequired - alreadySpent);

            if (remaining <= 0) return;

            // ceil division so we don't "lose" resources over time
            int perMonth = (int) Math.ceil((float) remaining / (float) months);

            putCommoditiesIntoMap(commodities, key, perMonth);
        });

        return commodities;
    }


    public String getUniqueIdForContract() {
        if (uniqueIdForContract == null) uniqueIdForContract = Misc.genUID();
        return "grand_wonder_construction_" + uniqueIdForContract;
    }

    public AoTDTradeContract getContract() {
        return AoTDTradeContractManager.getInstance().getActiveContracts().get(getUniqueIdForContract());
    }

    @Override
    protected Object readResolve() {
        Object resolved = super.readResolve();
        spec = getSpec();
        return resolved;
    }


    public void setAssignedWonder(String assignedWonder) {

        this.assignedWonder = assignedWonder;
        building = true;
        uniqueIdForContract = "aotd_gw_construction_site_" + Misc.genUID();
        this.id = "aotd_gw_construction_site_" + assignedWonder;
        wonderAPI = (GrandWonderAPI) market.instantiateIndustry(assignedWonder);
        GrandWonderContract contract = new GrandWonderContract(this);
        AoTDTradeContractManager.getInstance().addContract(contract);
        daysPassedOnConstruction = 0f;
    }

    @Override
    public IndustrySpecAPI getSpec() {
        return Global.getSettings().getIndustrySpec("aotd_gw_construction_site");
    }

    @Override
    public String getCurrentName() {
        if (assignedWonder != null) {
            return Global.getSettings().getIndustrySpec(assignedWonder).getName();
        }
        return super.getCurrentName();
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);
        if (assignedWonder != null) {
            tooltip.addSectionHeading("Current Wonder In Construction", Alignment.MID, 5f);
            tooltip.addPara("Currently constructed wonder : %s", 5f, Color.ORANGE, wonderAPI.getCurrentName());
            tooltip.addPara("This wonder to progress requires monthly flow of resources, which are provided via Trade Contract", 3f);
            LinkedHashMap<String, Integer> am = new LinkedHashMap<>(getMonthlyResNeeded());
            float progress = Math.round(getAllowedProgressOnRestoration() * 100);
            AoTDCommodityShortPanelCombined combined = new AoTDCommodityShortPanelCombined(tooltip.getWidthSoFar(), 3, am);
            ProgressBarComponentV2 bar = new ProgressBarComponentV2(tooltip.getWidthSoFar(), 15, null, null, market.getFaction().getBaseUIColor(), market.getFaction().getDarkUIColor(), progress / 100);
            tooltip.addCustom(combined.getMainPanel(), 5f);
            tooltip.addPara("Currently allowed progress : %s", 3f, Color.ORANGE, progress + "%").setAlignment(Alignment.MID);
            tooltip.addCustom(bar.getMainPanel(), 3f);
        }
    }

    @Override
    public boolean isBuilding() {
        return super.isBuilding();
    }

    @Override
    public boolean isUpgrading() {
        return assignedWonder != null && daysPassedOnConstruction <= wonderAPI.getSpec().getBuildTime();
    }

    @Override
    public float getBuildOrUpgradeProgress() {
        if (assignedWonder != null) {
            return daysPassedOnConstruction / Global.getSettings().getIndustrySpec(assignedWonder).getBuildTime();
        }
        return super.getBuildOrUpgradeProgress();
    }

    @Override
    public String getBuildOrUpgradeProgressText() {
        if (assignedWonder != null) {
            float progress = getBuildOrUpgradeProgress();
            progress = Math.round(progress * 100);
            return "Wonder Construction : " + progress + "%";
        }
        return super.getBuildOrUpgradeProgressText();
    }

    @Override
    public boolean canInstallAICores() {
        return false;
    }

    @Override
    public boolean isAvailableToBuild() {
        return !GrandWonderTypeManager.getWondersVisibleForMarket(market).isEmpty() && market.getFaction().isPlayerFaction() && GrandWonderManager.canBuiltAdditionalWonderDueToSlots(market);
    }

    @Override
    public boolean showWhenUnavailable() {
        return isAvailableToBuild();
    }

    @Override
    public String getId() {
        this.id = getSpec().getId();
        return super.getId();
    }

    @Override
    public void apply() {
        super.apply(true);
        applyImpl();
    }

    @Override
    public void unapply() {
        super.unapply();
        unapplyImpl();
    }

    public void applyImpl() {

    }

    public void unapplyImpl() {

    }


    @Override
    public void advance(float amount) {

        if (assignedWonder != null) {
            if (isUpgrading()) {
                float days = Global.getSector().getClock().convertToDays(amount);
                if (Global.getSettings().isDevMode()) {
                    days *= 30;
                }
                float totalDaysNeeded = wonderAPI.getSpec().getBuildTime();
                float allowedProgress = getAllowedProgressOnRestoration();
                float allowedDays = allowedProgress * totalDaysNeeded;

                if (daysPassedOnConstruction < allowedDays) {
                    daysPassedOnConstruction = Math.min(daysPassedOnConstruction + days, allowedDays);
                }
                float progress = getBuildOrUpgradeProgress();
                if (daysPassedOnConstruction >= totalDaysNeeded || progress >= 1f) {
                    long token = SchedulerBridge.beforeMarketMutation(
                            market, SchedulerBridge.MUTATION_INDUSTRY_STRUCTURE);
                    try {
                        daysPassedOnConstruction = totalDaysNeeded;
                        building = false;
                        GrandWonderAPI wonderAPI1 = (GrandWonderAPI) market.instantiateIndustry(wonderAPI.getId());
                        wonderAPI.doPreSaveCleanup();
                        wonderAPI = null;
                        wonderAPI1.finishedConstruction(market);
                        market.getIndustries().add(wonderAPI1);
                        MessageIntel intel = new MessageIntel(wonderAPI1.getCurrentName() + " at " + market.getName(), Misc.getBasePlayerColor());
                        intel.addLine(BaseIntelPlugin.BULLET + "Construction completed");
                        intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
                        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
                        AoTDIndustryData.getInstance(market).statesOnMarket.put(wonderAPI1.getId(), AoTDIndustryData.AoTDIndustryState.ALREADY_WORKING);
                        AoTDTradeContractManager.getInstance().removeContract(getUniqueIdForContract());
                        GrandWonderManager.getInstance().addBuiltSoFar(wonderAPI1.getId(), 1);
                        market.removeIndustry(this.getId(), MarketAPI.MarketInteractionMode.REMOTE, false);
                    } finally {
                        SchedulerBridge.afterMarketMutation(token, market,
                                SchedulerBridge.DIRTY_STRUCTURE
                                        | SchedulerBridge.DIRTY_INDUSTRIES
                                        | SchedulerBridge.DIRTY_DERIVED_ECONOMY, 0L);
                    }
                }

            }
        } else {
            super.advance(amount);
        }
    }

    @Override
    public void finishBuildingOrUpgrading() {
        if (assignedWonder == null) {
            super.finishBuildingOrUpgrading();
        }
    }

    @Override
    public String getCurrentImage() {
        return super.getCurrentImage();
    }

    public static void putCommoditiesIntoMap(HashMap<String, Integer> map, String commodity, int val) {
        if (map.get(commodity) == null) {
            map.put(commodity, val);
        } else {
            map.compute(commodity, (k, prev) -> val + prev);
        }
    }

    public static int getMonthsFromNextMonth(float daysLeft) {
        if (daysLeft <= 0f) return 0;

        GregorianCalendar cal = (GregorianCalendar) Global.getSector().getClock().getCal().clone();

        // Move to first day of NEXT month (skip current month entirely)
        cal.add(GregorianCalendar.MONTH, 1);
        cal.set(GregorianCalendar.DAY_OF_MONTH, 1);

        int months = 0;
        float remainingDays = daysLeft;

        while (remainingDays > 0f) {
            int daysInMonth = cal.getActualMaximum(GregorianCalendar.DAY_OF_MONTH);

            remainingDays -= daysInMonth;
            months++;

            // Move to next month
            cal.add(GregorianCalendar.MONTH, 1);
            cal.set(GregorianCalendar.DAY_OF_MONTH, 1);
        }

        return months;
    }
}
