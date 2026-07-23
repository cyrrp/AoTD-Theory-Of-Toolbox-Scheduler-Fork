package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySourceType;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.campaign.econ.CommodityOnMarket;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.campaign.econ.reach.CommodityMarketData;
import com.fs.starfarer.campaign.econ.reach.MarketShareData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomySemanticBaseline;

import java.util.Iterator;

public class AoTDCommodityMarketData extends CommodityMarketData {
    public AoTDCommodityMarketData(String commodityId, String econGroup) {
        super(commodityId, econGroup);

        try (AoTDEconomySemanticBaseline.Scope ignored =
                     AoTDEconomySemanticBaseline.begin(
                             "commodity-market-data.constructor", null,
                             commodityId + "|" + (econGroup == null ? "<global>" : econGroup))) {
        Iterator iter = Global.getSector().getEconomy().getMarketsInGroup(econGroup).iterator();
        while (iter.hasNext()) {
            Market market = (Market) iter.next();
            CommodityOnMarket commodityOnMarket  =  market.getCommodityData(commodityId);

            float stockPile = 0;
            if(commodityOnMarket instanceof AoTDCommodityOnMarket commodity){
                stockPile+=Math.max(commodity.getSupplyDemandData().getTotalRawUnitsFromDemand(),commodity.getSupplyDemandData().getTotalRawUnitsFromSupply());

            }
            if(stockPile>0 && commodityOnMarket instanceof  AoTDCommodityOnMarket aoTDCommodityOnMarket){
                if(market.getId().equals("jangala")&&commodityId.equals(Commodities.SUPPLIES)){
                    String he= "he";
                }
                AoTDAvailableStat var40 = (AoTDAvailableStat) commodityOnMarket.getAvailableStat();
                var40.unmodifyFlat(KEY_LOCAL);
                var40.unmodifyFlat(KEY_SHORTAGE);
                var40.unmodifyFlat(KEY_IMPORTS);
                var40.unmodifyFlat(KEY_LOWACCESS);
                float changesMinus = 0;
                float changesPlus = 0;
                for (String s : var40.getFlatMods().keySet()) {
                    if(s.equals("aotd_local"))continue;
                    if(var40.getFlatStatMod(s).getValue()<0){
                        MutableStat stat = new MutableStat(-var40.getFlatStatMod(s).getValue());
                        aoTDCommodityOnMarket.setDef(aoTDCommodityOnMarket.getSupplyDemandData().getEconSpec().getCalculationScript().getRawUnitsFromDemand(stat,null,commodityId,null),30,s,var40.getFlatStatMod(s).desc);
                    }
                }
                var40.unmodify();


                stockPile-=aoTDCommodityOnMarket.getDeficitQuantity();
                stockPile+=aoTDCommodityOnMarket.getExcessQuantity();



                int am = aoTDCommodityOnMarket.getSupplyDemandData().getAvailableOnThisMarket(stockPile,market,commodityId);

                MarketShareData var33 = this.getMarketShareData(market);
                var33.setSource(CommoditySourceType.LOCAL);
                var40.modifyFlat("aotd_local",am,"Ashes of the Domain Stockpile script");
            }


        }
        }
    }

    @Override
    public int getExportIncome(CommodityOnMarketAPI commodityOnMarketAPI) {
        if(commodityOnMarketAPI instanceof AoTDCommodityOnMarket ){
            return AoTDTradeManager.getExportIncome(commodityOnMarketAPI);
        }
        return super.getExportIncome(commodityOnMarketAPI);
    }
}
