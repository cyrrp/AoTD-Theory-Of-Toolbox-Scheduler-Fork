package data.kaysaar.aotd.tot.ui.economy.tradecontracts.popup;

import ashlib.data.plugins.ui.models.PopUpUI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.DetailedTradeContractUI;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.TradeContractUITable;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.browser.ContractBrowsingPanelPlugin;

public class ContractDataPopUP extends PopUpUI {
    AoTDTradeContract contract;
    CustomPanelAPI mainPanel;
    DetailedTradeContractUI tradeContractUI;
    TradeContractUITable table;

    public ContractDataPopUP(AoTDTradeContract contract, TradeContractUITable tableref) {
        this.contract = contract;
        this.table = tableref;
    }

    @Override
    public float createUIMockup(CustomPanelAPI panelAPI) {
        mainPanel =
                Global.getSettings()
                        .createCustom(
                                panelAPI.getPosition().getWidth(),
                                panelAPI.getPosition().getHeight(),
                                null);
        tradeContractUI =
                new DetailedTradeContractUI(
                        panelAPI.getPosition().getWidth(),
                        panelAPI.getPosition().getHeight() - 5,
                        contract,
                        true);
        mainPanel.addComponent(tradeContractUI.getMainPanel()).inTL(0, 5);
        return mainPanel.getPosition().getHeight();
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createUIMockup(panelAPI);
        panelAPI.addComponent(mainPanel).inTL(0, 0);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (tradeContractUI != null) {
            if (tradeContractUI.accept.isChecked()) {
                tradeContractUI.accept.setChecked(false);
                AoTDTradeContractManager.getInstance()
                        .getCurrentlyGeneratedInBrowser()
                        .remove(contract.getId());
                AoTDTradeContractManager.getInstance().addContract(contract);
                ContractBrowsingPanelPlugin.updateUIStuff = true;
                table.recreateTable();
                forceDismiss();
            }
        }
    }
}
