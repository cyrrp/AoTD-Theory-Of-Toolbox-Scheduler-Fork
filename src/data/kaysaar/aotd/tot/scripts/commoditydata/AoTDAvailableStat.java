package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;

public class AoTDAvailableStat extends MutableStatWithTempMods {
    AoTDExcDefData data = new AoTDExcDefData();
    private AoTDSupplyDemandData supplyDemandData;

    public AoTDSupplyDemandData getSupplyDemandData(CommodityOnMarketAPI commodity) {
        if (supplyDemandData == null) {
            supplyDemandData = getOrCreateSupplyDemandDataWithoutRefresh(commodity);
            supplyDemandData.updateSupplyDemandData(commodity.getMarket());
        }

        return supplyDemandData;
    }

    /**
     * Creates the derived-state holder without consulting live industry supply/demand maps.
     *
     * <p>Save restoration uses this path while Starsector is rebuilding those maps one industry at
     * a time. The normal scheduler later prepares and publishes one complete market revision.
     */
    public AoTDSupplyDemandData getOrCreateSupplyDemandDataWithoutRefresh(
            CommodityOnMarketAPI commodity) {
        if (supplyDemandData == null) {
            supplyDemandData = new AoTDSupplyDemandData(commodity.getId());
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
