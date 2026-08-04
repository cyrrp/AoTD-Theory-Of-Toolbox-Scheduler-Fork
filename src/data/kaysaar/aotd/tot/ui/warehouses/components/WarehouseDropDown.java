package data.kaysaar.aotd.tot.ui.warehouses.components;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

public class WarehouseDropDown extends DropDownButton {
    public MarketAPI market;

    public WarehouseDropDown(
            UITableImpl tableOfReference,
            float width,
            float height,
            float maxWidth,
            float maxHeight,
            MarketAPI market) {
        super(tableOfReference, width, height, maxWidth, maxHeight, false);
        this.market = market;
    }

    @Override
    public void createUIContent() {
        super.createUIContent();
        mainButton =
                new WarehouseCustomButton(
                        width,
                        height,
                        market,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor());
        mainButton.createUI();
        tooltipOfImpl.addCustom(mainButton.getPanel(), 5f).getPosition().inTL(0, 0);
    }
}
