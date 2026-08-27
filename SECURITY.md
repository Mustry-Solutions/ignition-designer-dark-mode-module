# Security Policy

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.**

Instead, use one of these private channels:

- **GitHub Private Vulnerability Reporting** — on this repository, go to the
  **Security** tab → **Report a vulnerability** (preferred).
- **Email** — `hello@mustrysolutions.com` with the details below.

Please include:

- the module version and the Ignition version of the Designer,
- the host OS,
- a description of the issue and its impact,
- steps to reproduce.

We aim to acknowledge reports within a few business days, keep you updated on
remediation, and credit you (if you wish) once a fix ships.

## Scope

This module runs **only inside the Ignition Designer**, on the machine where
the Designer is launched. It has no gateway scope, no client scope, and no
network listener; it neither reads nor writes gateway resources, and it
transmits nothing. Its only persisted state is the theme preference, stored
locally via the Java preferences API.

Two properties of its implementation are worth stating plainly, because they
look alarming out of context and are the areas where a real issue is most
likely to be found:

- **Reflection into the running Designer.** To reach colors the Ignition UI
  hard-codes, the module mutates static `Color` instances belonging to Ignition
  classes, in place, and swaps cell renderers and UI delegates. This is
  confined to the Designer's own UI objects. It never touches user project
  content — the Vision design canvas is explicitly excluded — and every
  mutation is snapshotted and reversed when dark mode is turned off.
- **A bundled look-and-feel.** The module ships FlatLaf (see
  [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)) and installs it as the
  Designer's look and feel while dark mode is active.

Reports that the module corrupts Designer state, fails to restore cleanly, or
leaks data are in scope. Misconfiguration of gateway security is outside it,
but we are happy to advise.

## Supported versions

Pre-1.0, fixes land on the latest release line. Please test against the most
recent release before reporting.
