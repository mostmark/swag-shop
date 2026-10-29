package com.demo.shop.load;

import java.util.concurrent.atomic.AtomicReferenceArray;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;

/** Runtime load level of each scenario, and whether the shoppers are sending traffic at all. */
@ApplicationScoped
public class LoadSettings {

    private final AtomicReferenceArray<LoadLevel> loads = new AtomicReferenceArray<>(Scenario.values().length);
    private volatile boolean trafficEnabled;

    @Inject
    LoadConfig config;

    void onStart(@Observes StartupEvent event) {
        reset();
    }

    public LoadLevel load(Scenario scenario) {
        return loads.get(scenario.ordinal());
    }

    public void setLoad(Scenario scenario, LoadLevel load) {
        loads.set(scenario.ordinal(), load);
    }

    public boolean trafficEnabled() {
        return trafficEnabled;
    }

    public void setTrafficEnabled(boolean enabled) {
        trafficEnabled = enabled;
    }

    /** Goes back to the values from application.properties. */
    public void reset() {
        for (Scenario scenario : Scenario.values()) {
            loads.set(scenario.ordinal(), config.load(scenario));
        }
        trafficEnabled = config.trafficEnabled();
    }
}
