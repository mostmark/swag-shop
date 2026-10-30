package com.demo.shop.clients;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

/** A service that reports its JVM's heap, CPU and threads for the demo control page. */
public interface JvmClient {

    @GET
    @Path("/api/demo/jvm")
    Uni<JsonNode> jvm();
}
