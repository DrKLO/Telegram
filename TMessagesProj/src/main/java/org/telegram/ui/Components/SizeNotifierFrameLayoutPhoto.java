/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

public class SizeNotifierFrameLayoutPhoto extends FrameLayout {

    private Rect rect = new Rect();
    private int keyboardHeight;
    private SizeNotifierFrameLayoutPhotoDelegate delegate;
    private WindowManager windowManager;
    private boolean withoutWindow;

    public interface SizeNotifierFrameLayoutPhotoDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public SizeNotifierFrameLayoutPhoto(Context context) {
        super(context);
    }

    public void setDelegate(SizeNotifierFrameLayoutPhotoDelegate sizeNotifierFrameLayoutPhotoDelegate) {
        delegate = sizeNotifierFrameLayoutPhotoDelegate;
    }

    public void setWithoutWindow(boolean value) {
        withoutWindow = value;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        notifyHeightChanged();
    }

    public int getKeyboardHeight() {
        View rootView = getRootView();
        getWindowVisibleDisplayFrame(rect);
        if (withoutWindow) {
            int usableViewHeight = rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);
            return usableViewHeight - (rect.bottom - rect.top);
        } else {
            int usableViewHeight = rootView.getHeight() - AndroidUtilities.getViewInset(rootView);
            int top = rect.top;
            int size = AndroidUtilities.displaySize.y - top - usableViewHeight;
            if (size <= Math.max(AndroidUtilities.dp(10), AndroidUtilities.statusBarHeight)) {
                size = 0;
            }
            return size;
        }
    }

    public void notifyHeightChanged() {
        if (delegate != null) {
            keyboardHeight = getKeyboardHeight();
            final boolean isWidthGreater = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
            post(new Runnable() {
                @Override
                public void run() {
                    if (delegate != null) {
                        delegate.onSizeChanged(keyboardHeight, isWidthGreater);
                    }
                }
            });
        }
    }
}
