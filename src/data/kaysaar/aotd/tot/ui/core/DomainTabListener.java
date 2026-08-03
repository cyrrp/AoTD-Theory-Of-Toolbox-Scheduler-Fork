package data.kaysaar.aotd.tot.ui.core;

import ashlib.data.plugins.coreui.CommandTabListener;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.kaysaar.aotd.tot.ui.DomainUIPanel;
import org.lwjgl.input.Keyboard;

import java.awt.*;

import static data.kaysaar.aotd.tot.ui.core.EconomyTabListener.HEIGHT;
import static data.kaysaar.aotd.tot.ui.core.EconomyTabListener.WIDTH;

public class DomainTabListener implements CommandTabListener {
    @Override
    public String getNameForTab() {
        return "Domain";
    }

    @Override
    public String getButtonToReplace() {
        return "colonies";
    }

    @Override
    public String getButtonToBePlacedNear() {
        return null;
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltipCreatorForButton() {
        return new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 500f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addSectionHeading("Ashes of the Domain : Theory of Toolbox", Alignment.MID,0f);
                tooltip.addPara("This tab lists all assets owned by you or your faction: %s, %s, and %s."
                        ,5f, Color.ORANGE,"Star Systems","Colonies","Warehouses");
                tooltip.addPara("As well as provides economic data of your faction and access to Trade Contracts",3f);
            }
        };
    }

    @Override
    public CommandUIPlugin createPlugin() {
        return new DomainUIPanel(WIDTH,HEIGHT);
    }

    @Override
    public float getWidthOfButton() {
        return 130;
    }

    @Override
    public int getKeyBind() {
        return Keyboard.KEY_1;
    }

    @Override
    public void performRecalculations(UIComponentAPI uiComponentAPI) {
        EconomyTabListener.recalculatePanelSize(uiComponentAPI);
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean shouldButtonBeEnabled() {
        return true;
    }

    @Override
    public void performRefresh(ButtonAPI buttonAPI) {

    }
}
