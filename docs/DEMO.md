# Demo script: JFR and Cryostat with the Swag Shop

Two demos for a 40-minute introductory session:

| Part | Time | Where | Story |
|---|---|---|---|
| Demo 1 | ~5 min | Laptop: the search service, JDK Mission Control | "JFR is built into the JVM: find a CPU hotspot and fix it" |
| Slides | | | "Now do that for pod 3 of 12 in OpenShift" → Cryostat |
| Demo 2 | 12–15 min | OpenShift, Cryostat: the whole shop as seven services | Discovery, lock contention in the wrong service, a memory leak captured automatically, evidence that survives the pod |

Everything below was rehearsed against the lab cluster (Cryostat 4.2, Cryostat Agent 0.7.0) and JMC 9.1.2.

---

## Before the session (30 minutes ahead)

### Laptop (demo 1)

Demo 1 uses only the search service, on its own: one JVM with its own copy of the product list, and a load
script instead of the web shop.

```shell
./mvnw -pl search package -DskipTests
java -Dquarkus.profile=standalone -jar search/target/quarkus-app/quarkus-run.jar
```

- [ ] Terminal 1 (the service) shows **Loaded 8 products from the bundled products.json** and **Listening on:
  http://0.0.0.0:8082**.
- [ ] Terminal 2 (big font, the audience watches it): `java scripts/SearchLoad.java high` is running and prints a
  line every second, with searches answered in a few milliseconds.
- [ ] JDK Mission Control is open. Close old recordings.
- [ ] Terminal 3 in the project folder, with these commands ready to paste:
  ```shell
  scripts/search-problem.sh on
  jcmd $(pgrep -f search/target/quarkus-app) JFR.start name=demo settings=profile
  jcmd $(pgrep -f search/target/quarkus-app) JFR.dump name=demo filename=recordings/live-cpu.jfr
  scripts/search-problem.sh off
  ```
- [ ] The IDE is open on `search/src/main/java/com/demo/shop/search/RecursiveEditDistance.java` and
  `IterativeEditDistance.java`.

### Cluster (demo 2)

Demo 2 uses the whole shop: seven services in the project `swag-shop`.

```shell
oc login ...
scripts/build-push-container.sh                # only if a service changed (needs QUAY_USER and podman login)
scripts/openshift-deploy.sh swag-shop     # applies the resources and restarts the pods that exist
scripts/cryostat-setup.sh                      # creates the automated rule (once; safe to run again)
oc delete pod -n swag-shop -l app.kubernetes.io/part-of=swag-shop-demo   # fresh JVMs, empty heap, trigger re-armed
```

- [ ] Shop: `https://swag-shop-frontend-swag-shop.apps.<cluster-domain>` and its `/demo.html`.
  Shoppers on, everything **Fixed**, every card shows a JVM line.
- [ ] Cryostat console: `https://cryostat-sample-cryostat.apps.<cluster-domain>`. The seven `swag-shop-…` pods
  appear as targets about 15 seconds after they start.
- [ ] `<cluster-domain>` is your cluster's apps domain; `oc get route -n swag-shop swag-shop-frontend` and
  `oc get route -n cryostat cryostat-sample` show the full host names.
- [ ] The automated rule `swag_shop_continuous` has started a recording on every pod (Recordings view).
- [ ] OpenShift console, **Topology** of `swag-shop`: the seven services, with arrows for the calls between
  them.
- [ ] Optional: delete old archives from earlier rehearsals so the Archives view is clean.
- [ ] Browser zoom at 125–150% for the projector.

### Fallback recordings

`recordings/` holds verified recordings for every scenario (not in git). If anything fails live, open these
instead:

| File | Shows |
|---|---|
| `backup-demo1-search-standalone.jfr` | Demo 1: Method Profiling WARNING on `RecursiveEditDistance.distance` (Profiling template, laptop) |
| `backup-demo2-lock-contention-inventory.jfr` | Demo 2: Java Blocking WARNING (score 99) on the `SynchronizedInventory` monitor (inventory pod, Continuous template) |
| `backup-demo2-lock-contention-order.jfr` | Demo 2: the order pod at the same time: no warnings from the scenario |
| `backup-demo2-memory-leak-pricing.jfr` | Demo 2: Heap Live Set Trend WARNING (score 100), old object samples held by `RequestQuoteCache` (pricing pod, archived by the Agent) |
| `backup-demo2-cpu-hotspot-search.jfr` | Demo 2, optional part: Method Profiling WARNING (search pod, Continuous template) |

The older `backup-*.jfr` recordings without `demo1`/`demo2` in the name come from the single-application shop on
the `main` branch.

Check any recording without opening JMC: `scripts/jfr-rules.sh recordings/<file>.jfr`.

---

## Demo 1: JFR and JDK Mission Control (~5 min)

| Time | Do | Say |
|---|---|---|
| 0:00 | Terminal 2: the load script sends 30 searches a second, with typos (`hoody`, `backpak`) and long words (`waterbottle`). Each answer takes a few milliseconds. | "This is the search service of a web shop, a small Quarkus app. These are simulated shoppers using the search box." |
| 0:30 | Terminal 3: `scripts/search-problem.sh on`. Within about 5 seconds the lines turn red: 2–4 seconds per search, and 40 searches waiting. | "Customers complain that search is slow. The CPU is busy, but where?" |
| 1:00 | Paste the `JFR.start` command. | "JFR is part of the JVM. No agent, no restart: we just ask the running JVM to record. The overhead is around 1 %." |
| 2:00 | Paste the `JFR.dump` command after about 60 seconds (at least 45: the rule below looks at 30-second windows); open `recordings/live-cpu.jfr` in JMC. | |
| 2:15 | **Automated Analysis Results**: "Method Profiling" is red and names `RecursiveEditDistance.distance`. Open the **Method Profiling** page (or the flame graph) to show the deep recursion. | "JMC ran its analysis rules for us, and named the exact method." |
| 3:00 | IDE: `RecursiveEditDistance`. The textbook recursion calls itself three times per character, so the work explodes: each extra letter in the search word makes it about 2.5 times slower. Long searches hold all three search slots (`ProductSearchService.MAX_CONCURRENT_SEARCHES`), so even a short search waits in line. Show `IterativeEditDistance`. | "Correct code, exponential cost. The fix is the dynamic programming version." |
| 4:00 | Terminal 3: `scripts/search-problem.sh off`. Within about 5 seconds the lines are back to milliseconds. | "In real life this is a new deployment; here a switch saves us the build." |
| 4:30 | Bridge to the slides: | "Now imagine this is one of a dozen services in OpenShift, each with several pods. No JMC attached, no shell, and the evidence disappears when the pod restarts." |

Also worth a mention: the recording contains the service's own **Swag Shop › Demo › Problem Toggled** event, which
shows exactly when the problem was switched on and by what (**Changed By**: `search-problem.sh`), and Quarkus adds an
event for every REST request.

---

## Demo 2: Cryostat on OpenShift (12–15 min)

### 1. Discovery and the always-on recording (~2 min)

| Do | Say |
|---|---|
| OpenShift console, **Topology** of `swag-shop`: frontend, catalog, search, pricing, inventory, order and the load generator. | "The same shop, now as it would run in production: seven services, seven JVMs." |
| Show the pod labels `cryostat.io/name` and `cryostat.io/namespace` (a deployment's YAML, or `openshift/inventory.yaml`). | "Two labels. The Cryostat Operator injects the Cryostat Agent into each pod." |
| Cryostat: **Topology**, the seven `swag-shop-…` targets; **Dashboard** of one of them. | "Every Java pod in the namespace shows up by itself." |
| **Automated Rules**: `swag_shop_continuous`. **Recordings** of any pod: its continuous recording is already running. | "A rule says: every Swag Shop pod gets a continuous, low-overhead recording from the moment it starts." |

### 2. Lock contention: slow, but in which service? (~4 min)

| Do | Say |
|---|---|
| Demo control: **Checkout** → switch on, load **High**. Wait about 5 seconds, then place an order in the shop: it hangs for about 2.5 seconds. | "Checkout is slow." |
| The Checkout card: 2.5 s per order, and its JVM line shows the **order** service, the one the shop calls for checkout: CPU about 0.1 cores, heap low. | "The order service is slow, but it is not doing anything. Metrics cannot tell us why." |
| Cryostat: the **order** pod's continuous recording → **Automated Analysis**. "REST response duration" is red (its requests are slow), but nothing about locks or CPU. | "JFR in the order service sees slow requests, and threads that wait for an answer." |
| The **inventory** pod's continuous recording → **Automated Analysis**: "Java Blocking" is red (score 100), with threads blocked for minutes on the `SynchronizedInventory` monitor. | "Order waits for inventory, and inside inventory the threads wait for one lock. Same tool, every service: that is how you find the service that is really at fault." |
| Demo control → Checkout → **View code**: `public synchronized Reservation reserve` in `inventory/…`, with the slow warehouse call inside the lock. The **Fix** tab: per-product locks, slow call outside. | |
| Switch **Checkout** off. Orders drop to about 70 ms (one warehouse call). | |

### 3. A memory leak, captured automatically (~5 min)

| Do | Say |
|---|---|
| Demo control: **Product prices** → switch on, load **High**. The pricing card counts leaked price quotes, and the heap on its JVM line climbs and turns red. | "Product pages now leak memory in the pricing service. In production this ends with an OOMKilled pod." |
| Show the `CRYOSTAT_AGENT_SMART_TRIGGER_DEFINITIONS` variable in the **pricing** deployment: `[HeapMemoryUsagePercent>0.35;TargetDuration>duration("20s")]~SwagShopMemoryLeak`. | "A smart trigger: when the heap stays more than 35 % full for 20 seconds, the Agent starts a recording by itself." |
| After about 2 minutes (the heap passes 35 % after about 50 seconds, then has to stay there for 20) a recording named `cryostat-smart-trigger-…` appears in the pricing pod's **Recordings**. | "Nobody had to be watching." |
| About a minute later, open its **Automated Analysis**: "Heap Live Set Trend" is red (score above 90), and the explanation names the leak candidate held by `com.demo.shop.pricing.RequestQuoteCache.quotes`. | "The SwagShopMemoryLeak template records paths to GC roots, so the analysis can name the object that keeps the leak alive." |
| Demo control → Product prices → **View code**: the cache key contains the request id, so nothing is ever found or removed. The **Fix** tab: keyed by product, bounded, expiring. | |
| Delete the pricing pod (OpenShift console, or `oc delete pod -n swag-shop -l app.kubernetes.io/name=swag-shop-pricing`). | "A restart 'fixes' it, and normally the evidence is gone." |
| **Archives**: `swag-shop-pricing-…_unknown_….jfr`, uploaded by the Agent as the JVM shut down. Its analysis still shows "Heap Live Set Trend" red, and it still contains the **Problem Toggled** event. | "With Cryostat, the evidence survives the pod." |

### 4. Wrap-up (~1 min)

- JFR: always on, low overhead, built into the JVM.
- JMC: analysis that names methods, locks and leaking objects.
- Cryostat: brings JFR to containers and Kubernetes. It discovers JVMs, records automatically with rules and
  smart triggers, analyses recordings and keeps them after the pod is gone. With many services, it shows which
  JVM has the problem, not just which one is slow.

### Optional, if there is time: the CPU hotspot in Cryostat

Demo control: **Product search** → switch on, load **High**. The search card's JVM line shows about 3 busy cores.
After about 60 seconds, open the search pod's continuous recording → **Automated Analysis**: "Method Profiling"
names `RecursiveEditDistance.distance`. The Continuous template samples often enough; no extra recording is needed.

---

## Things the audience may ask about

- **"PID-1 Rule" and sometimes "Inverted Parallelism" show warnings in Cryostat's analysis.** They also appear in
  recordings of the shop with every scenario fixed, so they are not related to the scenarios. PID-1 notes that the
  JVM runs as process 1 in the container, which is normal for this image.
- **"REST response duration"** is a rule that reads the Quarkus REST events. It turns red whenever requests are
  slow, so it supports the scenario you are showing (or reports slow requests left over from the previous one).
- **Why not a CPU-based smart trigger?** Cryostat Agent 0.7.0 reads `ProcessCpuLoad` as 0.0 even when the CPU is
  saturated (verified in this cluster), so a CPU trigger never fires. The heap trigger is reliable.
- **Why a custom template?** The built-in Continuous and Profiling templates do not record paths to GC roots.
  Without them, JMC's "Heap Live Set Trend" rule scores a clear leak as OK. `cryostat/SwagShopMemoryLeak.jfc` is the
  Profiling template plus GC-root paths; the image copies it into the JDK, so JFR, Cryostat and the Agent can
  use it by name.
- **Why does a short search hang when only long searches are expensive?** Searches use a lot of CPU, so the shop
  runs at most three at once and queues the rest, which protects checkout and product pages. With the naive code at
  high load, slow searches from other shoppers keep all three slots busy, and every search waits in line behind
  them. JFR records the waiting as thread park events in `Semaphore.acquire`.
- **Why does every order wait, not just some?** Checkout works the same way: at most eight orders are processed
  at once, first come, first served. With the global lock, those eight crawl through the 60 ms warehouse call one
  at a time, so every new order waits in line for about 2.5 seconds. Without that limit, waiting for a
  `synchronized` lock is luck: Java does not hand it over in arrival order, so half the orders would finish at
  once and the other half would wait twice as long.
- **Why does the order service look calm, when its eight checkout slots are full?** Its threads wait for the
  inventory service to answer, or for a free slot (a `Semaphore`). JFR records that as socket reads and thread
  parks, not as lock contention, and no CPU is used. The lock everybody waits for is in inventory, so that is
  where "Java Blocking" turns red.
- **Why does the Checkout card show the order service's JVM?** Each card shows the JVM that shoppers call for that
  feature. For checkout that is the order service, which is the point: the slow service is not the one with the
  problem.
- **What is the overhead?** The Continuous template is designed for production (around 1 %). Profiling and
  memory-leak recordings cost more and are meant for minutes, not days.

---

## Reset between rehearsals

1. Demo control → **Reset to defaults** (everything fixed, shoppers on).
2. Delete the pods for fresh JVMs: `oc delete pod -n swag-shop -l app.kubernetes.io/part-of=swag-shop-demo`.
3. In Cryostat, delete old archived recordings if you want a clean Archives view.

The smart trigger fires once per JVM, so delete at least the pricing pod before rehearsing the memory leak part
again.

## Troubleshooting

| Problem | Fix |
|---|---|
| The pod does not appear in Cryostat | The namespace must be in the Cryostat resource's `targetNamespaces`, and the pod needs the two `cryostat.io` labels. The Agent registers about 15 seconds after start-up. |
| No smart trigger recording | Is **Product prices** on and the load **High**? The heap must stay above 35 % for 20 seconds, which takes about 2 minutes. The trigger fires once per JVM: delete the pricing pod after a rehearsal. Otherwise start a recording by hand with the **SwagShopMemoryLeak** template. |
| The leak stops growing | By design: it stops at 70 % of the heap (`pricing.leak-max-heap-ratio`) so the pod never runs out of memory. Switch the scenario off and on to clear it. |
| A continuous recording is shorter than expected | Cryostat sometimes restarts the rule's recordings, for example when a pod of the shop goes away; the earlier part is in **Archives** (`…_auto_swag_shop_continuous_….jfr`). Do the lock contention part before deleting the pricing pod. |
| The demo control page shows "–" on a JVM line | That service did not answer within 1.5 seconds, for example while its pod restarts. It comes back by itself. |
| Pods stay Pending after a deploy | The lab cluster has a single node whose CPU is almost fully reserved. Keep the CPU requests in `openshift/*.yaml` small (50m, 100m for search); the limits decide how much a service may use. |
| The shop page looks outdated after a redeploy | Reload the page. |
| An order with the checkout problem on is not slow | Set the checkout load to **High** and wait a few seconds. At medium load the lock keeps up, and an order takes well under a second. |
| A search with the problem on is not slow | Use high load (`java scripts/SearchLoad.java high`, or **High** on the demo control page) and wait a few seconds. Only high load sends more searches than the naive code can handle, so they queue for the three search slots; at medium load a short word such as `hoodie` still takes only about 30 ms. |
| JMC or Cryostat analysis shows "Method Profiling" only as INFO | Use high search load; at medium load the hot method uses less than one CPU. The recording needs at least 45 seconds with the problem on: the rule looks at 30-second windows. Keep the search pod's CPU limit at 4: with 2 cores the pod is throttled, JFR takes far fewer samples and the rule stays at INFO. |

---

## Visual check before the talk (5 minutes)

Things that need a human eye, on the projector if possible:

- [ ] Shop and demo control page are readable at the projector's resolution, in the colour scheme you will use
  (the footer has a light/dark/system switch).
- [ ] JMC: open `recordings/backup-demo1-search-standalone.jfr`; check the Automated Analysis page, the Method
  Profiling page and the flame graph.
- [ ] JMC: open `recordings/backup-demo2-memory-leak-pricing.jfr`; check Memory → Memory Leak (Old Object Sample)
  shows the reference chain to `RequestQuoteCache`.
- [ ] Cryostat: the order pod's Automated Analysis during the checkout problem shows "REST response duration" but
  no "Java Blocking", and the inventory pod's shows "Java Blocking".
- [ ] Cryostat: the Dashboard charts, the Recordings view and the Automated Analysis view of a recording, and
  the Archives view.
- [ ] OpenShift Topology view of `swag-shop`: the arrows between the services are readable.
- [ ] Run through demo 2 once on the cluster and time it.
