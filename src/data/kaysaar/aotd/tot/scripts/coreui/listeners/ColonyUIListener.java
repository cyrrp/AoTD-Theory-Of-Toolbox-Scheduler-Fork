package data.kaysaar.aotd.tot.scripts.coreui.listeners;

import com.fs.starfarer.api.Global;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.log4j.Logger;

public final class ColonyUIListener {
    private static final Logger LOG = Global.getLogger(ColonyUIListener.class);
    private static final CopyOnWriteArrayList<MarketUIListener> MARKET_UI_LISTENERS =
            new CopyOnWriteArrayList<>();

    private ColonyUIListener() {}

    public static void addMarketListener(MarketUIListener l) {
        if (l != null) MARKET_UI_LISTENERS.addIfAbsent(l);
    }

    public static void removeMarketListener(MarketUIListener l) {
        MARKET_UI_LISTENERS.remove(l);
    }

    public static void notifyMarketOverview(IndustryPanelContextUI ctx) {
        for (MarketUIListener l : MARKET_UI_LISTENERS) {
            try {
                l.onMarketOverviewDiscovered(ctx);
            } catch (RuntimeException failure) {
                throw new RuntimeException(
                        "Market overview listener failed: " + l.getClass().getName(), failure);
            }
        }
    }

    public static void notifySurveyPanelOverview(SurveyPanelContextUI ctx) {
        for (MarketUIListener l : MARKET_UI_LISTENERS) {
            try {
                l.onSurveyPanelCreated(ctx);
            } catch (RuntimeException failure) {
                LOG.error("Survey panel listener failed: " + l.getClass().getName(), failure);
            }
        }
    }

    public static void notifyMarketOverview(CargoPanelContextUI ctx) {
        for (MarketUIListener l : MARKET_UI_LISTENERS) {
            try {
                l.onSubmarketCargoCreated(ctx);
            } catch (RuntimeException failure) {
                LOG.error("Submarket cargo listener failed: " + l.getClass().getName(), failure);
            }
        }
    }

    /** Re-register listeners on game load. */
    public static void refresh() {
        MARKET_UI_LISTENERS.clear();
        Global.getSettings().getModManager().getEnabledModPlugins().stream()
                .filter(x -> x instanceof MarketContextListenerInjector)
                .forEach(
                        x -> {
                            ((MarketContextListenerInjector) x).reloadListenerContext();
                        });
    }
}
