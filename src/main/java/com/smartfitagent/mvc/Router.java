package com.smartfitagent.mvc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class Router implements HttpHandler {
    private final Map<String, Handler> get = new LinkedHashMap<>();
    private final Map<String, Handler> post = new LinkedHashMap<>();
    private Handler fallback;

    public void get(String path, Handler handler) { get.put(path, handler); }
    public void post(String path, Handler handler) { post.put(path, handler); }
    public void fallback(Handler handler) { fallback = handler; }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            Request request = new Request(exchange);
            Handler handler = "POST".equalsIgnoreCase(request.method()) ? post.get(request.path()) : get.get(request.path());
            Response response = handler != null ? handler.handle(request) : (fallback == null ? Response.notFound(request.path()) : fallback.handle(request));
            for (var header : response.headers().entrySet()) {
                exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            }
            exchange.sendResponseHeaders(response.status(), response.body().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.body());
            }
        } catch (Exception ex) {
            try {
                Response response = Response.json(com.smartfitagent.Json.obj(Map.of("error", ex.getMessage() == null ? "unknown" : ex.getMessage())));
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(500, response.body().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.body());
                }
            } catch (Exception ignored) {
                exchange.close();
            }
        }
    }
}
