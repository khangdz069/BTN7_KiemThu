package org.example.editvideoytbtool.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reusable WAV preview player backed by the Java Sound API. */
public final class AudioPlaybackService implements AutoCloseable {
    private Clip activeClip;

    public synchronized void play(Path wavFile) throws AudioPlaybackException {
        Objects.requireNonNull(wavFile, "wavFile");
        if (!Files.isRegularFile(wavFile)) {
            throw new AudioPlaybackException("Không tìm thấy file WAV: " + wavFile, null);
        }

        closeActiveClip();
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile.toFile())) {
            Clip clip = AudioSystem.getClip();
            try {
                clip.open(stream);
                activeClip = clip;
                clip.setFramePosition(0);
                clip.start();
            } catch (Exception exception) {
                clip.close();
                activeClip = null;
                throw exception;
            }
        } catch (UnsupportedAudioFileException exception) {
            throw new AudioPlaybackException("File âm thanh không phải WAV hợp lệ: " + wavFile, exception);
        } catch (LineUnavailableException exception) {
            throw new AudioPlaybackException(
                    "Windows không mở được thiết bị phát âm thanh. Hãy kiểm tra loa và thiết bị đầu ra.",
                    exception);
        } catch (IOException exception) {
            throw new AudioPlaybackException("Không đọc được file WAV: " + wavFile, exception);
        }
    }

    public synchronized void stop() {
        closeActiveClip();
    }

    public synchronized boolean isPlaying() {
        return activeClip != null && activeClip.isRunning();
    }

    @Override
    public synchronized void close() {
        closeActiveClip();
    }

    private void closeActiveClip() {
        if (activeClip != null) {
            activeClip.stop();
            activeClip.close();
            activeClip = null;
        }
    }
}
