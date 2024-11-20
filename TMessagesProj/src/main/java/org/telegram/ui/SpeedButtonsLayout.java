package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.SpeedIconDrawable;

public class SpeedButtonsLayout extends LinearLayout {

    ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[5];
    public SpeedButtonsLayout(Context context, Callback callback) {
        super(context);
        setOrientation(VERTICAL);

        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(this, R.drawable.msg_speed_0_2, LocaleController.getString(R.string.SpeedVerySlow), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(0.2f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[0] = item;

        item = ActionBarMenuItem.addItem(this, R.drawable.msg_speed_slow, LocaleController.getString(R.string.SpeedSlow), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(0.5f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[1] = item;

        item = ActionBarMenuItem.addItem(this, R.drawable.msg_speed_normal, LocaleController.getString(R.string.SpeedNormal), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(1f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[2] = item;

        item = ActionBarMenuItem.addItem(this, R.drawable.msg_speed_fast, LocaleController.getString(R.string.SpeedFast), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(1.5f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[3] = item;

        item = ActionBarMenuItem.addItem(this, R.drawable.msg_speed_superfast, LocaleController.getString(R.string.SpeedVeryFast), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(2f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[4] = item;

        FrameLayout gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(8);
        gap.setLayoutParams(layoutParams);
    }

    public void update(float currentVideoSpeed, boolean isFinal) {
        for (int a = 0; a < speedItems.length; a++) {
            if (isFinal && (
                    a == 0 && Math.abs(currentVideoSpeed - 0.2f) < 0.01f ||
                            a == 1 && Math.abs(currentVideoSpeed - 0.5f) < 0.1f ||
                            a == 2 && Math.abs(currentVideoSpeed - 1.0f) < 0.1f ||
                            a == 3 && Math.abs(currentVideoSpeed - 1.5f) < 0.1f ||
                            a == 4 && Math.abs(currentVideoSpeed - 2.0f) < 0.1f
            )) {
                speedItems[a].setColors(0xff6BB6F9, 0xff6BB6F9);
            } else {
                speedItems[a].setColors(0xfffafafa, 0xfffafafa);
            }
        }
    }

    public interface Callback {
        void onSpeedSelected(float speed, boolean isFinal, boolean closeMenu);
    }
}
