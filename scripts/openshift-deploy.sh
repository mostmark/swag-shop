#!/usr/bin/env bash
# Deploys the Swag Shop services to OpenShift with the manifests from the gitops repository
# (https://github.com/mostmark/swag-shop-gitops), and restarts the pods that were running so they pull the latest
# images. Build and push the images first with scripts/build-push-container.sh.
#
#   scripts/openshift-deploy.sh [namespace]      (default namespace: swag-shop)
#
# The namespace must be one that Cryostat watches (the Cryostat resource's targetNamespaces), and you must be
# logged in with oc. Set SWAG_SHOP_MANIFESTS to deploy another copy of swag-shop.yaml, a URL or a local file, for
# example a fork that uses your own images. GitHub may serve swag-shop.yaml up to five minutes old after a push.
set -euo pipefail

NAMESPACE="${1:-swag-shop}"
MANIFESTS="${SWAG_SHOP_MANIFESTS:-https://raw.githubusercontent.com/mostmark/swag-shop-gitops/main/swag-shop.yaml}"
SERVICES="catalog search pricing inventory order frontend load-generator"

# Deployments that exist already are restarted below, so they pull the latest images; new ones pull them anyway
existing=""
for service in $SERVICES; do
  if oc get "deployment/swag-shop-$service" -n "$NAMESPACE" >/dev/null 2>&1; then
    existing="$existing $service"
  fi
done

echo "==> Applying $MANIFESTS to $NAMESPACE"
oc apply -n "$NAMESPACE" -f "$MANIFESTS"

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
