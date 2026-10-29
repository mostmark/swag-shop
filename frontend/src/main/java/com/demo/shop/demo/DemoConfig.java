package com.demo.shop.demo;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Start-up state of the demo, set in application.properties. The demo control page can change everything at
 * runtime, and "Reset to defaults" goes back to these values.
 */
@ConfigMapping(prefix = "demo")
public interface DemoConfig {

    ScenarioConfig search();

    ScenarioConfig pricing();

    ScenarioConfig checkout();

    Traffic traffic();

    Safety safety();

    default ScenarioConfig scenario(Scenario scenario) {
        return switch (scenario) {
            case SEARCH -> search();
            case PRICING -> pricing();
            case CHECKOUT -> checkout();
        };
    }

    interface ScenarioConfig {

        /** true runs the naive implementation (the problem), false runs the fixed one. */
        @WithDefault("false")
        boolean problemEnabled();

        /** How many requests per second the simulated shoppers send to this feature. */
        @WithDefault("medium")
        LoadLevel load();
    }

    interface Traffic {

        /** Start the simulated shoppers when the application starts. */
        @WithDefault("false")
        boolean enabled();
    }

    interface Safety {

        /**
         * The leaking price cache stops growing when its estimated size reaches this fraction of the maximum
         * heap, so the demo never ends with an OutOfMemoryError.
         */
        @WithDefault("0.7")
        double leakMaxHeapRatio();
    }
}
