package com.demo.shop.load;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;

/**
 * Simulated shoppers: sends a steady stream of HTTP requests to the shop's REST API (the frontend), so the
 * problems show up without anyone clicking around during the demo, and measures the response times they get.
 */
@ApplicationScoped
public class ShopperSimulator {

    private static final Logger LOG = Logger.getLogger(ShopperSimulator.class);

    static final int MAX_IN_FLIGHT_PER_SCENARIO = 40;
    private static final Duration TICK = Duration.ofMillis(100);

    private static final List<String> SEARCH_TERMS = List.of(
            "fedora", "red hat", "coffee mug", "hoody", "backpak", "water bottle", "stickers", "enamel pins",
            "tshirt", "laptop bag", "zipper", "charcoal", "notebook", "ceramic cup", "cotton shirt", "travel",
            "waterbottle", "insulated", "drinkware", "stainless steel");

    @Inject
    LoadSettings settings;

    @Inject
    RequestStats stats;

    @Inject
    ProductCatalog catalog;

    @ConfigProperty(name = "shop.url")
    URI shopUrl;

    private final Map<Scenario, Semaphore> inFlight = new EnumMap<>(Scenario.class);
    private final Map<Scenario, Double> credit = new EnumMap<>(Scenario.class);
    private final AtomicInteger threadNumber = new AtomicInteger();
    private ExecutorService senders;
    private ScheduledExecutorService ticker;
    private HttpClient client;

    void onStart(@Observes StartupEvent event) {
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
        LOG.infof("Shopper simulator ready, sending to %s (traffic %s)", shopUrl,
                settings.trafficEnabled() ? "on" : "off");
    }

    void onShutdown(@Observes ShutdownEvent event) {
        if (ticker != null) {
            ticker.shutdownNow();
            senders.shutdownNow();
        }
    }

    private void tick() {
        try {
            if (!settings.trafficEnabled()) {
                return;
            }
            for (Scenario scenario : Scenario.values()) {
                double available = credit.get(scenario) + scenario.rate(settings.load(scenario)) / 10.0;
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
        long start = System.nanoTime();
        client.sendAsync(request(scenario), HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, failure) -> {
                    stats.record(scenario, System.nanoTime() - start, failure != null || response.statusCode() >= 500);
                    permits.release();
                });
    }

    private HttpRequest request(Scenario scenario) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Product> products = catalog.all();
        HttpRequest.Builder builder = switch (scenario) {
            case SEARCH -> HttpRequest.newBuilder(shopUrl.resolve("/api/search?q=" + URLEncoder.encode(
                    SEARCH_TERMS.get(random.nextInt(SEARCH_TERMS.size())), StandardCharsets.UTF_8)));
            case PRICING -> HttpRequest.newBuilder(shopUrl.resolve("/api/products/"
                    + products.get(random.nextInt(products.size())).id()))
                    .header("X-Request-Id", UUID.randomUUID().toString());
            case CHECKOUT -> HttpRequest.newBuilder(shopUrl.resolve("/api/checkout"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"items":[{"productId":"%s","quantity":%d}]}"""
                            .formatted(products.get(random.nextInt(products.size())).id(), 1 + random.nextInt(3))));
        };
        return builder.timeout(Duration.ofSeconds(30)).build();
    }
}
