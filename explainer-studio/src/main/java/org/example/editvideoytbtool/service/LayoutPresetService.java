package org.example.editvideoytbtool.service;

import org.example.editvideoytbtool.model.LayoutPreset;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.example.editvideoytbtool.model.VisualLayerType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Applies one of the three requested starting layouts while keeping layers editable. */
public final class LayoutPresetService {
    public void apply(SceneData scene, LayoutPreset preset) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(preset, "preset");
        scene.setLayoutPreset(preset);
        List<VisualLayer> characters = byType(scene, VisualLayerType.CHARACTER);
        List<VisualLayer> pictures = scene.getLayers().stream()
                .filter(layer -> layer.getType() == VisualLayerType.IMAGE || layer.getType() == VisualLayerType.PLACEHOLDER)
                .toList();
        List<VisualLayer> texts = byType(scene, VisualLayerType.TEXT);

        switch (preset) {
            case CHARACTER_RIGHT_OVERLAP_IMAGE -> {
                pictures.forEach(layer -> place(layer, 90, 130, 1320, 820, 10));
                characters.forEach(layer -> place(layer, 1210, 120, 650, 920, 30));
                placeTextsAround(texts, 70, 70, 850);
            }
            case CHARACTER_RIGHT_IMAGE_LEFT -> {
                pictures.forEach(layer -> place(layer, 80, 180, 960, 720, 10));
                characters.forEach(layer -> place(layer, 1180, 170, 620, 850, 30));
                placeTextsAround(texts, 90, 70, 920);
            }
            case CHARACTER_CENTER_TEXT_AROUND -> {
                pictures.forEach(layer -> place(layer, 650, 210, 620, 620, 5));
                characters.forEach(layer -> place(layer, 620, 120, 680, 920, 20));
                for (int i = 0; i < texts.size(); i++) {
                    VisualLayer text = texts.get(i);
                    if (i % 2 == 0) place(text, 70, 130 + (i / 2) * 240, 500, 170, 40 + i);
                    else place(text, 1350, 130 + (i / 2) * 240, 500, 170, 40 + i);
                }
            }
            case CUSTOM -> { /* Keep the user's exact transforms. */ }
        }
        normalizeZ(scene);
    }

    public VisualLayer addDefaultIllustrationPlaceholder(SceneData scene, String description) {
        VisualLayer layer = VisualLayer.placeholder(description == null || description.isBlank()
                ? "Chọn ảnh minh họa từ máy" : description);
        layer.setWidth(960);
        layer.setHeight(720);
        scene.getLayers().add(layer);
        apply(scene, scene.getLayoutPreset());
        return layer;
    }

    public void normalizeZ(SceneData scene) {
        List<VisualLayer> sorted = scene.getLayers().stream()
                .sorted(Comparator.comparingInt(VisualLayer::getZIndex)).toList();
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).setZIndex(i * 10);
    }

    private static List<VisualLayer> byType(SceneData scene, VisualLayerType type) {
        return scene.getLayers().stream().filter(layer -> layer.getType() == type).toList();
    }

    private static void place(VisualLayer layer, double x, double y, double width, double height, int z) {
        layer.setX(x);
        layer.setY(y);
        layer.setWidth(width);
        layer.setHeight(height);
        layer.setZIndex(z);
    }

    private static void placeTextsAround(List<VisualLayer> texts, double x, double y, double width) {
        for (int i = 0; i < texts.size(); i++) {
            place(texts.get(i), x, y + i * 180, width, 150, 40 + i);
        }
    }
}
