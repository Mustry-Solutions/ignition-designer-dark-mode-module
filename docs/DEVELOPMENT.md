# Development guide

How to build, run, debug, and sign Designer Dark Mode.

## Prerequisites

- **JDK 17.** The Gradle toolchain pins Java 17; the build fails on other
  versions. (Note: the Ignition Module Generator that scaffolded this project
  needs a specific JDK too — the `v0.5.0` generator tag works on JDK 17.)
- **Docker** — only if you want the local dev gateway under `ops/`.
- Network access to `https://nexus.inductiveautomation.com/repository/public`,
  where the Ignition SDK artifacts live.

Everything else is resolved by Gradle: Gradle 9.7.1 via the wrapper, the
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
./gradlew :designer:lafHarness    # the headless look-and-feel harness (below)
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
Three tools make it tractable — and reach for the first one before you build a
`.modl`.

### The headless look-and-feel harness

```bash
./gradlew :designer:lafHarness
```

Runs `ThemeManager`'s real switch sequence against the real Designer look and
feels — Synthetica through `IgnitionLookAndFeel$LaF`, JIDE's extension on top,
FlatLaf arriving over both — with **no gateway, no Designer and no
screenshots**, in a couple of seconds. Source in `designer/src/lafHarness/`.

This exists because looking at a Designer is a bad instrument for most of these
bugs. A stock Designer resolves about 1,700 `UIManager` defaults and a switch
to dark rewrites or adds roughly 2,300 entries, so most of what a switch does is
somewhere you are not looking. When [#23][23] was finally diagnosed, **192
defaults came back wrong after a light→dark→light cycle and exactly one of them
was visible on screen.**
The harness snapshots every resolvable default before and after a cycle and
diffs them, so the other 191 are visible too.

What it pins today: a full cycle returns every default to its stock value; the
FlatLaf overrides are cleared *while FlatLaf is still installed*, which is the
ordering [#23][23] got wrong; repeated cycles converge instead of drifting; and
JIDE's `Theme.painter` map comes back to the Synthetica entries ([#14][14],
[#19][19]).

Mostly it replaces the "which defaults are wrong" half of the loop, not the
"does this look right" half: with no windows open, every pass that walks the
component tree runs and finds nothing, so component-level state (the painters
JIDE caches in private fields, the white background swaps) is out of reach of
the defaults diff. A Designer and a pair of eyes still settle those.

**One test does read pixels.** `TagBrowserHeaderBandTest` builds the real
`SimpleTreeTable` by hand, drives the dark passes over it, and paints it into a
`BufferedImage`. That is not a screenshot test — there is no reference image, so
it cannot fail on a font or a one-pixel shift. It asserts a single property: no
long run of light pixels in a dark panel.

Reach for that shape only when the wrong colour never passes through any state
an assertion could read. [#21][21] was exactly that. The pale band under the Tag
Browser's `Tag | Value` header comes from
`SimpleTreeTable$SimpleHeaderRenderer`, whose header cells carry a compound
border of 8px `Color.WHITE` over 1px of `Table.gridColor` — and `GRID_COLOR` is
a `static final` read once at class-init, so it keeps the light theme's
`#C0C5CA` forever. A header cell is a rubber stamp: configured, painted through
a `CellRendererPane`, never added to anything. Thirty component inspections
across several sessions all came back dark because there was nothing in the
hierarchy to find. Rendering found it in one run.

Two mechanics matter if you write another one. `validate()` is a no-op on a tree
with no peer, so lay out by hand (twice — the scroll pane sizes its row header
from the tree's width). And only the **dark** half can be rendered headlessly:
Synthetica's `ImagePainter` asks for a default screen device and throws
`HeadlessException`, so a light-theme render needs `java.awt.headless=false`.

**The light half does render on a machine with a display** — worth knowing,
because a before/after of the light theme is the most direct evidence there is
that a restore works, and it is what settled the Tag Browser header and the
property-editor filter. It is a local-only trick until [#42][42] gives the
harness a proper windowed mode, and it has two traps:

- Not from a Gradle test worker. JIDE pops a modal *"Unauthorized usage of JIDE
  products"* dialog the first time it is used unlicensed outside headless mode,
  and inside the worker that dialog blocks forever — the task simply hangs with
  no output, on the user's screen rather than yours. Run the probe with a plain
  `java` off `sourceSets["lafHarness"].runtimeClasspath` instead.
- One more opening than the task passes: `--add-opens
  java.desktop/javax.swing.tree=ALL-UNNAMED`. Synthetica's `LabelPainter`
  reflects into `DefaultTreeCellRenderer.selected` while painting a tree row,
  and without it the render dies with an `InaccessibleObjectException` that
  looks nothing like a theming problem.

**Component state is its own instrument, separate from the defaults diff.**
`LightRestoreComponentStateTest` ([#45][45]) snapshots what components and cell renderers
actually hold — background, foreground, border, UI delegate — before and after a
cycle. Both bugs it was written for were invisible to `UiDefaultsSnapshot`: every
default came back correct while a Designer showed a dark `Value` header and a
dark filter field, because the wrong colour was sitting on a component (and, in
the header's case, on a renderer that is not in the hierarchy at all). Reach for
this shape whenever a restore is right in `UIManager` and wrong on screen.

### The reflective surface, and which Ignition the harness runs against

The module works by reaching into Ignition, JIDE and JFreeChart internals **by
name** — a class called `InlineTipLabel`, a field called `COLOR`, a method
called `setLineHighlightColor`. None of that is checked by the compiler, and
every pass that uses it is deliberately wrapped in a guard so a failure costs a
surface rather than the Designer. Put those two together and a rename by IA
makes a pass stop working *silently*. [#35][35] was exactly that.

`ReflectiveSurfaceTest` enumerates the whole surface — about 40 names — and
asserts each still resolves. It cannot tell you a class still *behaves* the
same; that is what a Designer and the QA checklist are for. It turns "quietly
stopped theming after an upgrade" into a red build.

**Add to it whenever you add a reflective call.** Class names, the colour-token
map and the class-constant map are read from the production classes themselves,
so those cannot drift; method and field names are listed in the test, grouped by
the class that reaches them.

The version it runs against is a separate knob from the one the module compiles
against, and the distinction matters:

| | Version | Why |
|---|---|---|
| `sdk_version` | 8.3.0 | The support floor. Compiling against it is what stops a newer API creeping in, and it is what `module.xml` claims as the minimum. |
| `harness_sdk_version` | 8.3.8 | What people actually run. `-Pharness.sdk=8.3.6` pins a specific gateway. |

CI runs the harness at both. Testing only the floor is testing jars nobody has;
testing only the latest would let the module drift off its own support claim.

**Validate a new invariant with a mutation.** A green test proves nothing until
you have seen it go red for the right reason. Break the thing it claims to
protect — reorder a phase, delete a restore call — and check that the test you
just wrote is the one that fails. Reintroducing [#23][23]'s ordering fails three
of the six, with 1297 of 1740 defaults left null; a test that survives the bug it
is named after is decoration.

Doing that across fifteen mutations mapped the harness's blind spots, which are
worth knowing before you trust a pass:

- **State outside `UIManager` is invisible to it.** Never uninstalling the IA
  colour tokens, or never calling `keepSyntheticaAlive()`, both pass everything
  here — those mutate static fields outside the defaults tables.
  `IaColorTokensTest` in the unit suite covers the first. The second was
  [#35][35] — now covered, but only because the harness asks Synthetica for its
  singleton directly rather than looking for it in the defaults. Anything else
  living in a static field is still invisible here.
- **The dark half is thinner than the light half**, though the background rule
  below narrows the gap. Headlessly,
  `installJideExtension(VSNET_STYLE)` really does derive its colours from
  FlatLaf's dark palette: skipping the module's FlatLaf re-assert entirely
  changes exactly one value (`MenuBar.border`) and no colour at all. The white
  search fields and invisible context menus that pass exists for do not
  reproduce without a Designer.
- **`Theme.painter` self-heals.** Removing the module's painter restore leaves
  the painter test passing; removing `installJideExtension()` from the light
  path fails it. JIDE's own reinstall is what repopulates that map here. The
  test is an outcome check, not evidence the restore code runs — and this JVM
  has one classloader where a Designer has several.

It can also drive **components**, which is easy to forget given it has no
windows. 17 of 19 common Swing types build headlessly (`JSlider` and
`JSplitPane` are the two that throw), so a component tree can be built, put
through a full cycle and handed to `updateComponentTreeUI`. Windows are the hard
limit, not components.

Two notes if you extend it:

- Values are compared as **text**, not by identity or `equals`. JIDE rebuilds
  its `Border` and `Icon` instances on every `installJideExtension()`, so a
  perfectly clean restore produces ~200 false differences otherwise. Colours are
  rendered exactly (ARGB); borders and icons collapse to a class name.
- The JVM args in the `lafHarness` task are **not tuning**. Synthetica fails to
  initialise without those `--add-exports`/`--add-opens` — you get an
  `IllegalAccessError` out of a static initialiser, not a theming difference.
  The Designer Launcher passes the same set.

A rule worth knowing, since it is the one assertion here that would have found a
bug outright rather than caught a regression: **a `UIManager` key whose name ends
in `background` must be dark while dark mode is active.** 174 of them resolve to
a colour, 170 go dark, and the four that stay light each have a reason (three are
the fill behind a checkmark glyph; `ProgressBar.selectionBackground` is misnamed
by Swing and is really a text colour). Do *not* generalise it to "no light
colour" — 206 of the 542 colour defaults are legitimately light under dark, since
foregrounds, carets, arrows, disabled text and the accent palette all paint *on*
a dark surface. Narrowing to keys that name a background is what turns 206
judgment calls into a rule with four exceptions. [#22][22] was two of these
(`SidePane.background`, `CommandBarSeparator.background`).

[42]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/42
[45]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45
[14]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/14
[19]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/19
[21]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/21
[22]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/22
[23]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/23
[35]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/35

### When a window fails to restyle

`updateComponentTreeUI` failing on one window is isolated and survivable, but
the module now writes a diagnostic when it happens — unconditionally, since
nobody has `-Ddesignerdarkmode.debug=true` on at the moment a bug first appears.
Look for `Tree-update diagnostic for` in the log. It answers the three questions
the stack trace cannot:

- **which components have no font at all**, with the ancestor path to each
  (`getFont()` returns null only when the whole chain is unset, so the path is
  the diagnostic, not the component);
- **which `UIManager` font keys resolve to null** right then;
- **how much of the tree still holds a wrong-look-and-feel delegate** — the
  subtree the aborted update never reached, which is the actual damage.

Counts are of everything found; only the first twelve of each are described. The
two numbers are deliberately separate, because "the update skipped 12
components" and "the update skipped 1,400, here are 12" are different bug
reports.

### The debug log

`~/.ignition/designer-dark-mode.log` — append-only, written by `DebugLog`.
**Timestamps are UTC** — add your local offset when correlating with the clock.

Two levels. `DebugLog.log` always writes, and is reserved for what a user or a
maintainer reading a bug report needs: the theme switches and the failures
(with stack traces). `DebugLog.detail` writes only when the Designer is
launched with `-Ddesignerdarkmode.debug=true`, and covers everything else —
the per-pass counts, icon classes encountered, popup contents, stale-delegate
refreshes, every dock title pane and every light `UIManager` default. The
component watcher re-runs the theming passes for the whole session, so those
lines are unbounded; turn them on while hunting a mistyped surface and leave
them off otherwise, or the warnings drown.

Two diagnostics that cost real time are gated on the same flag rather than
merely silenced: the light-defaults dump, and the renderer-straggler walk that
would otherwise run over every painted table cell.

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
