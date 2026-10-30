#!/usr/bin/env bash
# Switches the search problem (the CPU hotspot) on or off in the search service, for demo 1. The switch is
# recorded in JFR as a "Problem Toggled" event.
#
#   scripts/search-problem.sh on|off [url]      (default url: http://localhost:8082)
set -euo pipefail

case "${1:-}" in
  on) ENABLED=true ;;
  off) ENABLED=false ;;
  *)
    echo "Usage: $0 on|off [url]" >&2
    exit 1
    ;;
esac
URL="${2:-http://localhost:8082}"

curl -sSf -X PUT -H 'Content-Type: application/json' \
  -d "{\"problemEnabled\":$ENABLED,\"changedBy\":\"search-problem.sh\"}" \
  "$URL/api/demo/scenario" >/dev/null
if [ "$ENABLED" = true ]; then
  echo "Search problem on: the search service runs the naive RecursiveEditDistance"
else
  echo "Search problem off: the search service runs the fixed IterativeEditDistance"
fi
