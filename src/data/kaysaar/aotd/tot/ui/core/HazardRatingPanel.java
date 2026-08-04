package data.kaysaar.aotd.tot.ui.core;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.ui.core.onhover.HazardRatingOnHover;
import java.awt.*;
import java.util.List;

public class HazardRatingPanel implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    MarketAPI tiedMarket;
    LabelAPI title, number;

    public HazardRatingPanel(MarketAPI tiedMarket) {
        mainPanel = Global.getSettings().createCustom(160, 40, this);
        this.tiedMarket = tiedMarket;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI tooltip =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 300, false);
        tooltip.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        title = tooltip.addPara("Hazard Rating", tiedMarket.getFaction().getBaseUIColor(), 4f);
        title.setAlignment(Alignment.MID);
        title.setHighlightOnMouseover(true);
        tooltip.setParaFont("graphics/fonts/insignia25LTaa.fnt");
        // do income
        int hazard = (int) (tiedMarket.getHazardValue() * 100);
        final String income = hazard + "%";
        number = tooltip.addPara(income, tiedMarket.getFaction().getBrightUIColor(), 3f);
        number.setAlignment(Alignment.MID);
        number.setHighlightOnMouseover(true);

        ReflectionUtilis.invokeMethodWithAutoProjection("setAdditiveColor", number, (Color) null);
        contentPanel.addUIElement(tooltip).inTL(0, 0);
        mainPanel.addComponent(contentPanel).inTL(0, -4);
        tooltip.addTooltipTo(
                new HazardRatingOnHover(tiedMarket),
                contentPanel,
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);
        boolean finalNotAvailable = false;
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (title != null && number != null) {
            Fader fader =
                    (Fader)
                            ReflectionUtilis.invokeMethodWithAutoProjection(
                                    "getMouseoverFader", title);
            Fader fader2 =
                    (Fader)
                            ReflectionUtilis.invokeMethodWithAutoProjection(
                                    "getMouseoverFader", number);
            fader.fadeOut();
            fader2.fadeOut();

            for (InputEventAPI event : events) {
                if (!event.isConsumed()
                        && event.isMouseEvent()
                        && this.contentPanel.getPosition().containsEvent(event)) {
                    fader.fadeIn();
                    fader2.fadeIn();
                    break;
                }
            }
        }
    }

    @Override
    public void buttonPressed(Object buttonId) {}
}
