package com.smartfitagent.ai.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 所有 LLM 策略的抽象基类（模板方法 + 策略模式结合）
 *
 * 提取了所有策略共用的：
 *  - HTTP 客户端构造与请求发送
 *  - 响应体 content 字段提取
 *  - 流式 SSE 响应处理
 *  - 错误处理与重试逻辑
 *  - Token 估算
 *
 * 子类只需提供：
 *  - buildRequestBody(...) — 特定格式的请求体 JSON
 *  - name() / maxContextTokens() / defaultTemperature() / capabilities() / costPer1kTokens()
 */
public abstract class AbstractLlmStrategy implements LlmStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String url;
    protected final String apiKey;
    protected final String model;
    protected final HttpClient httpClient;

    protected AbstractLlmStrategy(String url, String apiKey, String model) {
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // ── Template method: simple completion ───────────────────────────────────

    @Override
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        String body = buildRequestBody(systemPrompt, userPrompt, defaultTemperature(), false);
        return sendAndExtract(body);
    }

    // ── Template method: multi-turn conversation ──────────────────────────────

    @Override
    public String completions(List<Map<String, String>> messages, double temperature) throws Exception {
        String body = buildMultiTurnBody(messages, temperature);
        return sendAndExtract(body);
    }

    // ── Template method: streaming ────────────────────────────────────────────

    @Override
    public void streamComplete(String systemPrompt, String userPrompt,
                               Consumer<String> callback) throws Exception {
        String body = buildRequestBody(systemPrompt, userPrompt, defaultTemperature(), true);
        HttpRequest request = buildHttpRequest(body);
        httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
                .body()
                .filter(line -> line.startsWith("data:") && !line.contains("[DONE]"))
                .map(line -> line.substring(5).trim())
                .filter(data -> !data.isBlank())
                .forEach(data -> {
                    try {
                        String chunk = extractStreamChunk(data);
                        if (chunk != null && !chunk.isBlank()) {
                            callback.accept(chunk);
                        }
                    } catch (Exception e) {
                        log.debug("跳过无法解析的 SSE chunk: {}", data);
                    }
                });
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    protected String sendAndExtract(String requestBody) throws Exception {
        HttpRequest request = buildHttpRequest(requestBody);
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.error("{} API 返回错误 HTTP {}: {}", name(), status, response.body());
            throw new IllegalStateException(name() + " API 错误: HTTP " + status
                    + " — " + truncate(response.body(), 200));
        }
        String content = extractContent(response.body());
        if (content == null || content.isBlank()) {
            log.warn("{} 返回了空内容，原始响应: {}", name(), truncate(response.body(), 300));
            return "（AI 暂时无法生成有效回复，请稍后重试）";
        }
        return content;
    }

    protected HttpRequest buildHttpRequest(String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "SmartFitAgent/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    // ── Abstract methods for subclasses ───────────────────────────────────────

    /**
     * 构建特定 API 的单轮请求体 JSON
     */
    protected abstract String buildRequestBody(String systemPrompt, String userPrompt,
                                                double temperature, boolean stream);

    /**
     * 构建多轮对话请求体 JSON
     */
    protected abstract String buildMultiTurnBody(List<Map<String, String>> messages,
                                                  double temperature);

    // ── JSON extraction utilities ─────────────────────────────────────────────

    /**
     * 从标准 OpenAI 格式的响应体中提取 content 字段
     */
    protected String extractContent(String body) {
        // 支持 choices[0].message.content 格式
        String marker = "\"content\":";
        int index = body.indexOf(marker);
        if (index < 0) {
            // fallback: 某些 API 返回 text 字段
            marker = "\"text\":";
            index = body.indexOf(marker);
        }
        if (index < 0) return null;

        int afterColon = index + marker.length();
        // skip whitespace
        while (afterColon < body.length() && Character.isWhitespace(body.charAt(afterColon))) {
            afterColon++;
        }
        if (afterColon >= body.length()) return null;

        char first = body.charAt(afterColon);
        if (first == 'n') return null; // null value

        if (first == '"') {
            return unescapeJsonString(body, afterColon + 1);
        }
        return null;
    }

    /**
     * 从流式 SSE data chunk 提取 delta.content
     */
    protected String extractStreamChunk(String jsonChunk) {
        String marker = "\"content\":";
        int index = jsonChunk.indexOf(marker);
        if (index < 0) return null;
        int afterColon = index + marker.length();
        while (afterColon < jsonChunk.length() && Character.isWhitespace(jsonChunk.charAt(afterColon))) {
            afterColon++;
        }
        if (afterColon >= jsonChunk.length()) return null;
        char first = jsonChunk.charAt(afterColon);
        if (first == '"') {
            return unescapeJsonString(jsonChunk, afterColon + 1);
        }
        return null;
    }

    protected String unescapeJsonString(String body, int start) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n'  -> sb.append('\n');
                    case 't'  -> sb.append('\t');
                    case 'r'  -> sb.append('\r');
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u'  -> {
                        if (i + 4 < body.length()) {
                            String hex = body.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('\\').append('u');
                            }
                        }
                    }
                    default   -> { sb.append('\\'); sb.append(c); }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── JSON building helpers ─────────────────────────────────────────────────

    protected String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    protected String buildMessagesArray(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, String> msg = messages.get(i);
            sb.append("{\"role\":").append(q(msg.getOrDefault("role", "user")))
              .append(",\"content\":").append(q(msg.getOrDefault("content", "")))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    protected String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && url != null && !url.isBlank();
    }
}
