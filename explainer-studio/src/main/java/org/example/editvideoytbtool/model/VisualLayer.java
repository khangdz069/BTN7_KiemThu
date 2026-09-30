package org.example.editvideoytbtool.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.UUID;

/**
 * One independently editable item in a scene. Coordinates and sizes are in
 * output-video pixels. Visibility offsets are seconds relative to the start
 * of the owning scene; a null end offset means "until the scene ends".
 */
public class VisualLayer {
    private String id = UUID.randomUUID().toString();
    private VisualLayerType type = VisualLayerType.PLACEHOLDER;
    private String name = "";
    private String assetPath;
    private String text = "";
    private String description = "";
    private double x;
    private double y;
    private double width = 640;
    private double height = 360;
    private double rotationDegrees;
    private double opacity = 1.0;
    private int zIndex;
    private double startOffsetSeconds;
    private Double endOffsetSeconds;
    private String fontFamily = "Arial";
    private double fontSize = 48;
    private String textColor = "#111111";
    private boolean locked;

    public VisualLayer() {
    }

    public VisualLayer(VisualLayerType type) {
        setType(type);
    }

    public static VisualLayer character(String assetPath) {
        VisualLayer layer = new VisualLayer(VisualLayerType.CHARACTER);
        layer.setName("Nhân vật");
        layer.setAssetPath(assetPath);
        return layer;
    }

    public static VisualLayer image(String assetPath) {
        VisualLayer layer = new VisualLayer(VisualLayerType.IMAGE);
        layer.setName("Ảnh minh họa");
        layer.setAssetPath(assetPath);
        return layer;
    }

    public static VisualLayer text(String text) {
        VisualLayer layer = new VisualLayer(VisualLayerType.TEXT);
        layer.setName("Chữ");
        layer.setText(text);
        layer.setWidth(800);
        layer.setHeight(160);
        return layer;
    }

    public static VisualLayer placeholder(String description) {
        VisualLayer layer = new VisualLayer(VisualLayerType.PLACEHOLDER);
        layer.setName("Ảnh cần thêm");
        layer.setDescription(description);
        return layer;
    }

    /** Creates an editable duplicate with a new stable identity. */
    public VisualLayer duplicate() {
        VisualLayer copy = new VisualLayer(type);
        copy.name = name;
        copy.assetPath = assetPath;
        copy.text = text;
        copy.description = description;
        copy.x = x;
        copy.y = y;
        copy.width = width;
        copy.height = height;
        copy.rotationDegrees = rotationDegrees;
        copy.opacity = opacity;
        copy.zIndex = zIndex;
        copy.startOffsetSeconds = startOffsetSeconds;
        copy.endOffsetSeconds = endOffsetSeconds;
        copy.fontFamily = fontFamily;
        copy.fontSize = fontSize;
        copy.textColor = textColor;
        copy.locked = locked;
        return copy;
    }

    @JsonIgnore
    public boolean isVisibleAt(double sceneRelativeSeconds, double sceneDurationSeconds) {
        double effectiveEnd = endOffsetSeconds == null ? sceneDurationSeconds : endOffsetSeconds;
        return sceneRelativeSeconds >= startOffsetSeconds && sceneRelativeSeconds < effectiveEnd;
    }

    public void setVisibilityWindow(double startSeconds, Double endSeconds) {
        requireNonNegativeFinite(startSeconds, "startOffsetSeconds");
        if (endSeconds != null) {
            requireNonNegativeFinite(endSeconds, "endOffsetSeconds");
            if (endSeconds < startSeconds) {
                throw new IllegalArgumentException("endOffsetSeconds must not be before startOffsetSeconds");
            }
        }
        this.startOffsetSeconds = startSeconds;
        this.endOffsetSeconds = endSeconds;
    }

    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Visual layer id is required");
        }
        Objects.requireNonNull(type, "Visual layer type is required");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireNonNegativeFinite(width, "width");
        requireNonNegativeFinite(height, "height");
        requireFinite(rotationDegrees, "rotationDegrees");
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalStateException("opacity must be between 0 and 1");
        }
        requireNonNegativeFinite(startOffsetSeconds, "startOffsetSeconds");
        if (endOffsetSeconds != null) {
            requireNonNegativeFinite(endOffsetSeconds, "endOffsetSeconds");
            if (endOffsetSeconds < startOffsetSeconds) {
                throw new IllegalStateException("Layer end time is before its start time");
            }
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative finite value");
        }
    }

    public String getId() { return id; }

    public void setId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        this.id = id;
    }

    public VisualLayerType getType() { return type; }
    public void setType(VisualLayerType type) { this.type = Objects.requireNonNull(type, "type"); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name; }
    public String getAssetPath() { return assetPath; }
    public void setAssetPath(String assetPath) { this.assetPath = assetPath == null || assetPath.isBlank() ? null : assetPath; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text == null ? "" : text; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }
    public double getX() { return x; }
    public void setX(double x) { requireFinite(x, "x"); this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { requireFinite(y, "y"); this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { requireNonNegativeFinite(width, "width"); this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { requireNonNegativeFinite(height, "height"); this.height = height; }
    public double getRotationDegrees() { return rotationDegrees; }
    public void setRotationDegrees(double rotationDegrees) { requireFinite(rotationDegrees, "rotationDegrees"); this.rotationDegrees = rotationDegrees; }
    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) {
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) throw new IllegalArgumentException("opacity must be between 0 and 1");
        this.opacity = opacity;
    }
    public int getZIndex() { return zIndex; }
    public void setZIndex(int zIndex) { this.zIndex = zIndex; }
    public double getStartOffsetSeconds() { return startOffsetSeconds; }
    public void setStartOffsetSeconds(double startOffsetSeconds) { requireNonNegativeFinite(startOffsetSeconds, "startOffsetSeconds"); this.startOffsetSeconds = startOffsetSeconds; }
    public Double getEndOffsetSeconds() { return endOffsetSeconds; }
    public void setEndOffsetSeconds(Double endOffsetSeconds) {
        if (endOffsetSeconds != null) requireNonNegativeFinite(endOffsetSeconds, "endOffsetSeconds");
        this.endOffsetSeconds = endOffsetSeconds;
    }
    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily == null || fontFamily.isBlank() ? "Arial" : fontFamily; }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { requireNonNegativeFinite(fontSize, "fontSize"); this.fontSize = fontSize; }
    public String getTextColor() { return textColor; }
    public void setTextColor(String textColor) { this.textColor = textColor == null || textColor.isBlank() ? "#111111" : textColor; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
}
