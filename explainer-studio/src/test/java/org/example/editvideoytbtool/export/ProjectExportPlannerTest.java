package org.example.editvideoytbtool.export;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectExportPlannerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void recalculatesSceneTimelineAndConvertsRelativeLayerWindowsToAbsoluteTime() throws Exception {
        Files.createDirectories(tempDirectory.resolve("audio"));
        Files.write(tempDirectory.resolve("audio/one.wav"), new byte[]{1});
        Files.write(tempDirectory.resolve("audio/two.wav"), new byte[]{1});

        ProjectDocument project = new ProjectDocument();
        project.setProjectFile(tempDirectory.resolve("demo.evproj"));

        SceneData first = new SceneData("Cảnh một.");
        first.markAudioGenerated("audio/one.wav", 1.5);
        first.setPauseAfterSeconds(0.25);
        VisualLayer firstText = VisualLayer.text("Nội dung");
        firstText.setVisibilityWindow(0.25, 1.0);
        first.getLayers().add(firstText);

        SceneData second = new SceneData("Cảnh hai.");
        second.markAudioGenerated("audio/two.wav", 2.0);
        VisualLayer placeholder = VisualLayer.placeholder("Một sơ đồ");
        placeholder.setVisibilityWindow(0, null);
        second.getLayers().add(placeholder);
        project.getScenes().add(first);
        project.getScenes().add(second);

        ExportPlan plan = new ProjectExportPlanner().create(project, null);

        assertEquals(3.75, plan.durationSeconds(), 0.000_001);
        assertEquals(1.75, second.getTimelineStartSeconds(), 0.000_001);
        assertEquals(0.25, plan.layers().getFirst().startSeconds(), 0.000_001);
        assertEquals(1.0, plan.layers().getFirst().endSeconds(), 0.000_001);
        assertEquals(1.75, plan.layers().get(1).startSeconds(), 0.000_001);
        assertEquals(3.75, plan.layers().get(1).endSeconds(), 0.000_001);
        assertEquals(tempDirectory.resolve("audio/one.wav").toAbsolutePath().normalize(),
                plan.audioItems().getFirst().file());
    }

    @Test
    void rejectsPortableAssetTraversal() throws Exception {
        ProjectDocument project = new ProjectDocument();
        project.setProjectFile(tempDirectory.resolve("demo.evproj"));
        SceneData scene = new SceneData("");
        VisualLayer layer = VisualLayer.image("../outside.png");
        scene.getLayers().add(layer);
        project.getScenes().add(scene);

        ExportException error = assertThrows(
                ExportException.class,
                () -> new ProjectExportPlanner().create(project, null)
        );

        assertEquals(ExportException.Code.INVALID_PROJECT, error.getCode());
        assertTrue(error.getMessage().contains("ngoài thư mục dự án"), error.getMessage());
    }

    @Test
    void asksForSceneAudioRegenerationWhenNarrationIsStale() {
        ProjectDocument project = new ProjectDocument();
        project.setProjectFile(tempDirectory.resolve("demo.evproj"));
        project.getScenes().add(new SceneData("Lời thoại chưa được tạo giọng."));

        ExportException error = assertThrows(
                ExportException.class,
                () -> new ProjectExportPlanner().create(project, null)
        );

        assertEquals(ExportException.Code.INVALID_PROJECT, error.getCode());
        assertTrue(error.getMessage().contains("tạo lại giọng đọc"), error.getMessage());
    }
}
