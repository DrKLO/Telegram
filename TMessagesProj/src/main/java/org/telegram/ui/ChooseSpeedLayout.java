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

public class ChooseSpeedLayout {

    ActionBarPopupWindow.ActionBarPopupWindowLayout speedSwipeBackLayout;
    ActionBarMenuSlider.SpeedSlider slider;

    private static final float MIN_SPEED = 0.2f;
    private static final float MAX_SPEED = 2.5f;

    ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[5];
    public ChooseSpeedLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback) {
        speedSwipeBackLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        speedSwipeBackLayout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_arrow_back, LocaleController.getString("Back", R.string.Back), false, null);
        backItem.setOnClickListener(view -> {
            swipeBackLayout.closeForeground();
        });
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        speedSwipeBackLayout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(8);
        gap.setLayoutParams(layoutParams);

        slider = new ActionBarMenuSlider.SpeedSlider(context, null);
        slider.setMinimumWidth(AndroidUtilities.dp(196));
        slider.setDrawShadow(false);
        slider.setBackgroundColor(0xff222222);
        slider.setTextColor(0xffffffff);
        slider.setOnValueChange((value, isFinal) -> {
            final float speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * value;
            callback.onSpeedSelected(speed, isFinal, false);
        });
        speedSwipeBackLayout.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));

        gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        speedSwipeBackLayout.addView(gap);
        layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(8);
        gap.setLayoutParams(layoutParams);

        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_0_2, LocaleController.getString("SpeedVerySlow", R.string.SpeedVerySlow), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(0.2f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[0] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_slow, LocaleController.getString("SpeedSlow", R.string.SpeedSlow), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(0.5f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[1] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_normal, LocaleController.getString("SpeedNormal", R.string.SpeedNormal), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(1f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[2] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_fast, LocaleController.getString("SpeedFast", R.string.SpeedFast), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(1.5f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[3] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_superfast, LocaleController.getString("SpeedVeryFast", R.string.SpeedVeryFast), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(2f, true, true);
        });
        item.setSelectorColor(0x0fffffff);
        speedItems[4] = item;
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

        slider.setSpeed(currentVideoSpeed, true);
    }

    public interface Callback {
        void onSpeedSelected(float speed, boolean isFinal, boolean closeMenu);
    }
}
