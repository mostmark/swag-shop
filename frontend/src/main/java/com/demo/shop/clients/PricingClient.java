package com.demo.shop.clients;

import java.math.BigDecimal;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

@RegisterRestClient(configKey = "pricing")
public interface PricingClient extends ScenarioServiceClient {

    record Price(String productId, BigDecimal price, BigDecimal lowestLast30Days, JsonNode priceHistory) {
    }

    /** The shopper's X-Request-Id is passed on: the leaking price cache uses it in its key. */
    @GET
    @Path("/api/prices/{productId}")
    Uni<Price> price(@PathParam("productId") String productId, @HeaderParam("X-Request-Id") String requestId);

    /** Size of the leaking price cache. */
    @GET
    @Path("/api/demo/leak")
    Uni<JsonNode> leak();
}
