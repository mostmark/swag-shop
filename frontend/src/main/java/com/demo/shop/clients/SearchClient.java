package com.demo.shop.clients;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.smallrye.mutiny.Uni;

@RegisterRestClient(configKey = "search")
public interface SearchClient extends ScenarioServiceClient {

    @GET
    @Path("/api/search")
    Uni<Response> search(@QueryParam("q") String query);
}
