package data.kaysaar.aotd.tot.plugins;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.combat.MutableStat;

public interface AoTDSupDemListener {
    void applyEffectsOnMutableStatCopySupply(MutableStat stat, Industry ind, String commodityId);

    void applyEffectsOnMutableStatCopyDemand(MutableStat stat, Industry ind, String commodityId);
}
