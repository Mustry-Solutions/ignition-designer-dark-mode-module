# Designer Dark Mode

A **dark mode** for the Ignition Designer, packaged as an Ignition module — the
most-requested Designer idea on Inductive Automation's ideas portal.

> Status: early development (`0.0.1-SNAPSHOT`). Designer scope only; targets
> Ignition **8.3+**.

## What it does

Ignition ships the Designer with a light, Synthetica-based look and feel and no
dark option. Designer Dark Mode adds a **Tools → Dark Mode** toggle that restyles the
whole Designer — dock panels, trees, tables, menus, the Perspective component
palette and property editor, the tag browser and tag editor, dialogs, and icons
— to a dark theme built on [FlatLaf](https://www.formdev.com/flatlaf/), and puts
it back exactly as it was when you toggle off. The choice is remembered between
sessions.

Because Ignition's own UI hard-codes many light colors in ways a normal look and
feel swap cannot reach, the module does substantial work under the hood to make
the theme comprehensive. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) if you
want to understand how, or extend it.

## Install

Grab a built `.modl` (see [Build](#build)) and install it on your gateway:
**Config → Modules → Install or Upgrade a Module** in the Gateway web UI, then
relaunch the Designer. The module is unsigned by default, so an 8.3 gateway will
ask you to accept it during install.

Then, in the Designer: **Tools → Dark Mode**.

## Build

Requirements: **JDK 17** (the Gradle toolchain will use it) and internet access
to Inductive Automation's Maven repository. Everything else — Gradle 8.14, the
module plugin, FlatLaf — is resolved automatically.

```bash
./gradlew build
```

The module lands at `build/designer-dark-mode.unsigned.modl`. Builds are unsigned
unless you pass signing credentials (see
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#signing)).

## Try it in a throwaway gateway

The `ops/` scripts spin up a disposable Ignition gateway in Docker with the
module staged, so you can iterate without touching a real gateway:

```bash
ops/setup.sh      # build, start the gateway with the module staged
ops/deploy.sh     # rebuild + reload after code changes
ops/teardown.sh   # stop it (--purge also wipes gateway data)
```

Requires Docker. Full details in [ops/README.md](ops/README.md).

## Project layout

```
build.gradle.kts            Module definition (id, scopes, hook, signing)
settings.gradle             Gradle project + IA Maven repositories
designer/                   The only scope: Designer-side code
  build.gradle.kts          Designer deps (FlatLaf bundled via modlImplementation)
  src/main/java/.../designer/
    DesignerDarkModeHook  Module entry point; registers the Tools menu item
    ThemeManager             Orchestrates the whole theme switch
    IaColorTokens            Reflectively restyles Ignition's hard-coded colors
    TreeIconRecolorer        Dark-adapts tree icons and cell renderers
    CellRendererSanitizer    Dark-adapts table/list cells and renderer delegates
    ComponentInspector           Debug tool: dumps the component under the cursor
    DebugLog               Append-only debug log
    MoonIcon                 The menu item's icon
  src/main/resources/.../    Bundle strings (menu/action labels)
ops/                        Disposable Docker gateway for local testing
docs/                       Architecture and development guides
```

## Documentation

- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — how the dark theme is
  applied, why it takes so much machinery, and the reverse-engineered Ignition
  internals it depends on. Read this before changing theming code.
- **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)** — build, run, deploy, debug, and
  sign; the diagnostic tools; how to fix a "still light" component.
- **[ops/README.md](ops/README.md)** — the local dev gateway.

## License

Not yet specified.
