package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Color;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationConfigurationView;
import org.telegram.ui.Components.LayoutHelper;

public class AnimationConfigurationCell extends FrameLayout {

    public AnimationConfigurationView configView;

    public AnimationConfigurationCell(Context context) {
        super(context);
        configView = new AnimationConfigurationView(context);

        //Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        addView(configView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 240));
    }
}