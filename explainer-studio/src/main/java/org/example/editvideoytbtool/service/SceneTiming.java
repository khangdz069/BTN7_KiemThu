package org.example.editvideoytbtool.service;

/** Immutable calculated placement of one scene on the project timeline. */
public record SceneTiming(
        String sceneId,
        int sceneIndex,
        double startSeconds,
        double endSeconds,
        double durationSeconds,
        boolean manualStart,
        boolean overlapsPrevious) {
}
