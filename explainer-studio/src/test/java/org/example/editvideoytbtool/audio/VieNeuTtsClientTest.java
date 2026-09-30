package org.example.editvideoytbtool.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VieNeuTtsClientTest {
    private HttpServer server;

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void callsVerifiedV3EndpointsWithBearerAndWritesPcmAsWav() throws Exception {
        AtomicReference<String> healthMethod = new AtomicReference<>();
        AtomicReference<String> voicesMethod = new AtomicReference<>();
        AtomicReference<String> speechMethod = new AtomicReference<>();
        AtomicReference<String> speechBody = new AtomicReference<>();
        AtomicReference<String> speechContentType = new AtomicReference<>();
        AtomicInteger authorizedRequestCount = new AtomicInteger();
        byte[] pcm = oneSecondOfSilence();

        server = localServer();
        server.createContext("/health", exchange -> {
            healthMethod.set(exchange.getRequestMethod());
            countAuthorization(exchange, authorizedRequestCount);
            respond(exchange, 200, "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
        });
        server.createContext("/v1/voices", exchange -> {
            voicesMethod.set(exchange.getRequestMethod());
            countAuthorization(exchange, authorizedRequestCount);
            respond(exchange, 200,
                    "{\"voices\":[\"banmai\",{\"id\":\"thuminh\",\"language\":\"vi\"}]}"
                            .getBytes(StandardCharsets.UTF_8),
                    "application/json");
        });
        server.createContext("/v1/audio/speech", exchange -> {
            speechMethod.set(exchange.getRequestMethod());
            speechContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            speechBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            countAuthorization(exchange, authorizedRequestCount);
            respond(exchange, 200, pcm, "application/octet-stream");
        });
        server.start();

        VieNeuTtsClient client = new VieNeuTtsClient(serverUri(), "local-secret");
        assertTrue(client.checkHealth().contains("ok"));
        assertEquals(List.of("banmai", "thuminh"), client.listVoiceIds());

        Path wav = client.synthesizeToWav(
                "Xin chào \"Việt Nam\".\nDòng hai.", "banmai",
                temporaryDirectory.resolve("scene-1.wav"));

        assertEquals("GET", healthMethod.get());
        assertEquals("GET", voicesMethod.get());
        assertEquals("POST", speechMethod.get());
        assertEquals(3, authorizedRequestCount.get());
        assertTrue(speechContentType.get().startsWith("application/json"));

        JsonNode request = new ObjectMapper().readTree(speechBody.get());
        assertEquals("vieneu-v3-turbo", request.path("model").asText());
        assertEquals("Xin chào \"Việt Nam\".\nDòng hai.", request.path("input").asText());
        assertEquals("banmai", request.path("voice").asText());
        assertEquals("pcm", request.path("response_format").asText());
        assertEquals(48_000, request.path("sample_rate").asInt());
        assertEquals(5, request.size());

        byte[] wavBytes = Files.readAllBytes(wav);
        assertEquals(44 + pcm.length, wavBytes.length);
        assertEquals("RIFF", new String(wavBytes, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wavBytes, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("fmt ", new String(wavBytes, 12, 4, StandardCharsets.US_ASCII));
        assertEquals("data", new String(wavBytes, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(48_000, littleEndianInt(wavBytes, 24));
        assertEquals(96_000, littleEndianInt(wavBytes, 28));
        assertEquals(1, littleEndianShort(wavBytes, 22));
        assertEquals(16, littleEndianShort(wavBytes, 34));
        assertEquals(pcm.length, littleEndianInt(wavBytes, 40));
        assertArrayEquals(pcm, Arrays.copyOfRange(wavBytes, 44, wavBytes.length));
        assertEquals(Duration.ofSeconds(1), WavFileUtil.readDuration(wav));

        try (AudioInputStream audio = AudioSystem.getAudioInputStream(wav.toFile())) {
            assertEquals(48_000f, audio.getFormat().getSampleRate());
            assertEquals(16, audio.getFormat().getSampleSizeInBits());
            assertEquals(1, audio.getFormat().getChannels());
            assertFalse(audio.getFormat().isBigEndian());
        }
    }

    @Test
    void omitsAuthorizationWhenApiKeyIsBlank() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>("not-called");
        server = localServer();
        server.createContext("/health", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "ok".getBytes(StandardCharsets.UTF_8), "text/plain");
        });
        server.start();

        new VieNeuTtsClient(serverUri(), "  ").checkHealth();

        assertNull(authorization.get());
    }

    @Test
    void reportsActionableMessagesForExpectedHttpErrors() throws Exception {
        AtomicInteger status = new AtomicInteger(400);
        server = localServer();
        server.createContext("/v1/audio/speech", exchange -> respond(exchange, status.get(),
                "{\"detail\":\"test failure\"}".getBytes(StandardCharsets.UTF_8),
                "application/json"));
        server.start();
        VieNeuTtsClient client = new VieNeuTtsClient(serverUri(), null);

        VieNeuTtsException badRequest = assertThrows(VieNeuTtsException.class,
                () -> client.synthesizePcm("Một câu.", "voice"));
        assertEquals(400, badRequest.getHttpStatus());
        assertTrue(badRequest.getMessage().contains("lời thoại, voice"));
        assertTrue(badRequest.getMessage().contains("test failure"));

        status.set(401);
        VieNeuTtsException unauthorized = assertThrows(VieNeuTtsException.class,
                () -> client.synthesizePcm("Một câu.", "voice"));
        assertEquals(401, unauthorized.getHttpStatus());
        assertTrue(unauthorized.getMessage().contains("API key"));

        status.set(429);
        VieNeuTtsException limited = assertThrows(VieNeuTtsException.class,
                () -> client.synthesizePcm("Một câu.", "voice"));
        assertEquals(429, limited.getHttpStatus());
        assertTrue(limited.getMessage().contains("chờ"));
    }

    @Test
    void reportsConnectionRefusedAsServerStartupProblem() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            unusedPort = socket.getLocalPort();
        }
        VieNeuTtsClient client = new VieNeuTtsClient(
                URI.create("http://127.0.0.1:" + unusedPort), null);

        VieNeuTtsException error = assertThrows(VieNeuTtsException.class, client::checkHealth);

        assertEquals(-1, error.getHttpStatus());
        assertTrue(error.getMessage().contains("Không kết nối được VieNeu-TTS"));
        assertTrue(error.getMessage().contains("khởi động VieNeu-TTS v3 Turbo"));
    }

    @Test
    void rejectsOddLengthPcmBeforeWritingWav() throws Exception {
        server = localServer();
        server.createContext("/v1/audio/speech", exchange ->
                respond(exchange, 200, new byte[]{1, 2, 3}, "application/octet-stream"));
        server.start();

        VieNeuTtsException error = assertThrows(VieNeuTtsException.class,
                () -> new VieNeuTtsClient(serverUri(), null)
                        .synthesizePcm("Một câu.", "voice"));

        assertTrue(error.getMessage().contains("PCM 16-bit không hợp lệ"));
    }

    private HttpServer localServer() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private URI serverUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void countAuthorization(HttpExchange exchange, AtomicInteger count) {
        if ("Bearer local-secret".equals(
                exchange.getRequestHeaders().getFirst("Authorization"))) {
            count.incrementAndGet();
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] body, String contentType)
            throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static byte[] oneSecondOfSilence() {
        return new byte[VieNeuTtsClient.SAMPLE_RATE * WavFileUtil.BYTES_PER_SAMPLE];
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }
}
