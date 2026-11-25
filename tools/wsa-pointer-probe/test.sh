#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
probe=com/pokedaisy/mousesupremacy/probe
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d "$classes" \
    "src/$probe/PointerState.java" "src/$probe/Trace.java" "src/$probe/WsaPointer.java" \
    "src/$probe/ProbeInput.java" "src/$probe/DeviceAliases.java" \
    "test/$probe/ProbeTest.java" "test/$probe/WsaProbeTest.java" "test/$probe/RouterOrderingTest.java"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$classes" com.pokedaisy.mousesupremacy.probe.ProbeTest
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$classes" com.pokedaisy.mousesupremacy.probe.WsaProbeTest test/wsa-replay.tsv
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$classes" com.pokedaisy.mousesupremacy.probe.RouterOrderingTest
