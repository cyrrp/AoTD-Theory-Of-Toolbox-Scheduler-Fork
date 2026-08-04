package data.kaysaar.aotd.tot.ui.economy.tradecontracts.dialogs;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.browser.ContractBrowsingPanelPlugin;
import java.awt.*;

public class ContractBrowsingDialog extends BasePopUpDialog {
    ContractBrowsingPanelPlugin plugin;
    float tablePrevWidth;
    float heightRecorded;

    public ContractBrowsingDialog() {
        super("Available Trade Contracts");
    }

    @Override
    public ButtonAPI generateCancelButton(TooltipMakerAPI tooltip) {
        ButtonAPI button =
                tooltip.addButton(
                        "Exit",
                        "cancel",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TL_BR,
                        buttonConfirmWidth,
                        25.0F,
                        0.0F);
        button.setShortcut(1, true);
        this.cancelButton = button;
        return button;
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        return null;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        heightRecorded = panelAPI.getPosition().getHeight() - this.y - 20;
        super.createUI(panelAPI);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        plugin = new ContractBrowsingPanelPlugin(width, heightRecorded - 10);
        tooltip.setParaFont(Fonts.ORBITRON_20AABOLD);
        tooltip.addCustom(plugin.getMainPanel(), 0f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    @Override
    public void applyConfirmScript() {
        AoTDTradeContract contract =
                AoTDTradeContractManager.getInstance()
                        .getCurrentlyGeneratedInBrowser()
                        .remove(plugin.getCurrContract());
        AoTDTradeContractManager.getInstance().addContract(contract);
    }

    @Override
    public void onExit() {
        plugin.clearUI();
    }
}
