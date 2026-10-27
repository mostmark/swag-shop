#!/usr/bin/env bash
# Runs JDK Mission Control's automated analysis rules on a JFR recording, without opening the JMC window.
#
#   scripts/jfr-rules.sh recording.jfr          rules that score INFO (25+) or WARNING
#   scripts/jfr-rules.sh recording.jfr --all    every rule
#
# Uses the analysis libraries of a local JMC installation. Set JMC_HOME if JMC is not installed in the default
# macOS location.
set -euo pipefail

JMC_HOME="${JMC_HOME:-/Applications/JDK Mission Control.app}"
PLUGINS="$JMC_HOME/Contents/Eclipse/plugins"
[ -d "$PLUGINS" ] || PLUGINS="$JMC_HOME/plugins"
if [ ! -d "$PLUGINS" ]; then
  echo "JDK Mission Control not found in $JMC_HOME; set JMC_HOME" >&2
  exit 1
fi

CLASSPATH=""
for jar in "$PLUGINS"/org.openjdk.jmc.common_*.jar \
           "$PLUGINS"/org.openjdk.jmc.flightrecorder_*.jar \
           "$PLUGINS"/org.openjdk.jmc.flightrecorder.rules_*.jar \
           "$PLUGINS"/org.openjdk.jmc.flightrecorder.rules.jdk_*.jar \
           "$PLUGINS"/lz4-java_*.jar \
           "$PLUGINS"/org.owasp.encoder_*.jar; do
  CLASSPATH="$CLASSPATH:$jar"
done

exec java -cp "${CLASSPATH#:}" "$(dirname "$0")/JfrRules.java" "$@" 2> >(grep -v -E 'ValueReaders|Could not find field' >&2)
