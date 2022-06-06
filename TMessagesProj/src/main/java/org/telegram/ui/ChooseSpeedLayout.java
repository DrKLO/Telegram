package org.telegram.ui;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.PopupSwipeBackLayout;

public class ChooseSpeedLayout {

    ActionBarPopupWindow.ActionBarPopupWindowLayout speedSwipeBackLayout;

    ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[5];
    public ChooseSpeedLayout(Context context, PopupSwipeBackLayout swipeBackLayout,  Callback callback) {
        speedSwipeBackLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        speedSwipeBackLayout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_arrow_back, LocaleController.getString("Back", R.string.Back), false, null);
        backItem.setOnClickListener(view -> {
            swipeBackLayout.closeForeground();
        });
        backItem.setColors(0xfffafafa, 0xfffafafa);

        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_0_2, LocaleController.getString("SpeedVerySlow", R.string.SpeedVerySlow), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(0.25f);
        });
        speedItems[0] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_0_5, LocaleController.getString("SpeedSlow", R.string.SpeedSlow), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(0.5f);
        });
        speedItems[1] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_1, LocaleController.getString("SpeedNormal", R.string.SpeedNormal), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(1f);
        });
        speedItems[2] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_1_5, LocaleController.getString("SpeedFast", R.string.SpeedFast), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(1.5f);
        });
        speedItems[3] = item;

        item = ActionBarMenuItem.addItem(speedSwipeBackLayout, R.drawable.msg_speed_2, LocaleController.getString("SpeedVeryFast", R.string.SpeedVeryFast), false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener((view) -> {
            callback.onSpeedSelected(2f);
        });
        speedItems[4] = item;
    }

    public void update(float currentVideoSpeed) {
        for (int a = 0; a < speedItems.length; a++) {
            if (a == 0 && Math.abs(currentVideoSpeed - 0.25f) < 0.001f ||
                    a == 1 && Math.abs(currentVideoSpeed - 0.5f) < 0.001f ||
                    a == 2 && Math.abs(currentVideoSpeed - 1.0f) < 0.001f ||
                    a == 3 && Math.abs(currentVideoSpeed - 1.5f) < 0.001f ||
                    a == 4 && Math.abs(currentVideoSpeed - 2.0f) < 0.001f) {
                speedItems[a].setColors(0xff6BB6F9, 0xff6BB6F9);
            } else {
                speedItems[a].setColors(0xfffafafa, 0xfffafafa);
            }
        }
    }

    public interface Callback {
        void onSpeedSelected(float speed);
    }
}
