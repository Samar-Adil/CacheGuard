#!/usr/bin/env sh
# Builds (if needed) and starts CacheGuard. Requires JDK 17+ and Maven.
set -e
cd "$(dirname "$0")/.."
JAR=$(ls target/cacheguard-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ]; then
  mvn -B -ntp -DskipTests package
  JAR=$(ls target/cacheguard-*.jar | head -n 1)
fi
exec java -jar "$JAR" "$@"
