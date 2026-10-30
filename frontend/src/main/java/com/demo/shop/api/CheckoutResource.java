package com.demo.shop.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.demo.shop.clients.OrderClient;

import io.smallrye.mutiny.Uni;

/** Places the order from the cart with the order service. */
@Path("/api/checkout")
public class CheckoutResource {

    @Inject
    @RestClient
    OrderClient order;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> checkout(String cart) {
        return Relay.relay(order.checkout(cart));
    }
}
