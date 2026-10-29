package com.demo.shop.demo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;

/**
 * Runtime state of the demo scenario. Reads are lock free because the service checks it on every request; a
 * lock here would show up in the lock contention analysis.
 */
@ApplicationScoped
public class DemoSettings {

    private volatile Boolean problemEnabled;

    @Inject
    DemoConfig config;

    @Inject
    Event<ScenarioChanged> scenarioChanged;

    void onStart(@Observes StartupEvent event) {
        reset("startup");
    }

    public boolean problemEnabled() {
        return Boolean.TRUE.equals(problemEnabled);
    }

    public synchronized void setProblemEnabled(boolean enabled, String changedBy) {
        if (problemEnabled == null || problemEnabled != enabled) {
            problemEnabled = enabled;
            ProblemToggledEvent.emit(config.scenario(), enabled, changedBy);
            scenarioChanged.fire(new ScenarioChanged(enabled));
        }
    }

    /** Goes back to the value from application.properties. */
    public void reset(String changedBy) {
        setProblemEnabled(config.scenario().problemEnabled(), changedBy);
    }
}
