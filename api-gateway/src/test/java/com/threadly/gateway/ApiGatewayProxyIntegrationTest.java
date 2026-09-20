package com.threadly.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayProxyIntegrationTest {

    private static HttpServer mockServer;
    private static int mockServerPort;
    private static final AtomicReference<String> receivedAuthHeader = new AtomicReference<>();
    private static final AtomicReference<String> receivedUri = new AtomicReference<>();

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUpClient() {
        this.webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @BeforeAll
    static void startMockServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServerPort = mockServer.getAddress().getPort();
        mockServer.createContext("/api/v1/auth", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                receivedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                receivedUri.set(exchange.getRequestURI().toString());
                byte[] response = "{\"status\":\"ok\"}".getBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        });
        mockServer.start();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_SERVICE_URL", () -> "http://localhost:" + mockServerPort);
    }

    @Test
    void shouldForwardAuthorizationHeaderAndQueryParamsUnchanged() {
        webTestClient.get()
            .uri("/api/v1/auth/check?sort=desc&filter=active")
            .header("Authorization", "Bearer sample-jwt-token")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok");

        assertThat(receivedAuthHeader.get()).isEqualTo("Bearer sample-jwt-token");
        assertThat(receivedUri.get()).isEqualTo("/api/v1/auth/check?sort=desc&filter=active");
    }
}
