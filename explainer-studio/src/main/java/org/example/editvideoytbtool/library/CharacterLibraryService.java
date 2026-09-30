package org.example.editvideoytbtool.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Persistent, application-wide character PNG library. */
public class CharacterLibraryService {
    public static final String APP_DATA_PROPERTY = "explainer.appData";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final Path libraryDirectory;
    private final Path imagesDirectory;
    private final Path indexFile;
    private final ObjectMapper mapper;
    private CharacterLibraryIndex index;

    public CharacterLibraryService() throws IOException {
        this(defaultAppDataDirectory());
    }

    /** The supplied directory is the app-data root; library files live below it. */
    public CharacterLibraryService(Path appDataDirectory) throws IOException {
        Objects.requireNonNull(appDataDirectory, "appDataDirectory");
        this.libraryDirectory = appDataDirectory.toAbsolutePath().normalize().resolve("character-library");
        this.imagesDirectory = libraryDirectory.resolve("images");
        this.indexFile = libraryDirectory.resolve("index.json");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Files.createDirectories(imagesDirectory);
        this.index = readIndex();
    }

    public synchronized List<CharacterAsset> list() {
        return index.getCharacters().stream().map(CharacterAsset::copy).toList();
    }

    public synchronized Optional<CharacterAsset> find(String id) {
        return index.getCharacters().stream()
                .filter(asset -> Objects.equals(id, asset.getId()))
                .findFirst().map(CharacterAsset::copy);
    }

    public synchronized CharacterAsset importPng(Path source, String displayName,
                                                  Collection<String> labels) throws IOException {
        Path png = requirePng(source);
        CharacterAsset asset = new CharacterAsset();
        asset.setDisplayName(displayName == null || displayName.isBlank()
                ? stripExtension(png.getFileName().toString()) : displayName);
        asset.setLabels(labels);

        String originalName = sanitizeFileName(png.getFileName().toString());
        Path target = imagesDirectory.resolve(asset.getId() + "-" + originalName).normalize();
        ensureInsideLibrary(target);
        Files.copy(png, target, StandardCopyOption.REPLACE_EXISTING);
        asset.setStoredPath(portableRelative(libraryDirectory, target));

        index.getCharacters().add(asset);
        try {
            writeIndex();
        } catch (IOException error) {
            index.getCharacters().remove(asset);
            Files.deleteIfExists(target);
            throw error;
        }
        return asset.copy();
    }

    public synchronized CharacterAsset update(String id, String displayName,
                                               Collection<String> labels) throws IOException {
        CharacterAsset asset = mutableAsset(id);
        String previousName = asset.getDisplayName();
        List<String> previousLabels = List.copyOf(asset.getLabels());
        asset.setDisplayName(displayName);
        asset.setLabels(labels);
        try {
            writeIndex();
        } catch (IOException error) {
            asset.setDisplayName(previousName);
            asset.setLabels(previousLabels);
            throw error;
        }
        return asset.copy();
    }

    public synchronized void remove(String id) throws IOException {
        CharacterAsset asset = mutableAsset(id);
        Path image = resolveImage(asset);
        int position = index.getCharacters().indexOf(asset);
        index.getCharacters().remove(asset);
        try {
            writeIndex();
            Files.deleteIfExists(image);
        } catch (IOException error) {
            index.getCharacters().add(position, asset);
            throw error;
        }
    }

    public synchronized Path resolveImage(CharacterAsset asset) throws IOException {
        Objects.requireNonNull(asset, "asset");
        if (asset.getStoredPath() == null) throw new IOException("Nhân vật chưa có file PNG.");
        Path relative;
        try {
            relative = Path.of(asset.getStoredPath().replace('/', java.io.File.separatorChar));
        } catch (RuntimeException error) {
            throw new IOException("Đường dẫn PNG trong thư viện không hợp lệ.", error);
        }
        if (relative.isAbsolute()) throw new IOException("Thư viện chứa đường dẫn PNG tuyệt đối không an toàn.");
        Path resolved = libraryDirectory.resolve(relative).normalize().toAbsolutePath();
        ensureInsideLibrary(resolved);
        return resolved;
    }

    public Path getLibraryDirectory() { return libraryDirectory; }
    public Path getIndexFile() { return indexFile; }

    public static Path defaultAppDataDirectory() {
        String override = System.getProperty(APP_DATA_PROPERTY);
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String windowsAppData = System.getenv("APPDATA");
        if (windowsAppData != null && !windowsAppData.isBlank()) {
            return Path.of(windowsAppData).resolve("ExplainerVideoStudio").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".explainer-video-studio").toAbsolutePath().normalize();
    }

    private CharacterLibraryIndex readIndex() throws IOException {
        if (!Files.exists(indexFile)) return new CharacterLibraryIndex();
        try {
            CharacterLibraryIndex loaded = mapper.readValue(indexFile.toFile(), CharacterLibraryIndex.class);
            if (loaded.getSchemaVersion() != 1) throw new IOException("Phiên bản thư viện nhân vật chưa được hỗ trợ: " + loaded.getSchemaVersion());
            for (CharacterAsset asset : loaded.getCharacters()) resolveImage(asset);
            return loaded;
        } catch (JsonProcessingException error) {
            throw new IOException("Chỉ mục thư viện nhân vật không phải JSON hợp lệ: " + indexFile, error);
        }
    }

    private void writeIndex() throws IOException {
        Files.createDirectories(libraryDirectory);
        Path temporary = Files.createTempFile(libraryDirectory, ".characters-", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), index);
            try {
                Files.move(temporary, indexFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, indexFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private CharacterAsset mutableAsset(String id) {
        return index.getCharacters().stream().filter(asset -> Objects.equals(id, asset.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown character id: " + id));
    }

    private Path requirePng(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IOException("Không tìm thấy file PNG: " + normalized);
        String fileName = normalized.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".png")) throw new IOException("Thư viện nhân vật chỉ nhận file PNG.");
        byte[] signature = new byte[PNG_SIGNATURE.length];
        try (InputStream input = Files.newInputStream(normalized)) {
            if (input.readNBytes(signature, 0, signature.length) != signature.length
                    || !Arrays.equals(signature, PNG_SIGNATURE)) {
                throw new IOException("File đã chọn không có định dạng PNG hợp lệ.");
            }
        }
        return normalized;
    }

    private void ensureInsideLibrary(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(libraryDirectory)) {
            throw new IOException("Đường dẫn đi ra ngoài thư viện nhân vật.");
        }
    }

    private static String portableRelative(Path base, Path target) {
        return base.relativize(target).toString().replace('\\', '/');
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "character.png" : sanitized;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
