package com.demo.shop.demo;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The demo scenario this service plays, set in application.properties. The demo control page can switch the
 * problem on and off at runtime, and "Reset to defaults" goes back to problem-enabled.
 */
@ConfigMapping(prefix = "demo")
public interface DemoConfig {

    ScenarioConfig scenario();

    interface ScenarioConfig {

        /** Id of the scenario on the demo control page: search, pricing or checkout. */
        String id();

        String feature();

        String problemType();

        String symptom();

        String cryostatHint();

        /** Repository path of the naive implementation, shown on the demo control page. */
        String problemSource();

        /** Repository path of the fixed implementation. */
        String fixSource();

        /** true runs the naive implementation (the problem), false runs the fixed one. */
        @WithDefault("false")
        boolean problemEnabled();
    }
}
