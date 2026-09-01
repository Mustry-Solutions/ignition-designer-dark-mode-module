# Designer Dark Mode

A **dark mode** for the Ignition Designer, packaged as an Ignition module — the
most-requested Designer idea on Inductive Automation's ideas portal.

![The Ignition Designer with dark mode enabled](docs/images/designer-dark.png)

**[Download the latest release](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/releases/latest)**,
install the `.modl` on your gateway, relaunch the Designer, and turn it on with
**Tools → Dark Mode**. Full steps in [Install](#install).

> Status: young but usable. Designer scope only; targets Ignition **8.3+**.
> See [Known limitations](#known-limitations) before installing.

## What it does

Ignition ships the Designer with a light, Synthetica-based look and feel and no
dark option. Designer Dark Mode adds a **Tools → Dark Mode** toggle that restyles the
whole Designer — dock panels, trees, tables, menus, the Perspective component
palette and property editor, the tag browser and tag editor, the script editors
and output console, dialogs, and icons — to a dark theme built on
[FlatLaf](https://www.formdev.com/flatlaf/). The choice is remembered between
sessions, per computer and OS user account — see
[Where the setting is stored](#where-the-setting-is-stored).

Toggling back restores the stock Designer. A relaunch always starts from the
stock theme and re-applies dark on top of it, so nothing carries over from the
last session but the choice itself.

Because Ignition's own UI hard-codes many light colors in ways a normal look and
feel swap cannot reach, the module does substantial work under the hood to make
the theme comprehensive. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) if you
want to understand how, or extend it.

The same Designer, toggled off and on:

| Dark mode off | Dark mode on |
|---|---|
| ![Designer with dark mode off](docs/images/designer-light.png) | ![Designer with dark mode on](docs/images/designer-dark.png) |

## Known limitations

Some surfaces are left light **on purpose**, because they render *your* content
rather than the Designer's chrome, and theming them would misrepresent what your
users will actually see:

- the **Vision design canvas**, which shows your window content;
- the **Perspective view canvas**, which renders the view as a session would —
  it follows the session's own theme, not the Designer's;
- **Symbol Factory thumbnails**, which are the symbol artwork itself.

Two more, following the prior art's own judgement (see
[Prior art](#prior-art)): the Vision **property tables** are deliberately not
themed, because colouring them looks spotty and makes them harder to use.

Genuinely open, rather than deliberate: the Perspective view editor's **rulers
and surround** stay light. They are chrome rather than content, so they arguably
should follow the theme; it has not been decided.

Everything else in the Designer is themed. If you find a surface that is not,
that is a bug worth reporting — the
[QA checklist](docs/QA-CHECKLIST.md) tracks what has been swept and what has
not.

## Install

You do not need to build anything.

1. Download `designer-dark-mode.modl` from the
   **[latest release](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/releases/latest)**.
2. In the Gateway web UI, go to **Config → Modules**, choose **Install or
   Upgrade a Module**, and pick that file.
3. The gateway will ask you to accept the module's certificate. It is signed by
   Mustry Solutions with our own certificate rather than one Ignition ships, so
   you are asked to trust it once, on first install.
4. **Relaunch the Designer**, then turn it on with **Tools → Dark Mode**.

Requires Ignition **8.3.0** or newer. Upgrading is the same flow — install the
newer `.modl` over the old one and relaunch the Designer.

Everything this module does happens inside the Designer. It adds no gateway
service, no tags, and no scripting functions, and it changes nothing for
Perspective sessions, Vision clients, or anyone else using your gateway. To
remove it: **Config → Modules → Uninstall**, and relaunch the Designer.

### Where the setting is stored

The toggle is saved for the **OS user account on that computer**. It is not
per-gateway and not per-project, and there is no gateway-wide default to set.

It lives in the standard Java preference store, under the node
`com/mustrysolutions/designerdarkmode/designer`, key `darkMode`:

| OS | Where |
|---|---|
| Windows | `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\mustrysolutions\designerdarkmode\designer` |
| macOS | `~/Library/Preferences/com.mustrysolutions.designerdarkmode.plist` |
| Linux | `~/.java/.userPrefs/com/mustrysolutions/designerdarkmode/designer/prefs.xml` |

So one person turning dark mode on gets it in **every Designer they launch on
that machine** — every gateway they connect to, every project they open. Nobody
else is affected: not a colleague at the next desk, and not the same person
signed in to a different computer, who each choose for themselves.

The gateway does have to have the module installed for the setting to do
anything, so a Designer opened against a gateway without it comes up stock.

## Build

You only need this to develop the module — to *use* it, see
[Install](#install) above.

Requirements: **JDK 17** (the Gradle toolchain will use it) and internet access
to Inductive Automation's Maven repository. Everything else — Gradle 9.7.1, the
module plugin, FlatLaf — is resolved automatically.

```bash
./gradlew build
```

The module lands at `build/designer-dark-mode.unsigned.modl`. Builds from source
are unsigned unless you pass signing credentials (see
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#signing)), and an 8.3 gateway will
refuse to load an unsigned module unless you accept it during install.

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
    DesignerDarkModeHook     Module entry point; registers the Tools menu item
    ThemeManager             Orchestrates the whole theme switch
    IaColorTokens            Reflectively restyles Ignition's hard-coded colors
    TreeIconRecolorer        Dark-adapts tree icons and cell renderers
    CellRendererSanitizer    Dark-adapts table/list cells and renderer delegates
    ScriptEditorTheme        Applies Ignition's own dark theme to the code editors
    ConsoleTextTheme         Recolours the console output styles
    ComponentInspector       Debug tool: dumps the component under the cursor
    DebugLog                 Append-only debug log
    MoonIcon                 The menu item's icon
  src/main/resources/.../    Bundle strings (menu/action labels)
ops/                        Disposable Docker gateway for local testing
docs/                       Architecture and development guides
```

## Documentation

- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — how the dark theme is
  applied, why it takes so much machinery, and the Ignition implementation
  details it depends on. Read this before changing theming code.
- **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)** — build, run, deploy, debug, and
  sign; the diagnostic tools; how to fix a "still light" component.
- **[docs/QA-CHECKLIST.md](docs/QA-CHECKLIST.md)** — the dark-mode sweep of
  Designer surfaces, run before a release.
- **[ops/README.md](ops/README.md)** — the local dev gateway.

## Prior art

Inductive Automation's Exchange already has a
[Dark Mode for the Designer](https://inductiveautomation.com/exchange/2719/overview)
by Justin Edwards — a well-polished Jython script, MIT licensed, that has been
serving this need on **Ignition 8.1** since 2024. If you are on 8.1, use it.

Designer Dark Mode differs in two ways. It targets **8.3**, and it is a module
rather than a project-library script, so it installs once on the gateway
instead of being imported into each project and needs no Vision client tag to
persist. Under the hood it swaps the Designer's look and feel for
[FlatLaf](https://www.formdev.com/flatlaf/) and restyles Ignition's own design
tokens, rather than painting enumerated components one class at a time — which
means surfaces nobody has explicitly catalogued come out dark by default.

That script's careful catalogue of where the Designer leaks light informed this
project's testing, and is gratefully acknowledged.

## Contributing

Bug reports and pull requests are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md). Security issues should go through the
private channels in [SECURITY.md](SECURITY.md) rather than a public issue.

## Licensing & support

Licensed under the **Apache License, Version 2.0** — see [LICENSE](LICENSE).

The module is **free**: no trial period, no activation, no per-seat or
per-gateway fee, and it may be installed on any number of gateways. Bundled
third-party components are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

Designer Dark Mode is an independent third-party module. It is not produced,
endorsed, supported, or certified by Inductive Automation, LLC. "Ignition",
"Perspective" and "Vision" are trademarks of Inductive Automation, LLC, used
here only to identify the software this module interoperates with.

There is no commercial support contract. Please raise questions and bugs as
[GitHub issues](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues).
