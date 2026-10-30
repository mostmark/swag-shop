package com.demo.shop.clients;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.smallrye.mutiny.Uni;

@RegisterRestClient(configKey = "inventory")
public interface InventoryClient extends ScenarioServiceClient {

    record Stock(String productId, int available) {
    }

    @GET
    @Path("/api/inventory/{productId}")
    Uni<Stock> stock(@PathParam("productId") String productId);
}
