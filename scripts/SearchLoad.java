import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulated shoppers for the search service alone (demo 1): sends searches at the same rates and with the same
 * search words as the load generator, and prints the response times every second.
 *
 * <pre>
 *   java scripts/SearchLoad.java [low|medium|high] [url]      (defaults: medium http://localhost:8082)
 * </pre>
 *
 * While it runs, type low, medium or high and press Enter to change the load. Ctrl+C stops it. The url can also
 * be the frontend (http://localhost:8080), which passes searches on to the search service.
 */
public class SearchLoad {

    /** Searches per second at low, medium and high load, as in the load generator. */
    static final int[] RATES = { 2, 6, 30 };
    static final String[] LEVELS = { "low", "medium", "high" };

    /** Searches waiting for an answer at most; while that many wait, new ones are not sent. */
    static final int MAX_IN_FLIGHT = 40;
    static final Duration TICK = Duration.ofMillis(100);
    static final Duration WINDOW = Duration.ofSeconds(5);
    /** Response times above this count as slow, as on the demo control page. */
    static final long SLOW_MS = 250;

    static final List<String> SEARCH_TERMS = List.of(
            "fedora", "red hat", "coffee mug", "hoody", "backpak", "water bottle", "stickers", "enamel pins",
            "tshirt", "laptop bag", "zipper", "charcoal", "notebook", "ceramic cup", "cotton shirt", "travel",
            "waterbottle", "insulated", "drinkware", "stainless steel");

    static final String RED = "\u001b[31;1m";
    static final String RESET = "\u001b[0m";

    record Sample(long finishedAtNanos, long durationNanos, boolean failed) {
    }

    static volatile int level;
    static double credit;
    static final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);
    static final AtomicInteger skipped = new AtomicInteger();
    static final ConcurrentLinkedDeque<Sample> samples = new ConcurrentLinkedDeque<>();

    public static void main(String[] args) throws Exception {
        level = args.length > 0 ? level(args[0]) : 1;
        if (level < 0) {
            System.err.println("Usage: java scripts/SearchLoad.java [low|medium|high] [url]");
            System.exit(1);
        }
        String baseUrl = (args.length > 1 ? args[1] : "http://localhost:8082").replaceAll("/+$", "");
        boolean color = System.console() != null;

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .executor(Executors.newFixedThreadPool(8, daemon("search-load")))
                .build();
        ScheduledExecutorService clock = Executors.newScheduledThreadPool(2, daemon("search-load-clock"));
        clock.scheduleAtFixedRate(() -> tick(client, baseUrl), 0, TICK.toMillis(), TimeUnit.MILLISECONDS);
        // Halfway between two ticks, so the searches just sent are not counted as waiting
        clock.scheduleAtFixedRate(() -> report(color), 1050, 1000, TimeUnit.MILLISECONDS);

        System.out.printf("Searching %s at %s load. Type low, medium or high and Enter to change it, Ctrl+C to stop.%n",
                baseUrl, LEVELS[level]);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        for (String line; (line = input.readLine()) != null;) {
            int next = level(line.trim());
            if (next >= 0) {
                level = next;
                System.out.printf("Load changed to %s (%d searches/s)%n", LEVELS[next], RATES[next]);
            } else if (!line.isBlank()) {
                System.out.println("Type low, medium or high");
            }
        }
        // stdin closed (for example when started in the background): keep sending until stopped
        clock.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    static void tick(HttpClient client, String baseUrl) {
        credit += RATES[level] * TICK.toMillis() / 1000.0;
        for (; credit >= 1; credit--) {
            if (!inFlight.tryAcquire()) {
                skipped.incrementAndGet();
                continue;
            }
            String term = SEARCH_TERMS.get(ThreadLocalRandom.current().nextInt(SEARCH_TERMS.size()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/search?q="
                    + URLEncoder.encode(term, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            long start = System.nanoTime();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
                long end = System.nanoTime();
                samples.addLast(new Sample(end, end - start, failure != null || response.statusCode() != 200));
                inFlight.release();
            });
        }
    }

    static void report(boolean color) {
        long cutoff = System.nanoTime() - WINDOW.toNanos();
        while (!samples.isEmpty() && samples.peekFirst().finishedAtNanos() - cutoff < 0) {
            samples.pollFirst();
        }
        List<Long> millis = new ArrayList<>();
        int failed = 0;
        for (Sample sample : samples) {
            millis.add(sample.durationNanos() / 1_000_000);
            failed += sample.failed() ? 1 : 0;
        }
        millis.sort(null);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line;
        if (millis.isEmpty()) {
            line = "%s  %-6s  no answers yet".formatted(time, LEVELS[level]);
        } else {
            double average = millis.stream().mapToLong(Long::longValue).average().orElse(0);
            long p95 = millis.get((int) Math.min(millis.size() - 1, Math.ceil(millis.size() * 0.95) - 1));
            line = String.format(Locale.ROOT, "%s  %-6s  %5.1f searches/s   average %8s   95th percentile %8s",
                    time, LEVELS[level], millis.size() / (double) WINDOW.toSeconds(), format(average), format(p95));
            if (color && p95 > SLOW_MS) {
                line = RED + line + RESET;
            }
        }
        int waiting = MAX_IN_FLIGHT - inFlight.availablePermits();
        int dropped = skipped.getAndSet(0);
        System.out.println(line + (waiting > 0 ? "   %d waiting".formatted(waiting) : "")
                + (failed > 0 ? "   %d failed".formatted(failed) : "")
                + (dropped > 0 ? "   %d not sent".formatted(dropped) : ""));
    }

    static String format(double millis) {
        return millis >= 1000 ? String.format(Locale.ROOT, "%.2f s", millis / 1000) : Math.round(millis) + " ms";
    }

    static int level(String name) {
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    static ThreadFactory daemon(String name) {
        AtomicInteger number = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
