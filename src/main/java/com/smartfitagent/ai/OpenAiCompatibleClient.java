package com.smartfitagent.ai;

import com.smartfitagent.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpenAiCompatibleClient implements AiClient {
    private final String provider;
    private final String url;
    private final String key;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiCompatibleClient(String provider, String url, String key, String model) {
        this.provider = provider;
        this.url = url;
        this.key = key;
        this.model = model;
    }

    public String name() { return provider; }

    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String body = "{"
                + "\"model\":" + Json.q(model) + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + Json.q(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + Json.q(userPrompt) + "}"
                + "],\"temperature\":0.4}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IllegalStateException(provider + " API错误:" + response.statusCode());
        }
        return extract(response.body());
    }

    private String extract(String body) {
        String marker = "\"content\":";
        int index = body.indexOf(marker);
        if (index < 0) return body;
        int start = body.indexOf('"', index + marker.length());
        if (start < 0) return body;
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                out.append(c == 'n' ? '\n' : c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
