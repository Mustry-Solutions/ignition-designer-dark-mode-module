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

1. **Look and feel swap.** Dark: `UIManager.setLookAndFeel(new FlatDarkLaf())`.
   Light: reinstall the stock theme through Synthetica's own entry point.
   Wrapped in a one-shot retry (see [Gotchas](#gotchas-and-hard-won-facts)).
2. **Synthetica singleton** — `keepSyntheticaAlive()`, first of the `safely(...)`
   passes on the dark switch, because nothing else may call into Synthetica
   until it is back.
3. **Color tokens** — `IaColorTokens.install()` (dark) / `.uninstall()` (light).
4. **JIDE extension** — `installJideExtension(dark)`. FlatLaf isn't a look and
   feel JIDE recognizes, so under dark it must be told the VSNET style
   explicitly: `installJideExtension(1)`.
5. **Theme painters** — `overrideThemePainters(dark)` repoints JIDE's painter
   map (see below).
6. **Default re-assert** — `applyMenuDefaults(dark)` re-puts *all* FlatLaf
   defaults on top (the JIDE extension clobbers standard Swing keys), then
   `applyJideDarkOverrides(dark)` sets the JIDE-specific keys.
7. **Renderer colour capture** (dark only) — `CellRendererSanitizer
   .captureStockColors()`, before the tree update, because
   `JTableHeader.updateUI()` nulls a cell renderer's colours and a renderer that
   colours itself in its constructor never gets them back.
8. **`updateComponentTreeUI`** on every window.
9. **macOS title bars** — set/clear the `apple.awt.windowAppearance` client
   property so the native title bar follows the theme.
10. **The passes** (dark only): tree icons, button/label icons, cell-renderer
    sanitizer, collapsible title panes, white-token background and border swaps,
    script editors, **JIDE code editors**, **diagnostics chart axes**, console
    output styles (including the Output Console's per-run colours), block
    workspaces, status-bar legibility, cached JIDE painters, stale-delegate
    refresh in secondary windows. On light, the corresponding restores — plus a
    **dark-leftover pass** that re-runs `updateUI()` child-first on anything
    still wearing a dark look-and-feel colour.
11. **Component watchers.** The dark watcher is installed on dark and removed on
    light. A much smaller **light watcher** takes its place on the light side,
    re-running the dark-leftover pass when a subtree is attached — a dock
    detached during the restore keeps its dark state, and re-attaching it
    recreates the parent-first copy that leaves JIDE wrappers dark.

On light mode, the restores iterate **tracked component sets**, never the live
hierarchy — a component detached at restore time (a closed dialog, a hidden
section) would be missed by a walk and come back stuck dark. The converse is
what the light watcher is for: state the restore could not reach because the
subtree was not attached at all.

## The components

### ThemeManager
The orchestrator. Owns the preference (`java.util.prefs`, node
`com/mustrysolutions/designerdarkmode/designer`, key `darkMode`), the `uiReady` gate, the
per-phase error isolation, the JIDE/Synthetica/FlatLaf plumbing, the
`UIManager` key overrides (dock, collapsible-pane, and container keys), the
component watcher, and the popup / stale-delegate handling.

The preference node is `Preferences.userNodeForPackage(ThemeManager.class)`:
it lives on the machine running the Designer, under the OS user's own
preference store, and holds a single boolean. Nothing gateway-specific goes
into the path or the key, so the value is shared by every Designer that user
launches against any gateway carrying the module; a gateway without the module
never loads `ThemeManager` and simply ignores it. `finishSwitch()` writes the
node after every switch and every startup apply, so a failed apply against one
gateway resets the value for all of them (see
[When Ignition changes underneath us](#when-ignition-changes-underneath-us)).
Keying the node by gateway would be the change to make if a per-gateway
setting is ever wanted.

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

Wrapping a **list** renderer has one rule that is not obvious:
`getCellRenderer()` is not always the renderer `setCellRenderer()` replaces.
JIDE's `CheckBoxList` returns a `CheckBoxListCellRenderer` it re-points at
JList's own renderer field on every call, and SwingX's `JXList` keeps a
`DelegatingRenderer` in that field and pushes what you set into *it*. Wrapping
what came back therefore built a delegation cycle and overflowed the stack on
the first painted cell. Both publish the real renderer separately
(`getActualCellRenderer`, `getWrappedCellRenderer`), so the wrapper goes *under*
the decorator; a decorating list that publishes nothing is left unwrapped, since
there is no way to reach its renderer or to put one back. A census in the
look-and-feel harness pins the assumption to the Designer's actual classpath.

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

### CodeEditorTheme
JIDE's `com.jidesoft.editor.CodeEditor` — the Database Query Browser and **every
expression editor in the Designer** (46 referring classes in 8.3.6). Its colours
live on the component and in its `SyntaxStyleSchema`, not in `UIManager`, so the
look-and-feel swap never touched them: a cream current-line band, a black caret
on dark chrome, and syntax tokens at `#000000`, `#000080`, `#650099`.

Foregrounds are **lifted, not replaced** — each keeps its hue and is raised to a
readable *luminance*, so a keyword stays blue and an error stays red. Note
luminance, not HSB brightness: blue carries 11% of perceived luminance against
green's 59%, so `#0000FF` at full brightness still measures 29 and is
unreadable.

Failures are isolated **per property and per editor**. JIDE throws
`IndexOutOfBoundsException: Wrong line: -1` out of `setBracketHighlightColor` on
an editor with no valid caret line; treating that as systemic once disabled
theming for every expression editor for a whole session.

### DiagnosticsChartTheme
The Diagnostics performance charts. IA colours the JFreeChart plot background
and gridlines from its own design tokens, which `IaColorTokens` already
restyles — so the plot came out right and the rest did not. What IA never sets
is the **axis paints** (JFreeChart defaults them to `Color.black`) and the
**chart background** (white). This pass sets both and restores them.

Targeted at `DynamicTimeSeriesChart` by name rather than at any `ChartPanel`:
Vision windows render *user* charts, and repainting those would misrepresent
what an operator sees.

### BlockWorkspaceTheme
Alarm pipeline and SFC blocks. A block *is* a component — `BasicBlockUI extends
JPanel` — but it does not paint from its own background: `paintComponent` fills
a shape with one of two `Color` fields and strokes it with one of three others,
all assigned literals in the constructor. No look-and-feel swap reaches a
literal, so the inspector shows nothing wrong while the screen does. The pass
darkens the fills rather than correcting the labels, judged on each colour's own
luminance.

### VisionGate
Not a theming pass: the reason dark mode and Vision are kept apart, and the
mechanism that keeps them so.

Vision saves a window by serializing every component property that differs
from a *clean copy* of the component's class (`XMLSerializer.getCleanCopy`),
and it caches that clean copy in a **static map for the life of the Designer**,
constructed under whatever look and feel was installed the first time the
class was saved. Property equality is `equals`, except that a border whose
class is literally named `SynthBorder` is deemed equal to anything — a stock
look-and-feel assumption baked into `AbstractEqualityDelegateSupport`. FlatLaf
borders extend `BasicBorders$MarginBorder`, so they miss that exception and
get written by class name. (`BorderUIResourceDelegate` is not a way out: it is
keyed on `BorderUIResource` and serializes the wrapped border, it does not
skip it. Equality delegates are consulted only when both operands share an
exact class, so none can reconcile a FlatLaf border with a Synthetica one.)
The consequences, reproduced headlessly against the real
`vision-client`/`vision-designer` jars (2026-09-02):

- a stock-born window saved after switching to FlatLaf gains `setFont
  Helvetica Neue 13`, `setForeground`, `setBackground`, `setButtonBG`,
  `setMargin` and `<o cls="com.formdev.flatlaf.ui.FlatButtonBorder"/>` on
  every component;
- that XML fails to load without FlatLaf on the classpath — every Vision
  client, and every Designer without this module — with
  `ClassNotFoundException`, not a warning;
- in the harness, a window deserialized under FlatLaf and then restored to
  stock still failed to save (`Unable to create clean copy of
  de.javasoft.plaf.synthetica.ScalableFont`). A live 8.3.6 Designer hands
  components a plain `FontUIResource` rather than Synthetica's `ScalableFont`
  (the module logs the class at startup), so that particular failure is a
  harness artefact — but the gate treats the round trip as damage anyway,
  because the clean-copy cache makes any toggle-then-save suspect.

Hence the gate, in three parts, all in `VisionGate` and its three call sites
in `ThemeManager`:

1. **Refuse** — `beginSwitch(true)` and `applyStartupPreference()` ask
   `blockingReason()`: any `TopLevelContainer` (the interface both
   `FPMIWindow` and `VisionTemplate` implement) under any window, or the
   `WorkspaceManager`'s selected workspace keyed `windows`. A refused click
   resets the preference and the menu; a refused startup keeps the preference,
   and so does a drop-out (2 and 3 below) — nothing failed, and a launch away
   from Vision should come up dark again. The click asks twice: once when the
   menu item is ticked and again one event turn later when the theme is
   actually installed, because a click on a Vision node queued behind the
   menu click selects the Vision workspace in between, and the first answer
   is stale by then. If dark mode is somehow already on when the gate blocks
   (a rebuilt menu re-asserting the preference), Vision wins and the Designer
   drops out rather than reporting a refusal.
2. **Leave first** — `watchNavigation` adds a `WorkspaceNavigationListener`
   proxy to the `WorkspaceManager`. Selecting a Vision node in the project
   browser activates the `windows` workspace on the FIRST click, synchronously;
   the window is deserialized on the second. `leaveDarkForVision` runs
   `apply(false)` right there, in the listener, so the window is born under
   Synthetica. Deferring it one event turn would race the second click.
3. **Catch the rest** — the dark-mode component watcher checks every attached
   container (four levels deep) for a `TopLevelContainer`; a hit ends dark
   mode on the next turn and tells the user to close and reopen the window.

Everything Vision-side is reached by name — `TopLevelContainer`,
`WorkspaceManager`, `IgnitionDesigner.getWorkspace()` — so a Designer without
Vision loses the gate, not the module. The Designer-side names are pinned by
`ReflectiveSurfaceTest`; the Vision one cannot be (its jars are not a published
artifact) and is checked by hand in the QA checklist, §N.

**Why not fix the serializer instead.** It can be reached: every module's
`DesignerModuleHook.configureSerializer(XMLSerializer)` runs on the fresh
serializer `DesignerContextImpl.createSerializer()` builds for each save,
`XMLSerializer.setCleanCopy(Class, Object)` is public static, and the cache
behind `getCleanCopy` is a plain static `HashMap`. A headless probe against
the real 8.3.8 platform and Vision 12.3.8 jars (2026-09-15, nine scenarios:
`BasicContainer` with a button, label and text field, saved across a
stock → dark → stock cycle) showed that clearing that cache after each
look-and-feel switch removes every FlatLaf class name, `setFont` and
look-and-feel colour from every save — dark save of a stock-born window,
dark-born window, stock-saved window reloaded under dark, and the light
saves after the switch back — while hand-set values (`setButtonBG`,
`setText`) still round-trip. The crash, in other words, is curable from
here.

What is not: a window LOADED under dark still saves `setForeground #DDE0E3`
on its buttons. `PMIButton.initialize()` copies the static
`IgnitionLookAndFeel$Colors.ButtonForeground` object into the button's
foreground; `IaColorTokens` rewrites that object to Base900 (#DDE0E3) under
dark, while the clean copy's foreground is FlatLaf's `Button.foreground`
UIResource (#DDDDDD), so the two differ and the DARK text colour is baked
into the window — light-grey text in a light Vision client. Under the stock
theme the constant equals the look-and-feel default, which is the assumption
Vision relies on. Nine `factorypmi.application.components` classes read
`Colors.*` this way: `PMIButton`, `PMIToggleButton`, `PMINStateButton`,
`PMIControlButton`, `PMIMultiStateIndicator` (button colours and the
indicator colours), `PMICheckBox` and `PMIRadioButton` (Base100),
`PMIProgressBar` (Base100, Base900, Primary), `PMITextArea` (Base000,
NonEditableBackground). Dark mode inside Vision therefore needs the cache
refresh AND either FlatLaf defaults kept equal to the rewritten constants for
those keys or those constants left alone in Vision, plus one more thing the
probe surfaced: after the light restore a text field still held Tahoma 11
while the defaults said Dialog 12 until a second `updateComponentTreeUI` —
the restore's phase order leaves fonts stale, which would write `setFont`
into any window open across the switch. That is a follow-up feature, not a
swap for the gate; the probe recipe is in the project notes.

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

- **The Designer calls `getModuleMenu()` again during its own teardown**
  (`IgnitionDesigner$LoadedModule.shutdown()` does so twice before
  `hook.shutdown()`, from both exit and opening another project). A
  `StateChangeAction` fires `itemStateChanged` from `setSelected`, so seeding
  the Tools menu checkbox from the preference used to read as a click at that
  moment — harmless while the preference always matched the screen, a refusal
  dialog on the way out plus a wiped preference once a Vision-blocked launch
  could keep "dark" saved with a light Designer. The hook seeds under its
  `syncing` guard, and `ThemeManager.setDark` ignores requests after
  `shutdown()`.
- **Toggle sometimes ignored.** With FlatLaf user scaling enabled, FlatLaf
  registers a permanent `UIScale` listener on the UI defaults; a later
  Synthetica `uninitialize()` fires `defaultFont = null` through it → NPE that
  aborts the switch, and only from the *second* toggle on. Fix:
  `System.setProperty("flatlaf.uiScale.enabled", "false")` before FlatLaf ever
  initializes (macOS is system-scaled, so this costs nothing), plus a one-shot
  retry around `setLookAndFeel`.

  Measured since, both ways. With scaling on, `UIScale$1` lands on **all three**
  tables — `UIManager`, the developer defaults and the look-and-feel defaults —
  and is still on all three *after* the stock look and feel is back: "permanent"
  is literal. With the property as shipped it is never registered at all. The
  whole defence is therefore one ordering-sensitive line in `startup()`, so the
  harness now pins it (`theFlatLafScalingListenerIsNeverRegistered`).

  **This is not the cause of [#12][12].** That NPE was attributed to this
  listener; with the property as shipped there is no listener to fire and none
  to unregister, so both fixes suggested there aim at the wrong thing. No
  `UIManager` font default is null at any point in a cycle either (36 under
  stock, 72 under dark, none null). See the issue for what the evidence does
  point at.
- **Broken first launch.** The Tools menu checkbox's `setSelected(...)` fires
  `itemStateChanged` during module startup, which used to apply the theme to a
  half-built Designer. All applies are gated behind a `uiReady` flag set once the
  main window and its panels exist.
- **Synthetica singleton.** Installing FlatLaf runs Synthetica's
  `uninitialize()`, which nulls its private static `activeInstance`; the Designer
  still calls `SyntheticaLookAndFeel.getInstance()` at runtime (UI scaling). We
  reflectively re-point `activeInstance` at the stock instance under dark to keep
  those calls alive. It is matched **by name** in a jar we do not control, so the
  way it breaks is a Synthetica upgrade renaming it — for everyone at once, on
  one release. That is why the repair runs under `safely(...)` and **rethrows**
  rather than logging: a swallowed failure left the switch reporting complete
  success while every `getInstance()` call NPE'd ([#35][35]). The harness pins
  both halves.
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

[35]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/35

[12]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/12

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

Afterwards, either way, `finishSwitch()` squares the preference and the Tools
menu checkmark with the look and feel that is *actually* installed. The
checkmark used to track the request, so a switch that failed left it claiming a
theme the Designer was not in, and the preference retried it at every launch.

That shapes how to debug a regression after an Ignition upgrade. A surface that
has gone light is usually a name that no longer resolves: check
`~/.ignition/designer-dark-mode.log` for the warning before assuming the theming
logic itself is wrong, and use the component inspector
(**Cmd/Ctrl+Shift+I**, see [DEVELOPMENT.md](DEVELOPMENT.md#the-inspector--diagnosing-a-still-light-component))
to identify what is actually painting the area now.
