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
   again. Do not move, resize or scroll anything in between.

4. Downscale to 1600px wide and replace the files:

   ```bash
   sips -Z 1600 designer-light.png designer-dark.png
   ```

   Retina captures come out around 3500px and 1 MB each; 1600px is plenty for
   GitHub (which renders README images at roughly half that) and keeps the
   clone small.

## Known caveat in the current pair

The dark capture shows [#1](https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/1):
the toolbar icons keep their dark "enabled" variant, which against a dark
toolbar reads as disabled. Compare the top toolbar between the two files — it
is the most visible difference after the background. When #1 is fixed, retake
both.
