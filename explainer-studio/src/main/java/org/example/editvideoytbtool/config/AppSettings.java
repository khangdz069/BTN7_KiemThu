package org.example.editvideoytbtool.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AppSettings {
    private String ttsBaseUrl = "http://127.0.0.1:8000";
    private String ttsVoice = "Mai Anh";
    private String ffmpegPath = "ffmpeg";
    private String llmBaseUrl = "https://api.openai.com/v1";
    private String llmModel = "";
    @JsonIgnore
    private String ttsApiKey = env("VIENEU_API_KEY");
    @JsonIgnore
    private String llmApiKey = env("EXPLAINER_LLM_API_KEY");

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    public String getTtsBaseUrl() { return ttsBaseUrl; }
    public void setTtsBaseUrl(String ttsBaseUrl) { this.ttsBaseUrl = clean(ttsBaseUrl); }
    public String getTtsVoice() { return ttsVoice; }
    public void setTtsVoice(String ttsVoice) { this.ttsVoice = clean(ttsVoice); }
    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String ffmpegPath) { this.ffmpegPath = clean(ffmpegPath); }
    public String getLlmBaseUrl() { return llmBaseUrl; }
    public void setLlmBaseUrl(String llmBaseUrl) { this.llmBaseUrl = clean(llmBaseUrl); }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = clean(llmModel); }
    public String getTtsApiKey() { return ttsApiKey; }
    public void setTtsApiKey(String ttsApiKey) { this.ttsApiKey = clean(ttsApiKey); }
    public String getLlmApiKey() { return llmApiKey; }
    public void setLlmApiKey(String llmApiKey) { this.llmApiKey = clean(llmApiKey); }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
