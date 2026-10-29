package com.demo.shop.demo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

import com.demo.shop.checkout.CheckoutService;
import com.demo.shop.pricing.PriceService;
import com.demo.shop.pricing.RequestQuoteCache;

/** Backend of the demo control page. */
@Path("/api/demo")
public class DemoResource {

    private static final String CHANGED_BY_UI = "demo control page";

    public record ScenarioView(String id, String feature, String problemType, String symptom, String cryostatHint,
            boolean problemEnabled, LoadLevel load, boolean defaultProblemEnabled, LoadLevel defaultLoad,
            Map<LoadLevel, Integer> requestsPerSecond) {
    }

    public record DemoState(boolean trafficEnabled, boolean defaultTrafficEnabled, List<ScenarioView> scenarios) {
    }

    public record ScenarioUpdate(Boolean problemEnabled, LoadLevel load) {
    }

    public record TrafficUpdate(boolean enabled) {
    }

    public record JvmStats(long heapUsedMb, long heapCommittedMb, long heapMaxMb, double processCpuLoad,
            int availableProcessors, int liveThreads, long gcCount, long gcTimeMs, long uptimeSeconds) {
    }

    public record LeakStats(int entries, long maxEntries, long estimatedMb) {
    }

    public record Stats(JvmStats jvm, Map<String, RequestStats.Summary> requests, LeakStats priceCache,
            long ordersPlaced, boolean trafficEnabled) {
    }

    public record SourceFile(String className, String path, String code) {
    }

    public record SourceView(String scenario, SourceFile problem, SourceFile fix) {
    }

    @Inject
    DemoSettings settings;

    @Inject
    DemoConfig config;

    @Inject
    RequestStats requestStats;

    @Inject
    PriceService priceService;

    @Inject
    CheckoutService checkoutService;

    @GET
    @Path("/settings")
    public DemoState state() {
        List<ScenarioView> scenarios = Arrays.stream(Scenario.values()).map(this::view).toList();
        return new DemoState(settings.trafficEnabled(), config.traffic().enabled(), scenarios);
    }

    @PUT
    @Path("/scenarios/{id}")
    public ScenarioView update(@PathParam("id") String id, ScenarioUpdate update) {
        Scenario scenario = scenario(id);
        settings.update(scenario, update.problemEnabled(), update.load(), CHANGED_BY_UI);
        return view(scenario);
    }

    @PUT
    @Path("/traffic")
    public DemoState traffic(TrafficUpdate update) {
        settings.setTrafficEnabled(update.enabled());
        return state();
    }

    @POST
    @Path("/reset")
    public DemoState reset() {
        settings.reset(CHANGED_BY_UI);
        return state();
    }

    @GET
    @Path("/stats")
    public Stats stats() {
        Map<String, RequestStats.Summary> requests = new LinkedHashMap<>();
        for (Scenario scenario : Scenario.values()) {
            requests.put(scenario.id(), requestStats.summary(scenario));
        }
        RequestQuoteCache leakingCache = priceService.requestQuoteCache();
        LeakStats leak = new LeakStats(leakingCache.size(), leakingCache.maxEntries(),
                leakingCache.estimatedBytes() / (1024 * 1024));
        return new Stats(jvmStats(), requests, leak, checkoutService.ordersPlaced(), settings.trafficEnabled());
    }

    @GET
    @Path("/source/{id}")
    public SourceView source(@PathParam("id") String id) {
        Scenario scenario = scenario(id);
        return new SourceView(scenario.id(), sourceFile(scenario.problemSource()),
                sourceFile(scenario.fixSource()));
    }

    private ScenarioView view(Scenario scenario) {
        DemoSettings.ScenarioState state = settings.state(scenario);
        DemoConfig.ScenarioConfig defaults = config.scenario(scenario);
        Map<LoadLevel, Integer> rates = new LinkedHashMap<>();
        for (LoadLevel level : LoadLevel.values()) {
            rates.put(level, ShopperSimulator.rate(scenario, level));
        }
        return new ScenarioView(scenario.id(), scenario.feature(), scenario.problemType(), scenario.symptom(),
                scenario.cryostatHint(), state.problemEnabled(), state.load(), defaults.problemEnabled(),
                defaults.load(), rates);
    }

    private static Scenario scenario(String id) {
        return Scenario.fromId(id).orElseThrow(() -> new NotFoundException("Unknown scenario " + id));
    }

    private static SourceFile sourceFile(String path) {
        try (InputStream in = DemoResource.class.getClassLoader().getResourceAsStream("shop-source/" + path)) {
            if (in == null) {
                throw new NotFoundException("Source not packaged: " + path);
            }
            String className = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            return new SourceFile(className, "src/main/java/" + path, new String(in.readAllBytes(),
                    StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private static JvmStats jvmStats() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double cpu = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? Math.max(0, os.getProcessCpuLoad())
                : 0;
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        long mb = 1024 * 1024;
        return new JvmStats(heap.getUsed() / mb, heap.getCommitted() / mb, heap.getMax() / mb, cpu,
                Runtime.getRuntime().availableProcessors(), ManagementFactory.getThreadMXBean().getThreadCount(),
                gcCount, gcTime, ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }
}
