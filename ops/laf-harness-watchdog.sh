#!/usr/bin/env bash
# Run the look-and-feel harness under a watchdog (pass -Pharness.windowed=true
# for the windowed mode, #42).
#
# Since #85 the harness hangs in about half of CI runs, somewhere inside
# PropertyKeyFieldTest, and cannot be reproduced locally. A hang used to sit
# there until the job's 25-minute timeout cancelled it — leaving no evidence
# at all. This wrapper gives up much sooner and, before killing anything,
# thread-dumps every JVM Gradle started (the build itself and the test
# executor) so the hung test and the threads it is waiting on end up in the
# CI log and in build/laf-harness-threads-*.txt.
#
# Usage: ops/laf-harness-watchdog.sh [extra gradle args, e.g. -Pharness.sdk=8.3.0]
#
#   HARNESS_DEADLINE_SECONDS   seconds before the watchdog fires (default 300;
#                              a healthy run takes ~35 s including JVM start-up)
#
# Standalone on purpose: no docker, no lib.sh — this runs on the CI runner.

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_ROOT}"

deadline="${HARNESS_DEADLINE_SECONDS:-300}"
dump_dir="${PROJECT_ROOT}/build"
mkdir -p "${dump_dir}"

# Every process under the gradlew we start: the wrapper JVM, the single-use
# daemon it forks (--no-daemon), and the test executor the daemon forks.
descendants() {
  local child
  for child in $(pgrep -P "$1"); do
    echo "${child}"
    descendants "${child}"
  done
}

./gradlew :designer:lafHarness --no-daemon --stacktrace "$@" &
gradle_pid=$!

waited=0
while kill -0 "${gradle_pid}" 2>/dev/null; do
  if [ "${waited}" -ge "${deadline}" ]; then
    echo "::error::lafHarness has run for ${deadline}s (healthy: ~35s); dumping threads and giving up"
    tree="$(descendants "${gradle_pid}")"
    for jvm_pid in ${tree}; do
      args="$(ps -o args= -p "${jvm_pid}" 2>/dev/null || true)"
      case "${args}" in *java*) ;; *) continue ;; esac
      out="${dump_dir}/laf-harness-threads-${jvm_pid}.txt"
      echo "==== jstack ${jvm_pid} -> ${out}"
      echo "     ${args:0:160}"
      # -l adds lock ownership, which is what a deadlock or a lock-order cycle
      # needs. If the JVM is too wedged even for that, fall back to the plain dump.
      jstack -l "${jvm_pid}" > "${out}" 2>&1 || jstack "${jvm_pid}" > "${out}" 2>&1 || true
      # The test executor is the interesting one; echo it into the CI log so
      # nobody has to download an artifact to see what hung.
      case "${args}" in
        *"Test Executor"*|*GradleWorkerMain*) cat "${out}" ;;
      esac
    done
    kill ${tree} "${gradle_pid}" 2>/dev/null
    sleep 5
    kill -9 ${tree} "${gradle_pid}" 2>/dev/null
    exit 1
  fi
  sleep 5
  waited=$((waited + 5))
done

wait "${gradle_pid}"
