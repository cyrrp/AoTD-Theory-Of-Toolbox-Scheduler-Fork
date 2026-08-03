package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;

public class AoTDAvailableStat extends MutableStatWithTempMods {
    AoTDExcDefData data = new AoTDExcDefData();
    private AoTDSupplyDemandData supplyDemandData;

    public AoTDSupplyDemandData getSupplyDemandData(CommodityOnMarketAPI commodity) {
        if (supplyDemandData == null) {
            supplyDemandData = new AoTDSupplyDemandData(commodity.getId());
            supplyDemandData.getEconSpec();
            supplyDemandData.updateSupplyDemandData(commodity.getMarket());
        }

        return supplyDemandData;
    }

    /** Returns the last published object without creating or refreshing it. */
    public AoTDSupplyDemandData peekSupplyDemandData() {
        return supplyDemandData;
    }

    public AoTDExcDefData getData() {
        return data;
    }

    public AoTDAvailableStat(float base) {
        super(base);
    }

    @Override
    public void advance(float days) {
        super.advance(days);
        data.advance(days);
    }
}
