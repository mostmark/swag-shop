package com.demo.shop.clients;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

/** The simulated shoppers: load levels, the shoppers switch and the response times they see. */
@RegisterRestClient(configKey = "load-generator")
@Path("/api/load")
public interface LoadGeneratorClient {

    record ScenarioLoad(String id, String load, String defaultLoad, Map<String, Integer> requestsPerSecond) {
    }

    record LoadState(boolean trafficEnabled, boolean defaultTrafficEnabled, List<ScenarioLoad> scenarios) {
    }

    record LoadUpdate(String load) {
    }

    record TrafficUpdate(boolean enabled) {
    }

    @GET
    Uni<LoadState> state();

    @PUT
    @Path("/scenarios/{id}")
    Uni<ScenarioLoad> updateLoad(@PathParam("id") String id, LoadUpdate update);

    @PUT
    @Path("/traffic")
    Uni<LoadState> traffic(TrafficUpdate update);

    @POST
    @Path("/reset")
    Uni<LoadState> reset();

    @GET
    @Path("/stats")
    Uni<JsonNode> stats();
}
