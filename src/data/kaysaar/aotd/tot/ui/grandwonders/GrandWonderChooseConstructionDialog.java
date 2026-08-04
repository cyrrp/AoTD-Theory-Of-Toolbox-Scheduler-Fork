package data.kaysaar.aotd.tot.ui.grandwonders;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import kaysaar.bmo.buildingmenu.MarketDialog;

public class GrandWonderChooseConstructionDialog extends BasePopUpDialog {
    Object panelInd;
    AoTDConstructionSite site;
    GrandWonderPluginUI pluginUI;

    public GrandWonderChooseConstructionDialog(
            DialogCreatorUI creatorUI, AoTDConstructionSite site) {
        super("Choose Grand Wonder to be constructed");
        this.site = site;
        panelInd =
                ReflectionUtilis.invokeMethodWithAutoProjection(
                        "getIndustryPanel",
                        ReflectionUtilis.findFieldWithMethodName(creatorUI, "getIndustryPanel"));
    }

    @Override
    public void applyConfirmScript() {
        Global.getSoundPlayer().playUISound("ui_build_industry", 1, 1);
        site.setAssignedWonder(pluginUI.getCurrChosen().getId());
    }

    @Override
    public void onExit() {
        super.onExit();
        ReflectionUtilis.invokeMethodWithAutoProjection("recreateOverview", panelInd);
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        ButtonAPI bt = super.generateConfirmButton(tooltip);
        bt.setText("Construct");
        return bt;
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        super.createContentForDialog(tooltip, width);
        pluginUI = new GrandWonderPluginUI(width, 650 - this.y, site);
        tooltip.addCustom(pluginUI.getMainPanel(), 0f);
        tooltip.setHeightSoFar(0f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (pluginUI != null) {
            if (pluginUI.getCurrChosen() == null && getConfirmButton().isEnabled()) {
                getConfirmButton().setEnabled(false);
            }
            if (pluginUI.getCurrChosen() != null) {
                if (getConfirmButton().isEnabled()) {
                    if (!MarketDialog.isAvailableToBuild(
                                    pluginUI.getCurrChosen(),
                                    pluginUI.getCurrChosen().getMarket(),
                                    false)
                            || !pluginUI.getCurrChosen().isAvailableToBuild()) {
                        getConfirmButton().setEnabled(false);
                    }
                } else {
                    if (pluginUI.getCurrChosen().isAvailableToBuild()
                            && MarketDialog.isAvailableToBuild(
                                    pluginUI.getCurrChosen(),
                                    pluginUI.getCurrChosen().getMarket(),
                                    false)) {
                        getConfirmButton().setEnabled(true);
                    }
                }
            }
        }
    }
}
