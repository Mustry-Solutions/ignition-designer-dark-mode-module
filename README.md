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
sessions: a Designer you left dark comes back dark, and one you left light
comes back light.

Toggling back restores the stock Designer. Because a relaunch always starts
from the stock theme and applies dark on top of it only if the setting asks for
it, a relaunch is also the sure way to clear any residue a toggle-off left
behind. See [Where the setting is stored](#where-the-setting-is-stored) for
what "remembered" means when you work against more than one gateway.

Because Ignition's own UI hard-codes many light colors in ways a normal look and
feel swap cannot reach, the module does substantial work under the hood to make
the theme comprehensive. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) if you
want to understand how, or extend it.

The same Designer, toggled off and on:

| Dark mode off | Dark mode on |
|---|---|
| ![Designer with dark mode off](docs/images/designer-light.png) | ![Designer with dark mode on](docs/images/designer-dark.png) |

## Known limitations

**Dark mode and Vision do not mix, and the module keeps them apart.** A Vision
window saved from a FlatLaf Designer picks up FlatLaf's fonts, colours and
border classes as if you had set them by hand, and a Vision client cannot open
it at all (`ClassNotFoundException: com.formdev.flatlaf.ui.FlatButtonBorder`).
The cause is the platform's window serializer, which compares every property
against a "clean copy" cached for the life of the Designer. The module now
refreshes that cache at every switch, which cures the crash — but not the
rest: Vision also copies the Designer's colour constants into components as a
window opens, and dark mode rewrites those. So, for now, the module still
stays out of Vision's way:

- **Tools → Dark Mode is refused** while any Vision window or template is open,
  or while the Vision workspace is selected. The status bar and a dialog say why.
- **A dark Designer turns itself light** the moment you navigate to Vision in
  the project browser, before the window you are opening is loaded. Turn dark
  mode back on once you have left Vision and closed its windows. Your saved
  preference is kept, so the next launch away from Vision comes up dark.
- If a Vision window still reaches the screen under dark mode, the module turns
  light and asks you to **close and reopen that window** before editing it.

Perspective, scripting, tags, reports, pipelines and everything else are
unaffected. The details, and the headless reproduction, are in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#visiongate).

**Do not use this module and the Exchange dark-mode script in the same
project.** [Dark Mode for the Designer](https://inductiveautomation.com/exchange/2719/overview)
(see [Prior art](#prior-art)) is a project-library script plus a Vision client
tag, so a project that imported it keeps running it in every Designer that
opens the project, whether or not this module is installed. The tag adds its
own **View → Dark Mode** checkbox shortly after launch. Ticked alongside
Tools → Dark Mode, the script paints explicit colours over this module's theme,
and its own light setting paints explicit white, and neither is anything this
module's toggle-off can undo. Before turning dark mode on in such a project,
remove the script's `designerPatch` Vision client tag; the `darkModePatch`
project library script does nothing on its own and can stay. Detecting the
script at startup is tracked in
[#89](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/89).

Some surfaces are left light **on purpose**, because they render *your* content
rather than the Designer's chrome, and theming them would misrepresent what your
users will actually see:

- the **Perspective view canvas**, which renders the view as a session would —
  it follows the session's own theme, not the Designer's;
- **Symbol Factory thumbnails**, which are the symbol artwork itself.

Genuinely open, rather than deliberate: the Perspective view editor's **rulers
and surround** stay light. They are chrome rather than content, so they arguably
should follow the theme; it has not been decided.

On **Windows and Linux** the native **title bar and window frame** stay light.
The Designer's macOS title bar follows the theme through a property Java only
honours there; doing the same elsewhere would mean replacing the window
decorations, which is a larger change than the gap justifies.

The module has been developed and checked by eye on macOS. Its automated
checks run on Windows and Linux too, but nobody has yet sat in front of a
Designer there. If you do and something is off, the debug log at
`~/.ignition/designer-dark-mode.log` opens with an `env:` block describing your
JVM — please include it in the report. One thing it will tell you directly: if
the status bar says the JVM has not opened `java.awt`, add
`--add-opens java.desktop/java.awt=ALL-UNNAMED` under *Additional JVM
Arguments* for the gateway in the Designer Launcher and relaunch.

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

## Where the setting is stored

The Dark Mode choice is saved **on the computer running the Designer, per
operating-system user**, through Java's `java.util.prefs` API. It is not stored
on the gateway, not in the project, and not in a tag. Nothing on the gateway
changes when you toggle it, and a colleague launching the Designer against the
same gateway from their own machine or account gets their own setting.

It is the preference node `com/mustrysolutions/designerdarkmode/designer`, key
`darkMode`:

| OS | Where |
|---|---|
| Windows | `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\mustrysolutions\designerdarkmode\designer` |
| macOS | `~/Library/Preferences/com.mustrysolutions.designerdarkmode.plist` |
| Linux | `~/.java/.userPrefs/com/mustrysolutions/designerdarkmode/designer/prefs.xml` |

It is **one setting for every gateway you connect to**, not one per gateway.
The saved value does not record which gateway it was set from, so turning dark
mode on while connected to one gateway turns it on for the Designer of every
other gateway you open, as long as that gateway has the module installed. A
gateway *without* the module never loads this code, so its Designer stays
stock; the saved value is left untouched and applies again the next time you
open a gateway that has the module.

One consequence of the shared value: after every toggle and every launch the
module rewrites the setting to match the theme that is actually installed. If
applying dark mode fails on one gateway (for example after an Ignition upgrade
the module does not yet support), the setting flips back to light, and that
flip also applies to the Designers of your other gateways until you toggle it
on again.

If you need to reset it by hand, see
[Recovering from a bricked launch](docs/DEVELOPMENT.md#recovering-from-a-bricked-launch).

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
    ScriptEditorTheme        Dark-themes the Python editors (RSyntaxTextArea)
    CodeEditorTheme          Dark-themes the SQL/expression editors (JIDE CodeEditor)
    ConsoleTextTheme         Recolours the console output styles
    DiagnosticsChartTheme    Dark-adapts the Diagnostics chart axes and background
    BlockWorkspaceTheme      Dark-adapts alarm pipeline and SFC blocks
    DesignerStatus           Reports switch progress and failures in the status bar
    ComponentInspector       Debug tool: dumps the component under the cursor
    TreeUpdateDiagnostic     Explains a failed updateComponentTreeUI
    DebugLog                 Append-only debug log
    MoonIcon                 The menu item's icon
  src/main/resources/.../    Bundle strings (menu/action labels)
  src/test/java/.../         Unit tests: colour predicates, snapshot/restore
  src/lafHarness/java/.../   Headless harness against the real Synthetica/JIDE/FlatLaf
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
serving this need on Ignition 8.1 since 2024. Since version 1.3.0
(3 September 2026) it runs on **8.3** as well. On 8.1 it is the only option;
on 8.3 you have a choice.

Designer Dark Mode differs in how it is delivered and how it themes. It is a
module rather than a project-library script, so it installs once on the
gateway instead of being imported into each project, is toggled from the Tools
menu, and needs no Vision client tag to persist. Under the hood it swaps the
Designer's look and feel for [FlatLaf](https://www.formdev.com/flatlaf/) and
restyles Ignition's own design tokens, rather than painting enumerated
components one class at a time — which means surfaces nobody has explicitly
catalogued come out dark by default.

One difference cuts the other way. Because the script never swaps the look and
feel, the Vision serializer problem described under
[Known limitations](#known-limitations) does not arise with it, and on a
project that is mostly Vision it remains the better choice today. The two are
not meant to run in the same project; that section says why and what to
remove.

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
