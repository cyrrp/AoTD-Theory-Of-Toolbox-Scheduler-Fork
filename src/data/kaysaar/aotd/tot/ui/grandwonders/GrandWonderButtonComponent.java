package data.kaysaar.aotd.tot.ui.grandwonders;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeManager;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeSpecAPI;
import java.awt.*;

public class GrandWonderButtonComponent extends CustomButton {
    public GrandWonderButtonComponent(
            float width, float height, GrandWonderAPI buttonData, MarketAPI market) {
        super(
                width,
                height,
                buttonData,
                0f,
                market.getFaction().getBaseUIColor(),
                market.getFaction().getDarkUIColor(),
                market.getFaction().getBrightUIColor());
        isWithArrow = false;
    }

    public GrandWonderAPI getButtonData() {
        return (GrandWonderAPI) buttonData;
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI container = Global.getSettings().createCustom(this.width, this.height, null);
        createContainerContent(container);
        tooltip.addCustom(container, 0f).getPosition().inTL(5, 0);
        float centerY = height / 2;
        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);
            //            tooltip.addCustom(panelIndicator,0f).getPosition().inTL((float)
            // StarSystemHoldingTable.widthMap.get("name")*0.75f,centerY-7);

        }
    }

    public void createContainerContent(CustomPanelAPI container) {
        ImageViewer viewer = new ImageViewer(100, 50, getButtonData().getCurrentImage());
        container
                .addComponent(viewer.getComponentPanel())
                .inTL(2, container.getPosition().getHeight() / 2 - 25);
        TooltipMakerAPI tlLabels =
                container.createUIElement(
                        container.getPosition().getWidth() - 105,
                        container.getPosition().getHeight(),
                        false);
        tlLabels.addPara(getButtonData().getCurrentName(), 2f);
        GrandWonderTypeSpecAPI typeSpecAPI =
                GrandWonderTypeManager.getSpec(getButtonData().getWonderTypeId());
        if (typeSpecAPI.showTypeSeparate()) {
            tlLabels.addPara(typeSpecAPI.getName(), typeSpecAPI.getColor(), 10f);
        }
        container.addUIElement(tlLabels).inTL(105, 0);
    }
}
