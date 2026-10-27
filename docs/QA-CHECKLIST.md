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
handler dialog. Those are now rows here, marked **(1.3.0)**, all confirmed on
8.3.6 on 2026-10-17.
It also stops forcing a foreground on the Perspective binding icons (see §E).
Gratefully acknowledged; see [Prior art](../README.md#prior-art).

This module themes top-down (look-and-feel swap plus token mutation) rather than
by painting enumerated classes, so most of these should already be dark. The
value is knowing *where to look*.

## Runs

Newest first.

| Date | OS | Ignition | Vision | Module | Scope covered |
|---|---|---|---|---|---|
| 2026-10-27 | Linux (cloud container, Xvfb 1920×1080, no window manager) | 8.3.6 | 12.3.6 | `47f01a0` ([#200](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/200) branch head), dev-signed via `ops/setup.sh` | **Match System ([#201](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/201)), §M's four new rows**, driven by Claude on a Designer it launched itself (the gateway's own Designer Launcher on a virtual display, clicks by `xdotool`), a fresh project `test` and an empty preference node. The container has no desktop to report an appearance, so a stand-in `gsettings` on the Designer's PATH answered `color-scheme` from a file the sitting flipped; the portal call failed (no session bus) and fell through to it, as on a GNOME without the portal. Everything after the command is the shipped path: the module starting the process, parsing the answer, the switch, the menu and the saved keys. A new install showed Tools → Dark Mode as a submenu with Light selected, one About item. Match System with the system dark: dark within seconds, logged `Match System: the system reads as DARK (portal color-scheme (exit 1), gsettings color-scheme (exit 0), …)`, saved `followSystem=true darkMode=true`; dark again after File → Exit and a relaunch, the system read at launch, Match System still selected. System set light, relaunch: stock light, `followSystem=true darkMode=false`. Then Dark, then Light: each switched, and each saved `followSystem=false`. The submenu was legible with its selection visible in both themes. No failure in the debug log. Not covered: Windows and macOS in a Designer (CI's `SystemThemeLiveTest` runs the real command on both), a real GNOME or KDE desktop, 8.1 |
| 2026-10-17 | Linux (cloud container, Xvfb 1920×1080, no window manager) | 8.3.6 | 12.3.6 | `3c81dd4` (main), dev-signed via `ops/setup.sh`; the three fixes from this run (#41's PR) checked live on the same gateway via `ops/deploy.sh` | **The #41 sweep: §A–F and §H–K, §L, the toggle-off check and a stock relaunch**, driven by Claude on a Designer it launched itself (the gateway's own Designer Launcher, clicks and palette drags by `xdotool`, real right-clicks). Each surface was judged from a 100% screenshot and a probe run in the Designer's JVM: a command runner started from the Script Console walked each window for opaque components lighter than luminance 0.62 under dark, darker than 0.40 after light, and delegates from the other look and feel. Seeded for it: a SQLite connection `qa` (the driver ships with the gateway), a Perspective view with a tag-bound Label, a Button and a Table, a style class, a Vision Main Window (Button, Text Field, Tab Strip, Table, Easy Chart) and a template, a report with a pie chart and a schedule, an SFC chart, a named query, a project library script, a gateway message handler, a `Motor` UDT with an instance. Perspective's Designer hook needs `-Dignition.jxBrowser.sandboxMode=DISABLE` in a container run as root, or JxBrowser fails to start and the Perspective workspace never loads. **Found:** three fixed in the PR (SFC chart, Export/Import tree icons, the Tag Browser's selected Value cell) and five filed: [#194](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/194) (Border Chooser paints blank), [#195](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/195) (Style Customizer header, style editor's Margin and Padding diagram), [#196](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/196) (Diagnostics Scripts tab, Log Viewer details), [#197](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/197) (touchscreen keyboard preview), [#198](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/198) (window backgrounds after the restore). Fourteen locations corrected or confirmed. Not covered: Windows, macOS, 8.1, the native title bar (no window manager), and the rows still marked — |
| 2026-10-12 | Linux (cloud container, Xvfb) | 8.3.6 | 12.3.6 | `e91e3f4` (main) and `37cbcb2` (main before #187) as controls, then the fix branch for [#188](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/188), dev-signed via `ops/setup.sh` / `ops/deploy.sh` | The live check for #188, driven by Claude on a Designer it launched itself (the gateway's own Designer Launcher on a virtual display, clicks by `xdotool`), project `test` with a Main Window holding one Slider, and a Script Console probe that runs `prepareRenderer` over every row of the property table and lists the renderer components still on a FlatLaf delegate. The issue's sequence (light with the property editor already shown, dark, slider selected, a probe, light) left 0 on main and `37cbcb2`: those editors were first built under light, refreshed by a dark paint and so restored. A fresh Designer that goes dark before the property editor shows anything, then the slider selected, its properties scrolled down and back up, then light: on main and `37cbcb2` 10 components were still on FlatLaf after the switch (editors below the fold, the colour and font editors among them), and scrolling to them drew them stock, the light watcher refreshing each after its first paint. On the branch, 0 after each of two switches, and the scrolled rows drew stock. Under dark the slider's Foreground Color reads 221,221,221 (46,46,46 under stock and after light) on main and the branch alike, so that predates this. Not covered: 8.1, Windows, macOS, and every checklist section not named here |
| 2026-10-12 | Linux (cloud container, Xvfb) | 8.3.6 | 12.3.6 | `c277ce4` (the #141/#186 branch head), dev-signed via `ops/setup.sh` | Driven by Claude on a Designer it launched itself (the gateway's own Designer Launcher on a virtual display, clicks by `xdotool`), a fresh project `test` with one new Main Window. A Script Console probe recorded every table's mouse listener order under stock, after dark, and after light. The 14 tables present under stock were in their stock order in dark and again after light, the Vision property table included (`TableColumnResizer$a TreeTable$ExpandMouseListener ToolTipManager BasicNavigableTableUIDelegate$a PropertyTablePanel$1`); the #140 sitting had 13 of 14 after light, the 14th being that table with the expand listener moved behind the UI handler ([#186](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/186)). Two dataset editor tables built under dark were also back on stock delegates in their own order after light. In the Vision Property Editor, a click on the Behavior group's arrow collapsed or expanded it exactly once, under dark and after light. Under dark, File → Open…, a hover and one click on `test`'s OPEN re-opened the project (#130). No failure logged by the module in either direction. No control run on main in this sitting: the #140 and #186 sittings recorded main's order for the same table. Not covered: 8.1, Windows, macOS, and every checklist section not named here |
| 2026-10-07 | Linux (Docker on macOS, Xvfb) | 8.3.6 | 12.3.6 | `70cfd7f` (the #185 branch head), with `9245077` and `c25af55` (earlier heads of the same branch) and `067cd23` (main) for comparison, dev-signed via `ops/deploy.sh` | Driven by Claude on a Designer it launched itself (the gateway's own Designer Launcher on a virtual display, clicks by `xdotool`), project `test` with Main Window open. Dark, the window's slider selected under dark (so the Vision Property Editor builds the slider's editors under FlatLaf), light, dark, light, with a Script Console probe after each. On `70cfd7f`, `9245077` and `c25af55`: no failures logged, and the main frame's root pane fully on `FlatRootPaneUI` under dark and on `SynthRootPaneUI` after light. The probe found the same 14 property editor renderer components still on FlatLaf after light on all three, likely rows off screen during the switch. On `067cd23` every switch logged `updateUI failed for javax.swing.JRootPane` and the root pane stayed on `FlatRootPaneUI` with `rootPane` null. The slider's Border reads "No Border" under dark and "Other Border" under stock and light on main too, so that predates #185. Not covered: 8.1, Windows, macOS, and every checklist section not named here |
| 2026-10-06 | Linux (cloud container, Xvfb) | 8.3.6 | 12.3.6 | `65e013c` (main after #182) as the control, then the fix branch for the root pane failure behind a kept cell renderer at `d556a76` (its first, dark-only listener; the later light-direction refresh was not checked live), dev-signed via `ops/setup.sh` / `ops/deploy.sh` | Driven by Claude on a Designer it launched itself (the gateway's own Designer Launcher on a virtual display, clicks by `xdotool`), project `test` with one new main window open, so the Vision Property Editor showed its properties. On `65e013c` the switch to dark logged `updateUI failed for javax.swing.JRootPane` with Synthetica's `ImagePainter` NPE from the property editor's `EditorComboBox` cell, and the switch to light the `FlatRootPaneUI.uninstallClientDecorations` NPE. A Script Console probe showed the main frame's root pane on `FlatRootPaneUI` with its `rootPane` field null under dark and still after the light restore. Under dark the binding icons on the combo box rows were dimmed, unlike every other row and unlike light. On the branch, dark → light → dark → light logged no failures, the root pane was fully on `FlatRootPaneUI` under dark and on `SynthRootPaneUI` after light, and the binding icons matched. Not covered: 8.1, Windows, macOS, and every checklist section not named here |
| 2026-09-30 | macOS | 8.3.6 | 12.3.6 | `cbd5caf` (main after #166) as the control, then `c4e5c22` ([#169](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/169) branch), dev-signed via `ops/deploy.sh` | The live check for [#168](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/168), driven by Claude on Sam's screen, on `test`'s project library script and the default tag provider. On main under dark, ⌘C / ⌘V in the Project Browser did nothing; light made the copy; the Tag Browser worked under dark. On #169, both browsers worked in every state tried (started dark, switched to dark at runtime, light after the restore), including ⌘X / ⌘V in the Project Browser. Found on the way: a shortcut pressed in the same instant as the click that moves focus from one dock to the other goes to the previously focused dock and does nothing, in light as well, so it is Ignition's and not a regression. Not covered: right-click Copy / Paste, Windows (Ctrl), and 8.1. The project was not saved and the pasted tags were deleted |
| 2026-09-28 | Linux (cloud container, Xvfb) | **8.1.33** (the 8.1 floor), then 8.3.6 for comparison | 11.1.33 | `887a4d6` (main at the 0.6.0 changelog), `designer-dark-mode-8.1.modl` dev-signed via `IGNITION_LINE=8.1 ops/setup.sh` with `IGNITION_81_VERSION=8.1.33` | The first live Designer on the 8.1 floor, driven by Claude on a Designer it launched itself: the gateway's own 1.1.33 Designer Launcher on a virtual display, clicks by `xdotool`, results read from screenshots and the debug log. A fresh project `test`: Tools held one Dark Mode and one About item; dark → light → dark → light → dark, each visually correct; the Script Console themed (banner, prompt, editor); a `Motor` UDT with an alarm and a `valueChanged` script plus a `Motor1` instance, built from the Script Console, and under dark the inherited alarm's override icon and the selected inherited event's both toggled on and off by click (#135, #136); a second project `test2` created and switched to, still dark, one set of Tools items (#131); File → Open… on the switched Designer, one click on `test`'s OPEN opened it (#130); a Vision window with a Text Field, Button and Progress Bar dropped under dark and saved, `ops/vision-check.sh` clean (no FlatLaf class, no `setFont`, the two colour calls §N expects). **Found ([#164](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/164)):** every switch logs `updateUI failed for javax.swing.JRootPane`. On the way into dark, `FlatRootPaneUI.installDefaults` sets the frame's background, the X11 peer repaints synchronously inside that call, and the dock title panes, still holding `SyntheticaJidePainter` because the cached-painter repoint runs after the tree update, throw `ClassCastException`. The root pane's UI is left half-installed, so every later switch (both directions) fails on it with an NPE in `FlatRootPaneUI.uninstallClientDecorations`, and after a switch to light the main frame's root pane is still on `FlatRootPaneUI`. The rest of the tree is walked regardless and nothing visible was wrong. The same `ClassCastException` appears in an 8.3.6 Designer started dark on this display, so it is Linux (X11), not 8.1; macOS does not repaint inside `setBackground`. Not covered: Windows, an 8.1 Maker Edition gateway, a Vision client opening the saved window, and every checklist section not named here |
| 2026-09-27 | Linux (cloud container, Xvfb) | 8.3.6 | 12.3.6 | `2d97196` (main after #153), then `6dd6afc` and **v0.5.0 as released** for comparison, dev-signed via `ops/setup.sh` / `ops/deploy.sh` | The live check for [#135](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/135) and [#136](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/136), driven by Claude on a Designer it launched itself: the dev gateway in Docker, the Designer Launcher on a virtual display, clicks by `xdotool`, and results read from screenshots. The test data was a `Motor` UDT with a memory tag carrying an alarm and a `valueChanged` script, plus one instance, `Motor1`, built from the Script Console. On `2d97196` under dark, a click on the inherited alarm's override icon, and on the selected inherited event's, toggled the override exactly as it did in light. On `6dd6afc` neither click did anything, and the event row could not even be selected with the mouse. The sitting also found three things that look the same on v0.5.0, so they predate the fix: [#156](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/156) (Tag Editor property table dark after a dark→light switch), [#157](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/157) (alarm name near-black on the dark list) and [#158](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/158) (selected event text dark on the selection). Not covered: an 8.1 Designer, and macOS or Windows |
| 2026-09-25 | macOS | 8.3.6 | 12.3.6 | [#134](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/134) branch, dev-signed | The Script Console check for [#129](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/129), driven by Claude on Sam's screen, colours measured from the console window's pixels. Banner, traceback and a `print` written while light, then Dark Mode on with the console open, more written while dark, off, and on again. Every line had the expected colour at every step (see the Script Console row); the debug log agrees (5 runs recoloured, 8 restored, 8 recoloured) with no failures. The first sitting on the branch found a second bug, regular text `#DDDDDD` on white after switching off; fixed on the same PR and re-measured. Not covered: an 8.1 Designer, where #129 was first seen, and the diagnostics console |
| 2026-09-24 (evening) | macOS | 8.3.6 | 12.3.6 | `4f4cdf8` ([#131](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/131) branch), dev-signed | The project-switch check for #131, driven by Claude on Sam's screen, using the new empty `test2` project on the dev gateway: File → Open `test`, then `test2`, then back to `test`. Tools held exactly one Dark Mode and one About Designer Dark Mode… after each open (before the fix, each open added another pair). After the second switch, Tools → Dark Mode still toggled both ways (log: `switching to light`, then `switching to dark`). Each switch also logs a light/dark pair about 2 s apart: the old hook's shutdown, then the new hook's startup, not a toggle. Not covered: an 8.1 Designer, where the bug was first seen |
| 2026-09-24 (afternoon) | macOS | 8.3.6 | 12.3.6 | `6e98997` (main after #126), dev-signed | Short sitting, driven by Claude on Sam's screen: one dark/light cycle on `test` (dark 0.8 s, light 0.24 s, no failures logged), the About dialog in both themes (§M), and the debug log's UTC `Z` timestamps — every line the session wrote carries one |
| 2026-09-24 | macOS | 8.3.6 | 12.3.6 | `e48f3c9` (main after #123), dev-signed | The narrow sitting for [#121](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/121) — [#42](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/120)'s three renderer-unwrap fixes, driven by Claude on Sam's screen. Measured with [the renderer probe](#the-renderer-probe) rather than by eye. **After a full dark→light cycle the whole renderer inventory is identical to a plain `updateComponentTreeUI` baseline taken in the same session** — every tree, every table, renderer class, row height and row count — and no FlatLaf class survives anywhere. Populated table (`TagValuesTable`, 3 rows): `SyntheticaDefaultTableCellRenderer` back, every row band white. Opened *while dark* and restored clean: OPC Browser tree, Query Browser schema tree, and the Tag Browser closed and rebuilt under dark — all back to their stock renderers. Under dark, all 74 trees and 15 tables carry the module's wrappers, so the fixed path is genuinely exercised. Relaunch comes up stock. **Not exercised: a tree using the look and feel's own renderer** — this Designer has none (`createdRenderer=False` on all 74, in the stock Designer too), and no lazily-built tree renderer was found. One apparent regression (`EventTimelinePanel$TimelineHeader$2` → Synthetica's) reproduced with the module uninvolved, so it is Swing/Synthetica's, not ours |
| 2026-09-20 | headless harness, all three CI platforms | — | — | branch for #76 | `UiScaleDisableTest`: under simulated Synthetica-scaled fonts (Dialog 18 / 24 pt) dark mode keeps the size, so it cannot be undersized relative to stock; FlatLaf's user-scale path is inert under the shipped property. What the harness cannot see is the JDK transform itself. Closes the investigation in [#76](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/76); a live Windows/Linux `env:` block remains the only real corroboration, via [#96](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/96) |
| 2026-09-18 (afternoon) | macOS | 8.3.6 | 12.3.6 | dev build of main, then the #89 branch, dev-signed via the new worktree-aware `ops/deploy.sh` | First run of §O, driven by Claude on Sam's screen: the Exchange 1.3.0 script seeded into `test`, its checkbox added from the Script Console, all five transitions of the two toggles recorded with screenshots (see §O's last row). Then on the fix: startup notice, checkbox arriving disabled, the console-ticked case unticked by the next click |
| 2026-09-18 (release) | macOS | 8.3.6 | 12.3.6 | **v0.4.1 as released** (the GitHub asset, release certificate, seeded on the dev gateway) | Short sitting on the released patch, driven by Claude on Sam's screen: Designer came up dark on Vision (`Designer Dark Mode [v0.4.1]` in the console), Main Window opened under dark, a fresh Comments Panel dropped from the palette (property editor: Border "No Border"), Save All; checker clean, byte scan clean (no FlatLaf class, no font, no dark value), no failed phase in the log; the Vision client opened the window with the new panel light, Synthetica's own outline and black text, beside the two panels the fix had repaired the evening before |
| 2026-09-17 (evening) | macOS | 8.3.6 | 12.3.6 | **v0.4.0 as released** | The five palette kinds the headless sweep cannot build, dropped under dark on Sam's screen: Slider, Spinner, both Comments Panels, Easy Chart. Every save carried a FlatLaf border class by name — the first real corruption since the gate came out, missed by the sweep. Root cause and fix the same evening (`LookAndFeelBorders`, `VisionConstructionBorders`); then the dev gateway ran the fix, the corrupted window was re-saved clean from a dark Designer, and the Vision client opened it |
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
| Menus and menu popups | Top of the main frame | `pass` | `2026-10-17` | File / Project / Tools / Help popups. 2026-10-17 on Linux 8.3.6: every popup opened in the sweep was `FlatPopupMenuUI` on #303234 |
| macOS system menu bar | Top of the screen | `skip` | `2026-08-31` | Drawn by the OS and following the system appearance — not reachable from a Swing look and feel. Neither is the search field inside the Help menu |
| In-window menu bar (Windows, Linux) | Top of the main frame | `pass` (Linux) | `2026-10-17` | Linux only: 8.1.33 under Xvfb on 2026-09-28, 8.3.6 on 2026-10-17; the bar, menu titles and popups dark and legible. Windows not yet seen. The opposite case: off macOS the menu bar is a Swing `JMenuBar`, so the swap DOES theme it. Menu titles, hover, mnemonics and the accelerator text in the popups |
| Native title bar and window frame (Windows, Linux) | Around every window | `skip` | — | Stays light by decision (see [ARCHITECTURE](ARCHITECTURE.md#the-switch-step-by-step), step 10): the `apple.awt.windowAppearance` property is macOS-only, and FlatLaf window decorations on Ignition's frames would be a larger change than the gap justifies. Record it, do not file it |
| Toolbars | Below the menu bar | `pass` | `2026-10-17` | incl. the Vision workspace's extra toolbars |
| Dock title bars, grippers, split dividers | Any docked panel | `pass` | `2026-10-17` | |
| Section headers / collapsible title panes | Left and right docks | `pass` | `2026-10-17` | `SESSION PROPS`. PROPS / POSITION / CUSTOM / META on 2026-10-17 |
| Project Browser tree | Left dock | `pass` | `2026-10-17` | |
| Cut / copy / paste shortcuts on Project Browser items | Select a named query or script, Ctrl+C (⌘C), select a folder, Ctrl+V (⌘V) | `fixed` | `2026-09-30` | [#168](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/168): under dark the keys did nothing and only the right-click menu worked. Pinned by `TreeClipboardKeysTest` (8.3.8 and 8.1.55). **Confirmed live on 8.3.6 (macOS), 2026-09-30:** on main, ⌘C / ⌘V on a project library script made no copy under dark and did in light. On the fix, ⌘C / ⌘V and ⌘X / ⌘V worked in a Designer that started dark, after a switch to dark at runtime, and in light after the restore. Right-click Copy / Paste was not checked (synthetic right-clicks do not open the tree's menu). Press the key about a second after clicking from another dock into this tree: a key sent in the same instant goes to the previously focused dock, in light too |
| Status bar | Bottom of the frame | `pass` | `2026-10-17` | |
| Output Console | Bottom dock | `fixed` | `2026-08-31` | [#52](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/52). Check BOTH halves: text already on screen when the switch happens, and lines that arrive afterwards — they are different mechanisms |
| Workspace tab strip (open resource tabs) | Bottom edge of the Perspective, script, named query, report and Web Dev workspaces | `fixed` | `2026-10-17` | [#81](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/81) — **Windows only**: the selected tab kept the OS's #E3E3E3 3D colour with light text on it. Reproduced and fixed headlessly (`WorkspaceTabStripTest` simulates the Windows path). Checked by eye on macOS 2026-09-15 (text contrast, which was ~3:1 there before the fix): pass. The Windows fill itself has NOT been seen by eye — no Windows machine available; ask the reporter to confirm on the release build. Linux 2026-10-17: script, report, view and window tabs dark |

## B. Menus, dialogs, project settings

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Project → Properties: Project General | Project → Properties | `pass` | `2026-10-17` | incl. the nav list, combos, checkboxes and OK/Apply/Cancel |
| File chooser dialogs (Windows, Linux) | File → Import / Export, Tag Browser ⋮ → Export Tags / Import Tags | `pass` (Linux) | `2026-10-17` | Linux 8.3.6: a Swing `JFileChooser` on `FlatFileChooserUI`, Save and Open, FlatLaf's own folder icons, probe clean. On macOS the Designer may hand you a native `FileDialog`, which no look and feel reaches. Windows not yet seen |
| Open/Create Project dialog (`ProjectListTable`) | File → Open… | `fixed` | `2026-09-28` | [#130](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/130): the OPEN buttons did nothing while dark (seen live on 8.1.50 and 8.3.6). Two causes: IA's mouse listener casts the action column's renderer (our wrapper broke the cast), and under FlatLaf the table's UI handler lands behind that listener, which consumes the press. Pinned by `TableRendererCastTest`. **Confirmed live on 8.1.33 (Linux), 2026-09-28:** one click on OPEN, after a project switch, opened the project. **Confirmed live on 8.3.6, 2026-09-24:** hover highlights the row and button, one click on OPEN launches the project, and the action column is dark. With only the cast fixed, the click selected the row and launched nothing, while light launched on one click. That was the control that found the second cause. The Krisp overlay covers this dialog's OPEN column; drag the dialog left first |
| Project → Properties: Project Permissions | " | `pass` | `2026-10-17` | |
| Project → Properties: Project Designer | " | `pass` | `2026-10-17` | |
| Project → Properties: Vision General | " | `pass` | `2026-10-17` | |
| Project → Properties: Vision Launching | " | `pass` | `2026-10-17` | |
| Project → Properties: Vision Login | " | `pass` | `2026-10-17` | |
| Project → Properties: Vision Timing | " | `pass` | `2026-10-17` | |
| Project → Properties: Vision UI | " | `pass` | `2026-10-17` | The Client Background Color swatch stays light — that is a colour *value*, correctly left alone |
| Project → Properties: Perspective General | " | `pass` | `2026-10-17` | |
| Project → Properties: Perspective Permissions | " | `pass` | `2026-10-17` | |
| Project → Properties: Perspective Inactivity | " | `pass` | `2026-10-17` | |
| Project → Properties: Perspective Tag Drop | " | `pass` | `2026-10-17` | |
| Project → Properties: Perspective Symbols | " | `pass` | `2026-10-17` | |
| Project → Properties: Perspective Offline Mode | " | `pass` | `2026-10-17` | Not in the 8.1-era list; on 8.3.6 |
| Project → Properties: Vision Design | " | `pass` | `2026-10-17` | Not in the 8.1-era list; on 8.3.6. The Default Color Mapping table is dark |
| Project → Properties: Vision Permissions | " | `pass` | `2026-10-17` | Not in the 8.1-era list; on 8.3.6 |
| Project Export dialog (`CheckBoxTree`) | File → Export… | `fixed` | `2026-10-17` | Tri-state checkboxes legible. The resource icons were not: `TreeIconRecolorer` skipped JIDE `CheckBoxTree`s outright, so each resource's `NavTreeIcon` kept its stock dark grey (luminance 70–86 on #3C3F41, the QA view's nearly invisible). Fixed on #41's PR by recolouring at paint time, as for the event tree in #136; `CheckBoxTreeIconTest`. Confirmed live: icons visible, a real click still toggles a box |
| Project Import dialog (`CheckBoxTree`) | File → Import… → choose a project export | `fixed` | `2026-10-17` | `ProjectImporter`, the same `CheckBoxTree` as Export, so the same icon fix |
| Security-level tree (`SecurityLevelTree`, a `CheckBoxTree`) | `PermissionsConfigurator` and the tag permission model editor (`SecurityLevelPermissionModelEditor`); exact menu path unconfirmed | — | — | Added after the 2026-10-17 run: #41's Export/Import fix recolours every `CheckBoxTree`'s icons at paint time, this one included. Not yet seen live with that fix |
| Keyboard Layouts | Tools → Keyboard Layouts, **only while the Vision workspace is active** | `pass` | `2026-10-17` | **Correction:** the item exists on 8.3.6, but only in the Vision workspace's Tools menu, which is why the 2026-08-31 run, in another workspace, found none. `KeyboardEditor` ('Touchscreen Keyboard Layouts'): the layout list and JSON definition editor are dark |
| Touchscreen keyboard preview | Keyboard Layouts → a layout → Preview (▶) | `light` | `2026-10-17` | [#197](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/197): the letter keys are dark, but the function keys (Backsp, Enter, Shift, Cancel, !123, EN, the arrows) are white on #C4C9CD, about 1.6:1. `InternationalKeypadDialog`'s `SYSTEM_KEY_REST` is `Colors.Base800`, which the token pass lifts; the text is `Base000`. Stock: #70757A with white text |
| Diagnostics dialog | Help → Diagnostics | `partial` | `2026-10-17` | The tip banner ([#47](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/47)) and chart axes ([#50](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/50)) still hold. Performance, Console, Logging Levels, Thread Viewer and Connections are dark. **Scripts** is `light`: `ScriptsTab$ScriptTable` extends `PrettyTable`, whose `paintEmptyRows` fills white and #E1EAF4 stripes, so the empty table is all stripes. **Log Viewer** is `partial`: the details pane is an HTML table with a hard-coded white stylesheet. Both in [#196](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/196) |
| Diagnostics performance charts | Help → Diagnostics → Performance | `fixed` | `2026-10-17` | Axis paints AND the chart's own background — [#50](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/50). The first fix did only the axes and the label margin stayed white |
| About dialog | Help → About Ignition Designer | `pass` | `2026-10-17` | Ignition's own (`DefaultPopupWindowParent`); the module's is in §M |

## C. Tags

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Tag Browser tree | Tag Browser dock | `fixed` | `2026-10-17` | The pale band under `Tag \| Value` ([#21](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/21)) is gone, both header cells. 2026-10-17: a selected tag's **Value** cell (and the UDT Definitions tab's Config cell) kept the light theme's #47A9E6 with white text: IA's `ValueRenderer` copies four `Tree.*` colours in its constructor. Fixed on #41's PR; `TagValueSelectionTest`. Confirmed live: #4B6EAF with light text |
| Tag Browser filter field | Top of the Tag Browser | `pass` | `2026-10-17` | |
| Cut / copy / paste shortcuts on tags | Select a tag, Ctrl+C (⌘C), select a folder, Ctrl+V (⌘V) | `pass` | `2026-09-30` | [#168](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/168): same wiring as the Project Browser (an `EditHandlingFrame` whose tree has a transfer handler), but **it was never broken**. Live on 8.3.6 (macOS), 2026-09-30: on main under dark, ⌘C / ⌘V on a tag pasted a copy. FlatLaf's binding most likely did the copy through the tree's own transfer handler, which handles tags. On the fix the keys go to the Edit menu instead, and a tag copy and paste worked under dark (after a switch at runtime) and in light. Same timing trap as the Project Browser row: wait a moment after clicking into this tree before pressing the key |
| Tag Editor | Double-click a tag | `pass` | `2026-10-17` | incl. the JIDE property table and category list. The three 2026-09-27 findings are fixed (0.6.2): the alarm name reads light ([#157](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/157)), a selected event reads light on the selection ([#158](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/158)), and a Tag Editor opened after a dark→light switch has a fully stock property table ([#156](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/156)) |
| Tag data type / binding sub-editors | Inside the Tag Editor | `pass` | `2026-08-31` | |
| Override icons on inherited alarms and event scripts | Tag Editor on a UDT instance member → Alarms, and → Scripting | `fixed` | `2026-09-28` | [#135](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/135), [#136](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/136): found by a bytecode sweep. Each click casts the list's or tree's own renderer, and the module's wrapper broke the cast, so a click on the icon did nothing while dark. Pinned by `OwnedRendererCastTest`. **Confirmed live on 8.3.6, 2026-09-27:** under dark, a click on an inherited alarm's icon, and on the icon of an inherited event script once it is selected, toggles the override, as in light. On the build before the fix neither click did anything, and the event row could not be selected with the mouse either. The event toggle acts on the *selected* event in light too, so select it first. **Confirmed live on 8.1.33 (Linux), 2026-09-28:** both icons toggle on and off under dark |
| UDT definition editor | Tag Browser → UDT Definitions → double-click a definition | `pass` | `2026-10-17` | The same `TagEditorDialog` ('UDT Definition Editor') with the Type Structure tree; a member's Alarms and Scripting categories checked |
| Tag import/export dialogs | Tag Browser ⋮ menu → Export Tags; Import Tags → Interactive / Direct | `pass` | `2026-10-17` | Export goes straight to a file chooser and an 'Export Finished' message; Interactive import opens `TagCreatorDialog` ('Advanced Tag Import'). All dark |

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
| Project Library editor | Project Browser → Scripting → Project Library → a script | `pass` | `2026-10-17` | `RSyntaxTextArea` colours dark: keywords, strings, numbers, comments |
| Project Library nav tree | **Right** of that editor (**corrected**: the function outline, `FunctionPanel`) | `pass` | `2026-10-17` | Source of a `JTree` popup in §L. Icons recoloured but dim (luminance about 110) |
| Script editor gutter, autocomplete popup | Type inside any script editor | `pass` | `2026-08-31` | `system.` + Ctrl+Space in the Script Console. The list and the attribute pane are dark; the enclosing `AutoCompletePopupWindow` window is still `#EEEEEE` explicit, which does not show at this size |
| Script Console | Tools → Script Console | `pass` | `2026-09-25` | 8.3.6, #129 branch, measured from window pixels. Written while light, then on with the console open: banner `#6FB3E8` (4.69:1; was `#0000FF`, 1.23:1), traceback `#FF7B72` (4.21:1), prompts/input/print `#C8CDD1` (6.62:1). Off: banner `#0000FF`, both tracebacks `#FF0000` (including the one written while dark), prompts/input/print `#2E2E2E`. A second on cycle repeats the dark values. Before the fix, text written before the switch kept its stock colour, and after switching off every regular line was `#DDDDDD` on white (1.36:1). The 2026-08-31 `pass` missed both. **Re-measured 2026-09-25 on the #139 branch** (8.3.6, window pixels, Designer opened dark): the interpreter pane paints `#46494B` (FlatDarkLaf `TextPane.background`), not `#3C3F41`, so the ratios above were against the wrong background; against `#46494B` they were 4.01:1 (banner), 3.60:1 (traceback), 5.66:1 (regular). Now banner `#85BFEC` and traceback `#FF9F99`, 4.61:1 each. Output Console `System.err` line `#FF9F99` on `#3C3F41` (`inactiveBackground`), 5.39:1. Then off: banner `#0000FF`, both tracebacks and the Output Console line `#FF0000`, no dark colour left (log: 4 styles restored, 5 runs given back, 27 Output Console runs). On again: `#FF9F99`/`#85BFEC` back. The pane's background depends on how dark arrived: `#46494B` in a Designer that STARTS dark, `#3C3F41` after switching on at runtime (which is what #129 measured). The new colours pass on both (4.61:1 and 5.39:1) |
| Gateway Events editor | Project Browser → Scripting → Gateway Events → an event type (**corrected**: on 8.3 each type is a node: Message, Scheduled, Tag Change, Timer, Shutdown, Startup, Update) | `pass` | `2026-10-17` | Startup editor checked |
| Message handler editor **(1.3.0)** | Gateway Events → Message → right-click → New Message Script (**corrected**: a workspace editor, not a dialog) | `pass` | `2026-10-17` | Threading, the Security zone/role table, Description, Parameters and Script, which are 1.3.0's `MessageHandlerEditor$MessageHandlerConfigPanel` and `SecurityPanel` |
| Client/Session Events editor | Vision → Client Events → an event (Keystroke, Message, Tag Change, Timer, Menubar, Shutdown, Shutdown-intercept, Startup); Perspective → Session Events → an event | `pass` | `2026-10-17` | Menubar, Client Startup and Session Startup checked |

## E. Perspective

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| View editor canvas | Open any view | `pass` | `2026-10-17` | The canvas is the view (content). Rulers dark; the surround stays #D8D8D8, the open question in [What is still unchecked](#what-is-still-unchecked-and-why). On Linux as root, Perspective needs `-Dignition.jxBrowser.sandboxMode=DISABLE` |
| Component palette | Right dock (View → Panels → Perspective Components) | `pass` | `2026-10-17` | Guard hierarchy walks against `FilterablePalette` (see [Out of scope](#out-of-scope)) |
| Property editor tree | Right dock, view open | `pass` | `2026-10-17` | Checked properly this time, with a view open: PROPS/CUSTOM/PARAMS, value colouring, Add Property links. 2026-10-17 with a bound Label selected |
| Binding and add-member icons in that tree **(1.3.0)** | Hover and click the binding icon and the `+` on an object property | `pass` | `2026-10-17` | On a row hover both show, legible; hovering the icons themselves changes nothing wrong; a click opens the binding editor |
| Property key editor field | Click a property name; CUSTOM → Add Custom Property… → Value | `pass` | `2026-10-17` | `PerspectiveKeyEditor` #DDE0E3 text; in edit mode #46494B with light text and a dark selection. The light restore puts black back (toggle-off check) |
| Property NAMES (Session Props, PROPS) | Right dock, Perspective selected or a view open | `fixed` | `2026-09-15` | Black on dark, ~1.9:1, raised on the forum against the 0.1.0 announcement screenshot and still black in the 0.2.0 README screenshot — the 2026-09-01 `pass` above missed it. Headless proof in `PropertyKeyFieldTest`; confirmed by eye on 8.3.6, light under dark and black again after the switch back |
| Binding editor dialog | Click a property's binding icon | `pass` | `2026-10-17` | `BindingEditorFrame`, all seven types: Tag, Property, Expression, Expression Structure, Query, Tag History, HTTP; Add Transform |
| Component scope / node picker in a binding | Binding editor → Property → the browse button (**confirmed**) | `pass` | `2026-10-17` | A JIDE `ResizableWindow` holding a `JTree` (`BrowsePropertiesButton`); its own #DFDFDF background is covered by the content |
| Style editor | Project Browser → Perspective → Styles → a class ('Edit Style' dialog) | `partial` | `2026-10-17` | Text, Background, Border, Shape and Misc are dark. **Margin and Padding** is not: the box-model diagram is pale blue and its 'Margin' and 'Padding' captions are near-white on it ([#195](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/195)) |
| Page Configuration | Perspective → Page Configuration | `pass` | `2026-08-31` | |

## F. Vision

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Window editor chrome | Open any Vision window | `pass` | `2026-10-17` | Rulers and surround dark; the canvas shows FlatLaf's colours, the §N limitation |
| Component palette | Left/right dock with a window open | `pass` | `2026-10-17` | With Main Window open |
| Property Inspector | Vision Property Editor dock | `pass` | `2026-10-17` | With a Button and a Table selected |
| Border chooser (9 sub-panels) | Property Editor → Border → pencil | `light` | `2026-10-17` | [#194](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/194): the dialog paints **blank** under dark. The Field option's sample `FieldBorder` holds a Synthetica border captured at class init, which throws under FlatLaf and aborts the paint. With that one border swapped in memory, Simple (Etched, Bevel, Field, Line, Matte; Button hidden) and Titled (Plain, Line, Titled Panel) are all dark; the Titled Panel sample is a preview (`skip`) |
| Layout dialog | Component → Layout… or right-click a component → Layout… (**confirmed**) | `pass` | `2026-10-17` | `LayoutDialog` ('Layout Constraints') |
| Size and Position dialog | Component → Size & Position… or right-click a component (**confirmed**) | `pass` | `2026-10-17` | `PositionDialog` |
| Dataset editor dialog | A dataset property → edit | `pass` | `2026-08-29` | |
| Custom property editor | Component → Customizers → Custom Properties | `pass` | `2026-10-17` | `DynamicPropertyProviderCustomizer`, `CustomPropertyEditPanel`; a new row checked |
| Template custom properties **(1.3.0)** | Open a template → Component → Customizers → Custom Properties (**confirmed**) | `pass` | `2026-10-17` | Template Parameters and Internal Properties (`PublicPrivateCustomPropertyCustomizer`, `$1`, `$2`) |
| Binding editor dialog **(1.3.0)** | Property Editor → a property's binding icon (**confirmed**) | `pass` | `2026-10-17` | `DynamicOptsDialog`. A String property: Tag, Indirect Tag, Expression, Property, Named Query, DB Browse, SQL Query; a Dataset adds Tag History, **Cell Update** and Functions. Minor: the Property binding's tree draws its `(String)` type hints in HTML `color: green` (#008000), about 2.1:1 on #3C3F41 |
| Security panel **(1.3.0)** | Component → Security, or right-click → Security (**corrected**: opens the docked Security Settings panel, beside Properties) | `pass` | `2026-10-17` | `ComponentSecurityPanel` and its `TristateCheckBox`es legible |
| Easy Chart customizer, last tab **(1.3.0)** | Easy Chart → Component → Customizers → Easy Chart Customizer → Dynamic Groups (**confirmed**) | `pass` | `2026-10-17` | Pens, Axes, Subplots and Dynamic Groups all dark |
| Tab Strip customizer **(1.3.0)** | Tab Strip → Component → Customizers → Tab Strip Customizer (**confirmed**) | `pass` | `2026-10-17` | The preview strip and the colour swatches are the component's data |
| Style Customizer | A component → Component → Customizers → Style Customizer | `light` | `2026-10-17` | [#195](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/195): the Styles table's 'Value \| Preview' header (`TableHeaderLabel`) is a gradient from `Color.WHITE` with near-white text. The lists above it are dark |

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

### SFC charts are not block workspaces

The checklist used to assume `BlockWorkspaceTheme` covered SFC charts as
well, since SFC and alarm pipelines once shared `BasicBlockUI`. The
2026-10-17 run, the first to open an SFC chart, found that **on 8.3.6 they
do not**. The chart editor draws the chart with the SFC client's own panel,
`com.inductiveautomation.sfc.client.SfcDisplayPanel`, the one a client
monitors a chart with. Its steps and links are
`sfc.client.ui.StepComponent`, `TransitionComponent` and `LinkComponent`,
filled with `Color.WHITE` and drawn in `Color.BLACK`: JDK constants nothing
may restyle. The panel's own background is `Base000`, so the white swap
darkened it, and the black links all but vanished on #3A3D3F.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| SFC chart canvas, steps and links | Project Browser → Sequential Function Charts → a chart | `fixed` | `2026-10-17` | Fixed on #41's PR: the white swap now skips `SfcDisplayPanel`, so the chart stays white, as in a client, like a Vision window's canvas (`SfcChartWhiteSwapTest`). Confirmed live: Begin, step, transition and End blocks with readable links |
| SFC chart editor chrome | The palette strip, rulers, Chart Design / Chart Control / Recordings docks | `pass` | `2026-10-17` | |

## H. Reporting

Reporting module required; mark the whole section `n/a` if it is not installed.

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Report Data tab | Open a report → Data | `pass` | `2026-10-17` |  |
| Report Design tab | Open a report → Design | `pass` | `2026-10-17` | The page is the report (content) |
| Schedule tab | Open a report → Schedule | `pass` | `2026-10-17` |  |
| Scheduled parameters panel | Schedule tab → add a schedule → Parameters | `pass` | `2026-10-17` | Expression editors dark |
| Property inspector | Design tab, left dock | `pass` | `2026-10-17` | Configure and Properties tabs |
| Report Preview tab | Open a report → Preview | `pass` | `2026-10-17` | The page is output |
| Pie chart configuration | Design tab → drop a pie chart | `pass` | `2026-10-17` | Segment swatches are values |

## I. Search, replace, translation

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Find/Replace panel | Ctrl-F / Cmd-F | `pass` | `2026-10-17` | Edit → Find/Replace opens `SearchReplaceDialog` |
| Find/Replace provider dropdown | Inside Find/Replace | `pass` | `2026-10-17` | Dropdown *popup*, not just the closed control. The 'Tag providers' popup is a `Popup$HeavyWeightWindow`, dark |
| Find/Replace target checkboxes | Inside Find/Replace | `pass` | `2026-10-17` |  |
| Find/Replace scrollable result list | After a search | `pass` | `2026-10-17` | Result rows are renderer-painted. JIDE `SortableTable`; Current/Preview panes dark |
| Translation Manager | Tools → Translation Manager | `pass` | `2026-10-17` | |

## J. Images and symbols

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Image Management panel | Tools → Image Management | `pass` | `2026-10-17` | location confirmed |
| Symbol Factory browser | Tools → Symbol Factory | `pass` | `2026-10-17` | Location corrected: it is in the Tools menu. Category list, search field and preview all dark |
| Symbol Factory thumbnail gallery | Inside that browser | `skip` | `2026-10-17` | The white cells are the symbol ARTWORK, drawn on white. Recolouring it would misrepresent what an operator sees, the same rule as the Vision canvas |
| SVG canvas (`JSVGCanvas`) | Symbol Factory preview | `pass` | `2026-10-17` | Draws the artwork over the dark panel, with no light box |

## K. Database and queries

| Surface | Where | Result | Last checked | Notes |
|---|---|---|---|---|
| Query Browser | Tools → Database Query Browser | `fixed` | `2026-10-17` | SQL editor [#48](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/48) and the action buttons [#51](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/51) |
| Query Browser result table | After running a query | `pass` | `2026-10-17` | `SELECT * FROM motors` on a seeded SQLite connection: the stock #EFEFEF/#FFFFFF stripes are darkened at paint time |
| Named Query editor | Project Browser → Named Queries | `pass` | `2026-10-17` | Settings, Authoring and Testing tabs |
| Named Query parameter table | Authoring tab; Testing → Test Parameters | `pass` | `2026-10-17` | |
| Named Query security table | Settings tab | `pass` | `2026-10-17` | Security Zone / Role |
| Query Builder | Named Query → Authoring → Open Query Builder | `pass` | `2026-10-17` | Not on the Exchange list |

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
| `TagFrameTree` | Tag Browser tree | `pass` | `2026-10-17` | `TagPopupMenu`, 22 items — arrived **stale**, repaired before paint (2026-10-17, by a real right-click, the log's `Refreshed stale UI delegates under ...TagPopupMenu`). Same on 2026-08-29 and 2026-09-01 |
| `NavTreePanel$1` | Project Browser tree | `pass` | `2026-10-17` | Arrived **stale**, repaired before paint. `NodeContextMenu` (8 openings) on 2026-08-29. 2026-10-17: Named Queries, Reports, Project Library, a script, Gateway Events → Message and Sequential Function Charts, all dark |
| `Graphics2dRenderWidget` | Perspective view editor canvas | `pass` | `2026-10-17` | 12 items, disabled ones correctly greyed. `Popup$HeavyWeightWindow`. Also `pass` 2026-08-29. 2026-10-17 on a Button: 15 items |
| `InteractionLayer` | Vision window editor canvas | `pass` | `2026-10-17` | `factorypmi.designer.workspace.ComponentPopup` with a `CustomizerMenu` submenu, 17 items |
| `BorderlessField` / `PerspectiveKeyEditor` | A text field's cut/copy/paste menu | `pass` | `2026-10-17` | `JPopupMenu`, 3 items on 2026-08-29. On 2026-10-17 a right-click inside an editing `BorderlessField` in the Perspective property editor opened the row's `NodeContextMenu` (13 items, dark) instead |
| `JTree` | Project Library nav tree (Scripting) | `pass` | `2026-10-17` | `NodeContextMenu`, 14 items on 2026-08-29. The outline beside a script (`FunctionPanel`): Copy Path / Expand All / Collapse All, dark |
| `NodeEditor$MainEditor`, `…$1` | Perspective property editor, a property row | `pass` | `2026-10-17` | `jsonedit.NodeEditor$MainEditor$NodeContextMenu`, 13 items (Actions / Structure / Binding / Options) |
| `ComponentScopeEditor$…$2` | Binding editor → component/property picker | `pass` | `2026-08-29` | `JPopupMenu` |
| — | UDT instance menu (Tag Browser) | `pass` | `2026-10-17` | `TagActions$udtInstanceMenu$1` on 2026-08-29; on 8.3.6 (2026-10-17) the same `TagPopupMenu` as any tag |
| — | Combo dropdowns | `pass` | `2026-10-17` | `FlatComboPopup` (Project Properties, Tag Browser provider) |
| — | Tag Browser hamburger menu (left-click) | `pass` | `2026-10-17` | `Popup$HeavyWeightWindow` -> `JPopupMenu`, seen by the watcher, dark, nothing stale. **Left-click trigger, not right-click** |
| — | An IA error popup | `pass` | `2026-10-17` | `DefaultPopupWindowParent` → `ErrorPanel` — **not a `JPopupMenu`**, see below. 2026-10-17: 'Can't get roles from the gateway', probe clean |
| — | Find/Replace provider dropdown (left-click) | `pass` | `2026-10-17` | 'Tag providers' checklist in a `Popup$HeavyWeightWindow` |

### Right-click cannot be automated from outside — but it can from inside

*On Linux under Xvfb it can be automated from outside too.* The 2026-10-17
run sent real right-clicks with `xdotool` and every source above opened its
menu; the limit recorded below was macOS desktop control.


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
| Dark Mode menu item, unchecked | Tools → Dark Mode, light theme | `pass` | `2026-08-31` | Moon icon and label legible. Checked on the old checkbox; now a submenu |
| Dark Mode menu item, checked | Tools → Dark Mode, dark theme | `pass` | `2026-08-31` | Checkmark visible against the dark popup. Checked on the old checkbox; now a submenu |
| Dark Mode submenu, both themes | Tools → Dark Mode | `pass` (Linux) | `2026-10-27` | Moon icon on the submenu; Light, Dark and Match System as radio items, the saved one selected and its dot legible in both themes. A new install shows Light selected |
| Match System, system dark | Set the OS to dark, choose Match System, relaunch | `pass` (Linux, stand-in `gsettings`) | `2026-10-27` | Dark after the choice and again after the relaunch, Match System still selected. The debug log has `Match System: the system reads as DARK (…)`. Once per OS: Windows (app mode), macOS (Appearance; also Auto), Linux |
| Match System, system light | Set the OS to light with Match System chosen, relaunch | `pass` (Linux, stand-in `gsettings`) | `2026-10-27` | Comes up stock light, Match System still selected. Changing the OS setting with the Designer open changes nothing until a relaunch or choosing Match System again |
| Light or Dark after Match System | Choose Dark, then Light, with Match System selected before | `pass` (Linux) | `2026-10-27` | Each switches as before, and Match System is no longer selected after a relaunch. 2026-10-27: checked by the submenu's selection and the saved keys (`followSystem=false`), not by a relaunch |
| Tools items after a project switch | File → Open another project, then open Tools | `pass` | `2026-09-24` | Exactly one Dark Mode and one About Designer Dark Mode…, however many projects the session has opened. Before [#131](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/131) each open added another pair. `ModuleMenuReinstallTest` pins it in the harness. The dev gateway has an empty `test2` project to switch to |
| About dialog, light | Tools → About Designer Dark Mode…, light theme | `pass` | `2026-09-24` | Shows a real version, not `development build` or `${version}`; links legible. Dev build read `Version 0.0.1-SNAPSHOT`, as expected. Links measured from screenshot pixels: #1F5FBF on #FAFAFB, **5.84:1** |
| About dialog, dark | Tools → About Designer Dark Mode…, dark theme | `pass` | `2026-09-24` | Dialog dark, text legible, links at least 4.5:1 against the background — `AboutDialogTest` pins that in the windowed harness; by eye, compute it from screenshot pixels rather than judging it. Measured: #77AEF8 on #3C3F41, **4.64:1** |
| About dialog links | Click each link | — | — | Other modules → mustrysolutions.com/ignition-modules, Work with us → /contact-us, Report a problem → this repo's issues |
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
| Drop the palette kinds the headless sweep cannot build — Comments Panel, Comments Panel (legacy), Easy Chart, Slider, Spinner — under dark, save | Checker clean: no FlatLaf class; a Vision client opens the window | `pass` (0.4.1; `fail` on 0.4.0) | `2026-09-18` | 2026-09-17 on the released 0.4.0: all five dropped and saved; the bytes held no dark value and no font, but `com.formdev.flatlaf.ui.FlatScrollPaneBorder` — the checker refused the window and a client cannot open it. Mechanism worked out headlessly once the probe could build a Comments Panel (a scroll pane built borderless, given FlatLaf's border by the tree update, against a clean copy with none; the serializer has an equality rule for Synthetica borders and none for FlatLaf's, and none at all for a null), fixed by `LookAndFeelBorders` and `VisionConstructionBorders`, with the sweep now tree-updating before every dark save. Re-run the same evening on the fixed build: the corrupted window opened in the dark Designer, one Save All from there re-wrote it without the border (the walk aligns the loaded panel back to no border), the checker passed, and the Vision client opened it with every component light. On 0.4.0 itself the remedy is a light save from the same Designer. 2026-09-18: re-run on the released 0.4.1 asset with a fresh Comments Panel; clean, and light in the client |

**Once per Ignition version:** the interface the module keys on is
`com.inductiveautomation.vision.api.client.components.model.TopLevelContainer`,
implemented by `FPMIWindow` and `VisionTemplate`. `ReflectiveSurfaceTest`
cannot pin it (no Vision jar on the harness classpath); the Vision probe can:
`./gradlew :designer:visionProbe -Pvision.jars="$(ops/vision-jars.sh)"` runs
`VisionWindowSaveTest`, whose first test resolves the name against the cached
Vision jars and checks both classes implement it, and whose other tests are
the saves above, headless.

## O. The Exchange dark-mode script in the project

The Exchange "Dark Mode for the Designer" script (1.3.0) lives inside a
project: a project-library script at `designer/darkModePatch` plus a Vision
client tag `designerPatch` that adds a **View → Dark Mode** checkbox two
seconds after launch. The module takes that checkbox out of play (#89). To
set up: seed the script into the dev project (`ignition/script-python/
designer/darkModePatch/{code.py,resource.json}` from the Exchange zip's
project export, `chown 2003:2003`, restart the gateway), and either import
the tag from `Tags/autoAdd Vision Client Tag.xml` through the Tag Browser's
Vision Client Tags provider, or call `designer.darkModePatch.addSelectionBox()`
from the Script Console, which is what the tag does.

| Step | Expect | Result | Last checked | Notes |
|---|---|---|---|---|
| Launch a dark Designer on a project carrying the script | Status bar and log: one notice naming the script and the `designerPatch` tag, two seconds after the dark apply; nothing else changes | `pass` | `2026-09-18` | `ExchangeScript: This project contains…` in the log. The lookup is under the `ignition/script-python` resource type; `ScriptConfig.RESOURCE_TYPE` is event scripts and finds nothing |
| The script adds its checkbox (tag fires, or `addSelectionBox()` from the console) | Within a second the View → Dark Mode item is greyed out with the module's tooltip; the notice, if not yet shown, appears | `pass` | `2026-09-18` | From the console: `getSelectionCheckbox()` reported enabled False, selected False, tooltip set; View menu showed it grey |
| `designer.darkModePatch.setDarkMode(True)` from the console (ticks the box regardless), then Tools → Dark Mode | The script's paint applies (nothing stops a console call); the next Tools → Dark Mode click unticks the box again | `pass` | `2026-09-18` | Reported enabled False, selected True after the call, selected False after the click |
| **Without the fix, for the record** (the reproduction of 2026-09-18 on a dev build of 0.4.1) | — | — | `2026-09-18` | Script present, unticked: nothing wrong. Ticked on top of the module's dark: black filter field, black tree background, grey table cells; survives Tools → Dark Mode off and on. Unticked again under the module's dark: toolbar strip and every dock title light. Module off: stock light, script's whites invisible. Module on again: normal dark (the module's own white-hunting passes cover its light paint). Dialogs opened later come up dark. The script's `TreeListener` threw inside our tree-icon phase, failing it for every tree after the first |

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

*Historical: what that run could and could not show. §L has since been
re-verified from inside the JVM (2026-09-01), and the dev project now has a
saved view.*

Driven with computer use rather than by hand, which changes what the run is
worth in two directions:

- **§L (popups) was not re-verified.** Synthetic right-clicks did not reach the
  Designer at all — no popup opened and none was logged — so every §L result
  in [§L](#l-popup-sweep) still dated from 2026-08-29. A popup sweep needs a
  human hand.
- **Judge colour zoomed, never on a scaled screenshot.** Two surfaces (the
  autocomplete popup, the whole Project Properties dialog) read as *light* on a
  full-screen capture scaled from 3456px and turned out to be perfectly dark
  when zoomed. Both would have been false bug reports.

The Perspective view editor, binding editors and style editor were still
unchecked in substance: the dev project had no views then.

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
| Perspective property editor | `pass` | `2026-10-17` | incl. the filter field ([#45](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45)) — checked over four toggles, and again with the frame re-attached under dark. 2026-10-17: names black again, values coloured as stock |
| Menus and menu items | `pass` | `2026-10-17` | 2026-10-17 (Linux, Swing menu bar) |
| Tag Browser | `pass` | `2026-10-17` | `Tag \| Value` header back to matching light grey ([#45](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/45)). 2026-10-17: `TagRenderer` and `ValueRenderer` on `SynthTableUI` back, white rows; the Tag Browser had been closed and reopened under dark |
| Dock title bars and dividers | `pass` | `2026-10-17` | 2026-10-17 |
| Script editor and console | `pass` | `2026-10-17` | 2026-10-17: a project library script back on the stock editor theme (log: `ScriptEditorTheme: restored the default editor theme`); the Script Console's editor #FFFFFF/#000000 and interpreter pane #FFFFFF/#2E2E2E (`ConsoleTextTheme: restored 4 console style(s)`) |
| Tree and table cell colours | `pass` | `2026-09-24` | Renderers are restored from tracked sets, not the live tree. Measured rather than eyeballed on the 2026-09-24 run: after a cycle every visible tree's and table's renderer matches a plain `updateComponentTreeUI` baseline exactly — see [the renderer probe](#the-renderer-probe) |
| Tables: no dark alternating rows | `pass` | `2026-09-24` | Tag Browser `TagValuesTable`, 3 rows: `SyntheticaDefaultTableCellRenderer` back, every row band `#FFFFFF` |
| Trees: icons and row height back | `pass` | `2026-09-24` | Project Browser and OPC Browser after two cycles: full-colour icons, stock row height |
| **Surfaces opened while dark** | `pass` | `2026-10-17` | See [the standing step](#surfaces-opened-while-dark) below. 2026-10-17: Tag Browser closed and reopened and the OPC Browser opened and expanded, both under dark; after light, `TagRenderer` and `OPCRenderer`, stock row heights, light rows |
| **With a Vision window open** | `fixed` | `2026-10-17` | The throw is now prevented rather than contained — see below. 2026-10-17: Main Window open through the cycle, no failure logged, the canvas stock after light |
| Tag Editor opened after the switch | `pass` | `2026-10-17` | [#156](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/156) holds: a Tag Editor opened in light has a fully stock property table |
| Window and root-pane backgrounds | `partial` | `2026-10-17` | [#198](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/198): every window's own background and its `JRootPane`'s stay #3C3F41 after light, windows created after the restore too; a relaunched stock Designer has #FAFAFB. Covered by the content pane, so nothing showed on screen |

Then relaunch the Designer and confirm it comes up stock. *(Done 2026-09-24:
after a toggle-off and a quit, the relaunch logs `Stock look and feel` and no
`switching to dark mode`, and the renderer inventory matches the pre-cycle
baseline. Again 2026-10-17 on Linux: `Stock look and feel` logged, no switch,
and the dark-leftover probe found nothing on the main frame.)*

### Surfaces opened while dark

**Do this on every run.** It is two minutes, and it is the state that both
review findings on [#42](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/pull/120)
needed: a component wrapped *after* the switch, whose renderer the restore has
to hand back. Nothing else in this checklist asks for it — every other row
opens its surface light.

1. With dark mode **on**, open a table and a tree that were not on screen when
   you toggled. Cheapest pair that needs no database:
   - **View → Panels → Tag Browser** to close it, then the same item again to
     reopen it — a populated tree *and* table, rebuilt under dark;
   - **View → Panels → OPC Browser** — a tree with folder icons, expanded one
     level.
   The debug log confirms the wrap took: `TreeIconRecolorer: wrapped N tree
   renderer(s)` with a rising total.
2. Toggle dark **off**.
3. Both must come back on the light palette, with their icons and row heights.

> **A modal dialog can never be part of this.** Diagnostics and Image
> Management blank the Designer's screen menu bar while they are up, so
> **Tools → Dark Mode is unreachable** and the surface cannot be on screen
> across a toggle. Use dock panels. (Likewise, with a *floating* panel focused
> the Tools items are disabled — click the main window first.)

### The renderer probe

Neither `TreeIconRecolorer` nor `CellRendererSanitizer` names the components it
touches — the log only counts them — and the inspector reports colours and
borders, not cell renderers. So "the tree came back right" is otherwise an
eyeball judgement. [`ops/qa-renderer-probe.py`](../ops/qa-renderer-probe.py)
closes that gap from the **Script Console**, which runs in the Designer's own
JVM: it writes every visible `JTree`'s cell renderer, every `JTable`'s default
renderer, their row heights and `BasicTreeUI.createdRenderer` to a file.

```python
OUTPATH='/tmp/ddm-qa/stock.txt'; execfile('/path/to/ops/qa-renderer-probe.py')
```

Take one before the cycle and one after, and `diff` them. Two traps learned on
2026-09-24:

- **Baseline against a plain tree update, not against the untouched Designer.**
  A bare `updateComponentTreeUI` is not a no-op even Synthetica→Synthetica: it
  replaces a `UIResource` renderer an application installed. On this project
  that costs the SFC timeline header its `EventTimelinePanel$TimelineHeader$2`,
  which looks exactly like a restore bug until you reproduce it with the module
  uninvolved. Set `CONTROL=True` before the `execfile` and the probe does that
  refresh first, on the EDT.
- **`screencapture -l <windowid>`, not a full-screen grab**, for colour
  evidence: a full-screen capture picks up whatever overlaps the Designer, and
  the desktop-control screenshot can serve a stale frame for this app.

Worth reading the debug log after the restore as well as looking at the screen:
`Light restore: re-ran updateUI on N component(s)` says whether the child-first
refresh pass found anything. On 2026-08-31 it reported **nothing on every
toggle**, including with the property editor re-attached under dark — so on this
path the filter is restored by the tracked white-swap, and that pass is
belt-and-braces rather than the thing doing the work.

> **Fixed; found by the 2026-08-29 run.** With a Vision window open, the
> light restore's tree update (step 9 in
> [ARCHITECTURE](ARCHITECTURE.md#the-switch-step-by-step)) used to throw
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
> **Prevented, in two layers.** The throw itself no longer happens: before each
> `updateUI()` the walk swaps the content pane's background for the same colour
> as a plain `Color`, so IA's `instanceof UIResource` block skips itself, and
> puts a `UIResource` back afterwards
> (`ThemeManager.neutraliseInternalFrameBackground`). Merely catching the throw
> was tried first and was worse: the frame never got its layout, and every later
> `getMinimumSize()` threw instead. Independently, the tree update is now per
> component, so anything else that throws out of `updateUI()` costs only that
> component and not the rest of the main frame's tree.

## What is still unchecked, and why

Kept explicit so nobody has to reconstruct it from the tables. Every one of
these is a real gap, not a pass.

| Surface | Why it is unchecked | What it needs |
|---|---|---|
| ~~§L right-click popups~~ | **Done 2026-10-17** by real right-clicks on Linux (`xdotool` under Xvfb reaches the Designer, unlike macOS desktop control): every source in §L opened dark except the component scope picker's menu, which was not opened and keeps its 2026-08-29 result | — |
| ~~§E binding editor, component scope picker, style editor~~ | **Done 2026-10-17** on a view with components, seeded straight into the gateway's project folder. The style editor's Margin and Padding diagram is [#195](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/195) | — |
| ~~§H Reporting, Preview and Schedule tabs~~ | **Done 2026-10-17**, with a pie chart and a schedule | — |
| ~~§F border chooser, Layout, Size and Position, the (1.3.0) rows~~ | **Done 2026-10-17** on a window with a Button, Text Field, Tab Strip, Table and Easy Chart, plus a template. The border chooser is [#194](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/194) | — |
| ~~§D message handler~~ | **Done 2026-10-17**. On 8.3 it is a workspace editor, not a dialog | — |
| ~~Relaunch-comes-up-stock~~ | **Run 2026-09-01 and passed.** Toggled off, relaunched, confirmed the Designer comes up genuinely stock, toggled back. Recorded rather than deleted because the run doubles as the baseline half of the comparison in [Compare against a relaunched Designer](#compare-against-a-relaunched-designer-before-calling-something-a-bug) | — |
| §E view editor surround | Not a gap in testing — an undecided question. On 8.3.6 the rulers are dark; the surround stays #D8D8D8 | A decision |
| Rows that kept an older date on 2026-10-17 | Not re-opened in that run: Open/Create Project, the cut/copy/paste shortcuts, the dataset editor, tag data-type sub-editors, the override icons on inherited alarms and scripts, the Script Console's contrast measurement, Perspective property NAMES, Page Configuration, the script autocomplete popup, the Output Console's two halves, §G's alarm pipeline rows (§G was outside #41's scope; only its SFC rows are new), and §L's component scope picker menu | Their own sittings already cover them; re-check before a release |
| Security-level tree (§B) | Added after the 2026-10-17 run; #41's `CheckBoxTree` icon fix reaches it, untested live | Open it under dark, confirm the path and that the icons are light and the checkboxes still toggle |
| **Everything, on Windows; §A–K on macOS since 2026-08-31** | The §A–F and §H–K pass of 2026-10-17 is Linux under Xvfb with no window manager, so the native title bar and frame were not seen. Windows has never had a sitting. The macOS rows of §A–K date from 2026-08-31/09-01. The headless harness runs on all three platforms in CI, which proves the switch sequence and the reflective reach, not what anything looks like. The HiDPI question in [#76](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/76) is answered as far as a headless harness can | A Windows sitting walking this checklist, with the `env:` block from the log kept alongside the run ([#96](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/96)) |

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
