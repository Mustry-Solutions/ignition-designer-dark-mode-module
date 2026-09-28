#!/usr/bin/env bash
# Print the GitHub Release notes for one version: which file to download, the
# version's CHANGELOG section, and the footer every release page carries.
# The release workflow publishes exactly this; run it by hand to rewrite the
# notes of a release that is already out.
#
# Usage: ops/release-notes.sh 0.6.0
#        ops/release-notes.sh 0.6.0 > notes.md && gh release edit v0.6.0 --notes-file notes.md
#
# Needs no Docker or gateway, so it does not source lib.sh.
set -euo pipefail

version="${1:?usage: ops/release-notes.sh <x.y.z>}"
changelog="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/CHANGELOG.md"

# The release notes ARE the CHANGELOG section for this version. Generated
# notes are a list of commit subjects, which says what was touched but not
# what was wrong or why it broke — the part a reader of a release actually
# needs. Fail loudly rather than print notes with no body.
section="$(awk -v ver="${version}" '
  index($0, "## [" ver "]") == 1 { found = 1; next }
  found && index($0, "## [") == 1 { exit }
  found { print }
' "${changelog}")"
if [[ -z "${section//[$'\n\t ']/}" ]]; then
  echo "CHANGELOG.md has no '## [${version}]' section. Add one before tagging." >&2
  exit 1
fi

# Every release page opens with which file to download, so nobody has to
# guess from the two names. Same table as the README.
cat <<'HEADER'
**Which file?**

| Your gateway | Download |
|---|---|
| Ignition **8.3** | `designer-dark-mode-8.3.modl` |
| Ignition **8.1.33** or newer | `designer-dark-mode-8.1.modl` |

---
HEADER
# The summary above the first `###` heading (what the release is, how to
# upgrade) stays in view. Each `### Added` / `### Changed` / ... subsection
# folds into a <details> block titled with its entry count, so the page opens
# short and the full detail is one click away. GitHub needs a blank line after
# </summary> and before </details> to render the Markdown inside.
printf '%s\n' "${section}" | awk '
  function flush() {
    if (title == "") return
    sub(/\n+$/, "", body)
    printf "<details>\n<summary><b>%s</b>%s</summary>\n\n", title, (n ? " (" n ")" : "")
    printf "%s\n\n</details>\n\n", body
    title = ""; body = ""; n = 0
  }
  index($0, "### ") == 1 { flush(); title = substr($0, 5); next }
  title == "" { print; next }
  # Skip the blank lines right after the heading; <summary> stands in for it.
  body == "" && $0 ~ /^[ \t]*$/ { next }
  index($0, "- ") == 1 { n++ }
  { body = body $0 "\n" }
  END { flush() }
'
# Every release page carries the same footer: the Download link in the README
# lands here, so this is where a reader learns who made it.
cat <<'FOOTER'

---

Designer Dark Mode is free and made by [Mustry Solutions](https://mustrysolutions.com), an IT/OT consultancy that builds Ignition systems and modules. We also sell [Ignition 8.3 modules](https://mustrysolutions.com/ignition-modules) (TimescaleDB, AMQP, Observability, Secrets) and build modules to order. [Get in touch](https://mustrysolutions.com/contact-us).
FOOTER
