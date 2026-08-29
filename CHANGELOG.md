# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Note that released module versions are plain `x.y.z`: Ignition's `module.xml`
version parser is numeric-only and rejects a prerelease suffix at install time.

## [Unreleased]

### Fixed

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

### Added

- A **headless look-and-feel harness** (`./gradlew :designer:lafHarness`,
  [#32](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/32)).
  It drives the real switch sequence against the real Synthetica, JIDE and
  FlatLaf jars — no gateway, no Designer, no screenshots — and diffs every
  resolvable `UIManager` default across a light→dark→light cycle. The unit
  tests only ever saw stub look and feels, so every bug this module has had
  (#14, #17, #19, #22, #23) had to be found by deploying and looking. Four
  invariants are now pinned instead: a full cycle restores every default, the
  FlatLaf overrides are cleared while FlatLaf is still installed (the ordering
  #23 got wrong), repeated cycles converge, and JIDE's `Theme.painter` map
  comes back to its stock entries, the standard Swing colours actually go dark,
  and no `UIManager` key naming a background stays light under dark mode —
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

### Changed

- The debug log has two levels. `DebugLog.log` (theme switches, failures) always
  writes; the per-pass counts, icon classes, popup contents and stale-delegate
  traces moved to `DebugLog.detail`, which writes only under
  `-Ddesignerdarkmode.debug=true`. The component watcher re-runs the theming
  passes for the whole session, so those lines were unbounded — and every one
  cost a file open, write and close on the event dispatch thread. Popup state
  was logged on **every** popup menu creation, and a renderer-straggler walk
  ran over **every painted table cell**; both are now behind the same flag.
- The log file is opened once and held for the session, flushed per line.

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
