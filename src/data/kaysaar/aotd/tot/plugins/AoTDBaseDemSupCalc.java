package data.kaysaar.aotd.tot.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import data.kaysaar.aotd.tot.scripts.economy.AoTDUpdateMarketAgainTask;
import java.util.Map;

public class AoTDBaseDemSupCalc implements AoTDDemSupCalc {
    public static float econMult = 0.3f;
    private static final float LN2_INV = (float) (1.0 / Math.log(2.0));

    @Override
    public int getRawUnitsFromSupply(
            MutableStat supply, MarketAPI market, String commodityID, Industry ind) {
        if (supply.getModifiedInt() <= 0) return 0;
        MutableStat statSupply = supply.createCopy();
        if (statSupply.getMult() == 0) return 0;
        for (AoTDSupDemListener listener : AoTDCommodityEconSpecManager.getListeners()) {
            listener.applyEffectsOnMutableStatCopySupply(statSupply, ind, commodityID);
        }
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityID);
        ;
        return (int)
                Math.floor(
                        AoTDCommodityEconSpecManager.getEconSpec(commodityID).getSupplyMult()
                                * statSupply.getModifiedInt()
                                * spec.getEconUnit()
                                * econMult);
    }

    @Override
    public int getRawUnitsFromDemand(
            MutableStat dem, MarketAPI market, String commodityID, Industry ind) {
        MutableStat statDem = dem.createCopy();
        for (AoTDSupDemListener listener : AoTDCommodityEconSpecManager.getListeners()) {
            listener.applyEffectsOnMutableStatCopyDemand(statDem, ind, commodityID);
        }
        int effective = statDem.getModifiedInt();
        if (statDem.getMult() == 0) return 0;
        for (Map.Entry<String, MutableStat.StatMod> entry : statDem.getFlatMods().entrySet()) {
            if (entry.getValue().getValue() < 0) {
                int howMuch = (int) -entry.getValue().getValue();
                if (howMuch >= AoTDUpdateMarketAgainTask.getReduction() - 1) {
                    effective += AoTDUpdateMarketAgainTask.getReduction();
                }
            }
        }
        if (effective <= 0) return 0;
        float supplyMultComd =
                AoTDCommodityEconSpecManager.getEconSpec(commodityID).getDemandMult();
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityID);
        ;
        return (int) Math.floor(supplyMultComd * effective * spec.getEconUnit() * econMult);
    }

    @Override
    public int convertRawUnitsToSupply(float units, MarketAPI market, String commodityID) {

        if (units <= 0) return 0;

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityID);
        float supplyMult = AoTDCommodityEconSpecManager.getEconSpec(commodityID).getSupplyMult();

        float denom = supplyMult * spec.getEconUnit() * econMult;

        if (denom <= 0f) return 0;

        float base = units / denom;

        if (base <= 0f) return 0;

        // ---------- LINEAR MODE ----------
        return (int) Math.floor(base);
    }

    @Override
    public int convertRawUnitsToDemand(float units, MarketAPI market, String commodityID) {

        if (units <= 0) return 0;

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityID);
        float demandMult = AoTDCommodityEconSpecManager.getEconSpec(commodityID).getDemandMult();

        float denom = demandMult * spec.getEconUnit() * econMult;

        if (denom <= 0f) return 0;

        float base = units / denom;

        if (base <= 0f) return 0;

        // ---------- LINEAR MODE ----------
        return (int) Math.floor(base);
    }
}
