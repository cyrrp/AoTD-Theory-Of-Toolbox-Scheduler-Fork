package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import com.fs.starfarer.api.util.IntervalUtil;

public class AoTDCoreUIListener implements CoreUITabListener, EveryFrameScript {
    /**
     * @deprecated Use {@link #isInCore()} for reads. Kept for binary compatibility.
     */
    @Deprecated public static volatile boolean isInCore = false;

    private final IntervalUtil util = new IntervalUtil(1f, 1f);

    public static boolean isInCore() {
        return isInCore;
    }

    public static void resetCampaignState() {
        isInCore = false;
    }

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId tab, Object param) {
        isInCore = true;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        util.advance(amount);
        if (util.intervalElapsed()) {
            isInCore = Global.getSector().getCampaignUI().getCurrentCoreTab() != null;
        }
    }
}
