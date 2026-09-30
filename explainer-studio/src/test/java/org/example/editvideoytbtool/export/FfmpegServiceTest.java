package org.example.editvideoytbtool.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void probeReturnsActionableMessageInsteadOfThrowingForMissingExecutable() {
        Path missing = tempDirectory.resolve("Program Files/FFmpeg không tồn tại/ffmpeg.exe");
        FfmpegService service = new FfmpegService(new FfmpegConfig(
                missing.toString(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        ));

        FfmpegProbeResult result = service.probe();

        assertFalse(result.available());
        assertTrue(result.message().contains("Không tìm thấy"), result.message());
        assertTrue(result.message().contains("PATH"), result.message());
    }
}
