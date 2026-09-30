package org.example.editvideoytbtool.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavFileUtilTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void measuresFractionalDurationFromFrames() throws Exception {
        byte[] quarterSecond = new byte[24_000];
        Path wav = WavFileUtil.writePcm16Mono(
                temporaryDirectory.resolve("quarter-second.wav"), quarterSecond, 48_000);

        assertEquals(Duration.ofMillis(250), WavFileUtil.readDuration(wav));
        assertEquals(0.25d, WavFileUtil.readDurationSeconds(wav), 0.000_001d);
    }

    @Test
    void rejectsIncompleteSixteenBitSample() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WavFileUtil.writePcm16Mono(
                        temporaryDirectory.resolve("bad.wav"), new byte[]{1}, 48_000));

        assertTrue(error.getMessage().contains("even byte count"));
    }

    @Test
    void explainsInvalidAudioFile() throws Exception {
        Path text = temporaryDirectory.resolve("not-a-wav.txt");
        java.nio.file.Files.writeString(text, "not audio");

        IOException error = assertThrows(IOException.class,
                () -> WavFileUtil.readDuration(text));

        assertTrue(error.getMessage().contains("không phải WAV"));
    }
}
