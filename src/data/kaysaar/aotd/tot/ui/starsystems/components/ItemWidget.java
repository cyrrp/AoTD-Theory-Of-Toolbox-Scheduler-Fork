package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.*;
import java.util.stream.Collectors;

public class ItemWidget implements ExtendedUIPanelPlugin {

    private CustomPanelAPI mainPanel;
    private CustomPanelAPI contentPanel;
    private final MarketAPI market;

    public ItemWidget(float width, float height, MarketAPI market) {
        this.market = market;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    private static final class IconEntry {
        final String id;
        final boolean isCommodity; // true = AI core commodity, false = special item

        IconEntry(String id, boolean isCommodity) {
            this.id = id;
            this.isCommodity = isCommodity;
        }

        String getIconName() {
            return isCommodity
                    ? Global.getSettings().getCommoditySpec(id).getIconName()
                    : Global.getSettings().getSpecialItemSpec(id).getIconName();
        }
    }

    private static final class GridLayoutData {
        int columns;
        int rows;
        float iconSize;
        float gapDifferent;
        float gapSame;

        GridLayoutData(int columns, int rows, float iconSize, float gapDifferent, float gapSame) {
            this.columns = columns;
            this.rows = rows;
            this.iconSize = iconSize;
            this.gapDifferent = gapDifferent;
            this.gapSame = gapSame;
        }
    }

    private static final class Row {
        final List<IconEntry> entries = new ArrayList<>();
        float rowWidth = 0f;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }

        float panelW = mainPanel.getPosition().getWidth();
        float panelH = mainPanel.getPosition().getHeight();

        contentPanel = Global.getSettings().createCustom(panelW, panelH, null);
        mainPanel.addComponent(contentPanel).inTL(0f, 0f);

        float maxIconSize = 22f;
        float gapDifferent = 2f;

        /*
         * Keep this behavior unchanged:
         * repeated identical icons overlap/stack instead of taking full width.
         */
        float gapSame = -12f;

        LinkedHashMap<String, Integer> aiCores = collectAndSortAICores(market);
        LinkedHashMap<String, Integer> items = collectAndSortSpecialItems(market);

        List<IconEntry> icons = new ArrayList<>();
        addExpanded(icons, aiCores, true);
        addExpanded(icons, items, false);

        if (icons.isEmpty()) return;

        GridLayoutData layout =
                computeBestIconGrid(icons, panelW, panelH, maxIconSize, gapDifferent, gapSame);

        if (layout == null || layout.iconSize <= 0f) {
            return;
        }

        layoutIconsCenteredGrid(icons, panelW, panelH, layout);
    }

    private void addExpanded(
            List<IconEntry> out, LinkedHashMap<String, Integer> map, boolean isCommodity) {
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            int count = (e.getValue() == null) ? 0 : e.getValue();
            if (count <= 0) continue;

            for (int i = 0; i < count; i++) {
                out.add(new IconEntry(e.getKey(), isCommodity));
            }
        }
    }

    private GridLayoutData computeBestIconGrid(
            List<IconEntry> icons,
            float width,
            float height,
            float maxIconSize,
            float gapDifferent,
            float gapSame) {
        if (icons == null || icons.isEmpty()) {
            return null;
        }

        if (width <= 0f || height <= 0f) {
            return null;
        }

        float maxSize = Math.min(maxIconSize, Math.min(width, height));
        if (maxSize <= 0f) {
            return null;
        }

        GridLayoutData best = null;
        int iconCount = icons.size();

        /*
         * First pass: normal gap.
         * Second pass: no different-item gap, as fallback, same as StorageItemsWidget logic.
         *
         * gapSame remains unchanged because that is the grouping/stacking behavior.
         */
        for (int separatorMode = 0; separatorMode < 2; separatorMode++) {
            float actualGapDifferent = separatorMode == 0 ? Math.max(0f, gapDifferent) : 0f;

            for (int columns = 1; columns <= iconCount; columns++) {
                List<Row> rowsAtMax =
                        buildRowsForColumns(icons, columns, maxSize, actualGapDifferent, gapSame);

                if (rowsAtMax.isEmpty()) {
                    continue;
                }

                int rows = rowsAtMax.size();

                float widestRowAtMax = getWidestRow(rowsAtMax);
                float totalHeightAtMax =
                        rows * maxSize + Math.max(0, rows - 1) * actualGapDifferent;

                float widthScale = widestRowAtMax <= 0f ? 1f : width / widestRowAtMax;
                float heightScale = totalHeightAtMax <= 0f ? 1f : height / totalHeightAtMax;

                float iconSize = Math.min(maxSize, maxSize * Math.min(widthScale, heightScale));

                if (iconSize <= 0f) {
                    continue;
                }

                List<Row> rowsAtSize =
                        buildRowsForColumns(icons, columns, iconSize, actualGapDifferent, gapSame);

                float widestRowAtSize = getWidestRow(rowsAtSize);
                float totalHeightAtSize =
                        rowsAtSize.size() * iconSize
                                + Math.max(0, rowsAtSize.size() - 1) * actualGapDifferent;

                if (widestRowAtSize > width + 0.01f) {
                    continue;
                }

                if (totalHeightAtSize > height + 0.01f) {
                    continue;
                }

                GridLayoutData candidate =
                        new GridLayoutData(
                                columns, rowsAtSize.size(), iconSize, actualGapDifferent, gapSame);

                if (isBetterGrid(candidate, best)) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private boolean isBetterGrid(GridLayoutData candidate, GridLayoutData currentBest) {
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
         * Same icon size: prefer fewer rows.
         * This keeps one row when it fits.
         */
        if (candidate.rows != currentBest.rows) {
            return candidate.rows < currentBest.rows;
        }

        /*
         * Same size and row count: prefer normal spacing over compressed spacing.
         */
        if (candidate.gapDifferent != currentBest.gapDifferent) {
            return candidate.gapDifferent > currentBest.gapDifferent;
        }

        /*
         * Same everything: prefer more columns so rows are filled more naturally.
         */
        return candidate.columns > currentBest.columns;
    }

    private List<Row> buildRowsForColumns(
            List<IconEntry> icons, int columns, float iconSize, float gapDifferent, float gapSame) {
        List<Row> rows = new ArrayList<>();

        if (columns <= 0 || icons == null || icons.isEmpty()) {
            return rows;
        }

        Row current = new Row();

        float rowX = 0f;
        String prevId = null;
        int column = 0;

        for (IconEntry icon : icons) {
            if (column >= columns) {
                current.rowWidth = rowX;
                rows.add(current);

                current = new Row();
                rowX = 0f;
                prevId = null;
                column = 0;
            }

            float gap = computeGap(rowX, prevId, icon.id, gapDifferent, gapSame);

            current.entries.add(icon);
            rowX += gap + iconSize;

            prevId = icon.id;
            column++;
        }

        if (!current.entries.isEmpty()) {
            current.rowWidth = rowX;
            rows.add(current);
        }

        return rows;
    }

    private float getWidestRow(List<Row> rows) {
        float widest = 0f;

        for (Row row : rows) {
            widest = Math.max(widest, row.rowWidth);
        }

        return widest;
    }

    private void layoutIconsCenteredGrid(
            List<IconEntry> icons, float width, float height, GridLayoutData layout) {
        List<Row> rows =
                buildRowsForColumns(
                        icons,
                        layout.columns,
                        layout.iconSize,
                        layout.gapDifferent,
                        layout.gapSame);

        if (rows.isEmpty()) {
            return;
        }

        float gridHeight =
                rows.size() * layout.iconSize + Math.max(0, rows.size() - 1) * layout.gapDifferent;

        float startY = Math.max(0f, (height - gridHeight) / 2f);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);

            float startX = Math.max(0f, (width - row.rowWidth) / 2f);
            float rowX = 0f;
            String prevId = null;
            TooltipMakerAPI tl =
                    Global.getSettings().createCustom(1, 1, null).createUIElement(1, 1, false);
            for (IconEntry icon : row.entries) {
                float gap = computeGap(rowX, prevId, icon.id, layout.gapDifferent, layout.gapSame);

                ImageViewer viewer =
                        new ImageViewer(layout.iconSize, layout.iconSize, icon.getIconName());
                if (!icon.isCommodity) {
                    CargoStackAPI stackAPI =
                            Global.getFactory()
                                    .createCargoStack(
                                            CargoAPI.CargoItemType.SPECIAL,
                                            new SpecialItemData(icon.id, null),
                                            null);
                    tl.addTooltipTo(
                            new TooltipMakerAPI.TooltipCreator() {
                                @Override
                                public boolean isTooltipExpandable(Object tooltipParam) {
                                    return false;
                                }

                                @Override
                                public float getTooltipWidth(Object tooltipParam) {
                                    return 400;
                                }

                                @Override
                                public void createTooltip(
                                        TooltipMakerAPI tooltip,
                                        boolean expanded,
                                        Object tooltipParam) {
                                    stackAPI.getPlugin()
                                            .createTooltip(tooltip, expanded, null, null);
                                }
                            },
                            viewer.getComponentPanel(),
                            TooltipMakerAPI.TooltipLocation.BELOW,
                            false);
                } else {
                    tl.addTooltipTo(
                            new TooltipMakerAPI.TooltipCreator() {
                                @Override
                                public boolean isTooltipExpandable(Object tooltipParam) {
                                    return false;
                                }

                                @Override
                                public float getTooltipWidth(Object tooltipParam) {
                                    return 400;
                                }

                                @Override
                                public void createTooltip(
                                        TooltipMakerAPI tooltip,
                                        boolean expanded,
                                        Object tooltipParam) {
                                    CommoditySpecAPI spec =
                                            Global.getSettings().getCommoditySpec(icon.id);
                                    tooltip.addTitle(spec.getName());
                                    tooltip.addPara(
                                            Global.getSettings()
                                                    .getDescription(
                                                            spec.getId(), Description.Type.RESOURCE)
                                                    .getText1FirstPara(),
                                            10f);
                                }
                            },
                            viewer.getComponentPanel(),
                            TooltipMakerAPI.TooltipLocation.BELOW,
                            false);
                }

                contentPanel
                        .addComponent(viewer.getComponentPanel())
                        .inTL(
                                startX + rowX + gap,
                                startY + rowIndex * (layout.iconSize + layout.gapDifferent));

                rowX += gap + layout.iconSize;
                prevId = icon.id;
            }
        }
    }

    private float computeGap(float x, String prevId, String id, float gapDifferent, float gapSame) {
        if (x <= 0f) return 0f;
        if (prevId == null) return gapDifferent;
        return prevId.equals(id) ? gapSame : gapDifferent;
    }

    @Override
    public void clearUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }
    }

    public LinkedHashMap<String, Integer> collectAndSortAICores(MarketAPI market) {
        Map<String, Integer> counts = new HashMap<>();

        for (Industry industry : market.getIndustries()) {
            String aiCoreId = industry.getAICoreId();
            if (AshMisc.isStringValid(aiCoreId)) {
                counts.merge(aiCoreId, 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(
                        (a, b) -> {
                            CommoditySpecAPI specA =
                                    Global.getSettings().getCommoditySpec(a.getKey());
                            CommoditySpecAPI specB =
                                    Global.getSettings().getCommoditySpec(b.getKey());
                            float orderA = specA != null ? specA.getOrder() : 0f;
                            float orderB = specB != null ? specB.getOrder() : 0f;
                            return Float.compare(orderB, orderA);
                        })
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                LinkedHashMap::new));
    }

    public LinkedHashMap<String, Integer> collectAndSortSpecialItems(MarketAPI market) {
        Map<String, Integer> counts = new HashMap<>();

        for (Industry industry : market.getIndustries()) {
            SpecialItemData data = industry.getSpecialItem();
            if (data == null) continue;

            String id = data.getId();
            if (id == null || id.isEmpty()) continue;

            counts.merge(id, 1, Integer::sum);
        }

        return counts.entrySet().stream()
                .sorted(
                        (a, b) -> {
                            SpecialItemSpecAPI specA =
                                    Global.getSettings().getSpecialItemSpec(a.getKey());
                            SpecialItemSpecAPI specB =
                                    Global.getSettings().getSpecialItemSpec(b.getKey());
                            float orderA = specA != null ? specA.getOrder() : 0f;
                            float orderB = specB != null ? specB.getOrder() : 0f;
                            return Float.compare(orderB, orderA);
                        })
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                LinkedHashMap::new));
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
}
