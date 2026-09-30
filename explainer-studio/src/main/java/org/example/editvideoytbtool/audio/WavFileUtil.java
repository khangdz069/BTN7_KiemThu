package org.example.editvideoytbtool.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/** Utilities for the canonical PCM WAV files used by VieNeu-TTS v3 Turbo. */
public final class WavFileUtil {
    public static final int DEFAULT_SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;

    private WavFileUtil() {
    }

    /** Writes signed little-endian 16-bit mono PCM to a standard RIFF/WAVE file. */
    public static Path writePcm16Mono(Path destination, byte[] pcm, int sampleRate)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(pcm, "pcm");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if ((pcm.length & 1) != 0) {
            throw new IllegalArgumentException(
                    "16-bit PCM must contain a whole number of samples (an even byte count)");
        }
        long riffChunkSize = 36L + pcm.length;
        if (riffChunkSize > 0xffff_ffffL) {
            throw new IllegalArgumentException("PCM data is too large for a RIFF/WAVE file");
        }

        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (target.getFileName() == null) {
            throw new IllegalArgumentException("destination must name a WAV file");
        }
        String temporaryPrefix = target.getFileName() + ".";
        if (temporaryPrefix.length() < 3) {
            temporaryPrefix = "wav" + temporaryPrefix;
        }
        Path temporary = Files.createTempFile(parent, temporaryPrefix, ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                writeAscii(output, "RIFF");
                writeLittleEndianInt(output, (int) riffChunkSize);
                writeAscii(output, "WAVE");
                writeAscii(output, "fmt ");
                writeLittleEndianInt(output, 16);
                writeLittleEndianShort(output, 1);
                writeLittleEndianShort(output, CHANNELS);
                writeLittleEndianInt(output, sampleRate);
                writeLittleEndianInt(output, sampleRate * CHANNELS * BYTES_PER_SAMPLE);
                writeLittleEndianShort(output, CHANNELS * BYTES_PER_SAMPLE);
                writeLittleEndianShort(output, BITS_PER_SAMPLE);
                writeAscii(output, "data");
                writeLittleEndianInt(output, pcm.length);
                output.write(pcm);
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            return target;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static Path writePcm16Mono(Path destination, byte[] pcm) throws IOException {
        return writePcm16Mono(destination, pcm, DEFAULT_SAMPLE_RATE);
    }

    /** Reads duration from the file's frame count and frame rate. */
    public static Duration readDuration(Path wavFile) throws IOException {
        Objects.requireNonNull(wavFile, "wavFile");
        try (AudioInputStream input = AudioSystem.getAudioInputStream(wavFile.toFile())) {
            AudioFormat format = input.getFormat();
            long frames = input.getFrameLength();
            float frameRate = format.getFrameRate();
            if (frames < 0 || frameRate <= 0 || Float.isNaN(frameRate)) {
                throw new IOException("Không đọc được thời lượng WAV: thông tin frame không hợp lệ.");
            }
            return Duration.ofNanos(Math.round(frames / (double) frameRate * 1_000_000_000d));
        } catch (UnsupportedAudioFileException exception) {
            throw new IOException("File không phải WAV/âm thanh hợp lệ: " + wavFile, exception);
        }
    }

    public static double readDurationSeconds(Path wavFile) throws IOException {
        return readDuration(wavFile).toNanos() / 1_000_000_000d;
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        for (int index = 0; index < value.length(); index++) {
            output.write(value.charAt(index));
        }
    }

    private static void writeLittleEndianShort(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void writeLittleEndianInt(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }
}
