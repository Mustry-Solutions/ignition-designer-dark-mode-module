# Contributing

Thanks for your interest in improving **Designer Dark Mode**. This guide covers
how to build, run, and submit changes.

## Ground rules

- Be respectful — see our [Code of Conduct](CODE_OF_CONDUCT.md).
- By contributing you agree your work is licensed under the repository's
  [Apache-2.0 License](LICENSE).
- Found a security issue? **Do not** open a public issue — see
  [SECURITY.md](SECURITY.md).

## Prerequisites

- **JDK 17** (Temurin) — the Gradle toolchain expects it.
- **Docker** — only for the local dev gateway.
- Network access to Inductive Automation's Maven repository.

## Build

```bash
./gradlew build
```

The module lands at `build/designer-dark-mode.unsigned.modl`. Plain builds are
unsigned; see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#signing) for signing.

## Run it

```bash
ops/setup.sh      # build, start a gateway with the module accepted and enabled
ops/deploy.sh     # rebuild + reload after code changes
ops/teardown.sh   # stop it (--purge also wipes gateway data)
```

Then launch a Designer against the gateway and use **Tools → Dark Mode**. See
[ops/README.md](ops/README.md) for the dev-gateway lifecycle.

## Read this before touching theming code

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains why a dark theme here is
not simply "install a dark look and feel", and records the Ignition internals
the module depends on. The pieces interact in non-obvious ways and several of
them exist to work around a specific, documented failure. Changing one pass
without reading that document is the fastest way to reintroduce a bug someone
already fixed.

## Diagnosing a "still light" component

This is the most common kind of contribution. The module ships a component
inspector: with dark mode on, press **Cmd/Ctrl+Shift+I** (or `+F12`) with the
mouse over the offending area, and the component chain — class, bounds, colors,
opacity, borders and UI delegate per level, plus a scroll pane's parts — is
dumped to
`~/.ignition/designer-dark-mode.log`. Full walkthrough in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#the-inspector--diagnosing-a-still-light-component).

## Pull request flow

1. Branch off `main` (`feature/…`, `fix/…`, `docs/…`).
2. Make your change and add a `CHANGELOG.md` entry under `## [Unreleased]`.
   Most of this module can only be judged by looking at a running Designer,
   but anything that is pure logic — a colour predicate, a threshold, the
   token snapshot/restore bookkeeping — should come with a unit test. Run
   them with `./gradlew test`; they need no gateway.
   If you touched the switch sequence itself, also run `./gradlew
   :designer:lafHarness` — it drives that sequence against the real Synthetica,
   JIDE and FlatLaf jars headlessly, and catches the kind of `UIManager` damage
   that is invisible on screen. See
   [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#the-headless-look-and-feel-harness).
3. **Verify the light restore, not just the dark result.** Toggling dark mode
   off must return the Designer exactly to stock. A change that only looks
   right in dark mode is half a change — restores iterate tracked component
   sets rather than the live hierarchy, and it is easy to darken something
   without registering it for restore.
4. Open a PR. The **Build & test** check must pass — it is a required status
   check on `main`, so a red build cannot be merged.
5. A maintainer squash-merges. `main` is always releasable.

## Conventions

- Match the surrounding code style.
- Every theming pass runs inside `safely(...)` so one failure is logged rather
  than stranding the rest of the switch. Keep new passes isolated the same way.
- **Log at the right level.** `DebugLog.log` always writes and is for what a
  user — or a maintainer reading their bug report — needs: theme switches and
  failures. Everything else is `DebugLog.detail`, which writes only under
  `-Ddesignerdarkmode.debug=true`. The component watcher re-runs the theming
  passes for the whole session, so anything that can fire per rescan, per
  attached component or per painted cell **must** be `detail`. A diagnostic
  that costs real work — a tree walk, a defaults dump — belongs inside
  `if (DebugLog.verbose())` rather than merely being logged quietly.
- **Say so when a pass fails.** Passes are isolated, so a failure shows up as
  one wrong-looking surface and nothing else. `ThemeManager` collects the names
  of failed phases and reports them in the Designer's status bar via
  `DesignerStatus`; a new pass added through `safely(...)` is covered
  automatically, which is another reason to add it that way.
- Prefer fixing a color at its source — an Ignition design token in
  `IaColorTokens` — over walking the component hierarchy. One token entry
  covers every component and painter that holds it, whenever it was built.
- Never mutate `Base000`: it is the `java.awt.Color.WHITE` instance itself, and
  corrupting it breaks white JVM-wide.
- Prior art matters here. The Ignition Exchange has a
  [dark mode script for 8.1](https://inductiveautomation.com/exchange/2719/overview)
  (MIT, Justin Edwards) that maps many of the Designer's light surfaces. If you
  port code from it, say so in the commit and keep the MIT attribution.

## Releasing

Maintainers only. A release is a `vX.Y.Z` tag; pushing it builds, signs and
publishes the `.modl` via `.github/workflows/release.yml`. Tags must be plain
`x.y.z` — Ignition's module version parser is numeric-only and rejects a
prerelease suffix at install time.
