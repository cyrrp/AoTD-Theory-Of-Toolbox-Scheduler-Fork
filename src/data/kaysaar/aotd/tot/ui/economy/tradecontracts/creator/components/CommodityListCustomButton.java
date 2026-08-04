package data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;

public class CommodityListCustomButton extends CustomButton {
    public static class ButtonData {
        String commodityId;
        int am;
        float cut;

        public ButtonData(String commodityId, int am, float cut) {
            this.commodityId = commodityId;
            this.am = am;
            this.cut = cut;
        }
    }

    public ButtonData getButtonData() {
        return (ButtonData) buttonData;
    }

    public CommodityListCustomButton(
            float width, float height, String commodityId, int am, float cut) {
        super(
                width,
                height,
                new ButtonData(commodityId, am, cut),
                0,
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
        mainButton.setClickable(false);
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(getButtonData().commodityId);
        float ratio = 1.75f;
        ImageViewer viewer =
                new ImageViewer(
                        container.getPosition().getHeight() - 4,
                        container.getPosition().getHeight() - 4,
                        spec.getIconName());
        TooltipMakerAPI tlBottomTitle =
                container.createUIElement(container.getPosition().getWidth() - 4, 30, false);
        TooltipMakerAPI amount =
                container.createUIElement(container.getPosition().getWidth() - 4, 30, false);
        float opadText = 8f;
        tlBottomTitle.setParaFont(Fonts.ORBITRON_12);
        int basePrice = (int) spec.getBasePrice();
        float am = basePrice * getButtonData().am * getButtonData().cut;
        tlBottomTitle
                .addPara(Misc.getDGSCredits(am), Color.ORANGE, opadText)
                .setAlignment(Alignment.TR);
        amount.addPara("- " + spec.getName() + ": x" + getButtonData().am, opadText);
        container.addComponent(viewer.getComponentPanel()).inTL(2, 2);
        container.addUIElement(tlBottomTitle).inTL(2, 0);
        container
                .addUIElement(amount)
                .inTL(viewer.getComponentPanel().getPosition().getWidth() + 5, 0);
    }
}
