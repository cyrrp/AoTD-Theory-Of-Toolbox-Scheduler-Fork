package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;

public class AoTDTradeContractRefresh implements EconomyTickListener {
    @Override
    public void reportEconomyTick(int iterIndex) {}

    @Override
    public void reportEconomyMonthEnd() {
        if (Global.getSector().getClock().getMonth() % 3 == 0) {
            AoTDTradeContractManager.getInstance().generateNewContractsForBrowser();
        }
    }
}
