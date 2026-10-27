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

existing="$(curl -sf "${auth[@]}" "$CRYOSTAT/api/v4/rules" | grep -c "\"name\":\"$RULE\"" || true)"
if [ "$existing" != "0" ]; then
  echo "==> Removing the existing rule $RULE"
  curl -sf "${auth[@]}" -X DELETE "$CRYOSTAT/api/v4/rules/$RULE?clean=true" >/dev/null
fi
if [ "$DELETE" = true ]; then
  exit 0
fi

echo "==> Creating the automated rule $RULE in $CRYOSTAT"
curl -sf "${auth[@]}" -H 'Content-Type: application/json' -X POST "$CRYOSTAT/api/v4/rules" -d @- <<'EOF'
{
  "name": "swag_shop_continuous",
  "description": "Continuous JFR recording for every Swag Shop pod, analysed automatically",
  "matchExpression": "target.alias.startsWith('swag-shop')",
  "eventSpecifier": "template=Continuous,type=TARGET",
  "archivalPeriodSeconds": 0,
  "initialDelaySeconds": 0,
  "preservedArchives": 0,
  "maxAgeSeconds": 300,
  "maxSizeBytes": 0,
  "metadata": { "labels": [ { "key": "autoanalyze", "value": "true" } ] },
  "enabled": true
}
EOF
echo
echo "==> Done. Cryostat: $CRYOSTAT"
