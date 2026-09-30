package org.example.editvideoytbtool.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterLibraryServiceTest {
    private static final byte[] MINIMAL_PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void importedPngAndLabelsSurviveServiceRestart() throws Exception {
        Path png = Files.write(temporaryDirectory.resolve("normal.png"), MINIMAL_PNG_HEADER);
        CharacterLibraryService service = new CharacterLibraryService(temporaryDirectory.resolve("app-data"));

        CharacterAsset imported = service.importPng(png, "Minh bình thường",
                List.of("bình thường", "đang nói", "đang nói"));
        assertEquals(2, imported.getLabels().size());
        assertTrue(Files.isRegularFile(service.resolveImage(imported)));

        CharacterLibraryService reopened = new CharacterLibraryService(temporaryDirectory.resolve("app-data"));
        assertEquals(1, reopened.list().size());
        assertEquals(imported.getId(), reopened.list().getFirst().getId());
        assertEquals("Minh bình thường", reopened.list().getFirst().getDisplayName());

        reopened.update(imported.getId(), "Minh nói", List.of("đang nói"));
        assertEquals("Minh nói", reopened.find(imported.getId()).orElseThrow().getDisplayName());
        reopened.remove(imported.getId());
        assertTrue(reopened.list().isEmpty());
        assertFalse(Files.exists(reopened.resolveImage(imported)));
    }

    @Test
    void rejectsRenamedNonPngContent() throws Exception {
        Path fake = Files.write(temporaryDirectory.resolve("fake.png"), new byte[]{1, 2, 3});
        CharacterLibraryService service = new CharacterLibraryService(temporaryDirectory.resolve("app-data"));
        assertThrows(IOException.class, () -> service.importPng(fake, "Sai", List.of()));
    }
}
