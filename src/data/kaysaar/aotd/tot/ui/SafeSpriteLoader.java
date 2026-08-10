package data.kaysaar.aotd.tot.ui;

import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps one bad data-driven icon from aborting construction of an entire UI section. */
public final class SafeSpriteLoader {
    private static final Set<String> REPORTED_PATHS = ConcurrentHashMap.newKeySet();
    private static final Set<String> VALIDATED_PATHS = ConcurrentHashMap.newKeySet();
    private static final Set<String> INVALID_PATHS = ConcurrentHashMap.newKeySet();

    private SafeSpriteLoader() {}

    public static SpriteAPI getSpriteOrNull(String path, String context) {
        if (path == null || path.isBlank()) {
            report(path, context, null);
            return null;
        }
        if (INVALID_PATHS.contains(path)) {
            return null;
        }
        try {
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                report(path, context, null);
            } else {
                VALIDATED_PATHS.add(path);
            }
            return sprite;
        } catch (RuntimeException exception) {
            report(path, context, exception);
            return null;
        }
    }

    public static ImageViewer createImageViewerOrNull(
            float width, float height, String path, String context) {
        if (path == null || INVALID_PATHS.contains(path)) {
            return null;
        }
        if (!VALIDATED_PATHS.contains(path) && getSpriteOrNull(path, context) == null) return null;
        try {
            return new ImageViewer(width, height, path);
        } catch (RuntimeException exception) {
            report(path, context, exception);
            return null;
        }
    }

    private static void report(String path, String context, RuntimeException exception) {
        String printablePath = path == null ? "<null>" : path;
        if (path != null) {
            INVALID_PATHS.add(path);
        }
        if (!REPORTED_PATHS.add(printablePath)) {
            return;
        }
        String message =
                "Skipping invalid UI sprite " + printablePath + " while rendering " + context;
        if (exception == null) {
            Global.getLogger(SafeSpriteLoader.class).warn(message);
        } else {
            Global.getLogger(SafeSpriteLoader.class).warn(message, exception);
        }
    }
}
