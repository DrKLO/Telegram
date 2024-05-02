/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

public class CombinedDrawable extends Drawable implements Drawable.Callback {

    private Drawable background;
    private Drawable icon;
    private int left;
    private int top;
    private int iconWidth;
    private int iconHeight;
    private int backWidth;
    private int backHeight;
    private boolean center;
    private int offsetX;
    private int offsetY;
    private boolean fullSize;
    private boolean both;

    public CombinedDrawable(Drawable backgroundDrawable, Drawable iconDrawable, int leftOffset, int topOffset) {
        background = backgroundDrawable;
        icon = iconDrawable;
        left = leftOffset;
        top = topOffset;
        if (iconDrawable != null) {
            iconDrawable.setCallback(this);
        }
    }

    public void setIconSize(int width, int height) {
        iconWidth = width;
        iconHeight = height;
    }

    public CombinedDrawable(Context context, int backgroundDrawableResId, int iconDrawableResId) {
        background = context.getResources().getDrawable(backgroundDrawableResId);
        icon = context.getResources().getDrawable(iconDrawableResId);
        if (icon != null) {
            icon.setCallback(this);
        }
        both = true;
    }

    public CombinedDrawable(Drawable backgroundDrawable, Drawable iconDrawable) {
        background = backgroundDrawable;
        icon = iconDrawable;
        if (iconDrawable != null) {
            iconDrawable.setCallback(this);
        }
    }

    public void setBackgroundDrawable(Drawable backgroundDrawable) {
        background = backgroundDrawable;
        invalidateSelf();
    }

    public void setIconDrawable(Drawable iconDrawable) {
        icon = iconDrawable;
        invalidateSelf();
    }

    public void setCustomSize(int width, int height) {
        backWidth = width;
        backHeight = height;
    }

    public void setCenter(boolean value) {
        center = value;
    }

    public void setIconOffset(int x, int y) {
        offsetX = x;
        offsetY = y;
    }

    public Drawable getIcon() {
        return icon;
    }

    public Drawable getBackground() {
        return background;
    }

    public void setFullsize(boolean value) {
        fullSize = value;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        icon.setColorFilter(colorFilter);
        if (both) {
            background.setColorFilter(colorFilter);
        }
    }

    @Override
    public boolean isStateful() {
        return icon.isStateful();
    }

    @Override
    public boolean setState(int[] stateSet) {
        icon.setState(stateSet);
        return true;
    }

    @Override
    public int[] getState() {
        return icon.getState();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return true;
    }

    @Override
    public void jumpToCurrentState() {
        icon.jumpToCurrentState();
    }

    @Override
    public ConstantState getConstantState() {
        return icon.getConstantState();
    }

    @Override
    public void draw(Canvas canvas) {
        if (center) {
            Rect bounds = getBounds();
            setBounds(
                bounds.centerX() - getIntrinsicWidth() / 2,
                bounds.centerY() - getIntrinsicHeight() / 2,
                bounds.centerX() + getIntrinsicWidth() / 2,
                bounds.centerY() + getIntrinsicHeight() / 2
            );
        }
        if (background != null) {
            background.setBounds(getBounds());
            background.draw(canvas);
        }
        if (icon != null) {
            if (fullSize) {
                android.graphics.Rect bounds = getBounds();
                if (left != 0) {
                    icon.setBounds(bounds.left + left, bounds.top + top, bounds.right - left, bounds.bottom - top);
                } else {
                    icon.setBounds(bounds);
                }
            } else {
                int x;
                int y;
                if (iconWidth != 0) {
                    x = getBounds().centerX() - iconWidth / 2 + left + offsetX;
                    y = getBounds().centerY() - iconHeight / 2 + top + offsetY;
                    icon.setBounds(x, y, x + iconWidth, y + iconHeight);
                } else {
                    x = getBounds().centerX() - icon.getIntrinsicWidth() / 2 + left;
                    y = getBounds().centerY() - icon.getIntrinsicHeight() / 2 + top;
                    icon.setBounds(x, y, x + icon.getIntrinsicWidth(), y + icon.getIntrinsicHeight());
                }
            }
            icon.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        icon.setAlpha(alpha);
        background.setAlpha(alpha);
    }

    @Override
    public int getIntrinsicWidth() {
        return backWidth != 0 ? backWidth : background.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return backHeight != 0 ? backHeight : background.getIntrinsicHeight();
    }

    @Override
    public int getMinimumWidth() {
        return backWidth != 0 ? backWidth : background.getMinimumWidth();
    }

    @Override
    public int getMinimumHeight() {
        return backHeight != 0 ? backHeight : background.getMinimumHeight();
    }

    @Override
    public int getOpacity() {
        return icon.getOpacity();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }

    public Drawable getBackgroundDrawable() {
        return background;
    }
}
