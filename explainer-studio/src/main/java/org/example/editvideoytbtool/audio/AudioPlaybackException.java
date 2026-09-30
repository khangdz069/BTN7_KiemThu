package org.example.editvideoytbtool.audio;

/** A user-facing failure to open or play an audio file. */
public final class AudioPlaybackException extends Exception {
    public AudioPlaybackException(String message, Throwable cause) {
        super(message, cause);
    }
}
