package data.kaysaar.aotd.tot.ui.core;

import ashlib.data.plugins.coreui.CommandTabListener;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.kaysaar.aotd.tot.ui.economy.EconomyUIPanel;
import org.lwjgl.input.Keyboard;

import static ashlib.data.plugins.coreui.CommandTabTracker.tryToGetButtonProd;

public class EconomyTabListener implements CommandTabListener {
    public static float WIDTH ,HEIGHT;
    @Override
    public String getNameForTab() {
        return "Economy";
    }

    @Override
    public String getButtonToReplace() {
        return null;
    }

    @Override
    public String getButtonToBePlacedNear() {
        if(Global.getSettings().getModManager().isModEnabled("Terraforming & Station Construction")){
            return "Terraforming";
        }
        if(Global.getSettings().getModManager().isModEnabled("aotd_vok")){
            return "research & production";
        }

        return "custom production";
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
                return 500;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addSectionHeading("Ashes of the Domain : Theory of Toolbox", Alignment.MID,0f);
                tooltip.addPara("In this tab, you can analyze economy data and manage markets production and exports",5f);
            }
        };
    }

    @Override
    public CommandUIPlugin createPlugin() {
        return new EconomyUIPanel(WIDTH,HEIGHT);
    }

    @Override
    public float getWidthOfButton() {
        return 150;
    }

    @Override
    public int getKeyBind() {
        if(Global.getSettings().getModManager().isModEnabled("Terraforming & Station Construction")){
            return Keyboard.KEY_7;
        }
        return  Keyboard.KEY_6;
    }

    @Override
    public void performRecalculations(UIComponentAPI uiComponentAPI) {
        recalculatePanelSize(uiComponentAPI);
    }

    static void recalculatePanelSize(UIComponentAPI uiComponentAPI) {
        if (uiComponentAPI == null || uiComponentAPI.getPosition() == null) {
            return;
        }

        ButtonAPI button = tryToGetButtonProd("domain");
        if (button == null) {
            button = tryToGetButtonProd("colonies");
        }

        if (button != null && button.getPosition() != null) {
            WIDTH = Global.getSettings().getScreenWidth() - button.getPosition().getX();
        } else {
            WIDTH = uiComponentAPI.getPosition().getWidth();
        }
        HEIGHT = uiComponentAPI.getPosition().getHeight();
    }

    @Override
    public int getOrder() {
        return 40000;
    }

    @Override
    public boolean shouldButtonBeEnabled() {
        return true;
    }

    @Override
    public void performRefresh(ButtonAPI buttonAPI) {

    }
}
