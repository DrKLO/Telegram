/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

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
    SnowflakesEffect snowflakesEffect;

    public void invalidateBlur() {
        invalidateBlur = true;
    }


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
                    checkSnowflake(canvas);
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
                checkSnowflake(canvas);
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
                checkSnowflake(canvas);
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
                    checkSnowflake(canvas);
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
                    checkSnowflake(canvas);
                    canvas.restore();
                }
            }
            if (a == 0 && oldBackgroundDrawable != null && themeAnimationValue >= 1.0f) {
                oldBackgroundDrawable = null;
                invalidate();
            }
        }
    }

    private void checkSnowflake(Canvas canvas) {
        if (Theme.canStartHolidayAnimation()) {
            if (snowflakesEffect == null) {
                snowflakesEffect = new SnowflakesEffect(1);
            }
            snowflakesEffect.onDraw(this, canvas);
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


    public boolean needBlur;
    public boolean blurIsRunning;
    public boolean blurGeneratingTuskIsRunning;
    BlurBitmap currentBitmap;
    public ArrayList<BlurBitmap> unusedBitmaps = new ArrayList<>(10);
    public ArrayList<View> blurBehindViews = new ArrayList<>();

    Matrix matrix = new Matrix();
    public Paint blurPaintTop = new Paint();
    public Paint blurPaintTop2 = new Paint();
    public Paint blurPaintBottom = new Paint();
    public Paint blurPaintBottom2 = new Paint();
    public float blurCrossfadeProgress;
    private final float DOWN_SCALE = 12f;
    private static DispatchQueue blurQueue;
    ValueAnimator blurCrossfade;
    public boolean invalidateBlur;

    int count;
    int times;

    public void startBlur() {
        if (!blurIsRunning || blurGeneratingTuskIsRunning || !invalidateBlur || !SharedConfig.chatBlurEnabled()) {
            return;
        }
        invalidateBlur = false;
        blurGeneratingTuskIsRunning = true;
        int lastW = getMeasuredWidth();
        int lastH = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(100);

        int bitmapH = (int) (lastH / DOWN_SCALE) + 10;
        int bitmapW = (int) (lastW / DOWN_SCALE);

        BlurBitmap bitmap = null;
        if (unusedBitmaps.size() > 0) {
            bitmap = unusedBitmaps.remove(unusedBitmaps.size() - 1);
        }

        if (bitmap == null) {
            bitmap = new BlurBitmap();
            bitmap.topBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
            bitmap.topCanvas = new Canvas(bitmap.topBitmap);

            bitmap.bottomBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
            bitmap.bottomCanvas = new Canvas(bitmap.bottomBitmap);
        }
        bitmap.topBitmap.eraseColor(Color.TRANSPARENT);
        bitmap.bottomBitmap.eraseColor(Color.TRANSPARENT);

        BlurBitmap finalBitmap = bitmap;

        float sX = (float) finalBitmap.topBitmap.getWidth() / (float) lastW;
        float sY = (float) (finalBitmap.topBitmap.getHeight() - 10) / (float) lastH;
        finalBitmap.topCanvas.save();
        finalBitmap.topCanvas.clipRect(0, 10 * sY, finalBitmap.topBitmap.getWidth(), finalBitmap.topBitmap.getHeight());
        finalBitmap.topCanvas.scale(sX, sY);
        finalBitmap.topScaleX = 1f / sX;
        finalBitmap.topScaleY = 1f / sY;

       // finalBitmap.pixelFixOffset = getScrollOffset() % (int) DOWN_SCALE;
        finalBitmap.topCanvas.translate(0, finalBitmap.pixelFixOffset);
        drawList(finalBitmap.topCanvas, true);
        finalBitmap.topCanvas.restore();

        sX = (float) finalBitmap.bottomBitmap.getWidth() / (float) lastW;
        sY = (float) (finalBitmap.bottomBitmap.getHeight() - 10) / (float) lastH;
        finalBitmap.bottomOffset = getBottomOffset() - lastH;
        finalBitmap.bottomCanvas.save();
        finalBitmap.bottomCanvas.clipRect(0, 10 * sY, finalBitmap.bottomBitmap.getWidth(), finalBitmap.bottomBitmap.getHeight());
        finalBitmap.bottomCanvas.scale(sX, sY);
        finalBitmap.bottomCanvas.translate(0, 10 - finalBitmap.bottomOffset);
        finalBitmap.bottomScaleX = 1f / sX;
        finalBitmap.bottomScaleY = 1f / sY;

        drawList(finalBitmap.bottomCanvas, false);
        finalBitmap.bottomCanvas.restore();


        int radius = (int) (Math.max(6, Math.max(lastH, lastW) / 180) * 2.5f);
        if (blurQueue == null) {
            blurQueue = new DispatchQueue("BlurQueue");
        }
        blurQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                long time = System.currentTimeMillis();
                Utilities.stackBlurBitmap(finalBitmap.topBitmap, radius);
                Utilities.stackBlurBitmap(finalBitmap.bottomBitmap, radius);
                times += System.currentTimeMillis() - time;
                count++;
                if (count > 1000) {
                    FileLog.d("chat blur generating average time" + (times / (float) count));
                    count = 0;
                    times = 0;
                }

                AndroidUtilities.runOnUIThread(() -> {
                    BlurBitmap oldBitmap = currentBitmap;
                    blurPaintTop2.setShader(blurPaintTop.getShader());
                    blurPaintBottom2.setShader(blurPaintBottom.getShader());

                    BitmapShader bitmapShader = new BitmapShader(finalBitmap.topBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    blurPaintTop.setShader(bitmapShader);

                    bitmapShader = new BitmapShader(finalBitmap.bottomBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    blurPaintBottom.setShader(bitmapShader);

                    blurCrossfadeProgress = 0;
                    if (blurCrossfade != null) {
                        blurCrossfade.cancel();
                    }
                    blurCrossfade = ValueAnimator.ofFloat(0, 1f);
                    blurCrossfade.addUpdateListener(valueAnimator -> {
                        blurCrossfadeProgress = (float) valueAnimator.getAnimatedValue();
                        for (int i = 0; i < blurBehindViews.size(); i++) {
                            blurBehindViews.get(i).invalidate();
                        }
                    });
                    blurCrossfade.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            unusedBitmaps.add(oldBitmap);
                            super.onAnimationEnd(animation);
                        }
                    });
                    blurCrossfade.setDuration(50);
                    blurCrossfade.start();
                    for (int i = 0; i < blurBehindViews.size(); i++) {
                        blurBehindViews.get(i).invalidate();
                    }
                    currentBitmap = finalBitmap;

                    AndroidUtilities.runOnUIThread(() -> {
                        blurGeneratingTuskIsRunning = false;
                        startBlur();
                    }, 32);

                });
            }
        });
    }

    protected float getBottomOffset() {
        return getMeasuredHeight();
    }

    protected Theme.ResourcesProvider getResourceProvider() {
        return null;
    }

    protected void drawList(Canvas blurCanvas, boolean top) {


    }

    protected int getScrollOffset() {
        return 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (blurIsRunning) {
            startBlur();
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (needBlur && !blurIsRunning) {
            blurIsRunning = true;
            invalidateBlur = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurPaintTop.setShader(null);
        blurPaintTop2.setShader(null);
        blurPaintBottom.setShader(null);
        blurPaintBottom2.setShader(null);
        if (blurCrossfade != null) {
            blurCrossfade.cancel();
        }
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        for (int i = 0; i < unusedBitmaps.size(); i++) {
            if (unusedBitmaps.get(i) != null) {
                unusedBitmaps.get(i).recycle();
            }
        }
        unusedBitmaps.clear();
        blurIsRunning = false;
    }

    public void drawBlur(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
        if (currentBitmap == null || !SharedConfig.chatBlurEnabled()) {
            canvas.drawRect(rectTmp, blurScrimPaint);
            return;
        }
        Paint blurPaint = top ? blurPaintTop : blurPaintBottom;
        Paint blurPaint2 = top ? blurPaintTop2 : blurPaintBottom2;

        if (blurPaint.getShader() != null) {
            matrix.reset();
            if (!top) {
                matrix.setTranslate(0, -y + currentBitmap.bottomOffset - currentBitmap.pixelFixOffset);
                matrix.preScale(currentBitmap.bottomScaleX, currentBitmap.bottomScaleY);
            } else {
                matrix.setTranslate(0, -y);
                matrix.preScale(currentBitmap.topScaleX, currentBitmap.topScaleY);
            }


            blurPaint.getShader().setLocalMatrix(matrix);
            if (blurPaint2.getShader() != null) {
                blurPaint2.getShader().setLocalMatrix(matrix);
            }
        }
        if (blurCrossfadeProgress != 1f && blurPaint2.getShader() != null) {
            canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, blurPaint2);
            canvas.saveLayerAlpha(rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom, (int) (blurCrossfadeProgress * 255), Canvas.ALL_SAVE_FLAG);
//        blurScrimPaint.setAlpha((int) (blurCrossfadeProgress * 255));
//        blurPaint.setAlpha((int) (blurCrossfadeProgress * 255));
            canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, blurPaint);
            canvas.restore();
        } else {
            canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, blurPaint);
        }


        blurScrimPaint.setAlpha(Color.alpha(Theme.getColor(Theme.key_chat_BlurAlpha)));
        canvas.drawRect(rectTmp, blurScrimPaint);
    }

    private static class BlurBitmap {
        int pixelFixOffset;
        Canvas topCanvas;
        Bitmap topBitmap;
        float topScaleX, topScaleY;
        float bottomScaleX, bottomScaleY;
        float bottomOffset;

        Canvas bottomCanvas;
        Bitmap bottomBitmap;

        public void recycle() {
            topBitmap.recycle();
            bottomBitmap.recycle();
        }
    }

}
