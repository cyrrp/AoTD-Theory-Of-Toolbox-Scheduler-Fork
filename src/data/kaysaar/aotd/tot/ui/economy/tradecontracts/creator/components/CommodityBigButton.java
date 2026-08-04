package data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;

public class CommodityBigButton extends CustomButton {

    public String getCommodityId() {
        return (String) buttonData;
    }

    public CommodityBigButton(float width, float height, String commodityId) {
        super(
                width,
                height,
                commodityId,
                0f,
                Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor());
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
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(getCommodityId());
        float ratio = 1.75f;
        ImageViewer viewer =
                new ImageViewer(
                        container.getPosition().getHeight() / ratio,
                        container.getPosition().getHeight() / ratio,
                        spec.getIconName());
        TooltipMakerAPI tlBottomTitle =
                container.createUIElement(container.getPosition().getWidth() - 4, 30, false);
        tlBottomTitle.setParaFont(Fonts.ORBITRON_12);
        tlBottomTitle.addPara(spec.getName(), 0f).setAlignment(Alignment.MID);
        container
                .addComponent(viewer.getComponentPanel())
                .inTL(
                        container.getPosition().getWidth() / 2
                                - (viewer.getComponentPanel().getPosition().getWidth() / 2),
                        container.getPosition().getHeight() / 2
                                - (viewer.getComponentPanel().getPosition().getHeight() / 2));
        container.addUIElement(tlBottomTitle).inTL(2, container.getPosition().getHeight() - 18);
    }
}
