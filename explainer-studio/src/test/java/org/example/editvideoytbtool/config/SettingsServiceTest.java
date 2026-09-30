package org.example.editvideoytbtool.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsServiceTest {
    @TempDir Path temp;

    @Test void persistsNonSecretSettingsOnly() throws Exception {
        Path file = temp.resolve("settings.json");
        AppSettings settings = new AppSettings();
        settings.setFfmpegPath("C:/tools/ffmpeg.exe");
        settings.setLlmModel("my-model");
        settings.setLlmApiKey("do-not-write");
        new SettingsService(file).save(settings);

        String json = Files.readString(file);
        assertFalse(json.contains("do-not-write"));
        AppSettings loaded = new SettingsService(file).load();
        assertEquals("C:/tools/ffmpeg.exe", loaded.getFfmpegPath());
        assertEquals("my-model", loaded.getLlmModel());
    }
}
