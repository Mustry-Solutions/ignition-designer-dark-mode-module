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

1. **Look and feel swap.** Dark: first a copy of the DEVELOPER defaults —
   Ignition's own `UIManager.put`s, which Synthetica's uninstall is about to
   clear (see [DeveloperDefaults](#developerdefaults)) — then
   `UIManager.setLookAndFeel(new FlatDarkLaf())`,
   then `keepStockFont(...)` puts the `Label.font` read just before the swap
   as FlatLaf's `defaultFont`, so the switch is colour-only (see
   [Gotchas](#gotchas-and-hard-won-facts)). Light: reinstall the stock theme
   through Synthetica's own entry point. Wrapped in a one-shot retry. Then,
   light only and before anything else can ask Synthetica for a style,
   **prime the text styles** — `primeSyntheticaStyles()` builds a throwaway
   component of each text kind and updates it, because the first
   formatted-text-field style Synthetica serves after a reinstall is stale
   (#92, part 3; see [Vision](#vision)).
2. **Synthetica singleton** — `keepSyntheticaAlive()`, first of the `safely(...)`
   passes on the dark switch, because nothing else may call into Synthetica
   until it is back.
3. **Color tokens** — `IaColorTokens.install()` (dark) / `.uninstall()` (light).
4. **JIDE extension** — `installJideExtension(dark)`. FlatLaf isn't a look and
   feel JIDE recognizes, so under dark it must be told the VSNET style
   explicitly: `installJideExtension(1)`. Light only, straight after it:
   **developer defaults** — put back what Ignition had put at startup, over
   what JIDE has just re-put (`DeveloperDefaults.restore`).
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
   property so the native title bar follows the theme. A no-op elsewhere: on
   Windows and Linux the native title bar and frame stay light, by decision —
   FlatLaf's own window decorations on Ignition's frames would be a larger
   and riskier change than the gap justifies.
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
12. **Serializer clean copies** — `SerializerCleanCopies.refresh()`, last in
    both directions, once every default is where the next save will find it:
    the platform serializer's clean-copy cache was built under the look and
    feel that just left (see [SerializerCleanCopies](#serializercleancopies)).

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
panel's tile-selection color, the selected/hovered section card on an Event
Stream editor's `FlowCellContent`). Originals are snapshotted and restored on
light.

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

One exclusion: an IA SVG glyph whose tint is a token instance `IaColorTokens`
restyles (`SvgIconUtil.getIcon(name, w, h, Colors.IconDefault)` and friends) is
already light under dark mode, and the smart invert would turn it back into a
dim grey — the Event Stream editor's Enabled/Disabled toggles (#79). The pass
checks the tint by identity, not the render by brightness, so stock light glyphs
that still need inverting (#60) are unaffected.

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

### Vision
Dark mode works inside Vision since 0.4.0. Until then `VisionGate` refused
**Tools → Dark Mode** while a Vision window or template was open and dropped
a dark Designer to light on the way into the Vision workspace, because a
window saved from a FlatLaf Designer could not be opened by a Vision client.
Three things made that so, and each is fixed where it lives:

1. **The serializer's clean-copy cache** — built under whatever look and feel
   was installed at the first save and kept for the life of the Designer, so
   a save under the other look and feel wrote that look and feel's font,
   colours and border classes by name into the window. Refreshed at every
   switch: [SerializerCleanCopies](#serializercleancopies).
2. **The colour tokens Vision copies into components** — `PMIButton
   .initialize()` at palette drop, and `ComponentDeserializationHandler` for
   every loaded button, hand components the static
   `IgnitionLookAndFeel$Colors` objects, which dark mode rewrites in place,
   so a save made while dark wrote the dark values. Written with the stock
   value on every save: [TokenColorDelegate](#tokencolordelegate).
3. **Synthetica's stale first text style after a reinstall** — the first
   Vision text field the restore's tree update reached came back on the
   theme's raw Tahoma 11, and a save of it failed outright. Spent on a
   throwaway component by the restore's style primer (phase 1).

And one the gate had hidden: **Synthetica's uninstall clears the developer
defaults**, which left the Vision Property Editor blank after every switch
back ([DeveloperDefaults](#developerdefaults)).

What is left of the gate is `VisionWindows.isVisionTopLevel`, the by-name
test for the interface both `FPMIWindow` and `VisionTemplate` implement,
which the leftover pass uses to tell Vision content from the chrome around
it. The Vision jars are not SDK surface, so the Vision probe
(`designer/src/visionProbe/`, `-Pvision.jars`) pins that name against the
cached jars, along with the saves themselves: real `PMIButton`/`PMILabel`
/`PMITextField` created the way the palette creates them, Vision's own
serialization delegates and BeanInfos, a client-side load of every save,
across a stock → dark → stock cycle.

**What dark mode does to the Vision canvas.** The look-and-feel swap is
global: a Vision window open under dark mode shows its components in
FlatLaf's dark colours wherever they carry the look-and-feel defaults, which
is not what a Vision client, always light, will show. The colour passes
already leave the canvas alone; the look and feel underneath it cannot be.
That is the same trade the Exchange script avoids by never swapping the look
and feel, and it is documented in the README as a limitation. Nothing is
written into the window by it: the three fixes above are what the saves
depend on, and `ops/vision-check.sh` reads every saved window and template
back to prove it.

**History.** `VisionGate` was built on 2026-09-02, shipped in 0.3.0 with
three call sites in `ThemeManager` (refuse in `beginSwitch` and at startup,
drop out synchronously from a `WorkspaceManager` navigation listener, catch a
window attached under dark in the component watcher) and its own §N in the
QA checklist, and was removed once the probe showed the three fixes held on
the real classes and a live sitting had run the gate rows clean. The write-up
of the reproduction that led to it — the headless probe against the real
`vision-client`/`vision-designer` jars, the `SynthBorder` special case in
`AbstractEqualityDelegateSupport`, why an equality delegate cannot reconcile
a FlatLaf border with a Synthetica one — is in the 0.3.0 changelog entry and
the project notes.

### SerializerCleanCopies
The first of the three #92 pieces: the platform serializer's clean-copy cache
is replaced with an empty one as the last phase of every switch, in both
directions. `XMLSerializer.cleanMap` is a private static `HashMap`, looked up
with `get` and seeded with `Class.newInstance()` on a miss, so an empty map
simply rebuilds each entry under the look and feel current at the next save —
the one the saved components were dressed by — and the diff is clean again.
Replaced rather than cleared: a save in flight on another thread keeps its own
reference, and its late puts land in the discarded map. Not done in
`configureSerializer`, which runs per save and is where Vision seeds its own
`PathBasedVisionShape` copy.

`ReflectiveSurfaceTest` pins the field. `SerializerCleanCopyTest` drives the
platform serializer itself over a `JButton` with a Vision-style `BeanInfo`
(`SerializerProbeButton`) and shows both halves: a deliberately stale copy
writes `setBackground`, `setBorder <o cls="com.formdev.flatlaf.ui
.FlatButtonBorder"/>`, `setFont` and `setForeground` into a dark save; after
the refresh the same save is an empty element, and a hand-set tooltip still
round-trips. The class is reached by name; if the field moves, the phase fails
visibly (status bar and debug log) rather than leaving a stale cache behind a
passing switch. Each switch logs `SerializerCleanCopies: dropped N clean
copies`.

### TokenColorDelegate
The second #92 piece. Vision hands a component dropped from the palette the
static `IgnitionLookAndFeel$Colors` objects themselves (`PMIButton
.initialize()`: `setForeground(Colors.ButtonForeground)`; the state datasets
of the multi-state components; a check box's default background; a progress
bar's text colour), and under dark mode `IaColorTokens` has rewritten those
same objects in place, so a save made while dark writes the dark value into
the window. The delegate replaces the serializer's `java.awt.Color` entry on
every save (`DesignerModuleHook.configureSerializer`, which the Designer
calls on the fresh serializer it builds per save) with one that asks the
token pass, BY IDENTITY, for the colour's stock value and hands the
platform's own encoder a copy holding that instead. A user-picked colour at
the same RGB is a different object and is written as picked; dataset cells
go through the same path, since the platform serializes them one object at a
time. Pass-through while nothing is restyled, so it is registered light or
dark and the XML is byte-for-byte the platform's when the theme is stock
(asserted). The saved window then carries the stock value where a stock
Designer would have written nothing — an explicit colour equal to the
default, harmless on every client; that residue is Vision copying an object
rather than reading a default and cannot be removed here. `TokenColorOnSaveTest`
reproduces `initialize()` verbatim on `SerializerProbeButton` and on a
`BasicDataset` cell, with the control save first; `VisionWindowSaveTest`
(the Vision probe) does it on the real classes, including a window loaded
under dark, whose buttons get the tokens from Vision's deserialization
handler.

### DeveloperDefaults
Swing keeps the look and feel's defaults in a table every `setLookAndFeel`
replaces, and the *developer* defaults — what `UIManager.put` writes — in
the merged `UIDefaults` object's own storage, meant to outlive look-and-feel
changes. Ignition relies on that: `IgnitionLookAndFeel.init()` puts its
option-pane and file-chooser icons, the category icons of every JIDE
property table and the OK/Cancel mnemonics there at startup, and JIDE's
extension writes several hundred more. Synthetica's `uninitialize()`, run by
Swing when FlatLaf is installed over it, calls `clear()` on that merged
object, which empties the developer storage with the tables. The reinstall
on the way back lets Synthetica and JIDE put theirs again; Ignition's were
never seen again.

The one that showed (#102): `CategorizedTable.categoryExpandedIcon`. In a
Designer that has never been dark it is Ignition's vector chevron; after a
cycle it was missing, JIDE fell back to `Tree.expandedIcon`, and Synthetica's
tree icon painted outside a Synth context — by a JIDE renderer, not a tree —
handed Ignition's `TreeExpandedIconPainter` a context with no component.
Every paint of the Vision Property Editor then died in that painter, and the
editor was blank after every drop-out, on 0.3.0 as well.

So the dark switch copies the developer entries just before FlatLaf goes in,
and the light restore, after the stock reinstall and JIDE's re-put, puts back
every one that is missing or different — except what the reinstall must own:
Synthetica's objects, fonts, UI delegates, and the action and input maps
Swing installs lazily. `Synth.doNotSetTextAA`, which `init()` puts into the
look and feel's own table, goes back there. No reflection: the merged
object's `containsKey` is `Hashtable`'s own and answers for the developer
storage alone. Under dark the developer entries stay as FlatLaf and JIDE
leave them — a fidelity gap (FlatLaf's option-pane icons, JIDE's category
chevrons), not a defect. The harness's stock install now runs
`IgnitionLookAndFeel.init()` itself, so the #23 cycle test sees these puts
and would catch their loss; `PropertyEditorAfterRestoreTest` paints a JIDE
property table's category row across a cycle.

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

### EnvironmentProbe
Once per session, at the first switch, the debug log gets a block of host
facts: OS, JRE, the module-system and look-and-feel JVM arguments, whether
each `java.desktop` package the module needs is opened or exported to it,
the scaling properties (`flatlaf.uiScale.enabled`, `sun.java2d.uiScale`,
`GDK_SCALE`, FlatLaf's system/user factors, the screen transform),
Synthetica's own scale factor and font, and the `UIManager` font. Every
switch also logs the `Label.font` on either side. The module has only ever
been watched on macOS, and these are exactly the facts that can differ on
another platform — so a bug report from one carries the answers. Written at
`log` level on purpose: the reader of a bug report will not have had the
debug flag on. Diagnostic only; every step is guarded.

### DebugLog
Best-effort append-only log at `~/.ignition/designer-dark-mode.log`. The
Designer keeps its own logs in memory only; this file is the dev-loop's eyes.
**Timestamps are UTC.** Two levels: `log` always writes (switches, failures),
`detail` only under `-Ddesignerdarkmode.debug=true` (counts, per-event traces,
the dumps). The writer is opened once and held for the session — the detail
lines are unbounded, and each used to cost an open/write/close on the event
dispatch thread.

## Gotchas and hard-won facts

- **Synthetica's uninstall clears the developer defaults.** Every
  `UIManager.put` made before the switch to dark — Ignition's, JIDE's, the
  module's own — is gone once FlatLaf is in, without a property-change event
  (it is one `clear()`, not per-key removals). The module keeps a copy and
  puts it back on the light restore ([DeveloperDefaults](#developerdefaults));
  anything that must survive under dark has to be re-put after the swap.
  This is also why a diagnostic that watches `UIManager.getDefaults()` with a
  listener sees nothing.

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
- **"Inside Vision" is the canvas, not the package.** The passes that lift
  dark text and refresh dark leftovers must not touch Vision's user content,
  and the first cut keyed that on any ancestor from a `factorypmi` package.
  Vision's component palette and property editor are `factorypmi` classes
  too, so after a switch back to light their filter
  fields stayed dark — the same JIDE parent-first quirk as #45, on the two
  components the fix for #45 was told to skip. The test is now an ancestor
  that is a Vision `TopLevelContainer` or the Designer's
  `AbstractDesignableWorkspace`. Related: IA's `PanelBasedTreeCellRenderer`
  reads the `Tree.*` colours once, in its constructor, and has no `updateUI`;
  the Tag Browser creates new ones while dark, so the light restore re-syncs
  the renderer of every tree it walks (`TreeIconRecolorer.syncRendererColors`).
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
- **The font changed on every toggle.** Left alone, FlatLaf resolves the
  operating system's UI font (the harness log on macOS: `Dialog 12pt` →
  `Helvetica Neue 13pt`) and the stock restore puts `Dialog 12` back by
  name. A point on macOS, unnoticed; on Windows the pick is Segoe UI at the
  desktop's message-font size, and text that grows shifts row heights and
  clips labels in fixed-size panels. `keepStockFont` reads `Label.font`
  before the swap and puts it as `defaultFont` right after — before
  `snapshotMenuDefaults` resolves FlatLaf's active font values into the
  snapshot, which is what makes the pin reach every `*.font` key. It is in
  that snapshot, so the light clear removes it with the rest. Reading it
  live rather than hard-coding `Dialog 12` also carries Synthetica's scale
  factor into FlatLaf on a scaled display. Pinned by
  `darkModeKeepsTheStockFont` in the harness, which runs on all three
  platforms in CI.
- **`--add-opens java.desktop/java.awt` is a launcher fact, not a module
  one.** `IaColorTokens` needs it, the Designer Launcher passes it on macOS,
  and nobody has read the launcher's command line anywhere else. Without it
  every token-coloured surface stays light while the rest goes dark.
  `install()` throws `JvmNotOpened` for exactly the
  `InaccessibleObjectException` case — the one failure the user can fix —
  and the degraded status line carries the argument and where it goes in
  the launcher, ahead of the log pointer. Verified by running the harness
  with that opening removed: 1 of 23 phases fails, everything else
  completes.
- **`flatlaf.uiScale.enabled=false` is justified by macOS.** The comment
  says system scaling covers it, which is true there and on Windows (Java
  9+), and doubtful on a HiDPI Linux desktop, where FlatLaf user scaling is
  the usual path. Deliberately left as is until a Linux `env:` block shows
  what the JVM actually sees; if it does render wrong, the fix is a
  platform conditional that keeps the retry, not a plain re-enable, and the
  `theFlatLafScalingListenerIsNeverRegistered` pin becomes conditional too.

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
