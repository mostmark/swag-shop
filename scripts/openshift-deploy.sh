#!/usr/bin/env bash
# Deploys the Swag Shop image from quay.io to OpenShift. Build and push the image first with
# scripts/build-push-container.sh.
#
#   scripts/openshift-deploy.sh [namespace]      (default namespace: cryostat-demo1)
#
# The namespace must be one that Cryostat watches (the Cryostat resource's targetNamespaces), and you must be
# logged in with oc. Set QUAY_USER to deploy quay.io/$QUAY_USER/swag-shop:latest instead of the image in
# openshift/swag-shop.yaml.
set -euo pipefail

NAMESPACE="${1:-cryostat-demo1}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Applying the OpenShift resources to $NAMESPACE"
oc apply -n "$NAMESPACE" -f "$ROOT/openshift/swag-shop.yaml"
if [[ -n "${QUAY_USER:-}" ]]; then
  oc set image -n "$NAMESPACE" deployment/swag-shop "swag-shop=quay.io/$QUAY_USER/swag-shop:latest"
fi

echo "==> Restarting the pod so it pulls the latest image"
oc rollout restart deployment/swag-shop -n "$NAMESPACE"
oc rollout status deployment/swag-shop -n "$NAMESPACE" --timeout=300s

echo "==> Swag Shop is running at https://$(oc get route swag-shop -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
