package data.kaysaar.aotd.tot.ui.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * Supply/Demand area graph.
 *
 * <p>Visual meaning: - ORANGE = covered demand / existing production: baseline -> min(supply,
 * demand) - GREEN = surplus: demand -> supply, when supply > demand - RED = shortage: supply ->
 * demand, when demand > supply
 *
 * <p>AA: - Outer visible top edge is anti-aliased. - Internal covered boundary is also
 * anti-aliased: green lower edge -> feather down into orange red lower edge -> feather down into
 * orange
 *
 * <p>This keeps orange visible when production exists, while avoiding orange fringe over red/green.
 */
public class SupplyDemandAreaGraph implements ExtendedUIPanelPlugin {

    private static final float EPS = 0.0001f;

    private final CustomPanelAPI mainPanel;

    // Panel-local Y samples [0..height].
    private final ArrayList<Float> supplyY = new ArrayList<>();
    private final ArrayList<Float> demandY = new ArrayList<>();

    private Color greenFill = new Color(41, 126, 65, 255);
    private Color orangeFill = new Color(178, 130, 34, 255);
    private Color redFill = new Color(161, 18, 18, 255);

    private float alphaMult = 1f;

    private boolean aaEnabled = true;
    private float aaFeatherPx = 1.25f;

    /** Kept for compatibility with existing callers. */
    private float crossingOverlapPx = 0f;

    /** Kept for compatibility with existing callers. */
    private float aaCrossCutPx = 0f;

    public SupplyDemandAreaGraph(
            float width, float height, List<Float> supplySamplesY, List<Float> demandSamplesY) {
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        setData(supplySamplesY, demandSamplesY);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {}

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}

    public void setAlphaMult(float alphaMult) {
        this.alphaMult = alphaMult;
    }

    /** Green / Orange / Red. */
    public void setColors(Color green, Color orange, Color red) {
        if (green != null) this.greenFill = green;
        if (orange != null) this.orangeFill = orange;
        if (red != null) this.redFill = red;
    }

    public void setCrossingOverlapPx(float px) {
        this.crossingOverlapPx = Math.max(0f, px);
    }

    public void setAAEnabled(boolean enabled) {
        this.aaEnabled = enabled;
    }

    public void setAAFeatherPx(float px) {
        this.aaFeatherPx = Math.max(0f, px);
    }

    public void setAACrossCutPx(float px) {
        this.aaCrossCutPx = Math.max(0f, px);
    }

    public void setData(List<Float> supplySamplesY, List<Float> demandSamplesY) {
        supplyY.clear();
        demandY.clear();

        if (supplySamplesY != null) supplyY.addAll(supplySamplesY);
        if (demandSamplesY != null) demandY.addAll(demandSamplesY);

        int n = Math.min(supplyY.size(), demandY.size());

        while (supplyY.size() > n) {
            supplyY.remove(supplyY.size() - 1);
        }

        while (demandY.size() > n) {
            demandY.remove(demandY.size() - 1);
        }
    }

    @Override
    public void render(float uiAlphaMult) {
        int n = Math.min(supplyY.size(), demandY.size());
        if (n < 2) return;

        PositionAPI pos = mainPanel.getPosition();

        float left = pos.getX();
        float bottom = pos.getY();
        float width = pos.getWidth();
        float height = pos.getHeight();
        float top = bottom + height;

        float step = width / (n - 1f);
        float actualAlpha = uiAlphaMult * this.alphaMult;

        ArrayList<P> points = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            float x = left + step * i;

            float s = bottom + clamp(supplyY.get(i), 0f, height);
            float d = bottom + clamp(demandY.get(i), 0f, height);

            points.add(new P(x, s, d));
        }

        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);

        // Normal alpha blending. This is safer than ONE/ZERO once AA is involved.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawOrangeCoveredArea(bottom, top, points, orangeFill, actualAlpha);
        drawGreenSurplusArea(bottom, top, points, greenFill, actualAlpha);
        drawRedShortageArea(bottom, top, points, redFill, actualAlpha);

        if (aaEnabled && aaFeatherPx > 0f) {
            // AA for the orange boundary against green/red.
            // This is what was missing.
            drawCoveredBoundaryAA(bottom, top, points, actualAlpha, aaFeatherPx);

            // AA for the outer graph silhouette.
            drawVisibleTopEdgeAA(bottom, top, points, actualAlpha, aaFeatherPx);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static class P {
        final float x;
        final float s;
        final float d;

        P(float x, float s, float d) {
            this.x = x;
            this.s = s;
            this.d = d;
        }
    }

    private void drawOrangeCoveredArea(
            float baseline, float top, List<P> points, Color color, float alphaMult) {
        setColor(color, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < points.size() - 1; i++) {
            P a = points.get(i);
            P b = points.get(i + 1);

            float diffA = a.s - a.d;
            float diffB = b.s - b.d;

            if (crossesZero(diffA, diffB)) {
                P mid = createCrossingPoint(a, b, diffA, diffB);

                emitOrangePiece(baseline, top, a, mid);
                emitOrangePiece(baseline, top, mid, b);
            } else {
                emitOrangePiece(baseline, top, a, b);
            }
        }

        GL11.glEnd();
    }

    private void drawGreenSurplusArea(
            float baseline, float top, List<P> points, Color color, float alphaMult) {
        setColor(color, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < points.size() - 1; i++) {
            P a = points.get(i);
            P b = points.get(i + 1);

            float diffA = a.s - a.d;
            float diffB = b.s - b.d;

            if (crossesZero(diffA, diffB)) {
                P mid = createCrossingPoint(a, b, diffA, diffB);

                emitGreenPiece(baseline, top, a, mid);
                emitGreenPiece(baseline, top, mid, b);
            } else {
                emitGreenPiece(baseline, top, a, b);
            }
        }

        GL11.glEnd();
    }

    private void drawRedShortageArea(
            float baseline, float top, List<P> points, Color color, float alphaMult) {
        setColor(color, alphaMult);
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < points.size() - 1; i++) {
            P a = points.get(i);
            P b = points.get(i + 1);

            float diffA = a.s - a.d;
            float diffB = b.s - b.d;

            if (crossesZero(diffA, diffB)) {
                P mid = createCrossingPoint(a, b, diffA, diffB);

                emitRedPiece(baseline, top, a, mid);
                emitRedPiece(baseline, top, mid, b);
            } else {
                emitRedPiece(baseline, top, a, b);
            }
        }

        GL11.glEnd();
    }

    private static void emitOrangePiece(float baseline, float top, P a, P b) {
        float yA = clamp(Math.min(a.s, a.d), baseline, top);
        float yB = clamp(Math.min(b.s, b.d), baseline, top);

        if (yA <= baseline && yB <= baseline) return;

        emitBand(a.x, baseline, yA, b.x, baseline, yB);
    }

    private static void emitGreenPiece(float baseline, float top, P a, P b) {
        float diffA = a.s - a.d;
        float diffB = b.s - b.d;

        if (diffA <= EPS && diffB <= EPS) return;

        float lowerA = clamp(a.d, baseline, top);
        float upperA = clamp(a.s, baseline, top);

        float lowerB = clamp(b.d, baseline, top);
        float upperB = clamp(b.s, baseline, top);

        if (upperA < lowerA) upperA = lowerA;
        if (upperB < lowerB) upperB = lowerB;

        if (upperA <= lowerA && upperB <= lowerB) return;

        emitBand(a.x, lowerA, upperA, b.x, lowerB, upperB);
    }

    private static void emitRedPiece(float baseline, float top, P a, P b) {
        float diffA = a.s - a.d;
        float diffB = b.s - b.d;

        if (diffA >= -EPS && diffB >= -EPS) return;

        float lowerA = clamp(a.s, baseline, top);
        float upperA = clamp(a.d, baseline, top);

        float lowerB = clamp(b.s, baseline, top);
        float upperB = clamp(b.d, baseline, top);

        if (upperA < lowerA) upperA = lowerA;
        if (upperB < lowerB) upperB = lowerB;

        if (upperA <= lowerA && upperB <= lowerB) return;

        emitBand(a.x, lowerA, upperA, b.x, lowerB, upperB);
    }

    private static void emitBand(
            float xA, float lowerA, float upperA, float xB, float lowerB, float upperB) {
        GL11.glVertex2f(xA, lowerA);
        GL11.glVertex2f(xA, upperA);
        GL11.glVertex2f(xB, lowerB);

        GL11.glVertex2f(xB, lowerB);
        GL11.glVertex2f(xA, upperA);
        GL11.glVertex2f(xB, upperB);
    }

    /**
     * AA for the orange covered boundary.
     *
     * <p>In surplus: orange ends at demand, green starts at demand. Use green AA feathering
     * downward into orange.
     *
     * <p>In shortage: orange ends at supply, red starts at supply. Use red AA feathering downward
     * into orange.
     *
     * <p>This makes the visible orange boundary smooth without drawing orange AA on top of
     * red/green.
     */
    private void drawCoveredBoundaryAA(
            float baseline, float top, List<P> points, float alphaMult, float featherPx) {
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < points.size() - 1; i++) {
            P a = points.get(i);
            P b = points.get(i + 1);

            float diffA = a.s - a.d;
            float diffB = b.s - b.d;

            if (crossesZero(diffA, diffB)) {
                P mid = createCrossingPoint(a, b, diffA, diffB);

                emitCoveredBoundaryAAPiece(baseline, top, a, mid, alphaMult, featherPx);
                emitCoveredBoundaryAAPiece(baseline, top, mid, b, alphaMult, featherPx);
            } else {
                emitCoveredBoundaryAAPiece(baseline, top, a, b, alphaMult, featherPx);
            }
        }

        GL11.glEnd();
    }

    private void emitCoveredBoundaryAAPiece(
            float baseline, float top, P a, P b, float alphaMult, float featherPx) {
        float diffA = a.s - a.d;
        float diffB = b.s - b.d;

        boolean surplus = diffA > EPS || diffB > EPS;
        boolean shortage = diffA < -EPS || diffB < -EPS;

        if (!surplus && !shortage) {
            return;
        }

        Color color;

        float yA;
        float yB;

        if (surplus) {
            // Green begins at demand. Feather green down into orange.
            color = greenFill;
            yA = clamp(a.d, baseline, top);
            yB = clamp(b.d, baseline, top);
        } else {
            // Red begins at supply. Feather red down into orange.
            color = redFill;
            yA = clamp(a.s, baseline, top);
            yB = clamp(b.s, baseline, top);
        }

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float bCol = color.getBlue() / 255f;
        float aCol = (color.getAlpha() / 255f) * alphaMult;

        aaStripVertical(a.x, yA, b.x, yB, -featherPx, r, g, bCol, aCol);
    }

    /** AA for the outer visible top edge of the graph. */
    private void drawVisibleTopEdgeAA(
            float baseline, float top, List<P> points, float alphaMult, float featherPx) {
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < points.size() - 1; i++) {
            P a = points.get(i);
            P b = points.get(i + 1);

            float diffA = a.s - a.d;
            float diffB = b.s - b.d;

            if (crossesZero(diffA, diffB)) {
                P mid = createCrossingPoint(a, b, diffA, diffB);

                emitVisibleTopAAPiece(baseline, top, a, mid, alphaMult, featherPx);
                emitVisibleTopAAPiece(baseline, top, mid, b, alphaMult, featherPx);
            } else {
                emitVisibleTopAAPiece(baseline, top, a, b, alphaMult, featherPx);
            }
        }

        GL11.glEnd();
    }

    private void emitVisibleTopAAPiece(
            float baseline, float top, P a, P b, float alphaMult, float featherPx) {
        float diffA = a.s - a.d;
        float diffB = b.s - b.d;

        Color color;

        if (diffA > EPS || diffB > EPS) {
            color = greenFill;
        } else if (diffA < -EPS || diffB < -EPS) {
            color = redFill;
        } else {
            color = orangeFill;
        }

        float yA = clamp(Math.max(a.s, a.d), baseline, top);
        float yB = clamp(Math.max(b.s, b.d), baseline, top);

        if (yA <= baseline && yB <= baseline) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float bCol = color.getBlue() / 255f;
        float aCol = (color.getAlpha() / 255f) * alphaMult;

        aaStripVertical(a.x, yA, b.x, yB, featherPx, r, g, bCol, aCol);
    }

    private static void aaStripVertical(
            float x0,
            float y0,
            float x1,
            float y1,
            float dy,
            float r,
            float g,
            float b,
            float aInner) {
        float x0Outer = x0;
        float y0Outer = y0 + dy;

        float x1Outer = x1;
        float y1Outer = y1 + dy;

        GL11.glColor4f(r, g, b, aInner);
        GL11.glVertex2f(x0, y0);

        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(x0Outer, y0Outer);

        GL11.glColor4f(r, g, b, aInner);
        GL11.glVertex2f(x1, y1);

        GL11.glColor4f(r, g, b, aInner);
        GL11.glVertex2f(x1, y1);

        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(x0Outer, y0Outer);

        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(x1Outer, y1Outer);
    }

    private static P createCrossingPoint(P a, P b, float diffA, float diffB) {
        float t = solveT(diffA, diffB, 0f);

        return new P(lerp(a.x, b.x, t), lerp(a.s, b.s, t), lerp(a.d, b.d, t));
    }

    private static boolean crossesZero(float a, float b) {
        return (a > EPS && b < -EPS) || (a < -EPS && b > EPS);
    }

    private static void setColor(Color c, float alphaMult) {
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        float a = (c.getAlpha() / 255f) * alphaMult;

        GL11.glColor4f(r, g, b, a);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float solveT(float v0, float v1, float target) {
        float denom = v1 - v0;
        if (Math.abs(denom) < 1e-6f) return 0.5f;

        float t = (target - v0) / denom;
        return clamp(t, 0f, 1f);
    }

    /**
     * Converts integer values to panel-local Y samples [0..height]. highest should be max across
     * both supply and demand.
     */
    public static ArrayList<Float> createSeriesForGraph(
            float height, List<Integer> values, float highest) {
        ArrayList<Float> out = new ArrayList<>();
        if (values == null || values.isEmpty()) return out;

        float denom = Math.max(1f, highest);

        for (Integer v : values) {
            float val = v == null ? 0f : v;
            float y = (val / denom) * height;

            out.add(clamp(y, 0f, height));
        }

        return out;
    }
}
