package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.List;

public class StarSystemHoldingDropDown extends DropDownButton {
    StarSystemAPI main;
    ArrayList<MarketAPI> sub;
    Object originalPanel;

    public StarSystemHoldingDropDown(
            UITableImpl tableOfReference,
            float width,
            float height,
            float maxWidth,
            float maxHeight,
            boolean droppableMode,
            StarSystemAPI system,
            ArrayList<MarketAPI> markets,
            Object originalPanel) {
        super(tableOfReference, width, height, maxWidth, maxHeight, system != null);
        this.main = system;
        this.sub = markets;
        this.originalPanel = originalPanel;
    }

    public List<MarketAPI> getMarkets() {
        return sub;
    }

    public StarSystemAPI getStarSystem() {
        return main;
    }

    @Override
    public void advance(float amount) {
        if (this.mainButton != null) {
            for (CustomButton button : this.buttons) {
                if (button.isChecked()) {
                    button.setChecked(false);
                    this.tableOfReference.reportButtonPressed(button);
                }
            }

            if (this.mainButton.isChecked()) {
                this.mainButton.setChecked(false);
                if (this.droppableMode) {
                    this.isDropped = !this.isDropped;
                    this.tableOfReference.reportButtonPressed(this.mainButton);
                    this.tableOfReference.recreateTable();
                } else {
                    this.tableOfReference.reportButtonPressed(this.mainButton);
                }
            }
            if (this.droppableMode && isDropped) {
                this.mainButton.mainButton.highlight();
            } else {
                this.mainButton.mainButton.unhighlight();
            }
        }
        if (mainButton != null) {
            mainButton.setArrowPointDown(isDropped);
        }
    }

    @Override
    public void createUIContent() {
        if (buttons == null) {
            buttons = new ArrayList<>();
            if (droppableMode) {
                for (MarketAPI subSpec : sub) {
                    StarSystemHoldingButton button =
                            new StarSystemHoldingButton(
                                    width - 5f,
                                    height,
                                    subSpec,
                                    5f,
                                    Misc.getBasePlayerColor(),
                                    Misc.getDarkPlayerColor(),
                                    Misc.getBrightPlayerColor(),
                                    false,
                                    originalPanel);
                    button.createUI();
                    buttons.add(button);
                }
            }
            if (droppableMode) {
                mainButton =
                        new StarSystemHoldingButton(
                                width,
                                height,
                                main,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                true,
                                originalPanel);

            } else {
                mainButton =
                        new StarSystemHoldingButton(
                                width,
                                height,
                                main,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                false,
                                originalPanel);
            }
            mainButton.createUI();
        } else {
            buttons.forEach(
                    x -> {
                        x.clearUI();
                        x.createUI();
                    });
            mainButton.clearUI();
            mainButton.createUI();
        }
        if (!isDropped) {
            tooltipOfImpl.addCustom(mainButton.getPanel(), 5f).getPosition().inTL(0, 0);
        } else {
            tooltipOfImpl.addCustom(mainButton.getPanel(), 5f).getPosition().inTL(0, 0);
            float currY = mainButton.getPanel().getPosition().getHeight() + 2;
            for (CustomButton button : buttons) {
                tooltipOfImpl
                        .addCustom(button.getPanel(), 0f)
                        .getPosition()
                        .inTL(button.indent, currY);
                currY += button.getPanel().getPosition().getHeight() + 2;
            }
            tooltipOfImpl.getPosition().setSize(width, currY);
            panelOfImpl.getPosition().setSize(width, currY);
            mainPanel.getPosition().setSize(width, currY);
        }
    }

    @Override
    public void createUI() {
        super.createUI();
    }

    @Override
    public void clear() {
        super.clear();
        sub.clear();
    }
}
