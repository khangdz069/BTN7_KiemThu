package org.example.editvideoytbtool.service;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Calculates scene boundaries from WAV durations, pauses and optional overrides. */
public class TimelineService {

    /**
     * Recalculates and writes each scene's derived start/end values. Manual
     * starts are absolute seconds. They may intentionally overlap another
     * scene; such overlaps are reported as warnings instead of silently moved.
     */
    public TimelineResult recalculate(ProjectDocument project) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(project.getSettings(), "project.settings");

        List<SceneTiming> timings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double automaticCursor = 0;
        double latestContentEnd = 0;

        for (int i = 0; i < project.getScenes().size(); i++) {
            SceneData scene = project.getScenes().get(i);
            double duration = effectiveDuration(scene, project.getSettings().getDefaultSceneDurationSeconds());
            boolean manual = scene.getManualStartSeconds() != null;
            double start = manual ? scene.getManualStartSeconds() : automaticCursor;
            double end = start + duration;
            boolean overlaps = i > 0 && start < latestContentEnd;

            if (overlaps) {
                warnings.add("Cảnh " + (i + 1) + " bắt đầu trước khi cảnh trước kết thúc.");
            }

            scene.setTimelineStartSeconds(start);
            scene.setTimelineEndSeconds(end);
            timings.add(new SceneTiming(scene.getId(), i, start, end, duration, manual, overlaps));

            latestContentEnd = Math.max(latestContentEnd, end);
            automaticCursor = Math.max(automaticCursor, end + scene.getPauseAfterSeconds());
        }

        return new TimelineResult(timings, latestContentEnd, warnings);
    }

    /** Returns the exact WAV duration, otherwise a preview long enough for the default and all finite layers. */
    public double effectiveDuration(SceneData scene, double defaultDurationSeconds) {
        Objects.requireNonNull(scene, "scene");
        if (!Double.isFinite(defaultDurationSeconds) || defaultDurationSeconds <= 0) {
            throw new IllegalArgumentException("defaultDurationSeconds must be positive and finite");
        }
        if (scene.getAudioDurationSeconds() > 0) {
            return scene.getAudioDurationSeconds();
        }
        double layerEnd = scene.getLargestFiniteLayerEndSeconds();
        return Math.max(layerEnd, defaultDurationSeconds);
    }

    public void setManualStart(ProjectDocument project, String sceneId, Double startSeconds) {
        SceneData scene = project.findScene(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scene id: " + sceneId));
        scene.setManualStartSeconds(startSeconds);
        recalculate(project);
        project.touch();
    }

    public void clearAllManualStarts(ProjectDocument project) {
        Objects.requireNonNull(project, "project");
        project.getScenes().forEach(scene -> scene.setManualStartSeconds(null));
        recalculate(project);
        project.touch();
    }
}
