# Designer dark-mode QA checklist

A repeatable sweep of Designer surfaces under dark mode, so coverage gaps are
found on purpose before a release rather than reported as bugs afterwards.

The surface list comes from the catalogue in the MIT-licensed
[Dark Mode for the Designer](https://inductiveautomation.com/exchange/2719/overview)
Exchange script, which enumerates 291 class names across 13 dispatch lists,
each annotated with where it lives in the UI. The catalogue was taken from the
script's 8.1-era release, and many of those class names have shifted on 8.3 —
**the UI locations have not**, so the locations are what this checklist
tracks. The script's 1.3.0 release (3 September 2026) targets 8.3 itself; it
was diffed against 1.0.6 on 2026-09-15 and adds 24 class names, all on Vision
customizers, the Vision binding and security dialogs, and the gateway message
handler dialog. Those are now rows here, marked **(1.3.0)** and unverified.
It also stops forcing a foreground on the Perspective binding icons (see §E).
Gratefully acknowledged; see [Prior art](../README.md#prior-art).

This module themes top-down (look-and-feel swap plus token mutation) rather than
by painting enumerated classes, so most of these should already be dark. The
value is knowing *where to look*.

## Runs

Newest first.

| Date | OS | Ignition | Vision | Module | Scope covered |
|---|---|---|---|---|---|
| 2026-09-17 (evening) | macOS | 8.3.6 | 12.3.6 | **v0.4.0 as released** | The five palette kinds the headless sweep cannot build, dropped under dark on Sam's screen: Slider, Spinner, both Comments Panels, Easy Chart. Every save carried a FlatLaf border class by name — the first real corruption since the gate came out, missed by the sweep. Root cause and fix the same evening (`LookAndFeelBorders`, `VisionConstructionBorders`); the dev gateway now runs the fix, the live re-check of this row and the client are still to do, and the dev project's Main Window needs a save from the fixed Designer |
| 2026-09-17 (release) | macOS | 8.3.6 | 12.3.6 | **v0.4.0 as released** (the GitHub asset, signed with the release certificate, accepted by the dev gateway after seeding its fingerprint and license hash) | Short sitting on the released build: Designer came up dark on Vision, window opened under dark, saved under dark, restored to light (Text Field font `Dialog, Plain, 12`, property editor populated), saved, dark again with the window open, saved; every save clean in the checker and in a byte scan (no dark value, no FlatLaf class, no font); the log has every switch with `SerializerCleanCopies` and `DeveloperDefaults: restored`, no failed phase; the Vision client opened the window showing every component as a stock save would. The first non-dev build ever run on this gateway |
| 2026-09-17 (late) | macOS | 8.3.6 | 12.3.6 | PR #108 branch, dev-signed | The two palette-sweep leaks, live: a Rectangle (shape tool) and a Tree View dropped under dark and saved; byte scan clean; the Vision client showed both as a stock Designer would have saved them |
| 2026-09-17 (evening) | macOS | 8.3.6 | 12.3.6 | `96d60ed` (main after #106) | Vision client opened the window saved under dark (light, correct); a Designer on a throwaway gateway without Vision toggled cleanly; the dev project's stray text-field colour reset. Closes the two §N rows left unrecorded |
| 2026-09-17 (afternoon) | macOS | 8.3.6 | 12.3.6 | PR #105 branch, dev-signed | First run of the new §N, Vision under dark mode, driven by Claude on Sam's screen. Found two things and fixed both on #105 the same afternoon: a save after the restore failed on a progress bar dropped under dark (the style primer covered text kinds only), and a text field saved under dark carried the module's own #3A3D3F (the white-token swap had no Vision guard). With both fixed: window and template open under dark, saved under dark and after the restore, all clean in the checker and in a byte scan for every dark value; dark applied with a window open; relaunch came up dark on Vision. The dev project's Main Window text field keeps a hand-set #3A3D3F from the bad save |
| 2026-09-17 | macOS | 8.3.6 | 12.3.6 | `6269432` (main after PR #101, dev-signed) | Fourth §N run, driven by Claude on Sam's screen (Sam typed the logins): startup dark switch, two navigation drop-outs, refusal with a window open, dark applied again after leaving Vision, relaunch with the dark preference kept, clean teardown, three saves clean in `ops/vision-check.sh`, and the three #92 rows. Found the Vision Property Editor painting BLANK after a drop-out (#102); reproduced on a dev-signed 0.3.0, so it predates #101. Template, fallback and no-Vision rows still unrecorded |
| 2026-09-15 (evening) | macOS | 8.3.6 | 12.3.6 | `f951416` (PR #84 with the review fixes) | Third §N run by Sam, following the step-by-step guide: control, navigation drop-out, preference kept across quit and relaunch on Vision (twice), both refusals, template, clean save. Found Vision's palette and property-editor filters and the Tag Browser rows dark after the drop-out; fixed in the harness and confirmed by eye on the redeployed build |
| 2026-09-15 | macOS | 8.3.6 | 12.3.6 | worktree, uncommitted (same build as 2026-09-02) | Second §N run by Sam after the gateway had been down for two weeks: the refusal with the Vision workspace selected and the navigation drop-out fired again per the log; `ops/vision-check.sh` clean. Found and fixed the checker's locale bug (macOS `tr` under UTF-8 emptied the sweep, so every file passed vacuously). Relaunch, template and fallback rows still unrecorded |
| 2026-09-02 | macOS | 8.3.6 | 12.3.6 | worktree, uncommitted | First run of §N, the Vision gate, by Sam in a live Designer after the headless reproduction. The navigation path, both refusals and a clean save confirmed by the log and `ops/vision-check.sh`; the relaunch, template and fallback rows not yet recorded |
| 2026-09-01 (afternoon) | macOS | 8.3.6 | 12.3.6 | `b01d80eb` | #57 and the editor-resilience fix confirmed by eye. Third popup source verified (Perspective canvas). A **saved** view added to the dev project so §E stops being blocked by an empty project |
| 2026-09-01 | macOS | 8.3.6 | 12.3.6 | `b510440` | Closing the gaps left by the 2026-08-31 sweep (#41). Created a Perspective view so §E had something to open; Symbol Factory; one more heavyweight popup. **Right-click is confirmed un-automatable** — see [§L](#l-popup-sweep) |
| 2026-08-31 (evening) | macOS | 8.3.6 | 12.3.6 | `b510440` | Verification pass by eye in a real Designer for #47, #48, #50, #51, #52 and the late-attach fix. All confirmed. Two rounds: the first found #50 only half-fixed (chart background still white) and the Vision filters dark in light mode |
| 2026-08-31 | macOS | 8.3.6 | 12.3.6 | `76c4600` (`main`, release candidate) | Driven with computer use, 12:01–12:13 UTC. §A, §B (Properties, Export, Diagnostics), §C, §D console + autocomplete, §E property editor, §F palette/inspector, §J Image Management, §K Query Browser, toggle-off. **Two new `light` results — see [Diagnostics](#b-menus-dialogs-project-settings) and [Query Browser](#k-database-and-queries).** §L not re-verified (see [Note on this run](#note-on-the-2026-08-31-run)) |
| 2026-08-29 | macOS | 8.3.6 | 12.3.6 | `f4104b54`, built at `33cf8c7` — **10 commits behind `main`**, so without #36/#37/#38 | Three Designer sittings (13:22, 13:40, 14:15 UTC): §L popups, Alarm Pipeline Editor, Translation Manager, dataset editor, named-query selector |

## Running the sweep

1. Launch the Designer with the debug flag so popup and window contents are
   logged with their **real 8.3 class names**: `-Ddesignerdarkmode.debug=true`
   (see [DEVELOPMENT.md](DEVELOPMENT.md#the-debug-log)). It goes in the Designer
   Launcher, per gateway: select the gateway → **Edit** → *Additional JVM
   Arguments*. It survives relaunches, so this is a one-time setup.
2. **Tools → Dark Mode** on. The log now opens with an `env:` block — OS,
   JRE, which `java.desktop` packages the launcher opened, scaling, fonts.
   Record the OS in the [Runs](#runs) table; on anything but macOS, keep the
   block with the run, because it is what the
   [platform rows](#a-main-shell) below are read against.
3. Walk the sections below. Open each surface, look at it, record a result and
   the date.
   **Judge colour at 100%, never on a scaled screenshot.** On a Retina display a
   downscaled capture washes #3C3F41 out until it reads as light grey: two
   surfaces on the 2026-08-31 run looked like bugs at full-screen scale and were
   perfectly dark when zoomed. Both would have been false reports.
4. For anything light, hover it and press **Cmd+Shift+I** (or Ctrl+Shift+I) to
   dump the component chain to `~/.ignition/designer-dark-mode.log`, and put the
   deepest offending class in the Notes column. A chain dump is worth more than
   a description — see
   [the inspector](DEVELOPMENT.md#the-inspector--diagnosing-a-still-light-component).
5. Finish with the [toggle-off spot check](#toggle-off-spot-check).

Record the Ignition version and module version at the top of the run; a result
is only meaningful against a specific pair.

## Recording results

| Code | Meaning |
|---|---|
| `pass` | Dark, legible, no light leaks |
| `partial` | Mostly dark; note the specific element that is not |
| `light` | Leaks light; file or reference an issue |
| `n/a` | Surface does not exist on this version / module not installed |
| `skip` | Deliberately not themed (see [Out of scope](#out-of-scope)) |
| `fixed` | Was `light` or `partial` on an earlier run and has since been fixed in the module. Keeps the history visible; re-check it like any other row |
| — | Not yet checked |

**`Last checked` is not decoration.** A result is only true of the module
version that produced it, so a row whose date predates the release you are about
to cut has not been checked for that release. Fill it in on every row you touch.

Locations marked **(unverified)** are inferred from the 8.1 class names and have
not been confirmed on 8.3. Correct them in place as you go — that is half the
point of the first run.

---

## A. Main shell

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Menus and menu popups | Top of the main frame | `pass` | `2026-08-31` | File / Project / Tools / Help popups |
| macOS system menu bar | Top of the screen | `skip` | `2026-08-31` | Drawn by the OS and following the system appearance — not reachable from a Swing look and feel. Neither is the search field inside the Help menu |
| In-window menu bar (Windows, Linux) | Top of the main frame | — | — | The opposite case: off macOS the menu bar is a Swing `JMenuBar`, so the swap DOES theme it — and it has never been looked at, because on macOS it does not exist. Menu titles, hover, mnemonics and the accelerator text in the popups |
| Native title bar and window frame (Windows, Linux) | Around every window | `skip` | — | Stays light by decision (see [ARCHITECTURE](ARCHITECTURE.md#the-switch-step-by-step), step 9): the `apple.awt.windowAppearance` property is macOS-only, and FlatLaf window decorations on Ignition's frames would be a larger change than the gap justifies. Record it, do not file it |
| Toolbars | Below the menu bar | `pass` | `2026-08-31` | incl. the Vision workspace's extra toolbars |
| Dock title bars, grippers, split dividers | Any docked panel | `pass` | `2026-08-31` | |
| Section headers / collapsible title panes | Left and right docks | `pass` | `2026-08-31` | `SESSION PROPS` |
| Project Browser tree | Left dock | `pass` | `2026-08-31` | |
| Status bar | Bottom of the frame | `pass` | `2026-08-31` | |
| Output Console | Bottom dock | `fixed` | `2026-08-31` | [#52](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/52). Check BOTH halves: text already on screen when the switch happens, and lines that arrive afterwards — they are different mechanisms |
| Workspace tab strip (open resource tabs) | Bottom edge of the Perspective, script, named query, report and Web Dev workspaces | `fixed` | `2026-09-15` | [#81](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/81) — **Windows only**: the selected tab kept the OS's #E3E3E3 3D colour with light text on it. Reproduced and fixed headlessly (`WorkspaceTabStripTest` simulates the Windows path). Checked by eye on macOS 2026-09-15 (text contrast, which was ~3:1 there before the fix): pass. The Windows fill itself has NOT been seen by eye — no Windows machine available; ask the reporter to confirm on the release build |

## B. Menus, dialogs, project settings

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Project → Properties: Project General | Project → Properties | `pass` | `2026-08-31` | incl. the nav list, combos, checkboxes and OK/Apply/Cancel |
| File chooser dialogs (Windows, Linux) | File → Import / Export, Image Management upload | — | — | On macOS the Designer may hand you a native `FileDialog`, which no look and feel reaches; off macOS it is a Swing `JFileChooser`, which FlatLaf themes — with icons that may be the platform's own. A different surface, not the same one |
| Project → Properties: Project Permissions | " | — | — | |
| Project → Properties: Project Designer | " | — | — | |
| Project → Properties: Vision General | " | — | — | Vision module required |
| Project → Properties: Vision Launching | " | — | — | |
| Project → Properties: Vision Login | " | — | — | |
| Project → Properties: Vision Timing | " | — | — | |
| Project → Properties: Vision UI | " | `pass` | `2026-08-31` | The Client Background Color swatch stays light — that is a colour *value*, correctly left alone |
| Project → Properties: Perspective General | " | — | — | Perspective module required |
| Project → Properties: Perspective Permissions | " | — | — | |
| Project → Properties: Perspective Inactivity | " | — | — | |
| Project → Properties: Perspective Tag Drop | " | — | — | |
| Project → Properties: Perspective Symbols | " | `pass` | `2026-08-31` | |
| Project Export dialog (`CheckBoxTree`) | File → Export | `pass` | `2026-08-31` | tri-state checkboxes legible |
| Project Import dialog (`CheckBoxTree`) | File → Import | — | — | |
| Keyboard Layout | ~~Tools → Keyboard Layout~~ | `n/a` | `2026-08-31` | **No such item on 8.3.6.** The Tools menu is Console, Image Management, Script Console, Database Query Browser, Translation Manager, Symbol Factory, Dark Mode, Launch Perspective |
| Diagnostics dialog | Help → Diagnostics | `fixed` | `2026-08-31` | Tip banner [#47](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/47) and chart axes [#50](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/50), both confirmed by eye |
| Diagnostics performance charts | Help → Diagnostics → Performance | `fixed` | `2026-08-31` | Axis paints AND the chart's own background — [#50](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/50). The first fix did only the axes and the label margin stayed white |
| About dialog | Help → About | — | — | |

## C. Tags

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Tag Browser tree | Tag Browser dock | `pass` | `2026-08-31` | The pale band under `Tag \| Value` ([#21](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/21)) is gone, both header cells |
| Tag Browser filter field | Top of the Tag Browser | `pass` | `2026-08-31` | |
| Tag Editor | Double-click a tag | `pass` | `2026-08-31` | incl. the JIDE property table and category list |
| Tag data type / binding sub-editors | Inside the Tag Editor | `pass` | `2026-08-31` | |
| UDT definition editor | Tag Browser → UDT Definitions | — | — | |
| Tag import/export dialogs | Tag Browser hamburger menu | — | — | |

## D. Scripting

**Two different editors live behind the word "editor", and they need separate
checks.** The Python editors (Script Console, Project Library, event scripts)
are `RSyntaxTextArea`, themed by `ScriptEditorTheme` through IA's `NamedTheme`.
The SQL and expression editors (Query Browser, and probably the Named Query
editor and Transaction Groups) are JIDE's `com.jidesoft.editor.CodeEditor`, with
their own style registry — themed since 0.2.0 by a separate pass,
`CodeEditorTheme` ([#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48)).
Two passes, two failure modes: a `pass` on one says nothing about the other, so
check an editor of each kind rather than assuming "the editors" are covered.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Project Library editor | Project Browser → Scripting → Project Library | — | — | |
| Project Library nav tree | Left of that editor | — | — | Source of a `JTree` popup in §K |
| Script editor gutter, autocomplete popup | Type inside any script editor | `pass` | `2026-08-31` | `system.` + Ctrl+Space in the Script Console. The list and the attribute pane are dark; the enclosing `AutoCompletePopupWindow` window is still `#EEEEEE` explicit, which does not show at this size |
| Script Console | Tools → Script Console | `pass` | `2026-08-31` | both panes |
| Gateway Events editor | Project Browser → Scripting → Gateway Events | — | — | |
| Message handler dialog **(1.3.0)** | Gateway Events → Message → add or edit a handler **(unverified)** | — | — | 1.3.0 added `MessageHandlerEditor$MessageHandlerConfigPanel` and `SecurityPanel` for this dialog |
| Client/Session Events editor | Project Browser → Scripting → … Events | — | — | |

## E. Perspective

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| View editor canvas | Open any view | — | — | `n/a` on 2026-08-31 only because the dev project had no views. A saved view (`qa-dark-mode`) exists since 2026-09-01, so this is now an ordinary unchecked row, not a gap in the Designer |
| Component palette | Right dock | — | — | Guard hierarchy walks against `FilterablePalette` (see [Out of scope](#out-of-scope)) |
| Property editor tree | Right dock, view open | `pass` | `2026-09-01` | Checked properly this time, with a view open: PROPS/CUSTOM/PARAMS, value colouring, Add Property links |
| Binding and add-member icons in that tree **(1.3.0)** | Hover and click the binding icon and the `+` on an object property | — | — | The Exchange's 1.3.0 fix: it had been forcing white on `ComponentScopeEditor$BindingCompatibleNodeEditor$BindingControl` on every hover, which broke the icon's own states, and it now leaves that class alone (it also forces every `IconButton` visible). We restyle tokens rather than components, so this may already be fine, but nobody has looked at the icon *states*, only the tree |
| Property key editor field | Click a property name | — | — | The name's text colour is now lifted by class (see the row below); check that editing a key still shows light text and that the light restore puts black back |
| Property NAMES (Session Props, PROPS) | Right dock, Perspective selected or a view open | `fixed` | `2026-09-15` | Black on dark, ~1.9:1, raised on the forum against the 0.1.0 announcement screenshot and still black in the 0.2.0 README screenshot — the 2026-09-01 `pass` above missed it. Headless proof in `PropertyKeyFieldTest`; confirmed by eye on 8.3.6, light under dark and black again after the switch back |
| Binding editor dialog | Click a property's binding icon | — | — | Still unchecked: needs a component on the canvas, and adding one needs a palette drag the automation cannot do |
| Component scope / node picker in a binding | Inside the binding editor **(unverified)** | — | — | |
| Style editor | Project Browser → Styles | — | — | |
| Page Configuration | Perspective → Page Configuration | `pass` | `2026-08-31` | |

## F. Vision

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Window editor chrome | Open any Vision window | — | — | Dark mode stays on in Vision since 0.4.0; the `n/a` of 0.3.0 is lifted and the rows below are due a fresh pass |
| Component palette | Left/right dock with a window open | `pass` | `2026-08-31` | Vision workspace open (no window) |
| Property Inspector | Right dock | `pass` | `2026-08-31` | empty but dark |
| Border chooser (9 sub-panels) | Property Inspector → border property | — | — | Check every tab of the chooser |
| Layout dialog | Right-click a component → Layout **(unverified)** | — | — | |
| Size and Position dialog | Right-click a component → Size and Position **(unverified)** | — | — | |
| Dataset editor dialog | A dataset property → edit | `pass` | `2026-08-29` | |
| Custom property editor | Component → custom properties | — | — | 1.3.0 names `DynamicPropertyProviderCustomizer` and `CustomPropertyEditPanel` here |
| Template custom properties **(1.3.0)** | Open a template → Custom Properties **(unverified)** | — | — | Separate internal and external panels (`PublicPrivateCustomPropertyCustomizer`, `$1`, `$2`) |
| Binding editor dialog **(1.3.0)** | Property Inspector → a property's binding icon, every binding type **(unverified)** | — | — | 1.3.0 added the Cell Update binding (`CellUpdateBindingConfigurator`) and the colour state table (`ColorStateConfig`) to its list. The other configurators were already on the 8.1 list |
| Security panel **(1.3.0)** | Right-click a component → Security **(unverified)** | — | — | Justin's "security panel text not readable" fix: `ComponentSecurityPanel$1` and the `TristateCheckboxList`, whose parent is what carries the background. Check the tri-state boxes are legible |
| Easy Chart customizer, last tab **(1.3.0)** | Easy Chart → Customizers → Easy Chart Customizer → last tab **(unverified)** | — | — | `EasyChartCustomizer$DynamicGroupPanel` |
| Tab Strip customizer **(1.3.0)** | Tab Strip → Customizers → Tab Strip Customizer **(unverified)** | — | — | `TabStripCustomizer`, `TabAttributesPanel` (+`$GeneralPanel`, `$SelectedOrUnselectedPanel`), `PropertiesPanel`, `PreviewPanel`. 1.3.0 also rewrites the strip's own `tabData` (`SELECTED_BACKGROUND_COLOR`) on `PMITabStrip`. That is a component's *data*, the same rule as the canvas, so the preview strip is a `skip` for us if it stays light |

## G. Alarm notification pipelines

Not on the Exchange list — added from the 2026-08-29 run.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Pipeline Blocks palette | Top dock with a pipeline open | `pass` | `2026-08-29` | |
| Pipeline canvas background | `BlockDesignableContainer` | `pass` | `2026-08-29` | |
| **Pipeline blocks themselves** | Blocks on the canvas | `fixed` | `2026-08-29` | Was: pale-blue fill with near-white text. `START` was unaffected. See below |
| Pipeline Block Editor panel | Left dock | `pass` | `2026-08-29` | |

> **Fixed by `BlockWorkspaceTheme`.** A block *is* a component —
> `BasicBlockUI extends JPanel` — but it does not paint from its own
> background, so the inspector would have shown nothing light in the chain.
> `paintComponent` fills a shape with one of two `Color` fields and strokes it
> with one of three others, all assigned literals in the constructor
> (`#B0D8EA` connected, `#EEEEEE` unconnected, `DARK_GRAY` /`#CFCAC6`/`#F7901E`
> borders). No look-and-feel swap reaches a literal. Meanwhile `initHeader`
> builds a plain `new JLabel(getTitle())` and never sets a foreground, so the
> title inherits FlatLaf's light `Label.foreground` — light text, pale fill.
>
> The pass darkens the fills rather than correcting the labels; the other way
> round would leave pale blocks on a dark canvas. It judges each colour on its
> own luminance rather than by which field holds it, because
> `StartBlock$UI` sets its fill *and* all three borders to IA's `#F7901E`
> through the same public setters — a per-field table would have repainted the
> one block that already reads correctly.

## H. Reporting

Reporting module required; mark the whole section `n/a` if it is not installed.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Report Data tab | Open a report → Data | — | — | |
| Report Design tab | Open a report → Design | — | — | |
| Schedule tab | Open a report → Schedule | — | — | |
| Scheduled parameters panel | Schedule tab → Parameters | — | — | |
| Property inspector | Design tab, right side | — | — | |
| Pie chart configuration | Design tab → a pie chart | — | — | |

## I. Search, replace, translation

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Find/Replace panel | Ctrl-F / Cmd-F | — | — | |
| Find/Replace provider dropdown | Inside Find/Replace | — | — | Dropdown *popup*, not just the closed control |
| Find/Replace target checkboxes | Inside Find/Replace | — | — | |
| Find/Replace scrollable result list | After a search | — | — | Result rows are renderer-painted |
| Translation Manager | Tools → Translation Manager | `pass` | `2026-08-29` | |

## J. Images and symbols

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Image Management panel | Tools → Image Management | `pass` | `2026-08-31` | location confirmed |
| Symbol Factory browser | Tools → Symbol Factory | `pass` | `2026-09-01` | Location corrected: it is in the Tools menu. Category list, search field and preview all dark |
| Symbol Factory thumbnail gallery | Inside that browser | `skip` | `2026-09-01` | The white cells are the symbol ARTWORK, drawn on white. Recolouring it would misrepresent what an operator sees, the same rule as the Vision canvas |
| SVG canvas (`JSVGCanvas`) | Symbol Factory preview | — | — | Third-party Batik canvas; may not honour Swing colours |

## K. Database and queries

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Query Browser | Tools → Database Query Browser | `fixed` | `2026-08-31` | SQL editor [#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48) and the action buttons [#51](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/51) |
| Query Browser result table | After running a query | — | — | No datasource on the dev gateway, so no result set |
| Named Query editor | Project Browser → Named Queries | `pass` | `2026-08-29` | selector only |
| Named Query parameter table | Inside the Named Query editor | — | — | |
| Named Query security table | Inside the Named Query editor | — | — | |

## L. Popup sweep

Right-click each source with dark mode on. This section is
[#4](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/4)
in full.

Popups are the one place where "built under the other look and feel" bites
hardest: a cached `JPopupMenu` keeps a Synthetica UI delegate that cannot paint
under FlatLaf, so the failure mode is a **blank or white menu**, not a
slightly-wrong shade. Two mechanisms cover it —
`ThemeManager.refreshCachedPopups` walks `getComponentPopupMenu()` and
`JMenu.getPopupMenu()`, and the global `COMPONENT_ADDED` listener installed by
`ThemeManager.installAwtEventListener` refreshes any `JPopupMenu` before its
first paint
([ThemeManager.java](../designer/src/main/java/com/mustrysolutions/designerdarkmode/designer/ThemeManager.java)).
The second should cover popups that are not registered on their source
component, which is what the Exchange script's 50 ms polling exists to catch.
This section is the evidence for whether it does.

With `-Ddesignerdarkmode.debug=true`, each popup logs its contents and class —
so the log from this sweep also records the real 8.3 names for these sources.

| 8.1 source class | Where to right-click | Result | Last checked | 8.3 class from the log |
|---|---|---|---|---|
| `TagFrameTree` | Tag Browser tree | `pass` | `2026-09-01` | `TagPopupMenu` — arrived **stale**, repaired before paint. Same result on 2026-08-29 |
| `NavTreePanel$1` | Project Browser tree | `pass` | `2026-09-01` | Arrived **stale**, repaired before paint. `NodeContextMenu` (8 openings) on 2026-08-29 |
| `Graphics2dRenderWidget` | Perspective view editor canvas | `pass` | `2026-09-01` | 12 items, disabled ones correctly greyed. `Popup$HeavyWeightWindow`. Also `pass` 2026-08-29 |
| `InteractionLayer` | Vision window editor canvas | `pass` | `2026-08-29` | `JPopupMenu` with a `CustomizerMenu` submenu, 17 items |
| `BorderlessField` / `PerspectiveKeyEditor` | A text field's cut/copy/paste menu | `pass` | `2026-08-29` | `JPopupMenu`, 3 items |
| `JTree` | Project Library nav tree (Scripting) | `pass` | `2026-08-29` | `NodeContextMenu`, 14 items |
| `NodeEditor$MainEditor`, `…$1` | Perspective property editor, a property row | `pass` | `2026-08-29` | `JPopupMenu`, 7 and 15 items, right after `PropertyEditorFrame` attached |
| `ComponentScopeEditor$…$2` | Binding editor → component/property picker | `pass` | `2026-08-29` | `JPopupMenu` |
| — | UDT instance menu (Tag Browser) | `pass` | `2026-08-29` | `TagActions$udtInstanceMenu$1` — arrived **stale**, repaired |
| — | Combo dropdowns | `pass` | `2026-08-29` | `FlatComboPopup` |
| — | Tag Browser hamburger menu (left-click) | `pass` | `2026-09-01` | `Popup$HeavyWeightWindow` -> `JPopupMenu`, seen by the watcher, dark, nothing stale. **Left-click trigger, not right-click** |
| — | An IA error popup | `pass` | `2026-08-29` | `DefaultPopupWindowParent` → `ErrorPanel` — **not a `JPopupMenu`**, see below |

### Right-click cannot be automated from outside — but it can from inside

Synthetic right-clicks at the OS level do not reach the Designer: no popup
opens and none is logged, established on two Designers across two days.
`Shift+F10`, Swing's keyboard route, does nothing either.

**What does work** is dispatching the popup-trigger event from inside the
Designer's own JVM, through the Script Console. It goes through the component's
own mouse listeners, so it is the same path a right-click takes minus the OS
layer:

```python
from java.awt.event import MouseEvent
from java.lang import System
# target = the JTree/JTable/etc, found by walking Window.getWindows()
e = MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
               MouseEvent.BUTTON3_DOWN_MASK, 20, 20, 1, True,   # popupTrigger
               MouseEvent.BUTTON3)
target.dispatchEvent(e)      # on the EDT, via SwingUtilities.invokeLater
```

The `popupTrigger=True` argument is the whole trick — Swing's tree and table
listeners check `isPopupTrigger()`, not the button number.

Two things this does NOT prove, and they are worth being honest about: that a
human right-click reaches the same listener (it does in practice — the menus
that opened are the ones a right-click opens), and anything about menus that
are built lazily in response to the click's *position*.

One thing it proves better than a human could: the log shows both menus
**arrived stale and were repaired before their first paint**, so the delegate
refresh is demonstrably firing rather than idle.

Popups reachable from a **left-click** menu button are covered too, and one is
recorded above.

### The one that is not a JPopupMenu

At 13:44:56 a `com.inductiveautomation.ignition.client.util.gui.DefaultPopupWindowParent`
opened carrying an `ErrorPanel`. This is the shape that would defeat the
`COMPONENT_ADDED` → `instanceof JPopupMenu` branch entirely — it is IA's own
popup window class, not a Swing popup menu. It is covered by a *different*
mechanism: the `WINDOW_OPENED` handler refreshes any non-main
`RootPaneContainer`'s root pane and then re-runs the theming passes. Nothing in
it was stale, and it painted dark.

Worth knowing that this class exists, and that it rides a separate route. A
future popup source that is neither a `JPopupMenu` nor a `Window` would be
covered by neither.

### What the 2026-08-29 run establishes

Roughly 20 popup openings across 8 distinct menus. In every one:

- The window was `javax.swing.Popup$HeavyWeightWindow` — **not one lightweight
  popup in the whole sweep**, so the heavyweight case the Exchange script polls
  for is exactly what was exercised.
- Every item's UI delegate was `Flat*` (`FlatMenuItemUI`,
  `FlatCheckBoxMenuItemUI`, `FlatPopupMenuSeparatorUI`, `FlatMenuUI`). Zero
  `Synthetica*` delegates anywhere in the run.
- Each one was logged by `logPopupState`, which is only reached from the
  `COMPONENT_ADDED` → `child instanceof JPopupMenu` branch of the watcher — so
  the listener saw every popup, including the heavyweight ones.
- Two of them (`TagPopupMenu`, one `JPopupMenu`) were logged as stale and
  refreshed immediately before their first paint. The mechanism is demonstrably
  *firing*, not merely idle because nothing was ever stale.

Across all three sittings: **~40 popup openings, 14 distinct menus, zero
`Synthetica*` delegates, four popups repaired from stale before first paint.**
Every source on the Exchange's list has now been exercised.

The conclusion for #4: the delegate refresh covers heavyweight popups and the
Exchange's polling is not needed.

If a source ever does come up light, port the Exchange's retry approach (poll
`Window.getWindows()` for `Popup$HeavyWeightWindow`, 50 ms, up to 5 attempts
after the triggering click).

**Do not** add `Popup$HeavyWeightWindow` to a general window-targeting list.
The Exchange author's own comment: it does not trigger the dispatch event and
badly slows the nav tree popup.

## M. The module's own surfaces

Everything above is Ignition's UI. These are ours, and they can be wrong in
either theme — nothing else in this checklist would catch them.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Dark Mode menu item, unchecked | Tools → Dark Mode, light theme | `pass` | `2026-08-31` | Moon icon and label legible |
| Dark Mode menu item, checked | Tools → Dark Mode, dark theme | `pass` | `2026-08-31` | Checkmark visible against the dark popup |
| "Applying dark mode…" status message | Status bar, during a switch | — | — | Shown by `DesignerStatus` while the switch runs |
| Degraded-switch status message | Status bar, after a partial failure | — | — | The `N of M steps failing` line. Hard to trigger on purpose; check it is legible if you ever see it |
| First launch, before the theme applies | Startup, with dark mode saved | `skip` | `2026-08-31` | The theme is applied only once the UI is ready, so the Designer is briefly stock-themed at launch. That is by design — applying earlier kills the launch |
| Debug log is being written | `~/.ignition/designer-dark-mode.log` | `pass` | `2026-08-31` | Worth confirming at the start of a sweep: no log means no chain dumps when you need one |

## N. Vision under dark mode

Since 0.4.0 dark mode stays on inside Vision. What has to hold is that a
window or template saved under dark mode is a window a light Vision client
can open, looking as it did when saved by hand: no FlatLaf class names, no
font or colour you did not set, and the colours Vision copies from the
Designer's tokens written with their light values. `ops/vision-check.sh`
reads every saved window and template of the project back out of the dev
gateway and reports on exactly that. Needs a project with at least one Vision
window and one template. The gate rows of 0.3.0 (refusal, drop-out, close
and reopen) are gone with the gate; the sittings above record them.

| Step | Expect | Result | Last checked | Notes |
|---|---|---|---|---|
| Dark Designer. Open a Vision window from the project browser | The Designer stays dark; the window opens; the palette, property editor and their filters are dark; the window's components show FlatLaf's dark colours where they use the defaults (a limitation, see README) | `pass` | `2026-09-17` | The gate used to drop out here |
| Same window. Select a component | The Vision Property Editor lists its properties with Ignition's chevrons; no `TreeExpandedIconPainter` exception in the Output Console | `pass` | `2026-09-17` | #102 under dark: the developer defaults are not restored under dark, so the category icons are JIDE's, but the editor must paint |
| Edit a label's text, save | `ops/vision-check.sh` clean: no FlatLaf classes, no `setFont`, colour calls only where you set one; a Vision client, if you have one, opens the window | `pass` | `2026-09-17` | #92 part 1. The log has `SerializerCleanCopies: dropped …` from the last switch |
| Drop a Button and a Multi-State Indicator from the palette under dark, save | Checker: no FlatLaf classes; the button's `setForeground`/`setBackground` are counted (written with the STOCK values, since the palette handed it the dark token objects); a Vision client shows a normal light button and stock indicator colours | `pass` | `2026-09-17` | #92 part 2. The counts are expected here; what must not appear is a dark value. 2026-09-17: a Label and a Progress Bar (the palette's Button sits under a Krisp overlay on this laptop); the raw bytes of the save held no dark token value and eleven stock ones. Compare the client if you can |
| With that window open, **Tools → Dark Mode** off | The Designer restores; the window's text field keeps its font (property editor: `Dialog, Plain, 12`, not Tahoma 11); save; checker clean | `pass` | `2026-09-17` | #92 part 3 and #102 together. 2026-09-17: failed first on a Progress Bar dropped under dark (`Unable to create clean copy of ScalableFont`) until the primer covered every kind; then clean. The log has `primeStyles` only as a failure if it failed, and `DeveloperDefaults: restored …` |
| Turn dark mode back on with the window still open, edit, save | Applies; checker clean | `pass` | `2026-09-17` | The refusal is gone |
| Open a Vision **template** under dark, edit, save | Same standard as the window | `pass` | `2026-09-17` | Templates serialize too. 2026-09-17: the template's Text Area is a white-token component; before the fix on #105 a dark save wrote the module's #3A3D3F into it, after it the save carried no colour at all, under dark and after the restore |
| Dark preference saved, quit with a Vision window open, relaunch | Comes up DARK on the Vision workspace; the window reopens dark | `pass` | `2026-09-17` | Used to come up light with a notice |
| Drop a Rectangle and a Tree View under dark, save; open in a Vision client | The rectangle's text colour is the normal dark grey, not near-white; the tree's sample rows have white backgrounds and dark text | `pass` | `2026-09-17` | The two leaks the palette sweep found. 2026-09-17, on the #108 build: byte scan of the save clean (no `-2236963`, no `color(70,73,75`, the tree's three sample strings stock, zero dark values); the Vision client rendered the rectangle white with a black stroke and the tree's East Area / West Area rows dark-on-white. A byte scan of the save for `-2236963` (#DDDDDD) and `color(70,73,75` is the quick check |
| Designer without the Vision module | Dark mode behaves as before; no Vision-related lines in the log | `pass` | `2026-09-17` | `VisionWindows` resolves the interface by name and must cost nothing when it is absent. 2026-09-17: a throwaway 8.3.6 gateway on 9488 with Vision's `onStartup` set to `disabled` in `modules.json` and only this module staged; a fresh project came up dark from the shared preference, toggled light and dark again, no failed phase, no exception, nothing Vision-related in the log |
| Vision client opens a window saved under dark mode | The window renders as a light client should: normal buttons, tabs, scales, indicator colours; nothing dark that you did not set | `pass` | `2026-09-17` | The end-to-end proof. 2026-09-17: the Vision Client Launcher from the gateway's own `lib/core/launch/` dmg, project `test`; the window last saved under dark opened with every component light. The one dark rectangle was the text field's hand-set #3A3D3F from the bad save earlier that day, reset to white in the Designer afterwards and gone from the bytes |
| Drop the palette kinds the headless sweep cannot build — Comments Panel, Comments Panel (legacy), Easy Chart, Slider, Spinner — under dark, save | Checker clean: no FlatLaf class; a Vision client opens the window | `fail` on 0.4.0 → fixed, live re-check pending | `2026-09-17` | 2026-09-17 on the released 0.4.0: all five dropped and saved; the bytes held no dark value and no font, but `com.formdev.flatlaf.ui.FlatScrollPaneBorder` — the checker refused the window and a client cannot open it. Mechanism worked out headlessly once the probe could build a Comments Panel (a scroll pane built borderless, given FlatLaf's border by the tree update, against a clean copy with none; the serializer has an equality rule for Synthetica borders and none for FlatLaf's, and none at all for a null), fixed by `LookAndFeelBorders` and `VisionConstructionBorders`, with the sweep now tree-updating before every dark save. The remedy for a window saved this way is a light save from the same Designer. Re-run this row on the fixed build, then in the client |

**Once per Ignition version:** the interface the module keys on is
`com.inductiveautomation.vision.api.client.components.model.TopLevelContainer`,
implemented by `FPMIWindow` and `VisionTemplate`. `ReflectiveSurfaceTest`
cannot pin it (no Vision jar on the harness classpath); the Vision probe can:
`./gradlew :designer:visionProbe -Pvision.jars="$(ops/vision-jars.sh)"` runs
`VisionWindowSaveTest`, whose first test resolves the name against the cached
Vision jars and checks both classes implement it, and whose other tests are
the saves above, headless.

## Findings from the sweeps

Surfaces that failed, with the mechanism worked out. A finding stays here until
its issue is closed, so a re-run knows what it is re-checking.

### Inline tip banners (`InlineTipLabel`)

Found 2026-08-31 in **Help → Diagnostics**. The pale tip banner across the top
of the dialog stays light while its text is lifted to a light colour, so the
text is **illegible** — worse than a merely light panel.

The component chain comes back clean: `InlineTipLabel bg=#3C3F41|uires`. The
fill is not the background. `InlineTipLabel.paintComponent` is
`g.setColor(COLOR); g.fillRect(...)` where `COLOR` is a `private static final`
`new Color(14083309)` — **#D6E4ED**, a literal, so no look-and-feel swap and no
token mutation can reach it. Its constructor then does
`setForeground(IgnitionLookAndFeel$Colors.Base900)`, an IA token — which
`IaColorTokens` lightens under dark mode. Pale literal fill plus lightened text
is the illegibility, and the second half of it is ours.

Same shape as the pipeline blocks in §G, and fixable the same way: darken the
fill rather than correcting the text, judged on the literal's own luminance.

Filed as [#47](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/47).
Anywhere IA uses `InlineTipLabel` is affected; Diagnostics is just where this
run happened to look. Note that the plain "Tip:" line at the
bottom of **Image Management** is a different, ordinary label and is `pass`.

### Event Stream section cards (`FlowCellContent`) — fixed, confirmed by eye

Reported 2026-09-02 by a user on macOS
([#79](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/79)).
In an **Event Stream** editor, the strip of sections across the top (Source,
Encoder, Filter, Transform, Handler) highlights the selected section with a
pale rounded card, and the section name on it ("Tag Event") is **illegible**.
Hovering a section does the same with a paler card.

The reporter's own inspector dump comes back clean, exactly as the tip banner
above: `FlowCellContent bg=#3C3F41|uires fg=#DDDDDD|uires`. The card is not
the background. `FlowCellContent.paintSelected` does `fillRoundRect` and
`drawRoundRect` straight onto the Graphics from four `private static final`
literals — `SELECTED_BACKGROUND` **#DDE5EB**, `SELECTED_BORDER` #BBBBBB,
`HOVER_BACKGROUND` #EBEFF2, `HOVER_BORDER` #CCCCCC (verified against the
8.3.0, 8.3.6 and 8.3.8 jars). The name is a plain `JLabel` on
`Label.foreground`, #DDDDDD under dark mode: a luminance gap of 6.

Fixed by darkening the card, not the text: the four literals joined
`IaColorTokens.CLASS_DARK`, which already mutates the welcome workspace's
identical #DDE5EB tile selection in place. `FlowCellSelectionTest` in the
headless harness renders a section under dark mode and reads the pixels, so
the illegibility reproduces and the fix is proven without a Designer. What it
cannot prove is how the card looks in context — a section strip with a live
selection, the hover, and the disabled placeholder slots. Confirmed by eye
in a Designer against the dev gateway before PR #90 merged:
selected card, hover, and the three toggles below.

### Event Stream Enabled / Disabled toggles — fixed, confirmed by eye

Found 2026-09-15 while checking the section-card fix above: the icons on the
**Enabled** / **Disabled** toggles at the top right of an Event Stream editor,
and the show-test-panel button beside them, are barely visible.

`EventStreamResourceEditorPanel` builds all three as `JToggleButton`s with
`SvgIconUtil.getIcon(name, 16, 16, IgnitionLookAndFeel$Colors.IconDefault)`
(same in the 1.3.6 jar the gateway ships and the 1.3.8 in the SDK). The icon
keeps that `Color` as its paint, and the token pass restyles the `IconDefault`
instance in place — so the glyph renders light on its own, measured **174** in
the harness. `TreeIconRecolorer.recolorButtonIcons` then hands it to the smart
invert like any other button icon, and a light neutral glyph comes out of the
invert at **111**: the dim grey on screen. Same result whether the editor was
built before or after the switch.

Fixed by leaving alone any IA SVG glyph whose tint is a token instance the
token pass restyles (identity, not brightness — the QuickFilterField disc from
#60 is a light glyph that must still be inverted). `TokenTintedButtonIconTest`
covers both orderings headlessly. The three toggles were confirmed by eye in
a live editor before PR #90 merged; whether any other
token-tinted glyph regressed is still only covered by the harness.

While clicking around the same editor an `AWT-EventQueue-0` NPE surfaced in the
Output Console: `StatusSectionDiagnosticsCreator.addSourceDiagnostics` on a null
`diagnostics`. That is IA's `StatusSectionPanelViewModel.onActivate` reading the
gateway's diagnostics for a stream the gateway is not running — a new, unsaved
stream ("changes pending, save to see updates") or a disabled one — and not a
theming problem; the stack is entirely IA code and the module is not on it.
The seeded `Dark Mode Check` stream is enabled so its Status section has data.

### SQL editors (JIDE `CodeEditor`) — fixed in 0.2.0

Found 2026-08-31 in **Tools → Database Query Browser**. The editor's background
was correctly dark, but the **current-line highlight was still the light theme's
cream**, and the syntax token colours were the light theme's too (blue keywords).

The reason was that this is not the same editor as the script editors:
`ScriptEditorTheme` themes `org.fife.ui.rsyntaxtextarea.RSyntaxTextArea` through
IA's `NamedTheme`, and the chain here reads
`com.jidesoft.editor.CodeEditor` / `CodeEditorPainter` — JIDE's editor, with its
own style registry, which nothing in this module reached at the time.

Filed as [#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48)
and fixed in 0.2.0 by `CodeEditorTheme`, which lifts each syntax colour to a
readable luminance while keeping its hue. Kept here because the census it
carried is still open: the Named Query editor and Transaction Group SQL fields
are the likely other sites, and neither has been swept.

### Subtrees detached during a theme switch

Found 2026-08-31 by eye: after switching back to light, the Vision component
palette's and property editor's filter fields stayed dark, and the Query Browser
looked dark too.

The restore is not at fault. It walks what is attached and fixes it, and the
debug log says so (`Light restore: re-ran updateUI on 1 component(s)`). The gap
is subtrees that are **detached while it runs** — switching workspaces detaches
the Vision docks. The restore never sees them, and when they are attached again
`updateComponentTreeUI` runs over them parent first, JIDE's `LabeledTextField`
copies its still-dark child's background onto itself, and the child goes light a
step later. That is exactly [#45](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45),
on a subtree the restore could not have reached.

The dark-mode component watcher is uninstalled on the light path, so nothing was
left running to notice. There is now a light-mode counterpart that re-runs the
dark-leftover pass on a debounce when something is attached.

**Worth checking in every future sweep**, because it is invisible if you only
toggle while looking at one workspace: toggle to light, then visit each
workspace in turn — Vision, Perspective, SFC, pipelines — and look at the filter
fields and dock chrome in each.

Seen again on 2026-09-15 after a switch back to light, for a different
reason: the leftover pass itself excluded anything under a `factorypmi`
package (meant for Vision's user content, but the palette and property
editor live there too), and the Tag Browser's rows stayed dark because IA's
`PanelBasedTreeCellRenderer` copies the `Tree.*` colours at construction and
a renderer built under dark was never re-synced. Both are pinned in
`LightRestoreComponentStateTest` now.

### Note on the 2026-08-31 run

Driven with computer use rather than by hand, which changes what the run is
worth in two directions:

- **§L (popups) was not re-verified.** Synthetic right-clicks did not reach the
  Designer at all — no popup opened and none was logged — so every §L result
  below still dates from 2026-08-29. A popup sweep needs a human hand.
- **Judge colour zoomed, never on a scaled screenshot.** Two surfaces (the
  autocomplete popup, the whole Project Properties dialog) read as *light* on a
  full-screen capture scaled from 3456px and turned out to be perfectly dark
  when zoomed. Both would have been false bug reports.

The Perspective view editor, binding editors and style editor are still
unchecked in substance: the dev project has no views.

### Compare against a relaunched Designer before calling something a bug

Two of the five findings from the 2026-09-01 hand sweep — the Vision component
palette and the Reporting Design palette, both reported as looking washed out
or disabled after switching back to light — turned out to be **IA's own
light-mode styling**, unchanged by this module.

Eyeballing could not have settled it, because "does this look too pale?" has no
answer without a reference. What settled it was relaunching the Designer (which
comes up in whatever mode the preference last held, and never enters dark if you
do not toggle), recording the surface, and only then doing a dark -> light round
trip and recording it again:

| | stock | after dark -> light |
|---|---|---|
| `DefaultPaletteItemToggleButton` fg | `#70757A` uires | `#70757A` uires |
| its background | `#FAFAFB` uires | `#FAFAFB` uires |
| icon brightness, first 10 items | 183, 176, 177, 189, 187, 192, 179, 116, 94, 163 | identical |

Byte-identical, so there was nothing to fix. Measure the two states rather than
comparing the light one against your memory of the dark one — dark mode resets
what looks "normal", and a correct light surface can look wrong straight after
it.

The same run also found a genuine leftover this way: `CategoryView` on the
Reporting palette holds an explicit `#555A5C` computed under FlatLaf and never
recomputed, which neither the stale-`UIResource` sweep nor the dark-leftover
pass catches because both require a `UIResource`. It is invisible today only
because that pane is not opaque
([#59](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/59)).

### The light restore writes stack traces to the Output Console

Every switch back to light throws two `NullPointerException`s on the EDT from
Ignition's own `TreeCollapsedIconPainter` / `TreeExpandedIconPainter`, while a
JIDE tree-table repaints. They are transient — probing afterwards shows every
table back on a stock, correctly parented `CellRendererPane` — and the icons
repaint correctly, so there is nothing to see on screen. Check the console, not
the screen ([#61](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/61)).

## Toggle-off spot check

Reversibility is a project invariant, and restore has broken on its own before
(the amber property editor,
[#23](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/23),
took three wrong diagnoses). After the sweep, toggle **Tools → Dark Mode** off
and confirm these return to stock:

| Surface | Result | Last checked | Notes |
|---|---|---|---|
| Perspective property editor | `pass` | `2026-08-31` | incl. the filter field ([#45](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45)) — checked over four toggles, and again with the frame re-attached under dark |
| Menus and menu items | `pass` | `2026-08-31` | |
| Tag Browser | `pass` | `2026-08-31` | `Tag \| Value` header back to matching light grey ([#45](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45)) |
| Dock title bars and dividers | `pass` | `2026-08-31` | |
| Script editor and console | — | — | |
| Tree and table cell colours | `pass` | `2026-08-31` | Renderers are restored from tracked sets, not the live tree |
| **With a Vision window open** | `fixed` | `2026-08-29` | The throw is now prevented rather than contained — see below |

Then relaunch the Designer and confirm it comes up stock. *(Not done on the
2026-08-31 run.)*

Worth reading the debug log after the restore as well as looking at the screen:
`Light restore: re-ran updateUI on N component(s)` says whether the child-first
refresh pass found anything. On 2026-08-31 it reported **nothing on every
toggle**, including with the property editor re-attached under dark — so on this
path the filter is restored by the tracked white-swap, and that pass is
belt-and-braces rather than the thing doing the work.

> **Open defect, found by the 2026-08-29 run.** With a Vision window open, the
> light restore's phase-6 `updateComponentTreeUI` throws
> `NullPointerException: Cannot invoke "java.awt.Color.getAlpha()" because
> "newColor" is null` and abandons the walk for the entire main frame.
>
> `DockingInternalFrameUI.installDefaults` line 68 — bytecode offset 58 of
> `vision-client-12.3.6.jar` — is an explicit null:
>
> ```java
> if (contentPane != null && contentPane.getBackground() instanceof UIResource) {
>     contentPane.setBackground(null);   // line 68 — throws
> }
> frame.setBackground(UIManager.getLookAndFeelDefaults().getColor("control"));  // line 71
> ```
>
> A Vision window's content pane is a `BasicContainer`, whose `setBackground`
> override calls `adjustOpacityBasedOnBackgroundColor(newColor, old)` and
> dereferences `newColor` without a null check. So the trigger is **the content
> pane's background being a `UIResource` when `installDefaults` re-runs** — the
> state a preceding `updateComponentTreeUI` leaves behind. A stock Designer
> never hits it because `installDefaults` runs once, at construction.
>
> The `control` lookup on line 71 is a red herring: it is after the throw point
> and never executes. It *is* null under stock — `LookAndFeelDefaultsTableTest`
> pins that, along with 109 colour keys reachable through `UIManager` but not
> through `getLookAndFeelDefaults()` — but it is not this bug, and the restore
> leaves that gap exactly as it found it.
>
> **Not every light switch throws.** The toggle at 13:48:56, in the same
> Designer, completed clean — consistent with no Vision *window* being open at
> the time, only the Vision workspace.
>
> The harm is the abort, not the null: one throwing component stranded the rest
> of the main frame's tree. **Contained** — the phase-6 walk is now per
> component, so a throw costs only that component while its siblings and its
> own subtree are still walked. The NPE itself is Ignition's and still fires;
> what it no longer does is take the frame with it.

## What is still unchecked, and why

Kept explicit so nobody has to reconstruct it from the tables. Every one of
these is a real gap, not a pass.

| Surface | Why it is unchecked | What it needs |
|---|---|---|
| §L right-click popups, the remaining 5 sources | Three verified 2026-09-01 by dispatching the trigger from inside the JVM (both trees, the Perspective canvas). The rest need their surface open first: the Vision canvas needs a window, the property-row and binding-picker menus need a component on a view, and a text field's cut/copy/paste menu did not open from a synthetic trigger on the fields available | Either the Script Console technique in [§L](#right-click-cannot-be-automated-from-outside--but-it-can-from-inside), or ~10 minutes by hand |
| §E binding editor, component scope picker, style editor | **The view is no longer the blocker** — a saved view (`qa-dark-mode`) exists in the dev project, so the editor opens. What is still missing is a component dropped on that view: it needs a palette drag the automation cannot do, and the Perspective palette was not docked in the layout used | A component dropped on the view, by hand |
| §H Reporting, Preview and Schedule tabs | The dev project now HAS a report (`qa-report`, saved 2026-09-01), and Report Overview, Data and Design were swept on it in both modes — that sweep is what found #59. Preview and Schedule were not opened | Open the last two tabs on `qa-report` |
| §F border chooser, Layout, Size and Position | Need a Vision window with a component selected | A Vision window, by hand |
| §F the **(1.3.0)** rows: binding editor, security panel, template custom properties, Easy Chart and Tab Strip customizers | Added 2026-09-15 from the Exchange script's 1.3.0 diff, never opened under this module. Each needs a Vision window with a component of that type on it | A Vision window with a template, an Easy Chart and a Tab Strip, by hand |
| §D message handler dialog | Also from the 1.3.0 diff | Open Gateway Events → Message and add a handler |
| ~~Relaunch-comes-up-stock~~ | **Run 2026-09-01 and passed.** Toggled off, relaunched, confirmed the Designer comes up genuinely stock, toggled back. Recorded rather than deleted because the run doubles as the baseline half of the comparison in [Compare against a relaunched Designer](#compare-against-a-relaunched-designer-before-calling-something-a-bug) | — |
| §E view editor rulers and surround | Not a gap in testing — an undecided question. They are chrome and they stay light | A decision |
| **Everything, on Windows and Linux** | Every run above is macOS. The headless harness runs on all three platforms in CI, which proves the switch sequence and the reflective reach — not what anything looks like. The three rows marked *(Windows, Linux)* in §A and §B are surfaces that exist only there | A Designer sitting on each, walking this checklist, with the `env:` block from the log kept alongside the run. A 150% display on Windows and a HiDPI desktop on Linux would settle the scaling question in [ARCHITECTURE](ARCHITECTURE.md#gotchas-and-hard-won-facts) at the same time |

## Out of scope

Two things the Exchange author mapped that are worth respecting rather than
rediscovering:

- **`FilterablePalette` has a write-only `components` attribute that throws on
  access.** Any hierarchy walk that touches the Perspective palette must be
  guarded against it. Our walks go through `safely(...)`, so the failure mode is
  a logged pass rather than a broken Designer — but a pass that dies early stops
  theming everything after it, so a `light` result on the palette is worth
  checking the log for.
- **The property tables** (`InspectorFrame$CustomPropertyTable`,
  `PropertyTablePanel$CustomPropertyTable`) they **deliberately stopped**
  theming: *"Going any further with the coloring of the property table looks
  spotty, and can be difficult to use."* Record these as `skip` unless we have a
  reason to disagree.

**Vision windows and templates** are edited under dark mode since 0.4.0. What
is in scope is what a save carries (§N); what is not is how the canvas looks
under dark mode, which is FlatLaf's colours rather than the client's — a
documented limitation, not a theming target.
