package com.demo.shop.inventory;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.demo.DemoSettings;

@Path("/api/inventory")
public class InventoryResource {

    public record ReservationRequest(String productId, int quantity) {
    }

    public record Stock(String productId, int available) {
    }

    @Inject
    ProductCatalog catalog;

    @Inject
    DemoSettings settings;

    @Inject
    SynchronizedInventory synchronizedInventory;

    @Inject
    PerProductLockInventory perProductLockInventory;

    /** Called by the order service for every line of an order. */
    @POST
    @Path("/reservations")
    public Inventory.Reservation reserve(ReservationRequest request) {
        if (request == null || request.quantity() < 1) {
            throw new BadRequestException("Quantity must be at least 1");
        }
        if (catalog.findById(request.productId()).isEmpty()) {
            throw new BadRequestException("Unknown product " + request.productId());
        }
        return activeInventory().reserve(request.productId(), request.quantity());
    }

    @GET
    @Path("/{productId}")
    public Stock stock(@PathParam("productId") String productId) {
        if (catalog.findById(productId).isEmpty()) {
            throw new NotFoundException("Unknown product " + productId);
        }
        return new Stock(productId, activeInventory().available(productId));
    }

    private Inventory activeInventory() {
        return settings.problemEnabled() ? synchronizedInventory : perProductLockInventory;
    }
}
