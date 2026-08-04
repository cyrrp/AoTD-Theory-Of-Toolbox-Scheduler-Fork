package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly.MonthlyGrowthAndStabilityBoost;
import java.awt.*;
import java.util.LinkedHashSet;

public class CivilianSupplyProgram extends DirectedTradeInitiativeBaseContract {

    public static float growthPerMetDemandDomesticGoods = 2;
    public static float stabilityPerMetDemandFood = 1f;

    @Override
    public String getNameOfContract() {
        return "Civilian Supply Program";
    }

    @Override
    public String getBaseIdForContract() {
        return "civilian_supply";
    }

    @Override
    public void onContractCreated(AoTDTradeContract generatedContract) {
        super.onContractCreated(generatedContract);
        generatedContract.addReward(
                "aotd_civ_supply", new MonthlyGrowthAndStabilityBoost(generatedContract));
    }

    @Override
    public LinkedHashSet<String> getAvailableCommoditiesForContract() {
        LinkedHashSet<String> stuff = new LinkedHashSet<>();
        stuff.add(Commodities.FOOD);
        stuff.add(Commodities.DOMESTIC_GOODS);
        return stuff;
    }

    @Override
    public void createContractExplanationSection(TooltipMakerAPI tooltip, float width) {
        tooltip.setParaFont(Fonts.ORBITRON_12);

        tooltip.addPara(
                "This contract establishes a directed internal supply program for civilian goods. Your faction creates additional subsidized demand for essential commodities.",
                5f);

        tooltip.addPara(
                "While these sales generate only a very small amount of tax revenue, they improve living conditions and support internal stability across your colonies.",
                3f);

        tooltip.addPara(
                "For %s, every time your faction's total demand is fully satisfied through this contract, stability is increased by %s across your colonies.",
                3f, Color.ORANGE, "Food", "+1");

        tooltip.addPara(
                "For %s, every time your faction's total demand is fully satisfied through this contract, colony growth is increased by %s across your colonies.",
                3f,
                Color.ORANGE,
                "Domestic Goods",
                growthPerMetDemandDomesticGoods + "+ growth points");
    }

    @Override
    public void createProcTooltipSection(
            TooltipMakerAPI tooltip, float width, float price, int amount, String commodity) {
        int demand =
                AoTDSectorProductionDemandDataUtils.getTotalDemandFromFactionIgnoreContracts(
                        commodity, Factions.PLAYER);

        tooltip.addPara(
                "This will generate %s in tax revenue.",
                3f, Color.ORANGE, Misc.getDGSCredits(price));

        if (demand <= 0) {
            tooltip.addPara(
                    "No external sector demand for %s is currently present, so this contract will not provide any additional empire-wide benefits at this time.",
                    3f, Color.ORANGE, commodity);
            return;
        }

        float fulfilledDemandCycles = amount / (float) demand;
        String fulfilledCycles = Misc.getRoundedValueMaxOneAfterDecimal(fulfilledDemandCycles);

        if (Commodities.FOOD.equals(commodity)) {
            float stabilityBonus = fulfilledDemandCycles * stabilityPerMetDemandFood;
            String stability = Misc.getRoundedValueMaxOneAfterDecimal(stabilityBonus);

            tooltip.addPara(
                    "Current total external demand for %s is %s units. This shipment fulfills %s demand cycles and will increase stability by %s across all colonies.",
                    3f, Color.ORANGE, "food", Misc.getWithDGS(demand), fulfilledCycles, stability);
        }

        if (Commodities.DOMESTIC_GOODS.equals(commodity)) {
            float growthBonus = fulfilledDemandCycles * growthPerMetDemandDomesticGoods;
            String growth = Misc.getRoundedValueMaxOneAfterDecimal(growthBonus);

            tooltip.addPara(
                    "Current total external demand for %s is %s units. This shipment fulfills %s demand cycles and will provide %s colony growth points across all colonies.",
                    3f,
                    Color.ORANGE,
                    "domestic goods",
                    Misc.getWithDGS(demand),
                    fulfilledCycles,
                    growth);
        }
    }

    @Override
    public float getYForExplainSection() {
        return 120;
    }
}
