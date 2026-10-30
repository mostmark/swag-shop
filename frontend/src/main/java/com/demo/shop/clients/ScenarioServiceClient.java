package com.demo.shop.clients;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

/** A service with a demo scenario: search, pricing or inventory. They share the same demo API. */
public interface ScenarioServiceClient extends JvmClient {

    record ScenarioInfo(String id, String feature, String problemType, String symptom, String cryostatHint,
            boolean problemEnabled, boolean defaultProblemEnabled) {
    }

    /** changedBy is recorded in the service's ProblemToggled JFR event. */
    record ScenarioUpdate(boolean problemEnabled, String changedBy) {
    }

    @GET
    @Path("/api/demo/scenario")
    Uni<ScenarioInfo> scenario();

    @PUT
    @Path("/api/demo/scenario")
    Uni<ScenarioInfo> updateScenario(ScenarioUpdate update);

    @POST
    @Path("/api/demo/reset")
    Uni<ScenarioInfo> reset(@QueryParam("changedBy") String changedBy);

    /** The naive and fixed code of the scenario. */
    @GET
    @Path("/api/demo/source")
    Uni<JsonNode> source();
}
