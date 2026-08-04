package data.kaysaar.aotd.tot.conditions;

import static data.kaysaar.aotd.tot.conditions.AoTDToolboxFoodProd.prodId;
import static data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy.pruneCommoditiesThatMightAppear;
import static data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy.runningPrePlayerEconomy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.campaign.econ.Market;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.plugins.AoTDToolboxTheoryPlugin;
import java.util.ArrayList;

public class AoTDToolboxFoodProdCanceler extends BaseMarketConditionPlugin {
    // This should cover Unknown skies issues due to food prod being only exp scaling
    public boolean showIcon() {
        return false;
    }

    @Override
    public void apply(String id) {
        boolean conditionOrderMutation = market.getConditions().get(0) != this.condition;
        boolean commodityStructureMutation =
                (Global.LOADING_SAVE && AoTDToolboxTheoryPlugin.afterSaveState)
                        || runningPrePlayerEconomy;
        if (!conditionOrderMutation && !commodityStructureMutation) {
            applyWithoutStructureBoundary(id);
            return;
        }
        int reason = 0;
        int dirty = SchedulerBridge.DIRTY_STRUCTURE | SchedulerBridge.DIRTY_DERIVED_ECONOMY;
        if (conditionOrderMutation) {
            reason |= SchedulerBridge.MUTATION_CONDITION_STRUCTURE;
            dirty |= SchedulerBridge.DIRTY_CONDITIONS;
        }
        if (commodityStructureMutation) {
            reason |= SchedulerBridge.MUTATION_COMMODITY_STRUCTURE;
            dirty |=
                    MarketRegistry.DIRTY_VALUE_STATE
                            | MarketRegistry.DIRTY_PRICE
                            | MarketRegistry.DIRTY_STOCKPILE;
        }
        long token = SchedulerBridge.beforeMarketMutation(market, reason);
        try {
            applyWithoutStructureBoundary(id);
        } finally {
            SchedulerBridge.afterMarketMutation(token, market, dirty, 0L);
        }
    }

    private void applyWithoutStructureBoundary(String id) {
        super.apply(id);
        if (market.getConditions().get(0) != this.condition) {
            ArrayList<MarketConditionAPI> conditionAPIS = new ArrayList<>(market.getConditions());
            market.getConditions().clear();
            market.getConditions().add(this.condition);
            for (MarketConditionAPI conditionAPI : conditionAPIS) {
                if (conditionAPI != this.condition) {
                    market.getConditions().add(conditionAPI);
                }
            }
        }

        if ((Global.LOADING_SAVE && AoTDToolboxTheoryPlugin.afterSaveState)
                || runningPrePlayerEconomy) {
            pruneCommoditiesThatMightAppear((Market) market);
        }
        for (Industry industry : market.getIndustries()) {
            industry.getSupply(Commodities.FOOD).getQuantity().unmodify(prodId);
            industry.getDemand(Commodities.HEAVY_MACHINERY).getQuantity().unmodify(prodId);
        }
    }
}
