package org.example.editvideoytbtool.service;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineServiceTest {
    private final TimelineService service = new TimelineService();

    @Test
    void usesAudioDurationAndPauseForAutomaticScenes() {
        ProjectDocument project = new ProjectDocument();
        SceneData first = new SceneData("Một");
        first.markAudioGenerated("one.wav", 2.5);
        first.setPauseAfterSeconds(0.75);
        SceneData second = new SceneData("Hai");
        second.markAudioGenerated("two.wav", 3.0);
        project.getScenes().addAll(java.util.List.of(first, second));

        TimelineResult result = service.recalculate(project);

        assertEquals(0, first.getTimelineStartSeconds(), 1e-9);
        assertEquals(2.5, first.getTimelineEndSeconds(), 1e-9);
        assertEquals(3.25, second.getTimelineStartSeconds(), 1e-9);
        assertEquals(6.25, result.totalDurationSeconds(), 1e-9);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void honorsManualStartAndReportsAnOverlap() {
        ProjectDocument project = new ProjectDocument();
        SceneData first = new SceneData("Một");
        first.markAudioGenerated("one.wav", 4);
        SceneData second = new SceneData("Hai");
        second.markAudioGenerated("two.wav", 2);
        second.setManualStartSeconds(1.5);
        project.getScenes().addAll(java.util.List.of(first, second));

        TimelineResult result = service.recalculate(project);

        assertEquals(1.5, second.getTimelineStartSeconds(), 1e-9);
        assertTrue(result.scenes().get(1).overlapsPrevious());
        assertFalse(result.warnings().isEmpty());
        assertEquals(4, result.totalDurationSeconds(), 1e-9);
    }

    @Test
    void fallsBackToLayerEndThenPreviewDefaultWithoutAudio() {
        SceneData scene = new SceneData("Chưa có WAV");
        VisualLayer text = VisualLayer.text("Xin chào");
        text.setVisibilityWindow(1, 7.5);
        scene.getLayers().add(text);

        assertEquals(7.5, service.effectiveDuration(scene, 5), 1e-9);
        text.setVisibilityWindow(1, 2.0);
        assertEquals(5, service.effectiveDuration(scene, 5), 1e-9);
        scene.getLayers().clear();
        assertEquals(5, service.effectiveDuration(scene, 5), 1e-9);
    }
}
