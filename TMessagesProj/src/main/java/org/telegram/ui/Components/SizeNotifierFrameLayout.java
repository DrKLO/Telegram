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
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;

public class SizeNotifierFrameLayout extends FrameLayout {

    private Rect rect = new Rect();
    private Drawable backgroundDrawable;
    private int keyboardHeight;
    private int bottomClip;
    private SizeNotifierFrameLayoutDelegate delegate;
    private boolean occupyStatusBar = true;
    private WallpaperParallaxEffect parallaxEffect;
    private float translationX;
    private float translationY;
    private float parallaxScale = 1.0f;
    private boolean paused = true;

    public interface SizeNotifierFrameLayoutDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public SizeNotifierFrameLayout(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void setBackgroundImage(Drawable bitmap, boolean motion) {
        backgroundDrawable = bitmap;
        if (motion) {
            if (parallaxEffect == null) {
                parallaxEffect = new WallpaperParallaxEffect(getContext());
                parallaxEffect.setCallback((offsetX, offsetY) -> {
                    translationX = offsetX;
                    translationY = offsetY;
                    invalidate();
                });
                if (getMeasuredWidth() != 0 && getMeasuredHeight() != 0) {
                    parallaxScale = parallaxEffect.getScale(getMeasuredWidth(), getMeasuredHeight());
                }
            }
            if (!paused) {
                parallaxEffect.setEnabled(true);
            }
        } else if (parallaxEffect != null) {
            parallaxEffect.setEnabled(false);
            parallaxEffect = null;
            parallaxScale = 1.0f;
            translationX = 0;
            translationY = 0;
        }
        invalidate();
    }

    public Drawable getBackgroundImage() {
        return backgroundDrawable;
    }

    public void setDelegate(SizeNotifierFrameLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
    }

    public void onPause() {
        if (parallaxEffect != null) {
            parallaxEffect.setEnabled(false);
        }
        paused = true;
    }

    public void onResume() {
        if (parallaxEffect != null) {
            parallaxEffect.setEnabled(true);
        }
        paused = false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        notifyHeightChanged();
    }

    public int getKeyboardHeight() {
        View rootView = getRootView();
        getWindowVisibleDisplayFrame(rect);
        if (rect.bottom == 0 && rect.top == 0) {
            return 0;
        }
        int usableViewHeight = rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);
        return Math.max(0, usableViewHeight - (rect.bottom - rect.top));
    }

    public void notifyHeightChanged() {
        if (parallaxEffect != null) {
            parallaxScale = parallaxEffect.getScale(getMeasuredWidth(), getMeasuredHeight());
        }
        if (delegate != null) {
            keyboardHeight = getKeyboardHeight();
            final boolean isWidthGreater = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
            post(() -> {
                if (delegate != null) {
                    delegate.onSizeChanged(keyboardHeight, isWidthGreater);
                }
            });
        }
    }

    public void setBottomClip(int value) {
        bottomClip = value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundDrawable != null) {
            if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                if (bottomClip != 0) {
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - bottomClip);
                }
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
                if (bottomClip != 0) {
                    canvas.restore();
                }
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) backgroundDrawable;
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    backgroundDrawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                    backgroundDrawable.draw(canvas);
                    canvas.restore();
                } else {
                    int actionBarHeight = (isActionBarVisible() ? ActionBar.getCurrentActionBarHeight() : 0) + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
                    int viewHeight = getMeasuredHeight() - actionBarHeight;
                    float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight + keyboardHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                    float scale = scaleX < scaleY ? scaleY : scaleX;
                    int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale * parallaxScale);
                    int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale * parallaxScale);
                    int x = (getMeasuredWidth() - width) / 2 + (int) translationX;
                    int y = (viewHeight - height + keyboardHeight) / 2 + actionBarHeight + (int) translationY;
                    canvas.save();
                    canvas.clipRect(0, actionBarHeight, width, getMeasuredHeight() - bottomClip);
                    backgroundDrawable.setAlpha(255);
                    backgroundDrawable.setBounds(x, y, x + width, y + height);
                    backgroundDrawable.draw(canvas);
                    canvas.restore();
                }
            }
        } else {
            super.onDraw(canvas);
        }
    }

    protected boolean isActionBarVisible() {
        return true;
    }
}
