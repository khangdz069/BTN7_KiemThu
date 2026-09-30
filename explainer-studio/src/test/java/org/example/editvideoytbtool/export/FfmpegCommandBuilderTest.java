package org.example.editvideoytbtool.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegCommandBuilderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void buildsPathSafeH264AacCommandAndAbsoluteTimingGraph() {
        Path png = tempDirectory.resolve("ảnh nhân vật có khoảng trắng.png");
        Path wav = tempDirectory.resolve("giọng đọc cảnh một.wav");
        ExportPlan plan = new ExportPlan(
                1920,
                1080,
                30,
                4.25,
                List.of(),
                List.of(new ExportPlan.AudioItem("scene-1", wav, 1.125))
        );
        FfmpegCommandBuilder.RenderedLayer layer = new FfmpegCommandBuilder.RenderedLayer(
                png, 1.25, 3.75, 4, 0, 0
        );
        Path script = tempDirectory.resolve("filter complex.txt");
        Path part = tempDirectory.resolve("kết quả.mp4.part");

        FfmpegCommandBuilder.Command command = new FfmpegCommandBuilder()
                .build(plan, List.of(layer), script, part);

        // A ProcessBuilder argument vector keeps paths with spaces as one item;
        // no shell quoting is added or needed.
        assertTrue(command.arguments().contains(png.toAbsolutePath().normalize().toString()));
        assertTrue(command.arguments().contains(wav.toAbsolutePath().normalize().toString()));
        assertEquals(part.toAbsolutePath().normalize().toString(), command.arguments().getLast());
        assertOption(command.arguments(), "-c:v", "libx264");
        assertOption(command.arguments(), "-c:a", "aac");
        assertOption(command.arguments(), "-pix_fmt", "yuv420p");
        assertOption(command.arguments(), "-movflags", "+faststart");
        assertOption(command.arguments(), "-f", "mp4");

        assertTrue(command.filterGraph().contains("gte(t,1.250000)*lt(t,3.750000)"));
        assertTrue(command.filterGraph().contains("adelay=1125:all=1"));
        assertTrue(command.filterGraph().contains("anullsrc=r=48000:cl=stereo"));
    }

    private static void assertOption(List<String> arguments, String option, String value) {
        for (int index = 0; index < arguments.size() - 1; index++) {
            if (arguments.get(index).equals(option) && arguments.get(index + 1).equals(value)) {
                return;
            }
        }
        throw new AssertionError("Missing command option: " + option + " " + value + " in " + arguments);
    }
}
