# Development guide

How to build, run, debug, and sign Designer Dark Mode.

## Prerequisites

- **JDK 17.** The Gradle toolchain pins Java 17; the build fails on other
  versions. (Note: the Ignition Module Generator that scaffolded this project
  needs a specific JDK too — the `v0.5.0` generator tag works on JDK 17.)
- **Docker** — only if you want the local dev gateway under `ops/`.
- Network access to `https://nexus.inductiveautomation.com/repository/public`,
  where the Ignition SDK artifacts live.

Everything else is resolved by Gradle: Gradle 8.14 via the wrapper, the
`io.ia.sdk.modl` 0.5.0 module plugin, and FlatLaf 3.7.2.

## Build

```bash
./gradlew build
```

Output: `build/designer-dark-mode.unsigned.modl`. FlatLaf is bundled into the module
(declared `modlImplementation` in `designer/build.gradle.kts`), so there's no
runtime dependency to install separately.

Useful variants:

```bash
./gradlew :designer:compileJava   # fast compile-only check while iterating
./gradlew test                    # unit tests only (no gateway needed)
```

## Run it against a gateway

### Option A — the disposable Docker gateway (recommended for dev)

```bash
ops/setup.sh      # first time: build, start gateway, stage the module
ops/deploy.sh     # after code changes: rebuild + reload
ops/logs.sh       # tail gateway logs
ops/status.sh     # container status, URL, staged files
ops/teardown.sh   # stop (add --purge to wipe gateway data)
```

The gateway comes up at **http://localhost:8088** (copy `.env.example` to `.env`
to use different ports). Commissioning is unattended — `setup.sh` seeds the
module's certificate and EULA into the gateway's `data/modules.json`, so there
is no browser wizard. See [../ops/README.md](../ops/README.md) for the full
picture.

**Designer-scope code only loads when the Designer starts.** `deploy.sh` reloads
the module on the gateway, but you must **relaunch the Designer** to pick up
changes. This is the single easiest thing to forget — if a change "didn't do
anything," check you relaunched.

### Option B — your own gateway

Install `build/designer-dark-mode.unsigned.modl` via **Config → Modules** in the
Gateway web UI, accept the unsigned module, relaunch the Designer.

## The debug loop

Dark-mode work is mostly a diagnose-then-fix loop against the running Designer.
Two tools make it tractable:

### The debug log

`~/.ignition/designer-dark-mode.log` — append-only, written by `DebugLog`.
Theme switches, per-phase failures (with stack traces), icon classes
encountered, popup contents, and stale-delegate refreshes all land here.
**Timestamps are UTC** — add your local offset when correlating with the clock.

The noisiest diagnostics (every dock title pane, every light `UIManager`
default) are off unless the Designer is launched with
`-Ddesignerdarkmode.debug=true`. Turn them on when hunting a mistyped surface;
leave them off otherwise, or the warnings drown.

```bash
tail -f ~/.ignition/designer-dark-mode.log
```

### The inspector — diagnosing a "still light" component

With dark mode active, hover the mouse over the offending area and press
**Cmd+Shift+I** (Ctrl+Shift+I also works; `+F12` too if your F-keys aren't
media keys). The component chain under the cursor is dumped to the debug log:
each level's class, bounds, background and foreground (marked `uires` /
`explicit` / `inherited`), opacity, border (with its colour, recursing compound
borders), UI delegate, and — for a `JScrollPane` — its `viewportBorder` plus its
parts: the viewport's view, the row and column header views, and the corners.

The parts matter more than they sound. An empty `JTable` has almost no height,
so the pointer falls straight past it to the viewport and it never appears in
the chain; a pale band at the top of a viewport survived a dozen "everything is
dark" inspections for exactly that reason.

Read the dump top-down and identify *why* the pixel is light:

- **A light background** on some component — is it `Color.WHITE` (a `Base000`
  token), a stale light `UIResource` (a JIDE key the reassert missed), or an
  explicit light color hard-coded in an IA class?
- **A stale UI delegate** — a `Synthetica*` delegate under FlatLaf means a
  cached/detached component; it needs a `refreshStaleUiDelegates` reachable at
  the point it's shown.
- **A light border** — a component can report a perfectly dark background while
  drawing a pale `MatteBorder`. Check the `border=` field; `Base000` used as a
  border colour is handled by `swapWhiteBorder`.
- **Nothing light in the chain** — then it is not a component property at all.
  Either the pixel is *renderer-painted* (the deepest real component is a
  table/tree), and the fix belongs in `CellRendererSanitizer` /
  `TreeIconRecolorer`; or a UI delegate is painting from a shared default, in
  which case relaunch with `-Ddesignerdarkmode.debug=true` and read the
  "Light UIManager colour defaults under dark" line. That is how the JIDE
  `SidePane.background` and `CommandBarSeparator.background` strips were found.

Then pick the matching mechanism (see
[ARCHITECTURE.md](ARCHITECTURE.md#the-components)) and add the color/key/class to
it. The whole approach is additive nets, so most fixes are one entry in a map or
one predicate.

If a surface that used to be dark has gone light after an Ignition upgrade,
suspect a renamed class or field before suspecting the theming logic — see
[ARCHITECTURE.md](ARCHITECTURE.md#when-ignition-changes-underneath-us).

## Recovering from a bricked launch

If a bad theme state ever prevents the Designer from launching, clear the saved
preference so it starts light: remove the `java.util.prefs` node
`com/mustrysolutions/designerdarkmode/designer` (a tiny one-off program using
`Preferences.userRoot().node(...).removeNode()`). `ThemeManager` also
auto-reverts the preference when an apply fails in the essential phase, so this
is rarely needed.

## Signing

Builds are **unsigned by default**. `build.gradle.kts` sets
`skipModlSigning` to `true` unless the Gradle property
`ignition.signing.keystoreFile` is present, so a plain `./gradlew build` stays
unsigned and an 8.3 gateway accepts it interactively.

To produce a signed module, supply signing credentials via
`-Pignition.signing.*` properties pointing at a keystore. The `ops/` dev scripts
do this automatically against a throwaway self-signed certificate generated into
`ops/signing/` (gitignored — **never commit keystores or certs**).

## Project conventions

- **Designer scope only.** There is one Gradle subproject, `:designer`; the hook
  is `DesignerDarkModeHook`, mapped to scope `D` in `build.gradle.kts`.
- **Fail soft.** Theming code must never break the Designer. New passes go
  through `ThemeManager.safely(...)`; anything in a paint path swallows its own
  throwables. A theme fix that throws is worse than a component that stays light.
- **Reversible.** Every dark-mode mutation must be undone on the light switch,
  and restores iterate tracked sets rather than the live hierarchy.
- **User-facing strings** live in
  `designer/src/main/resources/.../designerdarkmode.properties` (bundle prefix
  `designerdarkmode`), not inline.
