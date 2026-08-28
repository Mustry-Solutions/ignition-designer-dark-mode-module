package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Themes the Designer's code editors — script console, project library,
 * Vision component scripts, Perspective script transforms, Named Query editor.
 *
 * <p>These are {@link RSyntaxTextArea}s, which own their colours through a
 * {@code SyntaxScheme} rather than taking them from the look and feel. Nothing
 * else in this module reaches them: not the look-and-feel swap, not the design
 * token mutation, not a component walk. They need their own theme applied.
 *
 * <p>Ignition ships one. {@code com.inductiveautomation.ignition.common.gui.NamedTheme}
 * is a platform enum with {@code Default}, {@code Dark}, {@code VisualStudio}
 * and {@code Disabled} members, each backed by a theme XML inside
 * {@code common.jar} and exposed through {@code getTheme()}. Using IA's own
 * dark theme beats inventing one: it is tuned for their syntax scheme and it
 * will follow their editor if they change it.
 *
 * <p>{@code NamedTheme} is resolved reflectively. It is not SDK surface, and a
 * Designer without it should lose code-editor theming rather than the whole
 * dark mode.
 */
final class ScriptEditorTheme {

    private static final String NAMED_THEME =
        "com.inductiveautomation.ignition.common.gui.NamedTheme";

    private final Logger log = LoggerFactory.getLogger(ScriptEditorTheme.class);

    /** Editors we have themed, so the light restore reaches detached ones too. */
    private final Set<RSyntaxTextArea> themed =
        Collections.newSetFromMap(new WeakHashMap<>());
    /** The re-apply guard installed on each editor, so it can be removed again. */
    private final Map<RSyntaxTextArea, PropertyChangeListener> guards = new WeakHashMap<>();

    /** Apply Ignition's dark editor theme. Safe to re-run. */
    void install() {
        apply("Dark", true);
    }

    /** Put every editor we touched back on Ignition's default editor theme. */
    void uninstall() {
        Theme stock = namedTheme("Default");
        if (stock == null) {
            return;
        }
        // Iterate the tracked set, not the live hierarchy: an editor in a
        // closed dialog is no longer reachable by a walk but is still holding
        // our dark scheme, and would come back dark when its window reopens.
        for (RSyntaxTextArea editor : new ArrayList<>(themed)) {
            PropertyChangeListener guard = guards.remove(editor);
            if (guard != null) {
                editor.removePropertyChangeListener(
                    RSyntaxTextArea.SYNTAX_SCHEME_PROPERTY, guard);
            }
            safelyApply(stock, editor);
        }
        themed.clear();
        guards.clear();
        DebugLog.detail("ScriptEditorTheme: restored the default editor theme.");
    }

    private void apply(String themeName, boolean guard) {
        Theme theme = namedTheme(themeName);
        if (theme == null) {
            return;
        }
        int applied = 0;
        for (RSyntaxTextArea editor : findEditors()) {
            if (themed.add(editor)) {
                applied++;
            }
            safelyApply(theme, editor);
            if (guard) {
                installReapplyGuard(editor, theme);
            }
        }
        if (applied > 0) {
            DebugLog.detail("ScriptEditorTheme: themed " + applied + " new editor(s), "
                + themed.size() + " under management.");
        }
    }

    /**
     * Switching tree nodes or editor tabs makes Ignition re-install its own
     * syntax scheme over ours, so the editor goes light the moment someone
     * changes what they are editing. Watch for that and re-apply.
     *
     * <p>The re-apply is scheduled rather than immediate: it fires from inside
     * the property change that is still installing the other scheme, so
     * applying synchronously would just be overwritten again.
     */
    private void installReapplyGuard(RSyntaxTextArea editor, Theme theme) {
        if (guards.containsKey(editor)) {
            return;
        }
        PropertyChangeListener guard = event -> SwingUtilities.invokeLater(() -> {
            if (themed.contains(editor)) {
                safelyApply(theme, editor);
            }
        });
        editor.addPropertyChangeListener(RSyntaxTextArea.SYNTAX_SCHEME_PROPERTY, guard);
        guards.put(editor, guard);
    }

    private void safelyApply(Theme theme, RSyntaxTextArea editor) {
        try {
            theme.apply(editor);
        } catch (Throwable t) {
            DebugLog.log("ScriptEditorTheme: could not apply a theme to an editor.", t);
        }
    }

    /** {@code NamedTheme.<name>.getTheme()}, or null if the platform lacks it. */
    private Theme namedTheme(String name) {
        try {
            Class<?> namedTheme = Class.forName(NAMED_THEME);
            for (Object constant : namedTheme.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return (Theme) namedTheme.getMethod("getTheme").invoke(constant);
                }
            }
            DebugLog.log("ScriptEditorTheme: NamedTheme has no constant named " + name + ".");
        } catch (Throwable t) {
            log.warn("Could not resolve the Ignition editor theme '" + name + "'.", t);
            DebugLog.log("ScriptEditorTheme: NamedTheme unavailable; "
                + "code editors keep their stock theme.", t);
        }
        return null;
    }

    private List<RSyntaxTextArea> findEditors() {
        List<RSyntaxTextArea> editors = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            collect(window, editors);
        }
        return editors;
    }

    private void collect(Container container, List<RSyntaxTextArea> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof RSyntaxTextArea) {
                out.add((RSyntaxTextArea) child);
            }
            if (child instanceof Container) {
                collect((Container) child, out);
            }
        }
    }
}
