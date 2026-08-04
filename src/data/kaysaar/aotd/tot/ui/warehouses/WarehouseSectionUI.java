package data.kaysaar.aotd.tot.ui.warehouses;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.kaysaar.aotd.tot.ui.warehouses.components.WarehouseDetailUI;
import data.kaysaar.aotd.tot.ui.warehouses.components.WarehouseHoldingTable;
import java.util.List;

public class WarehouseSectionUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    WarehouseHoldingTable warehouseHoldingTable;
    WarehouseDetailUI detailUI;

    public WarehouseSectionUI(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
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
        float detailedSectionWidth = 460;
        float restWidth = contentPanel.getPosition().getWidth() - detailedSectionWidth - 10;
        if (restWidth > WarehouseHoldingTable.getWidth()) {
            WarehouseHoldingTable.reDestributeAdditionalWidth(
                    restWidth - WarehouseHoldingTable.getWidth());
        }
        if (warehouseHoldingTable == null) {
            warehouseHoldingTable =
                    new WarehouseHoldingTable(
                            WarehouseHoldingTable.getWidth() + 13,
                            contentPanel.getPosition().getHeight(),
                            true,
                            0,
                            0);
            warehouseHoldingTable.createSections();
            warehouseHoldingTable.createTable();
        }
        if (detailUI == null) {
            detailUI =
                    new WarehouseDetailUI(
                            detailedSectionWidth - 10, contentPanel.getPosition().getHeight());
        }
        contentPanel.addComponent(warehouseHoldingTable.mainPanel).inTL(0, 0);
        contentPanel
                .addComponent(detailUI.getMainPanel())
                .inTL(contentPanel.getPosition().getWidth() - 455, 0);

        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (warehouseHoldingTable != null && detailUI != null) {
            detailUI.setMarket(warehouseHoldingTable.currentlyChosenMarket);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
