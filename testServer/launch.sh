#!/usr/bin/env bash
# Starts the local Paper server for manual testing. You run this; it never touches the game for you.
set -euo pipefail

cd "$(dirname "$0")"

JAVA_BIN="${JAVA_BIN:-java}"

shopt -s nullglob
jars=(paper-*.jar)
if (( ${#jars[@]} == 0 )); then
  echo "No paper-*.jar in $(pwd)." >&2
  echo "Download one from https://papermc.io/downloads/paper first." >&2
  exit 1
fi
if (( ${#jars[@]} > 1 )); then
  echo "Multiple Paper jars found: ${jars[*]}" >&2
  echo "Keep only one so the launch target is unambiguous." >&2
  exit 1
fi
SERVER_JAR="${jars[0]}"

# Pick up the plugin if it has been built.
shopt -u nullglob
plugin="$(ls -1t ../build/libs/TLockout-*.jar 2>/dev/null | head -n1 || true)"
if [[ -n "$plugin" ]]; then
  mkdir -p plugins
  cp -u "$plugin" plugins/
  echo "Using plugin: $plugin"
else
  echo "warning: no build/libs/TLockout-*.jar found; server will start without the plugin" >&2
fi

exec "$JAVA_BIN" -Xms2G -Xmx2G \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -jar "$SERVER_JAR" --nogui "$@"
