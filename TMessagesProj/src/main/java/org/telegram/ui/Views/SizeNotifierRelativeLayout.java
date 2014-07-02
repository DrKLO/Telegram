/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.RelativeLayout;

import org.telegram.android.AndroidUtilities;

public class SizeNotifierRelativeLayout extends RelativeLayout {

    private Rect rect = new Rect();
    private Drawable backgroundDrawable;
    public SizeNotifierRelativeLayoutDelegate delegate;

    public abstract interface SizeNotifierRelativeLayoutDelegate {
        public abstract void onSizeChanged(int keyboardHeight);
    }

    public SizeNotifierRelativeLayout(Context context) {
        super(context);
    }

    public SizeNotifierRelativeLayout(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public SizeNotifierRelativeLayout(android.content.Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setBackgroundImage(int resourceId) {
        backgroundDrawable = getResources().getDrawable(resourceId);
    }

    public void setBackgroundImage(Drawable bitmap) {
        backgroundDrawable = bitmap;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (delegate != null) {
            int usableViewHeight = this.getRootView().getHeight() - AndroidUtilities.statusBarHeight;
            this.getWindowVisibleDisplayFrame(rect);
            int keyboardHeight = usableViewHeight - (rect.bottom - rect.top);
            delegate.onSizeChanged(keyboardHeight);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundDrawable != null) {
            float scaleX = (float)AndroidUtilities.displaySize.x / (float)backgroundDrawable.getIntrinsicWidth();
            float scaleY = (float)AndroidUtilities.displaySize.y / (float)backgroundDrawable.getIntrinsicHeight();
            float scale = scaleX < scaleY ? scaleY : scaleX;
            int width = (int)Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
            int height = (int)Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
            int x = (AndroidUtilities.displaySize.x - width) / 2;
            int y = (AndroidUtilities.displaySize.y - height) / 2;
            backgroundDrawable.setBounds(x, y, x + width, y + height);
            backgroundDrawable.draw(canvas);
        } else {
            super.onDraw(canvas);
        }
    }
}
