package org.example.editvideoytbtool.export;

/** Receives best-effort export progress on the export worker thread. */
@FunctionalInterface
public interface ExportProgressListener {
    ExportProgressListener NONE = (progress, message) -> { };

    /** @param progress value from 0 through 1 */
    void onProgress(double progress, String message);
}
