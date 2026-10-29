package com.demo.shop.order;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** The inventory service, which reserves the stock for each line of an order. */
@RegisterRestClient(configKey = "inventory")
@Path("/api/inventory")
public interface InventoryClient {

    record ReservationRequest(String productId, int quantity) {
    }

    record Reservation(String productId, int quantity, int remaining) {
    }

    @POST
    @Path("/reservations")
    Reservation reserve(ReservationRequest request);
}
