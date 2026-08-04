package data.kaysaar.aotd.tot.ui.customprod.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpec;
import data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpecManager;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProductionCustomButton extends CustomButton {

    protected boolean isCreated = false;
    protected boolean isOrderMode = false;

    /**
     * Instance-owned column layout passed by ProductionBrowserSection.
     *
     * <p>This lets each custom button use whatever columns the current browser section declared,
     * without depending on static widths or static helper methods.
     */
    protected ProductionBrowserSection.ColumnLayout columnLayout;

    public ProductionCustomButton(float width, float height, AoTDProductionSpec buttonData) {
        this(width, height, buttonData, false, null);
    }

    public ProductionCustomButton(
            float width, float height, AoTDProductionSpec buttonData, boolean isOrderMode) {
        this(width, height, buttonData, isOrderMode, null);
    }

    public ProductionCustomButton(
            float width,
            float height,
            AoTDProductionSpec buttonData,
            ProductionBrowserSection.ColumnLayout columnLayout) {
        this(width, height, buttonData, false, columnLayout);
    }

    public ProductionCustomButton(
            float width,
            float height,
            AoTDProductionSpec buttonData,
            boolean isOrderMode,
            ProductionBrowserSection.ColumnLayout columnLayout) {
        super(
                width,
                height,
                buttonData,
                0f,
                Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor());

        this.isOrderMode = isOrderMode;
        this.columnLayout = columnLayout;
    }

    public boolean isCreated() {
        return isCreated;
    }

    public AoTDProductionSpec getSpec() {
        return (AoTDProductionSpec) buttonData;
    }

    public ProductionBrowserSection.ColumnLayout getColumnLayout() {
        return columnLayout;
    }

    public void setColumnLayout(ProductionBrowserSection.ColumnLayout columnLayout) {
        this.columnLayout = columnLayout;
    }

    protected boolean hasColumn(String id) {
        return columnLayout != null && columnLayout.hasColumn(id);
    }

    protected int getColumnWidth(String id) {
        if (columnLayout == null) return 0;
        return columnLayout.getWidth(id);
    }

    protected int getColumnStartX(String id) {
        if (columnLayout == null) return 0;
        return columnLayout.getStartX(id);
    }

    protected int getColumnEndX(String id) {
        if (columnLayout == null) return 0;
        return columnLayout.getEndX(id);
    }

    protected int getColumnGap() {
        if (columnLayout == null) return 0;
        return columnLayout.getGap();
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI container = Global.getSettings().createCustom(this.width, this.height, null);

        createContainerContent(container);

        isCreated = true;

        tooltip.addCustom(container, 0f).getPosition().inTL(5, 0);

        float centerY = height / 2f;

        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);

            if (hasColumn("name")) {
                tooltip.addCustom(panelIndicator, 0f)
                        .getPosition()
                        .inTL(
                                getColumnStartX("name") + getColumnWidth("name") * 0.75f,
                                centerY - 7f);
            }
        }
    }

    /**
     * Override in concrete buttons.
     *
     * <p>Example: add name panel at getColumnStartX("name") add FP panel at getColumnStartX("fp")
     */
    public void createContainerContent(CustomPanelAPI container) {}

    public CustomPanelAPI createCostSection(float width, float height) {
        CustomPanelAPI mainPanel = Global.getSettings().createCustom(width, height, null);

        ArrayList<CustomPanelAPI> panels = new ArrayList<>();
        float separatorX = 3f;
        float y = 5f;

        LinkedHashMap<String, Integer> orderedResources =
                getOrderedResourceMap(getSpec().getMapOfResourcesNeeded());

        orderedResources.forEach(
                (commodityId, amount) -> panels.add(createRowForItem(15, commodityId, amount)));

        if (panels.isEmpty()) {
            return mainPanel;
        }

        CustomPanelAPI centralized = Global.getSettings().createCustom(1, 1, null);
        mainPanel.addComponent(centralized).inTL(mainPanel.getPosition().getWidth() / 2f, 0);

        float totalWidth = 0f;
        for (CustomPanelAPI panel : panels) {
            totalWidth += panel.getPosition().getWidth();
        }
        totalWidth += separatorX * (panels.size() - 1);

        float startX = Math.max(0f, (width - totalWidth) * 0.5f);

        float currX = startX;
        for (CustomPanelAPI panel : panels) {
            mainPanel.addComponent(panel).inTL(currX, y);
            currX += panel.getPosition().getWidth() + separatorX;
        }

        LabelAPI labelAPI =
                Global.getSettings()
                        .createLabel(
                                Misc.getDGSCredits(getSpec().getProductionCost()),
                                Fonts.DEFAULT_SMALL);
        labelAPI.setColor(Color.ORANGE);
        labelAPI.getPosition()
                .setSize(
                        labelAPI.computeTextWidth(labelAPI.getText()),
                        labelAPI.computeTextHeight(labelAPI.getText()));

        mainPanel.addComponent((UIComponentAPI) labelAPI).belowMid(centralized, 20);

        return mainPanel;
    }

    public static CustomPanelAPI createRowForItem(float iconSize, String commodityId, int amount) {
        String displayAmount = formatCompactAmount(amount);

        if (Global.getSettings().getCommoditySpec(commodityId) != null) {
            CustomPanelAPI main = Global.getSettings().createCustom(iconSize * 3, iconSize, null);
            ImageViewer viewer =
                    new ImageViewer(
                            iconSize,
                            iconSize,
                            Global.getSettings().getCommoditySpec(commodityId).getIconName());

            main.addComponent(viewer.getComponentPanel()).inTL(0, 0);

            String toHighlight = displayAmount;
            LabelAPI label = Global.getSettings().createLabel(toHighlight, Fonts.DEFAULT_SMALL);
            label.setHighlight(toHighlight);
            label.setHighlightColor(Color.ORANGE);
            label.getPosition()
                    .setSize(
                            label.computeTextWidth(label.getText()),
                            label.computeTextHeight(label.getText()));

            float newWidth = iconSize + 2 + label.getPosition().getWidth();
            main.getPosition().setSize(newWidth, main.getPosition().getHeight());
            main.addComponent((UIComponentAPI) label).rightOfMid(viewer.getComponentPanel(), 2);

            return main;
        } else {
            CustomPanelAPI main = Global.getSettings().createCustom(iconSize * 3, iconSize, null);
            ImageViewer viewer =
                    new ImageViewer(
                            iconSize,
                            iconSize,
                            Global.getSettings().getSpecialItemSpec(commodityId).getIconName());

            main.addComponent(viewer.getComponentPanel()).inTL(0, 0);

            String toHighlight = displayAmount;
            LabelAPI label = Global.getSettings().createLabel(toHighlight, Fonts.DEFAULT_SMALL);
            label.setHighlight(toHighlight);
            label.setHighlightColor(
                    Misc.getDesignTypeColor(
                            Global.getSettings()
                                    .getSpecialItemSpec(commodityId)
                                    .getManufacturer()));
            label.getPosition()
                    .setSize(
                            label.computeTextWidth(label.getText()),
                            label.computeTextHeight(label.getText()));

            float newWidth = iconSize + 2 + label.getPosition().getWidth();
            main.getPosition().setSize(newWidth, main.getPosition().getHeight());
            main.addComponent((UIComponentAPI) label).rightOfMid(viewer.getComponentPanel(), 2);

            return main;
        }
    }

    public static LinkedHashMap<String, Integer> getOrderedResourceMap(Map<String, Integer> input) {
        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) return ordered;

        for (String orderedId : AoTDProductionSpecManager.orderedItemsForUI) {
            Integer amount = input.get(orderedId);
            if (amount != null && amount > 0) {
                ordered.put(orderedId, amount);
            }
        }

        input.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .filter(e -> !ordered.containsKey(e.getKey()))
                .sorted(
                        (a, b) -> {
                            String nameA = getDisplayNameForResource(a.getKey());
                            String nameB = getDisplayNameForResource(b.getKey());
                            return nameA.compareToIgnoreCase(nameB);
                        })
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));

        return ordered;
    }

    private static String getDisplayNameForResource(String commodityId) {
        if (commodityId == null) return "";

        if (Global.getSettings().getCommoditySpec(commodityId) != null) {
            return Global.getSettings().getCommoditySpec(commodityId).getName();
        }
        if (Global.getSettings().getSpecialItemSpec(commodityId) != null) {
            return Global.getSettings().getSpecialItemSpec(commodityId).getName();
        }

        return commodityId;
    }

    private static String formatCompactAmount(int amount) {
        if (amount < 1000) {
            return String.valueOf(amount);
        }

        if (amount < 1_000_000) {
            return formatWithSuffix(amount / 1000f, "k");
        }

        return formatWithSuffix(amount / 1_000_000f, "m");
    }

    private static String formatWithSuffix(float value, String suffix) {
        String formatted;

        if (value >= 100f) {
            formatted = String.valueOf((int) value);
        } else if (value >= 10f) {
            formatted = trimTrailingZeros(String.format(java.util.Locale.US, "%.1f", value));
        } else {
            formatted = trimTrailingZeros(String.format(java.util.Locale.US, "%.2f", value));
        }

        return formatted + suffix;
    }

    private static String trimTrailingZeros(String value) {
        if (!value.contains(".")) return value;

        while (value.endsWith("0")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }
}
