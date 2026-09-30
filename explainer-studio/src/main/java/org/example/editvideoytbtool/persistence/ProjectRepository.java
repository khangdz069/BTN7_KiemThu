package org.example.editvideoytbtool.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.example.editvideoytbtool.model.VisualLayerType;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads and writes portable JSON projects and their local asset folders. */
public class ProjectRepository {
    private final ObjectMapper mapper;

    public ProjectRepository() {
        this(new ObjectMapper());
    }

    public ProjectRepository(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Saves the document and copies every referenced external image/WAV into
     * assets/images or assets/audio beside the project file.
     */
    public synchronized void save(ProjectDocument project, Path projectFile) throws IOException {
        Objects.requireNonNull(project, "project");
        Path file = normalizeProjectFile(projectFile);
        Path projectDirectory = file.getParent();
        Files.createDirectories(projectDirectory);
        project.validate();

        List<PreparedAsset> preparedAssets = prepareAssets(project, projectDirectory);
        for (PreparedAsset asset : preparedAssets) {
            asset.applyPortablePath().run();
        }

        project.touch();
        Path temporary = Files.createTempFile(projectDirectory, ".project-", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), project);
            moveAtomicallyOrReplace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
        project.setProjectFile(file);
    }

    public synchronized ProjectDocument open(Path projectFile) throws IOException {
        Path file = normalizeProjectFile(projectFile);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Không tìm thấy file dự án: " + file);
        }

        ProjectDocument project;
        try {
            project = mapper.readValue(file.toFile(), ProjectDocument.class);
        } catch (JsonProcessingException error) {
            throw new IOException("File dự án không phải JSON hợp lệ: " + file, error);
        }

        try {
            project.validate();
            validatePortableAssetPaths(project, file);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new IOException("Dữ liệu dự án không hợp lệ: " + error.getMessage(), error);
        }
        project.setProjectFile(file);
        return project;
    }

    /** Safely resolves a stored relative path and rejects absolute/traversal paths. */
    public Path resolveAsset(Path projectFile, String storedPath) throws IOException {
        Path file = normalizeProjectFile(projectFile);
        if (storedPath == null || storedPath.isBlank()) {
            throw new IOException("Đường dẫn tài nguyên đang trống.");
        }

        final Path relative;
        try {
            relative = Path.of(storedPath.replace('/', java.io.File.separatorChar));
        } catch (InvalidPathException error) {
            throw new IOException("Đường dẫn tài nguyên không hợp lệ: " + storedPath, error);
        }
        if (relative.isAbsolute()) {
            throw new IOException("Dự án chứa đường dẫn tuyệt đối không an toàn: " + storedPath);
        }

        Path projectDirectory = file.getParent();
        Path resolved = projectDirectory.resolve(relative).normalize().toAbsolutePath();
        if (!resolved.startsWith(projectDirectory)) {
            throw new IOException("Đường dẫn tài nguyên đi ra ngoài thư mục dự án: " + storedPath);
        }

        if (Files.exists(resolved) && Files.exists(projectDirectory)) {
            Path realProjectDirectory = projectDirectory.toRealPath();
            Path realResolved = resolved.toRealPath();
            if (!realResolved.startsWith(realProjectDirectory)) {
                throw new IOException("Liên kết tài nguyên đi ra ngoài thư mục dự án: " + storedPath);
            }
        }
        return resolved;
    }

    public Path resolveAsset(ProjectDocument project, String storedPath) throws IOException {
        Objects.requireNonNull(project, "project");
        if (project.getProjectFile() == null) {
            throw new IOException("Dự án chưa được lưu nên chưa có thư mục tài nguyên.");
        }
        return resolveAsset(project.getProjectFile(), storedPath);
    }

    public ObjectMapper getObjectMapper() {
        return mapper.copy();
    }

    private List<PreparedAsset> prepareAssets(ProjectDocument project, Path targetDirectory) throws IOException {
        List<PreparedAsset> prepared = new ArrayList<>();
        Map<String, String> copiedSources = new HashMap<>();

        for (SceneData scene : project.getScenes()) {
            if (scene.getAudioPath() != null) {
                Path source = locateSource(project, targetDirectory, scene.getAudioPath());
                String portable = copyAsset(source, targetDirectory, "audio", "scene-" + scene.getId(), copiedSources);
                prepared.add(new PreparedAsset(portable, () -> scene.setAudioPath(portable)));
            }
            for (VisualLayer layer : scene.getLayers()) {
                if (!isFileBacked(layer) || layer.getAssetPath() == null) continue;
                Path source = locateSource(project, targetDirectory, layer.getAssetPath());
                String portable = copyAsset(source, targetDirectory, "images", "layer-" + layer.getId(), copiedSources);
                prepared.add(new PreparedAsset(portable, () -> layer.setAssetPath(portable)));
            }
        }
        return prepared;
    }

    private boolean isFileBacked(VisualLayer layer) {
        return layer.getType() == VisualLayerType.CHARACTER || layer.getType() == VisualLayerType.IMAGE;
    }

    private Path locateSource(ProjectDocument project, Path targetDirectory, String storedPath) throws IOException {
        final Path supplied;
        try {
            supplied = Path.of(storedPath.replace('/', java.io.File.separatorChar));
        } catch (InvalidPathException error) {
            throw new IOException("Đường dẫn tài nguyên không hợp lệ: " + storedPath, error);
        }

        List<Path> candidates = new ArrayList<>();
        if (supplied.isAbsolute()) {
            candidates.add(supplied);
        } else {
            if (project.getProjectDirectory() != null) candidates.add(project.getProjectDirectory().resolve(supplied));
            candidates.add(targetDirectory.resolve(supplied));
        }
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) return normalized;
        }
        throw new IOException("Không tìm thấy tài nguyên để lưu cùng dự án: " + storedPath);
    }

    private String copyAsset(Path source, Path projectDirectory, String category, String ownerPrefix,
                             Map<String, String> copiedSources) throws IOException {
        Path canonicalSource = source.toRealPath();
        String cacheKey = category + "\n" + canonicalSource;
        String cached = copiedSources.get(cacheKey);
        if (cached != null) return cached;

        Path assetsDirectory = projectDirectory.resolve("assets").resolve(category).normalize();
        Files.createDirectories(assetsDirectory);

        if (canonicalSource.startsWith(projectDirectory) && canonicalSource.startsWith(assetsDirectory)) {
            String portable = portableRelative(projectDirectory, canonicalSource);
            copiedSources.put(cacheKey, portable);
            return portable;
        }

        String originalName = source.getFileName() == null ? "asset" : source.getFileName().toString();
        String targetName = sanitizeFileName(ownerPrefix) + "-" + sanitizeFileName(originalName);
        Path target = assetsDirectory.resolve(targetName).normalize();
        if (!target.startsWith(assetsDirectory)) throw new IOException("Tên tài nguyên không an toàn: " + originalName);
        if (!Files.exists(target) || !Files.isSameFile(canonicalSource, target)) {
            Files.copy(canonicalSource, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String portable = portableRelative(projectDirectory, target);
        copiedSources.put(cacheKey, portable);
        return portable;
    }

    private void validatePortableAssetPaths(ProjectDocument project, Path projectFile) throws IOException {
        for (SceneData scene : project.getScenes()) {
            if (scene.getAudioPath() != null) resolveAsset(projectFile, scene.getAudioPath());
            for (VisualLayer layer : scene.getLayers()) {
                if (isFileBacked(layer) && layer.getAssetPath() != null) resolveAsset(projectFile, layer.getAssetPath());
            }
        }
    }

    private static Path normalizeProjectFile(Path projectFile) {
        Objects.requireNonNull(projectFile, "projectFile");
        Path file = projectFile.toAbsolutePath().normalize();
        if (file.getParent() == null) throw new IllegalArgumentException("Project file needs a parent directory");
        return file;
    }

    private static String portableRelative(Path base, Path target) {
        return base.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "asset" : sanitized;
    }

    private static void moveAtomicallyOrReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record PreparedAsset(String portablePath, Runnable applyPortablePath) {
    }
}
