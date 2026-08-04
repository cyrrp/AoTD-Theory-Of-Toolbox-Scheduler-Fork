package data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons;

import ashlib.data.plugins.ui.models.CustomButton;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.*;

public class GraphPeriodChosenButton extends CustomButton {
    public GraphPeriodChosenButton(
            float width,
            float height,
            int buttonData,
            float indent,
            Color base,
            Color bg,
            Color bright,
            boolean withArrow) {
        super(width, height, buttonData, indent, base, bg, bright);
        isWithArrow = withArrow;
        setArrowPointDown(withArrow);
    }

    public int getMonths() {
        return (int) buttonData;
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
                        container.getPosition().getWidth() - 5,
                        container.getPosition().getHeight(),
                        false);
        tlContainer.setParaFont(Fonts.ORBITRON_20AA);
        LabelAPI label;
        if (getMonths() == Integer.MAX_VALUE) {
            label = tlContainer.addPara("From Start", 0f);

        } else {
            label = tlContainer.addPara("<= " + getCombinedLabelStringForPeriod(getMonths()), 0f);
        }
        label.getPosition()
                .inTL(
                        0,
                        container.getPosition().getHeight() / 2
                                - (label.computeTextHeight(label.getText()) / 2));
        container.addUIElement(tlContainer).inTL(5, 0);
    }

    public static String getCombinedLabelStringForPeriod(int month) {
        if (month == Integer.MAX_VALUE) {
            return "From Start";
        }
        return getNumber(month) + " " + getLabelStringForMonth(month);
    }

    public static String getLabelStringForMonth(int month) {
        if (month == Integer.MAX_VALUE) return "From Start";
        if (month == 1) {
            return "Month";
        }
        if (month >= 2 && month < 12) {
            return "Months";
        }
        if (month == 12) {
            return "Cycle";
        }
        return "Cycles";
    }

    public static String getNumber(float month) {
        if (month == Integer.MAX_VALUE) return "";
        if (month < 12) {
            return String.format("%d", (int) month);
        }
        if (month % 12 == 0) {
            return String.format("%d", (int) (month / 12));
        }
        return String.format("%.1f", month / 12f);
    }
}
