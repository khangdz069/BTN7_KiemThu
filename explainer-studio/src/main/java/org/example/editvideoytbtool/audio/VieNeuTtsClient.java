package org.example.editvideoytbtool.audio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP client for the endpoints exposed by VieNeu-TTS v3 Turbo. Audio is
 * requested as raw signed 16-bit little-endian mono PCM and stored as a
 * canonical 48 kHz WAV file.
 */
public final class VieNeuTtsClient {
    public static final String MODEL = "vieneu-v3-turbo";
    public static final int SAMPLE_RATE = 48_000;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_ERROR_BODY_LENGTH = 500;
    private static final List<String> VOICE_ID_FIELDS =
            List.of("id", "voice", "name", "speaker");

    private final URI baseUri;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;

    public VieNeuTtsClient(URI baseUri, String apiKey) {
        this(baseUri, apiKey,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                DEFAULT_TIMEOUT, new ObjectMapper());
    }

    public VieNeuTtsClient(String baseUrl, String apiKey) {
        this(URI.create(Objects.requireNonNull(baseUrl, "baseUrl")), apiKey);
    }

    VieNeuTtsClient(URI baseUri, String apiKey, HttpClient httpClient,
                    Duration requestTimeout, ObjectMapper objectMapper) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (baseUri.getScheme() == null || baseUri.getHost() == null) {
            throw new IllegalArgumentException("VieNeu-TTS base URL phải là URL HTTP đầy đủ.");
        }
        String scheme = baseUri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("VieNeu-TTS base URL phải dùng http hoặc https.");
        }
        if (baseUri.getQuery() != null || baseUri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "VieNeu-TTS base URL không được chứa query hoặc fragment.");
        }
        this.baseUri = normalizeBaseUri(baseUri);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public URI getBaseUri() {
        return baseUri;
    }

    /** Checks {@code GET /health}. Any non-2xx response is reported as an error. */
    public String checkHealth() throws VieNeuTtsException {
        HttpResponse<byte[]> response = send(getRequest("health"));
        ensureSuccess(response, "kiểm tra trạng thái");
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** Returns the server response from {@code GET /v1/voices} as JSON text. */
    public String fetchVoicesJson() throws VieNeuTtsException {
        HttpResponse<byte[]> response = send(getRequest("v1/voices"));
        ensureSuccess(response, "tải danh sách giọng đọc");
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /**
     * Extracts voice IDs from the common v3 response shapes: a top-level
     * array, or a {@code voices}/{@code data} array of strings or objects.
     */
    public List<String> listVoiceIds() throws VieNeuTtsException {
        String json = fetchVoicesJson();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root;
            if (root.isObject()) {
                items = root.has("voices") ? root.get("voices") : root.get("data");
            }
            if (items == null || !items.isArray()) {
                throw new JsonProcessingException("missing voices array") { };
            }
            List<String> voiceIds = new ArrayList<>();
            for (JsonNode item : items) {
                if (item.isTextual() && !item.textValue().isBlank()) {
                    voiceIds.add(item.textValue());
                    continue;
                }
                if (item.isObject()) {
                    for (String field : VOICE_ID_FIELDS) {
                        JsonNode candidate = item.get(field);
                        if (candidate != null && candidate.isTextual()
                                && !candidate.textValue().isBlank()) {
                            voiceIds.add(candidate.textValue());
                            break;
                        }
                    }
                }
            }
            return List.copyOf(voiceIds);
        } catch (JsonProcessingException exception) {
            throw new VieNeuTtsException(
                    "VieNeu-TTS trả về danh sách voice không đúng định dạng JSON.", exception);
        }
    }

    /** Requests raw s16le mono PCM from {@code POST /v1/audio/speech}. */
    public byte[] synthesizePcm(String input, String voice) throws VieNeuTtsException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Lời thoại không được để trống.");
        }
        if (voice == null || voice.isBlank()) {
            throw new IllegalArgumentException("Tên voice không được để trống.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", MODEL);
        payload.put("input", input);
        payload.put("voice", voice);
        payload.put("response_format", "pcm");
        payload.put("sample_rate", SAMPLE_RATE);

        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new VieNeuTtsException("Không thể mã hóa yêu cầu VieNeu-TTS.", exception);
        }
        HttpRequest request = requestBuilder("v1/audio/speech")
                .header("Content-Type", "application/json")
                .header("Accept", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response = send(request);
        ensureSuccess(response, "tạo giọng đọc");
        byte[] pcm = response.body();
        if (pcm.length == 0) {
            throw new VieNeuTtsException(
                    "VieNeu-TTS trả về âm thanh rỗng. Hãy kiểm tra log của VieNeu-TTS.");
        }
        if ((pcm.length & 1) != 0) {
            throw new VieNeuTtsException(
                    "VieNeu-TTS trả về PCM 16-bit không hợp lệ (số byte lẻ).");
        }
        return pcm;
    }

    /** Synthesizes one scene and writes its PCM response as a 48 kHz WAV. */
    public Path synthesizeToWav(String input, String voice, Path destination)
            throws VieNeuTtsException {
        byte[] pcm = synthesizePcm(input, voice);
        try {
            return WavFileUtil.writePcm16Mono(destination, pcm, SAMPLE_RATE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new VieNeuTtsException("Không thể lưu file WAV: " + destination, exception);
        }
    }

    private HttpRequest getRequest(String relativePath) {
        return requestBuilder(relativePath).GET().build();
    }

    private HttpRequest.Builder requestBuilder(String relativePath) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(relativePath))
                .timeout(requestTimeout);
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws VieNeuTtsException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VieNeuTtsException("Yêu cầu tới VieNeu-TTS đã bị hủy.", exception);
        } catch (IOException exception) {
            if (hasCause(exception, ConnectException.class)
                    || hasCause(exception, HttpConnectTimeoutException.class)) {
                throw new VieNeuTtsException(
                        "Không kết nối được VieNeu-TTS tại " + baseUri
                                + ". Hãy khởi động VieNeu-TTS v3 Turbo và kiểm tra base URL.",
                        exception);
            }
            throw new VieNeuTtsException(
                    "Không thể gọi VieNeu-TTS tại " + baseUri + ": " + safeMessage(exception),
                    exception);
        }
    }

    private static void ensureSuccess(HttpResponse<byte[]> response, String operation)
            throws VieNeuTtsException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String suffix = responseDetail(response.body());
        String message = switch (status) {
            case 400 -> "VieNeu-TTS từ chối yêu cầu (HTTP 400). "
                    + "Hãy kiểm tra lời thoại, voice và tham số.";
            case 401, 403 -> "VieNeu-TTS không chấp nhận API key (HTTP " + status
                    + "). Hãy kiểm tra cấu hình xác thực.";
            case 404 -> "VieNeu-TTS không có endpoint cần thiết (HTTP 404). "
                    + "Hãy chắc chắn đang chạy đúng VieNeu-TTS v3 Turbo.";
            case 429 -> "VieNeu-TTS đang giới hạn số yêu cầu (HTTP 429). "
                    + "Hãy chờ một lúc rồi thử lại.";
            default -> status >= 500
                    ? "VieNeu-TTS gặp lỗi máy chủ (HTTP " + status + ") khi " + operation + "."
                    : "Không thể " + operation + " bằng VieNeu-TTS (HTTP " + status + ").";
        };
        throw new VieNeuTtsException(message + suffix, status);
    }

    private static String responseDetail(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String detail = new String(body, StandardCharsets.UTF_8)
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        if (detail.isEmpty()) {
            return "";
        }
        if (detail.length() > MAX_ERROR_BODY_LENGTH) {
            detail = detail.substring(0, MAX_ERROR_BODY_LENGTH) + "…";
        }
        return " Chi tiết: " + detail;
    }

    private static URI normalizeBaseUri(URI uri) {
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }
}
