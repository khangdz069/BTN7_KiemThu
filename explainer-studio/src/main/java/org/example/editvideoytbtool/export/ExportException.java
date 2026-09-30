package org.example.editvideoytbtool.export;

/**
 * A checked, user-facing export failure.  {@link #getMessage()} is safe to put
 * in an error dialog; {@link #getDiagnostic()} contains the technical details.
 */
public final class ExportException extends Exception {
    public enum Code {
        INVALID_PROJECT,
        FFMPEG_NOT_FOUND,
        FFMPEG_TIMED_OUT,
        ENCODING_FAILED,
        FILE_IO,
        EXPORT_CANCELLED
    }

    private final Code code;
    private final String diagnostic;

    public ExportException(Code code, String userMessage) {
        this(code, userMessage, "", null);
    }

    public ExportException(Code code, String userMessage, String diagnostic) {
        this(code, userMessage, diagnostic, null);
    }

    public ExportException(Code code, String userMessage, Throwable cause) {
        this(code, userMessage, cause == null ? "" : cause.toString(), cause);
    }

    public ExportException(Code code, String userMessage, String diagnostic, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public Code getCode() {
        return code;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
