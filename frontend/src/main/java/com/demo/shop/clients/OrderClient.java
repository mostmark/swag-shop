package com.demo.shop.clients;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

@RegisterRestClient(configKey = "order")
public interface OrderClient extends JvmClient {

    /** The order as the browser sent it. */
    @POST
    @Path("/api/checkout")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<Response> checkout(String order);

    @GET
    @Path("/api/demo/orders")
    Uni<JsonNode> orders();
}
