package data.kaysaar.aotd.tot.ui;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** A lightweight panel shell that creates its real contents only when first selected. */
public final class LazyUIPanel implements ExtendedUIPanelPlugin {
    private final CustomPanelAPI mainPanel;
    private final Supplier<? extends ExtendedUIPanelPlugin> factory;
    private ExtendedUIPanelPlugin delegate;

    public LazyUIPanel(
            float width, float height, Supplier<? extends ExtendedUIPanelPlugin> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
        mainPanel = Global.getSettings().createCustom(width, height, this);
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public boolean isInitialized() {
        return delegate != null;
    }

    public ExtendedUIPanelPlugin getDelegate() {
        return delegate;
    }

    @Override
    public void createUI() {
        if (delegate == null) {
            ExtendedUIPanelPlugin created =
                    Objects.requireNonNull(factory.get(), "factory returned null");
            created.createUI();
            mainPanel.addComponent(created.getMainPanel()).inTL(0, 0);
            delegate = created;
            return;
        }
        delegate.createUI();
    }

    @Override
    public void clearUI() {
        if (delegate != null) {
            delegate.clearUI();
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
}
