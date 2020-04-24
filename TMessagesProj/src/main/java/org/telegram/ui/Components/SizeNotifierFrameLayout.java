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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AdjustPanFrameLayout;
import org.telegram.ui.ActionBar.Theme;

public class SizeNotifierFrameLayout extends AdjustPanFrameLayout {

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
    private int backgroundTranslationY;
    private boolean paused = true;
    private Drawable oldBackgroundDrawable;
    private ActionBarLayout parentLayout;
    private boolean useSmoothKeyboard;

    public interface SizeNotifierFrameLayoutDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public SizeNotifierFrameLayout(Context context, boolean smoothKeyboard) {
        this(context, smoothKeyboard, null);
    }

    public SizeNotifierFrameLayout(Context context, boolean smoothKeyboard, ActionBarLayout layout) {
        super(context);
        setWillNotDraw(false);
        useSmoothKeyboard = smoothKeyboard;
        parentLayout = layout;
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
        return keyboardHeight = Math.max(0, usableViewHeight - (rect.bottom - rect.top));
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

    public void setBackgroundTranslation(int translation) {
        backgroundTranslationY = translation;
    }

    public int getHeightWithKeyboard() {
        return keyboardHeight + getMeasuredHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundDrawable == null) {
            super.onDraw(canvas);
            return;
        }
        int kbHeight = useSmoothKeyboard ? 0 : keyboardHeight;
        Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
        if (newDrawable != backgroundDrawable && newDrawable != null) {
            if (Theme.isAnimatingColor()) {
                oldBackgroundDrawable = backgroundDrawable;
            }
            backgroundDrawable = newDrawable;
        }
        float themeAnimationValue = parentLayout != null ? parentLayout.getThemeAnimationValue() : 1.0f;
        for (int a = 0; a < 2; a++) {
            Drawable drawable = a == 0 ? oldBackgroundDrawable : backgroundDrawable;
            if (drawable == null) {
                continue;
            }
            if (a == 1 && oldBackgroundDrawable != null && parentLayout != null) {
                drawable.setAlpha((int) (255 * themeAnimationValue));
            } else {
                drawable.setAlpha(255);
            }
            if (drawable instanceof ColorDrawable) {
                if (bottomClip != 0) {
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - bottomClip);
                }
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                drawable.draw(canvas);
                if (bottomClip != 0) {
                    canvas.restore();
                }
            } else if (drawable instanceof GradientDrawable) {
                if (bottomClip != 0) {
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - bottomClip);
                }
                drawable.setBounds(0, backgroundTranslationY, getMeasuredWidth(), backgroundTranslationY + getMeasuredHeight() + kbHeight);
                drawable.draw(canvas);
                if (bottomClip != 0) {
                    canvas.restore();
                }
            } else if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                    drawable.draw(canvas);
                    canvas.restore();
                } else {
                    int actionBarHeight = (isActionBarVisible() ? ActionBar.getCurrentActionBarHeight() : 0) + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
                    int viewHeight = getMeasuredHeight() - actionBarHeight;
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight + kbHeight) / (float) drawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale * parallaxScale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale * parallaxScale);
                    int x = (getMeasuredWidth() - width) / 2 + (int) translationX;
                    int y = backgroundTranslationY + (viewHeight - height + kbHeight) / 2 + actionBarHeight + (int) translationY;
                    canvas.save();
                    canvas.clipRect(0, actionBarHeight, width, getMeasuredHeight() - bottomClip);
                    drawable.setBounds(x, y, x + width, y + height);
                    drawable.draw(canvas);
                    canvas.restore();
                }
            }
            if (a == 0 && oldBackgroundDrawable != null && themeAnimationValue >= 1.0f) {
                oldBackgroundDrawable = null;
                invalidate();
            }
        }
    }

    protected boolean isActionBarVisible() {
        return true;
    }
}
