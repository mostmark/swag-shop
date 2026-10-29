# Swag Shop: a JFR and Cryostat demo application

A small web shop that sells conference swag, built as Quarkus services with the Red Hat Design System. It has
three performance problems that can be switched on and off while it runs. Each problem is real code in a
realistic place, so JDK Flight Recorder (JFR), JDK Mission Control (JMC) and Cryostat find it the way they would
in production: in one service out of several.

The demo script for the talk is in [docs/DEMO.md](docs/DEMO.md).

## The services

| Service | Port (local) | What it does | Calls |
|---|---|---|---|
| `frontend` | 8080 | Web pages and the `/api` they call; passes every request on to the services without blocking a thread, and combines their answers for the product page and the demo control page | all the others |
| `catalog` | 8081 | The product list (`products.json`) | |
| `search` | 8082 | Typo-tolerant product search: **CPU hotspot** | catalog, at start-up |
| `pricing` | 8083 | Prices from two years of price history: **memory leak** | catalog, at start-up |
| `inventory` | 8084 | Stock levels and reservations: **lock contention** | catalog, at start-up |
| `order` | 8085 | Checkout: reserves every order line in inventory | inventory; catalog at start-up |
| `load-generator` | 8086 | Simulated shoppers that use the shop through the frontend, and the response times they see | frontend; catalog at start-up |

The services that need the products fetch them from the catalog when they start, and retry for two minutes while
the catalog starts too. With the `standalone` profile they use their bundled copy of `products.json` instead, so
a service can run on its own; demo 1 runs the search service like that.

The cart lives in the browser.

## The scenarios

| Feature | Problem | Service | Naive code (problem on) | Fixed code (problem off) | What JFR shows |
|---|---|---|---|---|---|
| Product search | CPU hotspot | search | `RecursiveEditDistance`: textbook recursive edit distance, exponential work | `IterativeEditDistance`: dynamic programming | "Method Profiling" names `RecursiveEditDistance.distance`; CPU load rises |
| Product prices | Memory leak | pricing | `RequestQuoteCache`: cache keyed on the request id, never hits, never evicts | `ProductQuoteCache`: keyed on the product, bounded, expires | Heap after GC climbs like a staircase; with paths to GC roots, "Heap Live Set Trend" names the leak candidate held by `RequestQuoteCache.quotes` |
| Checkout | Lock contention | inventory | `SynchronizedInventory`: one global `synchronized` lock held during a slow warehouse call | `PerProductLockInventory`: per-product locks, slow call outside the lock | In inventory's recording, "Java Blocking" names the `SynchronizedInventory` monitor. Orders are slow in the order service, whose recording shows nothing: it only waits |

Both implementations of each feature return the same results (the tests check this); only their performance
differs. Switching a scenario to the fixed code stands in for deploying the fix.

The **load generator** sends a steady stream of requests to all three features, so problems show up without
anyone clicking around. Each feature's load can be set to low, medium or high. The **demo control page**
(`/demo.html`) switches the problems and the load, and shows the response times and, on each card, the heap, CPU
and threads of the JVM that the card's shoppers call.

## Running locally

Requires Java 21.

```shell
scripts/run-local.sh              # build, then start all seven services; Ctrl+C stops them
scripts/run-local.sh --no-build   # start what was built last
```

Open <http://localhost:8080> for the shop and <http://localhost:8080/demo.html> for the demo control page. Each
service logs to `target/local/<service>.log`.

A single service runs on its own with the standalone profile, for example the search service for demo 1:

```shell
./mvnw -pl search package -DskipTests
java -Dquarkus.profile=standalone -jar search/target/quarkus-app/quarkus-run.jar
java scripts/SearchLoad.java high      # simulated shoppers for search alone; type low, medium or high to change
scripts/search-problem.sh on           # or off
```

### Recording with JFR

Each service is a JVM of its own; pick it by its folder:

```shell
jcmd $(pgrep -f search/target/quarkus-app) JFR.start name=demo settings=profile
jcmd $(pgrep -f search/target/quarkus-app) JFR.dump name=demo filename=search.jfr
```

Open the file in JDK Mission Control, or print JMC's automated analysis in the terminal:

```shell
scripts/jfr-rules.sh search.jfr          # rules that score INFO or WARNING
scripts/jfr-rules.sh search.jfr --all    # every rule
```

`jfr-rules.sh` uses the analysis libraries of a local JMC installation (set `JMC_HOME` if JMC is not in
`/Applications`).

The services with a scenario write a custom JFR event, **Swag Shop › Demo › Problem Toggled**
(`com.demo.shop.ProblemToggled`), each time their problem is switched, with who switched it (the demo control page
or `search-problem.sh`), so a recording shows exactly when a problem started and stopped. The `quarkus-jfr`
extension adds an event per REST request.

## Configuration

Everything can be changed at runtime on the demo control page; **Reset to defaults** goes back to the configured
values. Set them in each service's `src/main/resources/application.properties` or as environment variables.

| Service | Property | Environment variable | Default | Meaning |
|---|---|---|---|---|
| search, pricing, inventory | `demo.scenario.problem-enabled` | `DEMO_SCENARIO_PROBLEM_ENABLED` | `false` | Run the naive code |
| load-generator | `load.search` | `LOAD_SEARCH` | `medium` | `low`, `medium` or `high` (2, 6 or 30 searches/s) |
| load-generator | `load.pricing` | `LOAD_PRICING` | `medium` | 5, 15 or 30 product page views/s |
| load-generator | `load.checkout` | `LOAD_CHECKOUT` | `medium` | 3, 8 or 30 checkouts/s |
| load-generator | `load.traffic-enabled` | `LOAD_TRAFFIC_ENABLED` | `true` (`false` in dev and test) | Start the simulated shoppers |
| pricing | `pricing.leak-max-heap-ratio` | `PRICING_LEAK_MAX_HEAP_RATIO` | `0.7` | The leak stops growing at this fraction of the maximum heap, so the demo never runs out of memory |
| all but catalog and frontend | `catalog.url` | `CATALOG_URL` | `http://localhost:8081` | Where the products are fetched at start-up |
| frontend | `quarkus.rest-client.<service>.url` | `QUARKUS_REST_CLIENT_<SERVICE>_URL` | `http://localhost:808x` | The services the frontend calls |

## Running on OpenShift with Cryostat

Requires an OpenShift cluster with the Cryostat Operator, a Cryostat instance whose target namespaces include
the namespace you deploy to, and `oc` logged in.

```shell
export QUAY_USER=mostmark
scripts/build-push-container.sh               # build the services and an image per service, push them to quay.io
scripts/build-push-container.sh search        # ... or only the services named
scripts/openshift-deploy.sh cryostat-demo2    # deploy them and restart the pods to pull the new images
```

`build-push-container.sh` builds every service's image from [docker/Dockerfile.jvm](docker/Dockerfile.jvm) for
`linux/amd64` and `linux/arm64` with podman, and pushes each as one manifest list to
`quay.io/$QUAY_USER/swag-shop-<service>:latest` (log in first with `podman login quay.io`; the repositories must be
public, or the namespace needs a pull secret). In their containers, all services listen on port 8080.

`openshift-deploy.sh` applies the files in [openshift/](openshift/), one per service, which use
`quay.io/mostmark/swag-shop-<service>:latest` (with `QUAY_USER` set, the script switches to your repositories).
Only the frontend has a route. Every pod carries the labels `cryostat.io/name` and `cryostat.io/namespace`, so the
Cryostat Operator injects the Cryostat Agent, which registers the pod with Cryostat. In the pricing pod, the
Agent also runs a smart trigger that starts a recording with the memory leak template when the heap stays more
than 35 % full for 20 seconds. The images add [cryostat/SwagShopMemoryLeak.jfc](cryostat/SwagShopMemoryLeak.jfc)
(the Profiling template plus paths to GC roots) to the JDK's templates, so Cryostat, `jcmd` and the Agent can use
it by name. When a pod stops, the Agent uploads its recordings to Cryostat's archive.

`scripts/cryostat-setup.sh` creates the Cryostat automated rule that starts a continuous recording for every
Swag Shop pod. Remove everything the demo created with:

```shell
oc delete all -l app.kubernetes.io/part-of=swag-shop-demo -n cryostat-demo2
```

## Project layout

Each service is a Quarkus application in a folder of its own; the root `pom.xml` builds them all.

```
frontend/         web pages (Red Hat Design System web components), the shop API and the demo control API
catalog/          products.json and the product API
search/           product search and the two edit distance implementations   (CPU hotspot)
pricing/          price quotes and the two quote caches                       (memory leak)
inventory/        stock and the two inventory implementations                 (lock contention)
order/            checkout
load-generator/   simulated shoppers and their response times
docker/           the Dockerfile of every service
openshift/        OpenShift resources, one file per service
scripts/          local run, demo 1, image build and push, deployment, Cryostat setup and JFR analysis scripts
docs/DEMO.md      demo script for the talk
```

Each service with a scenario has its own copy of the demo classes (`com.demo.shop.demo`: problem switch, JFR
event, source code and JVM endpoints), and the services that fetch products have their own copy of the catalog
client and `products.json`. The search service holds the reference copy; `scripts/check-copies.sh` checks that
the others are still identical.

The web pages use the Red Hat Design System (`@rhds/elements`, `@rhds/tokens`) and the Red Hat fonts, served from
[mvnpm](https://mvnpm.org) jars, so there is no JavaScript build. The product photos were generated for this
demo.

## Tests

```shell
./mvnw test                  # every service's tests
scripts/check-copies.sh      # the copied demo classes are still identical
```

The frontend's and the load generator's tests run against fake services; the other services' API tests use the
bundled products.
