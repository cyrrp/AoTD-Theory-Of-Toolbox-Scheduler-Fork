package data.kaysaar.aotd.tot.ui.economy.commoditydata.popup;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.PopUpUI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.ui.economy.EconomyCommodityData;
import data.kaysaar.aotd.tot.ui.economy.commoditydata.buttons.FactionChooserButton;
import java.util.ArrayList;

public class ChooseFactionPopUp extends PopUpUI {
    ArrayList<FactionChooserButton> factions = new ArrayList<>();
    String currFaction;
    EconomyCommodityData data;
    CustomPanelAPI mainPanel;

    public ChooseFactionPopUp(String currChosenFaction, EconomyCommodityData data) {
        this.currFaction = currChosenFaction;
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
        FactionChooserButton chosenPlayer =
                new FactionChooserButton(
                        panelAPI.getPosition().getWidth() - 20,
                        30,
                        Factions.PLAYER,
                        0f,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Misc.getBrightPlayerColor(),
                        false);
        if (factions.isEmpty()) {
            factions.add(
                    new FactionChooserButton(
                            panelAPI.getPosition().getWidth() - 20,
                            30,
                            Factions.NEUTRAL,
                            0f,
                            Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(),
                            Misc.getBrightPlayerColor(),
                            false));
            if (!AshMisc.getMarketsUnderPlayer().isEmpty()) {
                factions.add(chosenPlayer);
            }

            for (String s : AoTDTradeManager.getInstance().getAllFactionTradeData().keySet()) {
                if (s.equals(Factions.PLAYER)) continue;
                if (s.equals(Factions.NEUTRAL)) continue;
                factions.add(
                        new FactionChooserButton(
                                panelAPI.getPosition().getWidth() - 20,
                                30,
                                s,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                false));
            }
        }
        for (FactionChooserButton faction : factions) {
            faction.createUI();
            tooltipMakerAPI.addCustom(faction.getMainPanel(), 2f);
        }
        mainPanel.addUIElement(tooltipMakerAPI).inTL(0, 0);
        return Math.min(panelAPI.getPosition().getHeight(), tooltipMakerAPI.getHeightSoFar());
    }

    @Override
    public void onExit() {
        super.onExit();
        for (FactionChooserButton faction : factions) {
            faction.clearUI();
        }
        factions.clear();
        data.setCurrFactionId(currFaction);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        for (FactionChooserButton faction : factions) {
            if (faction.getFactionId().equals(currFaction)) {
                faction.mainButton.highlight();
            } else {
                faction.mainButton.unhighlight();
            }
            if (faction.mainButton.isChecked()) {
                faction.setChecked(false);
                currFaction = faction.getFactionId();
                this.forceDismiss();
                return;
            }
        }
    }
}
