package org.example.editvideoytbtool.service;

import org.example.editvideoytbtool.model.LayoutPreset;
import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Deterministic script parsing and scene editing; this is intentionally not AI. */
public class ScriptSceneService {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final Pattern NUMBERED_SCENE_MARKER = Pattern.compile(
            "(?iu)^#*\\s*(?:cảnh|scene)\\s*(?:\\d+|[ivxlcdm]+)\\s*:?[ \\t]*$");
    private static final Pattern BRACKET_SCENE_MARKER = Pattern.compile(
            "(?iu)^\\[\\s*(?:cảnh|scene)(?:\\s+[^]]*)?]$");

    private final TimelineService timelineService;

    public ScriptSceneService() {
        this(new TimelineService());
    }

    public ScriptSceneService(TimelineService timelineService) {
        this.timelineService = Objects.requireNonNull(timelineService, "timelineService");
    }

    /**
     * Honors explicit marker lines (---, ===, ***, |||, [Cảnh], Cảnh 1).
     * Without markers, groups sentence-by-sentence into deterministic 2–3
     * sentence scenes. This fallback does not claim to infer meaning.
     */
    public List<SceneData> parseScript(String script) {
        String normalized = normalizeScript(script);
        if (normalized.isBlank()) return List.of();

        List<String> chunks = hasExplicitSceneDelimiters(normalized)
                ? splitExplicitChunks(normalized)
                : groupSentences(normalized);
        List<SceneData> scenes = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            SceneData scene = new SceneData(chunks.get(i));
            scene.setLayoutPreset(defaultLayoutFor(i));
            scenes.add(scene);
        }
        return scenes;
    }

    public void replaceProjectScenes(ProjectDocument project, String script) {
        Objects.requireNonNull(project, "project");
        project.setScenes(parseScript(script));
        timelineService.recalculate(project);
        project.touch();
    }

    public boolean hasExplicitSceneDelimiters(String script) {
        if (script == null) return false;
        return script.lines().anyMatch(this::isDelimiterLine);
    }

    public SceneData addScene(ProjectDocument project, int index, String narration) {
        Objects.requireNonNull(project, "project");
        if (index < 0 || index > project.getScenes().size()) throw new IndexOutOfBoundsException("Scene index: " + index);
        SceneData scene = new SceneData(narration);
        scene.setLayoutPreset(defaultLayoutFor(index));
        project.getScenes().add(index, scene);
        afterStructuralEdit(project);
        return scene;
    }

    /** Splits text at the editor caret while cloning visual edits with new layer IDs. */
    public SceneData splitScene(ProjectDocument project, String sceneId, int characterOffset) {
        Objects.requireNonNull(project, "project");
        int index = requiredSceneIndex(project, sceneId);
        SceneData original = project.getScenes().get(index);
        String narration = original.getNarration();
        if (characterOffset <= 0 || characterOffset >= narration.length()) {
            throw new IllegalArgumentException("Split position must leave text on both sides");
        }
        String firstText = narration.substring(0, characterOffset).strip();
        String secondText = narration.substring(characterOffset).strip();
        if (firstText.isBlank() || secondText.isBlank()) {
            throw new IllegalArgumentException("Split position must leave text on both sides");
        }

        double originalPause = original.getPauseAfterSeconds();
        original.setNarration(firstText);
        original.setPauseAfterSeconds(0.4);

        SceneData second = new SceneData(secondText);
        second.setLayoutPreset(original.getLayoutPreset());
        second.setPauseAfterSeconds(originalPause);
        second.setLayers(original.getLayers().stream().map(VisualLayer::duplicate).toList());
        project.getScenes().add(index + 1, second);
        afterStructuralEdit(project);
        return second;
    }

    /** Merges a scene with its immediate successor and keeps both sets of layers. */
    public SceneData mergeWithNext(ProjectDocument project, String firstSceneId) {
        Objects.requireNonNull(project, "project");
        int index = requiredSceneIndex(project, firstSceneId);
        if (index + 1 >= project.getScenes().size()) throw new IllegalArgumentException("The last scene has no next scene to merge");

        SceneData first = project.getScenes().get(index);
        SceneData second = project.getScenes().get(index + 1);
        double visualOffset = timelineService.effectiveDuration(first, project.getSettings().getDefaultSceneDurationSeconds());

        for (VisualLayer layer : first.getLayers()) {
            if (layer.getEndOffsetSeconds() == null) layer.setEndOffsetSeconds(visualOffset);
        }
        for (VisualLayer layer : second.getLayers()) {
            layer.setStartOffsetSeconds(layer.getStartOffsetSeconds() + visualOffset);
            if (layer.getEndOffsetSeconds() != null) layer.setEndOffsetSeconds(layer.getEndOffsetSeconds() + visualOffset);
            first.getLayers().add(layer);
        }

        String combined = (first.getNarration() + " " + second.getNarration()).strip();
        first.setNarration(combined);
        first.clearAudio();
        first.setPauseAfterSeconds(second.getPauseAfterSeconds());
        if (first.getLayoutPreset() != second.getLayoutPreset() || !second.getLayers().isEmpty()) {
            first.setLayoutPreset(LayoutPreset.CUSTOM);
        }
        project.getScenes().remove(index + 1);
        afterStructuralEdit(project);
        return first;
    }

    /** Reordering never invalidates scene WAVs because scene IDs and contents stay unchanged. */
    public void moveScene(ProjectDocument project, int fromIndex, int toIndex) {
        Objects.requireNonNull(project, "project");
        int size = project.getScenes().size();
        if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size) {
            throw new IndexOutOfBoundsException("Scene move " + fromIndex + " -> " + toIndex);
        }
        if (fromIndex == toIndex) return;
        SceneData moved = project.getScenes().remove(fromIndex);
        project.getScenes().add(toIndex, moved);
        afterStructuralEdit(project);
    }

    public void moveScene(ProjectDocument project, String sceneId, int toIndex) {
        moveScene(project, requiredSceneIndex(project, sceneId), toIndex);
    }

    public SceneData removeScene(ProjectDocument project, String sceneId) {
        Objects.requireNonNull(project, "project");
        SceneData removed = project.getScenes().remove(requiredSceneIndex(project, sceneId));
        afterStructuralEdit(project);
        return removed;
    }

    public void updateNarration(ProjectDocument project, String sceneId, String narration) {
        Objects.requireNonNull(project, "project");
        SceneData scene = project.findScene(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scene id: " + sceneId));
        scene.updateNarration(narration);
        timelineService.recalculate(project);
        project.touch();
    }

    private List<String> splitExplicitChunks(String script) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R", -1)) {
            if (isDelimiterLine(line)) {
                addChunk(chunks, current);
            } else {
                if (!current.isEmpty()) current.append('\n');
                current.append(line);
            }
        }
        addChunk(chunks, current);
        return chunks;
    }

    private List<String> groupSentences(String script) {
        List<String> sentences = extractSentences(script);
        if (sentences.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < sentences.size();) {
            int remaining = sentences.size() - index;
            int groupSize = remaining <= 3 ? remaining : remaining == 4 ? 2 : 3;
            chunks.add(String.join(" ", sentences.subList(index, index + groupSize)));
            index += groupSize;
        }
        return chunks;
    }

    private List<String> extractSentences(String script) {
        String singleSpaced = script.replaceAll("\\s+", " ").strip();
        BreakIterator iterator = BreakIterator.getSentenceInstance(VIETNAMESE);
        iterator.setText(singleSpaced);
        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = singleSpaced.substring(start, end).strip();
            if (!sentence.isBlank()) sentences.add(sentence);
        }
        if (sentences.isEmpty() && !singleSpaced.isBlank()) sentences.add(singleSpaced);
        return sentences;
    }

    private boolean isDelimiterLine(String line) {
        String trimmed = line == null ? "" : line.strip();
        return trimmed.matches("-{3,}|={3,}|\\*{3,}|\\|{3,}")
                || NUMBERED_SCENE_MARKER.matcher(trimmed).matches()
                || BRACKET_SCENE_MARKER.matcher(trimmed).matches();
    }

    private void addChunk(List<String> chunks, StringBuilder current) {
        String chunk = current.toString().replaceAll("[ \\t]+", " ").strip();
        if (!chunk.isBlank()) chunks.add(chunk);
        current.setLength(0);
    }

    private int requiredSceneIndex(ProjectDocument project, String sceneId) {
        int index = project.indexOfScene(sceneId);
        if (index < 0) throw new IllegalArgumentException("Unknown scene id: " + sceneId);
        return index;
    }

    private void afterStructuralEdit(ProjectDocument project) {
        timelineService.recalculate(project);
        project.touch();
    }

    private static String normalizeScript(String script) {
        return script == null ? "" : script.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static LayoutPreset defaultLayoutFor(int index) {
        return switch (Math.floorMod(index, 3)) {
            case 0 -> LayoutPreset.CHARACTER_RIGHT_OVERLAP_IMAGE;
            case 1 -> LayoutPreset.CHARACTER_RIGHT_IMAGE_LEFT;
            default -> LayoutPreset.CHARACTER_CENTER_TEXT_AROUND;
        };
    }
}
