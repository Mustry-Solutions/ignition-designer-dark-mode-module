package com.inductiveautomation.sfc.client;

import javax.swing.JPanel;

/**
 * A stand-in for the SFC module's chart panel, under its real name.
 *
 * <p>The SFC client jar is a module jar, not on the harness classpath, and
 * the white swap recognises the chart by class NAME. The name is the contract
 * this stand-in carries; nothing here is the SFC module's code.
 */
public class SfcDisplayPanel extends JPanel {
}
