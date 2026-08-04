package data.kaysaar.aotd.tot.ui.economy.commoditydata.table;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.util.Misc;

public class AoTDCommodityProductionDropDownButton extends DropDownButton {
    String commodityId;
    String factionId;
    int months;

    public AoTDCommodityProductionDropDownButton(
            UITableImpl tableOfReference,
            float width,
            float height,
            float maxWidth,
            float maxHeight,
            String commodityId,
            String factionId,
            int months) {
        super(tableOfReference, width, height, maxWidth, maxHeight, false);
        this.factionId = factionId;
        this.commodityId = commodityId;
        this.months = months;
    }

    CommoditySpecAPI getSpec() {
        return Global.getSettings().getCommoditySpec(commodityId);
    }

    @Override
    public void createUIContent() {
        super.createUIContent();
        FactionAPI faction = Global.getSector().getFaction(factionId);
        mainButton =
                new AoTDCommodityProductionButton(
                        width,
                        height,
                        new AoTDCommodityProductionButton.AoTDCommodityProductionButtonData(
                                commodityId, factionId, months),
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor());

        mainButton.createUI();
        tooltipOfImpl.addCustom(mainButton.getPanel(), 5f).getPosition().inTL(0, 0);
    }
}
