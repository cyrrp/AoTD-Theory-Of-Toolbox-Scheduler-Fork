package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.StrategicArmamentSupply;
import java.awt.*;

public class MonthlyDefenceBoost implements TradeContractRewardDataAPI {
    public static final String DEFENSE_MOD_ID = "aotd_armament_contract_defense";
    public static final String FLEET_MOD_ID = "aotd_armament_contract_fleet";

    protected AoTDTradeContract tiedContract;

    public MonthlyDefenceBoost(AoTDTradeContract tiedContract) {
        this.tiedContract = tiedContract;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        float fleetPercent =
                tiedContract.getMonthlyAmountNeeded(Commodities.SHIPS)
                        / StrategicArmamentSupply.shipsPerFleetSize;
        float defensePercent =
                tiedContract.getMonthlyAmountNeeded(Commodities.HAND_WEAPONS)
                        / StrategicArmamentSupply.gunsPerDefense;

        tooltip.addPara(
                "Increases fleet size across all colonies by %s.",
                3f, Color.ORANGE, Misc.getRoundedValueMaxOneAfterDecimal(fleetPercent) + "%");

        tooltip.addPara(
                "Increases defensive capabilities multiplier across all colonies by %s.",
                3f, Color.ORANGE, Misc.getRoundedValueMaxOneAfterDecimal(defensePercent) + "%");

        tooltip.addPara(
                "*These bonuses are applied while the contract is fully maintained. If supply falls short, the effects are reduced.*",
                Misc.getGrayColor(),
                3f);
    }

    @Override
    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {}

    @Override
    public void executeRewardAtTheEndOfContract() {}

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {
        for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
            marketAPI
                    .getStats()
                    .getDynamic()
                    .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .unmodifyFlat(FLEET_MOD_ID);
            marketAPI
                    .getStats()
                    .getDynamic()
                    .getMod(Stats.GROUND_DEFENSES_MOD)
                    .unmodifyMult(DEFENSE_MOD_ID);
        }
    }

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {
        if (Commodities.SHIPS.equals(commodityId)) {
            float percentage =
                    amountOfCommoditiesDeliveredThisMonth
                            / StrategicArmamentSupply.shipsPerFleetSize;
            percentage /= 100f;

            for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
                marketAPI
                        .getStats()
                        .getDynamic()
                        .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                        .modifyFlat(
                                FLEET_MOD_ID,
                                percentage,
                                tiedContract.getSubTypeOfContractString());
            }
        }

        if (Commodities.HAND_WEAPONS.equals(commodityId)) {
            float percentage =
                    amountOfCommoditiesDeliveredThisMonth / StrategicArmamentSupply.gunsPerDefense;
            percentage /= 100f;

            for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
                marketAPI
                        .getStats()
                        .getDynamic()
                        .getMod(Stats.GROUND_DEFENSES_MOD)
                        .modifyMult(
                                DEFENSE_MOD_ID,
                                1 + percentage,
                                tiedContract.getSubTypeOfContractString());
            }
        }
    }
}
