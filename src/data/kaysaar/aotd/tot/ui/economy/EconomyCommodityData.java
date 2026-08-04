package data.kaysaar.aotd.tot.ui.economy;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.FactionChooserButton;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.GraphPeriodChosenButton;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.detailed.AoTDDetailedCommodityPanel;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.popup.ChooseFactionPopUp;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.popup.ChoosePeriodPopUp;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.table.AoTDCommodityProductionDataTable;
import java.util.List;

public class EconomyCommodityData implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;

    public EconomyCommodityData(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        if (AshMisc.getMarketsUnderPlayer().isEmpty()) {
            currFactionId = Factions.NEUTRAL;
        }

        createUI();
    }

    AoTDCommodityProductionDataTable table;
    AoTDDetailedCommodityPanel detailedCommodityPanel;
    String currCommodityId;
    String prevCommodityId;
    FactionChooserButton bt;
    GraphPeriodChosenButton btGraph;
    String currFactionId = Factions.PLAYER;
    int months = 1;
    boolean needsReplacement = false;

    public void setMonths(int months) {

        boolean recreate = months != this.months;
        this.months = months;
        if (recreate) {
            table.clearTable();
            table.clearUI();
            table = null;
            detailedCommodityPanel = null;
            createUI();
        }
    }

    public void setCurrFactionId(String currFactionId) {
        boolean recreate = !currFactionId.equals(this.currFactionId);
        this.currFactionId = currFactionId;
        if (recreate) {
            table.clearTable();
            table.clearUI();
            table = null;
            detailedCommodityPanel = null;
            createUI();
        }
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public float widthOfSecondSection = 440;

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
        bt =
                new FactionChooserButton(
                        450,
                        30,
                        currFactionId,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        true);
        bt.createUI();
        contentPanel
                .addComponent(bt.getMainPanel())
                .inTL(-4, contentPanel.getPosition().getHeight() - 75);

        btGraph =
                new GraphPeriodChosenButton(
                        250,
                        30,
                        months,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        true);
        btGraph.createUI();
        contentPanel
                .addComponent(btGraph.getMainPanel())
                .inTL(-4 + 470, contentPanel.getPosition().getHeight() - 75);

        if (table == null) {
            AoTDCommodityProductionDataTable.resizeToNewWidth(
                    contentPanel.getPosition().getWidth() - widthOfSecondSection - 25);
            table =
                    new AoTDCommodityProductionDataTable(
                            AoTDCommodityProductionDataTable.getWidth(),
                            contentPanel.getPosition().getHeight() - 100,
                            true,
                            0,
                            0,
                            currFactionId,
                            months);
            table.createSections();
            table.createTable();
        }
        table.currCommodityChecked = currCommodityId;
        // Remember to add 3 buttons later under table
        needsReplacement = true;
        createUIDetailed();
        contentPanel.addComponent(table.mainPanel).inTL(0, 0);

        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {}

    public void createUIDetailed() {
        if (detailedCommodityPanel == null && currCommodityId != null) {
            detailedCommodityPanel =
                    new AoTDDetailedCommodityPanel(
                            widthOfSecondSection,
                            contentPanel.getPosition().getHeight() - 75,
                            currCommodityId,
                            currFactionId);
            contentPanel
                    .addComponent(detailedCommodityPanel.getMainPanel())
                    .inTL(AoTDCommodityProductionDataTable.getWidth() + 10, -1);
            needsReplacement = false;
        } else if (currCommodityId != null) {
            detailedCommodityPanel.setCurrCommodityId(currCommodityId);
            if (needsReplacement) {
                needsReplacement = false;
                contentPanel
                        .addComponent(detailedCommodityPanel.getMainPanel())
                        .inTL(AoTDCommodityProductionDataTable.getWidth() + 10, -1);
            }
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (table != null) {
            if (table.currCommodityChecked != null) {
                if (currCommodityId == null) {
                    currCommodityId = table.currCommodityChecked;
                    createUIDetailed();
                } else if (!currCommodityId.equals(table.currCommodityChecked)) {
                    currCommodityId = table.currCommodityChecked;
                    createUIDetailed();
                }
            }
        }
        if (bt != null) {
            if (bt.mainButton.isChecked()) {
                bt.mainButton.setChecked(false);
                AshMisc.placePopUpUI(
                        new ChooseFactionPopUp(currFactionId, this), bt.mainButton, 470, 500);
            }
        }
        if (btGraph != null) {
            if (btGraph.mainButton.isChecked()) {
                btGraph.mainButton.setChecked(false);
                AshMisc.placePopUpUI(
                        new ChoosePeriodPopUp(months, this), btGraph.mainButton, 250, 400);
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
