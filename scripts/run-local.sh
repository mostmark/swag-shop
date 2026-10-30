#!/usr/bin/env bash
# Builds and starts all Swag Shop services on this machine, each in a JVM of its own. Ctrl+C stops them all.
#
#   scripts/run-local.sh              build, then start
#   scripts/run-local.sh --no-build   start what was built last
#
# The shop is at http://localhost:8080 and the demo control page at http://localhost:8080/demo.html. Each
# service logs to target/local/<service>.log. Attach JMC to a service by its name (quarkus-run.jar in its
# folder), or use jcmd, e.g. jcmd $(pgrep -f inventory/target/quarkus-app) JFR.start
set -euo pipefail
cd "$(dirname "$0")/.."

# Service, then the JVM options it runs with. The heap sizes are those of the OpenShift deployment; pricing's
# small heap makes the memory leak visible within a couple of minutes.
SERVICES=(
  "catalog   -Xmx128m"
  "search    -Xmx256m"
  "pricing   -Xms128m -Xmx384m"
  "inventory -Xmx256m"
  "order     -Xmx256m"
  "frontend  -Xmx256m"
  "load-generator -Xmx256m"
)
LOGS=target/local

if [ "${1:-}" != "--no-build" ]; then
  echo "==> Building"
  ./mvnw -B -q package -DskipTests
fi

mkdir -p "$LOGS"
pids=()
stop() {
  echo
  echo "==> Stopping"
  kill "${pids[@]}" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap stop EXIT
trap 'exit 0' INT TERM

for entry in "${SERVICES[@]}"; do
  read -r service options <<<"$entry"
  # shellcheck disable=SC2086
  java $options -jar "$service/target/quarkus-app/quarkus-run.jar" >"$LOGS/$service.log" 2>&1 &
  pids+=($!)
  echo "==> Started $service (pid $!), log: $LOGS/$service.log"
done

echo "==> Waiting for the shop"
for _ in $(seq 1 60); do
  if curl -sf http://localhost:8080/api/demo/settings >/dev/null; then
    echo "==> Swag Shop is running at http://localhost:8080 (demo control: http://localhost:8080/demo.html)"
    echo "    Press Ctrl+C to stop all services."
    wait
    exit 0
  fi
  sleep 1
done
echo "The shop did not start within a minute; see the logs in $LOGS" >&2
exit 1
