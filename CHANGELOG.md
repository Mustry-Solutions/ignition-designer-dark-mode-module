# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Note that released module versions are plain `x.y.z`: Ignition's `module.xml`
version parser is numeric-only and rejects a prerelease suffix at install time.

## [Unreleased]

### Changed

- The module's description in **Config → Modules** and the license shown at
  install now say who makes the module and where to find Mustry Solutions'
  other Ignition modules. Release pages get the same short footer.

### Fixed

- **A light restore no longer strips default-renderered trees of their icons
  or leaves plain tables painting every other row dark**
  ([#42](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/42)).
  The renderer wrappers dark mode installs were still in place during the
  light tree update, and a look and feel only exchanges renderers it
  recognises: `SynthTableUI`/Synthetica replace a `UIResource` table
  renderer, `BasicTreeUI` drops the tree renderer it created — and any
  `setCellRenderer` call, ours included, clears its `createdRenderer` flag.
  So the restore handed every such tree FlatLaf's plain
  `DefaultTreeCellRenderer` (no icons under Synthetica, smaller rows) and
  every such table the `DefaultTableCellRenderer.UIResource` FlatLaf had
  left there, still on FlatLaf's delegate with FlatLaf's dark background.
  Both wrappers are now unwrapped before the tree update, and a tree whose
  renderer was the look and feel's own gets `null` back so its UI does the
  exchange exactly as at startup. Found by the new windowed harness on its
  first render; its pixel test now pins a light theme identical after a
  cycle, and a mutation sweep shows either half of the fix being dropped
  fails it.

- **Two follow-on cases of the same unwrap, found reviewing it**
  ([#42](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/42)).
  A tree wrapped while it still had the look and feel's renderer and given
  its own one later — a view that builds its renderer when its model arrives
  — kept the first wrap's mark for the whole dark session, so the restore
  handed it `null` and its renderer was gone; the mark is now re-assigned on
  every wrap. And a table that entered the UI AFTER the switch to dark was in
  no colour record, because `captureStockColors` is a phase of that switch:
  with the unwrap now ahead of the tree update, `JTable.updateUI()` reaches
  such a renderer and nulls both its colours, so one that colours itself in
  its constructor came out of the restore with none. Those colours are now
  recorded as the renderer is wrapped.

### Added

- **The look-and-feel harness has a windowed mode**
  ([#42](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/42)).
  `./gradlew :designer:lafHarness -Pharness.windowed=true` drops the
  headless flag, adds the one JDK opening a light-theme render needs
  (`javax.swing.tree`, for Synthetica's `LabelPainter`) and keeps macOS out
  of the Dock. `WindowedCycleTest` builds a packed, never-shown `JFrame` of
  Designer-like shapes and drives `apply(true)`/`apply(false)` over it the
  way a Designer does — the first time the passes that walk
  `Window.getWindows()` have run under test at all. It pins: a swapped
  white or light-neutral background comes back as the same instance; every
  cached JIDE `ThemePainter` field is `BasicPainter` under dark and
  Synthetica's after the restore, including on a component that never
  re-reads the map; a `JInternalFrame` whose content pane throws on a null
  background survives the tree update with its layout (the Vision crash in
  plain Swing); and the light theme renders pixel-identical after a cycle,
  against a baseline of one plain `updateComponentTreeUI`. Headless, the
  class skips itself; with the property given and no display it fails, so a
  runner that loses its display shows as red. CI runs the harness windowed
  on all three platforms, Linux under Xvfb. The old "not from a Gradle test
  worker" trap — JIDE's unlicensed-use dialog hanging the worker — is gone:
  the harness runs `IgnitionLookAndFeel.init()` since #102, which is where
  the Designer licenses JIDE.

### Changed

- **`flatlaf.uiScale.enabled=false` is documented as required on every OS,
  not a macOS-only optimisation**
  ([#76](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/76)).
  FlatLaf system scaling (JDK HiDPI) is untouched by the property; only
  user scaling is off, because its permanent `UIScale` listener NPEs on a
  later Synthetica uninitialize. A new harness test simulates
  Synthetica-scaled fonts (Dialog 18 / 24 pt) and pins that dark mode keeps
  that size via the existing font pin, so it cannot come out undersized
  relative to stock. Re-enabling user scaling would stretch insets on top
  of that font on Linux/macOS and buy nothing on Windows (FlatLaf's own
  guard), while bringing the listener back everywhere. The `startup`
  comment, the ARCHITECTURE gotcha, and the QA checklist are updated to
  match; a live HiDPI `env:` block ([#96](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/96))
  remains the only real corroboration.
- **`WorkspaceTabStripTest` now does its Swing work on the dispatch thread
  and clears its Windows spoof through `setDesktopProperty`.** The reflective
  map removal it used never worked headless (the default toolkit is a
  `HeadlessToolkit` whose own map is empty), so the spoofed 3D colours
  outlived the test. The JIDE layout also ran on the test thread while JIDE
  had posted dispatch-thread work for the same pane; one Windows CI run
  (floor SDK) hit `No such child: 6` out of that layout, which needs the
  child list to change under it. Same remedy as #95; not reproduced on a
  Mac, so recorded as the likely cause, not a proven one.

## [0.4.2] - 2026-09-18

Two fixes for things reported on 0.4.1, neither of which touches the theme
itself: the gateway's Modules page called the module "Trial", and a project
carrying the Exchange dark-mode script could paint over it.

### Fixed

- **The Exchange "Dark Mode for the Designer" script no longer paints over
  the module.** A project that imported that script adds its own View →
  Dark Mode checkbox; ticked on top of this module's theme it painted black
  fields and grey table cells that a toggle off and on did not clear, and
  unticked again it painted the toolbar and dock title bars white. The
  module now greys that checkbox out with a tooltip saying why, unticks it
  if it finds it ticked, and says so once in the status bar and the log.
  The script's paints are never undone: a box ticked before the module saw
  it means a relaunch. Remove the project's `designerPatch` Vision client
  tag to silence the notice
  ([#89](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/89)).
- **One tree with a foreign listener no longer stops the tree-icon pass for
  every tree after it.** The Exchange script's tree listener throws inside
  `setCellRenderer`; the pass now contains that per tree.
- **Config → Modules listed the module as "Trial".** The module has said
  `<freeModule>true</freeModule>` in its module.xml since 0.1.0, but the 8.3
  gateway never reads that element: the one thing it consults is
  `isFreeModule()` on the module's *gateway*-scope hook, and a designer-only
  module has none to ask, so the license evaluation fell through to the
  platform trial state. The module now ships a gateway hook whose sole
  method returns `true`. Nothing else changes — there was never a gate, and
  the "Trial" row cost nothing but confusion. The Modules page reads *Free*
  after the upgrade
  ([#114](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/114)).

## [0.4.1] - 2026-09-17

A patch for 0.4.0, which every 0.4.0 user should take: a Comments Panel
dropped under dark mode and saved left the window unopenable in a Vision
client. Found the same day, in the live check of the palette kinds the
headless sweep could not build.

### Fixed

- **A Comments Panel dropped under dark mode made its window unopenable in a
  client.** Found on 0.4.0. The panel is built without a border; the
  Designer's tree update gives it FlatLaf's, and the save wrote
  `com.formdev.flatlaf.ui.FlatScrollPaneBorder` by name, which no client can
  resolve. Two fixes: the serializer's own "two Synthetica borders are
  equal" rule is extended to every FlatLaf border class, so a look-and-feel
  border is never written; and after every tree update under dark a Vision
  component's look-and-feel border is put back to what a fresh one has,
  which is what the save compares against. The headless sweep had missed it
  because it never tree-updated a dark-born component before saving; it
  does now, and it builds the Comments Panels and the Spinner too. A window
  already saved this way is repaired by any save from a Designer running
  this fix, in either theme; on 0.4.0 itself, by a light save.
- **A Date Time Popup Selector lost its border after a switch to dark**, and
  a dark save then wrote an explicit empty border a client showed. The same
  alignment puts it back.

## [0.4.0] - 2026-09-17

Dark mode inside Vision. The 0.3.0 gate that refused dark mode while a
Vision window was open, and dropped a dark Designer to light on the way into
Vision, is gone: what made it necessary is fixed at the serializer, proven
against every Vision palette component headlessly and on real windows in a
Vision client. Also fixed: a blank Vision Property Editor after any switch
back to light, present since 0.3.0.

### Changed

- **Dark mode stays on inside Vision.** Tools → Dark Mode applies with
  Vision windows and templates open, a dark Designer stays dark when you
  navigate to Vision, and a dark preference applies on a launch onto the
  Vision workspace. The refusal dialog, the drop-out and the "close and
  reopen" notice of 0.3.0 are removed. What remains is a limitation, not a
  defect: a Vision window open under dark mode renders in FlatLaf's dark
  colours wherever its components use the look-and-feel defaults, which is
  not what a light Vision client shows. Nothing is written into the window
  by it. `VisionWindows` keeps the one thing the gate knew that the colour
  passes still need, how to recognise a Vision window or template by name.

### Fixed

- **A Vision window saved from a dark Designer is the window a stock
  Designer would have saved** (#92). Four things could put a look-and-feel
  value into the file, and each is fixed where it lives:
  - *The serializer's clean-copy cache.* The platform compares each saved
    component against a clean instance of its class, cached for the life of
    the Designer under whatever look and feel was installed at the first
    save; a save under the other look and feel wrote that look and feel's
    border, font and colours into the window, and the FlatLaf border by
    class name is what a Vision client could not load. The cache is now
    replaced with an empty one as the last phase of every switch
    (`SerializerCleanCopies`).
  - *Ignition's colour tokens.* Vision hands components the static
    `IgnitionLookAndFeel$Colors` objects — at palette drop and, for every
    loaded button, from its deserialization handler — and dark mode rewrites
    those objects in place, so a save made while dark wrote the dark values:
    light-grey text on a light client. The module's `java.awt.Color`
    delegate, on every save, recognises a restyled token by identity and
    writes its stock value (`TokenColorDelegate`). A colour the user picked
    is a different object and is written as picked; dataset cells go through
    the same path.
  - *Inherited look-and-feel colours.* A component with no foreground of its
    own (rectangle, barcode, paintable canvas, cylindrical tank, fill level
    indicator) inherits its container's `Panel.foreground`, which Vision
    writes; under dark that was FlatLaf's near-white. Written as the stock
    value by the same delegate (`LookAndFeelColors`), which is now registered
    for `ColorUIResource` as well, since the platform keys its delegates by
    exact class.
  - *The Tree View's sample rows.* Its constructor copies four `Tree.*`
    colours into its sample dataset as strings, which no save-time delegate
    can see; a Tree View dropped under dark could carry FlatLaf's tree
    colours. They are put back to stock as the component is attached
    (`VisionConstructionColors`).

  Proven by `VisionCorruptionSweepTest` in the new Vision probe: every one
  of Vision's palette components that builds headlessly (55 of 61), built
  and saved under dark, born under stock and cycled twice with the window
  open, with hand-set values, nested with a template, and as a full window,
  serializes to the same bytes a stock Designer writes, net of four
  documented residues (a stock token value written explicitly, a drop size
  a couple of pixels smaller, a combo box row count of 15, chart sample
  data). Confirmed live: windows and templates saved under dark and after
  the restore, and a real Vision client opened them showing what a stock
  save shows.

- **A save after switching back to light no longer fails on a component
  built under dark.** After Synthetica is installed a second time in the
  same JVM, the first style it serves for each component kind still carries
  the theme's raw font, so the first Vision component of that kind the
  restore reached came back on Tahoma 11 and its save failed outright, since
  a Synthetica `ScalableFont` that differs from the clean copy cannot be
  serialized. The light restore now spends that stale request on a throwaway
  component of every Swing kind, straight after the reinstall. Found on the
  text field headlessly and on a progress bar live; reproduced without
  Vision in `RestoredTextFieldFontTest`.

- **The Vision Property Editor no longer paints blank after a switch back to
  light** (#102, #61; present since 0.3.0). Synthetica's uninstall, which
  the switch to FlatLaf triggers, clears Swing's developer defaults — the
  `UIManager.put`s Ignition makes at startup, among them the category icons
  of every JIDE property table. After the restore JIDE fell back to
  Synthetica's tree icon, which cannot paint outside a Synth context, and
  every paint of the editor threw. The dark switch now copies the developer
  entries first and the light restore puts back what is missing
  (`DeveloperDefaults`), so the editor, the option-pane and file-chooser
  icons and the OK/Cancel mnemonics come back as they were. Confirmed live.

- **The dark passes leave Vision window content alone.** The pass that
  swaps a component's literal white token background for the module's dark
  surface had no Vision guard and set an explicit colour on Vision text
  fields, which a dark save then wrote as if set by hand. It now skips every
  Vision window and template.

### Added

- **A Vision probe.** `./gradlew :designer:visionProbe
  -Pvision.jars="$(ops/vision-jars.sh)"` compiles a source set against the
  Vision jars in the Designer's module cache (never published, so CI never
  sees it) and saves and loads real Vision windows across a theme switch
  with Vision's own delegates and BeanInfos, on the whole palette. It pins
  the `TopLevelContainer` name the module keys on, which the QA checklist
  had to verify by hand. The debug log gains one line per switch,
  `SerializerCleanCopies: dropped N clean copies`, and one per restore,
  `DeveloperDefaults: restored N of M developer defaults`.

## [0.3.0] - 2026-09-16

A Vision-safety and portability release. Dark mode now keeps out of Vision's
way, since a Vision window saved under it could not be opened by a Vision
client; three user-reported defects are fixed (Perspective property names,
the Event Stream editor, the Windows workspace tab strip); the Designer's
font survives the switch; and the harness runs on Windows and macOS in CI.
The Windows fixes are proven headlessly and not yet seen by eye on Windows.

### Added

- **Dark mode and Vision are kept apart: Tools → Dark Mode is refused while
  a Vision window or template is open, and a dark Designer turns itself
  light when you navigate to Vision.** Paul Griffith's warning on the
  announcement thread was right, and reproducible: the platform's window
  serializer compares every component property against a clean copy cached in
  a static map for the life of the Designer, so once FlatLaf has been installed
  a Vision save writes FlatLaf's font, colours and border classes into the
  window — `<o cls="com.formdev.flatlaf.ui.FlatButtonBorder"/>` — and a Vision
  client, which has no FlatLaf, fails to open it
  (`ClassNotFoundException`). Reproduced headlessly against the real Vision
  jars; the mechanism is IA's, and no restyling on our side reaches it. A
  serializer-side cure for the crash does exist (refreshing the platform's
  clean-copy cache at each switch, verified headlessly), but it leaves Vision
  baking the module's dark colour constants into freshly opened components,
  so dark mode inside Vision stays a follow-up
  ([ARCHITECTURE.md](docs/ARCHITECTURE.md#visiongate)).

  So the module now stays out of Vision's way. `VisionGate` refuses **Tools →
  Dark Mode** while a Vision window or template is open or the Vision
  workspace is selected (status bar plus a dialog, preference and menu reset
  to light); a dark Designer drops to the stock theme synchronously from the
  workspace manager's navigation listener when the user selects Vision in the
  project browser — the first click of a double click, before the window is
  deserialized under FlatLaf; and a Vision window that is attached under dark
  mode by any other path still ends dark mode, with a "close and reopen"
  notice, since that window has already been through the round trip. A dark
  preference is kept, not applied, when the Designer comes up on Vision, and
  kept when a dark Designer drops out for Vision — only a refused click resets
  it. The gate is asked again at the moment the theme is installed, one turn
  after the click, since a Vision selection can land in between. The Tools
  menu is seeded without firing a switch, because the Designer rebuilds
  module menus during its own teardown and a kept preference used to read as
  a click on the way out. Vision and the Designer's `WorkspaceManager` are
  reached by class name, so a Designer without Vision loses the gate rather
  than the module.

- **Vision's palette and property-editor filters, and the Tag Browser's rows,
  come back light after the Vision gate drops dark mode.** Found in the first
  live run of the gate. Two mechanisms, both reproduced in the harness:
  the child-first leftover pass that fixes #45 skipped anything under a
  `factorypmi` package, which was meant to protect Vision's user content but
  also covered Vision's own dock frames — "inside Vision" now means under a
  Vision window or template or the workspace that hosts them; and IA's
  `PanelBasedTreeCellRenderer` (the Tag Browser's renderer) copies the
  `Tree.*` colours out of UIManager in its constructor with no `updateUI` to
  re-read them, so a renderer the Tag Browser created while the Designer was
  dark painted every row dark for the rest of the session — the light
  restore now re-syncs the renderer of every tree it walks, not only the ones
  the icon pass had wrapped.

- Unit tests for the theme preference — the one piece of state the module keeps
  between launches, and until now the only behaviour with no test of its own.
  They cover the `setDark`/`isDarkModeEnabled` round trip, the rule that the
  saved value follows the theme actually INSTALLED rather than the one
  requested (a switch that fails must not come back at the next launch), and
  that the startup path applies dark only when the preference asks for it.
  `ThemeManager` gained a package-private constructor taking the `Preferences`
  node so the tests write to an in-memory one instead of the developer's own,
  and the startup apply moved out of the readiness poll into
  `applyStartupPreference()` so it can be driven without a live Designer.
  They also assert the flush, not just the value: the value lands in the
  in-memory node either way, so a test that only read it back could not have
  caught the Linux bug below. Covers both write sites and an unwritable
  backing store.

- **The debug log opens with an environment block.** OS, JRE, the
  module-system and look-and-feel JVM arguments, which `java.desktop`
  packages the launcher opened, scaling, Synthetica's scale factor and font,
  and the `UIManager` font on either side of every switch. A bug report from
  Windows or Linux now carries the evidence instead of the guesswork.
- **The headless harness runs on Windows and macOS in CI**, not only Linux,
  with the debug log uploaded per platform.
- **A status-bar hint when the JVM has not opened `java.awt`.** The design
  tokens are restyled by rewriting `Color` instances in place, which needs
  `--add-opens java.desktop/java.awt=ALL-UNNAMED` from the Designer Launcher —
  observed on macOS, unverified elsewhere. Without it every token-coloured
  surface stayed light with nothing to say why. Now the status line names the
  argument and where it goes.

### Changed

- **CI's harness steps run under a watchdog that thread-dumps a hang.** Once
  the property-name lift landed, roughly half of CI runs stalled inside
  `PropertyKeyFieldTest` — a different method each time, on both the current
  and the 8.3.0 harness SDK, never locally in seventy attempts — and sat there
  until the job's 15-minute cap cancelled them, which left no evidence at all.
  `ops/laf-harness-watchdog.sh` now gives up after five minutes, `jstack -l`s
  the Gradle daemon and the test executor (the executor's dump goes straight
  into the step log), fails the step, and the dumps ship as a
  `laf-harness-diagnostics` artifact with JUnit's XML and the module's debug
  log. JUnit's own 60-second per-test timeout with `threaddump.enabled` is
  layered underneath so the hung test names itself. Nothing here fixed the
  hang; it produced the thread dump the fix needed, which is the deadlock
  entry under Fixed.

- The docs no longer describe Justin Edwards's
  [Exchange dark-mode script](https://inductiveautomation.com/exchange/2719/overview)
  as 8.1-only. Its 1.3.0 release (3 September 2026) targets 8.3, so the
  README's prior-art section now presents it as an alternative on 8.3 rather
  than the 8.1 counterpart, and the contributing guide and QA checklist say
  which release the borrowed class catalogue came from.
- The [QA checklist](docs/QA-CHECKLIST.md) now carries the surfaces that
  script's 1.3.0 release added on top of the 8.1-era catalogue: seven new rows
  (Vision binding editor, security panel, template custom properties, Easy
  Chart and Tab Strip customizers, and the gateway message handler dialog), a
  note on the Perspective binding-icon fix, and the matching entries in the
  "still unchecked" table. All are unverified until someone opens them.
- **Do not use this module and that script in the same project.** The README
  now says so under **Known limitations**, and says why (docs only; no
  behaviour change): the script lives inside the project as a library
  script plus a Vision client tag, adds its own View → Dark Mode checkbox
  shortly after every launch, and its explicit
  paints in either of its modes are nothing this module's toggle-off can undo,
  so remove its `designerPatch` client tag first; detecting the script at
  startup is tracked in
  [#89](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/89).
  Under **Prior art**: since the script never swaps the look and feel it does
  not have the Vision serializer problem, and on a mostly-Vision project it
  remains the better choice today — something its author had said on the
  announcement thread and this README had not.

- The native **title bar and window frame stay light on Windows and Linux**,
  by decision. The QA checklist gained an OS column and the three surfaces
  that only exist off macOS.

### Fixed

- **Dark mode keeps the Designer's font.** FlatLaf substituted the operating
  system's UI font (`Dialog 12` → `Helvetica Neue 13` on macOS; Segoe UI at
  the desktop's size on Windows), so every toggle changed text metrics, not
  just colours. The stock font is now pinned across the switch, and the pin
  carries Synthetica's scale factor with it on a scaled display. Visible on
  upgrade from 0.2.0: dark-mode text on macOS is a point smaller than it
  was, and now matches the light Designer exactly.

- **On Windows, the selected workspace tab stayed light with light text on
  it** ([#81](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/81)) —
  the row of open-resource tabs along the bottom of the Perspective, script,
  named query, report and Web Dev editors. JIDE does not recognise FlatLaf, so
  when the module reinstalls its extension it picks the table of tab defaults
  by operating system: everywhere else that table derives the tab colours
  from the look and feel and comes out dark, but on Windows it reads the OS's
  own 3D colours (`win.3d.lightColor` #E3E3E3, `win.3d.shadowColor` #A0A0A0,
  black button text) and never looks at FlatLaf at all. The selected tab is
  filled from `JideTabbedPane.light`, hence #E3E3E3 under a dark strip, while
  the label on it was lifted to light along with every other dark foreground.
  Every colour the tab delegate reads is now pinned from the dark palette, so
  the strip looks the same on every OS. macOS and Linux fills are unchanged;
  the tab text there was #7A7D7F on #2F3031 (about 3:1) and is now the label
  foreground. Reproduced and verified headlessly: the harness can now simulate
  the Windows path on any OS (`WorkspaceTabStripTest`), and rendered the
  reporter's exact colours before the fix. Not yet confirmed by eye on a
  Windows Designer.

- **The selected section of an Event Stream was unreadable**
  ([#79](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/79)).
  The strip of sections across the top of an Event Stream editor highlights the
  selected or hovered section with a pale rounded card, painted by
  `FlowCellContent.paintSelected` straight onto the Graphics from four
  `private static final` literals (`#DDE5EB` selected, `#EBEFF2` hover) — so no
  look-and-feel swap or component walk reaches it, and the reporter's inspector
  dump came back clean. The section name inside is a plain label on
  `Label.foreground`, light under dark mode: light text on a pale card. The
  literals now join `IaColorTokens.CLASS_DARK`, the in-place mutation that
  already handles the welcome workspace's identical tile selection; the card
  goes the same selection blue, with a matching outline, and comes back to its
  exact stock value on the light restore. Covered by `FlowCellSelectionTest`
  in the headless harness, which renders a section and reads the pixels, on
  the 8.3.0, 8.3.6 and 8.3.8 jars.

- **Toggle-button glyphs tinted with a design token came out dim** — the
  Event Stream editor's Enabled / Disabled / show-test-panel buttons
  ([#79](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/79)
  follow-up). IA builds those icons with
  `SvgIconUtil.getIcon(name, 16, 16, Colors.IconDefault)`, and the icon keeps
  that `Color` as its paint, so the token pass already renders them light
  (measured 174). The button-icon pass then handed them to the smart invert,
  which turned a light neutral glyph into a dim grey (111), whichever side of
  the switch the editor was built on. `TreeIconRecolorer` now leaves alone any
  IA SVG glyph whose tint is a token instance `IaColorTokens` restyles — judged
  by identity on the tint, not by brightness, so the stock light glyphs that
  must still be inverted (the QuickFilterField disc, #60) are unaffected.
  Covered by `TokenTintedButtonIconTest` in the headless harness, both
  orderings.

- **The look-and-feel harness no longer deadlocks in `PropertyKeyFieldTest`.**
  About half of CI runs since the property-name lift stalled there until the
  job cap cancelled them. PR #94's watchdog caught it: the test built a real
  `JsonEditor` and laid it out on the test thread, which holds the AWT tree
  lock inside `Container.preferredSize` while `BasicTextUI.getMaximumSize`
  waits for a text field's document lock; meanwhile `expandAll()` had made
  `NodeEditor` post its rebuild to the event dispatch thread, where a new key
  field's `setText` holds that document lock and its revalidate waits for the
  tree lock. The Designer only ever does any of this on the dispatch thread,
  so the test now does too, in phases, letting the queue drain between
  building the editor and inspecting it. Harness-only; no module code changed.

- The README's screenshot pair is retaken from `main` after the property-name
  fix below (docs only). The previous dark image showed the very defect the
  forum reported — every Session Props name black on the dark panel — so the
  "after" half of the before/after pair was itself a bug report. Same frame,
  same recipe (`docs/images/README.md`), 8.3.6; the dark name column now
  measures about 7:1 from the pixels where the old one measured 1.9:1.

- **Property names in the Perspective property editor are readable.** Raised
  on the forum against the announcement's own screenshot: every key in the
  Session Props editor — `host`, `locale`, `authenticated` — was pure black on
  the dark panel, a contrast ratio of about 1.9:1, while the values beside
  them were fine. It had passed a by-eye QA row. Each name is a
  `KeyEditorField`, a borderless `JTextField` whose base class keeps two
  private text colours and applies the *uneditable* one from
  `setEditable(false)` straight through `JTextField.setForeground`, bypassing
  its own override; the key class sets that colour to `Color.BLACK` and locks
  every schema'd key. The module's generic foreground lift lost both ways: it
  only fires over a dark background, and a text field with no background of
  its own reports the filter wrapper's permanent amber instead (the state #23
  documented in this editor); and when it did fire it rewrote only the
  editable colour, so the next `setEditable(false)` put the black back. The
  walk now recognises the field by class name, replaces the uneditable colour
  through its public setter, lifts whatever is showing regardless of the
  background, and restores both on the light switch. Values are untouched.
  Proven headlessly for both defeat paths and confirmed by eye in a Designer
  on 8.3.6 (2026-09-15): names light under dark, black again after the
  switch back. The proof is `PropertyKeyFieldTest` in the look-and-feel
  harness: a real `JsonEditor` over a session-props-shaped document, in
  both orders the Designer uses (rows before the switch, rows after it),
  asserting the names are readable, that the stock black comes back on the
  light restore, and — rendered to pixels — that the name column paints no
  black glyphs. Two of its cases model the runtime states that defeated the
  old lift and failed against the previous code.

- **The dark mode choice could be lost on Linux** if the Designer was
  force-quit, killed or crashed shortly after toggling. `ThemeManager` wrote
  the preference but never flushed it, and on Linux the backing store
  (`FileSystemPreferences`) only writes through on a 30-second sync timer or a
  shutdown hook — so the next launch came up in the theme the user had just
  changed away from. Both write sites now flush. Reproduced and verified
  against Ignition's own bundled Linux JRE 17. Windows (registry) and macOS
  (cfprefsd) persist out of process and were never affected, which is why this
  went unnoticed.
- The README now says where the Dark Mode setting lives and how far it
  reaches (docs only; no behaviour change). It is a `java.util.prefs` value on
  the machine running the Designer, per OS user — not on the gateway, not in
  the project — and it is one value for every gateway that user connects to:
  a gateway with the module applies it, a gateway without the module never
  loads the code and leaves it alone, and a failed apply against one gateway
  resets it for all of them. None of that was written down outside a comment
  in `ThemeManager`. The README's intro also read as contradicting itself:
  "the choice is remembered between sessions" followed two sentences later by
  "relaunching always gives a clean stock theme, whichever way you left it".
  The second sentence dates from when it sat next to a since-fixed restore
  limitation; it now says what it meant — a relaunch starts from stock and
  re-applies dark only if the setting asks for it. `ARCHITECTURE.md` and the
  bricked-launch recovery note in `DEVELOPMENT.md` cross-reference the new
  section.

- A second documentation pass, this one over statements that contradict
  themselves rather than the code (docs only; no behaviour change). A "four
  invariants" list in 0.2.0's own notes that introduced six; a "two more"
  in the README that introduced one; a "two tiers" in `ARCHITECTURE.md`
  followed by three bullets; the same §E row twice in the QA checklist,
  disagreeing with itself about whether it is blocked; the README's project
  layout, which had drifted five classes and both test source sets behind
  `designer/src`; the QA Runs table, now newest-first; and two Notes cells
  with a stray leading colon.

  `DEVELOPMENT.md`'s mutation figure was re-measured rather than re-guessed:
  reintroducing #23's ordering fails five assertions, not "three of the six"
  (`ThemeSwitchCycleTest` has had ten since #53). It now names the tests
  instead of counting them, so it does not go stale again the next time the
  harness grows. The 1297-defaults-left-null figure is unchanged and still
  exact.

- Documentation corrections found by a sweep of the docs against the code
  (docs only; no behaviour change). The build docs still named Gradle 8.14
  after the wrapper moved to 9.7.1. Three places — `docker-compose.yml`,
  `ops/lib.sh` and `ops/README.md` — still described accepting the dev
  certificate in the commissioning wizard, which `accept_staged_module`
  removed: the fingerprint and EULA hash are seeded into `data/modules.json`
  and nothing is clicked. The QA checklist said in two places that JIDE's
  `CodeEditor` is untouched by this module, which `CodeEditorTheme` stopped
  being true in 0.2.0, and its deep link into `ThemeManager` pointed at a line
  the file no longer has — now named by method instead, so it cannot drift
  again.

## [0.2.0] - 2026-09-01

A defect-fixing release. Ten dark-mode defects found by a Designer QA sweep
and fixed, each root-caused to a mechanism rather than patched by eye, and each
confirmed in a real Designer on 8.3.6. The theming passes gained failure
isolation, and the test harness gained two instruments it was missing:
component-level state diffing, and a check that everything this module reaches
by name still exists.

### Added

- **A test for everything the module reaches by name**
  ([#53](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/53)).
  This module works by reaching into Ignition, JIDE and JFreeChart internals as
  strings — a class called `InlineTipLabel`, a field called `COLOR`, a method
  called `setLineHighlightColor` — none of which the compiler checks, and every
  pass that uses one is deliberately guarded so a failure costs a surface rather
  than the Designer. Together that means an IA rename stops a pass working
  *silently*. `ReflectiveSurfaceTest` enumerates the whole surface (about 40
  names) and asserts each still resolves, and CI now runs the harness at both
  the 8.3.0 support floor and the current release. It cannot tell you a class
  still behaves the same — that is what the QA checklist is for — but it turns
  a silent regression into a red build.

- When `updateComponentTreeUI` fails on a window, the module now **says what
  broke and how much of the tree went unrefreshed**
  ([#12](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/12)).
  The failure was already survivable and already logged with its stack, but a
  stack names the UI delegate that threw — not the component, and not the
  subtree the aborted update never reached, which is the part that actually
  matters. The report names components with no font at all and the path to each,
  any `UIManager` font key resolving to null, and every component left holding a
  delegate from the wrong look and feel. Runs only on the failure path, so it is
  not gated on the debug flag: that is precisely the moment nobody has verbose
  logging on.

- The harness pins that **no FlatLaf scaling listener is left registered**
  ([#12](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/12)).
  With FlatLaf user scaling on, `UIScale` registers a listener on all three
  defaults tables that is still there after the stock theme is back, reacting to
  another look and feel's font changes. The module's only defence is one
  ordering-sensitive line setting `flatlaf.uiScale.enabled=false` before FlatLaf
  loads, and nothing guarded it. Measured both ways: as shipped no such listener
  appears anywhere; flip the property and all three tables carry one after a
  restore.

- A **headless look-and-feel harness** (`./gradlew :designer:lafHarness`,
  [#32](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/32)).
  It drives the real switch sequence against the real Synthetica, JIDE and
  FlatLaf jars — no gateway, no Designer, no screenshots — and diffs every
  resolvable `UIManager` default across a light→dark→light cycle. The unit
  tests only ever saw stub look and feels, so every bug this module has had
  (#14, #17, #19, #22, #23) had to be found by deploying and looking. Six
  invariants are now pinned instead: a full cycle restores every default, the
  FlatLaf overrides are cleared while FlatLaf is still installed (the ordering
  #23 got wrong), repeated cycles converge, JIDE's `Theme.painter` map comes
  back to its stock entries, the standard Swing colours actually go dark, and
  no `UIManager` key naming a background stays light under dark mode —
  which is [#22](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/22)
  turned from a manual dump into an assertion over 174 keys. It runs in CI.

  Its blind spots are mapped and documented rather than assumed: state outside
  `UIManager` (the IA colour tokens, the Synthetica singleton) is invisible to
  it, the dark half is weakly covered because JIDE derives dark colours
  correctly with no Designer present, and it cannot see pixels. A Designer
  still settles "does this look right".

- A **partly-applied theme now says so**, in the Designer's status bar: which
  passes failed, out of how many, and where to read the stack traces. Every
  pass after the look-and-feel swap is isolated, so one that fails leaves a
  Designer that works and is visibly wrong somewhere; the only record used to
  be a log file nobody knows to look for.

- The Designer's status bar stays readable under dark mode. `StatusBar
  .setMessage` re-asserts `Color.black` on the message label on every call, so
  its own messages were black on a dark bar.

- **[docs/QA-CHECKLIST.md](docs/QA-CHECKLIST.md)** — a sweep of Designer
  surfaces with a pass/fail per surface, so coverage gaps are found before a
  release rather than reported as bugs
  ([#5](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/5)).
  The surface list is drawn from the catalogue in the MIT-licensed
  [Exchange dark-mode script](https://inductiveautomation.com/exchange/2719/overview);
  its 8.1 class names have shifted on 8.3, its UI locations have not.

- `LookAndFeelDefaultsTableTest` (`./gradlew :designer:lafHarness`) pins the
  gap between the developer defaults table and the look-and-feel table: 109
  colour keys resolve through `UIManager` but not through
  `getLookAndFeelDefaults()` under the stock look and feel, and the restore
  leaves that set exactly as it found it. Ignition code reading a colour
  that way gets null in a stock Designer and a real colour under dark mode,
  which is the opposite of the intuitive direction and has already caused
  one stack trace to be misread.

### Changed

- **The headless harness runs against the current Ignition, not the support
  floor** ([#53](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/53)).
  `sdk_version` stays at 8.3.0 — it is what the module compiles against and what
  `module.xml` claims as the minimum — but the harness now resolves
  `harness_sdk_version` (8.3.8 by default, `-Pharness.sdk=8.3.6` to pin). The
  module reaches Ignition and JIDE internals by name, and none of that is
  compile-checked, so testing it against jars nobody runs was the weakest part
  of the setup. The full suite passes against 8.3.8.

- **Inline tip banners are readable under dark mode again**
  ([#47](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/47)).
  `InlineTipLabel.paintComponent` fills with a literal `#D6E4ED`, so no
  look-and-feel swap reaches it — while its text is IA's `Base900` token, which
  this module lightens. The result was light-on-pale: not merely wrong but
  *illegible*, in eleven places including Help → Diagnostics, the permissions
  configurator and the UDT multi-instance wizard. The fill now darkens with the
  other class constants and restores with them.

- **SQL and expression editors are themed**
  ([#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48)).
  "The script editor" is two components: the Python editors are
  `RSyntaxTextArea` and were already covered, while JIDE's `CodeEditor` — the
  Database Query Browser and **every expression editor in the Designer**, 46
  classes' worth — was not. Under dark mode it kept a cream `#FFFFD7` band
  across the current line, a **black caret on dark chrome**, and syntax tokens
  at `#000000`, `#000080`, `#650099`. A new `CodeEditorTheme` lifts each syntax
  colour to a readable luminance while keeping its hue, so a keyword still reads
  as a keyword, and restores every value on the way back to light.

- **The Tag Browser's `Value` header and the Perspective property editor's
  filter now come back when dark mode is switched off**
  ([#45](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45)). Two different
  mechanisms, both invisible to the defaults diff — every `UIManager` key was
  already coming back correct.

  The header is painted by a cell renderer, which is not in the component
  hierarchy. `JTableHeader.updateUI()` reaches its default renderer anyway, but
  only while that renderer is a `Component`: on the way into dark mode it is, so
  Swing calls `DefaultTableCellRenderer.updateUI()` — literally `super.updateUI();
  setForeground(null); setBackground(null);` — and destroys the colours
  `SimpleTreeTable$SimpleHeaderRenderer` sets in its constructor and never sets
  again. On the way back the renderer is wrapped in ours, which is not a
  `Component`, so Swing skips it and the header keeps null colours and a FlatLaf
  delegate for the rest of the session. The colours are now snapshotted before
  the switch can wipe them, and the delegate is put back explicitly.

  The filter is JIDE's `LabeledTextField`, whose `updateUI()` ends in
  `setEnabled()`, which does `setBackground(getTextField().getBackground())`.
  `updateComponentTreeUI` walks parent first, so on the restore the wrapper
  copies the inner field's still-dark `#46494B` onto itself a moment before that
  field goes light. A second pass now re-runs `updateUI()` child-first on
  anything left holding a dark `UIResource` background.

  Measured on the real components: a light→dark→light cycle over a
  `SimpleTreeTable` and a `QuickFilterField` rendered 15,462 pixels different
  from stock before the fix and 0 after.

- The **tree-update diagnostic now reports null backgrounds and foregrounds**,
  not only fonts. It was written against the description in
  [#12](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/12),
  which named a `Font.getFamily()` NPE. The failure actually caught in the wild
  was `Color.getAlpha()` on a null background, so the instrument was looking at
  the wrong property and would have reported `no font at all: 0` while saying
  nothing about the cause. Its `UIManager` sweep now covers every key rather
  than font-ish ones too.

- **Opening a Vision window no longer floods the Designer with
  `minimumSize` errors.** Vision's `DockingInternalFrameUI.installDefaults`
  nulls the content pane's background when it is a `UIResource`, and a Vision
  content pane is a `BasicContainer` whose `setBackground` cannot take null —
  so it threw three instructions before `frame.setLayout(...)`, leaving the
  frame with no layout and every later `getMinimumSize()` NPEing, once per
  paint and per property-table read. The condition is ours to remove: the
  background is a `UIResource` only because a previous walk of ours put one
  there. The walk now swaps the same colour in as a plain `Color` before
  `updateUI()` and restores a `UIResource` afterwards, so IA's block skips
  itself, the layout is installed, and the content pane still tracks the theme.

- **One component throwing out of `updateUI()` no longer strands the rest of
  the Designer's tree.** Swing's `updateComponentTreeUI` is an unguarded
  recursion, so the first throw abandons every component after it — and the
  tree that aborts is usually the main frame's, leaving everything below the
  throwing component on the outgoing look and feel's delegates. Isolating per
  window
  ([#11](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/11))
  was not enough. The walk is now per component, at **every**
  call site — the switch's own pass, the component watcher's debounced rescan,
  and the stale-delegate refresh: a failure costs that component, its siblings
  and its own subtree are still walked, and the distinct failing classes are
  logged once each. The rescan is a `Timer` callback, so a throw there did not
  merely lose the tree — it reached the EDT's default handler and killed the
  tick, including the theming passes that are the point of the rescan.

  Containment is the net, not the cure — where the throw leaves a component
  half-configured, it has to be prevented instead, as the Vision entry above
  does. What containment is right for is the case this module cannot reach at
  all: the `Font.getFamily()` NPE in
  [#12](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/12),
  whose cause is still open. The diagnostic added for that issue now also runs
  on the per-component failure path, since containing the throw made the
  window-level path it was written against nearly unreachable.

- **Alarm pipeline and SFC blocks are legible under dark mode.** A block paints
  itself from `Color` fields assigned literals in `BasicBlockUI`'s constructor
  — `#B0D8EA` connected, `#EEEEEE` unconnected — which no look-and-feel swap
  and no `UIManager` override can reach, while its title label is a plain
  `new JLabel` that inherits FlatLaf's light `Label.foreground`. Light text on
  a pale fill. The fills are now darkened instead of the labels corrected,
  since pale blocks on a dark canvas would be a different kind of wrong. Each
  colour is judged on its own luminance rather than by which field holds it:
  `StartBlock$UI` pushes IA's `#F7901E` orange through the same public setters,
  and the START block was the one thing on that canvas that already read
  correctly.

- Opening a Vision window no longer crashes the Designer's event thread with a
  `StackOverflowError` out of `CellRendererSanitizer`. JIDE's `CheckBoxList`
  does not return the renderer `setCellRenderer` replaces — it hands back a
  decorator that it re-points at the list's own renderer field on every call,
  and SwingX's `JXList` does the same thing with a `DelegatingRenderer` — so
  wrapping what `getCellRenderer()` returned left the wrapper and the decorator
  delegating to each other, and the first painted cell recursed until the stack
  ran out. The wrapper now goes *under* such a decorator (both publish the real
  renderer, and between them that covers every decorating list on the
  Designer's classpath), a list that hides its real renderer is left unwrapped
  rather than risked, and the wrapper breaks any remaining cycle instead of
  overflowing. Those lists are dark-adapted as they always should have been.

- A failure to keep the **Synthetica singleton** alive is no longer silent
  ([#35](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/35)).
  Installing FlatLaf nulls Synthetica's private `activeInstance`, and the
  Designer keeps calling `getInstance()` the whole time dark mode is active, so
  the module reflectively points it back. That repair swallowed its own
  exceptions and logged a warning: it never reached the failed-phase count, so
  the switch reported complete success while every such call NPE'd out of
  Ignition's own code. It now runs as a reported pass like every other, first on
  the dark switch since nothing may call into Synthetica before it. The field is
  matched by name in a third-party jar, so the failure that matters is a
  Synthetica upgrade renaming it — which would otherwise have broken dark mode
  for everyone on one release, silently.

- The **Tools → Dark Mode** checkmark no longer disagrees with the theme in
  effect ([#15](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/15)).
  It tracked the *request*: ticked the moment you clicked, whether or not the
  switch then worked, and a failed switch left it claiming a theme the Designer
  was not in — permanently, and retried at every launch. The checkmark and the
  saved preference are now both set from the look and feel actually installed
  once the switch has finished.

- A switch no longer looks like a click that did not register. It runs one
  event-queue turn late, behind an "Applying dark mode…" message painted
  before the event dispatch thread blocks, with the menu item disabled so a
  second click cannot queue an opposite toggle.

- The debug log has two levels. `DebugLog.log` (theme switches, failures) always
  writes; the per-pass counts, icon classes, popup contents and stale-delegate
  traces moved to `DebugLog.detail`, which writes only under
  `-Ddesignerdarkmode.debug=true`. The component watcher re-runs the theming
  passes for the whole session, so those lines were unbounded — and every one
  cost a file open, write and close on the event dispatch thread. Popup state
  was logged on **every** popup menu creation, and a renderer-straggler walk
  ran over **every painted table cell**; both are now behind the same flag.

- The log file is opened once and held for the session, flushed per line.

### Fixed

- **Reporting's Report Overview is readable**
  ([#59](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/59)).
  Its headings and report title were not dim but invisible: `HeaderLabel` and
  `AntialiasLabel` carry a literal `#454545`, which on the dark surface's
  `#3C3F41` is a contrast ratio of about 1.1:1. An explicit (non-`UIResource`)
  foreground is invisible to the look and feel by design, and the module lifted
  such foregrounds only as a rider on a background swap — which a component
  already sitting on a dark background never gets. Explicit dark text is now
  lifted on its own merits, guarded so text that would land on a *light*
  background is left alone. Fixing it exposed a second defect in the same place:
  lifted originals were restored only for components whose background had been
  swapped from white, so a lift made by any other branch was recorded and never
  put back, stranding near-white text on the light theme.

- **The Data tab's parameter and data-source headers are dark**
  ([#59](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/59)).
  A JIDE `GroupList` paints its group headers through a second renderer slot
  that `setCellRenderer` never touches, so the rows came out correctly themed
  under light-blue "Parameters" and "Data Sources" bars. The list itself probes
  clean — a group header is not a component, the same way a table's cell
  renderers are not.

- **The palette's clear-search button is no longer a bright blob**
  ([#60](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/60)).
  Two mechanisms had to give. The icon walk reads `getIcon()` once, and JIDE
  fills this button's icon slot only when there is text to clear, so it was
  never processed at all; the slot is now watched. That alone was not enough:
  the icon is not too dark but too *light* (a measured 226), so the
  enabled/disabled pair swap installed the icon already installed, changed
  nothing, and still reported the button handled — blocking the smart invert.
  The pair is now declined when it is a no-op *and* the icon is above 200, which
  is the point at which an icon drawn to be near-invisible on near-white chrome
  becomes the loudest thing on a dark surface. The threshold clears every other
  no-op pair on the Designer's classpath (the toolbar's vector icons at 174, the
  Vision palette at 183, popup menus at 109), all of which keep their icons.

- **The Tag Browser tree no longer throws on every repaint**
  ([#58](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/58)).
  `TreeIconRecolorer` wraps a tree's cell renderer to recolour its icons, but
  `TagBrowserTree` publishes its renderer through a typed accessor that casts —
  `(TagRenderer) getCellRenderer()` — and calls it from its own `paint`. The
  wrapper therefore turned every repaint into a `ClassCastException` on the
  EDT, swallowed by the paint loop, so the only symptom was a stack trace in
  the Output Console. Trees that publish a typed renderer accessor are now
  skipped, detected by shape rather than by class name.

- **The Tag Editor's combo cells are dark and readable**
  ([#57](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/57)).
  They rendered as white fields carrying our own lightened text — pale fill,
  light text, barely readable. The cause was not the renderer but the plumbing:
  the pass that replaces a table's `CellRendererPane` (the only thing that
  catches tables resolving a renderer per cell, as JIDE property grids do)
  remembered which tables it had done and never revisited them. But
  `updateComponentTreeUI` rebuilds a table's UI and installs a *fresh* pane, so
  any table refreshed after being intercepted silently lost the interception.
  The guard is now on the live pane rather than on the table.

- **The pale band under the Tag Browser's `Tag | Value` header is gone**
  ([#21](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/21)).
  `SimpleTreeTable$SimpleHeaderRenderer` gives every header cell a compound
  border of 8px `Color.WHITE` over 1px of `Table.gridColor`, and the grid colour
  is a `static final` captured at class-init, so it kept the light theme's
  `#C0C5CA` for the life of the Designer. A header cell is a rubber stamp,
  configured and painted through a `CellRendererPane` and never added to
  anything — which is why thirty component inspections came back clean while the
  band stayed on screen.

- **One code editor that throws no longer costs the rest.** JIDE raises
  `IndexOutOfBoundsException: Wrong line: -1` out of
  `setBracketHighlightColor` on an editor with no valid caret line. The first
  cut of the editor pass treated any throw as systemic and gave up, which
  silently left every later expression editor light for the rest of the
  session — found in a debug log, not on screen. Failures are now isolated per
  property and per editor, and only a genuinely systemic failure (a method that
  has moved) stops the pass.

- **The Output Console's log text is readable**
  ([#52](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/52)).
  Two consoles in the Designer are coloured two different ways, and only one of
  them was covered. The Script Console uses named document styles; the Output
  Console dock registers appenders on the bifurcated `System.out` / `System.err`
  holding `Color.black` and `Color.red` and stamps that colour onto *every
  inserted run* — and since the Designer's logging goes through stdout, that is
  the entire console. Neither colour may be mutated (they are the JDK globals,
  the same rule that protects `Base000`), so the pass rewrites the runs already
  in the document and repoints the appenders for lines still to come. The
  restore maps back by colour rather than by offset, which is what makes it
  survive the console being trimmed as it grows.

- **The Diagnostics performance charts' axes are readable**
  ([#50](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/50)).
  IA colours the plot background and gridlines from its own design tokens, which
  this module already restyles — but never sets the axis paints, so those kept
  JFreeChart's `Color.black` defaults and the axis numbers and titles sat black
  on dark. A new pass sets the label, tick-label, axis-line and tick-mark paints
  and restores them. Targeted at `DynamicTimeSeriesChart` by name rather than at
  any chart: Vision windows render *user* charts, and repainting those would
  misrepresent what an operator sees.

- **The Query Browser's result-table buttons no longer show pale boxes**
  ([#51](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/51)).
  `ResultTable$EditButton` paints its own gradient behind the label from eight
  literal colours, the first of which is plain white. The button component
  itself is correctly dark, which is why inspecting it came back clean while the
  screen showed the problem. The seven fills join the class constants
  `IaColorTokens` darkens; the two amber focus/hover accents are left alone.

## [0.1.0] - 2026-08-28

First release.

### Added

- Dark mode for the Ignition 8.3+ Designer, toggled from **Tools → Dark Mode**
  and remembered between sessions.
- **Script editors** are themed with Ignition's own `NamedTheme.Dark` — script
  console, project library, Vision component scripts, Perspective transforms —
  including a guard that re-applies when switching tabs or tree nodes, which
  Ignition would otherwise overwrite.
- **Console output** is recoloured by restyling `ConsolePanel`'s named document
  styles, so existing and future text change together and the restore is exact.
- Apache-2.0 licensing, declared to the gateway as a free module with an EULA
  shown at install.
- CI (build, Gradle wrapper validation, `module.xml` wiring checks) and a
  tag-driven release workflow that signs and publishes the `.modl`.
- `ops/` scripts for a disposable Docker dev gateway, including unattended
  module acceptance so a fresh gateway needs no browser commissioning.
- Unit tests (JUnit 5) covering the colour-token mutation and restore, the
  refusal to touch JDK global `Color` singletons, the white-border swap, and the
  neutral/luminance predicates that decide what gets restyled.
- The component inspector reports borders (with colours, recursing compound
  borders), component bounds, a scroll pane's `viewportBorder`, and a scroll
  pane's parts — viewport view, row and column header views, corners. An empty
  `JTable` is too short to hit-test, so its parts never appeared in a chain.
- `-Ddesignerdarkmode.debug=true` enables the verbose diagnostic dumps (dock
  state, and every `UIManager` colour default still light under dark mode).
  Off by default; they cost a hundred or more log lines per switch.

### Fixed

- Dark mode often applied around **two minutes** after the Designer launched, or
  appeared not to apply at all — a light Designer with the menu item checked.
  The readiness probe waited for two `JTree`s as a proxy for "the dock panels
  are built", which depends on which workspace the Designer restores; it now
  counts dockable frames, which every workspace has.
- A failed `updateComponentTreeUI` on one window aborted the update for **every
  window after it**, leaving the light restore visibly half-applied. Each window
  is now isolated.
- Dock title panes threw `ClassCastException` on every repaint under dark mode
  when a pane kept JIDE's Synthetica painter. The painter map is re-asserted on
  every rescan, and painters cached on components are repointed — including
  synchronously as a subtree is attached, since a workspace's panels are built
  early and paint the moment they appear.
- Borders drawn in the `Base000` token (which is `Color.WHITE` itself and must
  never be mutated) stayed white, drawing pale bands across the Project Browser
  and the tag table header. They are now replaced per component, tracked for an
  exact restore.
- Selected toolbar toggle buttons kept the stock near-white highlight under dark
  mode. JIDE's `BasicPainter` resolves button-state colours through
  `UIDefaultsLookup`, so neither the component walk nor the token mutation
  reached them; the eight `JideButton.*` keys it reads are now overridden.
- Three JIDE `UIManager` defaults stayed light under dark mode and painted thin
  strips: `SidePane.background`, `CommandBarSeparator.background`, and
  `JideTabbedPane.darkShadow` (a "darkShadow" holding `#DDDDDD`).
- Turning dark mode **off** left every property name in the Perspective Property
  Editor on an amber block, and left 192 of 1409 `UIManager` defaults at the
  wrong value. The light restore dropped the module's overrides with
  `UIManager.put(key, null)` — which deletes the developer-defaults entry rather
  than reverting it — *after* the stock look and feel and
  `installJideExtension()` had repopulated those same keys. Synthetica is
  Synth-based and owns none of the standard Swing colours in its own table, so
  `TextField.background` and ~190 others were left resolving to `null`;
  `BasicTextUI.installDefaults` then left every text field with no background of
  its own, and each inherited its parent's — in the property editor,
  `NodeEditor$FilterWrapper`'s permanent filter-match amber. The overrides are
  now cleared before the look-and-feel swap, while FlatLaf still serves the same
  values from its own defaults table, and are put back if that swap fails.
- `IaColorTokens.installClassColors` guarded only `Color.WHITE` and
  `Color.BLACK` against in-place mutation, while the token path also guarded the
  greys. A hard-coded `Color.GRAY` in an Ignition class would have been rewritten
  JVM-wide. Both paths now share one `isJdkGlobal` check, extended to every JDK
  `Color` singleton.
- The build no longer reports "incompatible with Gradle 9.0": the deprecated
  empty `moduleDependencies` call is gone, and `settings.gradle` uses assignment
  syntax for repository URLs.

### Known limitations

- A faint pale band under the Tag Browser's `Tag | Value` header in dark mode
  ([#21](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/21)).

Cosmetic, and affects no behaviour.
