package org.example.editvideoytbtool.service;

import java.util.List;

public record TimelineResult(
        List<SceneTiming> scenes,
        double totalDurationSeconds,
        List<String> warnings) {

    public TimelineResult {
        scenes = List.copyOf(scenes);
        warnings = List.copyOf(warnings);
    }
}
