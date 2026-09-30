package org.example.editvideoytbtool.export;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VideoSettings;
import org.example.editvideoytbtool.model.VisualLayer;
import org.example.editvideoytbtool.service.TimelineService;

import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Adapts the persisted project model into a validated, absolute export plan. */
final class ProjectExportPlanner {
    private final TimelineService timelineService;

    ProjectExportPlanner() {
        this(new TimelineService());
    }

    ProjectExportPlanner(TimelineService timelineService) {
        this.timelineService = Objects.requireNonNull(timelineService, "timelineService");
    }

    ExportPlan create(ProjectDocument project, Path explicitProjectDirectory) throws ExportException {
        Objects.requireNonNull(project, "project");
        try {
            project.validate();
        } catch (RuntimeException e) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "Dự án có dữ liệu không hợp lệ nên chưa thể xuất video.",
                    e
            );
        }
        if (project.getScenes().isEmpty()) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "Dự án chưa có cảnh nào để xuất."
            );
        }

        try {
            timelineService.recalculate(project);
        } catch (RuntimeException e) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "Không thể tính timeline. Hãy kiểm tra thời lượng và mốc bắt đầu của các cảnh.",
                    e
            );
        }

        Path projectDirectory = explicitProjectDirectory == null
                ? project.getProjectDirectory()
                : explicitProjectDirectory;
        if (projectDirectory != null) {
            projectDirectory = projectDirectory.toAbsolutePath().normalize();
        }

        VideoSettings settings = project.getSettings();
        List<ExportPlan.LayerItem> layers = new ArrayList<>();
        List<ExportPlan.AudioItem> audioItems = new ArrayList<>();
        double duration = 0;

        for (int sceneIndex = 0; sceneIndex < project.getScenes().size(); sceneIndex++) {
            SceneData scene = project.getScenes().get(sceneIndex);
            double sceneStart = scene.getTimelineStartSeconds();
            double sceneEnd = scene.getTimelineEndSeconds();
            double sceneDuration = sceneEnd - sceneStart;
            if (!Double.isFinite(sceneStart) || !Double.isFinite(sceneEnd)
                    || sceneStart < 0 || sceneDuration <= 0) {
                throw invalidScene(sceneIndex, "mốc thời gian không hợp lệ");
            }
            duration = Math.max(duration, sceneEnd);

            addAudio(scene, sceneIndex, sceneStart, projectDirectory, audioItems);
            for (VisualLayer layer : scene.getLayers()) {
                addLayer(layer, sceneIndex, sceneStart, sceneDuration, projectDirectory, layers);
            }
        }

        if (!Double.isFinite(duration) || duration <= 0) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "Tổng thời lượng video phải lớn hơn 0 giây."
            );
        }
        return new ExportPlan(
                settings.getWidth(),
                settings.getHeight(),
                settings.getFramesPerSecond(),
                duration,
                layers,
                audioItems
        );
    }

    private static void addAudio(
            SceneData scene,
            int sceneIndex,
            double sceneStart,
            Path projectDirectory,
            List<ExportPlan.AudioItem> result
    ) throws ExportException {
        boolean hasNarration = scene.getNarration() != null && !scene.getNarration().isBlank();
        if (hasNarration && scene.isAudioStale()) {
            throw invalidScene(sceneIndex, "lời thoại đã thay đổi; hãy tạo lại giọng đọc");
        }
        if (scene.getAudioPath() == null || scene.getAudioPath().isBlank()) {
            if (hasNarration) {
                throw invalidScene(sceneIndex, "chưa có file WAV; hãy tạo giọng đọc trước khi xuất");
            }
            return;
        }
        if (scene.getAudioDurationSeconds() <= 0) {
            throw invalidScene(sceneIndex, "file WAV chưa có thời lượng hợp lệ");
        }
        Path audio = resolveStoredPath(scene.getAudioPath(), projectDirectory, "âm thanh cảnh " + (sceneIndex + 1));
        if (!Files.isRegularFile(audio)) {
            throw invalidScene(sceneIndex, "không tìm thấy file WAV ‘" + audio + "’");
        }
        result.add(new ExportPlan.AudioItem(scene.getId(), audio, sceneStart));
    }

    private static void addLayer(
            VisualLayer layer,
            int sceneIndex,
            double sceneStart,
            double sceneDuration,
            Path projectDirectory,
            List<ExportPlan.LayerItem> result
    ) throws ExportException {
        // Zero-sized/fully transparent/out-of-range layers are valid editor
        // states but have no pixels in the exported video.
        if (layer.getWidth() <= 0 || layer.getHeight() <= 0 || layer.getOpacity() <= 0) {
            return;
        }
        double relativeStart = Math.max(0, layer.getStartOffsetSeconds());
        double requestedEnd = layer.getEndOffsetSeconds() == null
                ? sceneDuration
                : layer.getEndOffsetSeconds();
        double relativeEnd = Math.min(sceneDuration, requestedEnd);
        if (relativeEnd <= relativeStart || relativeStart >= sceneDuration) {
            return;
        }

        Path asset = null;
        if (layer.getType() == org.example.editvideoytbtool.model.VisualLayerType.CHARACTER
                || layer.getType() == org.example.editvideoytbtool.model.VisualLayerType.IMAGE) {
            if (layer.getAssetPath() == null || layer.getAssetPath().isBlank()) {
                throw new ExportException(
                        ExportException.Code.INVALID_PROJECT,
                        "Lớp ‘" + displayName(layer) + "’ ở cảnh " + (sceneIndex + 1)
                                + " chưa có file ảnh."
                );
            }
            asset = resolveStoredPath(layer.getAssetPath(), projectDirectory, displayName(layer));
        }

        String visibleText = layer.getType() == org.example.editvideoytbtool.model.VisualLayerType.PLACEHOLDER
                ? layer.getDescription()
                : layer.getText();
        result.add(new ExportPlan.LayerItem(
                layer.getId(),
                ExportPlan.LayerKind.valueOf(layer.getType().name()),
                asset,
                visibleText,
                layer.getFontFamily(),
                layer.getFontSize(),
                layer.getTextColor(),
                layer.getX(),
                layer.getY(),
                layer.getWidth(),
                layer.getHeight(),
                layer.getRotationDegrees(),
                layer.getOpacity(),
                layer.getZIndex(),
                sceneIndex,
                sceneStart + relativeStart,
                sceneStart + relativeEnd
        ));
    }

    private static Path resolveStoredPath(String stored, Path projectDirectory, String label)
            throws ExportException {
        try {
            Path path = Path.of(stored);
            if (!path.isAbsolute()) {
                if (projectDirectory == null) {
                    throw new ExportException(
                            ExportException.Code.INVALID_PROJECT,
                            "Dự án có đường dẫn tương đối cho ‘" + label
                                    + "’. Hãy lưu dự án trước khi xuất video."
                    );
                }
                Path normalizedBase = projectDirectory.toAbsolutePath().normalize();
                path = normalizedBase.resolve(path).normalize();
                if (!path.startsWith(normalizedBase)) {
                    throw new ExportException(
                            ExportException.Code.INVALID_PROJECT,
                            "Đường dẫn tài nguyên của ‘" + label + "’ đi ra ngoài thư mục dự án."
                    );
                }
                if (Files.exists(path) && Files.exists(normalizedBase)) {
                    try {
                        if (!path.toRealPath().startsWith(normalizedBase.toRealPath())) {
                            throw new ExportException(
                                    ExportException.Code.INVALID_PROJECT,
                                    "Liên kết tài nguyên của ‘" + label + "’ đi ra ngoài thư mục dự án."
                            );
                        }
                    } catch (java.io.IOException e) {
                        throw new ExportException(
                                ExportException.Code.FILE_IO,
                                "Không thể kiểm tra đường dẫn tài nguyên của ‘" + label + "’.",
                                e
                        );
                    }
                }
            }
            return path.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "Đường dẫn tài nguyên của ‘" + label + "’ không hợp lệ.",
                    e
            );
        }
    }

    private static ExportException invalidScene(int zeroBasedIndex, String reason) {
        return new ExportException(
                ExportException.Code.INVALID_PROJECT,
                "Cảnh " + (zeroBasedIndex + 1) + " " + reason + "."
        );
    }

    private static String displayName(VisualLayer layer) {
        return layer.getName() == null || layer.getName().isBlank() ? layer.getId() : layer.getName();
    }
}
