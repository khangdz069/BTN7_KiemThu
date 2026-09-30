package org.example.editvideoytbtool.persistence;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void saveCopiesExternalAssetsAndOpenRoundTripsAllSceneEdits() throws Exception {
        Path external = temporaryDirectory.resolve("external");
        Files.createDirectories(external);
        Path png = Files.write(external.resolve("nhan vat.png"), new byte[]{1, 2, 3});
        Path wav = Files.write(external.resolve("voice.wav"), new byte[]{4, 5, 6});
        Path projectFile = temporaryDirectory.resolve("portable").resolve("lesson.explainer.json");

        ProjectDocument project = new ProjectDocument();
        project.setName("Bài thử");
        SceneData scene = new SceneData("Xin chào.");
        scene.markAudioGenerated(wav.toString(), 2.75);
        scene.setManualStartSeconds(1.25);
        VisualLayer character = VisualLayer.character(png.toString());
        character.setX(1200);
        character.setVisibilityWindow(0.4, 2.2);
        scene.getLayers().add(character);
        project.getScenes().add(scene);

        ProjectRepository repository = new ProjectRepository();
        repository.save(project, projectFile);

        assertFalse(Path.of(scene.getAudioPath()).isAbsolute());
        assertFalse(Path.of(character.getAssetPath()).isAbsolute());
        assertTrue(Files.isRegularFile(repository.resolveAsset(projectFile, scene.getAudioPath())));
        assertTrue(Files.isRegularFile(repository.resolveAsset(projectFile, character.getAssetPath())));

        ProjectDocument reopened = repository.open(projectFile);
        SceneData loaded = reopened.getScenes().getFirst();
        assertEquals(scene.getId(), loaded.getId());
        assertEquals(character.getId(), loaded.getLayers().getFirst().getId());
        assertEquals(2.75, loaded.getAudioDurationSeconds(), 1e-9);
        assertFalse(loaded.isAudioStale());
        assertEquals(1.25, loaded.getManualStartSeconds(), 1e-9);
        assertEquals(1200, loaded.getLayers().getFirst().getX(), 1e-9);
        assertEquals(0.4, loaded.getLayers().getFirst().getStartOffsetSeconds(), 1e-9);
        assertEquals(projectFile.toAbsolutePath().normalize(), reopened.getProjectFile());
    }

    @Test
    void safeResolverRejectsAbsoluteAndTraversalPaths() {
        ProjectRepository repository = new ProjectRepository();
        Path file = temporaryDirectory.resolve("project.json");

        assertThrows(IOException.class, () -> repository.resolveAsset(file, "../secret.png"));
        assertThrows(IOException.class, () -> repository.resolveAsset(file, temporaryDirectory.resolve("secret.png").toString()));
    }

    @Test
    void openReportsMalformedJsonClearly() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("broken.json"), "{not-json", StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class, () -> new ProjectRepository().open(file));
        assertTrue(error.getMessage().contains("JSON"));
    }
}
