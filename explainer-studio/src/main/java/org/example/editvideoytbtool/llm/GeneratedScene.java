package org.example.editvideoytbtool.llm;

import java.util.List;

public record GeneratedScene(String narration, String layout, List<String> onScreenTexts,
                             String illustrationPrompt) {
    public GeneratedScene {
        narration = narration == null ? "" : narration.trim();
        layout = layout == null ? "" : layout.trim();
        onScreenTexts = onScreenTexts == null ? List.of() : List.copyOf(onScreenTexts);
        illustrationPrompt = illustrationPrompt == null ? "" : illustrationPrompt.trim();
    }
}
