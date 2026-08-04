package data.kaysaar.aotd.tot.ui.economy.commoditydata.popup;

import ashlib.data.plugins.ui.models.PopUpUI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.ui.economy.EconomyCommodityData;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.GraphPeriodChosenButton;
import java.util.ArrayList;

public class ChoosePeriodPopUp extends PopUpUI {
    ArrayList<GraphPeriodChosenButton> graphsIntervals = new ArrayList<>();
    int currMonths;
    EconomyCommodityData data;
    CustomPanelAPI mainPanel;
    public static ArrayList<Integer> monthsIntervals = new ArrayList<>();

    static {
        monthsIntervals.add(1);
        monthsIntervals.add(6);
        monthsIntervals.add(12);
        monthsIntervals.add(18);
        monthsIntervals.add(24);
        monthsIntervals.add(36);
        monthsIntervals.add(48);
        monthsIntervals.add(60);
        monthsIntervals.add(120);

        monthsIntervals.add(Integer.MAX_VALUE);
    }

    public ChoosePeriodPopUp(int currChosenFaction, EconomyCommodityData data) {
        this.currMonths = currChosenFaction;
        this.data = data;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createUIMockup(panelAPI);
        panelAPI.addComponent(mainPanel).inTL(0, 0);
    }

    @Override
    public float createUIMockup(CustomPanelAPI panelAPI) {
        mainPanel =
                panelAPI.createCustomPanel(
                        panelAPI.getPosition().getWidth(),
                        panelAPI.getPosition().getHeight(),
                        null);
        TooltipMakerAPI tooltipMakerAPI =
                mainPanel.createUIElement(
                        mainPanel.getPosition().getWidth(),
                        mainPanel.getPosition().getHeight(),
                        true);
        if (graphsIntervals.isEmpty()) {
            for (Integer monthsInterval : monthsIntervals) {
                graphsIntervals.add(
                        new GraphPeriodChosenButton(
                                panelAPI.getPosition().getWidth() - 20,
                                30,
                                monthsInterval,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                false));
            }
        }

        for (GraphPeriodChosenButton faction : graphsIntervals) {
            faction.createUI();
            tooltipMakerAPI.addCustom(faction.getMainPanel(), 2f);
        }
        mainPanel.addUIElement(tooltipMakerAPI).inTL(0, 0);
        return Math.min(panelAPI.getPosition().getHeight(), tooltipMakerAPI.getHeightSoFar());
    }

    @Override
    public void onExit() {
        super.onExit();
        for (GraphPeriodChosenButton faction : graphsIntervals) {
            faction.clearUI();
        }
        graphsIntervals.clear();
        data.setMonths(currMonths);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        for (GraphPeriodChosenButton faction : graphsIntervals) {
            if (faction.getMonths() == (currMonths)) {
                faction.mainButton.highlight();
            } else {
                faction.mainButton.unhighlight();
            }
            if (faction.mainButton.isChecked()) {
                faction.setChecked(false);
                currMonths = faction.getMonths();
                this.forceDismiss();
                return;
            }
        }
    }
}
