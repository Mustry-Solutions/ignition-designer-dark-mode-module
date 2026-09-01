# Screenshots

These are the README's images. They will go stale as the theming changes, so
this records how to reproduce them — a new pair should be the *same frame* as
the old one, otherwise a before/after comparison is meaningless.

| File | Shows |
|---|---|
| `designer-light.png` | The Designer with dark mode **off** (stock Synthetica) |
| `designer-dark.png` | The same frame with dark mode **on** |

Filenames are stable on purpose: replacing them in place keeps every existing
reference — README, issues, release notes — pointing at the current image. Do
not add dates or version numbers.

The current pair was captured on 2026-08-31 against 0.2.0. Two things to check
in a replacement, both of which have gone wrong before:

- **No trial badge in the status bar.** A later capture of the same frame was
  rejected for showing a red "Trial Expired" — the Reporting module's trial,
  started by QA work on the dev gateway, and nothing to do with this module. In
  a README it reads as though this module is trial-limited.
- **Tags in the Tag Browser.** An empty one wastes the panel that shows off the
  tree theming.

## Reproducing them

1. Bring up the dev gateway and launch a Designer against it:

   ```bash
   ops/setup.sh
   ```

   Then open the `designer-dark-mode-dev` entry in the Designer Launcher.

2. Set the frame up:
   - Open a project (the shots use one called `test`) so the Perspective
     workspace is showing rather than the welcome screen.
   - Have **Project Browser**, **Tag Browser** and the **Perspective Property
     Editor** all visible. The property editor is the visually densest surface
     in the Designer and does most of the work of showing the theme off.
   - Leave the window at a normal desktop size. Don't full-screen it — the
     macOS title bar is part of what the theme changes.

3. Capture with dark mode **off**, then **Tools → Dark Mode**, then capture
   again. Do not move, resize or scroll anything in between. Grab the window
   itself (`⌘⇧4`, then Space) rather than a screen region: that keeps the macOS
   drop shadow on a transparent background, which is how the current pair is
   framed and which GitHub renders correctly on a light or dark page.

4. Downscale to 1600px wide and replace the files:

   ```bash
   sips -Z 1600 designer-light.png designer-dark.png
   ```

   Retina captures come out around 3500px and 1 MB each; 1600px is plenty for
   GitHub (which renders README images at roughly half that) and keeps the
   clone small.

## Why the light shot comes from a fresh launch

Capture the light image from a Designer that has **never been toggled**, not by
switching dark mode off: it is what a user actually sees before installing the
module, which is the honest "before" half of a before/after pair.

Set the preference to light and relaunch, then follow step 3 — capture, toggle
dark, capture again in the same session, so both shots are the same frame.
