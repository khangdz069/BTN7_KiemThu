package org.example.editvideoytbtool.ui;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import org.example.editvideoytbtool.model.VisualLayer;
import org.example.editvideoytbtool.model.VisualLayerType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Editable 1920x1080 preview scaled into the available JavaFX area. */
public final class PreviewPane extends StackPane {
    public static final double LOGICAL_WIDTH = 1920;
    public static final double LOGICAL_HEIGHT = 1080;

    private final Pane surface = new Pane();
    private final Group scaledGroup = new Group(surface);
    private Function<String, Path> assetResolver = path -> Path.of(path);
    private Consumer<VisualLayer> onSelected = ignored -> { };
    private Runnable onChanged = () -> { };
    private VisualLayer selected;
    private Rectangle selection;
    private Rectangle resizeHandle;
    private double pressSceneX;
    private double pressSceneY;
    private double originalX;
    private double originalY;
    private double originalWidth;
    private double originalHeight;
    private double previewScale = 1;

    public PreviewPane() {
        getStyleClass().add("preview-shell");
        setMinSize(320, 180);
        setPrefSize(960, 540);
        surface.setPrefSize(LOGICAL_WIDTH, LOGICAL_HEIGHT);
        surface.setMinSize(LOGICAL_WIDTH, LOGICAL_HEIGHT);
        surface.setMaxSize(LOGICAL_WIDTH, LOGICAL_HEIGHT);
        surface.setStyle("-fx-background-color: white;");
        getChildren().add(scaledGroup);
        setAlignment(scaledGroup, Pos.CENTER);
    }

    @Override protected void layoutChildren() {
        double scale = Math.max(0.01, Math.min(getWidth() / LOGICAL_WIDTH, getHeight() / LOGICAL_HEIGHT));
        previewScale = scale;
        scaledGroup.getTransforms().setAll(new Scale(scale, scale, 0, 0));
        scaledGroup.relocate((getWidth() - LOGICAL_WIDTH * scale) / 2.0,
                (getHeight() - LOGICAL_HEIGHT * scale) / 2.0);
    }

    public void setAssetResolver(Function<String, Path> assetResolver) {
        this.assetResolver = Objects.requireNonNull(assetResolver);
    }

    public void setOnSelected(Consumer<VisualLayer> onSelected) {
        this.onSelected = onSelected == null ? ignored -> { } : onSelected;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged == null ? () -> { } : onChanged;
    }

    public VisualLayer getSelectedLayer() { return selected; }

    public void showLayers(List<VisualLayer> layers, double sceneDuration, double playheadSeconds) {
        String selectedId = selected == null ? null : selected.getId();
        surface.getChildren().clear();
        selection = null;
        resizeHandle = null;
        selected = null;
        if (layers == null) return;
        layers.stream().sorted(Comparator.comparingInt(VisualLayer::getZIndex)).forEach(layer -> {
            if (!layer.isVisibleAt(playheadSeconds, sceneDuration)) return;
            Node node = makeNode(layer);
            configureNode(node, layer);
            surface.getChildren().add(node);
            if (layer.getId().equals(selectedId)) select(node, layer);
        });
    }

    public void selectLayer(VisualLayer layer) {
        if (layer == null) {
            clearSelection();
            return;
        }
        for (Node node : surface.getChildren()) {
            if (node.getUserData() == layer) {
                select(node, layer);
                return;
            }
        }
    }

    private Node makeNode(VisualLayer layer) {
        return switch (layer.getType()) {
            case CHARACTER, IMAGE -> imageNode(layer);
            case TEXT -> textNode(layer, false);
            case PLACEHOLDER -> textNode(layer, true);
        };
    }

    private Node imageNode(VisualLayer layer) {
        try {
            Path path = layer.getAssetPath() == null ? null : assetResolver.apply(layer.getAssetPath());
            if (path != null && Files.isRegularFile(path)) {
                Image image = new Image(path.toUri().toString(), false);
                ImageView view = new ImageView(image);
                view.setPreserveRatio(false);
                view.setSmooth(true);
                view.setFitWidth(layer.getWidth());
                view.setFitHeight(layer.getHeight());
                return view;
            }
        } catch (Exception ignored) {
            // A visible placeholder below is more useful than failing the preview.
        }
        return missingAssetNode(layer.getName().isBlank() ? "Không tìm thấy ảnh" : layer.getName());
    }

    private Node textNode(VisualLayer layer, boolean placeholder) {
        Label label = new Label(placeholder
                ? (layer.getDescription().isBlank() ? "Ảnh minh họa chưa được chọn" : layer.getDescription())
                : layer.getText());
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        label.setPrefSize(layer.getWidth(), layer.getHeight());
        label.setMinSize(layer.getWidth(), layer.getHeight());
        label.setMaxSize(layer.getWidth(), layer.getHeight());
        if (placeholder) {
            label.getStyleClass().add("illustration-placeholder");
            label.setFont(Font.font(Math.max(22, Math.min(42, layer.getFontSize()))));
        } else {
            label.setFont(Font.font(layer.getFontFamily(), Math.max(1, layer.getFontSize())));
            try { label.setTextFill(Color.web(layer.getTextColor())); }
            catch (IllegalArgumentException ignored) { label.setTextFill(Color.web("#111111")); }
        }
        return label;
    }

    private Node missingAssetNode(String text) {
        Label label = new Label(text + "\n(Chọn lại file ảnh)");
        label.setAlignment(Pos.CENTER);
        label.setWrapText(true);
        label.setPrefSize(640, 360);
        label.getStyleClass().add("missing-asset");
        return label;
    }

    private void configureNode(Node node, VisualLayer layer) {
        node.setUserData(layer);
        node.setLayoutX(layer.getX());
        node.setLayoutY(layer.getY());
        node.setRotate(layer.getRotationDegrees());
        node.setOpacity(layer.getOpacity());
        node.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                select(node, layer);
                pressSceneX = event.getSceneX();
                pressSceneY = event.getSceneY();
                originalX = layer.getX();
                originalY = layer.getY();
                event.consume();
            }
        });
        node.setOnMouseDragged(event -> {
            if (!layer.isLocked() && event.isPrimaryButtonDown()) {
                double scale = previewScale;
                layer.setX(clamp(originalX + (event.getSceneX() - pressSceneX) / scale,
                        -layer.getWidth() + 20, LOGICAL_WIDTH - 20));
                layer.setY(clamp(originalY + (event.getSceneY() - pressSceneY) / scale,
                        -layer.getHeight() + 20, LOGICAL_HEIGHT - 20));
                node.setLayoutX(layer.getX());
                node.setLayoutY(layer.getY());
                updateSelection(layer);
                onChanged.run();
                event.consume();
            }
        });
    }

    private void select(Node node, VisualLayer layer) {
        clearSelection();
        selected = layer;
        selection = new Rectangle(layer.getWidth(), layer.getHeight(), Color.TRANSPARENT);
        selection.setStroke(Color.web("#2563EB"));
        selection.setStrokeWidth(4);
        selection.setMouseTransparent(true);
        selection.setLayoutX(layer.getX());
        selection.setLayoutY(layer.getY());
        selection.setRotate(layer.getRotationDegrees());
        resizeHandle = new Rectangle(24, 24, Color.web("#2563EB"));
        resizeHandle.setLayoutX(layer.getX() + layer.getWidth() - 12);
        resizeHandle.setLayoutY(layer.getY() + layer.getHeight() - 12);
        resizeHandle.setOnMousePressed(event -> {
            pressSceneX = event.getSceneX();
            pressSceneY = event.getSceneY();
            originalWidth = layer.getWidth();
            originalHeight = layer.getHeight();
            event.consume();
        });
        resizeHandle.setOnMouseDragged(event -> {
            if (!layer.isLocked()) {
                double scale = previewScale;
                double width = Math.max(24, originalWidth + (event.getSceneX() - pressSceneX) / scale);
                double height = Math.max(24, originalHeight + (event.getSceneY() - pressSceneY) / scale);
                layer.setWidth(width);
                layer.setHeight(height);
                resizeVisualNode(node, layer);
                updateSelection(layer);
                onChanged.run();
                event.consume();
            }
        });
        surface.getChildren().addAll(selection, resizeHandle);
        onSelected.accept(layer);
    }

    private void resizeVisualNode(Node node, VisualLayer layer) {
        if (node instanceof ImageView view) {
            view.setFitWidth(layer.getWidth());
            view.setFitHeight(layer.getHeight());
        } else if (node instanceof Label label) {
            label.setPrefSize(layer.getWidth(), layer.getHeight());
            label.setMinSize(layer.getWidth(), layer.getHeight());
            label.setMaxSize(layer.getWidth(), layer.getHeight());
        }
    }

    private void updateSelection(VisualLayer layer) {
        if (selection == null || resizeHandle == null) return;
        selection.setLayoutX(layer.getX());
        selection.setLayoutY(layer.getY());
        selection.setWidth(layer.getWidth());
        selection.setHeight(layer.getHeight());
        resizeHandle.setLayoutX(layer.getX() + layer.getWidth() - 12);
        resizeHandle.setLayoutY(layer.getY() + layer.getHeight() - 12);
    }

    private void clearSelection() {
        if (selection != null) surface.getChildren().remove(selection);
        if (resizeHandle != null) surface.getChildren().remove(resizeHandle);
        selection = null;
        resizeHandle = null;
        selected = null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
