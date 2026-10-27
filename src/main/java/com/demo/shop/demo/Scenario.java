package com.demo.shop.demo;

import java.util.Arrays;
import java.util.Optional;

/**
 * The problems the shop can simulate. Each one has a naive implementation (the problem) and a fixed one,
 * and the demo control page switches between them at runtime.
 */
public enum Scenario {

    SEARCH("search", "Product search", "CPU hotspot",
            "Searches get slow and CPU climbs while the shop has spare threads.",
            "Use high load and a Profiling recording. Automated Analysis: \"Method Profiling\" points at "
                    + "RecursiveEditDistance.distance. Dashboard: process CPU load rises.",
            "com/demo/shop/search/RecursiveEditDistance.java",
            "com/demo/shop/search/IterativeEditDistance.java"),

    PRICING("pricing", "Product prices", "Memory leak",
            "Heap usage climbs like a staircase and never comes back down after GC.",
            "Dashboard: heap used keeps growing. The Agent's smart trigger starts a SwagShopMemoryLeak recording "
                    + "(paths to GC roots); its Automated Analysis turns \"Heap Live Set Trend\" red and names the leak "
                    + "candidate: PricePoint objects held by RequestQuoteCache.",
            "com/demo/shop/pricing/RequestQuoteCache.java",
            "com/demo/shop/pricing/ProductQuoteCache.java"),

    CHECKOUT("checkout", "Checkout", "Lock contention",
            "Checkouts get slow but CPU stays flat: threads are waiting, not working.",
            "Use high load. Automated Analysis: \"Java Blocking\" names the SynchronizedInventory monitor and the "
                    + "blocked threads. Dashboard: CPU stays low while latency rises.",
            "com/demo/shop/inventory/SynchronizedInventory.java",
            "com/demo/shop/inventory/PerProductLockInventory.java");

    private final String id;
    private final String feature;
    private final String problemType;
    private final String symptom;
    private final String cryostatHint;
    private final String problemSource;
    private final String fixSource;

    Scenario(String id, String feature, String problemType, String symptom, String cryostatHint,
            String problemSource, String fixSource) {
        this.id = id;
        this.feature = feature;
        this.problemType = problemType;
        this.symptom = symptom;
        this.cryostatHint = cryostatHint;
        this.problemSource = problemSource;
        this.fixSource = fixSource;
    }

    public String id() {
        return id;
    }

    public String feature() {
        return feature;
    }

    public String problemType() {
        return problemType;
    }

    public String symptom() {
        return symptom;
    }

    public String cryostatHint() {
        return cryostatHint;
    }

    public String problemSource() {
        return problemSource;
    }

    public String fixSource() {
        return fixSource;
    }

    public static Optional<Scenario> fromId(String id) {
        return Arrays.stream(values()).filter(s -> s.id.equalsIgnoreCase(id)).findFirst();
    }
}
