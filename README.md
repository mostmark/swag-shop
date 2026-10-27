# Swag Shop: a JFR and Cryostat demo application

A small web shop that sells conference swag, built with Quarkus and the Red Hat Design System. It has three
performance problems that can be switched on and off while it runs. Each problem is real code in a
realistic place, so JDK Flight Recorder (JFR), JDK Mission Control (JMC) and Cryostat find it the way they would
in production.

The demo script for the talk is in [docs/DEMO.md](docs/DEMO.md).

## The scenarios

| Feature | Problem | Naive code (problem on) | Fixed code (problem off) | What JFR shows |
|---|---|---|---|---|
| Product search | CPU hotspot | `RecursiveEditDistance`: textbook recursive edit distance, exponential work | `IterativeEditDistance`: dynamic programming | "Method Profiling" names `RecursiveEditDistance.distance`; CPU load rises |
| Product prices | Memory leak | `RequestQuoteCache`: cache keyed on the request id, never hits, never evicts | `ProductQuoteCache`: keyed on the product, bounded, expires | Heap after GC climbs like a staircase; with paths to GC roots, "Heap Live Set Trend" names the leak candidate held by `RequestQuoteCache.quotes` |
| Checkout | Lock contention | `SynchronizedInventory`: one global `synchronized` lock held during a slow warehouse call | `PerProductLockInventory`: per-product locks, slow call outside the lock | "Java Blocking" names the `SynchronizedInventory` monitor; latency rises while CPU stays flat |

Both implementations of each feature return the same results (the tests check this); only their performance
differs. Switching a scenario to the fixed code stands in for deploying the fix.

Built-in **simulated shoppers** send a steady stream of requests to all three features, so problems show up
without anyone clicking around. Each feature's load can be set to low, medium or high.

## Running locally

Requires Java 21.

```shell
./mvnw quarkus:dev                         # dev mode, simulated shoppers off
./mvnw package && java -Xmx256m -jar target/quarkus-app/quarkus-run.jar    # packaged, shoppers on
```

Open <http://localhost:8080> for the shop and <http://localhost:8080/demo.html> for the demo control page.

### Recording with JFR

```shell
# Start with a recording that is written to a file when the JVM exits ...
java -Xmx256m -XX:StartFlightRecording=filename=swag-shop.jfr,settings=profile -jar target/quarkus-app/quarkus-run.jar

# ... or start and dump a recording on a running JVM
jcmd $(pgrep -f quarkus-run.jar) JFR.start name=demo settings=profile
jcmd $(pgrep -f quarkus-run.jar) JFR.dump name=demo filename=swag-shop.jfr
```

Open the file in JDK Mission Control, or print JMC's automated analysis in the terminal:

```shell
scripts/jfr-rules.sh swag-shop.jfr          # rules that score INFO or WARNING
scripts/jfr-rules.sh swag-shop.jfr --all    # every rule
```

`jfr-rules.sh` uses the analysis libraries of a local JMC installation (set `JMC_HOME` if JMC is not in
`/Applications`).

The shop writes a custom JFR event, **Swag Shop › Demo › Problem Toggled** (`com.demo.shop.ProblemToggled`), each
time a scenario changes, so a recording shows exactly when a problem started and stopped. The `quarkus-jfr`
extension adds an event per REST request.

## Configuration

Everything can be changed at runtime on the demo control page; **Reset to defaults** goes back to the
configured values. Set them in `src/main/resources/application.properties` or as environment variables.

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `demo.search.problem-enabled` | `DEMO_SEARCH_PROBLEM_ENABLED` | `false` | Run the naive search |
| `demo.search.load` | `DEMO_SEARCH_LOAD` | `medium` | `low`, `medium` or `high` (3, 10 or 32 searches/s) |
| `demo.pricing.problem-enabled` | `DEMO_PRICING_PROBLEM_ENABLED` | `false` | Run the leaking price cache |
| `demo.pricing.load` | `DEMO_PRICING_LOAD` | `medium` | 5, 15 or 30 product page views/s |
| `demo.checkout.problem-enabled` | `DEMO_CHECKOUT_PROBLEM_ENABLED` | `false` | Run the globally locked inventory |
| `demo.checkout.load` | `DEMO_CHECKOUT_LOAD` | `medium` | 15, 40 or 60 checkouts/s |
| `demo.traffic.enabled` | `DEMO_TRAFFIC_ENABLED` | `true` (`false` in dev and test) | Start the simulated shoppers |
| `demo.safety.leak-max-heap-ratio` | `DEMO_SAFETY_LEAK_MAX_HEAP_RATIO` | `0.7` | The leak stops growing at this fraction of the maximum heap, so the demo never runs out of memory |

## Running on OpenShift with Cryostat

Requires an OpenShift cluster with the Cryostat Operator, a Cryostat instance whose target namespaces include
the namespace you deploy to, and `oc` logged in.

```shell
scripts/openshift-deploy.sh cryostat-demo1
```

The script builds the application, builds the container image inside the cluster and deploys
[openshift/swag-shop.yaml](openshift/swag-shop.yaml). The pod carries the labels `cryostat.io/name` and
`cryostat.io/namespace`, so the Cryostat Operator injects the Cryostat Agent. The Agent registers the pod with
Cryostat and runs a smart trigger that starts a recording with the memory leak template when the heap stays more
than 35 % full for 20 seconds. The image adds [cryostat/SwagShopMemoryLeak.jfc](cryostat/SwagShopMemoryLeak.jfc)
(the Profiling template plus paths to GC roots) to the JDK's templates, so Cryostat, `jcmd` and the Agent can use
it by name. When the pod stops, the Agent uploads its recordings to Cryostat's archive.

`scripts/cryostat-setup.sh` creates the Cryostat automated rule that starts a continuous recording for every
Swag Shop pod. Remove everything the demo created with:

```shell
oc delete all,imagestream,buildconfig -l app.kubernetes.io/part-of=swag-shop-demo -n cryostat-demo1
```

## Project layout

```
src/main/java/com/demo/shop/
  catalog/      products
  search/       product search and the two edit distance implementations   (CPU hotspot)
  pricing/      price quotes and the two quote caches                       (memory leak)
  inventory/    stock and the two inventory implementations                 (lock contention)
  checkout/     orders
  api/          REST API of the shop
  demo/         scenario switches, configuration, simulated shoppers, JFR event, demo control API
src/main/resources/META-INF/resources/   web pages (Red Hat Design System web components)
openshift/                               OpenShift resources
scripts/                                 deployment, Cryostat setup and JFR analysis scripts
docs/DEMO.md                             demo script for the talk
```

The web pages use the Red Hat Design System (`@rhds/elements`, `@rhds/tokens`) and the Red Hat fonts, served from
[mvnpm](https://mvnpm.org) jars, so there is no JavaScript build. The product photos were generated for this
demo.

## Tests

```shell
./mvnw verify -DskipITs=false    # unit tests, API tests, and the API tests again against the packaged app
```
