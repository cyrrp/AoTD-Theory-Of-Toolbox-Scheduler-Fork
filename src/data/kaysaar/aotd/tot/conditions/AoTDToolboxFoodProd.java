package data.kaysaar.aotd.tot.conditions;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.Farming;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;

import java.util.ArrayList;
import java.util.Map;

public class AoTDToolboxFoodProd extends BaseMarketConditionPlugin {
    public static String prodId = "aotd_toolbox_food_prod";
    public static String prodId2 = "aotd_toolbox_food_prod_null_shortage";
    public static String deficitS = "aotd_toolbox_food_prod_null_shortage";

    @Override
    public boolean showIcon() {
        return false;
    }
    private static int computeNewProd(int total) {
        if (total <= 0) return 0;

        // Fast growth region: exact same as your current formula
        if (total <= 10) {
            return (int) Math.round(Math.pow(2.0, total) - total);
        }

        // Anchor at t=10 so curve is continuous
        double baseAt10 = Math.pow(2.0, 10) - 10; // 1014

        int extra = total - 10;

        // Diminishing returns: logarithmic growth
        // "tailScale" controls how much extra you get beyond the baseAt10.
        double tailScale = 300.0; // tune 150..600
        double tail = tailScale * (Math.log1p(extra) / Math.log(2.0)); // log2(1+extra)

        // Optional: keep your "subtract linear part" feel, but mild:
        // This prevents very large totals from inflating too much.
        double linearPenaltyPerPoint = 1.0; // tune 0..3
        double penalty = linearPenaltyPerPoint * extra;

        double result = baseAt10 + tail - penalty;
        if (result < 0) result = 0;

        return (int) Math.round(result);
    }

    @Override
    public void apply(String id) {
        boolean conditionStructureMutation =
                !market.hasCondition("aotd_toolbox_food_corrector_start")
                        || market.getConditions().get(market.getConditions().size() - 1) != this.condition;
        if (!conditionStructureMutation) {
            applyWithoutConditionBoundary(id);
            return;
        }
        long token = SchedulerBridge.beforeMarketMutation(
                market, SchedulerBridge.MUTATION_CONDITION_STRUCTURE);
        try {
            applyWithoutConditionBoundary(id);
        } finally {
            SchedulerBridge.afterMarketMutation(token, market,
                    SchedulerBridge.DIRTY_STRUCTURE
                            | SchedulerBridge.DIRTY_CONDITIONS
                            | SchedulerBridge.DIRTY_DERIVED_ECONOMY,
                    0L);
        }
    }

    private void applyWithoutConditionBoundary(String id) {
        super.apply(id);
        if(!market.hasCondition("aotd_toolbox_food_corrector_start")){
            ArrayList<MarketConditionAPI>conditionAPIS = new ArrayList<>(market.getConditions());
            market.getConditions().clear();
            market.addCondition("aotd_toolbox_food_corrector_start");
            market.getConditions().addAll(conditionAPIS);
        }
        if(market.getConditions().get(market.getConditions().size()-1)!=this.condition){
            //Again covering unknown skies
            market.getConditions().remove(this.condition);
            market.getConditions().add(this.condition);
        }

        for (Industry industry : market.getIndustries()) {
            if(industry.getSpec().hasTag(Industries.FARMING)||industry.getSpec().hasTag("aquaculture")||industry instanceof Farming){

                industry.getSupply(Commodities.FOOD).getQuantity().unmodify(prodId);
                int total = getSupplyFromIndustry(industry,Commodities.FOOD);
                int newTotal = (int) Math.round(total*Math.pow(2,market.getSize()-1));
                int toAdd =Math.max( newTotal-total,0);
                industry.getSupply(Commodities.FOOD).getQuantity().modifyFlatAlways(prodId, toAdd,"AoTD corrector");
                industry.getDemand(Commodities.HEAVY_MACHINERY).getQuantity().modifyMultAlways(prodId, Math.round(0.3f*Math.pow(2,market.getSize()-1)),"AoTD corrector");
            }

        }
    }
    public int getSupplyFromIndustry(Industry industry,String comId){
        MutableStat stat = industry.getSupply(comId).getQuantity();
        int total = 0;
        float currPenalty = 1f;
        for (MutableStat.StatMod object : stat.getFlatMods().values()) {
            if(object.getDesc()==null||!object.getDesc().contains("shortage")){
                total += (int) object.getValue();
            } else if (object.getDesc()!=null&&object.getDesc().contains("shortage")&&object.getDesc().contains("Heavy machinery")) {
                int totalDemanded = industry.getDemand(Commodities.HEAVY_MACHINERY).getQuantity().getModifiedInt();
                int missed = industry.getMaxDeficit(Commodities.HEAVY_MACHINERY).two;
                int delivered = Math.max(0,totalDemanded-missed);
                float ratio = (float) (delivered) /totalDemanded;
                if(ratio<=currPenalty){
                    currPenalty = ratio;
                }

            }
        }
        if(currPenalty<=0.2f){
            currPenalty = 0.2f;
        }
        return Math.round(currPenalty*total);

    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
    }

}
