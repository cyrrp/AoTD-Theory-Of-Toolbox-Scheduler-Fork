package data.kaysaar.aotd.tot.ui.commoditypanel;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.ui.commodityDetailedInfo.AoTDDetailedInfoButton;
import java.util.ArrayList;

public class AoTDCommodityTableDropDownButton extends DropDownButton {
    public AoTDCommodityOnMarket commodity;
    boolean isExtended = false;
    boolean showProducers;

    public AoTDCommodityTableDropDownButton(
            UITableImpl tableOfReference,
            float width,
            float height,
            float maxWidth,
            float maxHeight,
            boolean isExtended,
            AoTDCommodityOnMarket commodity) {
        super(tableOfReference, width, height, maxWidth, maxHeight, false);
        this.commodity = commodity;
        this.buttons = new ArrayList<>();
        this.isExtended = isExtended;
    }

    public AoTDCommodityTableDropDownButton(
            UITableImpl tableOfReference,
            float width,
            float height,
            float maxWidth,
            float maxHeight,
            boolean isExtended,
            AoTDCommodityOnMarket commodity,
            boolean showProducers) {
        super(tableOfReference, width, height, maxWidth, maxHeight, false);
        this.commodity = commodity;
        this.buttons = new ArrayList<>();
        this.isExtended = isExtended;
        this.showProducers = showProducers;
    }

    @Override
    public void createUIContent() {
        if (isExtended) {
            mainButton =
                    new AoTDDetailedInfoButton(
                            width,
                            height,
                            commodity,
                            0f,
                            commodity.getMarket().getFaction().getBaseUIColor(),
                            commodity.getMarket().getFaction().getDarkUIColor(),
                            commodity.getMarket().getFaction().getBrightUIColor(),
                            showProducers);
        } else {
            mainButton =
                    new AoTDCommodityInfoButton(
                            width,
                            height,
                            commodity,
                            0f,
                            commodity.getMarket().getFaction().getBaseUIColor(),
                            commodity.getMarket().getFaction().getDarkUIColor(),
                            commodity.getMarket().getFaction().getBrightUIColor());
        }
        mainButton.createUI();
        tooltipOfImpl.addCustom(mainButton.getPanel(), 5f).getPosition().inTL(0, 0);
    }
}
