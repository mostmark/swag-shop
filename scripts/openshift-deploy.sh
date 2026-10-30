#!/usr/bin/env bash
# Deploys the Swag Shop services from quay.io to OpenShift. Build and push the images first with
# scripts/build-push-container.sh.
#
#   scripts/openshift-deploy.sh [namespace]      (default namespace: swag-shop)
#
# The namespace must be one that Cryostat watches (the Cryostat resource's targetNamespaces), and you must be
# logged in with oc. Set QUAY_USER to deploy the images from quay.io/$QUAY_USER instead of the ones in
# openshift/*.yaml.
set -euo pipefail

NAMESPACE="${1:-swag-shop}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVICES="catalog search pricing inventory order frontend load-generator"

# Deployments that exist already are restarted below, so they pull the latest images; new ones pull them anyway
existing=""
for service in $SERVICES; do
  if oc get "deployment/swag-shop-$service" -n "$NAMESPACE" >/dev/null 2>&1; then
    existing="$existing $service"
  fi
done

echo "==> Applying the OpenShift resources to $NAMESPACE"
oc apply -n "$NAMESPACE" -f "$ROOT/openshift/"
if [[ -n "${QUAY_USER:-}" ]]; then
  for service in $SERVICES; do
    oc set image -n "$NAMESPACE" "deployment/swag-shop-$service" "$service=quay.io/$QUAY_USER/swag-shop-$service:latest"
  done
fi

if [[ -n "$existing" ]]; then
  echo "==> Restarting the pods so they pull the latest images"
  for service in $existing; do
    oc rollout restart "deployment/swag-shop-$service" -n "$NAMESPACE"
  done
fi
for service in $SERVICES; do
  oc rollout status "deployment/swag-shop-$service" -n "$NAMESPACE" --timeout=300s
done

echo "==> Swag Shop is running at https://$(oc get route swag-shop-frontend -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
