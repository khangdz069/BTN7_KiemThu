package org.example.editvideoytbtool.export;

import org.example.editvideoytbtool.model.ProjectDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Rasterizes logical layers and renders a real H.264/AAC MP4 using FFmpeg.
 * Call this service from a worker thread, not the JavaFX application thread.
 */
public final class VideoExporter {
    private final FfmpegService ffmpeg;
    private final LayerRasterizer rasterizer;
    private final FfmpegCommandBuilder commandBuilder;
    private final ProjectExportPlanner planner;

    public VideoExporter(FfmpegService ffmpeg) {
        this(ffmpeg, new LayerRasterizer(), new FfmpegCommandBuilder(), new ProjectExportPlanner());
    }

    public VideoExporter(FfmpegConfig config) {
        this(new FfmpegService(config));
    }

    public static VideoExporter systemDefault() {
        return new VideoExporter(FfmpegService.systemDefault());
    }

    VideoExporter(
            FfmpegService ffmpeg,
            LayerRasterizer rasterizer,
            FfmpegCommandBuilder commandBuilder,
            ProjectExportPlanner planner
    ) {
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
        this.rasterizer = Objects.requireNonNull(rasterizer, "rasterizer");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public FfmpegProbeResult probeFfmpeg() {
        return ffmpeg.probe();
    }

    public ExportResult export(ProjectDocument project, Path destination) throws ExportException {
        return export(project, null, destination, ExportProgressListener.NONE);
    }

    public ExportResult export(
            ProjectDocument project,
            Path destination,
            ExportProgressListener listener
    ) throws ExportException {
        return export(project, null, destination, listener);
    }

    /**
     * @param projectDirectory base directory used for portable relative asset
     *                         paths; pass {@code null} to use the project's file
     *                         location
     */
    public ExportResult export(
            ProjectDocument project,
            Path projectDirectory,
            Path destination,
            ExportProgressListener listener
    ) throws ExportException {
        ExportPlan plan = planner.create(project, projectDirectory);
        return exportPlan(plan, destination, listener);
    }

    ExportResult exportPlan(ExportPlan plan, Path destination, ExportProgressListener listener)
            throws ExportException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(destination, "destination");
        listener = listener == null ? ExportProgressListener.NONE : listener;
        Instant started = Instant.now();

        FfmpegProbeResult probe = ffmpeg.probe();
        if (!probe.available()) {
            throw new ExportException(
                    ExportException.Code.FFMPEG_NOT_FOUND,
                    probe.message(),
                    "Executable: " + probe.executable()
            );
        }
        publish(listener, 0.02, "FFmpeg đã sẵn sàng.");

        Path output = destination.toAbsolutePath().normalize();
        Path outputDirectory = output.getParent();
        if (outputDirectory == null) {
            throw new ExportException(ExportException.Code.FILE_IO, "Không xác định được thư mục xuất video.");
        }
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new ExportException(
                    ExportException.Code.FILE_IO,
                    "Không thể tạo thư mục xuất video ‘" + outputDirectory + "’.",
                    e
            );
        }
        if (Files.isDirectory(output)) {
            throw new ExportException(ExportException.Code.FILE_IO, "Đường dẫn xuất đang trỏ tới một thư mục.");
        }

        String baseName = output.getFileName().toString();
        Path partFile = output.resolveSibling(baseName + ".part");
        Path workspace;
        try {
            workspace = Files.createTempDirectory(outputDirectory, ".explainer-render-");
        } catch (IOException e) {
            throw new ExportException(
                    ExportException.Code.FILE_IO,
                    "Không thể tạo thư mục tạm cạnh file xuất.",
                    e
            );
        }

        boolean committed = false;
        try {
            List<FfmpegCommandBuilder.RenderedLayer> rendered = renderLayers(plan, workspace, listener);
            Path filterScript = workspace.resolve("filter-complex.txt");
            FfmpegCommandBuilder.Command command = commandBuilder.build(plan, rendered, filterScript, partFile);
            Files.writeString(filterScript, command.filterGraph(), StandardCharsets.UTF_8);

            publish(listener, 0.30, "Đang mã hóa video H.264 và âm thanh AAC…");
            ffmpeg.execute(command.arguments());
            if (!Files.isRegularFile(partFile) || Files.size(partFile) == 0) {
                throw new ExportException(
                        ExportException.Code.ENCODING_FAILED,
                        "FFmpeg kết thúc nhưng không tạo được file MP4 hợp lệ."
                );
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new ExportException(ExportException.Code.EXPORT_CANCELLED, "Đã hủy xuất video.");
            }

            publish(listener, 0.96, "Đang hoàn tất file MP4…");
            replaceAtomically(partFile, output);
            committed = true;
            long size = Files.size(output);
            publish(listener, 1.0, "Đã xuất video: " + output.getFileName());
            return new ExportResult(output, plan.durationSeconds(), size, Duration.between(started, Instant.now()));
        } catch (ExportException e) {
            throw e;
        } catch (IOException e) {
            throw new ExportException(
                    ExportException.Code.FILE_IO,
                    "Có lỗi khi ghi file video. File đích cũ (nếu có) vẫn được giữ nguyên.",
                    e
            );
        } finally {
            if (!committed) {
                deleteQuietly(partFile);
            }
            deleteTreeQuietly(workspace);
        }
    }

    private List<FfmpegCommandBuilder.RenderedLayer> renderLayers(
            ExportPlan plan,
            Path workspace,
            ExportProgressListener listener
    ) throws ExportException {
        List<ExportPlan.LayerItem> orderedLayers = new ArrayList<>(plan.layers());
        orderedLayers.sort(Comparator
                .comparingInt(ExportPlan.LayerItem::sceneIndex)
                .thenComparingInt(ExportPlan.LayerItem::zIndex));

        List<FfmpegCommandBuilder.RenderedLayer> rendered = new ArrayList<>(orderedLayers.size());
        for (int index = 0; index < orderedLayers.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ExportException(ExportException.Code.EXPORT_CANCELLED, "Đã hủy xuất video.");
            }
            ExportPlan.LayerItem layer = orderedLayers.get(index);
            Path png = workspace.resolve(String.format("layer-%05d.png", index));
            rasterizer.rasterize(layer, plan.width(), plan.height(), png);
            rendered.add(new FfmpegCommandBuilder.RenderedLayer(
                    png,
                    layer.startSeconds(),
                    layer.endSeconds(),
                    layer.zIndex(),
                    layer.sceneIndex(),
                    index
            ));
            double fraction = orderedLayers.isEmpty() ? 1 : (index + 1.0) / orderedLayers.size();
            publish(listener, 0.03 + fraction * 0.24, "Đang chuẩn bị lớp hình " + (index + 1)
                    + "/" + orderedLayers.size() + "…");
        }
        return rendered;
    }

    private static void replaceAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void publish(ExportProgressListener listener, double progress, String message) {
        listener.onProgress(Math.max(0, Math.min(1, progress)), message);
    }

    private static void deleteTreeQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(VideoExporter::deleteQuietly);
        } catch (IOException ignored) {
            // Temporary render files can be cleaned by the OS/user later; never
            // replace a successful export result with a cleanup error.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // See deleteTreeQuietly.
        }
    }
}
