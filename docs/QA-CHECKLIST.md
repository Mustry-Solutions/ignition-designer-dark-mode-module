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

## Running the sweep

1. Launch the Designer with the debug flag so popup and window contents are
   logged with their **real 8.3 class names**:
   `-Ddesignerdarkmode.debug=true` (see
   [DEVELOPMENT.md](DEVELOPMENT.md#the-debug-log)).
2. **Tools → Dark Mode** on.
3. Walk the sections below. Open each surface, look at it, record a result.
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
| — | Not yet checked |

Locations marked **(unverified)** are inferred from the 8.1 class names and have
not been confirmed on 8.3. Correct them in place as you go — that is half the
point of the first run.

---

## A. Main shell

| Surface | Where | Result | Notes |
|---|---|---|---|
| Menu bar and menus | Top of the main frame | — | |
| Toolbars | Below the menu bar | — | |
| Dock title bars, grippers, split dividers | Any docked panel | — | |
| Section headers / collapsible title panes | Left and right docks | — | |
| Project Browser tree | Left dock | — | |
| Status bar | Bottom of the frame | — | |
| Output Console | Bottom dock | — | |
| Tab strips (open resource tabs) | Above the workspace | — | |

## B. Menus, dialogs, project settings

| Surface | Where | Result | Notes |
|---|---|---|---|
| Project → Properties: Project General | Project → Properties | — | |
| Project → Properties: Project Permissions | " | — | |
| Project → Properties: Project Designer | " | — | |
| Project → Properties: Vision General | " | — | Vision module required |
| Project → Properties: Vision Launching | " | — | |
| Project → Properties: Vision Login | " | — | |
| Project → Properties: Vision Timing | " | — | |
| Project → Properties: Vision UI | " | — | |
| Project → Properties: Perspective General | " | — | Perspective module required |
| Project → Properties: Perspective Permissions | " | — | |
| Project → Properties: Perspective Inactivity | " | — | |
| Project → Properties: Perspective Tag Drop | " | — | |
| Project Export dialog (`CheckBoxTree`) | File → Export | — | Check the tri-state checkboxes, not just the panel |
| Project Import dialog (`CheckBoxTree`) | File → Import | — | |
| Keyboard Layout | Tools → Keyboard Layout **(unverified)** | — | |
| Diagnostics dialog | Help → Diagnostics **(unverified)** | — | Known cached-panel path; see ARCHITECTURE gotchas |
| About dialog | Help → About | — | |

## C. Tags

| Surface | Where | Result | Notes |
|---|---|---|---|
| Tag Browser tree | Tag Browser dock | — | Known pale band under the `Tag \| Value` header ([#21](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/21)) |
| Tag Browser filter field | Top of the Tag Browser | — | |
| Tag Editor | Double-click a tag | — | |
| Tag data type / binding sub-editors | Inside the Tag Editor | — | |
| UDT definition editor | Tag Browser → UDT Definitions | — | |
| Tag import/export dialogs | Tag Browser hamburger menu | — | |

## D. Scripting

| Surface | Where | Result | Notes |
|---|---|---|---|
| Project Library editor | Project Browser → Scripting → Project Library | — | |
| Project Library nav tree | Left of that editor | — | Source of a `JTree` popup in §K |
| Script editor gutter, autocomplete popup | Type inside any script editor | — | Autocomplete is its own popup window |
| Script Console | Tools → Script Console | — | Input and output panes |
| Gateway Events editor | Project Browser → Scripting → Gateway Events | — | |
| Client/Session Events editor | Project Browser → Scripting → … Events | — | |

## E. Perspective

| Surface | Where | Result | Notes |
|---|---|---|---|
| View editor canvas | Open any view | — | |
| Component palette | Right dock | — | Guard hierarchy walks against `FilterablePalette` (see [Out of scope](#out-of-scope)) |
| Property editor tree | Right dock, view open | — | Property tables deliberately untouched — see [Out of scope](#out-of-scope) |
| Property key editor field | Click a property name | — | |
| Binding editor dialog | Click a property's binding icon | — | |
| Component scope / node picker in a binding | Inside the binding editor **(unverified)** | — | |
| Style editor | Project Browser → Styles | — | |
| Page Configuration | Perspective → Page Configuration | — | |

## F. Vision

| Surface | Where | Result | Notes |
|---|---|---|---|
| Window editor chrome | Open any Vision window | — | The canvas itself is deliberately not themed (README) |
| Component palette | Left/right dock with a window open | — | |
| Property Inspector | Right dock | — | |
| Border chooser (9 sub-panels) | Property Inspector → border property | — | Check every tab of the chooser |
| Layout dialog | Right-click a component → Layout **(unverified)** | — | |
| Size and Position dialog | Right-click a component → Size and Position **(unverified)** | — | |
| Dataset editor dialog | A dataset property → edit | `pass` | 2026-08-29 |
| Custom property editor | Component → custom properties | — | |

## G. Alarm notification pipelines

Not on the Exchange list — added from the 2026-08-29 run.

| Surface | Where | Result | Notes |
|---|---|---|---|
| Pipeline Blocks palette | Top dock with a pipeline open | `pass` | |
| Pipeline canvas background | `BlockDesignableContainer` | `pass` | |
| **Pipeline blocks themselves** | Blocks on the canvas | `fixed` | Was: pale-blue fill with near-white text. `START` was unaffected. See below |
| Pipeline Block Editor panel | Left dock | `pass` | |

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

| Surface | Where | Result | Notes |
|---|---|---|---|
| Report Data tab | Open a report → Data | — | |
| Report Design tab | Open a report → Design | — | |
| Schedule tab | Open a report → Schedule | — | |
| Scheduled parameters panel | Schedule tab → Parameters | — | |
| Property inspector | Design tab, right side | — | |
| Pie chart configuration | Design tab → a pie chart | — | |

## I. Search, replace, translation

| Surface | Where | Result | Notes |
|---|---|---|---|
| Find/Replace panel | Ctrl-F / Cmd-F | — | |
| Find/Replace provider dropdown | Inside Find/Replace | — | Dropdown *popup*, not just the closed control |
| Find/Replace target checkboxes | Inside Find/Replace | — | |
| Find/Replace scrollable result list | After a search | — | Result rows are renderer-painted |
| Translation Manager | Tools → Translation Manager | `pass` | 2026-08-29 |

## J. Images and symbols

| Surface | Where | Result | Notes |
|---|---|---|---|
| Image Management panel | Tools → Image Management **(unverified)** | — | |
| Symbol Factory browser | Vision palette → Symbol Factory **(unverified)** | — | Module required |
| Symbol Factory thumbnail gallery | Inside that browser | — | |
| SVG canvas (`JSVGCanvas`) | Symbol Factory preview | — | Third-party Batik canvas; may not honour Swing colours |

## K. Database and queries

| Surface | Where | Result | Notes |
|---|---|---|---|
| Query Browser | Tools → Database Query Browser | — | |
| Query Browser result table | After running a query | — | |
| Named Query editor | Project Browser → Named Queries | `pass` | 2026-08-29, selector only |
| Named Query parameter table | Inside the Named Query editor | — | |
| Named Query security table | Inside the Named Query editor | — | |

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

| 8.1 source class | Where to right-click | Result | 8.3 class from the log |
|---|---|---|---|
| `TagFrameTree` | Tag Browser tree | `pass` | `TagPopupMenu` — arrived **stale**, repaired before paint |
| `NavTreePanel$1` | Project Browser tree | `pass` | `NodeContextMenu` (8 openings) |
| `Graphics2dRenderWidget` | Perspective view editor canvas | `pass` | `JPopupMenu`, 15 items |
| `InteractionLayer` | Vision window editor canvas | `pass` | `JPopupMenu` with a `CustomizerMenu` submenu, 17 items |
| `BorderlessField` / `PerspectiveKeyEditor` | A text field's cut/copy/paste menu | `pass` | `JPopupMenu`, 3 items |
| `JTree` | Project Library nav tree (Scripting) | `pass` | `NodeContextMenu`, 14 items |
| `NodeEditor$MainEditor`, `…$1` | Perspective property editor, a property row | `pass` | `JPopupMenu`, 7 and 15 items, right after `PropertyEditorFrame` attached |
| `ComponentScopeEditor$…$2` | Binding editor → component/property picker | `pass` | `JPopupMenu` |
| — | UDT instance menu (Tag Browser) | `pass` | `TagActions$udtInstanceMenu$1` — arrived **stale**, repaired |
| — | Combo dropdowns | `pass` | `FlatComboPopup` |
| — | An IA error popup | `pass` | `DefaultPopupWindowParent` → `ErrorPanel` — **not a `JPopupMenu`**, see below |

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

## Toggle-off spot check

Reversibility is a project invariant, and restore has broken on its own before
(the amber property editor,
[#23](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/23),
took three wrong diagnoses). After the sweep, toggle **Tools → Dark Mode** off
and confirm these return to stock:

| Surface | Result | Notes |
|---|---|---|
| Perspective property editor | — | The #23 surface |
| Menus and menu items | — | |
| Tag Browser | — | |
| Dock title bars and dividers | — | |
| Script editor and console | — | |
| Tree and table cell colours | — | Renderers are restored from tracked sets, not the live tree |
| **With a Vision window open** | `fixed` | The throw is now prevented rather than contained — see below |

Then relaunch the Designer and confirm it comes up stock.

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
