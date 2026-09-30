package org.example.editvideoytbtool.library;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** One reusable character pose in the persistent PNG library. */
public class CharacterAsset {
    private String id = UUID.randomUUID().toString();
    private String displayName = "Nhân vật";
    private List<String> labels = new ArrayList<>();
    private String storedPath;
    private long createdAtEpochMillis = System.currentTimeMillis();

    public CharacterAsset() {
    }

    public CharacterAsset copy() {
        CharacterAsset copy = new CharacterAsset();
        copy.id = id;
        copy.displayName = displayName;
        copy.labels = new ArrayList<>(labels);
        copy.storedPath = storedPath;
        copy.createdAtEpochMillis = createdAtEpochMillis;
        return copy;
    }

    public String getId() { return id; }
    public void setId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        this.id = id;
    }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null || displayName.isBlank() ? "Nhân vật" : displayName.strip(); }
    public List<String> getLabels() { return labels; }
    public void setLabels(Collection<String> labels) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (labels != null) {
            labels.stream().filter(label -> label != null && !label.isBlank())
                    .map(String::strip).forEach(normalized::add);
        }
        this.labels = new ArrayList<>(normalized);
    }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath == null || storedPath.isBlank() ? null : storedPath; }
    public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
    public void setCreatedAtEpochMillis(long createdAtEpochMillis) { this.createdAtEpochMillis = createdAtEpochMillis; }
}
