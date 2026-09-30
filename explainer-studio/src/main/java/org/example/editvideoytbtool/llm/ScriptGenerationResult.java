package org.example.editvideoytbtool.llm;

import java.util.List;

public record ScriptGenerationResult(List<GeneratedScene> scenes) {
    public ScriptGenerationResult {
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
    }
}
