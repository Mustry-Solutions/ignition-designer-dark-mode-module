#!/usr/bin/env bash
# Inspect every saved Vision window and template in the dev gateway's project
# for FlatLaf leakage — the corruption docs/ARCHITECTURE.md#vision describes.
#
# A clean resource names no com.formdev class and carries no setFont/setForeground
# call you did not make yourself. Any com.formdev hit means a Vision client
# cannot open that window at all.
#
# Usage: ops/vision-check.sh [project]      (default project: test)

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

PROJECT="${1:-test}"
VISION_DIR="/usr/local/bin/ignition/data/projects/${PROJECT}/com.inductiveautomation.vision"

require_docker
if ! docker exec "${CONTAINER_NAME}" test -d "${VISION_DIR}"; then
  err "No Vision resources in project '${PROJECT}' on ${CONTAINER_NAME} (${VISION_DIR})."
  exit 1
fi

files="$(docker exec "${CONTAINER_NAME}" sh -c \
  "find '${VISION_DIR}' -name '*.bin' -not -path '*/client-tags/*' | sort")"
if [[ -z "${files}" ]]; then
  warn "Project '${PROJECT}' has no saved Vision windows or templates yet."
  warn "Create one in the Designer (light mode), save, then run this again."
  exit 0
fi

info "Saved Vision resources in '${PROJECT}':"
bad=0
unreadable=0
while IFS= read -r file; do
  # Whether the resource is XML or binary, class names are stored as plain
  # strings, so a printable-text sweep catches both encodings. LC_ALL=C: under
  # a UTF-8 locale macOS tr rejects binary bytes ("Illegal byte sequence") and
  # the sweep silently comes back empty — every count reads 0 and the file
  # passes for the wrong reason.
  text="$(docker exec "${CONTAINER_NAME}" cat "${file}" | gzip -dc 2>/dev/null \
    | LC_ALL=C tr -c '[:print:]\n' '\n' || true)"
  if [[ -z "${text}" ]]; then
    err "${file#"${VISION_DIR}"/}: not a gzip stream; nothing was checked (Vision cannot open it either)"
    unreadable=$((unreadable + 1))
    continue
  fi
  flatlaf="$(printf '%s\n' "${text}" | LC_ALL=C grep -c 'com\.formdev' || true)"
  fonts="$(printf '%s\n' "${text}" | LC_ALL=C grep -c 'setFont' || true)"
  colours="$(printf '%s\n' "${text}" | LC_ALL=C grep -c 'setForeground\|setBackground' || true)"
  rel="${file#"${VISION_DIR}"/}"
  if [[ "${flatlaf}" -gt 0 ]]; then
    err "${rel}: ${flatlaf} FlatLaf class reference(s) — a Vision client cannot open this"
    bad=$((bad + 1))
  else
    ok "${rel}: no FlatLaf classes (setFont ×${fonts}, setForeground/Background ×${colours})"
  fi
done <<< "${files}"

if [[ "${bad}" -gt 0 || "${unreadable}" -gt 0 ]]; then
  [[ "${bad}" -gt 0 ]] && err "${bad} resource(s) corrupted. Fix them by reverting the resource, not by editing under dark mode again."
  [[ "${unreadable}" -gt 0 ]] && err "${unreadable} resource(s) could not be read; every saved window and template is a gzip stream, so revert those too."
  exit 2
fi
ok "No FlatLaf leakage. Font/colour counts should match what you set by hand."
