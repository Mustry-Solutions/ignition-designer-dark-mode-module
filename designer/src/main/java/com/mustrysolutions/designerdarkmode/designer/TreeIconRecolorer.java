package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.RGBImageFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import com.inductiveautomation.ignition.client.icons.SvgIconUtil;
import com.inductiveautomation.ignition.client.util.gui.tree.PanelBasedTreeCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts the Designer's trees (Project Browser, Tag Browser, ...) to dark mode
 * by wrapping each tree's cell renderer:
 *
 * 1) SVG icons are replaced with a cached, tinted COPY: the icon is painted to
 *    an offscreen image and its silhouette filled with the theme foreground
 *    (the SVG machinery bakes colors in at construction, so the live icon
 *    cannot be recolored — and copies leave the originals pristine for light
 *    mode).
 * 2) Bitmap icons (ImageIcon) get a cached "smart invert" copy: dark
 *    low-saturation pixels are brightened, saturated brand colors are kept.
 * 3) Ignition's renderers cache their colors in fields populated under the
 *    light theme at construction; both flavors (PanelBasedTreeCellRenderer and
 *    DefaultTreeCellRenderer) are re-synced from the current UIManager values
 *    on every render.
 *
 * uninstall() must run AFTER the stock look and feel is restored so the final
 * color re-sync picks up the light palette.
 */
public class TreeIconRecolorer {

    private final Logger log = LoggerFactory.getLogger(TreeIconRecolorer.class);

    // Weak keys: the watcher keeps re-running install() for the whole session,
    // and trees from closed views must not be pinned in memory.
    private final Map<JTree, TreeCellRenderer> wrappedTrees = new WeakHashMap<>();
    private final Map<Icon, Icon> darkVariants = new IdentityHashMap<>();
    private final Set<Icon> variantIcons =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TreeCellRenderer> touchedRenderers =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Component> whitenedRendererComponents =
        Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<String> loggedIconClasses = new HashSet<>();

    private final JLabel paintDummy = new JLabel();
    private Color iconColor;
    private Color rendererBackground;

    /** Wrap the renderers of every tree currently in the UI. Safe to re-run. */
    public void install() {
        readColors();
        wrapTrees(findAllTrees());
    }

    /** The two colours the wrapper paints with, from the current theme. */
    private void readColors() {
        iconColor = UIManager.getColor("Tree.foreground");
        if (iconColor == null) {
            iconColor = new Color(0xB8BFC6);
        }
        rendererBackground = UIManager.getColor("Tree.background");
        if (rendererBackground == null) {
            rendererBackground = new Color(0x3A3D3F);
        }
    }

    /** Package-private so tests can drive the wrap without a real Window. */
    void installIn(Container container) {
        readColors();
        List<JTree> trees = new ArrayList<>();
        collectTrees(container, trees);
        wrapTrees(trees);
    }

    private void wrapTrees(List<JTree> trees) {
        int wrapped = 0;
        for (JTree tree : trees) {
            TreeCellRenderer current = tree.getCellRenderer();
            if (current == null || current instanceof RecoloringRenderer) {
                continue;
            }
            // JIDE's CheckBoxTree hands out a CheckBoxTreeCellRenderer that
            // calls back into the tree's configured renderer — wrapping it
            // recurses infinitely (StackOverflow). Leave those trees to the
            // renderer-pane sanitizer.
            if (current.getClass().getName().contains("CheckBoxTreeCellRenderer")
                    || tree.getClass().getName().contains("CheckBoxTree")) {
                continue;
            }
            // A tree that publishes its renderer through a TYPED accessor casts
            // it, and our wrapper is not that type. TagBrowserTree does exactly
            // this — getTagRenderer() is (TagRenderer) getCellRenderer() — and
            // it is called from the tree's own paint, so wrapping turned every
            // repaint into a ClassCastException on the EDT.
            if (castsItsRenderer(tree)) {
                continue;
            }
            wrappedTrees.put(tree, current);
            tree.setCellRenderer(new RecoloringRenderer(current));
            tree.repaint();
            wrapped++;
        }
        // The component watcher re-runs install() on every UI change; only log
        // passes that actually wrapped something.
        if (wrapped > 0) {
            DebugLog.detail("TreeIconRecolorer: wrapped " + wrapped + " tree renderer(s), "
                + wrappedTrees.size() + " total under management.");
        }
    }

    /** Undo every wrapped renderer and cached renderer color. */
    public void uninstall() {
        wrappedTrees.forEach((tree, original) -> {
            if (tree.getCellRenderer() instanceof RecoloringRenderer) {
                tree.setCellRenderer(original);
                tree.repaint();
            }
        });
        wrappedTrees.clear();
        darkVariants.clear();
        variantIcons.clear();
        // UIManager now holds the light theme's values again; push them back
        // into the renderers' cached color fields.
        for (TreeCellRenderer renderer : touchedRenderers) {
            syncRendererColors(renderer);
        }
        touchedRenderers.clear();
        for (Component component : whitenedRendererComponents) {
            component.setBackground(Color.WHITE);
        }
        whitenedRendererComponents.clear();
        buttonIconOriginals.forEach((component, icon) -> {
            if (component instanceof javax.swing.AbstractButton) {
                ((javax.swing.AbstractButton) component).setIcon(icon);
            } else if (component instanceof javax.swing.JLabel) {
                ((javax.swing.JLabel) component).setIcon(icon);
            }
        });
        buttonIconOriginals.clear();
        buttonIconPairs.forEach((button, stock) -> {
            button.setIcon(stock[0]);
            button.setDisabledIcon(stock[1]);
            button.setDisabledSelectedIcon(stock[2]);
        });
        buttonIconPairs.clear();
        iconBrightness.clear();
        DebugLog.detail("TreeIconRecolorer: uninstalled.");
    }

    private List<JTree> findAllTrees() {
        List<JTree> trees = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            collectTrees(window, trees);
        }
        return trees;
    }

    private void collectTrees(Container container, List<JTree> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTree) {
                out.add((JTree) child);
            }
            if (child instanceof Container) {
                collectTrees((Container) child, out);
            }
        }
    }

    /** How many trees live under this container — used as a UI-readiness probe. */
    static int countTrees(Container container) {
        int count = 0;
        for (Component child : container.getComponents()) {
            if (child instanceof JTree) {
                count++;
            }
            if (child instanceof Container) {
                count += countTrees((Container) child);
            }
        }
        return count;
    }

    /** Push the current UIManager tree palette into the renderer's cached fields. */
    private void syncRendererColors(TreeCellRenderer renderer) {
        Color background = UIManager.getColor("Tree.background");
        Color foreground = UIManager.getColor("Tree.foreground");
        Color selectionBackground = UIManager.getColor("Tree.selectionBackground");
        Color selectionForeground = UIManager.getColor("Tree.selectionForeground");
        Color selectionBorder = UIManager.getColor("Tree.selectionBorderColor");
        if (renderer instanceof PanelBasedTreeCellRenderer) {
            PanelBasedTreeCellRenderer panel = (PanelBasedTreeCellRenderer) renderer;
            panel.setBackgroundNonSelectionColor(background);
            panel.setTextNonSelectionColor(foreground);
            panel.setBackgroundSelectionColor(selectionBackground);
            panel.setTextSelectionColor(selectionForeground);
            panel.setBorderSelectionColor(selectionBorder);
        } else if (renderer instanceof DefaultTreeCellRenderer) {
            DefaultTreeCellRenderer label = (DefaultTreeCellRenderer) renderer;
            label.setBackgroundNonSelectionColor(background);
            label.setTextNonSelectionColor(foreground);
            label.setBackgroundSelectionColor(selectionBackground);
            label.setTextSelectionColor(selectionForeground);
            label.setBorderSelectionColor(selectionBorder);
        }
    }

    /** Swap icons for dark variants on the label itself and any nested labels. */
    private void processComponent(Component component) {
        // Some renderers (Perspective's palette items) re-set the Base000
        // token — the Color.WHITE instance — as their background on every
        // render; identity check so a user's own white is left alone. Track
        // them: renderers that set colors only at construction would stay
        // dark into light mode otherwise.
        if (component instanceof javax.swing.JComponent
                && component.getBackground() == Color.WHITE) {
            whitenedRendererComponents.add(component);
            component.setBackground(rendererBackground);
        }
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            Icon icon = label.getIcon();
            if (icon != null && !variantIcons.contains(icon)) {
                logIconClassOnce(icon);
                Icon variant = darkVariant(icon);
                if (variant != null) {
                    label.setIcon(variant);
                }
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                processComponent(child);
            }
        }
    }

    private void logIconClassOnce(Icon icon) {
        if (loggedIconClasses.add(icon.getClass().getName())) {
            DebugLog.detail("TreeIconRecolorer: encountered icon class "
                + icon.getClass().getName());
        }
    }

    /**
     * Cached dark-mode replacement for an icon, or null to leave it alone.
     * Every icon type gets the same silhouette tint — the trees mix at least
     * six icon classes (SVG, vector-path, and bitmap flavors), and a uniform
     * monochrome treatment is both consistent and the standard dark-IDE look.
     */
    /**
     * Average brightness of an icon's <em>visible</em> pixels, 0-255.
     *
     * <p>Transparent pixels are excluded rather than counted as black: a glyph
     * is mostly transparent, so averaging every pixel would rank icons by how
     * much ink they contain instead of how light that ink is, and two icons of
     * the same colour but different coverage would compare unequal.
     *
     * <p>Cached by icon identity — rasterising is not free and the theming
     * passes re-run on every debounced rescan.
     */
    private double brightness(Icon icon) {
        Double cached = iconBrightness.get(icon);
        if (cached != null) {
            return cached;
        }
        double result = 0;
        try {
            int width = Math.max(icon.getIconWidth(), 1);
            int height = Math.max(icon.getIconHeight(), 1);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            try {
                icon.paintIcon(paintDummy, g2, 0, 0);
            } finally {
                g2.dispose();
            }
            double sum = 0;
            double weight = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha == 0) {
                        continue;
                    }
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    sum += (r * 299 + g * 587 + b * 114) / 1000.0 * alpha;
                    weight += alpha;
                }
            }
            result = weight == 0 ? 0 : sum / weight;
        } catch (Throwable t) {
            DebugLog.log("TreeIconRecolorer: could not measure icon brightness.", t);
        }
        iconBrightness.put(icon, result);
        return result;
    }

    /**
     * Swap a button's enabled and disabled icon variants for dark mode.
     *
     * <p>On Ignition <b>8.1</b> a toolbar button carried two bitmaps — a dark
     * one on {@code icon} meaning enabled, a light one on
     * {@code disabledIcon}/{@code disabledSelectedIcon} meaning disabled —
     * and on a dark background that reading inverts, so the pair has to be
     * swapped. That is what the Exchange dark-mode script does.
     *
     * <p><b>On 8.3 this is nearly a no-op, and deliberately kept anyway.</b>
     * Measured against a running 8.3.8 Designer, 32 of 34 button classes have
     * <em>no</em> disabled icon at all: IA moved to a single {@code VectorIcon}
     * or {@code SvgIcon} per button and lets Swing derive the disabled
     * appearance. There is nothing to swap on those, and the smart invert in
     * {@link #darkVariant} handles them correctly — it preserves relative
     * contrast, so an icon that was low-contrast on white stays low-contrast
     * on dark. The two classes that do carry a pair are handled here; see
     * {@link #logIconPairOnce} for the per-class evidence in the debug log.
     *
     * <p>Assignment is by measured brightness rather than a blind swap,
     * because a component can be processed more than once in the same mode
     * (a rescan, or a dock panel going from docked to floating) and a blind
     * swap would flip-flop. Choosing by brightness makes this idempotent.
     *
     * @return true if the button had a usable pair and was handled
     */
    private boolean swapEnabledDisabledIcons(javax.swing.AbstractButton button) {
        Icon enabled = button.getIcon();
        // IA is not consistent about which disabled slot it fills: take
        // whichever carries a distinct icon.
        Icon disabled = button.getDisabledSelectedIcon();
        if (disabled == null || disabled == enabled) {
            disabled = button.getDisabledIcon();
        }
        if (logIconPairOnce(button, enabled, disabled)) {
            // first sighting of this button class — logged above
        }
        if (enabled == null || disabled == null || enabled == disabled) {
            return false;
        }
        Icon brightest = brightness(enabled) >= brightness(disabled) ? enabled : disabled;
        Icon dimmest = brightest == enabled ? disabled : enabled;

        buttonIconPairs.putIfAbsent(button,
            new Icon[] {enabled, button.getDisabledIcon(), button.getDisabledSelectedIcon()});
        button.setIcon(brightest);
        button.setDisabledIcon(dimmest);
        button.setDisabledSelectedIcon(dimmest);
        return true;
    }

    /**
     * One line per button class the first time it is seen, recording which
     * icon slots Ignition actually populated and how bright each is. Which
     * slots carry a usable pair is an Ignition implementation detail that
     * varies by widget, so this is the evidence for whether the swap above
     * can do anything at all on a given surface.
     */
    private boolean logIconPairOnce(javax.swing.AbstractButton button, Icon enabled, Icon disabled) {
        String key = button.getClass().getName();
        if (!loggedIconClasses.add("pair:" + key)) {
            return false;
        }
        DebugLog.detail(String.format(
            "TreeIconRecolorer: %s icon=%s(%.0f) disabledIcon=%s disabledSelected=%s -> pair=%s",
            key,
            enabled == null ? "-" : enabled.getClass().getSimpleName(),
            enabled == null ? -1.0 : brightness(enabled),
            button.getDisabledIcon() == null ? "-"
                : button.getDisabledIcon().getClass().getSimpleName()
                    + "(" + Math.round(brightness(button.getDisabledIcon())) + ")",
            button.getDisabledSelectedIcon() == null ? "-"
                : button.getDisabledSelectedIcon().getClass().getSimpleName()
                    + "(" + Math.round(brightness(button.getDisabledSelectedIcon())) + ")",
            (enabled != null && disabled != null && enabled != disabled) ? "YES" : "no"));
        return true;
    }

    private Icon darkVariant(Icon original) {
        Icon cached = darkVariants.get(original);
        if (cached != null) {
            return cached;
        }
        Icon variant = tintedCopy(original);
        if (variant != null) {
            darkVariants.put(original, variant);
            variantIcons.add(variant);
        }
        return variant;
    }

    /**
     * Paint the icon offscreen and adapt it for a dark background with a
     * "smart invert": neutral (low-saturation) pixels have their lightness
     * inverted and take the theme tint — dark strokes become light — while
     * saturated brand colors (status greens/reds, logo colors) keep their hue
     * and are only brightened enough to read on the dark surface.
     */
    private Icon tintedCopy(Icon source) {
        try {
            int width = Math.max(source.getIconWidth(), 1);
            int height = Math.max(source.getIconHeight(), 1);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            try {
                source.paintIcon(paintDummy, g2, 0, 0);
            } finally {
                g2.dispose();
            }
            float[] tintHsb = Color.RGBtoHSB(
                iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), null);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = image.getRGB(x, y);
                    int alpha = pixel >>> 24;
                    if (alpha == 0) {
                        continue;
                    }
                    float[] hsb = Color.RGBtoHSB(
                        (pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF, null);
                    int rgb;
                    if (hsb[1] < 0.25f) {
                        // Neutral: invert lightness, colored with the theme tint.
                        float brightness = Math.min(1f, 0.25f + (1f - hsb[2]) * 0.75f);
                        rgb = Color.HSBtoRGB(tintHsb[0], tintHsb[1], brightness);
                    } else {
                        // Brand color: keep the hue, ensure it reads on dark.
                        rgb = Color.HSBtoRGB(hsb[0], hsb[1] * 0.9f, Math.max(hsb[2], 0.7f));
                    }
                    image.setRGB(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
                }
            }
            return new ImageIcon(image);
        } catch (Exception e) {
            log.warn("Could not tint a Designer SVG icon.", e);
            return null;
        }
    }

    /** Original icons of buttons/labels whose icon was swapped for a dark variant. */
    private final Map<Component, Icon> buttonIconOriginals = new WeakHashMap<>();
    /** Icon -> average brightness of its visible pixels; rasterising is not free. */
    private final Map<Icon, Double> iconBrightness = new IdentityHashMap<>();
    /** Button -> its stock {icon, disabledIcon, disabledSelectedIcon}, for the light restore. */
    private final Map<javax.swing.AbstractButton, Icon[]> buttonIconPairs = new WeakHashMap<>();

    /**
     * Toolbar/button icons (the Project Properties gear, ...) are dark glyphs
     * invisible on the dark theme; give them the same dark variants as tree
     * icons. Safe to re-run; restored by uninstall().
     */
    public void recolorButtonIcons() {
        for (Window window : Window.getWindows()) {
            recolorButtonIcons(window);
        }
    }

    private void recolorButtonIcons(Container container) {
        recolorButtonIcons(container, false);
    }

    /**
     * Recolor toolbar/status-bar glyph icons. Buttons anywhere are fair game;
     * bare JLabels only inside toolbars or the status bar (a JLabel elsewhere
     * is usually content, and tree/table renderer labels are handled by the
     * render path). {@code inIconBar} tracks whether we are under such a bar.
     */
    private void recolorButtonIcons(Container container, boolean inIconBar) {
        boolean bar = inIconBar
            || container instanceof javax.swing.JToolBar
            || container.getClass().getName().endsWith("StatusBar");
        for (Component child : container.getComponents()) {
            javax.swing.JLabel label = null;
            Icon icon = null;
            if (child instanceof javax.swing.AbstractButton) {
                icon = ((javax.swing.AbstractButton) child).getIcon();
            } else if (bar && child instanceof javax.swing.JLabel) {
                label = (javax.swing.JLabel) child;
                icon = label.getIcon();
            }
            // Prefer Ignition's own light variant where the button carries a
            // disabled/enabled pair: it is the icon IA drew for a low-contrast
            // context, so it beats anything a filter can synthesise. Only fall
            // back to the smart invert when there is no pair to swap.
            boolean paired = child instanceof javax.swing.AbstractButton
                && swapEnabledDisabledIcons((javax.swing.AbstractButton) child);
            if (!paired && icon != null && !variantIcons.contains(icon)) {
                Icon variant = darkVariant(icon);
                if (variant != null) {
                    buttonIconOriginals.putIfAbsent((Component) child, icon);
                    if (label != null) {
                        label.setIcon(variant);
                    } else {
                        ((javax.swing.AbstractButton) child).setIcon(variant);
                    }
                }
            }
            if (child instanceof Container) {
                recolorButtonIcons((Container) child, bar);
            }
        }
    }

    /** Delegates to the original renderer, then adapts colors and icons. */
    /**
     * Does this tree hand its renderer back through a typed accessor?
     *
     * <p>If it does, it casts — and a wrapper of ours will not survive the
     * cast. {@code TagBrowserTree.getTagRenderer()} is
     * {@code (TagRenderer) getCellRenderer()}, called from that tree's own
     * {@code paint}, so wrapping it threw a {@code ClassCastException} on the
     * EDT every time the Tag Browser repainted.
     *
     * <p>Detected by shape rather than by name: any zero-argument method
     * declared below {@code JTree} whose return type is a {@code
     * TreeCellRenderer} SUBTYPE is a cast waiting to happen. That is broader
     * than strictly necessary — a class could declare such an accessor and
     * never cast {@code getCellRenderer()} — and the trade is deliberate: the
     * cost of skipping is that one tree's icons keep their stock colours, and
     * the cost of getting it wrong the other way is an exception in the paint
     * loop.
     */
    private static boolean castsItsRenderer(JTree tree) {
        for (Class<?> type = tree.getClass();
                type != null && type != JTree.class;
                type = type.getSuperclass()) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() == 0
                        && TreeCellRenderer.class.isAssignableFrom(method.getReturnType())
                        && method.getReturnType() != TreeCellRenderer.class) {
                    DebugLog.detail("TreeIconRecolorer: not wrapping "
                        + tree.getClass().getName() + " — it publishes "
                        + method.getName() + "() returning "
                        + method.getReturnType().getSimpleName() + ", which casts.");
                    return true;
                }
            }
        }
        return false;
    }

    private class RecoloringRenderer implements TreeCellRenderer {

        private final TreeCellRenderer delegate;

        RecoloringRenderer(TreeCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            touchedRenderers.add(delegate);
            syncRendererColors(delegate);
            Component component = delegate.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, hasFocus);
            processComponent(component);
            return component;
        }
    }
}
