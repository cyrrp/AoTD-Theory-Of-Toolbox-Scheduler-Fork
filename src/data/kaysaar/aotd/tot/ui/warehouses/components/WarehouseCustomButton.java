package data.kaysaar.aotd.tot.ui.warehouses.components;

import ashlib.data.plugins.ui.EntityWithNameComponent;
import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.HoldingsUtilis;
import java.awt.*;

public class WarehouseCustomButton extends CustomButton {
    CustomPanelAPI container;
    CustomPanelAPI contOriginal;

    public WarehouseCustomButton(
            float width,
            float height,
            Object buttonData,
            float indent,
            Color base,
            Color bg,
            Color bright) {
        super(width, height, buttonData, indent, base, bg, bright);
    }

    public MarketAPI getMarket() {
        return (MarketAPI) buttonData;
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        container = Global.getSettings().createCustom(this.width, this.height, null);
        contOriginal = Global.getSettings().createCustom(width, height, null);
        createContainerContent(container);
        contOriginal.addComponent(container).inTL(0, 0);
        tooltip.addCustom(contOriginal, 0f).getPosition().inTL(5, 0);
        float centerY = height / 2;
        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);
            tooltip.addCustom(panelIndicator, 0f)
                    .getPosition()
                    .inTL((float) WarehouseHoldingTable.widthMap.get("name") * 0.75f, centerY - 7);
        }
    }

    public void recreateContainer() {
        contOriginal.removeComponent(container);
        container = Global.getSettings().createCustom(width, height, null);
        createContainerContent(container);
        contOriginal.addComponent(container).inTL(0, 0);
    }

    public void createContainerContent(CustomPanelAPI container) {
        MarketAPI market = getMarket();
        EntityWithNameComponent component =
                new EntityWithNameComponent(
                        market.getPrimaryEntity(),
                        WarehouseHoldingTable.widthMap.get("name"),
                        50,
                        false);
        component.createUI();
        container.addComponent(component.getMainPanel()).inTL(-indent, 8);

        float startingX = WarehouseHoldingTable.getStartingX("location") - indent;
        Color c = Misc.getGrayColor();
        if (market.getStarSystem().getStar() != null) {
            c = market.getStarSystem().getStar().getSpec().getIconColor();
        }
        LocationPointerComponent component1 =
                new LocationPointerComponent(
                        market, WarehouseHoldingTable.widthMap.get("location"), height, c);
        container.addComponent(component1.getMainPanel()).inTL(startingX, 0);
        startingX = WarehouseHoldingTable.getStartingX("data") - indent;
        float widthSectionData = WarehouseHoldingTable.widthMap.get("data");
        float defIconSizeForThreeSections = 31;
        float available = (widthSectionData - (defIconSizeForThreeSections * 3 - 15)) - 100;

        StorageItemsWidget widgetRes =
                new StorageItemsWidget(
                        (available / 2) - 5, height, market, StorageItemsWidget.Mode.COMMODITIES);
        StorageItemsWidget widgetAI =
                new StorageItemsWidget(100 - 5, height, market, StorageItemsWidget.Mode.AI_CORES);
        StorageItemsWidget widgetItems =
                new StorageItemsWidget(
                        (available / 2) - 5, height, market, StorageItemsWidget.Mode.SPECIAL_ITEMS);
        container.addComponent(widgetRes.getMainPanel()).inTL(startingX, 0);
        container.addComponent(widgetAI.getMainPanel()).rightOfMid(widgetRes.getMainPanel(), 5);
        container.addComponent(widgetItems.getMainPanel()).rightOfMid(widgetAI.getMainPanel(), 5);
        CustomPanelAPI reference = Global.getSettings().createCustom(1, height, null);
        container.addComponent(reference).rightOfMid(widgetItems.getMainPanel(), 0);
        SubmarketPlugin plugin = Misc.getStorage(market);
        startingX = WarehouseHoldingTable.getStartingX("upkeep") + 5 - indent;
        float width = WarehouseHoldingTable.widthMap.get("upkeep");
        TooltipMakerAPI tooltipIncome = container.createUIElement(width, height, false);
        if (!plugin.getCargo().getMothballedShips().getMembersListCopy().isEmpty()) {
            ImageViewer viewer =
                    new ImageViewer(
                            20,
                            20,
                            Global.getSettings().getCommoditySpec(Commodities.SHIPS).getIconName());
            container.addComponent(viewer.getComponentPanel()).rightOfMid(reference, 10);
            tooltipIncome.addTooltipTo(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 450;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            tooltip.addSectionHeading(
                                    "Ships present in magazine", Alignment.MID, 0f);
                            tooltip.showShips(
                                    plugin.getCargo().getMothballedShips().getMembersListCopy(),
                                    50,
                                    true,
                                    5f);
                        }
                    },
                    viewer.getComponentPanel(),
                    TooltipMakerAPI.TooltipLocation.BELOW,
                    false);
        }
        if (!plugin.getCargo().getWeapons().isEmpty()) {
            ImageViewer viewer2 =
                    new ImageViewer(
                            20,
                            20,
                            Global.getSettings()
                                    .getCommoditySpec(Commodities.SHIP_WEAPONS)
                                    .getIconName());
            container.addComponent(viewer2.getComponentPanel()).rightOfMid(reference, 35);
            tooltipIncome.addTooltipTo(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 450;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            CargoAPI cargo = Global.getFactory().createCargo(true);
                            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                                if (stackAPI.isWeaponStack()) {
                                    cargo.addFromStack(stackAPI);
                                }
                            }
                            tooltip.addSectionHeading(
                                    "Weapons present in magazine", Alignment.MID, 0f);
                            tooltip.showCargo(cargo, 50, true, 5f);
                        }
                    },
                    viewer2.getComponentPanel(),
                    TooltipMakerAPI.TooltipLocation.BELOW,
                    false);
        }
        if (!plugin.getCargo().getFighters().isEmpty()) {
            ImageViewer viewer3 =
                    new ImageViewer(
                            20, 20, Global.getSettings().getSpriteName("ui", "fighter_lpc"));
            container.addComponent(viewer3.getComponentPanel()).rightOfMid(reference, 60);
            tooltipIncome.addTooltipTo(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 450;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            CargoAPI cargo = Global.getFactory().createCargo(true);
                            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                                if (stackAPI.isFighterWingStack()) {
                                    cargo.addFromStack(stackAPI);
                                }
                            }
                            tooltip.addSectionHeading(
                                    "Fighters present in magazine", Alignment.MID, 0f);
                            tooltip.showCargo(cargo, 50, true, 5f);
                        }
                    },
                    viewer3.getComponentPanel(),
                    TooltipMakerAPI.TooltipLocation.BELOW,
                    false);
        }

        float income = -HoldingsUtilis.getUpkeepForStorage(market);
        c = Color.ORANGE;
        if (income < 0) {
            c = Misc.getNegativeHighlightColor();
        }
        if (income == 0) {
            c = Misc.getGrayColor();
        }
        LabelAPI l = tooltipIncome.addPara(Misc.getDGSCredits(income), c, 0f);
        l.getPosition().inTL(0, (height / 2) - (l.computeTextHeight(l.getText()) / 2));
        l.setAlignment(Alignment.MID);
        container.addUIElement(tooltipIncome).inTL(startingX, 0);
    }
}
