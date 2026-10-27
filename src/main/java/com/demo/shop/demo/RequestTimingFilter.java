package com.demo.shop.demo;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/** Measures the response time of the shop features that the demo scenarios affect. */
public class RequestTimingFilter {

    private static final String START_PROPERTY = "com.demo.shop.start";

    @Inject
    RequestStats stats;

    @ServerRequestFilter
    public void start(ContainerRequestContext request) {
        request.setProperty(START_PROPERTY, System.nanoTime());
    }

    @ServerResponseFilter
    public void finish(ContainerRequestContext request, ContainerResponseContext response) {
        if (request.getProperty(START_PROPERTY) instanceof Long start) {
            scenarioFor(request.getUriInfo().getPath())
                    .ifPresent(scenario -> stats.record(scenario, System.nanoTime() - start));
        }
    }

    static Optional<Scenario> scenarioFor(String path) {
        if (path.equals("/api/search")) {
            return Optional.of(Scenario.SEARCH);
        }
        if (path.startsWith("/api/products/")) {
            return Optional.of(Scenario.PRICING);
        }
        if (path.equals("/api/checkout")) {
            return Optional.of(Scenario.CHECKOUT);
        }
        return Optional.empty();
    }
}
