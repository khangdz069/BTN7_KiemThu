package org.example.editvideoytbtool.export;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable, model-independent description consumed by the FFmpeg exporter. */
record ExportPlan(
        int width,
        int height,
        int framesPerSecond,
        double durationSeconds,
        List<LayerItem> layers,
        List<AudioItem> audioItems
) {
    ExportPlan {
        if (width <= 0 || height <= 0 || framesPerSecond <= 0 || !finitePositive(durationSeconds)) {
            throw new IllegalArgumentException("Invalid video dimensions, frame rate, or duration.");
        }
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        audioItems = List.copyOf(Objects.requireNonNull(audioItems, "audioItems"));
    }

    enum LayerKind {
        CHARACTER,
        IMAGE,
        TEXT,
        PLACEHOLDER
    }

    record LayerItem(
            String stableName,
            LayerKind kind,
            Path asset,
            String text,
            String fontFamily,
            double fontSize,
            String textColor,
            double x,
            double y,
            double width,
            double height,
            double rotationDegrees,
            double opacity,
            int zIndex,
            int sceneIndex,
            double startSeconds,
            double endSeconds
    ) {
        LayerItem {
            stableName = stableName == null || stableName.isBlank() ? "layer" : stableName;
            kind = Objects.requireNonNull(kind, "kind");
            text = text == null ? "" : text;
            fontFamily = fontFamily == null || fontFamily.isBlank() ? "Arial" : fontFamily;
            textColor = textColor == null || textColor.isBlank() ? "#111111" : textColor;
            if (!finite(x) || !finite(y) || !finitePositive(width) || !finitePositive(height)
                    || !finite(rotationDegrees) || !finite(opacity) || !finite(fontSize) || fontSize < 0
                    || !finite(startSeconds) || !finite(endSeconds) || endSeconds <= startSeconds) {
                throw new IllegalArgumentException("Invalid geometry or timing for " + stableName + ".");
            }
            opacity = Math.max(0.0, Math.min(1.0, opacity));
        }
    }

    record AudioItem(String sceneId, Path file, double delaySeconds) {
        AudioItem {
            sceneId = sceneId == null || sceneId.isBlank() ? "scene" : sceneId;
            file = Objects.requireNonNull(file, "file");
            if (!finite(delaySeconds) || delaySeconds < 0) {
                throw new IllegalArgumentException("Invalid audio delay for " + sceneId + ".");
            }
        }
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static boolean finitePositive(double value) {
        return finite(value) && value > 0;
    }
}
