package data.kaysaar.aotd.tot.ui.economy;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.TradeContractFactionData;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.TradeContractUITable;
import java.util.List;

public class EconomyTradeDealsData implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;

    TradeContractUITable table;
    TradeContractFactionData data;

    public EconomyTradeDealsData(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        AoTDTradeContractManager.getInstance()
                .getActiveContracts()
                .values()
                .forEach(
                        x -> {
                            if (x.isIssuedByPlayer()
                                    && AoTDPlayerContractCreatorManager.getCreator(
                                                    x.getContractTypeId())
                                            != null) {
                                PlayerContractCreatorAPI creatorAPI =
                                        AoTDPlayerContractCreatorManager.getCreator(
                                                x.getContractTypeId());
                                creatorAPI.applyChangesToContractIfNecessary(x);
                            }
                        });
        AoTDTradeContractManager.getInstance().pruneEmptyContracts();
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TradeContractUITable.resizeToNewWidth(contentPanel.getPosition().getWidth() - 410);
        if (table == null) {
            table =
                    new TradeContractUITable(
                            TradeContractUITable.getWidth(),
                            contentPanel.getPosition().getHeight() - 50,
                            true,
                            0,
                            0);
            table.createSections();
            table.createTable();
        } else {
            table.recreateTable();
        }
        if (data == null) {
            data = new TradeContractFactionData(410, contentPanel.getPosition().getHeight() - 49);
        } else {
            data.getTradeContractUI().createUI();
        }
        contentPanel.addComponent(table.mainPanel).inTL(415, 0);
        contentPanel.addComponent(data.getMainPanel()).inTL(0, 1);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    public static boolean forceTableUpdate = false;

    @Override
    public void renderBelow(float alphaMult) {
        if (data != null && table != null) {
            String curr = table.getCurrentlyChosenContract();
            if (AshMisc.isStringValid(curr)) {
                data.setCurrentlyChosenContract(curr);
            }
            if (data.getTradeContractUI().isUpdateUI()) {
                data.getTradeContractUI().setUpdateUI(false);
                table.recreateTable();
            }
            if (forceTableUpdate) {
                forceTableUpdate = false;
                table.recreateTable();
            }
        }
    }

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
