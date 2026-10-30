package com.demo.shop.load;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** Load levels, the shoppers switch and the response times, for the demo control page (through the frontend). */
@Path("/api/load")
public class LoadResource {

    public record ScenarioLoad(String id, LoadLevel load, LoadLevel defaultLoad,
            Map<LoadLevel, Integer> requestsPerSecond) {
    }

    public record LoadState(boolean trafficEnabled, boolean defaultTrafficEnabled, List<ScenarioLoad> scenarios) {
    }

    public record LoadUpdate(LoadLevel load) {
    }

    public record TrafficUpdate(boolean enabled) {
    }

    public record Stats(boolean trafficEnabled, Map<String, RequestStats.Summary> requests) {
    }

    @Inject
    LoadSettings settings;

    @Inject
    LoadConfig config;

    @Inject
    RequestStats requestStats;

    @GET
    public LoadState state() {
        List<ScenarioLoad> scenarios = Arrays.stream(Scenario.values()).map(this::view).toList();
        return new LoadState(settings.trafficEnabled(), config.trafficEnabled(), scenarios);
    }

    @PUT
    @Path("/scenarios/{id}")
    public ScenarioLoad update(@PathParam("id") String id, LoadUpdate update) {
        Scenario scenario = Scenario.fromId(id).orElseThrow(() -> new NotFoundException("Unknown scenario " + id));
        if (update == null || update.load() == null) {
            throw new BadRequestException("load is missing");
        }
        settings.setLoad(scenario, update.load());
        return view(scenario);
    }

    @PUT
    @Path("/traffic")
    public LoadState traffic(TrafficUpdate update) {
        settings.setTrafficEnabled(update.enabled());
        return state();
    }

    @POST
    @Path("/reset")
    public LoadState reset() {
        settings.reset();
        return state();
    }

    @GET
    @Path("/stats")
    public Stats stats() {
        Map<String, RequestStats.Summary> requests = new LinkedHashMap<>();
        for (Scenario scenario : Scenario.values()) {
            requests.put(scenario.id(), requestStats.summary(scenario));
        }
        return new Stats(settings.trafficEnabled(), requests);
    }

    private ScenarioLoad view(Scenario scenario) {
        Map<LoadLevel, Integer> rates = new LinkedHashMap<>();
        for (LoadLevel level : LoadLevel.values()) {
            rates.put(level, scenario.rate(level));
        }
        return new ScenarioLoad(scenario.id(), settings.load(scenario), config.load(scenario), rates);
    }
}
