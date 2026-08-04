package data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import java.awt.*;

public class FactionChooserButton extends CustomButton {
    public FactionChooserButton(
            float width,
            float height,
            String factionId,
            float indent,
            Color base,
            Color bg,
            Color bright,
            boolean withArrow) {
        super(width, height, factionId, indent, base, bg, bright);
        isWithArrow = withArrow;
        setArrowPointDown(withArrow);
    }

    public String getFactionId() {
        return (String) buttonData;
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI container = Global.getSettings().createCustom(this.width, this.height, null);
        createContainerContent(container);
        tooltip.addCustom(container, 0f).getPosition().inTL(5, 0);
        float centerY = height / 2;
        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);
            container
                    .addComponent(panelIndicator)
                    .inTL(container.getPosition().getWidth() - 20, centerY - 7);
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    public void createContainerContent(CustomPanelAPI container) {
        float iconSize = container.getPosition().getHeight() - 6;
        TooltipMakerAPI tlContainer =
                container.createUIElement(
                        container.getPosition().getWidth() - iconSize - 15,
                        container.getPosition().getHeight(),
                        false);
        ImageViewer viewer =
                new ImageViewer(
                        iconSize,
                        iconSize,
                        Global.getSector().getFaction(getFactionId()).getCrest());
        container.addComponent(viewer.getComponentPanel()).inTL(3, 3);
        tlContainer.setParaFont(Fonts.ORBITRON_20AA);
        if (Factions.NEUTRAL.equals(getFactionId())) {
            LabelAPI label = tlContainer.addPara("Persean Sector", 0f);
            label.getPosition()
                    .inTL(
                            0,
                            container.getPosition().getHeight() / 2
                                    - (label.computeTextHeight(label.getText()) / 2));
        } else {
            LabelAPI label =
                    tlContainer.addPara(
                            AoTDToolboxMisc.capitalizeFirst(
                                    Global.getSector().getFaction(getFactionId()).getDisplayName()),
                            0f);
            label.getPosition()
                    .inTL(
                            0,
                            container.getPosition().getHeight() / 2
                                    - (label.computeTextHeight(label.getText()) / 2));
        }

        container.addUIElement(tlContainer).inTL(iconSize + 10, 0);
    }
}
