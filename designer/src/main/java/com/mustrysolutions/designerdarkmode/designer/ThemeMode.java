package com.mustrysolutions.designerdarkmode.designer;

/**
 * The three choices under Tools → Dark Mode.
 *
 * <p>{@link #SYSTEM} resolves to light or dark from {@link SystemTheme} when
 * the Designer launches and when it is chosen; it does not follow the system
 * while the Designer is open.
 */
public enum ThemeMode {
    LIGHT, DARK, SYSTEM
}
