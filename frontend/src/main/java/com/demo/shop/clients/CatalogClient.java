package com.demo.shop.clients;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

@RegisterRestClient(configKey = "catalog")
public interface CatalogClient {

    @GET
    @Path("/api/products")
    Uni<Response> products();

    @GET
    @Path("/api/products/{id}")
    Uni<JsonNode> product(@PathParam("id") String id);
}
