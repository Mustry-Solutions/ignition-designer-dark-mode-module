package com.mustrysolutions.designerdarkmode.designer;

import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * The shape of a Vision Date Time Popup Selector: a panel whose constructor
 * borrows {@code TextField.border}. FlatLaf defines no {@code Panel.border},
 * so a tree update strips it to {@code null}; {@link VisionConstructionBorders}
 * puts the fresh instance's back.
 */
public class SerializerProbeBorrowedBorderPanel extends JPanel {

    public SerializerProbeBorrowedBorderPanel() {
        setBorder(UIManager.getBorder("TextField.border"));
    }
}
