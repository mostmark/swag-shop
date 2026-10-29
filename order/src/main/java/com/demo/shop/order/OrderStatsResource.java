package com.demo.shop.order;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** The number of orders placed, for the demo control page. */
@Path("/api/demo/orders")
public class OrderStatsResource {

    public record OrderStats(long ordersPlaced) {
    }

    @Inject
    CheckoutService checkoutService;

    @GET
    public OrderStats orders() {
        return new OrderStats(checkoutService.ordersPlaced());
    }
}
