# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Note that released module versions are plain `x.y.z`: Ignition's `module.xml`
version parser is numeric-only and rejects a prerelease suffix at install time.

## [Unreleased]

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
