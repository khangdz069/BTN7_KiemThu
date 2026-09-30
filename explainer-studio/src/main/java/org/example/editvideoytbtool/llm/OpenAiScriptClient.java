package org.example.editvideoytbtool.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class OpenAiScriptClient {
    private static final Set<String> LAYOUTS = Set.of("RIGHT_OVERLAY", "SPLIT", "CENTER");
    private final HttpClient http;
    private final ObjectMapper mapper;

    public OpenAiScriptClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), new ObjectMapper());
    }

    OpenAiScriptClient(HttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    public ScriptGenerationResult generate(String baseUrl, String model, String apiKey,
                                           ScriptGenerationRequest request)
            throws ScriptGenerationException, InterruptedException {
        if (baseUrl == null || baseUrl.isBlank()) throw new ScriptGenerationException("Chưa cấu hình base URL của mô hình ngôn ngữ.");
        if (model == null || model.isBlank()) throw new ScriptGenerationException("Chưa cấu hình tên model ngôn ngữ.");

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model.trim());
        body.put("temperature", 0.7);
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt());
        messages.addObject().put("role", "user").put("content", userPrompt(request));

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(baseUrl))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey.trim());

        final HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new ScriptGenerationException("Không kết nối được dịch vụ mô hình ngôn ngữ tại " + baseUrl + ".", e);
        } catch (IOException e) {
            throw new ScriptGenerationException("Lỗi mạng khi gọi dịch vụ mô hình ngôn ngữ: " + e.getMessage(), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new ScriptGenerationException("Dịch vụ mô hình trả HTTP " + response.statusCode() + ": " + errorMessage(response.body()));
        }
        try {
            JsonNode envelope = mapper.readTree(response.body());
            JsonNode content = envelope.at("/choices/0/message/content");
            if (!content.isTextual()) throw new ScriptGenerationException("Phản hồi không có choices[0].message.content.");
            return validateStrictJson(content.textValue());
        } catch (ScriptGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ScriptGenerationException("Không đọc được phản hồi của mô hình: " + e.getMessage(), e);
        }
    }

    public ScriptGenerationResult validateStrictJson(String json) throws ScriptGenerationException {
        if (json == null || json.isBlank()) throw new ScriptGenerationException("Mô hình trả nội dung rỗng.");
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new ScriptGenerationException("Mô hình không trả JSON thuần hợp lệ. Hãy tạo lại.");
        }
        final JsonNode root;
        try {
            root = mapper.readTree(trimmed);
        } catch (Exception e) {
            throw new ScriptGenerationException("JSON từ mô hình không hợp lệ: " + e.getMessage(), e);
        }
        JsonNode array = root.get("scenes");
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw new ScriptGenerationException("JSON phải có mảng scenes không rỗng.");
        }
        if (array.size() > 100) throw new ScriptGenerationException("JSON có quá nhiều cảnh (tối đa 100).");
        List<GeneratedScene> scenes = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            if (!item.isObject()) throw new ScriptGenerationException("Cảnh " + (i + 1) + " phải là object.");
            String narration = text(item, "narration");
            if (narration.isBlank()) throw new ScriptGenerationException("Cảnh " + (i + 1) + " thiếu narration.");
            String layout = text(item, "layout").toUpperCase(Locale.ROOT);
            if (!LAYOUTS.contains(layout)) {
                throw new ScriptGenerationException("Cảnh " + (i + 1) + " có layout không hợp lệ: " + layout);
            }
            List<String> texts = new ArrayList<>();
            JsonNode textArray = item.get("onScreenTexts");
            if (textArray != null && !textArray.isNull()) {
                if (!textArray.isArray()) throw new ScriptGenerationException("onScreenTexts của cảnh " + (i + 1) + " phải là mảng.");
                for (JsonNode node : textArray) {
                    if (!node.isTextual()) throw new ScriptGenerationException("Mỗi onScreenTexts phải là chuỗi.");
                    if (!node.textValue().isBlank()) texts.add(node.textValue().trim());
                }
            }
            scenes.add(new GeneratedScene(narration, layout, texts, text(item, "illustrationPrompt")));
        }
        return new ScriptGenerationResult(scenes);
    }

    private String errorMessage(String body) {
        try {
            JsonNode message = mapper.readTree(body).at("/error/message");
            return message.isTextual() ? message.textValue() : abbreviate(body);
        } catch (Exception ignored) {
            return abbreviate(body);
        }
    }

    private static String abbreviate(String text) {
        if (text == null) return "không có nội dung lỗi";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 500 ? flat.substring(0, 500) + "…" : flat;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue().trim() : "";
    }

    private static URI endpoint(String baseUrl) throws ScriptGenerationException {
        try {
            String clean = baseUrl.trim().replaceAll("/+$", "");
            if (!clean.endsWith("/chat/completions")) clean += "/chat/completions";
            return URI.create(clean);
        } catch (IllegalArgumentException e) {
            throw new ScriptGenerationException("Base URL mô hình ngôn ngữ không hợp lệ.", e);
        }
    }

    private static String systemPrompt() {
        return """
                Bạn là biên kịch video giải thích. Chỉ trả về một JSON object thuần, không Markdown, không lời dẫn.
                Schema bắt buộc: {"scenes":[{"narration":"...","layout":"RIGHT_OVERLAY|SPLIT|CENTER","onScreenTexts":["..."],"illustrationPrompt":"..."}]}.
                Mỗi cảnh có narration khoảng 2-3 câu cùng một ý. Chọn đúng một trong ba layout. illustrationPrompt chỉ mô tả ảnh cần người dùng bổ sung, không giả vờ đã tạo ảnh.
                """;
    }

    private static String userPrompt(ScriptGenerationRequest r) {
        return "Chủ đề: " + r.topic() + "\nĐối tượng xem: " + r.audience()
                + "\nGiọng văn: " + r.tone() + "\nThời lượng mong muốn: " + r.desiredMinutes() + " phút.";
    }
}
