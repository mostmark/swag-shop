#!/usr/bin/env bash
# Creates the Cryostat automated rule for the demo: every Swag Shop pod gets a continuous JFR recording as soon as
# Cryostat discovers it, and Cryostat analyses it automatically.
#
#   scripts/cryostat-setup.sh [cryostat-namespace] [cryostat-name]     (defaults: cryostat cryostat-sample)
#   scripts/cryostat-setup.sh --delete [...]                           removes the rule again
#
# Uses the Cryostat REST API with your oc login token.
set -euo pipefail

DELETE=false
if [ "${1:-}" = "--delete" ]; then
  DELETE=true
  shift
fi
NAMESPACE="${1:-cryostat}"
NAME="${2:-cryostat-sample}"
RULE=swag_shop_continuous

HOST="$(oc get route "$NAME" -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
CRYOSTAT="https://$HOST"
TOKEN="$(oc whoami -t)"
auth=(-H "Authorization: Bearer $TOKEN")

# When this machine does not trust the route's certificate (a cluster whose routes are signed by its own CA),
# verify it with the cluster's ingress CA instead
tls=()
if ! curl -sS -o /dev/null "$CRYOSTAT/health" 2>/dev/null; then
  ca="$(mktemp)"
  trap 'rm -f "$ca"' EXIT
  oc get configmap default-ingress-cert -n openshift-config-managed -o jsonpath='{.data.ca-bundle\.crt}' >"$ca"
  tls=(--cacert "$ca")
fi

# Calls the Cryostat API and prints the answer; on an HTTP error, prints Cryostat's answer and stops
request() {
  local body status
  body="$(mktemp)"
  status="$(curl -sS -o "$body" -w '%{http_code}' ${tls[@]+"${tls[@]}"} "${auth[@]}" "$@")"
  if [[ "$status" != 2* ]]; then
    echo "Cryostat answered HTTP $status:" >&2
    cat "$body" >&2
    echo >&2
    rm -f "$body"
    exit 1
  fi
  cat "$body"
  rm -f "$body"
}

existing="$(request "$CRYOSTAT/api/v4/rules" | grep -c "\"name\":\"$RULE\"" || true)"
if [ "$existing" != "0" ]; then
  echo "==> Removing the existing rule $RULE"
  request -X DELETE "$CRYOSTAT/api/v4/rules/$RULE?clean=true" >/dev/null
fi
if [ "$DELETE" = true ]; then
  exit 0
fi

echo "==> Creating the automated rule $RULE in $CRYOSTAT"
# The request follows Cryostat's API description (GET $CRYOSTAT/api): matchExpression is an object with the
# script, and the labels are an object, although GET /api/v4/rules returns them as a string and a list
request -H 'Content-Type: application/json' -X POST "$CRYOSTAT/api/v4/rules" -d @- <<'EOF'
{
  "name": "swag_shop_continuous",
  "description": "Continuous JFR recording for every Swag Shop pod, analysed automatically",
  "matchExpression": { "script": "target.alias.startsWith('swag-shop')" },
  "eventSpecifier": "template=Continuous,type=TARGET",
  "archivalPeriodSeconds": 0,
  "initialDelaySeconds": 0,
  "preservedArchives": 0,
  "maxAgeSeconds": 300,
  "maxSizeBytes": 0,
  "metadata": { "labels": { "autoanalyze": "true" } },
  "enabled": true
}
EOF
echo
echo "==> Done. Cryostat: $CRYOSTAT"
