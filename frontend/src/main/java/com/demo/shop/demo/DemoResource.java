package com.demo.shop.demo;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.demo.shop.clients.InventoryClient;
import com.demo.shop.clients.JvmClient;
import com.demo.shop.clients.LoadGeneratorClient;
import com.demo.shop.clients.LoadGeneratorClient.LoadState;
import com.demo.shop.clients.LoadGeneratorClient.ScenarioLoad;
import com.demo.shop.clients.OrderClient;
import com.demo.shop.clients.PricingClient;
import com.demo.shop.clients.ScenarioServiceClient;
import com.demo.shop.clients.ScenarioServiceClient.ScenarioInfo;
import com.demo.shop.clients.ScenarioServiceClient.ScenarioUpdate;
import com.demo.shop.clients.SearchClient;
import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

/**
 * Backend of the demo control page. Each scenario card combines two services: the one with the problem switch
 * and the load generator with the load level and response times. The page's actions are passed on to them.
 */
@Path("/api/demo")
public class DemoResource {

    private static final String CHANGED_BY_UI = "demo control page";

    /** The statistics are polled every 2 seconds; a service that is slower to answer is shown as unavailable. */
    static final Duration STATS_TIMEOUT = Duration.ofMillis(1500);

    /**
     * jvmService is the service whose JVM the card shows: the one shoppers call. For checkout that is the order
     * service, while the problem switch is in inventory.
     */
    public record ScenarioView(String id, String feature, String problemType, String symptom, String cryostatHint,
            boolean problemEnabled, String load, boolean defaultProblemEnabled, String defaultLoad,
            Map<String, Integer> requestsPerSecond, String jvmService) {
    }

    public record DemoState(boolean trafficEnabled, boolean defaultTrafficEnabled, List<ScenarioView> scenarios) {
    }

    public record ScenarioChange(Boolean problemEnabled, String load) {
    }

    public record TrafficChange(boolean enabled) {
    }

    /** Every part is null while its service does not answer. */
    public record Stats(Boolean trafficEnabled, JsonNode requests, Map<String, JsonNode> jvm, JsonNode priceCache,
            Long ordersPlaced) {
    }

    @Inject
    @RestClient
    SearchClient search;

    @Inject
    @RestClient
    PricingClient pricing;

    @Inject
    @RestClient
    InventoryClient inventory;

    @Inject
    @RestClient
    OrderClient order;

    @Inject
    @RestClient
    LoadGeneratorClient loadGenerator;

    @GET
    @Path("/settings")
    public Uni<DemoState> state() {
        return Uni.combine().all()
                .unis(loadGenerator.state(), search.scenario(), pricing.scenario(), inventory.scenario())
                .asTuple()
                .map(all -> {
                    LoadState load = all.getItem1();
                    List<ScenarioView> scenarios = List.of(all.getItem2(), all.getItem3(), all.getItem4()).stream()
                            .map(scenario -> view(scenario, load))
                            .toList();
                    return new DemoState(load.trafficEnabled(), load.defaultTrafficEnabled(), scenarios);
                });
    }

    @PUT
    @Path("/scenarios/{id}")
    public Uni<ScenarioView> update(@PathParam("id") String id, ScenarioChange change) {
        ScenarioServiceClient service = scenarioService(id);
        Uni<?> problem = change.problemEnabled() == null ? Uni.createFrom().voidItem()
                : service.updateScenario(new ScenarioUpdate(change.problemEnabled(), CHANGED_BY_UI));
        Uni<?> load = change.load() == null ? Uni.createFrom().voidItem()
                : loadGenerator.updateLoad(id, new LoadGeneratorClient.LoadUpdate(change.load()));
        return Uni.combine().all().unis(problem, load).discardItems()
                .chain(() -> Uni.combine().all().unis(service.scenario(), loadGenerator.state()).asTuple())
                .map(both -> view(both.getItem1(), both.getItem2()));
    }

    @PUT
    @Path("/traffic")
    public Uni<DemoState> traffic(TrafficChange change) {
        return loadGenerator.traffic(new LoadGeneratorClient.TrafficUpdate(change.enabled())).chain(this::state);
    }

    @POST
    @Path("/reset")
    public Uni<DemoState> reset() {
        return Uni.combine().all()
                .unis(loadGenerator.reset(), search.reset(CHANGED_BY_UI), pricing.reset(CHANGED_BY_UI),
                        inventory.reset(CHANGED_BY_UI))
                .discardItems()
                .chain(this::state);
    }

    @GET
    @Path("/stats")
    public Uni<Stats> stats() {
        return Uni.combine().all()
                .unis(orNull(loadGenerator.stats()), orNull(search.jvm()), orNull(pricing.jvm()), orNull(order.jvm()),
                        orNull(pricing.leak()), orNull(order.orders()))
                .asTuple()
                .map(all -> {
                    JsonNode load = all.getItem1();
                    Map<String, JsonNode> jvm = new LinkedHashMap<>();
                    jvm.put("search", all.getItem2());
                    jvm.put("pricing", all.getItem3());
                    jvm.put("checkout", all.getItem4());
                    JsonNode orders = all.getItem6();
                    return new Stats(load == null ? null : load.path("trafficEnabled").asBoolean(),
                            load == null ? null : load.path("requests"), jvm, all.getItem5(),
                            orders == null ? null : orders.path("ordersPlaced").asLong());
                });
    }

    @GET
    @Path("/source/{id}")
    public Uni<JsonNode> source(@PathParam("id") String id) {
        return scenarioService(id).source();
    }

    private ScenarioServiceClient scenarioService(String id) {
        return switch (id) {
            case "search" -> search;
            case "pricing" -> pricing;
            case "checkout" -> inventory;
            default -> throw new NotFoundException("Unknown scenario " + id);
        };
    }

    private static String jvmService(String id) {
        return id.equals("checkout") ? "order" : id;
    }

    private static ScenarioView view(ScenarioInfo scenario, LoadState load) {
        ScenarioLoad scenarioLoad = load.scenarios().stream().filter(s -> s.id().equals(scenario.id())).findFirst()
                .orElseThrow(() -> new IllegalStateException("The load generator has no scenario " + scenario.id()));
        return new ScenarioView(scenario.id(), scenario.feature(), scenario.problemType(), scenario.symptom(),
                scenario.cryostatHint(), scenario.problemEnabled(), scenarioLoad.load(),
                scenario.defaultProblemEnabled(), scenarioLoad.defaultLoad(), scenarioLoad.requestsPerSecond(),
                jvmService(scenario.id()));
    }

    private static <T> Uni<T> orNull(Uni<T> call) {
        return call.ifNoItem().after(STATS_TIMEOUT).fail().onFailure().recoverWithNull();
    }
}
