package data.kaysaar.aotd.tot.ui.core;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.SharedUnlockData;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.state.AppDriver;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.CargoPanelContextUI;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.IndustryPanelContextUI;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.MarketUIListener;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.SurveyPanelContextUI;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityPanel;
import data.kaysaar.aotd.tot.ui.core.onhover.AccessibilityOnHover;
import data.kaysaar.aotd.tot.ui.industry.IndustryOnHoverTooltipV2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class CommoditiesPanelInjector implements MarketUIListener {
    boolean replaced= false;
    @Override
    public void onMarketOverviewDiscovered(IndustryPanelContextUI ctx) {
        MarketAPI market = ctx.market;
        UIPanelAPI panelOfOtherInfo = ctx.panelOfOtherInfo;
        UIPanelAPI mainPanel = ctx.mainColonyPanel;
        UIComponentAPI toRemove = null;
        UIComponentAPI widgets = null;
        UIComponentAPI incomeToRemove = null;
        UIPanelAPI incomeInjector = null;
        UIComponentAPI accessPanel = null;
        boolean foundRightPanel = false;
        CampaignState state = (CampaignState) AppDriver.getInstance().getCurrentState();
        for (UIComponentAPI componentAPI : ReflectionUtilis.getChildrenCopy((UIPanelAPI) ReflectionUtilis.invokeMethodWithAutoProjection("getScreenPanel",state))) {
            if(ReflectionUtilis.hasMethodOfName("computeShutdownRefund",componentAPI)){
                if(!replaced){
                    for (UIComponentAPI uiComponentAPI : ReflectionUtilis.getChildrenCopy((UIPanelAPI) componentAPI)) {
                        if(uiComponentAPI instanceof ButtonAPI bt ){
                            LabelAPI label = (LabelAPI) ReflectionUtilis.getChildrenCopy((UIPanelAPI) ReflectionUtilis.invokeMethodWithAutoProjection("getPanel",bt)).get(0);
                            if(label.getText().toLowerCase().contains("downgrade")){
                                Industry ind = (Industry) ReflectionUtilis.findFieldOfClass(componentAPI,Industry.class);

                                Industry downgrade = ind.getMarket().instantiateIndustry(ind.getSpec().getDowngrade());
                                TooltipMakerAPI tl = Global.getSettings().createCustom(0,0,null).createUIElement(0,0,false);
                                TooltipMakerAPI.TooltipLocation location = TooltipMakerAPI.TooltipLocation.RIGHT;

                                if(uiComponentAPI.getPosition().getX()+uiComponentAPI.getPosition().getWidth()+320+400>Global.getSettings().getScreenWidth()){
                                    location = TooltipMakerAPI.TooltipLocation.LEFT;
                                }
                                tl.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
                                    @Override
                                    public boolean isTooltipExpandable(Object tooltipParam) {
                                        return true;
                                    }

                                    @Override
                                    public float getTooltipWidth(Object tooltipParam) {
                                        return 400;
                                    }

                                    @Override
                                    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                                        IndustryOnHoverTooltipV2 v2 = new IndustryOnHoverTooltipV2(getTooltipWidth(tooltipParam),downgrade,expanded,true);
                                        v2.createUI();
                                        tooltip.addCustom(v2.getMainPanel(),0f);
                                    }
                                },uiComponentAPI, location,false);
                            }
                        }
                    }
                    replaced = true;
                }
                foundRightPanel = true;

            }
        }
        if(!foundRightPanel){
            replaced = false;
        }



        for (UIComponentAPI componentAPI : ReflectionUtilis.getChildrenCopy(mainPanel)) {
            if(ReflectionUtilis.hasMethodOfName("setDetailDialog",componentAPI)){
                toRemove = componentAPI;
            }

            if(ReflectionUtilis.hasMethodOfName("getShipping",componentAPI)){
                UIComponentAPI shipping = (UIComponentAPI) ReflectionUtilis.invokeMethodWithAutoProjection("getShipping",componentAPI);
                ((UIPanelAPI)componentAPI).removeComponent(shipping);
            }
            if(ReflectionUtilis.hasMethodOfName("getIncome",componentAPI)){
                incomeToRemove = (UIComponentAPI) ReflectionUtilis.invokeMethodWithAutoProjection("getIncome",componentAPI);
                ((UIPanelAPI)componentAPI).removeComponent(incomeToRemove);
                incomeInjector = (UIPanelAPI) componentAPI;
            }
            if(ReflectionUtilis.hasMethodOfName("getWidgets",componentAPI)){
               widgets = componentAPI;
            }
        }

        float xAxis = 70;
        float additionalHeight  =30;
        float extraWidth =74;
        if(toRemove != null){
            if(!ctx.grandColoniesLayout){
                if(widgets!=null){
                    TooltipMakerAPI tl = Global.getSettings().createCustom(1,1,null).createUIElement(1,1,false);
                    for (UIPanelAPI object : new ArrayList<UIPanelAPI>((Collection) ReflectionUtilis.invokeMethodWithAutoProjection("getWidgets", widgets))) {
                        final Industry ind = (Industry) ReflectionUtilis.findFieldOfClass(object, Industry.class);
                        for (UIComponentAPI componentAPI : ReflectionUtilis.getChildrenCopy(object)) {
                            if(componentAPI instanceof IconGroupAPI){
                                object.removeComponent(componentAPI);
                                break;
                            }
                        }
                        tl.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
                            @Override
                            public boolean isTooltipExpandable(Object tooltipParam) {
                                return true;
                            }

                            @Override
                            public float getTooltipWidth(Object tooltipParam) {
                                return 400;
                            }

                            @Override
                            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                                IndustryOnHoverTooltipV2 v2 = new IndustryOnHoverTooltipV2(getTooltipWidth(tooltipParam),ind,expanded);
                                tooltip.addCustom(v2.getMainPanel(),0f);
                                if (ind.getSpec() != null && ind.getSpec().hasTag(Tags.CODEX_UNLOCKABLE)) {
                                    SharedUnlockData.get().reportPlayerAwareOfIndustry(ind.getSpec().getId(), true);
                                }
                                tooltip.setCodexEntryId(CodexDataV2.getIndustryEntryId(ind.getSpec().getId()));

                            }
                        },object, TooltipMakerAPI.TooltipLocation.RIGHT,false);
                    }
                }
            }
            else{
                injectForGrandColonies(mainPanel,market);
            }
            if(incomeInjector!=null){
                UIComponentAPI imigr = (UIComponentAPI) ReflectionUtilis.invokeMethodWithAutoProjection("getImmigration",incomeInjector);
                IncomePanel panel = new IncomePanel(market);
                HazardRatingPanel hazardRatingPanel = new HazardRatingPanel(market);
                incomeInjector.addComponent(panel.getMainPanel()).rightOfTop(imigr,5+panel.getMainPanel().getPosition().getWidth());
                incomeInjector.addComponent(hazardRatingPanel.getMainPanel()).belowMid(panel.getMainPanel(),18);
            }
            TooltipMakerAPI tl = Global.getSettings().createCustom(1,1,null).createUIElement(1,1,false);
            if(ctx.stabAccessPanel!=null){
                tl.addTooltipTo(new AccessibilityOnHover(market),  ReflectionUtilis.getChildrenCopy(ctx.stabAccessPanel).get(1), TooltipMakerAPI.TooltipLocation.BELOW,false);

            }
            mainPanel.getPosition().inTL(-xAxis,30);
            AoTDCommodityPanel newPanel = new AoTDCommodityPanel(toRemove.getPosition().getWidth()+xAxis+extraWidth,toRemove.getPosition().getHeight()+additionalHeight,market,false);
            newPanel.setMainColonyPanel(ctx.mainColonyPanel);
            mainPanel.removeComponent(toRemove);
            mainPanel.addComponent(newPanel.getMainPanel()).inBR(-xAxis-extraWidth,mainPanel.getPosition().getHeight()-610+additionalHeight);

        }
    }

    @Override
    public void onSubmarketCargoCreated(CargoPanelContextUI ctx) {

    }

    @Override
    public void onSurveyPanelCreated(SurveyPanelContextUI ctx) {

    }
    private void injectForGrandColonies(UIPanelAPI panelOfIndustries, MarketAPI market) {
        CustomPanelAPI panelAPI = (CustomPanelAPI) ReflectionUtilis.getChildrenCopy((UIPanelAPI) panelOfIndustries).stream().filter(x -> x instanceof CustomPanelAPI && x.getPosition().getWidth() == 830 && x.getPosition().getHeight() == 400).findFirst().orElse(null);
        TooltipMakerAPI tl = Global.getSettings().createCustom(1,1,null).createUIElement(1,1,false);
        if (panelAPI != null) {
            UIPanelAPI panelInsider = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(panelAPI).get(0);
            if (ReflectionUtilis.hasMethodOfName("getContentContainer", panelInsider)) {
                Object container = ReflectionUtilis.invokeMethodWithAutoProjection("getContentContainer", panelInsider);
                TooltipMakerAPI tooltip = (TooltipMakerAPI) ReflectionUtilis.getChildrenCopy((UIPanelAPI) container).get(0);
                UIPanelAPI contentInside = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(tooltip).get(0);
                ArrayList<CustomPanelAPI> insiders = new ArrayList<>();
                for (UIComponentAPI uiComponentAPI : ReflectionUtilis.getChildrenCopy(contentInside)) {
                    if (uiComponentAPI instanceof CustomPanelAPI panel) {
                        if(panel.getPlugin()!=null)continue;
                        insiders.add(panel);
                    }
                }
                HashMap<String, UIComponentAPI> widgetsToDraw = new HashMap<>();
                UIComponentAPI mainWidget = null;
                for (CustomPanelAPI insider : insiders) {
                    UIComponentAPI widget = ReflectionUtilis.getChildrenCopy(insider).get(0);
                    for (UIComponentAPI componentAPI : ReflectionUtilis.getChildrenCopy((UIPanelAPI) widget)) {
                        if(componentAPI instanceof IconGroupAPI){
                            ((UIPanelAPI) widget).removeComponent(componentAPI);
                            break;
                        }
                    }
                    Industry ind = (Industry) ReflectionUtilis.findFieldOfClass(widget, Industry.class);

                    String id = ind.getId();
                    tl.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return true;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 400;
                        }

                        @Override
                        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            IndustryOnHoverTooltipV2 v2 = new IndustryOnHoverTooltipV2(getTooltipWidth(tooltipParam),ind,expanded);
                            tooltip.addCustom(v2.getMainPanel(),0f);
                            if (ind.getSpec() != null && ind.getSpec().hasTag(Tags.CODEX_UNLOCKABLE)) {
                                SharedUnlockData.get().reportPlayerAwareOfIndustry(ind.getSpec().getId(), true);
                            }
                            tooltip.setCodexEntryId(CodexDataV2.getIndustryEntryId(ind.getSpec().getId()));

                        }
                    },widget, TooltipMakerAPI.TooltipLocation.RIGHT,false);

                }


            } else {
                UIPanelAPI contentInside = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(panelInsider).get(0);
                ArrayList<CustomPanelAPI> insiders = new ArrayList<>();
                for (UIComponentAPI uiComponentAPI : ReflectionUtilis.getChildrenCopy(contentInside)) {
                    if (uiComponentAPI instanceof CustomPanelAPI panel) {
                        if (panel.getPlugin() !=null) {
                            return;
                        }
                        insiders.add(panel);
                    }
                }
                insiders.size();
                HashMap<String, UIComponentAPI> widgetsToDraw = new HashMap<>();
                UIComponentAPI mainWidget = null;
                for (CustomPanelAPI insider : insiders) {
                    UIComponentAPI widget = ReflectionUtilis.getChildrenCopy(insider).get(0);
                    Industry ind = (Industry) ReflectionUtilis.findFieldOfClass(widget, Industry.class);
                    for (UIComponentAPI componentAPI : ReflectionUtilis.getChildrenCopy((UIPanelAPI) widget)) {
                        if(componentAPI instanceof IconGroupAPI){
                            ((UIPanelAPI) widget).removeComponent(componentAPI);
                            break;
                        }
                    }
                    tl.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return true;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 400;
                        }

                        @Override
                        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            IndustryOnHoverTooltipV2 v2 = new IndustryOnHoverTooltipV2(getTooltipWidth(tooltipParam),ind,expanded);
                            tooltip.addCustom(v2.getMainPanel(),0f);
                            if (ind.getSpec() != null && ind.getSpec().hasTag(Tags.CODEX_UNLOCKABLE)) {
                                SharedUnlockData.get().reportPlayerAwareOfIndustry(ind.getSpec().getId(), true);
                            }
                            tooltip.setCodexEntryId(CodexDataV2.getIndustryEntryId(ind.getSpec().getId()));

                        }
                    },widget, TooltipMakerAPI.TooltipLocation.RIGHT,false);

                }
            }


        }
    }
}
