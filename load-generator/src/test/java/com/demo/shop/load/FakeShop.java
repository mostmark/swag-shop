package com.demo.shop.load;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Stands in for the frontend: answers every request with an empty JSON object and counts them by path. */
public class FakeShop implements QuarkusTestResourceLifecycleManager {

    static final Map<String, AtomicInteger> REQUESTS = new ConcurrentHashMap<>();

    private HttpServer server;

    static int requests(String pathPrefix) {
        return REQUESTS.entrySet().stream().filter(e -> e.getKey().startsWith(pathPrefix))
                .mapToInt(e -> e.getValue().get()).sum();
    }

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            REQUESTS.computeIfAbsent(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
                    path -> new AtomicInteger()).incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes());
            exchange.close();
        });
        server.start();
        return Map.of("shop.url", "http://localhost:" + server.getAddress().getPort());
    }

    @Override
    public void stop() {
        server.stop(0);
    }
}
