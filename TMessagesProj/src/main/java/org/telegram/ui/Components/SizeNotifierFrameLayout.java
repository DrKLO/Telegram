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
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.Theme;

public class SizeNotifierFrameLayout extends FrameLayout {

    private Rect rect = new Rect();
    private Drawable backgroundDrawable;
    protected int keyboardHeight;
    private int bottomClip;
    private SizeNotifierFrameLayoutDelegate delegate;
    private boolean occupyStatusBar = true;
    private WallpaperParallaxEffect parallaxEffect;
    private float translationX;
    private float translationY;
    private float bgAngle;
    private float parallaxScale = 1.0f;
    private int backgroundTranslationY;
    private boolean paused = true;
    private Drawable oldBackgroundDrawable;
    private ActionBarLayout parentLayout;
    protected AdjustPanLayoutHelper adjustPanLayoutHelper;
    private int emojiHeight;
    private float emojiOffset;
    private boolean animationInProgress;
    private boolean skipBackgroundDrawing;

    public interface SizeNotifierFrameLayoutDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public SizeNotifierFrameLayout(Context context) {
        this(context, null);
    }

    public SizeNotifierFrameLayout(Context context, ActionBarLayout layout) {
        super(context);
        setWillNotDraw(false);
        parentLayout = layout;
        adjustPanLayoutHelper = createAdjustPanLayoutHelper();
    }

    public void setBackgroundImage(Drawable bitmap, boolean motion) {
        if (backgroundDrawable == bitmap) {
            return;
        }
        if (bitmap instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) bitmap;
            motionBackgroundDrawable.setParentView(this);
        }
        backgroundDrawable = bitmap;
        if (motion) {
            if (parallaxEffect == null) {
                parallaxEffect = new WallpaperParallaxEffect(getContext());
                parallaxEffect.setCallback((offsetX, offsetY, angle) -> {
                    translationX = offsetX;
                    translationY = offsetY;
                    bgAngle = angle;
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

    public int measureKeyboardHeight() {
        View rootView = getRootView();
        getWindowVisibleDisplayFrame(rect);
        if (rect.bottom == 0 && rect.top == 0) {
            return 0;
        }
        int usableViewHeight = rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);
        return keyboardHeight = Math.max(0, usableViewHeight - (rect.bottom - rect.top));
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }

    public void notifyHeightChanged() {
        if (parallaxEffect != null) {
            parallaxScale = parallaxEffect.getScale(getMeasuredWidth(), getMeasuredHeight());
        }
        if (delegate != null) {
            keyboardHeight = measureKeyboardHeight();
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

    public int getBackgroundTranslationY() {
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            if (animationInProgress) {
                return (int) emojiOffset;
            } else if (emojiHeight != 0) {
                return emojiHeight;
            }
            return backgroundTranslationY;
        }
        return 0;
    }

    public int getBackgroundSizeY() {
        int offset = 0;
        if (backgroundDrawable instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
            if (!motionBackgroundDrawable.hasPattern()) {
                if (animationInProgress) {
                    offset = (int) emojiOffset;
                } else if (emojiHeight != 0) {
                    offset = emojiHeight;
                } else {
                    offset = backgroundTranslationY;
                }
            } else {
                offset = backgroundTranslationY != 0 ? 0 : -keyboardHeight;
            }
        }
        return getMeasuredHeight() - offset;
    }

    public int getHeightWithKeyboard() {
        return keyboardHeight + getMeasuredHeight();
    }

    public void setEmojiKeyboardHeight(int height) {
        emojiHeight = height;
    }

    public void setEmojiOffset(boolean animInProgress, float offset) {
        emojiOffset = offset;
        animationInProgress = animInProgress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundDrawable == null || skipBackgroundDrawing) {
            super.onDraw(canvas);
            return;
        }
        //int kbHeight = SharedConfig.smoothKeyboard ? 0 : keyboardHeight;
        Drawable newDrawable = getNewDrawable();
        if (newDrawable != backgroundDrawable && newDrawable != null) {
            if (Theme.isAnimatingColor()) {
                oldBackgroundDrawable = backgroundDrawable;
            }
            if (newDrawable instanceof MotionBackgroundDrawable) {
                MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) newDrawable;
                motionBackgroundDrawable.setParentView(this);
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
            if (drawable instanceof MotionBackgroundDrawable) {
                MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) drawable;
                if (motionBackgroundDrawable.hasPattern()) {
                    int actionBarHeight = (isActionBarVisible() ? ActionBar.getCurrentActionBarHeight() : 0) + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
                    int viewHeight = getRootView().getMeasuredHeight() - actionBarHeight;
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) drawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale * parallaxScale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale * parallaxScale);
                    int x = (getMeasuredWidth() - width) / 2 + (int) translationX;
                    int y = backgroundTranslationY + (viewHeight - height) / 2 + actionBarHeight + (int) translationY;
                    canvas.save();
                    canvas.clipRect(0, actionBarHeight, width, getMeasuredHeight() - bottomClip);
                    drawable.setBounds(x, y, x + width, y + height);
                    drawable.draw(canvas);
                    canvas.restore();
                } else {
                    if (bottomClip != 0) {
                        canvas.save();
                        canvas.clipRect(0, 0, getMeasuredWidth(), getRootView().getMeasuredHeight() - bottomClip);
                    }
                    motionBackgroundDrawable.setTranslationY(backgroundTranslationY);
                    int bottom = getMeasuredHeight() - backgroundTranslationY;
                    if (animationInProgress) {
                        bottom -= emojiOffset;
                    } else if (emojiHeight != 0) {
                        bottom -= emojiHeight;
                    }
                    drawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                    drawable.draw(canvas);
                    if (bottomClip != 0) {
                        canvas.restore();
                    }
                }
            } else if (drawable instanceof ColorDrawable) {
                if (bottomClip != 0) {
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - bottomClip);
                }
                drawable.setBounds(0, 0, getMeasuredWidth(), getRootView().getMeasuredHeight());
                drawable.draw(canvas);
                if (bottomClip != 0) {
                    canvas.restore();
                }
            } else if (drawable instanceof GradientDrawable) {
                if (bottomClip != 0) {
                    canvas.save();
                    canvas.clipRect(0, 0, getMeasuredWidth(), getRootView().getMeasuredHeight() - bottomClip);
                }
                drawable.setBounds(0, backgroundTranslationY, getMeasuredWidth(), backgroundTranslationY + getRootView().getMeasuredHeight());
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
                    drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getRootView().getMeasuredHeight() / scale));
                    drawable.draw(canvas);
                    canvas.restore();
                } else {
                    int actionBarHeight = (isActionBarVisible() ? ActionBar.getCurrentActionBarHeight() : 0) + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
                    int viewHeight = getRootView().getMeasuredHeight() - actionBarHeight;
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) drawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale * parallaxScale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale * parallaxScale);
                    int x = (getMeasuredWidth() - width) / 2 + (int) translationX;
                    int y = backgroundTranslationY + (viewHeight - height) / 2 + actionBarHeight + (int) translationY;
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

    protected AdjustPanLayoutHelper createAdjustPanLayoutHelper() {
        return null;
    }

    public void setSkipBackgroundDrawing(boolean skipBackgroundDrawing) {
        this.skipBackgroundDrawing = skipBackgroundDrawing;
        invalidate();
    }

    protected Drawable getNewDrawable() {
        return Theme.getCachedWallpaperNonBlocking();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == getBackgroundImage() || super.verifyDrawable(who);
    }
}
