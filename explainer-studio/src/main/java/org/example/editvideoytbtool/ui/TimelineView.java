package org.example.editvideoytbtool.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.example.editvideoytbtool.model.VisualLayer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Scene strip, selected-scene WAV waveform and draggable layer visibility bars. */
public final class TimelineView extends Region {
    public record SceneItem(String id, String title, double startSeconds, double durationSeconds,
                            Path wav, List<VisualLayer> layers) { }

    private enum DragKind { NONE, LAYER_START, LAYER_END, LAYER_MOVE, SCENE_START }

    private final Canvas canvas = new Canvas();
    private final Map<Path, float[]> waveformCache = new HashMap<>();
    private List<SceneItem> scenes = List.of();
    private String selectedSceneId;
    private Consumer<VisualLayer> onLayerChanged = ignored -> { };
    private BiConsumer<String, Double> onSceneStartChanged = (id, value) -> { };
    private DragKind dragKind = DragKind.NONE;
    private VisualLayer dragLayer;
    private SceneItem dragScene;
    private double dragOriginX;
    private double dragStart;
    private Double dragEnd;

    public TimelineView() {
        getChildren().add(canvas);
        setMinHeight(170);
        setPrefHeight(230);
        getStyleClass().add("timeline-view");
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::mousePressed);
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::mouseDragged);
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> dragKind = DragKind.NONE);
        widthProperty().addListener((obs, old, value) -> redraw());
        heightProperty().addListener((obs, old, value) -> redraw());
    }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    public void setData(List<SceneItem> scenes, String selectedSceneId) {
        this.scenes = scenes == null ? List.of() : List.copyOf(scenes);
        this.selectedSceneId = selectedSceneId;
        redraw();
    }

    public void setOnLayerChanged(Consumer<VisualLayer> callback) {
        onLayerChanged = callback == null ? ignored -> { } : callback;
    }

    public void setOnSceneStartChanged(BiConsumer<String, Double> callback) {
        onSceneStartChanged = callback == null ? (id, value) -> { } : callback;
    }

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 1 || height <= 1) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#111827"));
        g.fillRect(0, 0, width, height);
        g.setFont(Font.font("Segoe UI", 12));
        double total = Math.max(1, scenes.stream().mapToDouble(s -> s.startSeconds() + s.durationSeconds()).max().orElse(1));
        drawRuler(g, width, total);
        drawSceneStrip(g, width, total);
        SceneItem selected = selected();
        if (selected == null) {
            g.setFill(Color.web("#94A3B8"));
            g.fillText("Chọn một cảnh để xem waveform và thời điểm các lớp.", 12, 100);
            return;
        }
        double left = 110;
        double usable = Math.max(20, width - left - 12);
        double duration = Math.max(0.1, selected.durationSeconds());
        drawWaveform(g, selected.wav(), left, 75, usable, 54);
        g.setFill(Color.web("#CBD5E1"));
        g.fillText("WAV", 12, 107);
        List<VisualLayer> layers = new ArrayList<>(selected.layers() == null ? List.of() : selected.layers());
        layers.sort(Comparator.comparingInt(VisualLayer::getZIndex));
        double rowY = 137;
        for (VisualLayer layer : layers) {
            if (rowY + 24 > height) break;
            g.setFill(Color.web("#CBD5E1"));
            g.fillText(shortName(layer.getName(), 13), 12, rowY + 16);
            double start = Math.min(duration, layer.getStartOffsetSeconds());
            double end = Math.min(duration, layer.getEndOffsetSeconds() == null ? duration : layer.getEndOffsetSeconds());
            double x = left + usable * start / duration;
            double w = Math.max(4, usable * Math.max(0, end - start) / duration);
            g.setFill(layerColor(layer));
            g.fillRoundRect(x, rowY, w, 20, 6, 6);
            g.setStroke(Color.color(1, 1, 1, 0.35));
            g.strokeRoundRect(x, rowY, w, 20, 6, 6);
            rowY += 28;
        }
    }

    private void drawRuler(GraphicsContext g, double width, double total) {
        g.setFill(Color.web("#0F172A"));
        g.fillRect(0, 0, width, 24);
        int ticks = Math.max(2, Math.min(12, (int) (width / 120)));
        g.setStroke(Color.web("#475569"));
        g.setFill(Color.web("#94A3B8"));
        for (int i = 0; i <= ticks; i++) {
            double x = 8 + (width - 16) * i / ticks;
            g.strokeLine(x, 16, x, 24);
            g.fillText(String.format("%.1fs", total * i / ticks), x + 2, 13);
        }
    }

    private void drawSceneStrip(GraphicsContext g, double width, double total) {
        double usable = width - 16;
        for (int i = 0; i < scenes.size(); i++) {
            SceneItem scene = scenes.get(i);
            double x = 8 + usable * scene.startSeconds() / total;
            double w = Math.max(5, usable * scene.durationSeconds() / total);
            g.setFill(scene.id().equals(selectedSceneId) ? Color.web("#2563EB") : Color.web("#334155"));
            g.fillRoundRect(x, 31, w, 32, 5, 5);
            g.setFill(Color.WHITE);
            g.fillText((i + 1) + ". " + shortName(scene.title(), 18), x + 5, 51);
        }
    }

    private void drawWaveform(GraphicsContext g, Path wav, double x, double y, double w, double h) {
        g.setFill(Color.web("#0F172A"));
        g.fillRect(x, y, w, h);
        g.setStroke(Color.web("#22D3EE"));
        if (wav == null || !Files.isRegularFile(wav)) {
            g.setFill(Color.web("#64748B"));
            g.fillText("Chưa có WAV", x + 10, y + h / 2 + 4);
            return;
        }
        float[] peaks = waveformCache.computeIfAbsent(wav.toAbsolutePath().normalize(), TimelineView::readPeaks);
        if (peaks.length == 0) return;
        double mid = y + h / 2;
        for (int i = 0; i < peaks.length; i++) {
            double px = x + w * i / Math.max(1, peaks.length - 1);
            double amp = peaks[i] * (h / 2 - 2);
            g.strokeLine(px, mid - amp, px, mid + amp);
        }
    }

    private void mousePressed(MouseEvent event) {
        double total = Math.max(1, scenes.stream().mapToDouble(s -> s.startSeconds() + s.durationSeconds()).max().orElse(1));
        if (event.getY() >= 28 && event.getY() <= 66) {
            double time = clamp((event.getX() - 8) / Math.max(1, canvas.getWidth() - 16) * total, 0, total);
            dragScene = scenes.stream().min(Comparator.comparingDouble(s -> Math.abs(s.startSeconds() - time))).orElse(null);
            if (dragScene != null && Math.abs(dragScene.startSeconds() - time) < Math.max(0.15, total * 0.02)) {
                dragKind = DragKind.SCENE_START;
                return;
            }
        }
        SceneItem selected = selected();
        if (selected == null || event.getY() < 137) return;
        int row = (int) ((event.getY() - 137) / 28);
        List<VisualLayer> layers = new ArrayList<>(selected.layers() == null ? List.of() : selected.layers());
        layers.sort(Comparator.comparingInt(VisualLayer::getZIndex));
        if (row < 0 || row >= layers.size()) return;
        VisualLayer layer = layers.get(row);
        double left = 110;
        double usable = Math.max(20, canvas.getWidth() - left - 12);
        double duration = Math.max(0.1, selected.durationSeconds());
        double startX = left + usable * layer.getStartOffsetSeconds() / duration;
        double endSeconds = layer.getEndOffsetSeconds() == null ? duration : layer.getEndOffsetSeconds();
        double endX = left + usable * endSeconds / duration;
        if (event.getX() < startX - 8 || event.getX() > endX + 8) return;
        dragLayer = layer;
        dragOriginX = event.getX();
        dragStart = layer.getStartOffsetSeconds();
        dragEnd = layer.getEndOffsetSeconds();
        if (Math.abs(event.getX() - startX) <= 8) dragKind = DragKind.LAYER_START;
        else if (Math.abs(event.getX() - endX) <= 8) dragKind = DragKind.LAYER_END;
        else dragKind = DragKind.LAYER_MOVE;
    }

    private void mouseDragged(MouseEvent event) {
        SceneItem selected = selected();
        if (dragKind == DragKind.SCENE_START && dragScene != null) {
            double total = Math.max(1, scenes.stream().mapToDouble(s -> s.startSeconds() + s.durationSeconds()).max().orElse(1));
            double time = clamp((event.getX() - 8) / Math.max(1, canvas.getWidth() - 16) * total, 0, total);
            onSceneStartChanged.accept(dragScene.id(), roundMillis(time));
            return;
        }
        if (dragKind == DragKind.NONE || dragLayer == null || selected == null) return;
        double usable = Math.max(20, canvas.getWidth() - 122);
        double duration = Math.max(0.1, selected.durationSeconds());
        double delta = (event.getX() - dragOriginX) / usable * duration;
        double start = dragStart;
        double end = dragEnd == null ? duration : dragEnd;
        switch (dragKind) {
            case LAYER_START -> start = clamp(dragStart + delta, 0, Math.max(0, end - 0.01));
            case LAYER_END -> end = clamp((dragEnd == null ? duration : dragEnd) + delta, start + 0.01, duration);
            case LAYER_MOVE -> {
                double length = Math.max(0.01, end - start);
                start = clamp(dragStart + delta, 0, Math.max(0, duration - length));
                end = start + length;
            }
            default -> { return; }
        }
        dragLayer.setVisibilityWindow(roundMillis(start), roundMillis(end));
        onLayerChanged.accept(dragLayer);
        redraw();
    }

    private SceneItem selected() {
        return scenes.stream().filter(s -> s.id().equals(selectedSceneId)).findFirst().orElse(null);
    }

    private static float[] readPeaks(Path path) {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat sourceFormat = source.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(), 16, 1, 2, sourceFormat.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, source)) {
                byte[] bytes = pcm.readAllBytes();
                int samples = bytes.length / 2;
                int buckets = Math.min(900, Math.max(1, samples / 512));
                float[] peaks = new float[buckets];
                for (int i = 0; i < samples; i++) {
                    int lo = bytes[i * 2] & 0xff;
                    int hi = bytes[i * 2 + 1];
                    short sample = (short) (lo | (hi << 8));
                    int bucket = Math.min(buckets - 1, (int) ((long) i * buckets / Math.max(1, samples)));
                    peaks[bucket] = Math.max(peaks[bucket], Math.abs(sample / 32768f));
                }
                return peaks;
            }
        } catch (Exception ignored) {
            return new float[0];
        }
    }

    private static Color layerColor(VisualLayer layer) {
        return switch (layer.getType()) {
            case CHARACTER -> Color.web("#8B5CF6");
            case IMAGE -> Color.web("#10B981");
            case TEXT -> Color.web("#F59E0B");
            case PLACEHOLDER -> Color.web("#64748B");
        };
    }

    private static String shortName(String text, int max) {
        String value = text == null || text.isBlank() ? "Không tên" : text;
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    private static double roundMillis(double seconds) { return Math.round(seconds * 1000.0) / 1000.0; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
