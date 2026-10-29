package com.demo.shop.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

/** Switches this service's demo scenario; used by the demo control page and scripts/*-problem.sh. */
@Path("/api/demo")
public class DemoResource {

    private static final String CHANGED_BY_DEFAULT = "demo API";
    private static final String SOURCE_ROOT = "src/main/java/";

    public record ScenarioView(String id, String feature, String problemType, String symptom, String cryostatHint,
            boolean problemEnabled, boolean defaultProblemEnabled) {
    }

    /** changedBy is recorded in the ProblemToggled JFR event; problemEnabled null keeps the current value. */
    public record ScenarioUpdate(Boolean problemEnabled, String changedBy) {
    }

    public record SourceFile(String className, String path, String code) {
    }

    public record SourceView(String scenario, SourceFile problem, SourceFile fix) {
    }

    @Inject
    DemoSettings settings;

    @Inject
    DemoConfig config;

    @GET
    @Path("/scenario")
    public ScenarioView scenario() {
        DemoConfig.ScenarioConfig scenario = config.scenario();
        return new ScenarioView(scenario.id(), scenario.feature(), scenario.problemType(), scenario.symptom(),
                scenario.cryostatHint(), settings.problemEnabled(), scenario.problemEnabled());
    }

    @PUT
    @Path("/scenario")
    public ScenarioView update(ScenarioUpdate update) {
        if (update != null && update.problemEnabled() != null) {
            settings.setProblemEnabled(update.problemEnabled(), changedBy(update.changedBy()));
        }
        return scenario();
    }

    @POST
    @Path("/reset")
    public ScenarioView reset(@QueryParam("changedBy") String changedBy) {
        settings.reset(changedBy(changedBy));
        return scenario();
    }

    @GET
    @Path("/source")
    public SourceView source() {
        DemoConfig.ScenarioConfig scenario = config.scenario();
        return new SourceView(scenario.id(), sourceFile(scenario.problemSource()),
                sourceFile(scenario.fixSource()));
    }

    private static String changedBy(String changedBy) {
        return changedBy == null || changedBy.isBlank() ? CHANGED_BY_DEFAULT : changedBy;
    }

    /** The pom packages the scenario's source files under shop-source/, by their package path. */
    private static SourceFile sourceFile(String path) {
        String packagePath = path.substring(path.indexOf(SOURCE_ROOT) + SOURCE_ROOT.length());
        try (InputStream in = DemoResource.class.getClassLoader().getResourceAsStream("shop-source/" + packagePath)) {
            if (in == null) {
                throw new NotFoundException("Source not packaged: " + path);
            }
            String className = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            return new SourceFile(className, path, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
