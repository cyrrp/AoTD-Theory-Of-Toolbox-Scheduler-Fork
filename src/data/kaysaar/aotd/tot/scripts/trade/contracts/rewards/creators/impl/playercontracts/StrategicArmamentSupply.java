package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly.MonthlyDefenceBoost;
import java.awt.*;
import java.util.LinkedHashSet;

public class StrategicArmamentSupply extends DirectedTradeInitiativeBaseContract {
    public static float shipsPerFleetSize = 1000f;
    public static float gunsPerDefense = 50f;

    @Override
    public String getNameOfContract() {
        return "Strategic Armament Program";
    }

    @Override
    public void onContractCreated(AoTDTradeContract generatedContract) {
        super.onContractCreated(generatedContract);
        generatedContract.addReward(
                "aotd_military_complex", new MonthlyDefenceBoost(generatedContract));
    }

    @Override
    public String getBaseIdForContract() {
        return "aotd_strategic_armament";
    }

    @Override
    public LinkedHashSet<String> getAvailableCommoditiesForContract() {
        LinkedHashSet<String> stuff = new LinkedHashSet<>();
        stuff.add(Commodities.SHIPS);
        stuff.add(Commodities.HAND_WEAPONS);
        return stuff;
    }

    @Override
    public void createContractExplanationSection(TooltipMakerAPI tooltip, float width) {
        tooltip.setParaFont(Fonts.ORBITRON_12);

        tooltip.addPara(
                "This contract establishes a directed internal procurement program for military goods. Your faction creates additional subsidized demand for domestically produced ships and weapons.",
                5f);

        tooltip.addPara(
                "While these sales generate only a very small amount of tax revenue, they strengthen naval readiness and defensive coordination across your empire.",
                3f);

        tooltip.addPara(
                "For every %s %s sold through this contract, fleet size is increased by %s across your colonies.",
                3f, Color.ORANGE, Misc.getWithDGS((int) shipsPerFleetSize), "Ship Hulls", "1%");

        tooltip.addPara(
                "For every %s %s sold through this contract, defensive capabilities are multiplied by %s across your colonies.",
                3f,
                Color.ORANGE,
                Misc.getWithDGS((int) gunsPerDefense),
                "Heavy Armaments ",
                "1 + 0.01");
    }

    @Override
    public void createProcTooltipSection(
            TooltipMakerAPI tooltip, float width, float price, int amount, String commodity) {
        tooltip.addPara(
                "This will generate %s in tax revenue.",
                3f, Color.ORANGE, Misc.getDGSCredits(price));

        if (Commodities.SHIPS.equals(commodity)) {
            float fleetSizeBonus = amount / shipsPerFleetSize;
            String percentage = Misc.getRoundedValueMaxOneAfterDecimal(fleetSizeBonus);

            tooltip.addPara(
                    "It will also increase fleet size by %s across all colonies.",
                    3f, Color.ORANGE, percentage + "%");
        }

        if (Commodities.HAND_WEAPONS.equals(commodity)) {
            float defenseBonus = 1 + ((amount / gunsPerDefense) * 0.01f);
            String percentage = String.format("%.2f", defenseBonus);

            tooltip.addPara(
                    "It will also increase defensive capabilities multiplier by %s across all colonies.",
                    3f, Color.ORANGE, percentage);
        }
    }

    @Override
    public float getYForExplainSection() {
        return 108;
    }
}
