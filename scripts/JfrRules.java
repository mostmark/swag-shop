import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.util.IPreferenceValueProvider;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.rules.IResult;
import org.openjdk.jmc.flightrecorder.rules.IRule;
import org.openjdk.jmc.flightrecorder.rules.ResultToolkit;
import org.openjdk.jmc.flightrecorder.rules.RuleRegistry;
import org.openjdk.jmc.flightrecorder.rules.Severity;
import org.openjdk.jmc.flightrecorder.rules.TypedResult;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;

/**
 * Runs the JDK Mission Control automated analysis rules on a recording and prints each rule's score, the same
 * analysis JMC shows on its "Automated Analysis Results" page. Run it through scripts/jfr-rules.sh.
 */
public class JfrRules {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: JfrRules <recording.jfr> [--all]");
            System.exit(2);
        }
        boolean all = args.length > 1 && args[1].equals("--all");
        IItemCollection events = JfrLoaderToolkit.loadEvents(new File(args[0]));
        Map<IRule, Future<IResult>> futures = RulesToolkit.evaluateParallel(RuleRegistry.getRules(), events,
                IPreferenceValueProvider.DEFAULT_VALUES, 0);

        record Row(String id, String name, double score, Severity severity, String summary) {
        }
        List<Row> rows = futures.entrySet().stream().map(entry -> {
            try {
                IResult result = entry.getValue().get();
                Object score = result.getResult(TypedResult.SCORE);
                double value = score == null ? -1 : ((org.openjdk.jmc.common.unit.IQuantity) score).doubleValue();
                String summary = ResultToolkit.populateMessage(result, result.getSummary(), false);
                return new Row(entry.getKey().getId(), entry.getKey().getName(), value, result.getSeverity(),
                        summary == null ? "" : summary.replaceAll("\\s+", " "));
            } catch (Exception e) {
                return new Row(entry.getKey().getId(), entry.getKey().getName(), -1, Severity.NA,
                        "evaluation failed: " + e);
            }
        }).sorted(Comparator.comparingDouble(Row::score).reversed()).toList();

        for (Row row : rows) {
            if (all || row.severity() == Severity.WARNING || row.severity() == Severity.INFO
                    && row.score() >= 25) {
                System.out.printf("%5.0f  %-8s %-34s %s%n", row.score(), row.severity().name(), row.name(),
                        row.summary());
            }
        }
    }
}
