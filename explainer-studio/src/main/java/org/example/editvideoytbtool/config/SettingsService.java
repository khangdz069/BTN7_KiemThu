package org.example.editvideoytbtool.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SettingsService {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path file;

    public SettingsService() {
        this(resolveAppData().resolve("settings.json"));
    }

    public SettingsService(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public AppSettings load() {
        if (!Files.isRegularFile(file)) return new AppSettings();
        try {
            return mapper.readValue(file.toFile(), AppSettings.class);
        } catch (IOException ignored) {
            return new AppSettings();
        }
    }

    public void save(AppSettings settings) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), settings);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException noAtomicMove) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getFile() { return file; }

    public static Path resolveAppData() {
        String override = System.getProperty("explainer.appData", "").trim();
        if (!override.isEmpty()) return Path.of(override).toAbsolutePath().normalize();
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".explainer-video-studio")
                : Path.of(appData, "ExplainerVideoStudio");
        return base.toAbsolutePath().normalize();
    }
}
