package com.demo.shop.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.vertx.http.HttpServerStart;

import org.jboss.logging.Logger;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;

/**
 * Simulated shoppers: sends a steady stream of HTTP requests to the shop's own REST API, so the problems show
 * up without anyone clicking around during the demo.
 */
@ApplicationScoped
public class ShopperSimulator {

    private static final Logger LOG = Logger.getLogger(ShopperSimulator.class);

    /** Requests per second at LOW, MEDIUM and HIGH load. */
    static final Map<Scenario, int[]> RATES = Map.of(
            Scenario.SEARCH, new int[] { 3, 10, 32 },
            Scenario.PRICING, new int[] { 5, 15, 30 },
            Scenario.CHECKOUT, new int[] { 15, 40, 60 });

    static final int MAX_IN_FLIGHT_PER_SCENARIO = 40;
    private static final Duration TICK = Duration.ofMillis(100);

    private static final List<String> SEARCH_TERMS = List.of(
            "fedora", "red hat", "coffee mug", "hoody", "backpak", "water bottle", "stickers", "enamel pins",
            "tshirt", "laptop bag", "zipper", "charcoal", "notebook", "ceramic cup", "cotton shirt", "travel");

    @Inject
    DemoSettings settings;

    @Inject
    ProductCatalog catalog;

    private final Map<Scenario, Semaphore> inFlight = new EnumMap<>(Scenario.class);
    private final Map<Scenario, Double> credit = new EnumMap<>(Scenario.class);
    private final AtomicInteger threadNumber = new AtomicInteger();
    private ExecutorService senders;
    private ScheduledExecutorService ticker;
    private HttpClient client;
    private volatile String baseUri;

    void onHttpServerStart(@ObservesAsync HttpServerStart event) {
        baseUri = "http://localhost:" + event.options().getPort();
        for (Scenario scenario : Scenario.values()) {
            inFlight.put(scenario, new Semaphore(MAX_IN_FLIGHT_PER_SCENARIO));
            credit.put(scenario, 0.0);
        }
        senders = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "shopper-simulator-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .executor(senders)
                .build();
        ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "shopper-simulator-clock");
            thread.setDaemon(true);
            return thread;
        });
        ticker.scheduleAtFixedRate(this::tick, TICK.toMillis(), TICK.toMillis(), TimeUnit.MILLISECONDS);
        LOG.infof("Shopper simulator ready, sending to %s (traffic %s)", baseUri,
                settings.trafficEnabled() ? "on" : "off");
    }

    void onShutdown(@Observes ShutdownEvent event) {
        if (ticker != null) {
            ticker.shutdownNow();
            senders.shutdownNow();
        }
    }

    public static int rate(Scenario scenario, LoadLevel load) {
        return RATES.get(scenario)[load.ordinal()];
    }

    private void tick() {
        try {
            if (!settings.trafficEnabled()) {
                return;
            }
            for (Scenario scenario : Scenario.values()) {
                double available = credit.get(scenario) + rate(scenario, settings.load(scenario)) / 10.0;
                while (available >= 1) {
                    send(scenario);
                    available--;
                }
                credit.put(scenario, available);
            }
        } catch (RuntimeException e) {
            LOG.warn("Shopper simulator tick failed", e);
        }
    }

    private void send(Scenario scenario) {
        Semaphore permits = inFlight.get(scenario);
        if (!permits.tryAcquire()) {
            return;
        }
        client.sendAsync(request(scenario), HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, failure) -> permits.release());
    }

    private HttpRequest request(Scenario scenario) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Product> products = catalog.all();
        HttpRequest.Builder builder = switch (scenario) {
            case SEARCH -> HttpRequest.newBuilder(URI.create(baseUri + "/api/search?q="
                    + SEARCH_TERMS.get(random.nextInt(SEARCH_TERMS.size())).replace(" ", "+")));
            case PRICING -> HttpRequest.newBuilder(URI.create(baseUri + "/api/products/"
                    + products.get(random.nextInt(products.size())).id()))
                    .header("X-Request-Id", UUID.randomUUID().toString());
            case CHECKOUT -> HttpRequest.newBuilder(URI.create(baseUri + "/api/checkout"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"items":[{"productId":"%s","quantity":%d}]}"""
                            .formatted(products.get(random.nextInt(products.size())).id(), 1 + random.nextInt(3))));
        };
        return builder.timeout(Duration.ofSeconds(30)).build();
    }
}
