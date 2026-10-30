package com.demo.shop.order;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Stands in for the inventory service: accepts every reservation and counts them. */
public class FakeInventory implements QuarkusTestResourceLifecycleManager {

    static final AtomicInteger RESERVATIONS = new AtomicInteger();

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/inventory/reservations", exchange -> {
            InventoryClient.ReservationRequest request = mapper.readValue(exchange.getRequestBody(),
                    InventoryClient.ReservationRequest.class);
            RESERVATIONS.incrementAndGet();
            byte[] body = mapper.writeValueAsString(new InventoryClient.Reservation(request.productId(),
                    request.quantity(), 500 - request.quantity())).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return Map.of("quarkus.rest-client.inventory.url", "http://localhost:" + server.getAddress().getPort());
    }

    @Override
    public void stop() {
        server.stop(0);
    }
}
