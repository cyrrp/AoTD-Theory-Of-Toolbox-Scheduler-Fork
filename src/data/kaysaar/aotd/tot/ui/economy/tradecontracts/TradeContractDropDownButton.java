package data.kaysaar.aotd.tot.ui.economy.tradecontracts;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;

public class TradeContractDropDownButton extends DropDownButton {
    AoTDTradeContract contract;
    boolean browsingMode = false;

    public TradeContractDropDownButton(
            UITableImpl tableOfReference,
            float width,
            float height,
            float maxWidth,
            float maxHeight,
            AoTDTradeContract contract,
            boolean browsingMode) {
        super(tableOfReference, width, height, maxWidth, maxHeight, false);
        this.browsingMode = browsingMode;
        this.contract = contract;
    }

    @Override
    public void createUIContent() {
        super.createUIContent();

        mainButton =
                new TradeContractCustomButton(
                        width,
                        height,
                        contract,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        browsingMode);
        mainButton.createUI();
        tooltipOfImpl.addCustom(mainButton.getPanel(), 5f).getPosition().inTL(0, 0);
    }
}
