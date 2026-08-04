package data.kaysaar.aotd.tot.ui.warehouses.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageItemsWidget implements ExtendedUIPanelPlugin {

    public enum Mode {
        COMMODITIES,
        AI_CORES,
        SPECIAL_ITEMS
    }

    protected CustomPanelAPI mainPanel;
    protected CustomPanelAPI contentPanel;

    protected MarketAPI market;
    protected Mode mode;

    protected float width;
    protected float height;

    protected float defaultIconSize = 22f;
    protected float separator = 3f;

    public StorageItemsWidget(float width, float height, MarketAPI market, Mode mode) {
        this.width = width;
        this.height = height;
        this.market = market;
        this.mode = mode == null ? Mode.COMMODITIES : mode;

        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        clearUI();

        contentPanel = Global.getSettings().createCustom(width, height, null);

        TooltipMakerAPI tooltip = contentPanel.createUIElement(width, height, false);

        // Reset tooltip offset for manual placement.
        tooltip.addSpacer(0f).getPosition().inTL(0f, 0f);

        List<IconEntry> entries = collectEntries();

        if (!entries.isEmpty()) {
            layoutIcons(tooltip, entries);
        }

        contentPanel.addUIElement(tooltip).inTL(0f, 0f);
        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
    }

    protected List<IconEntry> collectEntries() {
        List<IconEntry> result = new ArrayList<>();

        if (market == null) {
            return result;
        }

        CargoAPI cargo = Misc.getStorageCargo(market);
        if (cargo == null) {
            return result;
        }

        LinkedHashMap<String, IconEntry> byKey = new LinkedHashMap<>();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack == null) continue;
            if (stack.getSize() <= 0f) continue;

            if (mode == Mode.SPECIAL_ITEMS) {
                collectSpecialStack(stack, byKey);
            } else {
                collectCommodityStack(stack, byKey);
            }
        }

        result.addAll(byKey.values());

        result.sort(
                new Comparator<IconEntry>() {
                    @Override
                    public int compare(IconEntry o1, IconEntry o2) {
                        int orderCompare = Float.compare(o1.order, o2.order);
                        if (orderCompare != 0) {
                            return orderCompare;
                        }

                        return o1.id.compareToIgnoreCase(o2.id);
                    }
                });

        return result;
    }

    protected void collectCommodityStack(CargoStackAPI stack, Map<String, IconEntry> byKey) {
        if (!stack.isCommodityStack()) {
            return;
        }

        String commodityId = stack.getCommodityId();
        if (commodityId == null || commodityId.isEmpty()) {
            return;
        }

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
        if (spec == null) {
            return;
        }

        boolean aiCore = isAICore(commodityId, spec);

        if (mode == Mode.AI_CORES && !aiCore) {
            return;
        }

        if (mode == Mode.COMMODITIES && aiCore) {
            return;
        }

        IconEntry entry = new IconEntry();
        entry.mode = mode;
        entry.id = commodityId;
        entry.specialData = null;
        entry.quantity = stack.getSize();
        entry.iconName = spec.getIconName();
        entry.displayName = spec.getName();
        entry.order = spec.getOrder();

        mergeEntry(byKey, commodityId, entry);
    }

    protected void collectSpecialStack(CargoStackAPI stack, Map<String, IconEntry> byKey) {
        if (!stack.isSpecialStack()) {
            return;
        }

        SpecialItemData data = stack.getSpecialDataIfSpecial();
        if (data == null) {
            return;
        }

        String id = data.getId();
        if (id == null || id.isEmpty()) {
            return;
        }

        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
        if (spec == null) {
            return;
        }

        String dataString = data.getData();
        String key = id + "|" + (dataString == null ? "" : dataString);

        IconEntry entry = new IconEntry();
        entry.mode = Mode.SPECIAL_ITEMS;
        entry.id = id;
        entry.specialData = data;
        entry.quantity = stack.getSize();
        entry.iconName = spec.getIconName();
        entry.displayName = spec.getName();
        entry.order = spec.getOrder();

        mergeEntry(byKey, key, entry);
    }

    protected void mergeEntry(Map<String, IconEntry> byKey, String key, IconEntry entry) {
        IconEntry existing = byKey.get(key);

        if (existing == null) {
            byKey.put(key, entry);
        } else {
            existing.quantity += entry.quantity;
        }
    }

    protected void layoutIcons(TooltipMakerAPI tooltip, List<IconEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        List<IconEntry> visibleEntries = new ArrayList<>();
        for (IconEntry entry : entries) {
            if (entry == null) continue;
            if (entry.iconName == null || entry.iconName.isEmpty()) continue;
            visibleEntries.add(entry);
        }

        if (visibleEntries.isEmpty()) {
            return;
        }

        GridLayoutData layout = computeBestIconGrid(visibleEntries.size());
        if (layout == null || layout.iconSize <= 0f || layout.columns <= 0 || layout.rows <= 0) {
            return;
        }

        float gridHeight =
                layout.rows * layout.iconSize + Math.max(0, layout.rows - 1) * layout.separator;
        float startY = Math.max(0f, (height - gridHeight) / 2f);

        for (int index = 0; index < visibleEntries.size(); index++) {
            IconEntry entry = visibleEntries.get(index);

            int row = index / layout.columns;
            int column = index % layout.columns;

            int rowStartIndex = row * layout.columns;
            int entriesInThisRow = Math.min(layout.columns, visibleEntries.size() - rowStartIndex);

            float rowWidth =
                    entriesInThisRow * layout.iconSize
                            + Math.max(0, entriesInThisRow - 1) * layout.separator;
            float startX = Math.max(0f, (width - rowWidth) / 2f);

            float x = startX + column * (layout.iconSize + layout.separator);
            float y = startY + row * (layout.iconSize + layout.separator);

            ImageViewer viewer = new ImageViewer(layout.iconSize, layout.iconSize, entry.iconName);
            CustomPanelAPI iconPanel = viewer.getComponentPanel();

            tooltip.addCustom(iconPanel, 0f).getPosition().inTL(x, y);

            placeOnHoverTooltip(tooltip, iconPanel, entry);
        }
    }

    protected GridLayoutData computeBestIconGrid(int iconCount) {
        if (iconCount <= 0 || width <= 0f || height <= 0f) {
            return null;
        }

        float maxIconSize = Math.min(defaultIconSize, Math.min(width, height));
        if (maxIconSize <= 0f) {
            return null;
        }

        GridLayoutData best = null;

        /*
         * First try to keep the configured separator.
         * Also try 0 spacing as a fallback; this lets the widget preserve icon size
         * when a tiny gap would otherwise force a large downscale.
         */
        for (int separatorMode = 0; separatorMode < 2; separatorMode++) {
            float actualSeparator = separatorMode == 0 ? Math.max(0f, separator) : 0f;

            for (int columns = 1; columns <= iconCount; columns++) {
                int rows = (int) Math.ceil(iconCount / (float) columns);

                float usableWidth = width - Math.max(0, columns - 1) * actualSeparator;
                float usableHeight = height - Math.max(0, rows - 1) * actualSeparator;

                if (usableWidth <= 0f || usableHeight <= 0f) {
                    continue;
                }

                float iconByWidth = usableWidth / columns;
                float iconByHeight = usableHeight / rows;
                float iconSize = Math.min(maxIconSize, Math.min(iconByWidth, iconByHeight));

                if (iconSize <= 0f) {
                    continue;
                }

                GridLayoutData candidate =
                        new GridLayoutData(columns, rows, iconSize, actualSeparator);

                if (isBetterGrid(candidate, best)) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    protected boolean isBetterGrid(GridLayoutData candidate, GridLayoutData currentBest) {
        if (currentBest == null) {
            return true;
        }

        float epsilon = 0.01f;

        if (candidate.iconSize > currentBest.iconSize + epsilon) {
            return true;
        }

        if (candidate.iconSize < currentBest.iconSize - epsilon) {
            return false;
        }

        /*
         * At the same icon size, prefer fewer rows so the widget does not wrap
         * unnecessarily. This keeps a single row when it already fits.
         */
        if (candidate.rows != currentBest.rows) {
            return candidate.rows < currentBest.rows;
        }

        /*
         * At the same size and row count, keep the normal visual gap if possible.
         */
        if (candidate.separator != currentBest.separator) {
            return candidate.separator > currentBest.separator;
        }

        return candidate.columns > currentBest.columns;
    }

    /**
     * Override this if you want custom hover behavior.
     *
     * <p>Important: this is called immediately after tooltip.addCustom(iconPanel, 0f), so
     * tooltip.addTooltipToPrevious(...) will attach to the placed icon.
     */
    protected void placeOnHoverTooltip(
            TooltipMakerAPI tooltip, CustomPanelAPI component, IconEntry entry) {
        tooltip.addTooltipToPrevious(
                new TooltipMakerAPI.TooltipCreator() {
                    @Override
                    public boolean isTooltipExpandable(Object tooltipParam) {
                        return false;
                    }

                    @Override
                    public float getTooltipWidth(Object tooltipParam) {
                        return 350f;
                    }

                    @Override
                    public void createTooltip(
                            TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                        if (entry.specialData != null) {
                            CargoStackAPI stack =
                                    Global.getFactory()
                                            .createCargoStack(
                                                    CargoAPI.CargoItemType.SPECIAL,
                                                    entry.specialData,
                                                    null);
                            tooltip.addTitle(stack.getDisplayName());
                            tooltip.addPara(
                                    "Stored: %s",
                                    5f, Misc.getHighlightColor(), formatQuantity(entry.quantity));
                        } else {
                            tooltip.addTitle(entry.getDisplayName());

                            tooltip.addPara(
                                    "Stored: %s",
                                    5f, Misc.getHighlightColor(), formatQuantity(entry.quantity));
                        }
                    }
                },
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);
    }

    protected boolean isAICore(String commodityId, CommoditySpecAPI spec) {
        if (commodityId == null) {
            return false;
        }
        return spec.hasTag(Commodities.TAG_AI_CORE);
    }

    protected String formatQuantity(float quantity) {
        int rounded = Math.round(quantity);

        if (Math.abs(quantity - rounded) < 0.01f) {
            return Misc.getWithDGS(rounded);
        }

        return String.format("%.1f", quantity);
    }

    @Override
    public void clearUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}

    protected static class GridLayoutData {
        protected int columns;
        protected int rows;
        protected float iconSize;
        protected float separator;

        public GridLayoutData(int columns, int rows, float iconSize, float separator) {
            this.columns = columns;
            this.rows = rows;
            this.iconSize = iconSize;
            this.separator = separator;
        }
    }

    protected static class IconEntry {
        protected Mode mode;
        protected String id;
        protected SpecialItemData specialData;

        protected float quantity;
        protected String iconName;
        protected String displayName;
        protected float order;

        public String getDisplayName() {
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }

            return id == null ? "Unknown item" : id;
        }
    }
}
