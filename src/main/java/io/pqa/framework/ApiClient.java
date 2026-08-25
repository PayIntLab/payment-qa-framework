package io.pqa.framework;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/** Minimal HTTP client over the mock gateway (swap this class when wiring a real PSP SDK). */
public final class ApiClient {

    public static final class ApiException extends RuntimeException {
        public final int status;
        public final String body;

        public ApiException(int status, String body) {
            super("HTTP " + status + ": " + body);
            this.status = status;
            this.body = body;
        }
    }

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Map<String, Object> post(String path, Map<String, Object> body) {
        return send("POST", path, body);
    }

    public Map<String, Object> get(String path) {
        return send("GET", path, null);
    }

    private Map<String, Object> send(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            if ("POST".equals(method)) {
                String payload = body == null ? "{}" : mapper.writeValueAsString(body);
                builder.POST(HttpRequest.BodyPublishers.ofString(payload));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(response.statusCode(), response.body());
            }
            String text = response.body();
            if (text == null || text.isBlank()) {
                return Collections.emptyMap();
            }
            return mapper.readValue(text, Map.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("api call failed: " + method + " " + path, e);
        }
    }
}
