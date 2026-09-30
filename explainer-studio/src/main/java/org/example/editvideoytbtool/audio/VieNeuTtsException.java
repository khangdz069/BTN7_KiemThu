package org.example.editvideoytbtool.audio;

import java.io.IOException;

/** User-facing failure while communicating with VieNeu-TTS. */
public final class VieNeuTtsException extends IOException {
    private final int httpStatus;

    public VieNeuTtsException(String message) {
        this(message, -1, null);
    }

    public VieNeuTtsException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public VieNeuTtsException(String message, int httpStatus) {
        this(message, httpStatus, null);
    }

    public VieNeuTtsException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** Returns {@code -1} when the server did not return an HTTP response. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
