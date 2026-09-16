#!/usr/bin/env bash
# Print the newest cached Vision jars as a classpath, for the Vision probe:
#
#     ./gradlew :designer:visionProbe -Pvision.jars="$(ops/vision-jars.sh)"
#
# A Designer that has opened a Vision project keeps the module's jars under
# ~/.ignition/cache/resources/modules/com.inductiveautomation.vision/; the
# top-level entries there are DIRECTORIES named after the jar, with the jar
# itself one level down. Pass a version to pin one (e.g. 12.3.6); the default
# is the newest present.
set -euo pipefail

cache="${IGNITION_MODULE_CACHE:-$HOME/.ignition/cache/resources/modules/com.inductiveautomation.vision}"
[ -d "$cache" ] || { echo "no Vision module cache at $cache — open a Vision project in a Designer first" >&2; exit 1; }

version="${1:-}"
if [ -z "$version" ]; then
  version=$(ls "$cache" | sed -nE 's/.*vision-client-([0-9]+\.[0-9]+\.[0-9]+)\.jar$/\1/p' | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
  [ -n "$version" ] || { echo "no vision-client jar under $cache" >&2; exit 1; }
fi

out=""
for part in client common designer; do
  jar=$(find "$cache" -type f -name "*vision-$part-$version.jar" | head -1)
  [ -n "$jar" ] || { echo "no vision-$part-$version.jar under $cache" >&2; exit 1; }
  out="${out:+$out:}$jar"
done
echo "$out"
