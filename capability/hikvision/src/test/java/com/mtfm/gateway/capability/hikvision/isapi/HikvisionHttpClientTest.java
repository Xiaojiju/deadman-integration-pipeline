package com.mtfm.gateway.capability.hikvision.isapi;

import com.mtfm.gateway.capability.hikvision.HikvisionAccessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HikvisionHttpClientTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private String lastPath = "";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"statusCode\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postReturnsBodyOnSuccess() throws Exception {
        status.set(200);
        String body = HikvisionHttpClient.post(url("/ISAPI/ok"), "{}", "user", "pass");
        assertEquals("{\"statusCode\":1}", body);
        assertEquals("/ISAPI/ok", lastPath);
    }

    @Test
    void fourXxThrowsAccessException() {
        status.set(400);
        HikvisionAccessException ex = assertThrows(HikvisionAccessException.class,
                () -> HikvisionHttpClient.put(url("/ISAPI/fail"), "{}", "user", "pass"));
        assertFalse(ex.retryable());
        assertTrue(ex.getMessage().contains("HTTP 400"));
    }

    @Test
    void fiveXxIsRetryable() {
        status.set(503);
        HikvisionAccessException ex = assertThrows(HikvisionAccessException.class,
                () -> HikvisionHttpClient.post(url("/ISAPI/busy"), "{}", "user", "pass"));
        assertTrue(ex.retryable());
        assertTrue(ex.getMessage().contains("HTTP 503"));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
