/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.RelativeLayout;

import org.telegram.android.AndroidUtilities;

public class SizeNotifierRelativeLayoutPhoto extends RelativeLayout {

    public interface SizeNotifierRelativeLayoutPhotoDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    private Rect rect = new Rect();
    private int keyboardHeight;
    private SizeNotifierRelativeLayoutPhotoDelegate delegate;

    public SizeNotifierRelativeLayoutPhoto(Context context) {
        super(context);
    }

    public void setDelegate(SizeNotifierRelativeLayoutPhotoDelegate delegate) {
        this.delegate = delegate;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (delegate != null) {
            View rootView = this.getRootView();
            int usableViewHeight = rootView.getHeight() - AndroidUtilities.getViewInset(rootView);
            this.getWindowVisibleDisplayFrame(rect);
            keyboardHeight = (rect.bottom - rect.top) - usableViewHeight;
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
