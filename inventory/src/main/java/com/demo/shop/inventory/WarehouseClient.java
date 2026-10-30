package com.demo.shop.inventory;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Tells the warehouse system about a reservation so the goods are put aside. The real system is a remote
 * service; here the call is simulated by waiting for its typical response time.
 */
@ApplicationScoped
public class WarehouseClient {

    static final Duration RESPONSE_TIME = Duration.ofMillis(60);

    public void confirmReservation(String productId, int quantity) {
        try {
            Thread.sleep(RESPONSE_TIME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the warehouse", e);
        }
    }
}
