package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.ui.models.resizable.ButtonComponent;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;

public class ButtonWithImageComponent extends ButtonComponent {
    ImageViewer viewer;

    public ButtonWithImageComponent(float width, float height, String imageName) {
        super(width, height);
        viewer = new ImageViewer(width, height, imageName);
        addComponent(viewer, 0, 0);
    }

    public void setViewerSpriteId(String id) {
        viewer.spriteOfImage = Global.getSettings().getSprite(id);
    }
}
