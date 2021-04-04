package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.AnimatedBg.AnimatedBgContainer;
import org.telegram.ui.Components.LayoutHelper;

public class AnimationBgPreviewCell extends FrameLayout {

    public AnimatedBgContainer bgContainer;

    public boolean snapshot;

    public AnimationBgPreviewCell(Context context) {
        super(context);
        bgContainer = new AnimatedBgContainer(context);

        setPadding(0, AndroidUtilities.dp(16), 0, 0);
        addView(bgContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 130));
    }
}