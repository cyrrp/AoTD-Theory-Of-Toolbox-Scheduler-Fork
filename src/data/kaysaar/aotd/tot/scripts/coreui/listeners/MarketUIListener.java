package data.kaysaar.aotd.tot.scripts.coreui.listeners;

public interface MarketUIListener {
    void onMarketOverviewDiscovered(IndustryPanelContextUI ctx);

    void onSubmarketCargoCreated(CargoPanelContextUI ctx);

    void onSurveyPanelCreated(SurveyPanelContextUI ctx);
}
