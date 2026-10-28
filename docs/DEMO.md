# Demo script: JFR and Cryostat with the Swag Shop

Two demos for a 40-minute introductory session:

| Part | Time | Where | Story |
|---|---|---|---|
| Demo 1 | ~5 min | Laptop, JDK Mission Control | "JFR is built into the JVM: find a CPU hotspot and fix it" |
| Slides | | | "Now do that for pod 3 of 12 in OpenShift" → Cryostat |
| Demo 2 | 12–15 min | OpenShift, Cryostat | Discovery, lock contention, a memory leak captured automatically, evidence that survives the pod |

Everything below was rehearsed against the lab cluster (Cryostat 4.2, Cryostat Agent 0.7.0) and JMC 9.1.2.

---

## Before the session (30 minutes ahead)

### Laptop (demo 1)

```shell
./mvnw package -DskipTests
java -Xmx256m -jar target/quarkus-app/quarkus-run.jar
```

- [ ] <http://localhost:8080/demo.html> shows **Simulated shoppers are sending traffic** and all three scenarios **Fixed**.
- [ ] JDK Mission Control is open. Close old recordings.
- [ ] A terminal in the project folder, with these commands ready to paste:
  ```shell
  jcmd $(pgrep -f quarkus-run.jar) JFR.start name=demo settings=profile
  jcmd $(pgrep -f quarkus-run.jar) JFR.dump name=demo filename=recordings/live-cpu.jfr
  ```
- [ ] The IDE is open on `src/main/java/com/demo/shop/search/RecursiveEditDistance.java` and
  `IterativeEditDistance.java`.

### Cluster (demo 2)

```shell
oc login ...
scripts/build-push-container.sh                # only if the app changed (needs QUAY_USER and podman login)
scripts/openshift-deploy.sh cryostat-demo1     # applies the resources and restarts the pod
scripts/cryostat-setup.sh                      # creates the automated rule (once; safe to run again)
oc delete pod -n cryostat-demo1 -l app.kubernetes.io/name=swag-shop   # fresh JVM, empty heap, trigger re-armed
```

- [ ] Shop: `https://swag-shop-cryostat-demo1.apps.<cluster-domain>` and its `/demo.html`.
  Shoppers on, everything **Fixed**.
- [ ] Cryostat console: `https://cryostat-sample-cryostat.apps.<cluster-domain>`. The new `swag-shop-…` pod
  appears as a target about 15 seconds after it starts.
- [ ] `<cluster-domain>` is your cluster's apps domain; `oc get route -n cryostat-demo1 swag-shop` and
  `oc get route -n cryostat cryostat-sample` show the full host names.
- [ ] The automated rule `swag_shop_continuous` has started a recording on the pod (Recordings view).
- [ ] Optional: delete old archives from earlier rehearsals so the Archives view is clean.
- [ ] Browser zoom at 125–150% for the projector.

### Fallback recordings

`recordings/` holds verified recordings for every scenario (not in git). If anything fails live, open these
instead:

| File | Shows |
|---|---|
| `backup-cpu-hotspot-search.jfr` | Method Profiling WARNING on `RecursiveEditDistance.distance` |
| `backup-lock-contention-checkout.jfr` | Java Blocking WARNING (score 100) on the `SynchronizedInventory` monitor |
| `backup-memory-leak-pricing.jfr` | Heap Live Set Trend WARNING (score 100) with the leak candidate |
| `backup-all-fixed-baseline.jfr` | The same shop with all fixes: no warnings from the scenarios |

Check any recording without opening JMC: `scripts/jfr-rules.sh recordings/<file>.jfr`.

---

## Demo 1: JFR and JDK Mission Control (~5 min)

| Time | Do | Say |
|---|---|---|
| 0:00 | Show the shop: the product grid, a search for `hoody` (typos welcome). | "A normal Quarkus web shop. Simulated shoppers are using it right now." |
| 0:30 | Demo control: **Product search** → switch on, load **High**. Wait about 5 seconds, then search the shop for `hoodie` (any word works): the page hangs for 2–4 seconds and the timing turns red. | "Customers complain that search is slow. The CPU is busy, but where?" |
| 1:00 | Paste the `JFR.start` command. | "JFR is part of the JVM. No agent, no restart: we just ask the running JVM to record. The overhead is around 1 %." |
| 2:00 | Paste the `JFR.dump` command after about 60 seconds; open `recordings/live-cpu.jfr` in JMC. | |
| 2:15 | **Automated Analysis Results**: "Method Profiling" is red and names `RecursiveEditDistance.distance`. Open the **Method Profiling** page (or the flame graph) to show the deep recursion. | "JMC ran its analysis rules for us, and named the exact method." |
| 3:00 | IDE: `RecursiveEditDistance`. The textbook recursion calls itself three times per character, so the work explodes: each extra letter in the search word makes it about 2.5 times slower. Long searches from other shoppers hold all three search slots (`ProductSearchService.MAX_CONCURRENT_SEARCHES`), so even a short search waits in line. Show `IterativeEditDistance`. | "Correct code, exponential cost. The fix is the dynamic programming version." |
| 4:00 | Demo control: switch **Product search** off (fixed code). Search for `hoodie` again: a few milliseconds. | "In real life this is a new deployment; here a switch saves us the build." |
| 4:30 | Bridge to the slides: | "Now imagine this is pod 3 of 12 in OpenShift. No JMC attached, no shell, and the evidence disappears when the pod restarts." |

Also worth a mention: the recording contains the shop's own **Swag Shop › Demo › Problem Toggled** event, which shows
exactly when the problem was switched on, and Quarkus adds an event for every REST request.

---

## Demo 2: Cryostat on OpenShift (12–15 min)

### 1. Discovery and the always-on recording (~2 min)

| Do | Say |
|---|---|
| Show the deployment's pod labels `cryostat.io/name` and `cryostat.io/namespace` (OpenShift console or `openshift/swag-shop.yaml`). | "Two labels. The Cryostat Operator injects the Cryostat Agent into the pod." |
| Cryostat: **Topology**, then the swag-shop target; **Dashboard** with the JVM charts. | "Every Java pod in the namespace shows up by itself." |
| **Automated Rules**: `swag_shop_continuous`. **Recordings**: its continuous recording is already running. | "A rule says: every Swag Shop pod gets a continuous, low-overhead recording from the moment it starts." |

### 2. Lock contention: slow, but not busy (~3–4 min)

| Do | Say |
|---|---|
| Demo control: **Checkout** → switch on, load **High**. In the shop, place an order: it takes about a second. | "Checkout is slow." |
| Dashboard: CPU stays flat. Demo control shows checkout latency around 0.9 s while CPU is low. | "But nothing is busy. Metrics cannot tell us why." |
| After about 60 seconds: **Recordings** → the continuous recording → **Automated Analysis**. "Java Blocking" is red, with threads blocked for minutes on the `SynchronizedInventory` monitor. | "Threads are waiting for one lock." |
| Demo control → Checkout → **View code**: `public synchronized Reservation reserve`, with the slow warehouse call inside the lock. The **Fix** tab: per-product locks, slow call outside. | |
| Switch **Checkout** off. Latency drops to about 25 ms. | |

### 3. A memory leak, captured automatically (~5 min)

| Do | Say |
|---|---|
| Demo control: **Product prices** → switch on, load **High**. The heap chart climbs like a staircase; the card counts leaked price quotes. | "Product pages now leak memory. In production this ends with an OOMKilled pod." |
| Show the `CRYOSTAT_AGENT_SMART_TRIGGER_DEFINITIONS` variable in the deployment: `[HeapMemoryUsagePercent>0.35;TargetDuration>duration("20s")]~SwagShopMemoryLeak`. | "A smart trigger: when the heap stays more than 35 % full for 20 seconds, the Agent starts a recording by itself." |
| After 30–60 seconds a recording named `cryostat-smart-trigger-1` appears in **Recordings**. | "Nobody had to be watching." |
| Wait another 30–60 seconds, then open its **Automated Analysis**: "Heap Live Set Trend" is red (score about 97), and the explanation names the leak candidate held by `com.demo.shop.pricing.RequestQuoteCache.quotes`. | "The SwagShopMemoryLeak template records paths to GC roots, so the analysis can name the object that keeps the leak alive." |
| Demo control → Product prices → **View code**: the cache key contains the request id, so nothing is ever found or removed. The **Fix** tab: keyed by product, bounded, expiring. | |
| Delete the pod (OpenShift console, or `oc delete pod -n cryostat-demo1 -l app.kubernetes.io/name=swag-shop`). | "A restart 'fixes' it, and normally the evidence is gone." |
| **Archives**: the smart trigger recording is there, uploaded by the Agent as the JVM shut down. Its analysis still works. | "With Cryostat, the evidence survives the pod." |

### 4. Wrap-up (~1 min)

- JFR: always on, low overhead, built into the JVM.
- JMC: analysis that names methods, locks and leaking objects.
- Cryostat: brings JFR to containers and Kubernetes. It discovers JVMs, records automatically with rules and
  smart triggers, analyses recordings and keeps them after the pod is gone.

### Optional, if there is time: the CPU hotspot in Cryostat

Search **High**, start a recording with the **Profiling** template from **Recordings → Create**, wait 60 seconds,
and open its Automated Analysis: "Method Profiling" names `RecursiveEditDistance.distance`.

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
- **What is the overhead?** The Continuous template is designed for production (around 1 %). Profiling and
  memory-leak recordings cost more and are meant for minutes, not days.

---

## Reset between rehearsals

1. Demo control → **Reset to defaults** (everything fixed, shoppers on).
2. Delete the pod for a fresh JVM: `oc delete pod -n cryostat-demo1 -l app.kubernetes.io/name=swag-shop`.
3. In Cryostat, delete old archived recordings if you want a clean Archives view.

The smart trigger fires once per JVM, so delete the pod before rehearsing the memory leak part again.

## Troubleshooting

| Problem | Fix |
|---|---|
| The pod does not appear in Cryostat | The namespace must be in the Cryostat resource's `targetNamespaces`, and the pod needs the two `cryostat.io` labels. The Agent registers about 15 seconds after start-up. |
| No smart trigger recording | Is **Product prices** on and the load **High**? The heap must stay above 35 % for 20 seconds. Otherwise start a recording by hand with the **SwagShopMemoryLeak** template. |
| The leak stops growing | By design: it stops at 70 % of the heap (`demo.safety.leak-max-heap-ratio`) so the pod never runs out of memory. Switch the scenario off and on to clear it. |
| The shop page looks outdated after a redeploy | Reload the page. |
| A search with the problem on is not slow | Set the search load to **High** and wait a few seconds. Only high load sends more searches than the naive code can handle, so they queue for the three search slots; at medium load a short word such as `hoodie` still takes only about 30 ms. |
| Cryostat analysis shows "Method Profiling" only as INFO | Use **High** search load; at medium load the hot method uses less than one CPU. Keep the pod's CPU limit at 4: with 2 cores the pod is throttled, JFR takes far fewer samples and the rule stays at INFO. |

---

## Visual check before the talk (5 minutes)

Things that need a human eye, on the projector if possible:

- [ ] Shop and demo control page are readable at the projector's resolution, in the colour scheme you will use
  (the footer has a light/dark/system switch).
- [ ] JMC: open `recordings/backup-cpu-hotspot-search.jfr`; check the Automated Analysis page, the Method Profiling
  page and the flame graph.
- [ ] JMC: open `recordings/backup-memory-leak-pricing.jfr`; check Memory → Memory Leak (Old Object Sample) shows
  the reference chain to `RequestQuoteCache`.
- [ ] Cryostat: the Dashboard charts, the Recordings view and the Automated Analysis view of a recording, and
  the Archives view.
- [ ] Run through demo 2 once on the cluster and time it.
