package com.demo.shop.load;

import java.time.Duration;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Request rate and response time of each scenario's feature over the last ten seconds, as the simulated shoppers
 * see them, for the demo control page.
 */
@ApplicationScoped
public class RequestStats {

    static final Duration WINDOW = Duration.ofSeconds(10);

    /** failed counts the requests in the window that got no answer or a server error. */
    public record Summary(double requestsPerSecond, double averageMillis, double p95Millis, long total, int failed) {
    }

    private record Sample(long finishedAtNanos, long durationNanos, boolean failed) {
    }

    private final Map<Scenario, Deque<Sample>> samples = new EnumMap<>(Scenario.class);
    private final Map<Scenario, AtomicLong> totals = new EnumMap<>(Scenario.class);

    public RequestStats() {
        for (Scenario scenario : Scenario.values()) {
            samples.put(scenario, new ConcurrentLinkedDeque<>());
            totals.put(scenario, new AtomicLong());
        }
    }

    public void record(Scenario scenario, long durationNanos, boolean failed) {
        samples.get(scenario).addLast(new Sample(System.nanoTime(), durationNanos, failed));
        totals.get(scenario).incrementAndGet();
    }

    public Summary summary(Scenario scenario) {
        Deque<Sample> deque = samples.get(scenario);
        long cutoff = System.nanoTime() - WINDOW.toNanos();
        while (!deque.isEmpty() && deque.peekFirst().finishedAtNanos() - cutoff < 0) {
            deque.pollFirst();
        }
        Sample[] window = deque.toArray(Sample[]::new);
        long[] durations = Arrays.stream(window).filter(s -> !s.failed()).mapToLong(Sample::durationNanos).sorted()
                .toArray();
        int failed = window.length - durations.length;
        long total = totals.get(scenario).get();
        if (durations.length == 0) {
            return new Summary(0, 0, 0, total, failed);
        }
        double average = Arrays.stream(durations).average().orElse(0) / 1_000_000.0;
        double p95 = durations[(int) Math.min(durations.length - 1, Math.ceil(durations.length * 0.95) - 1)]
                / 1_000_000.0;
        return new Summary(durations.length / (double) WINDOW.toSeconds(), average, p95, total, failed);
    }
}
