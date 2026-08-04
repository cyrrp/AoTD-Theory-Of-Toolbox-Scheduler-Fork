package data.kaysaar.aotd.tot.codex;

import static com.fs.starfarer.api.impl.codex.CodexDataV2.*;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.codex.CodexDialogAPI;
import com.fs.starfarer.api.impl.codex.CodexEntryPlugin;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;
import java.util.List;
import org.lwjgl.opengl.GL11;

public class AoTDToTIndustryEntryCodex extends CodexEntryV2 implements CustomUIPanelPlugin {

    public AoTDToTIndustryEntryCodex(String id, String title, String icon, Object param) {
        super(id, title, icon, param);
    }

    @Override
    public void destroyCustomDetail() {
        panel = null;
        relatedEntries = null;
        box = null;
        codex = null;
    }

    @Override
    public void createCustomDetail(
            CustomPanelAPI panel, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
        this.panel = panel;
        this.relatedEntries = relatedEntries;
        this.codex = codex;

        Color color = Misc.getBasePlayerColor();
        Color dark = Misc.getDarkPlayerColor();
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float opad = 10f;
        float pad = 3f;
        float small = 5f;

        float width = panel.getPosition().getWidth();

        float initPad = 0f;

        float horzBoxPad = 30f;

        // the right width for a tooltip wrapped in a box to fit next to relatedEntries
        // 290 is the width of the related entries widget, but it may be null
        float tw = width - opad - horzBoxPad + 10f;

        TooltipMakerAPI text = panel.createUIElement(tw, 0, false);
        text.setParaSmallInsignia();

        String design = "Cicero";
        if (design != null && !design.toLowerCase().equals("common")) {
            text.setParaFontDefault();
            Misc.addDesignTypePara(text, design, initPad);
            text.setParaSmallInsignia();
            initPad = opad;
        }

        text.addPara(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt "
                        + "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation "
                        + "ullamco laboris nisi ut aliquip ex ea commodo consequat.",
                initPad);

        // add a bunch of paragraphs so that it requires a scroller
        for (int i = 0; i < 13; i++) {
            text.addPara(
                    "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat "
                            + "nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia "
                            + "deserunt mollit anim id est laborum.",
                    opad);
        }

        panel.updateUIElementSizeAndMakeItProcessInput(text);

        box = panel.wrapTooltipWithBox(text);
        panel.addComponent(box).inTL(0f, 0f);
        if (relatedEntries != null) {
            panel.addComponent(relatedEntries).inTR(0f, 0f);
        }

        float height = box.getPosition().getHeight();
        if (relatedEntries != null) {
            height = Math.max(height, relatedEntries.getPosition().getHeight());
        }
        panel.getPosition().setSize(width, height);
    }

    protected CustomPanelAPI panel;
    protected UIPanelAPI relatedEntries;
    protected UIPanelAPI box;
    protected CodexDialogAPI codex;

    @Override
    public boolean hasCustomDetailPanel() {
        return true;
    }

    @Override
    public boolean hasTagDisplay() {
        return true;
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return this;
    }

    @Override
    public void configureTagDisplay(TagDisplayAPI tags) {
        int industry = 0;
        int structure = 0;
        int station = 0;
        int other = 0;
        int total = 0;
        for (CodexEntryPlugin curr : getChildren()) {
            if (!curr.isVisible() || curr.isLocked() || curr.skipForTags()) continue;
            if (!(curr.getParam() instanceof IndustrySpecAPI)) continue;

            IndustrySpecAPI spec = (IndustrySpecAPI) curr.getParam();
            if (spec.hasTag(Industries.TAG_INDUSTRY)) industry++;
            else if (spec.hasTag(Industries.TAG_STATION)) station++;
            else if (spec.hasTag(Industries.TAG_STRUCTURE)) structure++;
            else other++;

            total++;
        }
        tags.beginGroup(false, ALL_TYPES);
        tags.addTag(INDUSTRIES, industry);
        tags.addTag(STRUCTURES, structure);
        if (station > 0) tags.addTag(STATIONS, station);
        if (other > 0) tags.addTag(OTHER, other);
        tags.setTotalOverrideForCurrentGroup(total);
        tags.addGroup(0f);

        tags.checkAll();
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {
        // just rendering something to show how one might do it
        if (relatedEntries != null) {
            PositionAPI p = relatedEntries.getPosition();
            float x = p.getX();
            float y = p.getY();
            float w = p.getWidth();
            float h = p.getHeight();
            Color color = Misc.getDarkPlayerColor();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Misc.renderQuad(x, y - 110f, w, 100f, color, alphaMult);
        }
    }

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
