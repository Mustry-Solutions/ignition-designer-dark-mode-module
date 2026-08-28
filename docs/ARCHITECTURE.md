# Architecture: how dark mode works

This document explains how Designer Dark Mode turns the Ignition Designer dark,
and records the Ignition and JIDE implementation details it depends on. None of
this is covered by the Ignition SDK docs — it was established empirically, by
observing the running Designer and iterating until each surface themed
correctly. Read this before touching theming code; the pieces interact in
non-obvious ways, and several of them exist to work around a specific failure
recorded below.

## The core problem

The Designer's stock look and feel is **Synthetica-based**
(`com.inductiveautomation.ignition.client.IgnitionLookAndFeel$LaF`), and its
docking panels are the **JIDE** framework. A dark theme is not just "install a
dark look and feel," because Ignition colors much of its own UI in ways a look
and feel swap never reaches:

1. **Static color tokens** — IA hands components literal `Color` constants from
   `IgnitionLookAndFeel$Colors` (e.g. `Base100 = #FAFAFB`) at construction, via
   `setBackground(...)`. Neither `UIManager` overrides nor
   `updateComponentTreeUI` touch these.
2. **JIDE painters** — dock title bars, grippers, split dividers, and section
   headers are drawn by a `ThemePainter` resolved through a per-classloader map,
   not through `UIManager` color keys directly.
3. **Cell renderers** — trees and tables render cells with renderer components
   that live *outside* the component hierarchy and cache light colors at
   construction, so hierarchy walks and `updateComponentTreeUI` never reach them.
4. **Cached / detached components** — popups, dialogs, and lazily-built panels
   are constructed under whichever theme was active at the time and keep those
   UI delegates; a Synthetica delegate literally cannot paint under FlatLaf
   (it throws or paints broken).

So the module attacks the theme on several fronts at once. `ThemeManager` is the
orchestrator; the other classes are the fronts.

## The switch, step by step

`ThemeManager.apply(dark)` runs these phases. Phase 1 is essential; if it fails
the switch aborts. Phases 2+ are each wrapped in `safely(...)` so one failing
pass is logged (with a stack trace, to the debug log) without stranding the rest.

1. **Look and feel swap.** Dark: `UIManager.setLookAndFeel(new FlatDarkLaf())`,
   then `keepSyntheticaAlive()`. Light: reinstall the stock theme through
   Synthetica's own entry point. Wrapped in a one-shot retry (see
   [Gotchas](#gotchas-and-hard-won-facts)).
2. **Color tokens** — `IaColorTokens.install()` (dark) / `.uninstall()` (light).
3. **JIDE extension** — `installJideExtension(dark)`. FlatLaf isn't a look and
   feel JIDE recognizes, so under dark it must be told the VSNET style
   explicitly: `installJideExtension(1)`.
4. **Theme painters** — `overrideThemePainters(dark)` repoints JIDE's painter
   map (see below).
5. **Default re-assert** — `applyMenuDefaults(dark)` re-puts *all* FlatLaf
   defaults on top (the JIDE extension clobbers standard Swing keys), then
   `applyJideDarkOverrides(dark)` sets the JIDE-specific keys.
6. **`updateComponentTreeUI`** on every window.
7. **macOS title bars** — set/clear the `apple.awt.windowAppearance` client
   property so the native title bar follows the theme.
8. **The passes** (dark only): tree icons, button/label icons, cell-renderer
   sanitizer, collapsible title panes, white-token background and border swaps,
   script editors, console output styles, status-bar legibility, cached JIDE
   painters, stale-delegate refresh in secondary windows. On light, the
   corresponding restores.
9. **Component watcher** installed (dark) / removed (light).

On light mode, the restores iterate **tracked component sets**, never the live
hierarchy — a component detached at restore time (a closed dialog, a hidden
section) would be missed by a walk and come back stuck dark.

## The components

### ThemeManager
The orchestrator. Owns the preference (`java.util.prefs`, node
`com/mustrysolutions/designerdarkmode/designer`, key `darkMode`), the `uiReady` gate, the
per-phase error isolation, the JIDE/Synthetica/FlatLaf plumbing, the
`UIManager` key overrides (dock, collapsible-pane, and container keys), the
component watcher, and the popup / stale-delegate handling.

### IaColorTokens
Reflectively mutates the **shared `Color` instances** on
`IgnitionLookAndFeel$Colors` in place — the Designer JVM runs with
`--add-opens java.desktop/java.awt`, so `Color.value` can be rewritten. Every
component and painter holding a token then renders dark on its next repaint,
whenever it was built. Also mutates specific hard-coded color statics in named
classes (`CLASS_DARK` — e.g. `NodeEditor`'s gutter/hover colors, the welcome
panel's tile-selection color). Originals are snapshotted and restored on light.

**Never mutate `Base000`** — it is `java.awt.Color.WHITE` itself, and corrupting
it would break white JVM-wide. `isJdkGlobal` refuses it and every other JDK
`Color` singleton; unit tests pin that, including that the check is by identity
so an IA colour that merely *equals* white stays restyleable.

Components handed that instance are corrected per-component by identity instead,
in `ThemeManager`. Note there are **two** ways the token leaks and both need
covering: as a `background` (`swapWhiteTokenBackgrounds`) and as a **border**
colour (`swapWhiteBorder`, including a `JScrollPane`'s separate
`viewportBorder`). Only the first was handled originally, and a white
`MatteBorder` drew a pale band that survived two dozen inspections because
nothing examined borders.

### TreeIconRecolorer
Wraps every `JTree`'s cell renderer so that, per render: SVG/vector/bitmap icons
get a cached **smart-invert** dark variant (neutral strokes lighten and take the
theme tint; saturated brand/status colors keep their hue), the renderer's cached
color fields are re-synced from `UIManager`, and identity-`WHITE` backgrounds
(the `Base000` token) are corrected. Also recolors toolbar/status-bar button and
label icons. Restores everything on light.

### CellRendererSanitizer
The table/list counterpart. Wraps table (column + per-class default), header,
and list renderers, and — crucially — replaces each table UI's
**`CellRendererPane`** with a sanitizing subclass. Renderer painting for JIDE
grid tables is resolved dynamically and bypasses column/default renderers, but
*all* of it flows through the pane, so intercepting the pane catches every
mechanism. On each renderer paint it darkens light backgrounds and, if the
renderer component's UI delegate has gone stale (Synthetica under FlatLaf),
refreshes it. Undoes color mutations and restores original renderers on light.

### ScriptEditorTheme
The code editors — script console, project library, Vision component scripts,
Perspective transforms, Named Query editor — are `RSyntaxTextArea`s. They own
their colours through a `SyntaxScheme` rather than the look and feel, so nothing
else here reaches them: not the LAF swap, not the token mutation, not a
component walk.

Ignition ships a dark one. `NamedTheme` (in `common.jar`) is a platform enum with
`Default`, `Dark`, `VisualStudio` and `Disabled` members, each backed by a theme
XML, exposed through `getTheme()`. Using IA's own beats inventing one: it is
tuned for their syntax colours and follows their editor if they change it.

Two deliberate choices. `NamedTheme` is resolved **reflectively** — it is not SDK
surface, and a Designer without it should lose editor theming rather than the
whole dark mode. And the theme is applied **as-is**: `getTheme()` returns a
*shared* instance, so tuning its fields would repeat exactly the shared-singleton
mutation `IaColorTokens` refuses to make on `Base000`.

Switching tree nodes or editor tabs makes Ignition re-install its own scheme over
ours, so a guard on `RSTA.syntaxScheme` re-applies. It is scheduled rather than
immediate, because it fires from inside the property change that is still
installing the other scheme.

### ConsoleTextTheme
The Script Console's interpreter and the Output Console colour text per
character through the document, not through the component, so the LAF swap
leaves near-black output and `Color.blue` banners on a dark background.

`ConsolePanel` registers its colours as **named styles** on the styled document
— `regular`, `emphasize`, `error` — rather than stamping attributes onto each
run. That is the useful detail: restyling those style objects recolours all
existing *and future* text at once, and is exactly reversible. Rewriting
character attributes across the document would be neither, and could not be
undone once text scrolled away.

Styles are looked up by name, so only documents that define them are touched.
The restore distinguishes a style that had an explicit foreground from one that
inherited it, and removes the attribute rather than writing an explicit value
back.

### ComponentInspector
Debug only. **Cmd/Ctrl+Shift+I** (or `+F12`) dumps the component chain under the
mouse to the debug log — class, background/foreground with `UIResource` vs
explicit vs inherited markers, opacity, and UI delegate per level. This is the
primary tool for diagnosing a "still light" area. See
[DEVELOPMENT.md](DEVELOPMENT.md#the-inspector--diagnosing-a-still-light-component).

### DesignerStatus
The module's one-line channel to the user, via the Designer's own status bar
(`DesignerContext.getStatusBar()`, reached reflectively — it is not SDK
surface). It says a switch is under way before `apply` blocks the event
dispatch thread, and names the passes that failed afterwards. It also keeps the
bar legible: `StatusBar.setMessage` re-asserts `Color.black` on the message
label on every call, so under dark mode both the Designer's messages and ours
would be black on a dark bar — a listener lifts the foreground again each time,
the same shape as the white-background enforcer and for the same reason.

### DebugLog
Best-effort append-only log at `~/.ignition/designer-dark-mode.log`. The
Designer keeps its own logs in memory only; this file is the dev-loop's eyes.
**Timestamps are UTC.** Two levels: `log` always writes (switches, failures),
`detail` only under `-Ddesignerdarkmode.debug=true` (counts, per-event traces,
the dumps). The writer is opened once and held for the session — the detail
lines are unbounded, and each used to cost an open/write/close on the event
dispatch thread.

## Gotchas and hard-won facts

- **Toggle sometimes ignored.** With FlatLaf user scaling enabled, FlatLaf
  registers a permanent `UIScale` listener on the UI defaults; a later
  Synthetica `uninitialize()` fires `defaultFont = null` through it → NPE that
  aborts the switch, and only from the *second* toggle on. Fix:
  `System.setProperty("flatlaf.uiScale.enabled", "false")` before FlatLaf ever
  initializes (macOS is system-scaled, so this costs nothing), plus a one-shot
  retry around `setLookAndFeel`.
- **Broken first launch.** The Tools menu checkbox's `setSelected(...)` fires
  `itemStateChanged` during module startup, which used to apply the theme to a
  half-built Designer. All applies are gated behind a `uiReady` flag set once the
  main window and its panels exist.
- **Synthetica singleton.** Installing FlatLaf runs Synthetica's
  `uninitialize()`, which nulls its private static `activeInstance`; the Designer
  still calls `SyntheticaLookAndFeel.getInstance()` at runtime (UI scaling). We
  reflectively re-point `activeInstance` at the stock instance under dark to keep
  those calls alive.
- **JIDE `Theme.painter` map** is per-classloader; snapshot it **before** the
  JIDE reinstall (snapshotting after captures our own `BasicPainter` entries and
  the light restore then reinstalls the wrong painters).
- **`installJideExtension` clobbers standard Swing defaults**, not just JIDE's —
  `TextField.background`, `Table.background`, combo colors, menu colors. Snapshot
  *all* of FlatLaf's `lookAndFeelDefaults` right after `setLookAndFeel` and
  re-put them after the JIDE install.
- **Blank right-click menus.** Cached `JMenuItem`s keep `SyntheticaMenuItemUI`
  under FlatLaf (detached, so `updateComponentTreeUI` never reached them) and
  paint blank. Same disease as light tag-editor combo cells. Fix:
  `refreshStaleUiDelegates` at popup-show, renderer-paint, added-subtree rescan,
  and window open/activate.
- **Stale-delegate refresh must be per-paint, not once-per-component.** IA reuses
  one shared property-editor renderer and re-installs a Synthetica combo delegate
  when you switch tag-editor categories; a once-only guard leaves those combos
  light after a category switch. Check `hasStaleUi` every paint (it
  short-circuits, so it's cheap) behind a reentrancy flag.
- **Never wrap a JIDE `CheckBoxTree` renderer.** Its `CheckBoxTreeCellRenderer`
  calls back into the tree's configured renderer, so wrapping it recurses
  infinitely (`StackOverflowError`). `TreeIconRecolorer` skips them.
- **Restores must iterate tracked sets, not the hierarchy** (see above).
- **macOS native title bar** stays dark after a light switch unless the root
  pane's `apple.awt.windowAppearance` client property is explicitly cleared.

## When Ignition changes underneath us

Every Ignition class, field and `UIManager` key named in this document is an
implementation detail of the Designer, not public SDK surface. Any of them can
change in a point release without notice, and that is the expected maintenance
burden of this module rather than a bug in Ignition.

Names are resolved reflectively at runtime, and the module degrades in two
tiers when one stops resolving:

- **Phase 1 — the look-and-feel swap.** A failure here means the switch is
  genuinely off, so it is logged, said in the status bar, and `apply` returns.
  The Designer is left on the theme it had.
- **Everything after.** Each pass runs inside `safely(...)`, so a pass that
  throws is logged with its stack and the remaining passes still run. A renamed
  field costs one unthemed surface and a warning, not a broken switch. The
  failed pass names are collected and summarised in the status bar, so a
  half-dark Designer comes with an explanation rather than only a log line.
- **Afterwards, either way.** `finishSwitch()` squares the preference and the
  Tools menu checkmark with the look and feel that is *actually* installed. The
  checkmark used to track the request, so a switch that failed left it claiming
  a theme the Designer was not in, and the preference retried it at every
  launch.

That shapes how to debug a regression after an Ignition upgrade. A surface that
has gone light is usually a name that no longer resolves: check
`~/.ignition/designer-dark-mode.log` for the warning before assuming the theming
logic itself is wrong, and use the component inspector
(**Cmd/Ctrl+Shift+I**, see [DEVELOPMENT.md](DEVELOPMENT.md#the-inspector--diagnosing-a-still-light-component))
to identify what is actually painting the area now.
