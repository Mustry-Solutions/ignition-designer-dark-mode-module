package com.mustrysolutions.designerdarkmode.designer;

/**
 * The shape of a Vision Comments Panel: a scroll pane whose constructor
 * sets its border to {@code null} after the look and feel installed one.
 * Under FlatLaf a fresh one has no border and a tree update gives it
 * {@code ScrollPane.border}; {@link VisionConstructionBorders} puts the
 * {@code null} back.
 */
public class SerializerProbeBorderlessTable extends SerializerProbeTable {

    public SerializerProbeBorderlessTable() {
        setBorder(null);
    }
}
