package org.example.editvideoytbtool.llm;

public record ScriptGenerationRequest(String topic, String audience, String tone, int desiredMinutes) {
    public ScriptGenerationRequest {
        topic = topic == null ? "" : topic.trim();
        audience = audience == null ? "" : audience.trim();
        tone = tone == null ? "" : tone.trim();
        if (topic.isBlank()) throw new IllegalArgumentException("Chủ đề không được để trống.");
        if (desiredMinutes < 1 || desiredMinutes > 120) {
            throw new IllegalArgumentException("Thời lượng mong muốn phải từ 1 đến 120 phút.");
        }
    }
}
