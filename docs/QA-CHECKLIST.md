# Designer dark-mode QA checklist

A repeatable sweep of Designer surfaces under dark mode, so coverage gaps are
found on purpose before a release rather than reported as bugs afterwards.

The surface list comes from the catalogue in the MIT-licensed
[Dark Mode for the Designer](https://inductiveautomation.com/exchange/2719/overview)
Exchange script, which enumerates 291 class names across 13 dispatch lists,
each annotated with where it lives in the UI. Those class names are 8.1-era and
many have shifted on 8.3 — **the UI locations have not**, so the locations are
what this checklist tracks. Gratefully acknowledged; see
[Prior art](../README.md#prior-art).

This module themes top-down (look-and-feel swap plus token mutation) rather than
by painting enumerated classes, so most of these should already be dark. The
value is knowing *where to look*.

## Runs

| Date | Ignition | Vision | Module | Scope covered |
|---|---|---|---|---|
| 2026-08-29 | 8.3.6 | 12.3.6 | `f4104b54`, built at `33cf8c7` — **10 commits behind `main`**, so without #36/#37/#38 | Three Designer sittings (13:22, 13:40, 14:15 UTC): §L popups, Alarm Pipeline Editor, Translation Manager, dataset editor, named-query selector |
| 2026-09-01 (afternoon) | 8.3.6 | 12.3.6 | `b01d80eb` | #57 and the editor-resilience fix confirmed by eye. Third popup source verified (Perspective canvas). A **saved** view added to the dev project so §E stops being blocked by an empty project |
| 2026-09-01 | 8.3.6 | 12.3.6 | `b510440` | Closing the gaps left by the 2026-08-31 sweep (#41). Created a Perspective view so §E had something to open; Symbol Factory; one more heavyweight popup. **Right-click is confirmed un-automatable** — see [§L](#l-popup-sweep) |
| 2026-08-31 (evening) | 8.3.6 | 12.3.6 | `b510440` | Verification pass by eye in a real Designer for #47, #48, #50, #51, #52 and the late-attach fix. All confirmed. Two rounds: the first found #50 only half-fixed (chart background still white) and the Vision filters dark in light mode |
| 2026-08-31 | 8.3.6 | 12.3.6 | `76c4600` (`main`, release candidate) | Driven with computer use, 12:01–12:13 UTC. §A, §B (Properties, Export, Diagnostics), §C, §D console + autocomplete, §E property editor, §F palette/inspector, §J Image Management, §K Query Browser, toggle-off. **Two new `light` results — see [Diagnostics](#b-menus-dialogs-project-settings) and [Query Browser](#k-database-and-queries).** §L not re-verified (see [Note on this run](#note-on-the-2026-08-31-run)) |

## Running the sweep

1. Launch the Designer with the debug flag so popup and window contents are
   logged with their **real 8.3 class names**: `-Ddesignerdarkmode.debug=true`
   (see [DEVELOPMENT.md](DEVELOPMENT.md#the-debug-log)). It goes in the Designer
   Launcher, per gateway: select the gateway → **Edit** → *Additional JVM
   Arguments*. It survives relaunches, so this is a one-time setup.
2. **Tools → Dark Mode** on.
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
| Toolbars | Below the menu bar | `pass` | `2026-08-31` | incl. the Vision workspace's extra toolbars |
| Dock title bars, grippers, split dividers | Any docked panel | `pass` | `2026-08-31` | |
| Section headers / collapsible title panes | Left and right docks | `pass` | `2026-08-31` | `SESSION PROPS` |
| Project Browser tree | Left dock | `pass` | `2026-08-31` | |
| Status bar | Bottom of the frame | `pass` | `2026-08-31` | |
| Output Console | Bottom dock | `fixed` | `2026-08-31` | [#52](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/52). Check BOTH halves: text already on screen when the switch happens, and lines that arrive afterwards — they are different mechanisms |
| Tab strips (open resource tabs) | Above the workspace | — | — | |

## B. Menus, dialogs, project settings

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Project → Properties: Project General | Project → Properties | `pass` | `2026-08-31` | incl. the nav list, combos, checkboxes and OK/Apply/Cancel |
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
| Keyboard Layout | ~~Tools → Keyboard Layout~~ | `n/a` | `2026-08-31` | : **no such item on 8.3.6.** The Tools menu is Console, Image Management, Script Console, Database Query Browser, Translation Manager, Symbol Factory, Dark Mode, Launch Perspective |
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
are `RSyntaxTextArea`, themed by `ScriptEditorTheme`. The SQL editors (Query
Browser, and probably the Named Query editor and Transaction Groups) are JIDE's
`com.jidesoft.editor.CodeEditor`, with their own style registry that nothing in
this module touches — [#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48).
A `pass` on one says nothing about the other.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Project Library editor | Project Browser → Scripting → Project Library | — | — | |
| Project Library nav tree | Left of that editor | — | — | Source of a `JTree` popup in §K |
| Script editor gutter, autocomplete popup | Type inside any script editor | `pass` | `2026-08-31` | `system.` + Ctrl+Space in the Script Console. The list and the attribute pane are dark; the enclosing `AutoCompletePopupWindow` window is still `#EEEEEE` explicit, which does not show at this size |
| Script Console | Tools → Script Console | `pass` | `2026-08-31` | both panes |
| Gateway Events editor | Project Browser → Scripting → Gateway Events | — | — | |
| Client/Session Events editor | Project Browser → Scripting → … Events | — | — | |

## E. Perspective

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| View editor canvas | Open any view | `n/a` | `2026-08-31` | : the dev project has no views, so nothing to open. **Still unchecked in substance** |
| Component palette | Right dock | — | — | Guard hierarchy walks against `FilterablePalette` (see [Out of scope](#out-of-scope)) |
| Property editor tree | Right dock, view open | `pass` | `2026-09-01` | Checked properly this time, with a view open: PROPS/CUSTOM/PARAMS, value colouring, Add Property links |
| Property key editor field | Click a property name | — | — | |
| Binding editor dialog | Click a property's binding icon | — | — | Still unchecked: needs a component on the canvas, and adding one needs a palette drag the automation cannot do |
| Component scope / node picker in a binding | Inside the binding editor **(unverified)** | — | — | |
| Style editor | Project Browser → Styles | — | — | |
| Page Configuration | Perspective → Page Configuration | `pass` | `2026-08-31` | |

## F. Vision

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Window editor chrome | Open any Vision window | — | — | The canvas itself is deliberately not themed (README). 2026-08-31: the workspace home page ("Create a New Window") is `pass`, but no window was opened |
| Component palette | Left/right dock with a window open | `pass` | `2026-08-31` | Vision workspace open (no window) |
| Property Inspector | Right dock | `pass` | `2026-08-31` | empty but dark |
| Border chooser (9 sub-panels) | Property Inspector → border property | — | — | Check every tab of the chooser |
| Layout dialog | Right-click a component → Layout **(unverified)** | — | — | |
| Size and Position dialog | Right-click a component → Size and Position **(unverified)** | — | — | |
| Dataset editor dialog | A dataset property → edit | `pass` | `2026-08-29` | |
| Custom property editor | Component → custom properties | — | — | |

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
`JMenu.getPopupMenu()`, and a global `COMPONENT_ADDED` listener refreshes any
`JPopupMenu` before its first paint
([ThemeManager.java:1353](../designer/src/main/java/com/mustrysolutions/designerdarkmode/designer/ThemeManager.java:1353)).
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

### SQL editors (JIDE `CodeEditor`)

Found 2026-08-31 in **Tools → Database Query Browser**. The editor's background
is correctly dark, but the **current-line highlight is still the light theme's
cream**, and the syntax token colours are the light theme's too (blue keywords).

The reason is that this is not the same editor as the script editors:
`ScriptEditorTheme` themes `org.fife.ui.rsyntaxtextarea.RSyntaxTextArea` through
IA's `NamedTheme`, and the chain here reads
`com.jidesoft.editor.CodeEditor` / `CodeEditorPainter` — JIDE's editor, with its
own style registry that nothing in this module touches.

Filed as [#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48),
which also carries the census still to do: the Named Query editor and
Transaction Group SQL fields are the likely other sites.

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
| §E binding editor, component scope picker, style editor | **No longer blocked.** A saved view (`qa-dark-mode`) now exists in the dev project, so the editor opens; what is still missing is a component dropped on it, which needs a palette drag | A component on the view, by hand |
| §E binding editor, component scope picker, style editor | Need a component on the canvas; adding one needs a palette drag the automation cannot do, and the Perspective palette was not docked in this layout | A component dropped on a view, by hand |
| §H Reporting (whole section) | The dev project has no report resources | A report to open |
| §F border chooser, Layout, Size and Position | Need a Vision window with a component selected | A Vision window, by hand |
| Relaunch-comes-up-stock | Never run: the saved preference is dark, so a relaunch comes up dark | Toggle off, relaunch, confirm stock, toggle back |
| §E view editor rulers and surround | Not a gap in testing — an undecided question. They are chrome and they stay light | A decision |

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

Also out of scope by our own choice: **the Vision design canvas**, which renders
your own window content — theming it would misrepresent what your users will see.
