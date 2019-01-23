/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;

public class TestActivity extends BaseFragment {

    int num = 0;
    int p = 0;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Test");

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xff000000);
        fragmentView = frameLayout;

        MediaActionDrawable actionDrawable2 = new MediaActionDrawable();
        actionDrawable2.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false);

        ImageView imageView = new ImageView(context);
        imageView.setImageDrawable(actionDrawable2);
        actionDrawable2.setDelegate(imageView::invalidate);
        frameLayout.addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
        frameLayout.setOnClickListener(v -> {
            int icon = actionDrawable2.getCurrentIcon();
            boolean animated = true;
            if (icon == MediaActionDrawable.ICON_DOWNLOAD) {
                icon = MediaActionDrawable.ICON_CANCEL;
            } else if (icon == MediaActionDrawable.ICON_CANCEL) {
                icon = MediaActionDrawable.ICON_PLAY;
            } else if (icon == MediaActionDrawable.ICON_PLAY) {
                icon = MediaActionDrawable.ICON_DOWNLOAD;
                animated = false;
            }
            actionDrawable2.setIcon(icon, animated);
        });

        return fragmentView;
    }
}
