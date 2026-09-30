package org.example.editvideoytbtool.export;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builds an argument vector and a path-free filter graph for FFmpeg. */
final class FfmpegCommandBuilder {
    Command build(ExportPlan plan, List<RenderedLayer> renderedLayers, Path filterScript, Path partFile) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(renderedLayers, "renderedLayers");
        Objects.requireNonNull(filterScript, "filterScript");
        Objects.requireNonNull(partFile, "partFile");

        List<RenderedLayer> layers = renderedLayers.stream()
                .sorted(Comparator
                        .comparingInt(RenderedLayer::sceneIndex)
                        .thenComparingInt(RenderedLayer::zIndex)
                        .thenComparingInt(RenderedLayer::sourceOrder))
                .toList();

        List<String> arguments = new ArrayList<>();
        arguments.add("-y");
        arguments.add("-nostdin");
        arguments.add("-hide_banner");
        arguments.add("-f");
        arguments.add("lavfi");
        arguments.add("-i");
        arguments.add("color=c=white:s=" + plan.width() + "x" + plan.height()
                + ":r=" + plan.framesPerSecond() + ":d=" + seconds(plan.durationSeconds()));

        for (RenderedLayer layer : layers) {
            arguments.add("-loop");
            arguments.add("1");
            arguments.add("-framerate");
            arguments.add(Integer.toString(plan.framesPerSecond()));
            arguments.add("-i");
            arguments.add(layer.pngFile().toAbsolutePath().normalize().toString());
        }
        for (ExportPlan.AudioItem audio : plan.audioItems()) {
            arguments.add("-i");
            arguments.add(audio.file().toAbsolutePath().normalize().toString());
        }

        arguments.add("-filter_complex_script");
        arguments.add(filterScript.toAbsolutePath().normalize().toString());
        arguments.add("-map");
        arguments.add("[vout]");
        arguments.add("-map");
        arguments.add("[aout]");
        arguments.add("-t");
        arguments.add(seconds(plan.durationSeconds()));
        arguments.add("-r");
        arguments.add(Integer.toString(plan.framesPerSecond()));
        arguments.add("-c:v");
        arguments.add("libx264");
        arguments.add("-preset");
        arguments.add("medium");
        arguments.add("-crf");
        arguments.add("20");
        arguments.add("-pix_fmt");
        arguments.add("yuv420p");
        arguments.add("-c:a");
        arguments.add("aac");
        arguments.add("-b:a");
        arguments.add("192k");
        arguments.add("-ar");
        arguments.add("48000");
        arguments.add("-ac");
        arguments.add("2");
        arguments.add("-movflags");
        arguments.add("+faststart");
        arguments.add("-map_metadata");
        arguments.add("-1");
        // The temporary file intentionally ends in .part, so explicitly select
        // the muxer instead of relying on its extension.
        arguments.add("-f");
        arguments.add("mp4");
        arguments.add(partFile.toAbsolutePath().normalize().toString());

        return new Command(List.copyOf(arguments), buildFilterGraph(plan, layers));
    }

    private static String buildFilterGraph(ExportPlan plan, List<RenderedLayer> layers) {
        StringBuilder graph = new StringBuilder(2_048);
        graph.append("[0:v]setpts=PTS-STARTPTS,format=rgba[v0];\n");

        String previousVideo = "v0";
        for (int index = 0; index < layers.size(); index++) {
            RenderedLayer layer = layers.get(index);
            int inputIndex = index + 1;
            String layerLabel = "layer" + index;
            String outputLabel = "v" + (index + 1);
            graph.append('[').append(inputIndex).append(":v]")
                    .append("setpts=PTS-STARTPTS,format=rgba[").append(layerLabel).append("];\n");
            graph.append('[').append(previousVideo).append("][").append(layerLabel).append(']')
                    .append("overlay=x=0:y=0:eof_action=pass:shortest=0:format=auto:")
                    .append("enable='gte(t,").append(seconds(layer.startSeconds()))
                    .append(")*lt(t,").append(seconds(layer.endSeconds())).append(")'")
                    .append('[').append(outputLabel).append("];\n");
            previousVideo = outputLabel;
        }
        graph.append('[').append(previousVideo).append("]format=yuv420p[vout];\n");

        graph.append("anullsrc=r=48000:cl=stereo,atrim=duration=")
                .append(seconds(plan.durationSeconds()))
                .append(",asetpts=PTS-STARTPTS[silence];\n");

        int firstAudioInput = layers.size() + 1;
        for (int index = 0; index < plan.audioItems().size(); index++) {
            ExportPlan.AudioItem item = plan.audioItems().get(index);
            long delayMillis = Math.max(0, Math.round(item.delaySeconds() * 1_000.0));
            graph.append('[').append(firstAudioInput + index).append(":a]")
                    .append("asetpts=PTS-STARTPTS,adelay=").append(delayMillis).append(":all=1,")
                    .append("apad,atrim=duration=").append(seconds(plan.durationSeconds()))
                    .append("[audio").append(index).append("];\n");
        }

        graph.append("[silence]");
        for (int index = 0; index < plan.audioItems().size(); index++) {
            graph.append("[audio").append(index).append(']');
        }
        graph.append("amix=inputs=").append(plan.audioItems().size() + 1)
                .append(":duration=longest:dropout_transition=0:normalize=0,")
                .append("atrim=duration=").append(seconds(plan.durationSeconds()))
                .append(",aresample=48000[aout]\n");
        return graph.toString();
    }

    private static String seconds(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    record RenderedLayer(
            Path pngFile,
            double startSeconds,
            double endSeconds,
            int zIndex,
            int sceneIndex,
            int sourceOrder
    ) {
        RenderedLayer {
            Objects.requireNonNull(pngFile, "pngFile");
        }
    }

    record Command(List<String> arguments, String filterGraph) {
        Command {
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(filterGraph, "filterGraph");
        }
    }
}
