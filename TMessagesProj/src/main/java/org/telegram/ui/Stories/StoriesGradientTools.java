package org.telegram.ui.Stories;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.GradientTools;

public class StoriesGradientTools extends GradientTools {

    int colorKey1 = Theme.key_voipgroup_overlayGreen1;
    int colorKey2 = Theme.key_voipgroup_overlayBlue1;
    public StoriesGradientTools() {
        isDiagonal = true;
        setColors(Theme.getColor(colorKey1), Theme.getColor(colorKey2));
    }

    @Override
    protected void updateBounds() {
        setColors(Theme.getColor(colorKey1), Theme.getColor(colorKey2));
        super.updateBounds();
    }
}
