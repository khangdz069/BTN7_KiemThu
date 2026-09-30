package org.example.editvideoytbtool.export;

import java.nio.file.Path;
import java.time.Duration;

public record ExportResult(
        Path outputFile,
        double videoDurationSeconds,
        long fileSizeBytes,
        Duration elapsed
) {
    /** Concise aliases kept for controller bindings. */
    public Path output() {
        return outputFile;
    }

    public double durationSeconds() {
        return videoDurationSeconds;
    }
}
