package data.kaysaar.aotd.tot.ui.starsystems.admin;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.ui.holdings.starsystems.components.ButtonWithImageComponent;
import java.awt.*;
import java.util.List;

public class StarSystemAdminComponent implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, componentPanel;
    PersonAPI person;
    ButtonWithImageComponent component;
    StarSystemAPI system;
    LabelAPI title, number; // cohesion
    LabelAPI securityTitle, securityValue; // NEW

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public StarSystemAdminComponent(float width, PersonAPI person, StarSystemAPI system) {
        this.person = person;
        this.system = system;
        this.mainPanel = Global.getSettings().createCustom(width, 100, this);
        createUI();
    }

    @Override
    public void createUI() {
        if (componentPanel != null) {
            mainPanel.removeComponent(componentPanel);
        }
        componentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI tooltip =
                componentPanel.createUIElement(
                        componentPanel.getPosition().getWidth(),
                        componentPanel.getPosition().getHeight(),
                        false);

        component =
                new ButtonWithImageComponent(100, 100, person.getPortraitSprite()) {
                    @Override
                    public void performActionOnClick(boolean isRightClick) {
                        super.performActionOnClick(isRightClick);
                    }
                };
        tooltip.addCustom(component.getComponentPanel(), 0f);

        LabelAPI l =
                tooltip.addPara("System Administrator", person.getFaction().getBaseUIColor(), 0f);
        l.getPosition().inTL(115, 0);

        tooltip.addPara(person.getNameString(), person.getFaction().getBaseUIColor(), 2f);
        ButtonAPI bt =
                tooltip.addButton(
                        "Add stable point",
                        null,
                        person.getFaction().getBaseUIColor(),
                        person.getFaction().getDarkUIColor(),
                        l.computeTextWidth(l.getText()) + 20,
                        20,
                        15f);
        bt.getPosition().inTL(bt.getPosition().getX(), 100 - bt.getPosition().getHeight());
        // --- Cohesion ---
        tooltip.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        title = tooltip.addPara("Cohesion", person.getFaction().getBaseUIColor(), 0f);
        title.getPosition()
                .setSize(
                        title.computeTextWidth(title.getText()),
                        title.computeTextHeight(title.getText()));
        title.setHighlightOnMouseover(true);

        float startX = 115 + l.computeTextWidth(l.getText()) + 5;

        float cohesionTitleX = startX + 40;
        float cohesionTitleY = 0f; // keep as your current baseline
        title.getPosition().inTL(cohesionTitleX, cohesionTitleY);

        tooltip.setParaFont("graphics/fonts/insignia25LTaa.fnt");
        number = tooltip.addPara("10", person.getFaction().getBrightUIColor(), 2f);
        number.setHighlightOnMouseover(true);
        number.getPosition()
                .setSize(
                        number.computeTextWidth(number.getText()),
                        number.computeTextHeight(number.getText()));

        float cohesionCenterX =
                title.getPosition().getX() + (title.computeTextWidth(title.getText()) / 2f);
        number.getPosition()
                .inTL(cohesionCenterX - (number.computeTextWidth(number.getText()) / 2f), 15);

        ReflectionUtilis.invokeMethodWithAutoProjection("setAdditiveColor", number, (Color) null);

        tooltip.setParaFont("graphics/fonts/orbitron12condensed.fnt");
        securityTitle = tooltip.addPara("Stable Points", person.getFaction().getBaseUIColor(), 0f);
        securityTitle
                .getPosition()
                .setSize(
                        securityTitle.computeTextWidth(securityTitle.getText()),
                        securityTitle.computeTextHeight(securityTitle.getText()));
        securityTitle.setHighlightOnMouseover(true);

        float securityTitleX = cohesionTitleX + title.computeTextWidth(title.getText()) + 40f;
        securityTitle
                .getPosition()
                .inTL(securityTitleX, cohesionTitleY); // same Y as Cohesion title

        tooltip.setParaFont("graphics/fonts/insignia25LTaa.fnt");
        String securityLevel =
                "0 / "
                        + system.getEntitiesWithTag(Tags.STABLE_LOCATION)
                                .size(); // Low / Medium / High (set this from your data)
        securityValue = tooltip.addPara(securityLevel, person.getFaction().getBrightUIColor(), 2f);
        securityValue.setHighlightOnMouseover(true);
        securityValue
                .getPosition()
                .setSize(
                        securityValue.computeTextWidth(securityValue.getText()),
                        securityValue.computeTextHeight(securityValue.getText()));

        float securityCenterX =
                securityTitle.getPosition().getX()
                        + (securityTitle.computeTextWidth(securityTitle.getText()) / 2f);
        securityValue
                .getPosition()
                .inTL(
                        securityCenterX
                                - (securityValue.computeTextWidth(securityValue.getText()) / 2f),
                        15);

        ReflectionUtilis.invokeMethodWithAutoProjection(
                "setAdditiveColor", securityValue, (Color) null);

        componentPanel.addUIElement(tooltip).inTL(0, 0);
        mainPanel.addComponent(componentPanel).inTL(0, 0);
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

        // ---- Cohesion pair ----
        if (title != null && number != null) {
            Fader titleFader =
                    (Fader)
                            ReflectionUtilis.invokeMethodWithAutoProjection(
                                    "getMouseoverFader", title);
            Fader numberFader =
                    (Fader)
                            ReflectionUtilis.invokeMethodWithAutoProjection(
                                    "getMouseoverFader", number);

            titleFader.fadeOut();
            numberFader.fadeOut();

            for (InputEventAPI event : events) {
                if (!event.isConsumed()
                        && event.isMouseEvent()
                        && (title.getPosition().containsEvent(event)
                                || number.getPosition().containsEvent(event))) {

                    titleFader.fadeIn();
                    numberFader.fadeIn();
                    break;
                }
            }
        }

        // ---- Security pair (NEW, independent) ----
        if (securityTitle != null && securityValue != null) {
            Fader securityTitleFader =
                    (Fader)
                            ReflectionUtilis.invokeMethodWithAutoProjection(
                                    "getMouseoverFader", securityTitle);
            Fader securityValueFader =
                    (Fader)
                            ReflectionUtilis.invokeMethodWithAutoProjection(
                                    "getMouseoverFader", securityValue);

            securityTitleFader.fadeOut();
            securityValueFader.fadeOut();

            for (InputEventAPI event : events) {
                if (!event.isConsumed()
                        && event.isMouseEvent()
                        && (securityTitle.getPosition().containsEvent(event)
                                || securityValue.getPosition().containsEvent(event))) {

                    securityTitleFader.fadeIn();
                    securityValueFader.fadeIn();
                    break;
                }
            }
        }
    }

    @Override
    public void buttonPressed(Object buttonId) {}
}
