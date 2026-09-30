package org.example.editvideoytbtool.service;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptSceneServiceTest {
    private final ScriptSceneService service = new ScriptSceneService();

    @Test
    void explicitMarkersDefineScenes() {
        List<SceneData> scenes = service.parseScript("Cảnh 1\nXin chào. Đây là ý một.\n---\nÝ thứ hai. Kết thúc.");

        assertEquals(2, scenes.size());
        assertEquals("Xin chào. Đây là ý một.", scenes.get(0).getNarration());
        assertEquals("Ý thứ hai. Kết thúc.", scenes.get(1).getNarration());
    }

    @Test
    void fallbackGroupsFiveSentencesAsThreeAndTwo() {
        List<SceneData> scenes = service.parseScript("Một. Hai. Ba. Bốn. Năm.");

        assertEquals(2, scenes.size());
        assertEquals("Một. Hai. Ba.", scenes.get(0).getNarration());
        assertEquals("Bốn. Năm.", scenes.get(1).getNarration());
    }

    @Test
    void editingOnlyMarksThatScenesAudioStaleAndKeepsVisuals() {
        ProjectDocument project = new ProjectDocument();
        SceneData first = new SceneData("Câu cũ.");
        first.markAudioGenerated("old.wav", 2);
        VisualLayer layer = VisualLayer.text("Không mất");
        first.getLayers().add(layer);
        SceneData second = new SceneData("Không đổi.");
        second.markAudioGenerated("second.wav", 1);
        project.getScenes().addAll(List.of(first, second));

        service.updateNarration(project, first.getId(), "Câu mới.");

        assertTrue(first.isAudioStale());
        assertEquals("old.wav", first.getAudioPath());
        assertEquals(layer.getId(), first.getLayers().getFirst().getId());
        assertFalse(second.isAudioStale());
    }

    @Test
    void splitClonesLayersWithNewIdsAndReorderKeepsAudioFresh() {
        ProjectDocument project = new ProjectDocument();
        SceneData scene = new SceneData("Phần đầu. Phần sau.");
        scene.getLayers().add(VisualLayer.text("Tiêu đề"));
        project.getScenes().add(scene);

        SceneData second = service.splitScene(project, scene.getId(), "Phần đầu.".length());
        assertEquals(2, project.getScenes().size());
        assertNotEquals(scene.getId(), second.getId());
        assertNotEquals(scene.getLayers().getFirst().getId(), second.getLayers().getFirst().getId());

        scene.markAudioGenerated("first.wav", 1);
        second.markAudioGenerated("second.wav", 1);
        service.moveScene(project, 1, 0);
        assertFalse(scene.isAudioStale());
        assertFalse(second.isAudioStale());
    }

    @Test
    void mergeClearsInvalidAudioAndPreservesBothLayerTimings() {
        ProjectDocument project = new ProjectDocument();
        SceneData first = new SceneData("Một.");
        first.markAudioGenerated("one.wav", 2);
        first.getLayers().add(VisualLayer.text("A"));
        SceneData second = new SceneData("Hai.");
        second.markAudioGenerated("two.wav", 3);
        VisualLayer b = VisualLayer.text("B");
        b.setStartOffsetSeconds(0.5);
        second.getLayers().add(b);
        project.getScenes().addAll(List.of(first, second));

        service.mergeWithNext(project, first.getId());

        assertEquals(1, project.getScenes().size());
        assertEquals("Một. Hai.", first.getNarration());
        assertTrue(first.isAudioStale());
        assertEquals(null, first.getAudioPath());
        assertEquals(2.5, first.getLayers().get(1).getStartOffsetSeconds(), 1e-9);
    }
}
