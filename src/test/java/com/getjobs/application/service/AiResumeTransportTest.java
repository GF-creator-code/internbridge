package com.getjobs.application.service;

import com.getjobs.application.mapper.AiMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiResumeTransportTest {
    @Test void readsTextFromResponsesMessageContent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            byte[] response = """
                    {"output":[{"type":"reasoning"},{"type":"message","content":[
                    {"type":"output_text","text":"first "},{"type":"output_text","text":"second"}]}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        server.start();
        try {
            var config = mock(ConfigService.class);
            when(config.getAiConfigs()).thenReturn(Map.of("BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "API_KEY", "test-only", "MODEL", "gpt-4.1"));
            assertEquals("first second", new AiService(config, mock(AiMapper.class)).sendRequest("sample"));
        } finally { server.stop(0); }
    }
}
