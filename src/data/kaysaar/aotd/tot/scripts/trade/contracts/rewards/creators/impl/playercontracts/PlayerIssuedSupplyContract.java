package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly.MonthlyCommodityToStorageReward;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;

public class PlayerIssuedSupplyContract implements PlayerContractCreatorAPI {
    public static LinkedHashSet<String> blackList = new LinkedHashSet<>();
    public static LinkedHashSet<String> fullProdCommodities = new LinkedHashSet<>();
    public static float cut = 0.1f;

    static {
        blackList.add(Commodities.SHIPS);
    }

    @Override
    public void onContractCreated(AoTDTradeContract generatedContract) {
        MonthlyCommodityToStorageReward reward = new MonthlyCommodityToStorageReward();
        generatedContract.addReward("aotd_storage_reward", reward);
    }

    @Override
    public void applyChangesToContractIfNecessary(AoTDTradeContract contract) {
        for (AoTDTradeContract.TradeContractData value : contract.getContractData().values()) {
            int max = getMaxLimitForCommodityAmount(value.getCommodityId());
            int curr = Math.min(max, value.getReqMonthly());
            value.setReqMonthly(curr);
        }
        contract.runCleanUp();
    }

    @Override
    public String getNameOfContract() {
        return "Direct Procurement Contract";
    }

    @Override
    public String getBaseIdForContract() {
        return "aotd_personal_supply_contract";
    }

    @Override
    public LinkedHashSet<String> getAvailableCommoditiesForContract() {
        LinkedHashSet<String> available =
                new LinkedHashSet<>(
                        AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied());
        blackList.forEach(available::remove);
        available.removeIf(x -> getMaxLimitForCommodityAmount(x) <= 0);
        return available;
    }

    @Override
    public int getMaxLimitForCommodityAmount(String commodityId) {
        int totalProd =
                AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                        commodityId, Factions.PLAYER);
        if (fullProdCommodities.contains(commodityId)) {
            return totalProd;
        }
        return Math.round(totalProd * 0.1f);
    }

    @Override
    public boolean canUseContract() {
        boolean atLeastOnce = false;
        for (String s : getAvailableCommoditiesForContract()) {
            if (getMaxLimitForCommodityAmount(s) > 0) {
                atLeastOnce = true;
                break;
            }
        }
        return atLeastOnce;
    }

    @Override
    public float getCutToPayForCommodity(String commodityId) {
        return 1.2f;
    }

    @Override
    public int getMaxAmountOfConcurrentContracts() {
        return 1;
    }

    @Override
    public void createContractExplanationSection(TooltipMakerAPI tooltip, float width) {
        tooltip.setParaFont(Fonts.ORBITRON_12);

        tooltip.addPara(
                "This Contract enables you to receive selected commodities directly and have them transported to your designated gathering point.",
                5f);

        if (fullProdCommodities.isEmpty()) {
            tooltip.addPara(
                    "Each ordered unit costs %s of its original market value. You may request up to %s of your faction's total production of a given commodity, and only one %s may be active at any time.",
                    3f, Color.ORANGE, "150%", "10%", "Direct Procurement Contract");
        } else {
            StringBuilder builder = new StringBuilder();
            boolean coma = false;
            ArrayList<Color> colors = new ArrayList<>();
            colors.add(Color.ORANGE);
            colors.add(Color.ORANGE);
            colors.add(Misc.getPositiveHighlightColor());
            for (String fullProdCommodity : fullProdCommodities) {
                if (coma) builder.append(", ");
                builder.append(Global.getSettings().getCommoditySpec(fullProdCommodity).getName());
                colors.add(Color.ORANGE);
                coma = true;
            }
            colors.add(Color.ORANGE);

            LabelAPI labelAPI =
                    tooltip.addPara(
                            "Each ordered unit costs %s of its original market value. You may request up to %s of your faction's total production of a given commodity (%s for: %s), and only one %s may be active at any time.",
                            3f,
                            colors.toArray(new Color[0]),
                            "150%",
                            "10%",
                            "100%",
                            builder.toString(),
                            "Direct Procurement Contract");
        }
    }

    @Override
    public TooltipMakerAPI.TooltipCreator generateTooltipCreatorForButtonOnList(
            float widthOfTooltip) {
        return null;
    }

    @Override
    public boolean isContractUnlimited() {
        return false;
    }

    @Override
    public boolean canEditContract() {
        return true;
    }

    @Override
    public boolean useUnits() {
        return true;
    }

    @Override
    public void createProcTooltipSection(
            TooltipMakerAPI tooltip, float width, float price, int am, String commodity) {
        tooltip.addPara(
                "Procurement of this commodity will cost you : %s",
                3f, Color.ORANGE, Misc.getDGSCredits(price));
    }

    @Override
    public boolean isContractPaidByPlayer() {
        return true;
    }

    @Override
    public float getYForExplainSection() {
        return 60;
    }
}
