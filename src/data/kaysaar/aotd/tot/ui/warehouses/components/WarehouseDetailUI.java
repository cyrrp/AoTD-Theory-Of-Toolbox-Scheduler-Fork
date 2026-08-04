package data.kaysaar.aotd.tot.ui.warehouses.components;

import ashlib.data.plugins.ui.EntityWithNameComponent;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WarehouseDetailUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    MarketAPI market;
    UILinesRenderer renderer;
    CustomPanelAPI cargoPanel, cargoContentPanel;
    ButtonAPI res, ai_cores, items, weapons, fighters, ships;
    ButtonAPI currButton;
    boolean createdAtLeastOnce = false;

    public WarehouseDetailUI(float width, float height) {
        renderer = new UILinesRenderer(0f);
        mainPanel = Global.getSettings().createCustom(width, height, this);
        renderer.setPanel(mainPanel);
    }

    public void setMarket(MarketAPI market) {
        if (market == null) {
            this.market = null;
            return;
        }
        if (this.market == null || this.market != market) {
            this.market = market;
            createUI();
        }
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

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

        SubmarketPlugin plugin = Misc.getStorage(market);
        TooltipMakerAPI tooltipHeader =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 9000, false);
        tooltipHeader.setParaFont(Fonts.ORBITRON_20AA);
        tooltipHeader.addPara(market.getName(), 1f).setAlignment(Alignment.MID);
        tooltipHeader.setParaFont(Fonts.DEFAULT_SMALL);
        EntityWithNameComponent nameComponent =
                new EntityWithNameComponent(
                        market.getPrimaryEntity(),
                        contentPanel.getPosition().getWidth() - 5,
                        50,
                        true);
        nameComponent.createUI();
        tooltipHeader.addCustom(nameComponent.getMainPanel(), 3f);
        tooltipHeader
                .addPara(
                        "This market belongs to %s",
                        15f,
                        market.getTextColorForFactionOrPlanet(),
                        market.getFaction().getDisplayName())
                .setAlignment(Alignment.MID);
        if (!market.getFaction().isPlayerFaction()) {
            tooltipHeader.addRelationshipBar(
                    market.getFaction(), contentPanel.getPosition().getWidth() - 15, 5f);
            if (market.getFaction().getRelToPlayer().getRepInt() <= -25) {
                tooltipHeader
                        .addPara(
                                "We won't be able to access this storage with transponder on!",
                                Misc.getNegativeHighlightColor(),
                                5f)
                        .setAlignment(Alignment.MID);
            } else {
                tooltipHeader
                        .addPara(
                                "We are able to access this storage with transponder on!",
                                Misc.getPositiveHighlightColor(),
                                5f)
                        .setAlignment(Alignment.MID);
            }
        } else {
            tooltipHeader
                    .addPara(
                            "We are able to access this storage,due to it belonging to our faction!",
                            Misc.getPositiveHighlightColor(),
                            5f)
                    .setAlignment(Alignment.MID);
        }
        tooltipHeader
                .getPosition()
                .setSize(
                        contentPanel.getPosition().getWidth(), tooltipHeader.getHeightSoFar() + 30);
        float heightUsed = tooltipHeader.getHeightSoFar() + 30;
        UIComponentAPI componentAPI = tooltipHeader.addSpacer(28f);
        int bWidth = 72;
        res =
                tooltipHeader.addButton(
                        "Res.",
                        "res",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        bWidth,
                        20,
                        0f);
        ai_cores =
                tooltipHeader.addButton(
                        "AI cores",
                        "ai",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        bWidth,
                        20,
                        0);
        items =
                tooltipHeader.addButton(
                        "Items",
                        "items",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        bWidth,
                        20,
                        0);
        ships =
                tooltipHeader.addButton(
                        "Ships",
                        "ship",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        bWidth,
                        20,
                        0);
        weapons =
                tooltipHeader.addButton(
                        "Weapons",
                        "weapons",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        bWidth,
                        20,
                        0);
        fighters =
                tooltipHeader.addButton(
                        "Fighters",
                        "fighters",
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        bWidth,
                        20,
                        0);
        res.getPosition().rightOfMid(componentAPI, -8);
        ai_cores.getPosition().rightOfMid(res, 1);
        items.getPosition().rightOfMid(ai_cores, 1);
        ships.getPosition().rightOfMid(items, 1);
        weapons.getPosition().rightOfMid(ships, 1);
        fighters.getPosition().rightOfMid(weapons, 1);

        contentPanel.addUIElement(tooltipHeader).inTL(0, 0);

        float available = contentPanel.getPosition().getHeight() - heightUsed;
        cargoPanel =
                Global.getSettings()
                        .createCustom(contentPanel.getPosition().getWidth(), available, null);
        if (currButton != null) {
            createCargoSectionFromButton();
        }
        contentPanel.addComponent(cargoPanel).inTL(0, heightUsed);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
        createdAtLeastOnce = true;
    }

    public void createCargoSectionFromButton() {
        SubmarketPlugin plugin = Misc.getStorage(market);
        CargoAPI cargo = Global.getFactory().createCargo(true);
        String header = "";
        if (cargoContentPanel != null) {
            cargoPanel.removeComponent(cargoContentPanel);
        }
        if (currButton.getCustomData().equals(res.getCustomData())) {
            currButton = res;
            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                if (stackAPI.isCommodityStack()) {
                    CommoditySpecAPI specAPI =
                            Global.getSettings().getCommoditySpec(stackAPI.getCommodityId());
                    if (!specAPI.hasTag(Commodities.TAG_AI_CORE)) {
                        cargo.addFromStack(stackAPI);
                    }
                }
            }
            header = "Resources present in storage";
        }
        if (currButton.getCustomData().equals(ai_cores.getCustomData())) {
            currButton = ai_cores;
            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                if (stackAPI.isCommodityStack()) {
                    CommoditySpecAPI specAPI =
                            Global.getSettings().getCommoditySpec(stackAPI.getCommodityId());
                    if (specAPI.hasTag(Commodities.TAG_AI_CORE)) {
                        cargo.addFromStack(stackAPI);
                    }
                }
            }
            header = "Ai cores present in storage";
        }
        if (currButton.getCustomData().equals(items.getCustomData())) {
            currButton = items;
            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                if (stackAPI.isSpecialStack()) {
                    cargo.addFromStack(stackAPI);
                }
            }
            header = "Items present in storage";
        }
        if (currButton.getCustomData().equals(ships.getCustomData())) {
            currButton = ships;
            cargoContentPanel =
                    createShipSection(
                            plugin.getCargo().getMothballedShips().getMembersListCopy(),
                            cargoPanel.getPosition().getWidth(),
                            cargoPanel.getPosition().getHeight());
            cargoPanel.addComponent(cargoContentPanel).inTL(0, 0);
            return;
        }
        if (currButton.getCustomData().equals(weapons.getCustomData())) {
            currButton = weapons;
            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                if (stackAPI.isWeaponStack()) {
                    cargo.addFromStack(stackAPI);
                }
            }
            header = "Weapons present in storage";
        }
        if (currButton.getCustomData().equals(fighters.getCustomData())) {
            currButton = fighters;
            for (CargoStackAPI stackAPI : plugin.getCargo().getStacksCopy()) {
                if (stackAPI.isFighterWingStack()) {
                    cargo.addFromStack(stackAPI);
                }
            }
            header = "Fighters present in storage";
        }
        cargoContentPanel =
                createCargoSection(
                        header,
                        cargo,
                        cargoPanel.getPosition().getWidth(),
                        cargoPanel.getPosition().getHeight());
        cargoPanel.addComponent(cargoContentPanel).inTL(0, 0);
    }

    public CustomPanelAPI createShipSection(
            List<FleetMemberAPI> cargoToShow, float width, float heightTotal) {
        CustomPanelAPI panel = Global.getSettings().createCustom(width, heightTotal, null);
        TooltipMakerAPI tooltipHeader =
                panel.createUIElement(panel.getPosition().getWidth(), 20, false);
        tooltipHeader.addSectionHeading("Ships present in storage", Alignment.MID, 0f);
        panel.addUIElement(tooltipHeader).inTL(0, 0);
        TooltipMakerAPI tooltipContent =
                panel.createUIElement(panel.getPosition().getWidth(), heightTotal - 25, true);
        tooltipContent.showShips(cargoToShow, 100, true, 0f);
        panel.addUIElement(tooltipContent).inTL(0, 25);
        return panel;
    }

    public CustomPanelAPI createCargoSection(
            String headerName, CargoAPI cargoToShow, float width, float heightTotal) {
        CustomPanelAPI panel = Global.getSettings().createCustom(width, heightTotal, null);

        TooltipMakerAPI tooltipHeader = panel.createUIElement(width, 20f, false);
        tooltipHeader.addSectionHeading(headerName, Alignment.MID, 0f);
        panel.addUIElement(tooltipHeader).inTL(0f, 0f);

        TooltipMakerAPI tooltipContent = panel.createUIElement(width, heightTotal - 25f, true);

        int chunkSize = 100;

        List<CargoStackAPI> stacks = cargoToShow.getStacksCopy();

        Collections.sort(
                stacks,
                new Comparator<CargoStackAPI>() {
                    @Override
                    public int compare(CargoStackAPI a, CargoStackAPI b) {
                        int sizeCompare = Float.compare(b.getSize(), a.getSize());
                        if (sizeCompare != 0) return sizeCompare;

                        return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                    }
                });

        for (int i = 0; i < stacks.size(); i += chunkSize) {
            CargoAPI cargoCreated = Global.getFactory().createCargo(true);

            int end = Math.min(i + chunkSize, stacks.size());

            for (int j = i; j < end; j++) {
                CargoStackAPI stack = stacks.get(j);
                if (stack == null || stack.getSize() <= 0f) continue;

                cargoCreated.addItems(stack.getType(), stack.getData(), stack.getSize());
            }

            int amountToShow = end - i;

            // IMPORTANT:
            // amountToShow is always <= 100.
            // This prevents showCargo() from switching to single-column mode.
            tooltipContent.showCargo(cargoCreated, amountToShow, true, 0f);

            if (end < stacks.size()) {
                tooltipContent.addSpacer(5f);
            }
        }

        if (stacks.isEmpty()) {
            tooltipContent.addPara("None", 10f);
        }

        panel.addUIElement(tooltipContent).inTL(0f, 25f);
        return panel;
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {
        renderer.render(alphaMult);
    }

    @Override
    public void advance(float amount) {
        if (createdAtLeastOnce) {
            checkForButton(res);
            checkForButton(ai_cores);
            checkForButton(items);
            checkForButton(ships);
            checkForButton(fighters);
            checkForButton(weapons);
            if (currButton != null) {
                currButton.highlight();
            }
        }
    }

    public void checkForButton(ButtonAPI button) {
        if (button.isChecked()) {
            button.setChecked(false);
            if (currButton != null) {
                currButton.unhighlight();
            }

            currButton = button;
            createCargoSectionFromButton();
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
