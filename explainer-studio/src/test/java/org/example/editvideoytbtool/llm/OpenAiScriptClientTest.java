package org.example.editvideoytbtool.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiScriptClientTest {
    private HttpServer server;

    @AfterEach void stop() {
        if (server != null) server.stop(0);
    }

    @Test void callsChatCompletionsAndValidatesScenes() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = """
                    {"choices":[{"message":{"content":"{\\"scenes\\":[{\\"narration\\":\\"Câu một. Câu hai.\\",\\"layout\\":\\"SPLIT\\",\\"onScreenTexts\\":[\\"Ý chính\\"],\\"illustrationPrompt\\":\\"Một biểu đồ\\"}]}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        ScriptGenerationResult result = new OpenAiScriptClient().generate(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-model", "secret",
                new ScriptGenerationRequest("Mặt trời", "Học sinh", "Dễ hiểu", 2));

        assertEquals(1, result.scenes().size());
        assertEquals("SPLIT", result.scenes().getFirst().layout());
        assertEquals("Bearer secret", auth.get());
        assertTrue(body.get().contains("test-model"));
        assertTrue(body.get().contains("json_object"));
    }

    @Test void rejectsMarkdownAndUnknownLayout() {
        OpenAiScriptClient client = new OpenAiScriptClient();
        assertThrows(ScriptGenerationException.class,
                () -> client.validateStrictJson("```json\n{\"scenes\":[]}\n```"));
        ScriptGenerationException error = assertThrows(ScriptGenerationException.class,
                () -> client.validateStrictJson("{\"scenes\":[{\"narration\":\"Đủ lời.\",\"layout\":\"MAGIC\"}]}"));
        assertTrue(error.getMessage().contains("layout"));
    }
}
