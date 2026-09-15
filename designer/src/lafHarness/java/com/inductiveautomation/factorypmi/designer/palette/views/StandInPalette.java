package com.inductiveautomation.factorypmi.designer.palette.views;

import javax.swing.JPanel;

/**
 * A stand-in for Vision's {@code CollapsiblePanePalette}, under its real
 * package name.
 *
 * <p>The module decides what counts as "inside Vision" by class NAME, and the
 * palette and the property editor both live under {@code factorypmi} while
 * being Designer chrome rather than user content. The package is the
 * contract this stand-in carries; nothing here is Vision's code.
 */
public class StandInPalette extends JPanel {
}
