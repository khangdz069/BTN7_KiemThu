package org.example.editvideoytbtool.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Root object of the portable JSON project format. */
@JsonPropertyOrder({"schemaVersion", "projectId", "name", "createdAtEpochMillis",
        "modifiedAtEpochMillis", "settings", "scenes"})
public class ProjectDocument {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private String projectId = UUID.randomUUID().toString();
    private String name = "Dự án chưa đặt tên";
    private long createdAtEpochMillis = System.currentTimeMillis();
    private long modifiedAtEpochMillis = createdAtEpochMillis;
    private VideoSettings settings = new VideoSettings();
    private List<SceneData> scenes = new ArrayList<>();
    private transient Path projectFile;

    public ProjectDocument() {
    }

    public void validate() {
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) throw new IllegalStateException("Unsupported project schema version: " + schemaVersion);
        if (projectId == null || projectId.isBlank()) throw new IllegalStateException("Project id is required");
        Objects.requireNonNull(settings, "Video settings are required");
        if (scenes == null) throw new IllegalStateException("Scene collection is required");
        Set<String> sceneIds = new HashSet<>();
        Set<String> layerIds = new HashSet<>();
        for (SceneData scene : scenes) {
            Objects.requireNonNull(scene, "Scene collection contains null");
            scene.validate();
            if (!sceneIds.add(scene.getId())) throw new IllegalStateException("Duplicate scene id: " + scene.getId());
            for (VisualLayer layer : scene.getLayers()) {
                if (!layerIds.add(layer.getId())) throw new IllegalStateException("Duplicate visual layer id: " + layer.getId());
            }
        }
    }

    public void touch() { modifiedAtEpochMillis = System.currentTimeMillis(); }

    @JsonIgnore
    public Optional<SceneData> findScene(String id) {
        if (id == null) return Optional.empty();
        return scenes.stream().filter(scene -> id.equals(scene.getId())).findFirst();
    }

    @JsonIgnore
    public int indexOfScene(String id) {
        for (int i = 0; i < scenes.size(); i++) if (Objects.equals(id, scenes.get(i).getId())) return i;
        return -1;
    }

    @JsonIgnore
    public Path getProjectFile() { return projectFile; }
    @JsonIgnore
    public Path getProjectDirectory() { return projectFile == null ? null : projectFile.toAbsolutePath().normalize().getParent(); }
    @JsonIgnore
    public void setProjectFile(Path projectFile) { this.projectFile = projectFile == null ? null : projectFile.toAbsolutePath().normalize(); }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
        this.projectId = projectId;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null || name.isBlank() ? "Dự án chưa đặt tên" : name.strip(); }
    public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
    public void setCreatedAtEpochMillis(long createdAtEpochMillis) { this.createdAtEpochMillis = createdAtEpochMillis; }
    public long getModifiedAtEpochMillis() { return modifiedAtEpochMillis; }
    public void setModifiedAtEpochMillis(long modifiedAtEpochMillis) { this.modifiedAtEpochMillis = modifiedAtEpochMillis; }
    public VideoSettings getSettings() { return settings; }
    public void setSettings(VideoSettings settings) { this.settings = settings == null ? new VideoSettings() : settings; }
    public List<SceneData> getScenes() { return scenes; }
    public void setScenes(List<SceneData> scenes) { this.scenes = scenes == null ? new ArrayList<>() : new ArrayList<>(scenes); }
}
