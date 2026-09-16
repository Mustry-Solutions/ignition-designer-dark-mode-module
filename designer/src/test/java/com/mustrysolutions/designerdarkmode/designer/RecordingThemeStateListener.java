package com.mustrysolutions.designerdarkmode.designer;

import java.util.ArrayList;
import java.util.List;

/** Records what the Tools menu is told the theme ended up being. */
final class RecordingThemeStateListener implements ThemeManager.ThemeStateListener {

    /** One entry per finished switch: the theme the Designer was left in. */
    final List<Boolean> darkActive = new ArrayList<>();

    @Override
    public void switchStarted() {
    }

    @Override
    public void switchFinished(boolean dark) {
        darkActive.add(dark);
    }
}
