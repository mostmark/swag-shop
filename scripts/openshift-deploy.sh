#!/usr/bin/env bash
# Builds the Swag Shop, builds its container image inside OpenShift and deploys it.
#
#   scripts/openshift-deploy.sh [namespace]      (default namespace: cryostat-demo1)
#
# The namespace must be one that Cryostat watches (the Cryostat resource's targetNamespaces), and you must be
# logged in with oc. The image is built in the cluster, so it works from any laptop architecture.
set -euo pipefail

NAMESPACE="${1:-cryostat-demo1}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Building the application"
"$ROOT/mvnw" -B -q -f "$ROOT/pom.xml" package -DskipTests

echo "==> Applying the OpenShift resources to $NAMESPACE"
oc apply -n "$NAMESPACE" -f "$ROOT/openshift/swag-shop.yaml"

echo "==> Building the container image in the cluster"
CONTEXT="$(mktemp -d)"
trap 'rm -rf "$CONTEXT"' EXIT
mkdir -p "$CONTEXT/src/main/docker" "$CONTEXT/target" "$CONTEXT/cryostat"
cp "$ROOT/src/main/docker/Dockerfile.jvm" "$CONTEXT/src/main/docker/"
cp "$ROOT/cryostat/SwagShopMemoryLeak.jfc" "$CONTEXT/cryostat/"
cp -R "$ROOT/target/quarkus-app" "$CONTEXT/target/"
oc start-build swag-shop -n "$NAMESPACE" --from-dir="$CONTEXT" --follow

echo "==> Waiting for a pod that runs the new image"
IMAGE="$(oc get istag swag-shop:latest -n "$NAMESPACE" -o jsonpath='{.image.metadata.name}')"
for _ in $(seq 1 100); do
  READY="$(oc get pods -n "$NAMESPACE" -l app.kubernetes.io/name=swag-shop -o jsonpath='{range .items[*]}{.status.containerStatuses[0].imageID} {.status.containerStatuses[0].ready}{"\n"}{end}')"
  if echo "$READY" | grep -q "$IMAGE true" && [ "$(echo "$READY" | grep -c .)" = "1" ]; then
    break
  fi
  sleep 3
done
oc rollout status deployment/swag-shop -n "$NAMESPACE" --timeout=300s

echo "==> Swag Shop is running at https://$(oc get route swag-shop -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
