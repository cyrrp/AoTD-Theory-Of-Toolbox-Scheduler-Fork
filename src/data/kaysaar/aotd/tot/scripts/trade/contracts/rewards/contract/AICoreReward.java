// file: data/kaysaar/aotd/tot/scripts/trade/contracts/rewards/contract/AICoreReward.java
package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class AICoreReward implements TradeContractRewardDataAPI {

    public static LinkedHashMap<String, Float> validAICoresMapWeight = new LinkedHashMap<>();

    static {
        validAICoresMapWeight.put(Commodities.ALPHA_CORE, 0.10f);
        validAICoresMapWeight.put(Commodities.BETA_CORE, 0.30f);
        validAICoresMapWeight.put(Commodities.GAMMA_CORE, 0.60f);
    }

    private final LinkedHashMap<String, Integer> aiCoresToGive = new LinkedHashMap<>();

    public AICoreReward() {
        Random r = Misc.random;

        int level = 1;
        if (AoTDTradeContractManager.getInstance() != null
                && AoTDTradeContractManager.getInstance().getCurrLevelData() != null) {
            level = AoTDTradeContractManager.getInstance().getCurrLevelData().getCurrentLevel();
        }

        // total cores: 3..5 as level goes up (you can tweak thresholds easily)
        int total;
        if (level >= 13) total = 5;
        else if (level >= 10) total = 4;
        else total = 3;

        // Level bias: slightly more Alpha/Beta at higher level, still mostly Gamma.
        // t = 0 at lvl7, t = 1 at lvl15
        float t = (level - 7f) / 8f;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;

        float alphaW =
                validAICoresMapWeight.get(Commodities.ALPHA_CORE) * (1f + 2.5f * t); // up to ~3.5x
        float betaW =
                validAICoresMapWeight.get(Commodities.BETA_CORE) * (1f + 1.0f * t); // up to ~2x
        float gammaW =
                validAICoresMapWeight.get(Commodities.GAMMA_CORE) * (1f - 0.35f * t); // down to 65%

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(r);
        picker.add(Commodities.ALPHA_CORE, Math.max(0.0001f, alphaW));
        picker.add(Commodities.BETA_CORE, Math.max(0.0001f, betaW));
        picker.add(Commodities.GAMMA_CORE, Math.max(0.0001f, gammaW));

        for (int i = 0; i < total; i++) {
            String core = picker.pick();
            if (core == null) core = Commodities.GAMMA_CORE;
            aiCoresToGive.put(core, aiCoresToGive.getOrDefault(core, 0) + 1);
        }

        // Optional tiny “bonus” roll at high levels (still rare)
        if (level >= 15 && r.nextFloat() < 0.10f) {
            aiCoresToGive.put(
                    Commodities.ALPHA_CORE,
                    aiCoresToGive.getOrDefault(Commodities.ALPHA_CORE, 0) + 1);
        }
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        for (Map.Entry<String, Integer> entry : aiCoresToGive.entrySet()) {
            tooltip.addPara(
                    "Gain %s %s",
                    defaultOpadText,
                    Color.ORANGE,
                    entry.getValue() + "x ",
                    Global.getSettings().getCommoditySpec(entry.getKey()).getName());
        }
    }

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
            for (Map.Entry<String, Integer> e : aiCoresToGive.entrySet()) {
                market.getSubmarket(Submarkets.SUBMARKET_STORAGE)
                        .getCargo()
                        .addCommodity(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {}

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {}
}
