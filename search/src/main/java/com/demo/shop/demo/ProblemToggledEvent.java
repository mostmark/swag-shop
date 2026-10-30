package com.demo.shop.demo;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Custom JFR event written whenever the scenario is switched, so a recording shows exactly when a problem
 * started and stopped.
 */
@Name("com.demo.shop.ProblemToggled")
@Label("Problem Toggled")
@Category({ "Swag Shop", "Demo" })
@Description("A demo scenario was switched between its naive (problem) and fixed implementation")
@StackTrace(false)
public class ProblemToggledEvent extends jdk.jfr.Event {

    @Label("Scenario")
    String scenario;

    @Label("Problem Type")
    String problemType;

    @Label("Problem Enabled")
    @Description("true while the naive implementation runs")
    boolean problemEnabled;

    @Label("Changed By")
    String changedBy;

    static void emit(DemoConfig.ScenarioConfig scenario, boolean problemEnabled, String changedBy) {
        ProblemToggledEvent event = new ProblemToggledEvent();
        if (event.isEnabled()) {
            event.scenario = scenario.id();
            event.problemType = scenario.problemType();
            event.problemEnabled = problemEnabled;
            event.changedBy = changedBy;
            event.commit();
        }
    }
}
