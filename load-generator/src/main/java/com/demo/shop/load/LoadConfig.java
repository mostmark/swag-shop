package com.demo.shop.load;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Start-up state of the simulated shoppers, set in application.properties. The demo control page can change
 * everything at runtime, and "Reset to defaults" goes back to these values.
 */
@ConfigMapping(prefix = "load")
public interface LoadConfig {

    /** How many requests per second the shoppers send to product search. */
    @WithDefault("medium")
    LoadLevel search();

    @WithDefault("medium")
    LoadLevel pricing();

    @WithDefault("medium")
    LoadLevel checkout();

    /** Start the simulated shoppers when the load generator starts. */
    @WithDefault("false")
    boolean trafficEnabled();

    default LoadLevel load(Scenario scenario) {
        return switch (scenario) {
            case SEARCH -> search();
            case PRICING -> pricing();
            case CHECKOUT -> checkout();
        };
    }
}
