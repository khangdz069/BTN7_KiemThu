package org.example.editvideoytbtool.export;

import java.time.Duration;
import java.util.Objects;

/**
 * Runtime settings for the external FFmpeg process.
 *
 * <p>The executable is deliberately kept as a string instead of a {@code Path}:
 * both a full Windows path and the command name {@code ffmpeg} are valid.  It is
 * always passed to {@link ProcessBuilder} as one argument, so paths containing
 * spaces do not need quoting.</p>
 */
public record FfmpegConfig(
        String executable,
        Duration probeTimeout,
        Duration exportTimeout
) {
    public static final String EXECUTABLE_PROPERTY = "explainer.ffmpeg.path";
    public static final String EXECUTABLE_ENVIRONMENT_VARIABLE = "FFMPEG_PATH";

    public FfmpegConfig {
        executable = requireText(executable, "Đường dẫn FFmpeg không được để trống.");
        probeTimeout = requirePositive(probeTimeout, "Thời gian chờ kiểm tra FFmpeg");
        exportTimeout = requirePositive(exportTimeout, "Thời gian chờ xuất video");
    }

    public static FfmpegConfig systemDefault() {
        String configured = trimToNull(System.getProperty(EXECUTABLE_PROPERTY));
        if (configured == null) {
            configured = trimToNull(System.getenv(EXECUTABLE_ENVIRONMENT_VARIABLE));
        }
        return new FfmpegConfig(
                configured == null ? "ffmpeg" : configured,
                Duration.ofSeconds(8),
                Duration.ofHours(2)
        );
    }

    public static FfmpegConfig forExecutable(String executable) {
        return new FfmpegConfig(executable, Duration.ofSeconds(8), Duration.ofHours(2));
    }

    public FfmpegConfig withExecutable(String newExecutable) {
        return new FfmpegConfig(newExecutable, probeTimeout, exportTimeout);
    }

    private static Duration requirePositive(Duration value, String label) {
        Objects.requireNonNull(value, label + " không được null.");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " phải lớn hơn 0.");
        }
        return value;
    }

    private static String requireText(String value, String message) {
        String result = trimToNull(value);
        if (result == null) {
            throw new IllegalArgumentException(message);
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
