package com.demo.shop.demo;

/** CDI event fired after the scenario changes, for beans that need to react (for example to clear a cache). */
public record ScenarioChanged(boolean problemEnabled) {
}
