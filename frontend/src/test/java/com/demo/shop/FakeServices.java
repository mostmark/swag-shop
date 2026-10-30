package com.demo.shop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Stands in for the six services the frontend calls, each on a port of its own, with just enough behaviour to
 * test what the frontend does with their answers.
 */
public class FakeServices implements QuarkusTestResourceLifecycleManager {

    /** Problem switch of each scenario service, as the frontend set it. */
    static final Map<String, Boolean> PROBLEM_ENABLED = new ConcurrentHashMap<>();
    /** The last request body or header each fake got, by "service method path". */
    static final Map<String, String> LAST_REQUEST = new ConcurrentHashMap<>();
    static final Map<String, String> LOADS = new ConcurrentHashMap<>();
    /** Services that answer every request with 500, to test the demo page's statistics. */
    static final Map<String, Boolean> BROKEN = new ConcurrentHashMap<>();
    static volatile boolean trafficEnabled;

    private static final String PRODUCT = """
            {"id":"red-fedora","name":"Classic Red Fedora","category":"Headwear","description":"A fedora.",\
            "price":49.00,"image":"/img/products/red-fedora.webp","tags":["hat"]}""";

    private final List<HttpServer> servers = new ArrayList<>();

    @Override
    public Map<String, String> start() {
        reset();
        Map<String, String> config = new HashMap<>();
        for (String service : List.of("catalog", "search", "pricing", "inventory", "order", "load-generator")) {
            HttpServer server = server(service);
            config.put("quarkus.rest-client." + service + ".url", "http://localhost:" + server.getAddress().getPort());
        }
        return config;
    }

    @Override
    public void stop() {
        servers.forEach(server -> server.stop(0));
    }

    static void reset() {
        PROBLEM_ENABLED.clear();
        LAST_REQUEST.clear();
        BROKEN.clear();
        trafficEnabled = false;
        LOADS.put("search", "MEDIUM");
        LOADS.put("pricing", "MEDIUM");
        LOADS.put("checkout", "MEDIUM");
    }

    private HttpServer server(String service) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            LAST_REQUEST.put(service + " " + method + " " + path, body);
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            if (requestId != null) {
                LAST_REQUEST.put(service + " X-Request-Id", requestId);
            }
            if (BROKEN.getOrDefault(service, false)) {
                send(exchange, 500, "broken");
                return;
            }
            Answer answer = answer(service, method, path, exchange.getRequestURI().getQuery(), body);
            send(exchange, answer.status(), answer.json());
        });
        server.start();
        servers.add(server);
        return server;
    }

    private record Answer(int status, String json) {
    }

    private static Answer answer(String service, String method, String path, String query, String body) {
        if (path.equals("/api/demo/jvm")) {
            return ok("{\"service\":\"swag-shop-%s\",\"heapUsedMb\":50,\"heapMaxMb\":384,\"processCpuLoad\":0.1}"
                    .formatted(service));
        }
        String scenario = switch (service) {
            case "search" -> "search";
            case "pricing" -> "pricing";
            case "inventory" -> "checkout";
            default -> null;
        };
        if (scenario != null && path.startsWith("/api/demo/")) {
            if (method.equals("PUT") && path.equals("/api/demo/scenario")) {
                PROBLEM_ENABLED.put(scenario, body.contains("\"problemEnabled\":true"));
            }
            if (path.equals("/api/demo/reset")) {
                PROBLEM_ENABLED.put(scenario, false);
            }
            if (path.equals("/api/demo/source")) {
                return ok("{\"scenario\":\"%s\",\"problem\":{\"className\":\"Naive\"},\"fix\":{\"className\":\"Fixed\"}}"
                        .formatted(scenario));
            }
            if (path.equals("/api/demo/leak")) {
                return ok("{\"entries\":20,\"maxEntries\":1000,\"estimatedMb\":1}");
            }
            return ok("""
                    {"id":"%s","feature":"Feature %s","problemType":"Problem","symptom":"Slow","cryostatHint":"Look",\
                    "problemEnabled":%s,"defaultProblemEnabled":false}""".formatted(scenario, scenario,
                    PROBLEM_ENABLED.getOrDefault(scenario, false)));
        }
        return switch (service + " " + method + " " + path) {
            case "catalog GET /api/products" -> ok("[" + PRODUCT + "]");
            case "catalog GET /api/products/red-fedora" -> ok(PRODUCT);
            case "pricing GET /api/prices/red-fedora" -> ok("""
                    {"productId":"red-fedora","price":49.00,"lowestLast30Days":39.20,\
                    "priceHistory":[{"date":"2026-10-28","price":49.00},{"date":"2026-10-29","price":49.00}]}""");
            case "inventory GET /api/inventory/red-fedora" -> ok("{\"productId\":\"red-fedora\",\"available\":497}");
            case "search GET /api/search" -> ok("{\"query\":\"%s\",\"count\":1,\"products\":[%s]}"
                    .formatted(query.substring(2), PRODUCT));
            case "order GET /api/demo/orders" -> ok("{\"ordersPlaced\":42}");
            case "order POST /api/checkout" -> body.contains("unicorn")
                    ? new Answer(400, "Unknown product unicorn")
                    : ok("{\"orderId\":\"SW-12345678\",\"total\":49.00}");
            case "load-generator GET /api/load", "load-generator POST /api/load/reset",
                    "load-generator PUT /api/load/traffic" -> {
                if (path.endsWith("/reset")) {
                    reset();
                } else if (path.endsWith("/traffic")) {
                    trafficEnabled = body.contains("\"enabled\":true");
                }
                yield ok(loadState());
            }
            case "load-generator GET /api/load/stats" -> ok("""
                    {"trafficEnabled":true,"requests":{"search":{"requestsPerSecond":6.0,"averageMillis":3.0,\
                    "p95Millis":5.0,"total":60,"failed":0}}}""");
            default -> {
                if (path.startsWith("/api/load/scenarios/")) {
                    String id = path.substring(path.lastIndexOf('/') + 1);
                    LOADS.put(id, body.replaceAll(".*\"load\":\"([A-Z]+)\".*", "$1"));
                    yield ok(scenarioLoad(id));
                }
                yield new Answer(404, "Not found: " + path);
            }
        };
    }

    private static String loadState() {
        return "{\"trafficEnabled\":%s,\"defaultTrafficEnabled\":false,\"scenarios\":[%s,%s,%s]}".formatted(
                trafficEnabled, scenarioLoad("search"), scenarioLoad("pricing"), scenarioLoad("checkout"));
    }

    private static String scenarioLoad(String id) {
        return "{\"id\":\"%s\",\"load\":\"%s\",\"defaultLoad\":\"MEDIUM\",\"requestsPerSecond\":{\"LOW\":2,\"MEDIUM\":6,\"HIGH\":30}}"
                .formatted(id, LOADS.get(id));
    }

    private static Answer ok(String json) {
        return new Answer(200, json);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/json" : "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
