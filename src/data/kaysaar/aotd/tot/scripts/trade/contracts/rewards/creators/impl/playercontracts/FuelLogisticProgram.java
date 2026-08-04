package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly.MonthlyFuelAccessibilityBoost;
import java.awt.*;
import java.util.LinkedHashSet;

public class FuelLogisticProgram extends DirectedTradeInitiativeBaseContract {
    public static float perFuel = 2000;

    @Override
    public String getNameOfContract() {
        return "Fuel Logistic Program";
    }

    @Override
    public void onContractCreated(AoTDTradeContract generatedContract) {
        super.onContractCreated(generatedContract);
        generatedContract.addReward(
                "aotd_fuel", new MonthlyFuelAccessibilityBoost(generatedContract));
    }

    @Override
    public String getBaseIdForContract() {
        return "aotd_fuel_logistic";
    }

    @Override
    public LinkedHashSet<String> getAvailableCommoditiesForContract() {
        LinkedHashSet<String> stuff = new LinkedHashSet<>();
        stuff.add(Commodities.FUEL);
        return stuff;
    }

    @Override
    public void createContractExplanationSection(TooltipMakerAPI tooltip, float width) {
        tooltip.setParaFont(Fonts.ORBITRON_12);

        tooltip.addPara(
                "This contract establishes a directed internal trade program for fuel. Your faction creates additional subsidized demand for domestic fuel production.",
                5f);

        tooltip.addPara(
                "While these sales generate only a very small amount of tax revenue, they improve logistical integration across your empire.",
                3f);

        tooltip.addPara(
                "For every %s %s sold through this contract, accessibility is increased by %s across your colonies.",
                3f, Color.ORANGE, Misc.getWithDGS(perFuel), "Fuel", "1%");
    }

    @Override
    public void createProcTooltipSection(
            TooltipMakerAPI tooltip, float width, float price, int am, String commodity) {
        float accessibility = am / 2000f;
        String percentage = Misc.getRoundedValueMaxOneAfterDecimal(accessibility);
        tooltip.addPara(
                "This will generate %s in tax revenue and increase accessibility by %s across all colonies.",
                3f, Color.ORANGE, Misc.getDGSCredits(price), percentage + "%");
    }

    @Override
    public float getYForExplainSection() {
        return 80;
    }
}
