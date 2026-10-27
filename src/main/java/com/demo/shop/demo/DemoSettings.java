package com.demo.shop.demo;

import java.util.concurrent.atomic.AtomicReferenceArray;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;

/**
 * Runtime state of the demo scenarios. Reads are lock free because the shop's services check it on every
 * request; a lock here would show up in the lock contention analysis.
 */
@ApplicationScoped
public class DemoSettings {

    public record ScenarioState(boolean problemEnabled, LoadLevel load) {
    }

    private final AtomicReferenceArray<ScenarioState> states = new AtomicReferenceArray<>(Scenario.values().length);
    private volatile boolean trafficEnabled;

    @Inject
    DemoConfig config;

    @Inject
    Event<ScenarioChanged> scenarioChanged;

    void onStart(@Observes StartupEvent event) {
        reset("startup");
    }

    public boolean problemEnabled(Scenario scenario) {
        return state(scenario).problemEnabled();
    }

    public LoadLevel load(Scenario scenario) {
        return state(scenario).load();
    }

    public ScenarioState state(Scenario scenario) {
        return states.get(scenario.ordinal());
    }

    public boolean trafficEnabled() {
        return trafficEnabled;
    }

    public void setTrafficEnabled(boolean enabled) {
        trafficEnabled = enabled;
    }

    /** Changes a scenario; a null argument keeps the current value. */
    public synchronized void update(Scenario scenario, Boolean problemEnabled, LoadLevel load, String changedBy) {
        ScenarioState current = state(scenario);
        ScenarioState next = new ScenarioState(
                problemEnabled != null ? problemEnabled : current.problemEnabled(),
                load != null ? load : current.load());
        if (!next.equals(current)) {
            apply(scenario, next, changedBy);
        }
    }

    /** Goes back to the values from application.properties. */
    public synchronized void reset(String changedBy) {
        for (Scenario scenario : Scenario.values()) {
            DemoConfig.ScenarioConfig defaults = config.scenario(scenario);
            ScenarioState next = new ScenarioState(defaults.problemEnabled(), defaults.load());
            if (!next.equals(state(scenario))) {
                apply(scenario, next, changedBy);
            }
        }
        trafficEnabled = config.traffic().enabled();
    }

    private void apply(Scenario scenario, ScenarioState next, String changedBy) {
        states.set(scenario.ordinal(), next);
        ProblemToggledEvent.emit(scenario, next.problemEnabled(), next.load(), changedBy);
        scenarioChanged.fire(new ScenarioChanged(scenario, next.problemEnabled(), next.load()));
    }
}
