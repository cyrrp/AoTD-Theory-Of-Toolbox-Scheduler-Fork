package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;

import java.util.LinkedHashSet;
import java.util.Map;

public class AoTDExcDefData {


    public MutableStatWithTempMods excess = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods deficit = new MutableStatWithTempMods(0f);
    public float recordedDemandFromNonPendingThisMonth = 0f;
    public int deficitConsequtiveMonths = 0;
    public void recordDemandForThisMonth(AoTDCommodityOnMarket commodity){
        this.recordedDemandFromNonPendingThisMonth = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
    }

    public int stockPileRecordedWhenEventHappened = 0;

    public int getDeficitConsequtiveMonths() {
        return deficitConsequtiveMonths;
    }

    public int getExcess() {
        return excess.getModifiedInt();
    }
    public static final String EXT_TRADE_ID = "aotd_ext_trade";
    public static final String DEF_FROM_NEW_IND = "aotd_new_ind_demand";
    public void clearExternalTrade(AoTDCommodityOnMarket commodity) {
        int excessFromTrade = commodity.getExcessQuantityFromTrade();
        if(excessFromTrade>0){
            /*
             * When player-created excess is converted into local resources, the
             * live trade mods are cleared below. Remember that this market/commodity
             * has player-sold stock, otherwise the player can dump a huge amount,
             * let it become excess, and buy it back below what they were paid.
             */
            try {
                if (commodity.getMarket() != null && commodity.getMarket().getMemoryWithoutUpdate() != null) {
                    commodity.getMarket().getMemoryWithoutUpdate().set(
                            EffectivePriceCalculator.LOCAL_PLAYER_TRADE_MEMORY_PREFIX + commodity.getId(),
                            Math.max(1f, excessFromTrade * Math.max(0.0001f, commodity.getUtilityOnMarket())),
                            31f
                    );
                }
            } catch (Throwable ignored) {
            }

            if(commodity.getMarket().hasSubmarket(Submarkets.LOCAL_RESOURCES)){
                if(commodity.getMarket().getSubmarket(Submarkets.LOCAL_RESOURCES).getCargo()!=null){
                    commodity.getMarket().getSubmarket(Submarkets.LOCAL_RESOURCES).getCargo().addCommodity(commodity.getId(),commodity.getExcessQuantity());
                    commodity.getTradeMod().unmodify();
                    commodity.getTradeModPlus().unmodify();
                    commodity.getTradeModMinus().unmodify();
                }
            }
        }
        if(deficit.getModifiedValue()>0){
            deficitConsequtiveMonths++;
        }
        else{
            deficitConsequtiveMonths = 0;
        }
        deficit.removeTemporaryMod(EXT_TRADE_ID);
        LinkedHashSet<String>toRemove = new LinkedHashSet<>();
        for (Map.Entry<String, MutableStat.StatMod> flatMod : deficit.getFlatMods().entrySet()) {
            if(flatMod.getKey().contains("aotd_shortage_counter")){
                toRemove.add(flatMod.getKey());
            }
        }
        toRemove.forEach(x->deficit.removeTemporaryMod(x));
        deficit.removeTemporaryMod(DEF_FROM_NEW_IND);
        excess.removeTemporaryMod(EXT_TRADE_ID);
    }public void applyExternalTrade(int deficitAmt, int excessAmt, float days, AoTDCommodityOnMarket com) {
        if (deficitAmt > 0) deficit.addTemporaryModFlat(days, EXT_TRADE_ID,"Unable to import due to Global Deficit.", deficitAmt);
        else deficit.removeTemporaryMod(EXT_TRADE_ID);

        if (excessAmt > 0) {
            if(com.getSpec().hasTag(AoTDTradeTags.AOTD_DOES_NOT_HAVE_EXCESS)){
                return;
            }
            else{
                if(com.getSpec().hasTag(AoTDTradeTags.AOTD_NO_ONE_BUYS_OUTSIDE)){
                    excess.addTemporaryModFlat(days, EXT_TRADE_ID,"Unable to export due to Global Excess.", excessAmt);
                }
                else{
                    int soldMax = excessAmt/2;
                    AoTDTradeManager.getInstance().getMarketData(com.getMarket()).addExtraSold(com.getSpec().getId(),soldMax);
                    excess.addTemporaryModFlat(days, EXT_TRADE_ID,"Unable to export due to Global Excess.", soldMax);

                }


            }


        }

        else excess.removeTemporaryMod(EXT_TRADE_ID);
    }
    /**
     * Builds the sudden-demand change without mutating the live commodity.
     * Stage 8.2 carries this proposal with the price snapshot and only applies
     * it after the work ticket has passed generation validation.
     */
    public PreparedSuddenDemandUpdate prepareDeficitDueToSuddenChangeOfDemand(
            AoTDCommodityOnMarket commodity) {
        int currentDemand = commodity.getSupplyDemandData()
                .getDemandExceptPendingIndustries(commodity.getMarket());
        int recorded = (int) recordedDemandFromNonPendingThisMonth;

        boolean recordOnly = AoTDEconomy.runningPrePlayerEconomy
                || (!commodity.getMarket().isPlayerOwned()
                && Global.getSector().getClock().getMonth() <= 3
                && Global.getSector().getClock().getCycle() <= 206);

        int oldModifier = getFlatModifierInt(deficit, DEF_FROM_NEW_IND);
        int nextModifier = oldModifier;
        boolean replaceModifier = false;
        if (!recordOnly) {
            int diff = currentDemand - recorded;
            nextModifier = Math.max(0, diff);
            replaceModifier = true;
        }

        int projectedDeficitRaw = deficit.getModifiedInt();
        if (replaceModifier) {
            projectedDeficitRaw = projectedDeficitRaw - oldModifier + nextModifier;
        }
        int projectedEffectiveDeficit = Math.max(
                0, projectedDeficitRaw - excess.getModifiedInt());
        int projectedEffectiveExcess = Math.max(
                0, excess.getModifiedInt() - projectedDeficitRaw);

        return new PreparedSuddenDemandUpdate(
                this, currentDemand, nextModifier, replaceModifier,
                projectedEffectiveDeficit, projectedEffectiveExcess);
    }

    /** Applies a previously prepared change after generation validation. */
    public void commitPreparedSuddenDemandUpdate(
            PreparedSuddenDemandUpdate prepared) {
        if (prepared == null || prepared.owner != this) {
            throw new IllegalArgumentException(
                    "Prepared sudden-demand update belongs to another owner");
        }
        recordedDemandFromNonPendingThisMonth = prepared.recordedDemand;
        if (!prepared.replaceModifier) return;
        if (prepared.modifierAmount > 0) {
            deficit.addTemporaryModFlat(
                    31, DEF_FROM_NEW_IND, "Sudden surge of demand",
                    prepared.modifierAmount);
        } else {
            deficit.removeTemporaryMod(DEF_FROM_NEW_IND);
        }
    }

    /** Compatibility entry point for non-price callers. */
    public void applyDeficitDueToSuddenChangeOfDemand(
            AoTDCommodityOnMarket commodity) {
        commitPreparedSuddenDemandUpdate(
                prepareDeficitDueToSuddenChangeOfDemand(commodity));
    }

    private static int getFlatModifierInt(
            MutableStat stat, String modifierId) {
        MutableStat.StatMod mod = stat.getFlatMods().get(modifierId);
        return mod == null ? 0 : Math.round(mod.value);
    }

    public static final class PreparedSuddenDemandUpdate {
        private final AoTDExcDefData owner;
        public final int recordedDemand;
        public final int modifierAmount;
        public final boolean replaceModifier;
        public final int projectedEffectiveDeficit;
        public final int projectedEffectiveExcess;

        private PreparedSuddenDemandUpdate(
                AoTDExcDefData owner,
                int recordedDemand,
                int modifierAmount,
                boolean replaceModifier,
                int projectedEffectiveDeficit,
                int projectedEffectiveExcess) {
            this.owner = owner;
            this.recordedDemand = recordedDemand;
            this.modifierAmount = modifierAmount;
            this.replaceModifier = replaceModifier;
            this.projectedEffectiveDeficit = projectedEffectiveDeficit;
            this.projectedEffectiveExcess = projectedEffectiveExcess;
        }
    }



    /** Adds/refreshes a temporary excess mod. */
    public void setExcess(int excessAmount, AoTDCommodityOnMarket commodity, float days, String id) {
        this.excess.addTemporaryModFlat(days, id,"Unable to export due to Global Excess.", excessAmount);
    }

    public int getDeficit() {
        return deficit.getModifiedInt();
    }

    /** Adds/refreshes a temporary deficit mod. */
    public void setDeficit(int deficitAmount, AoTDCommodityOnMarket commodity, float days, String id) {
        this.deficit.addTemporaryModFlat(days, id,"Unable to import due to Global Deficit.", deficitAmount);
    }

    public int setstockPileRecordedWhenEventHappened(int stockPile) {
        return stockPileRecordedWhenEventHappened;
    }

    /** Clears ALL mods (use carefully). */
    public void reset() {
        deficit.unmodifyFlat(EXT_TRADE_ID);
        excess.unmodify(EXT_TRADE_ID);
    }

    public void resetDeficit() {
        deficit.unmodify();
    }

    public void resetExcess() {
        excess.unmodify();
    }



    public int getStockPileRecordedWhenEventHappened() {
        return stockPileRecordedWhenEventHappened;
    }

    public int getEffectiveDeficit(AoTDCommodityOnMarket commodity) {
        return (int) Math.max(0, deficit.getModifiedInt()-excess.getModifiedInt());
    }

    public int getEffectiveExcess(AoTDCommodityOnMarket commodity) {
        return (int) Math.max(0, excess.getModifiedInt()-deficit.getModifiedInt());
    }

    public void advance(float days) {
        excess.advance(days);
        deficit.advance(days);
    }
}
