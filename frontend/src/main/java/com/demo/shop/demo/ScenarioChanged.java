package com.demo.shop.demo;

/** CDI event fired after a scenario changes, for beans that need to react (for example to clear a cache). */
public record ScenarioChanged(Scenario scenario, boolean problemEnabled, LoadLevel load) {
}
