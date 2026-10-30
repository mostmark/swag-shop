package com.demo.shop.load;

import java.util.Arrays;
import java.util.Optional;

/** The shop features the simulated shoppers use, one per demo scenario, with their requests per second. */
public enum Scenario {

    // The naive search handles about 13 searches/s on a laptop: high load overloads it, medium does not
    SEARCH("search", 2, 6, 30),
    PRICING("pricing", 5, 15, 30),
    // The global inventory lock lets about 16 checkouts/s through (one 60 ms warehouse call each): high load queues
    // up behind it, medium does not
    CHECKOUT("checkout", 3, 8, 30);

    private final String id;
    private final int[] rates;

    Scenario(String id, int... rates) {
        this.id = id;
        this.rates = rates;
    }

    public String id() {
        return id;
    }

    public int rate(LoadLevel load) {
        return rates[load.ordinal()];
    }

    public static Optional<Scenario> fromId(String id) {
        return Arrays.stream(values()).filter(s -> s.id.equalsIgnoreCase(id)).findFirst();
    }
}
