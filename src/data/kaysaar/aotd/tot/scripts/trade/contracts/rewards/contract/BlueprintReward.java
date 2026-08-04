package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import java.awt.*;

public class BlueprintReward implements TradeContractRewardDataAPI {
    public enum BlueprintData {
        FIGHTER,
        WEAPON,
        SHIP,
        HULLSPEC,
        CUSTOM
    }

    BlueprintData blueprintData;
    String id;

    public BlueprintReward(String id, BlueprintData data) {
        this.id = id;
        this.blueprintData = data;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        if (blueprintData == BlueprintData.FIGHTER) {
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            tooltip.addPara(
                    "Gain blueprint for %s", defaultOpadText, Color.ORANGE, spec.getWingName());
        } else if (blueprintData == BlueprintData.WEAPON) {
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
            tooltip.addPara(
                    "Gain blueprint for %s", defaultOpadText, Color.ORANGE, spec.getWeaponName());

        } else if (blueprintData == BlueprintData.SHIP) {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
            tooltip.addPara(
                    "Gain blueprint for %s",
                    defaultOpadText, Color.ORANGE, spec.getHullNameWithDashClass());

        } else if (blueprintData == BlueprintData.HULLSPEC) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            tooltip.addPara(
                    "Gain blueprint for %s", defaultOpadText, Color.ORANGE, spec.getDisplayName());

        } else {
            createRewardForCustom(tooltip, width, mode, defaultOpadText);
        }
    }

    public void createRewardForCustom(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {}

    @Override
    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {}

    @Override
    public void executeRewardAtTheEndOfContract() {
        MarketAPI market =
                Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
        if (market != null) {
            if (blueprintData == BlueprintData.FIGHTER) {
                FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
                SpecialItemData data = new SpecialItemData(Items.FIGHTER_BP, spec.getId());
                market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addSpecial(data, 1);

            } else if (blueprintData == BlueprintData.WEAPON) {
                WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
                SpecialItemData data = new SpecialItemData(Items.WEAPON_BP, spec.getWeaponId());
                market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addSpecial(data, 1);

            } else if (blueprintData == BlueprintData.SHIP) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
                SpecialItemData data = new SpecialItemData(Items.SHIP_BP, spec.getBaseHullId());
                market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addSpecial(data, 1);

            } else if (blueprintData == BlueprintData.HULLSPEC) {
                HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
                SpecialItemData data = new SpecialItemData(Items.TAG_MODSPEC, spec.getId());
                market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addSpecial(data, 1);
            } else {
                createCustomReward();
            }
        }
    }

    public void createCustomReward() {}

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {}

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {}
}
