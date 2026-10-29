#!/usr/bin/env bash
# Checks that the classes every service keeps its own copy of are still identical: the demo package, the
# catalog client and the bundled products.json. The search service holds the reference copy (products.json:
# the catalog service). Run it after changing one of them, then copy the change to the other services.
#
#   scripts/check-copies.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SERVICES="pricing inventory order load-generator"
status=0

compare() {
  if ! diff -q "$1" "$2" >/dev/null; then
    echo "DIFFERENT: $2 (reference: $1)"
    status=1
  fi
}

for service in $SERVICES; do
  [ -d "$service" ] || continue
  for file in "$service"/src/main/java/com/demo/shop/demo/*.java "$service"/src/main/java/com/demo/shop/catalog/*.java; do
    [ -e "$file" ] || continue
    reference="search/${file#"$service"/}"
    if [ -e "$reference" ]; then
      compare "$reference" "$file"
    fi
  done
done
for file in */src/main/resources/products.json; do
  [ "$file" = catalog/src/main/resources/products.json ] || compare catalog/src/main/resources/products.json "$file"
done

if [ "$status" = 0 ]; then
  echo "All copies are identical."
fi
exit "$status"
