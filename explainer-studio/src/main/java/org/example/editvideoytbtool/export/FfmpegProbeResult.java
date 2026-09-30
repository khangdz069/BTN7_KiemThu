package org.example.editvideoytbtool.export;

/** A non-throwing result suitable for showing directly in a settings screen. */
public record FfmpegProbeResult(
        boolean available,
        String executable,
        String version,
        String message
) {
    public static FfmpegProbeResult available(String executable, String version) {
        return new FfmpegProbeResult(
                true,
                executable,
                version == null ? "" : version,
                "FFmpeg sẵn sàng" + (version == null || version.isBlank() ? "." : ": " + version)
        );
    }

    public static FfmpegProbeResult unavailable(String executable, String message) {
        return new FfmpegProbeResult(false, executable, "", message);
    }
}
