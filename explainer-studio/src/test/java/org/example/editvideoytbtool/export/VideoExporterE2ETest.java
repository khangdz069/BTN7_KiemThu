package org.example.editvideoytbtool.export;

import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real FFmpeg smoke test. Enable with -Dexplainer.ffmpeg.e2e=true or
 * RUN_FFMPEG_E2E=true; it skips cleanly on machines without FFmpeg.
 */
class VideoExporterE2ETest {
    @TempDir
    Path tempDirectory;

    @Test
    void exportsTwoScenesWithDistinctCharactersTimedLayersAndSynchronizedAudio() throws Exception {
        boolean optedIn = Boolean.getBoolean("explainer.ffmpeg.e2e")
                || "true".equalsIgnoreCase(System.getenv("RUN_FFMPEG_E2E"));
        Assumptions.assumeTrue(optedIn, "Real FFmpeg E2E test is opt-in.");

        FfmpegConfig config = new FfmpegConfig(
                System.getProperty("explainer.ffmpeg.path", "ffmpeg"),
                Duration.ofSeconds(8),
                Duration.ofMinutes(2)
        );
        FfmpegService ffmpeg = new FfmpegService(config);
        Assumptions.assumeTrue(ffmpeg.probe().available(), "FFmpeg is unavailable.");

        Files.createDirectories(tempDirectory.resolve("assets"));
        Files.createDirectories(tempDirectory.resolve("audio"));
        makePng(tempDirectory.resolve("assets/character-normal.png"), new Color(235, 75, 80), true);
        makePng(tempDirectory.resolve("assets/character-surprised.png"), new Color(65, 125, 235), false);
        makePng(tempDirectory.resolve("assets/illustration.png"), new Color(80, 185, 115), false);
        makeWav(tempDirectory.resolve("audio/scene-1.wav"), 0.45, 440);
        makeWav(tempDirectory.resolve("audio/scene-2.wav"), 0.55, 660);

        ProjectDocument project = new ProjectDocument();
        project.setProjectFile(tempDirectory.resolve("e2e.evproj"));

        SceneData first = new SceneData("Đây là cảnh thứ nhất.");
        first.markAudioGenerated("audio/scene-1.wav", 0.45);
        first.setPauseAfterSeconds(0.15);
        VisualLayer normal = VisualLayer.character("assets/character-normal.png");
        normal.setX(1320);
        normal.setY(230);
        normal.setWidth(480);
        normal.setHeight(760);
        normal.setZIndex(5);
        VisualLayer illustration = VisualLayer.image("assets/illustration.png");
        illustration.setX(120);
        illustration.setY(180);
        illustration.setWidth(900);
        illustration.setHeight(650);
        illustration.setVisibilityWindow(0.10, 0.38);
        VisualLayer title = VisualLayer.text("CẢNH MỘT");
        title.setX(120);
        title.setY(50);
        title.setWidth(900);
        title.setHeight(100);
        title.setTextColor("#173B74");
        title.setVisibilityWindow(0.05, 0.40);
        first.getLayers().addAll(List.of(illustration, normal, title));

        SceneData second = new SceneData("Đây là cảnh thứ hai.");
        second.markAudioGenerated("audio/scene-2.wav", 0.55);
        VisualLayer surprised = VisualLayer.character("assets/character-surprised.png");
        surprised.setX(660);
        surprised.setY(220);
        surprised.setWidth(600);
        surprised.setHeight(800);
        second.getLayers().add(surprised);
        project.getScenes().addAll(List.of(first, second));

        Path output = tempDirectory.resolve("video hoàn chỉnh.mp4");
        VideoExporter exporter = new VideoExporter(ffmpeg);
        ExportResult result = exporter.export(project, output);

        assertEquals(1.15, result.videoDurationSeconds(), 0.000_001);
        assertTrue(Files.size(output) > 10_000);
        assertFalse(Files.exists(output.resolveSibling(output.getFileName() + ".part")));

        FfmpegService.ProcessResult verification = ffmpeg.execute(List.of(
                "-hide_banner",
                "-i", output.toString(),
                "-af", "volumedetect",
                "-f", "null",
                "-"
        ));
        String diagnostic = verification.output().toLowerCase(Locale.ROOT);
        assertTrue(diagnostic.contains("video: h264"), diagnostic);
        assertTrue(diagnostic.contains("audio: aac"), diagnostic);
        assertTrue(diagnostic.contains("mean_volume:"), diagnostic);
        assertFalse(diagnostic.contains("mean_volume: -inf"), diagnostic);
        assertEquals(1.15, readContainerDurationSeconds(diagnostic), 0.04, diagnostic);
    }

    private static void makePng(Path target, Color color, boolean circle) throws Exception {
        BufferedImage image = new BufferedImage(360, 520, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            if (circle) {
                graphics.fillOval(20, 20, 320, 480);
            } else {
                graphics.fillRoundRect(20, 20, 320, 480, 70, 70);
            }
            graphics.setColor(Color.WHITE);
            graphics.fillOval(100, 150, 42, 42);
            graphics.fillOval(220, 150, 42, 42);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static void makeWav(Path target, double durationSeconds, double frequency) throws Exception {
        float sampleRate = 48_000;
        int frames = (int) Math.round(durationSeconds * sampleRate);
        byte[] pcm = new byte[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            double envelope = Math.min(1, Math.min(frame / 800.0, (frames - frame) / 800.0));
            short sample = (short) Math.round(Math.sin(2 * Math.PI * frequency * frame / sampleRate)
                    * 7_500 * Math.max(0, envelope));
            pcm[frame * 2] = (byte) (sample & 0xff);
            pcm[frame * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm),
                format,
                frames
        )) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, target.toFile());
        }
    }

    private static double readContainerDurationSeconds(String diagnostic) {
        Matcher matcher = Pattern.compile("duration: (\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})")
                .matcher(diagnostic);
        if (!matcher.find()) {
            throw new AssertionError("FFmpeg did not report container duration:\n" + diagnostic);
        }
        return Integer.parseInt(matcher.group(1)) * 3_600
                + Integer.parseInt(matcher.group(2)) * 60
                + Double.parseDouble(matcher.group(3));
    }
}
