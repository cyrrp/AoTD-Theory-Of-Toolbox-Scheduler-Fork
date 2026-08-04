package data.kaysaar.aotd.tot.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;

public interface AoTDDemSupCalc {
    public int getRawUnitsFromSupply(
            MutableStat supply, MarketAPI market, String commodityID, Industry ind);

    public int getRawUnitsFromDemand(
            MutableStat demand, MarketAPI market, String commodityID, Industry ind);

    public int convertRawUnitsToSupply(float units, MarketAPI market, String commodityID);

    public int convertRawUnitsToDemand(float units, MarketAPI market, String commodityID);
}
