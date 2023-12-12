/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

public class SizeNotifierFrameLayoutPhoto extends SizeNotifierFrameLayout {

    private Activity activity;
    private Rect rect = new Rect();
    private int keyboardHeight;
    private WindowManager windowManager;
    private boolean withoutWindow;
    private boolean useSmoothKeyboard;

    public SizeNotifierFrameLayoutPhoto(Context context, Activity activity, boolean smoothKeyboard) {
        super(context);
        setActivity(activity);
        useSmoothKeyboard = smoothKeyboard;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void setWithoutWindow(boolean value) {
        withoutWindow = value;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        notifyHeightChanged();
    }

    @Override
    public int getKeyboardHeight() {
       return keyboardHeight;
    }

    @Override
    public int measureKeyboardHeight() {
        View rootView = getRootView();
        getWindowVisibleDisplayFrame(rect);
        if (withoutWindow) {
            int usableViewHeight = rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);
            return usableViewHeight - (rect.bottom - rect.top);
        } else {
            int size = activity.getWindow().getDecorView().getHeight() - AndroidUtilities.getViewInset(rootView) - rootView.getBottom();
            if (size <= Math.max(AndroidUtilities.dp(10), AndroidUtilities.statusBarHeight)) {
                size = 0;
            }
            return size;
        }
    }

    @Override
    public void notifyHeightChanged() {
        if (super.delegate != null) {
            keyboardHeight = measureKeyboardHeight();
            final boolean isWidthGreater = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
            post(() -> {
                if (delegate != null) {
                    delegate.onSizeChanged(keyboardHeight, isWidthGreater);
                }
            });
        }
    }
}
