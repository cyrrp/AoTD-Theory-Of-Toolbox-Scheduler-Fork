package data.kaysaar.aotd.tot.ui.customprod.components;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.produciton.AoTDProductionUIData;
import data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpec;
import data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpecManager;
import data.kaysaar.aotd.tot.ui.customprod.common.*;
import data.kaysaar.aotd.vok.misc.AoTDMisc;
import data.kaysaar.aotd.vok.scripts.specialprojects.BlackSiteProjectManager;
import data.kaysaar.aotd.vok.scripts.specialprojects.models.ProjectReward;
import data.kaysaar.aotd.vok.ui.customprod.buttons.FighterProductionCustomButton;
import data.kaysaar.aotd.vok.ui.customprod.buttons.ItemProductionCustomButton;
import data.kaysaar.aotd.vok.ui.customprod.buttons.ShipProductionCustomButton;
import data.kaysaar.aotd.vok.ui.customprod.buttons.WeaponProductionCustomButton;
import java.util.*;
import org.lwjgl.input.Keyboard;

public class ProductionBrowserSection implements ExtendedUIPanelPlugin {
    protected TextFieldAPI textField;
    protected ProductionOptionList list;
    protected ChooseManufacturerPanel chooseManufacturerPanel;
    protected ChooseSizePanel chooseSizePanel;
    protected ChooseTypeInfo chooseTypeInfo;
    protected LinkedHashMap<String, Integer> sizes = new LinkedHashMap<>();
    protected LinkedHashMap<String, Integer> types = new LinkedHashMap<>();
    protected LinkedHashMap<String, Integer> manus = new LinkedHashMap<>();
    protected CustomPanelAPI mainPanel;
    protected CustomPanelAPI contentPanel;

    protected AoTDProductionSpec.AoTDProductionSpecType prodType;

    protected ButtonAPI name;
    protected ButtonAPI time;
    protected ButtonAPI size;
    protected ButtonAPI type;
    protected ButtonAPI design;
    protected ButtonAPI totalCost;

    protected final LinkedHashMap<String, ButtonAPI> headerButtons = new LinkedHashMap<>();

    protected List<ColumnDefinition> columnDefinitions = new ArrayList<>();
    protected ColumnLayout columnLayout;

    protected String prevText = "";

    protected Boolean isPressingShift = false;
    protected Boolean isPressingCtrl = false;

    public ProductionBrowserSection(
            float width, float height, AoTDProductionSpec.AoTDProductionSpecType prodType) {
        this.prodType = prodType;

        this.mainPanel = Global.getSettings().createCustom(width, height, this);
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    /**
     * Main override point.
     *
     * <p>Extend this class and override this method if you want different columns, different
     * labels, different ratios, or different sorting.
     */
    protected List<ColumnDefinition> createColumnDefinitions(
            AoTDProductionSpec.AoTDProductionSpecType prodType) {
        ArrayList<ColumnDefinition> columns = new ArrayList<>();

        switch (prodType) {
            case SHIP:
            case WEAPON:
                columns.add(
                        new ColumnDefinition(
                                "name",
                                "Name",
                                0.28f,
                                Comparator.comparing(o -> o.getSpec().getName())));
                columns.add(
                        new ColumnDefinition(
                                "time",
                                "Time",
                                0.10f,
                                Comparator.comparing(o -> o.getSpec().getDaysToBeCreated())));
                columns.add(
                        new ColumnDefinition(
                                "size",
                                "Size",
                                0.10f,
                                Comparator.comparingInt(
                                        o -> {
                                            AoTDProductionSpec spec = o.getSpec();

                                            if (spec.getUnderlyingSpec()
                                                    instanceof ShipHullSpecAPI ship) {
                                                return hullSizeRank(ship.getHullSize());
                                            }
                                            if (spec.getUnderlyingSpec()
                                                    instanceof WeaponSpecAPI weapon) {
                                                return weaponSizeRank(weapon.getSize());
                                            }

                                            return Integer.MAX_VALUE;
                                        })));
                columns.add(
                        new ColumnDefinition(
                                "type",
                                "Type",
                                0.10f,
                                Comparator.comparing(o -> o.getSpec().getTypeString())));
                columns.add(
                        new ColumnDefinition(
                                "design",
                                "Design type",
                                0.25f,
                                Comparator.comparing(o -> o.getSpec().getManufacturer())));
                columns.add(
                        new ColumnDefinition(
                                "totalCost",
                                "Cost",
                                0.17f,
                                Comparator.comparing(o -> o.getSpec().getMoneyPrice())));
                break;

            case FIGHTER:
                columns.add(
                        new ColumnDefinition(
                                "name",
                                "Name",
                                0.34f,
                                Comparator.comparing(o -> o.getSpec().getName())));
                columns.add(
                        new ColumnDefinition(
                                "time",
                                "Time",
                                0.10f,
                                Comparator.comparing(o -> o.getSpec().getDaysToBeCreated())));
                columns.add(
                        new ColumnDefinition(
                                "type",
                                "Type",
                                0.14f,
                                Comparator.comparing(o -> o.getSpec().getTypeString())));
                columns.add(
                        new ColumnDefinition(
                                "design",
                                "Design type",
                                0.24f,
                                Comparator.comparing(o -> o.getSpec().getManufacturer())));
                columns.add(
                        new ColumnDefinition(
                                "totalCost",
                                "Cost",
                                0.18f,
                                Comparator.comparing(o -> o.getSpec().getMoneyPrice())));
                break;

            case SPECIAL_ITEM:
            case COMMODITY_ITEM:
                columns.add(
                        new ColumnDefinition(
                                "name",
                                "Name",
                                0.42f,
                                Comparator.comparing(o -> o.getSpec().getName())));
                columns.add(
                        new ColumnDefinition(
                                "time",
                                "Time",
                                0.12f,
                                Comparator.comparing(o -> o.getSpec().getDaysToBeCreated())));
                columns.add(
                        new ColumnDefinition(
                                "design",
                                "Design type",
                                0.26f,
                                Comparator.comparing(o -> o.getSpec().getManufacturer())));
                columns.add(
                        new ColumnDefinition(
                                "totalCost",
                                "Cost",
                                0.20f,
                                Comparator.comparing(o -> o.getSpec().getMoneyPrice())));
                break;

            default:
                columns.add(
                        new ColumnDefinition(
                                "name",
                                "Name",
                                0.42f,
                                Comparator.comparing(o -> o.getSpec().getName())));
                columns.add(
                        new ColumnDefinition(
                                "time",
                                "Time",
                                0.12f,
                                Comparator.comparing(o -> o.getSpec().getDaysToBeCreated())));
                columns.add(
                        new ColumnDefinition(
                                "design",
                                "Design type",
                                0.26f,
                                Comparator.comparing(o -> o.getSpec().getManufacturer())));
                columns.add(
                        new ColumnDefinition(
                                "totalCost",
                                "Cost",
                                0.20f,
                                Comparator.comparing(o -> o.getSpec().getMoneyPrice())));
                break;
        }

        return columns;
    }

    public ColumnLayout getColumnLayout() {
        return columnLayout;
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

    protected ButtonAPI getHeaderButton(String id) {
        return headerButtons.get(id);
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }

        resetHeaderButtons();

        columnDefinitions = createColumnDefinitions(prodType);
        if (columnDefinitions == null) {
            columnDefinitions = new ArrayList<>();
        }

        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);

        TooltipMakerAPI tl = contentPanel.createUIElement(250, 20, false);
        TooltipMakerAPI buttonTl =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 20, false);

        int gap = getColumnGap();
        float usablePanelWidth = getUsableHeaderWidth();

        columnLayout = new ColumnLayout(columnDefinitions, usablePanelWidth, gap);

        ButtonAPI previous = null;

        for (ColumnDefinition column : columnDefinitions) {
            if (column == null || column.id == null) continue;

            int buttonWidth = columnLayout.getWidth(column.id);

            ButtonAPI btn =
                    buttonTl.addAreaCheckbox(
                            column.label,
                            UITableImpl.SortingState.NON_INITIALIZED,
                            Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(),
                            Misc.getBrightPlayerColor(),
                            buttonWidth,
                            20,
                            0f);

            if (previous == null) {
                btn.getPosition().inTL(0, 0);
            } else {
                btn.getPosition().rightOfMid(previous, gap);
            }

            headerButtons.put(column.id, btn);

            previous = btn;
        }

        ButtonAPI initialSortButton = headerButtons.get(getInitialSortColumnId());
        if (initialSortButton != null) {
            initialSortButton.setCustomData(getInitialSortStateBeforeFirstToggle());
            initialSortButton.setChecked(true);
        }

        contentPanel.addUIElement(buttonTl).inTL(0, 26);

        textField = tl.addTextField(250, 20, Fonts.DEFAULT_SMALL, 0f);
        contentPanel.addUIElement(tl).inTL(contentPanel.getPosition().getWidth() - 265, 0);

        AoTDProductionUIData.populateByType(prodType);

        float remHeight =
                contentPanel.getPosition().getHeight()
                        - 64
                        - tl.getPosition().getHeight()
                        - 30
                        - 20;
        float heightOfManus = getManufacturerPanelHeight();
        float sectionsHeight = remHeight - heightOfManus;

        if (list == null) {
            list =
                    createProductionOptionList(
                            getSpecsToShow(),
                            contentPanel.getPosition().getWidth(),
                            sectionsHeight,
                            prodType);

            list.setFilter(createListFilter());

            ColumnDefinition initialColumn = getColumnDefinition(getInitialSortColumnId());
            if (initialSortButton != null && initialColumn != null) {
                handleSortButton(initialSortButton, initialColumn.comparator);
            }
        }

        list.getButtonsStorage().forEach(x -> x.setListener(createListenerForButton(x)));
        list.getButtonsStorage()
                .forEach(
                        x -> {
                            manus.merge(x.getSpec().getManufacturer(), 1, Integer::sum);
                            manus.merge("All Designs", 1, Integer::sum);

                            sizes.merge(x.getSpec().getSize(), 1, Integer::sum);
                            sizes.merge("All Sizes", 1, Integer::sum);

                            types.merge(x.getSpec().getTypeString(), 1, Integer::sum);
                            types.merge("All Types", 1, Integer::sum);
                        });
        manus = AshMisc.sortByValueDescending(manus);
        sizes = AshMisc.sortByValueDescending(sizes);
        types = AshMisc.sortByValueDescending(types);

        list.createUI();
        contentPanel.addComponent(list.getMainPanel()).inTL(-5, 50);

        createManufacturerPanel(sectionsHeight, heightOfManus);
        createFilterPanels();
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    protected void createFilterPanels() {
        if (shouldShowTypeChooser()) {
            chooseTypeInfo = new ChooseTypeInfo(contentPanel.getPosition().getWidth(), types);
            contentPanel
                    .addComponent(chooseTypeInfo.getMainPanel())
                    .inTL(
                            0,
                            contentPanel.getPosition().getHeight()
                                    - 30
                                    - 4
                                    - chooseTypeInfo.getMainPanel().getPosition().getHeight());
        }

        if (shouldShowSizeChooser()) {
            chooseSizePanel = new ChooseSizePanel(contentPanel.getPosition().getWidth(), sizes);
            contentPanel
                    .addComponent(chooseSizePanel.getMainPanel())
                    .inTL(
                            0,
                            contentPanel.getPosition().getHeight()
                                    - chooseSizePanel.getMainPanel().getPosition().getHeight());
        }
    }

    protected void createManufacturerPanel(float sectionsHeight, float heightOfManus) {
        chooseManufacturerPanel =
                new ChooseManufacturerPanel(
                        contentPanel.getPosition().getWidth(), heightOfManus, manus);

        contentPanel
                .addComponent(chooseManufacturerPanel.getMainPanel())
                .inTL(0, 55 + sectionsHeight + 5);
    }

    protected ProductionOptionList createProductionOptionList(
            List<AoTDProductionSpec> specs,
            float width,
            float height,
            AoTDProductionSpec.AoTDProductionSpecType prodType) {
        return new ProductionOptionList(specs, width, height, prodType) {
            @Override
            public ProductionCustomButton createButtonForType(
                    AoTDProductionSpec.AoTDProductionSpecType type,
                    float width,
                    float height,
                    AoTDProductionSpec spec) {
                return ProductionBrowserSection.this.createProductionButtonForType(
                        type, width, height, spec);
            }
        };
    }

    /**
     * Override this if your custom section needs custom buttons that know about the layout.
     *
     * <p>Example: return new MyShipButton(width, height, spec, getColumnLayout());
     */
    protected ProductionCustomButton createProductionButtonForType(
            AoTDProductionSpec.AoTDProductionSpecType type,
            float width,
            float height,
            AoTDProductionSpec spec) {
        switch (prodType) {
            case SHIP:
                if (!BlackSiteProjectManager.getProjectMatchingRewardThroughSpec(
                                ProjectReward.ProjectRewardType.SHIP, spec.getId())
                        .isEmpty()) {
                    return null;
                }
                return new ShipProductionCustomButton(width, height, spec);

            case WEAPON:
                if (!BlackSiteProjectManager.getProjectMatchingRewardThroughSpec(
                                ProjectReward.ProjectRewardType.WEAPON, spec.getId())
                        .isEmpty()) {
                    return null;
                }
                return new WeaponProductionCustomButton(width, height, spec);

            case FIGHTER:
                if (!BlackSiteProjectManager.getProjectMatchingRewardThroughSpec(
                                ProjectReward.ProjectRewardType.FIGHTER, spec.getId())
                        .isEmpty()) {
                    return null;
                }
                return new FighterProductionCustomButton(width, height, spec);

            case SPECIAL_ITEM:
                if (!BlackSiteProjectManager.getProjectMatchingRewardThroughSpec(
                                ProjectReward.ProjectRewardType.ITEM, spec.getId())
                        .isEmpty()) {
                    return null;
                }
                return new ItemProductionCustomButton(width, height, spec);

            case COMMODITY_ITEM:
                if (!BlackSiteProjectManager.getProjectMatchingRewardThroughSpec(
                                ProjectReward.ProjectRewardType.AICORE, spec.getId())
                        .isEmpty()) {
                    return null;
                }
                return new ItemProductionCustomButton(width, height, spec);

            default:
                return new ProductionCustomButton(width, height, spec);
        }
    }

    protected ProductionMenuFilterAPI createListFilter() {
        return new ProductionMenuFilterAPI() {
            @Override
            public void pruneList(ArrayList<ProductionCustomButton> buttons) {
                String rawSearch = textField.getText();

                if (AshMisc.isStringValid(rawSearch)) {
                    pruneBySearch(buttons, rawSearch);
                } else {
                    pruneByFilterPanels(buttons);
                }
            }
        };
    }

    protected void pruneBySearch(ArrayList<ProductionCustomButton> buttons, String rawSearch) {
        final String searchString = rawSearch.toLowerCase().trim();
        final int threshold = getSearchLevenshteinThreshold();

        buttons.removeIf(
                button -> {
                    String candidate = getSearchString(button);
                    if (candidate == null) return true;

                    String normalized = candidate.toLowerCase();
                    int distance = AoTDMisc.levenshteinDistance(searchString, normalized);

                    return distance > threshold && !normalized.contains(searchString);
                });

        buttons.sort(
                (b1, b2) -> {
                    String s1 = getSearchString(b1);
                    String s2 = getSearchString(b2);

                    if (s1 == null) s1 = "";
                    if (s2 == null) s2 = "";

                    String s1S = s1.toLowerCase();
                    String s2S = s2.toLowerCase();

                    boolean s1Contains = s1S.contains(searchString);
                    boolean s2Contains = s2S.contains(searchString);

                    if (s1Contains && !s2Contains) return -1;
                    if (!s1Contains && s2Contains) return 1;

                    int distance1 = AoTDMisc.levenshteinDistance(searchString, s1S);
                    int distance2 = AoTDMisc.levenshteinDistance(searchString, s2S);

                    int cmp = Integer.compare(distance1, distance2);
                    if (cmp != 0) return cmp;

                    return s1S.compareTo(s2S);
                });
    }

    protected void pruneByFilterPanels(ArrayList<ProductionCustomButton> buttons) {
        buttons.removeIf(
                button -> {
                    AoTDProductionSpec spec = button.getSpec();

                    if (chooseManufacturerPanel != null
                            && !chooseManufacturerPanel.isManufacturerChosen(
                                    spec.getManufacturer())) {
                        return true;
                    }
                    if (chooseTypeInfo != null
                            && !chooseTypeInfo.isTypeChosen(spec.getTypeString())) {
                        return true;
                    }
                    if (chooseSizePanel != null && !chooseSizePanel.isSizeChosen(spec.getSize())) {
                        return true;
                    }

                    return false;
                });
    }

    protected String getSearchString(ProductionCustomButton button) {
        return button.getSpec().getName();
    }

    protected int getSearchLevenshteinThreshold() {
        return 2;
    }

    protected List<AoTDProductionSpec> getSpecsToShow() {
        return AoTDProductionSpecManager.getLearnedSpecsForFaction(
                prodType, Global.getSector().getPlayerFaction());
    }

    protected boolean shouldShowTypeChooser() {
        return prodType != AoTDProductionSpec.AoTDProductionSpecType.SPECIAL_ITEM;
    }

    protected boolean shouldShowSizeChooser() {
        return prodType == AoTDProductionSpec.AoTDProductionSpecType.WEAPON
                || prodType == AoTDProductionSpec.AoTDProductionSpecType.SHIP;
    }

    protected float getManufacturerPanelHeight() {
        return 180f;
    }

    protected int getColumnGap() {
        return 1;
    }

    protected float getUsableHeaderWidth() {
        return contentPanel.getPosition().getWidth() - 10f;
    }

    protected String getInitialSortColumnId() {
        return "totalCost";
    }

    protected UITableImpl.SortingState getInitialSortStateBeforeFirstToggle() {
        return UITableImpl.SortingState.ASCENDING;
    }

    public CustomButton.ButtonEventListener createListenerForButton(
            ProductionCustomButton customButton) {
        return null;
    }

    public void setProdType(AoTDProductionSpec.AoTDProductionSpecType prodType) {
        this.prodType = prodType;
        clearUI();
        createUI();
    }

    @Override
    public void clearUI() {
        if (chooseManufacturerPanel != null) {
            chooseManufacturerPanel.clearUI();
            chooseManufacturerPanel = null;
        }
        if (chooseTypeInfo != null) {
            chooseTypeInfo.clearUI();
            chooseTypeInfo = null;
        }
        if (chooseSizePanel != null) {
            chooseSizePanel.clearUI();
            chooseSizePanel = null;
        }
        if (list != null) {
            list.getButtonsStorage().forEach(x -> x.setListener(null));
            list.clearUI();
            list = null;
        }
        sizes.clear();
        manus.clear();
        types.clear();

        resetHeaderButtons();
        columnLayout = null;
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (list == null) return;

        if (chooseManufacturerPanel != null && chooseManufacturerPanel.isNeedsUpdate()) {
            chooseManufacturerPanel.setNeedsUpdate(false);
            list.createUI();
        }
        if (chooseSizePanel != null && chooseSizePanel.isNeedsUpdate()) {
            chooseSizePanel.setNeedsUpdate(false);
            list.createUI();
        }
        if (chooseTypeInfo != null && chooseTypeInfo.isNeedsUpdate()) {
            chooseTypeInfo.setNeedsUpdate(false);
            list.createUI();
        }
        if (textField != null && !prevText.equals(textField.getText())) {
            prevText = textField.getText();
            list.createUI();
        }

        for (ColumnDefinition column : columnDefinitions) {
            if (column == null || column.id == null) continue;
            handleSortButton(headerButtons.get(column.id), column.comparator);
        }
    }

    protected void handleSortButton(
            ButtonAPI button, Comparator<ProductionCustomButton> comparator) {
        if (button == null || comparator == null) return;
        if (!button.isChecked()) return;

        button.setChecked(false);

        UITableImpl.SortingState current = (UITableImpl.SortingState) button.getCustomData();
        UITableImpl.SortingState newState = AshMisc.switchState(current);

        AshMisc.sortByState(list.getButtonsStorage(), newState, comparator);

        button.setCustomData(newState);
        list.createUI();
    }

    protected ColumnDefinition getColumnDefinition(String id) {
        if (id == null) return null;

        for (ColumnDefinition column : columnDefinitions) {
            if (column == null) continue;
            if (id.equals(column.id)) return column;
        }

        return null;
    }

    protected void resetHeaderButtons() {
        headerButtons.clear();

        name = null;
        time = null;
        size = null;
        type = null;
        design = null;
        totalCost = null;
    }

    protected void assignLegacyButtonField(String columnId, ButtonAPI button) {
        if ("name".equals(columnId)) {
            name = button;
        } else if ("time".equals(columnId)) {
            time = button;
        } else if ("size".equals(columnId)) {
            size = button;
        } else if ("type".equals(columnId)) {
            type = button;
        } else if ("design".equals(columnId)) {
            design = button;
        } else if ("totalCost".equals(columnId)) {
            totalCost = button;
        }
    }

    protected int hullSizeRank(ShipAPI.HullSize size) {
        if (size == null) return Integer.MAX_VALUE;

        switch (size) {
            case FIGHTER:
                return 0;
            case FRIGATE:
                return 1;
            case DESTROYER:
                return 2;
            case CRUISER:
                return 3;
            case CAPITAL_SHIP:
                return 4;
            default:
                return Integer.MAX_VALUE;
        }
    }

    protected int weaponSizeRank(WeaponAPI.WeaponSize size) {
        if (size == null) return Integer.MAX_VALUE;

        switch (size) {
            case SMALL:
                return 0;
            case MEDIUM:
                return 1;
            case LARGE:
                return 2;
            default:
                return Integer.MAX_VALUE;
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isKeyUpEvent()) {
                if (event.getEventValue() == Keyboard.KEY_LSHIFT
                        || event.getEventValue() == Keyboard.KEY_RSHIFT) {
                    isPressingShift = false;
                }
                if (event.getEventValue() == Keyboard.KEY_LCONTROL
                        || event.getEventValue() == Keyboard.KEY_RCONTROL) {
                    isPressingCtrl = false;
                }
            }

            if (event.isKeyDownEvent()) {
                if (event.getEventValue() == Keyboard.KEY_LSHIFT
                        || event.getEventValue() == Keyboard.KEY_RSHIFT) {
                    isPressingShift = true;
                }
                if (event.getEventValue() == Keyboard.KEY_LCONTROL
                        || event.getEventValue() == Keyboard.KEY_RCONTROL) {
                    isPressingCtrl = true;
                }
            }
        }
    }

    @Override
    public void buttonPressed(Object buttonId) {}

    public class ColumnDefinition {
        public final String id;
        public final String label;
        public final float ratio;
        public final Comparator<ProductionCustomButton> comparator;

        public ColumnDefinition(
                String id,
                String label,
                float ratio,
                Comparator<ProductionCustomButton> comparator) {
            this.id = id;
            this.label = label;
            this.ratio = ratio;
            this.comparator = comparator;
        }
    }

    public class ColumnLayout {
        protected final List<ColumnDefinition> columns;
        protected final float totalPanelWidth;
        protected final int gap;
        protected final LinkedHashMap<String, Integer> widths = new LinkedHashMap<>();

        public ColumnLayout(List<ColumnDefinition> columns, float totalPanelWidth, int gap) {
            this.columns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);
            this.totalPanelWidth = totalPanelWidth;
            this.gap = gap;

            buildWidths();
        }

        public int getWidth(String id) {
            if (id == null) return 0;
            return widths.getOrDefault(id, 0);
        }

        public int getStartX(String id) {
            if (id == null) return 0;

            int x = 0;

            for (ColumnDefinition column : columns) {
                if (column == null || column.id == null) continue;

                if (id.equals(column.id)) {
                    return x;
                }

                x += getWidth(column.id) + gap;
            }

            return 0;
        }

        public int getEndX(String id) {
            return getStartX(id) + getWidth(id);
        }

        public boolean hasColumn(String id) {
            return id != null && widths.containsKey(id);
        }

        public int getGap() {
            return gap;
        }

        public float getTotalPanelWidth() {
            return totalPanelWidth;
        }

        public List<ColumnDefinition> getColumnsCopy() {
            return new ArrayList<>(columns);
        }

        public LinkedHashMap<String, Integer> getWidthsCopy() {
            return new LinkedHashMap<>(widths);
        }

        protected void buildWidths() {
            widths.clear();

            if (columns.isEmpty()) {
                return;
            }

            int gaps = Math.max(0, columns.size() - 1);
            int effectiveWidth = Math.max(0, Math.round(totalPanelWidth - gaps * gap));

            float totalRatio = 0f;
            for (ColumnDefinition column : columns) {
                if (column == null) continue;
                totalRatio += Math.max(0f, column.ratio);
            }

            boolean useEqualRatios = totalRatio <= 0f;
            float equalRatio = 1f / Math.max(1, columns.size());

            LinkedHashMap<String, Float> fractionalParts = new LinkedHashMap<>();

            int used = 0;

            for (ColumnDefinition column : columns) {
                if (column == null || column.id == null) continue;

                float normalizedRatio =
                        useEqualRatios ? equalRatio : Math.max(0f, column.ratio) / totalRatio;

                float exact = normalizedRatio * effectiveWidth;
                int base = (int) Math.floor(exact);

                widths.put(column.id, base);
                fractionalParts.put(column.id, exact - base);

                used += base;
            }

            int remainder = effectiveWidth - used;

            while (remainder > 0 && !fractionalParts.isEmpty()) {
                String bestKey = null;
                float bestFraction = -1f;

                for (Map.Entry<String, Float> entry : fractionalParts.entrySet()) {
                    if (entry.getValue() > bestFraction) {
                        bestFraction = entry.getValue();
                        bestKey = entry.getKey();
                    }
                }

                if (bestKey == null) break;

                widths.put(bestKey, widths.get(bestKey) + 1);
                fractionalParts.put(bestKey, 0f);
                remainder--;
            }
        }
    }
}
