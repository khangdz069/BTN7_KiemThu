package org.example.editvideoytbtool.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A narration unit and all visual layers shown while it is active. */
@JsonPropertyOrder({"id", "narration", "audioPath", "audioDurationSeconds", "audioStale",
        "layoutPreset", "manualStartSeconds", "pauseAfterSeconds", "timelineStartSeconds",
        "timelineEndSeconds", "layers"})
public class SceneData {
    private String id = UUID.randomUUID().toString();
    private String narration = "";
    private String audioPath;
    private double audioDurationSeconds;
    private boolean audioStale = true;
    private LayoutPreset layoutPreset = LayoutPreset.CHARACTER_RIGHT_IMAGE_LEFT;
    private Double manualStartSeconds;
    private double pauseAfterSeconds = 0.4;
    private double timelineStartSeconds;
    private double timelineEndSeconds;
    private List<VisualLayer> layers = new ArrayList<>();

    public SceneData() {
    }

    public SceneData(String narration) {
        this.narration = normalizeNarration(narration);
    }

    /**
     * Existing visual edits and the old WAV are retained, while the WAV is
     * marked stale so only this scene needs regeneration.
     */
    public void updateNarration(String narration) {
        setNarration(narration);
    }

    public void markAudioGenerated(String audioPath, double durationSeconds) {
        if (audioPath == null || audioPath.isBlank()) {
            throw new IllegalArgumentException("audioPath must not be blank");
        }
        requireNonNegativeFinite(durationSeconds, "durationSeconds");
        this.audioPath = audioPath;
        this.audioDurationSeconds = durationSeconds;
        this.audioStale = false;
    }

    public void clearAudio() {
        audioPath = null;
        audioDurationSeconds = 0;
        audioStale = true;
    }

    @JsonIgnore
    public Optional<VisualLayer> getCharacterLayer() {
        return layers.stream().filter(layer -> layer.getType() == VisualLayerType.CHARACTER).findFirst();
    }

    @JsonIgnore
    public double getLargestFiniteLayerEndSeconds() {
        return layers.stream()
                .map(VisualLayer::getEndOffsetSeconds)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
    }

    public void validate() {
        if (id == null || id.isBlank()) throw new IllegalStateException("Scene id is required");
        Objects.requireNonNull(layoutPreset, "Scene layout preset is required");
        requireNonNegativeFinite(audioDurationSeconds, "audioDurationSeconds");
        if (manualStartSeconds != null) requireNonNegativeFinite(manualStartSeconds, "manualStartSeconds");
        requireNonNegativeFinite(pauseAfterSeconds, "pauseAfterSeconds");
        requireNonNegativeFinite(timelineStartSeconds, "timelineStartSeconds");
        requireNonNegativeFinite(timelineEndSeconds, "timelineEndSeconds");
        if (timelineEndSeconds < timelineStartSeconds) throw new IllegalStateException("Scene timeline end is before its start");
        if (layers == null) throw new IllegalStateException("Scene layers collection is required");
        layers.forEach(VisualLayer::validate);
    }

    private static String normalizeNarration(String value) {
        return value == null ? "" : value.strip();
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
    public String getNarration() { return narration; }
    public void setNarration(String narration) {
        String normalized = normalizeNarration(narration);
        if (!Objects.equals(this.narration, normalized)) {
            this.narration = normalized;
            this.audioStale = true;
        }
    }
    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath == null || audioPath.isBlank() ? null : audioPath; }
    public double getAudioDurationSeconds() { return audioDurationSeconds; }
    public void setAudioDurationSeconds(double audioDurationSeconds) { requireNonNegativeFinite(audioDurationSeconds, "audioDurationSeconds"); this.audioDurationSeconds = audioDurationSeconds; }
    public boolean isAudioStale() { return audioStale; }
    public void setAudioStale(boolean audioStale) { this.audioStale = audioStale; }
    public LayoutPreset getLayoutPreset() { return layoutPreset; }
    public void setLayoutPreset(LayoutPreset layoutPreset) { this.layoutPreset = Objects.requireNonNull(layoutPreset, "layoutPreset"); }
    public Double getManualStartSeconds() { return manualStartSeconds; }
    public void setManualStartSeconds(Double manualStartSeconds) {
        if (manualStartSeconds != null) requireNonNegativeFinite(manualStartSeconds, "manualStartSeconds");
        this.manualStartSeconds = manualStartSeconds;
    }
    public double getPauseAfterSeconds() { return pauseAfterSeconds; }
    public void setPauseAfterSeconds(double pauseAfterSeconds) { requireNonNegativeFinite(pauseAfterSeconds, "pauseAfterSeconds"); this.pauseAfterSeconds = pauseAfterSeconds; }
    public double getTimelineStartSeconds() { return timelineStartSeconds; }
    public void setTimelineStartSeconds(double timelineStartSeconds) { requireNonNegativeFinite(timelineStartSeconds, "timelineStartSeconds"); this.timelineStartSeconds = timelineStartSeconds; }
    public double getTimelineEndSeconds() { return timelineEndSeconds; }
    public void setTimelineEndSeconds(double timelineEndSeconds) { requireNonNegativeFinite(timelineEndSeconds, "timelineEndSeconds"); this.timelineEndSeconds = timelineEndSeconds; }
    public List<VisualLayer> getLayers() { return layers; }
    public void setLayers(List<VisualLayer> layers) { this.layers = layers == null ? new ArrayList<>() : new ArrayList<>(layers); }
}
