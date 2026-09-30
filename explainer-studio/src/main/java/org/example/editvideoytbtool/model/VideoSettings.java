package org.example.editvideoytbtool.model;

/** Rendering settings stored with each project. */
public class VideoSettings {
    private int width = 1920;
    private int height = 1080;
    private int framesPerSecond = 30;
    private String backgroundColor = "#FFFFFF";
    private double defaultSceneDurationSeconds = 5.0;

    public VideoSettings() {
    }

    public int getWidth() { return width; }
    public void setWidth(int width) {
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        this.width = width;
    }
    public int getHeight() { return height; }
    public void setHeight(int height) {
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
        this.height = height;
    }
    public int getFramesPerSecond() { return framesPerSecond; }
    public void setFramesPerSecond(int framesPerSecond) {
        if (framesPerSecond <= 0 || framesPerSecond > 240) throw new IllegalArgumentException("framesPerSecond must be between 1 and 240");
        this.framesPerSecond = framesPerSecond;
    }
    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor == null || backgroundColor.isBlank() ? "#FFFFFF" : backgroundColor; }
    public double getDefaultSceneDurationSeconds() { return defaultSceneDurationSeconds; }
    public void setDefaultSceneDurationSeconds(double defaultSceneDurationSeconds) {
        if (!Double.isFinite(defaultSceneDurationSeconds) || defaultSceneDurationSeconds <= 0) throw new IllegalArgumentException("defaultSceneDurationSeconds must be positive and finite");
        this.defaultSceneDurationSeconds = defaultSceneDurationSeconds;
    }
}
