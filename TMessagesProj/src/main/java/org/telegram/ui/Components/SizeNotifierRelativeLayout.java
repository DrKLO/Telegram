/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.RelativeLayout;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.FileLog;

public class SizeNotifierRelativeLayout extends RelativeLayout {

    private Rect rect = new Rect();
    private Drawable backgroundDrawable;
    private int keyboardHeight;
    private SizeNotifierRelativeLayoutDelegate delegate;

    public interface SizeNotifierRelativeLayoutDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public SizeNotifierRelativeLayout(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public SizeNotifierRelativeLayout(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public SizeNotifierRelativeLayout(android.content.Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setWillNotDraw(false);
    }

    public void setBackgroundImage(int resourceId) {
        try {
            backgroundDrawable = getResources().getDrawable(resourceId);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    public void setBackgroundImage(Drawable bitmap) {
        backgroundDrawable = bitmap;
    }

    public Drawable getBackgroundImage() {
        return backgroundDrawable;
    }

    public void setDelegate(SizeNotifierRelativeLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (delegate != null) {
            View rootView = this.getRootView();
            int usableViewHeight = rootView.getHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.getViewInset(rootView);
            this.getWindowVisibleDisplayFrame(rect);
            keyboardHeight = usableViewHeight - (rect.bottom - rect.top);
            final boolean isWidthGreater = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
            FileLog.e("tmessages", "isWidthGreater = " + isWidthGreater + " height = " + keyboardHeight);
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

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundDrawable != null) {
            if (backgroundDrawable instanceof ColorDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
            } else {
                float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                float scaleY = (float) (getMeasuredHeight() + keyboardHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                float scale = scaleX < scaleY ? scaleY : scaleX;
                int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                int x = (getMeasuredWidth() - width) / 2;
                int y = (getMeasuredHeight() - height + keyboardHeight) / 2;
                backgroundDrawable.setBounds(x, y, x + width, y + height);
                backgroundDrawable.draw(canvas);
            }
        } else {
            super.onDraw(canvas);
        }
    }
}
