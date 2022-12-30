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
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BlurSettingsBottomSheet;

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
    private INavigationLayout parentLayout;
    public AdjustPanLayoutHelper adjustPanLayoutHelper;
    private int emojiHeight;
    private float emojiOffset;
    private boolean animationInProgress;
    private boolean skipBackgroundDrawing;
    SnowflakesEffect snowflakesEffect;
    protected View backgroundView;


    //blur variables
    public boolean needBlur;
    public boolean needBlurBottom;
    public boolean blurIsRunning;
    public boolean blurGeneratingTuskIsRunning;
    BlurBitmap currentBitmap;
    BlurBitmap prevBitmap;
    public ArrayList<BlurBitmap> unusedBitmaps = new ArrayList<>(10);
    public ArrayList<View> blurBehindViews = new ArrayList<>();

    Matrix matrix = new Matrix();
    Matrix matrix2 = new Matrix();
    public Paint blurPaintTop = new Paint();
    public Paint blurPaintTop2 = new Paint();
    public Paint blurPaintBottom = new Paint();
    public Paint blurPaintBottom2 = new Paint();
    private Paint selectedBlurPaint;
    private Paint selectedBlurPaint2;
    float saturation;

    public float blurCrossfadeProgress;
    private final float DOWN_SCALE = 12f;
    private final int TOP_CLIP_OFFSET = (int) (10 + DOWN_SCALE * 2);
    private static DispatchQueue blurQueue;
    ValueAnimator blurCrossfade;
    public boolean invalidateBlur;
    int count;
    int times;
    int count2;
    int times2;
    //

    public void invalidateBlur() {
        invalidateBlur = true;
        invalidate();
    }


    public interface SizeNotifierFrameLayoutDelegate {
        void onSizeChanged(int keyboardHeight, boolean isWidthGreater);
    }

    public SizeNotifierFrameLayout(Context context) {
        this(context, null);
    }

    public SizeNotifierFrameLayout(Context context, INavigationLayout layout) {
        super(context);
        setWillNotDraw(false);
        parentLayout = layout;
        adjustPanLayoutHelper = createAdjustPanLayoutHelper();
        backgroundView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (backgroundDrawable == null || skipBackgroundDrawing) {
                    return;
                }
                Drawable newDrawable = getNewDrawable();
                if (newDrawable != backgroundDrawable && newDrawable != null) {
                    if (Theme.isAnimatingColor()) {
                        oldBackgroundDrawable = backgroundDrawable;
                    }
                    if (newDrawable instanceof MotionBackgroundDrawable) {
                        MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) newDrawable;
                        motionBackgroundDrawable.setParentView(backgroundView);
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
                            int bottom = (int) (getRootView().getMeasuredHeight() - backgroundTranslationY + translationY);
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
                        backgroundView.invalidate();
                    }
                }
            }
        };
        addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        checkLayerType();
    }

    public void setBackgroundImage(Drawable bitmap, boolean motion) {
        if (backgroundDrawable == bitmap) {
            return;
        }
        if (bitmap instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) bitmap;
            motionBackgroundDrawable.setParentView(backgroundView);
        }
        backgroundDrawable = bitmap;
        if (motion) {
            if (parallaxEffect == null) {
                parallaxEffect = new WallpaperParallaxEffect(getContext());
                parallaxEffect.setCallback((offsetX, offsetY, angle) -> {
                    translationX = offsetX;
                    translationY = offsetY;
                    bgAngle = angle;
                    backgroundView.invalidate();
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
        backgroundView.invalidate();
        checkLayerType();
    }

    private void checkLayerType() {
//        if (parallaxEffect == null && backgroundDrawable instanceof MotionBackgroundDrawable && SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH) {
//            backgroundView.setLayerType(LAYER_TYPE_HARDWARE, null);
//        } else {
//            backgroundView.setLayerType(LAYER_TYPE_NONE, null);
//        }
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
        if (value != bottomClip) {
            bottomClip = value;
            backgroundView.invalidate();
        }
    }

    public void setBackgroundTranslation(int translation) {
        if (translation != backgroundTranslationY) {
            backgroundTranslationY = translation;
            backgroundView.invalidate();
        }
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
        if (emojiHeight != height) {
            emojiHeight = height;
            backgroundView.invalidate();
        }
    }

    public void setEmojiOffset(boolean animInProgress, float offset) {
        if (emojiOffset != offset || animationInProgress != animInProgress) {
            emojiOffset = offset;
            animationInProgress = animInProgress;
            backgroundView.invalidate();
        }
    }

    private void checkSnowflake(Canvas canvas) {
        if (Theme.canStartHolidayAnimation() && !SharedConfig.getLiteMode().enabled()) {
            if (snowflakesEffect == null) {
                snowflakesEffect = new SnowflakesEffect(1);
            }
            snowflakesEffect.onDraw(backgroundView, canvas);
        }
    }

    protected boolean isActionBarVisible() {
        return true;
    }

    protected AdjustPanLayoutHelper createAdjustPanLayoutHelper() {
        return null;
    }

    public void setSkipBackgroundDrawing(boolean skipBackgroundDrawing) {
        if (this.skipBackgroundDrawing != skipBackgroundDrawing) {
            this.skipBackgroundDrawing = skipBackgroundDrawing;
            backgroundView.invalidate();
        }
    }

    protected Drawable getNewDrawable() {
        return Theme.getCachedWallpaperNonBlocking();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == getBackgroundImage() || super.verifyDrawable(who);
    }

    final BlurBackgroundTask blurBackgroundTask = new BlurBackgroundTask();

    public void startBlur() {
        if (!blurIsRunning || blurGeneratingTuskIsRunning || !invalidateBlur || !SharedConfig.chatBlurEnabled()) {
            return;
        }

        int blurAlpha = Color.alpha(Theme.getColor(Theme.key_chat_BlurAlpha));
        if (blurAlpha == 255) {
            return;
        }
        int lastW = getMeasuredWidth();
        int lastH = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(100);
        if (lastW == 0 || lastH == 0) {
            return;
        }
// TODO uncomment for support saturation in blur
//        if (this.saturation != BlurSettingsBottomSheet.saturation) {
//            this.saturation = BlurSettingsBottomSheet.saturation;
//            ColorMatrix colorMatrix = new ColorMatrix();
//            colorMatrix.setSaturation(saturation * 5);
//            blurPaintTop.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//            blurPaintTop2.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//            blurPaintBottom.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//            blurPaintBottom2.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//        }

        invalidateBlur = false;
        blurGeneratingTuskIsRunning = true;

        int bitmapH = (int) (lastH / DOWN_SCALE) + TOP_CLIP_OFFSET;
        int bitmapW = (int) (lastW / DOWN_SCALE);

        long time = System.currentTimeMillis();
        BlurBitmap bitmap = null;
        if (unusedBitmaps.size() > 0) {
            bitmap = unusedBitmaps.remove(unusedBitmaps.size() - 1);
        }

        if (bitmap == null) {
            bitmap = new BlurBitmap();
            bitmap.topBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
            bitmap.topCanvas = new Canvas(bitmap.topBitmap);

            if (needBlurBottom) {
                bitmap.bottomBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
                bitmap.bottomCanvas = new Canvas(bitmap.bottomBitmap);
            }
        } else {
            bitmap.topBitmap.eraseColor(Color.TRANSPARENT);
            if (bitmap.bottomBitmap != null) {
                bitmap.bottomBitmap.eraseColor(Color.TRANSPARENT);
            }
        }

        BlurBitmap finalBitmap = bitmap;

        float sX = (float) finalBitmap.topBitmap.getWidth() / (float) lastW;
        float sY = (float) (finalBitmap.topBitmap.getHeight() - TOP_CLIP_OFFSET) / (float) lastH;
        finalBitmap.topCanvas.save();
        finalBitmap.pixelFixOffset = getScrollOffset() % (int) (DOWN_SCALE * 2);

        finalBitmap.topCanvas.clipRect(1, 10 * sY, finalBitmap.topBitmap.getWidth(), finalBitmap.topBitmap.getHeight() - 1);
        finalBitmap.topCanvas.scale(sX, sY);
        finalBitmap.topCanvas.translate(0, 10 * sY + finalBitmap.pixelFixOffset);

        finalBitmap.topScaleX = 1f / sX;
        finalBitmap.topScaleY = 1f / sY;

        drawList(finalBitmap.topCanvas, true);
        finalBitmap.topCanvas.restore();

        if (needBlurBottom) {
            sX = (float) finalBitmap.bottomBitmap.getWidth() / (float) lastW;
            sY = (float) (finalBitmap.bottomBitmap.getHeight() - TOP_CLIP_OFFSET) / (float) lastH;
            finalBitmap.needBlurBottom = true;
            finalBitmap.bottomOffset = getBottomOffset() - lastH;
            finalBitmap.drawnLisetTranslationY = getBottomOffset();
            finalBitmap.bottomCanvas.save();
            finalBitmap.bottomCanvas.clipRect(1, 10 * sY, finalBitmap.bottomBitmap.getWidth(), finalBitmap.bottomBitmap.getHeight() - 1);
            finalBitmap.bottomCanvas.scale(sX, sY);
            finalBitmap.bottomCanvas.translate(0, 10 * sY - finalBitmap.bottomOffset + finalBitmap.pixelFixOffset);
            finalBitmap.bottomScaleX = 1f / sX;
            finalBitmap.bottomScaleY = 1f / sY;

            drawList(finalBitmap.bottomCanvas, false);
            finalBitmap.bottomCanvas.restore();
        } else {
            finalBitmap.needBlurBottom = false;
        }


        times2 += System.currentTimeMillis() - time;
        count2++;
        if (count2 >= 20) {
            count2 = 0;
            times2 = 0;
        }

        if (blurQueue == null) {
            blurQueue = new DispatchQueue("BlurQueue");
        }
        blurBackgroundTask.radius = (int) ((int) (Math.max(6, Math.max(lastH, lastW) / 180) * 2.5f) * BlurSettingsBottomSheet.blurRadius);
        blurBackgroundTask.finalBitmap = finalBitmap;
        blurQueue.postRunnable(blurBackgroundTask);
    }

    private class BlurBackgroundTask implements Runnable {

        int radius;
        BlurBitmap finalBitmap;

        @Override
        public void run() {
            long time = System.currentTimeMillis();

            Utilities.stackBlurBitmap(finalBitmap.topBitmap, radius);
            if (finalBitmap.needBlurBottom && finalBitmap.bottomBitmap != null) {
                Utilities.stackBlurBitmap(finalBitmap.bottomBitmap, radius);
            }
            times += System.currentTimeMillis() - time;
            count++;
            if (count > 1000) {
                FileLog.d("chat blur generating average time" + (times / (float) count));
                count = 0;
                times = 0;
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (!blurIsRunning) {
                    if (finalBitmap != null) {
                        finalBitmap.recycle();
                    }
                    blurGeneratingTuskIsRunning = false;
                    return;
                }
                prevBitmap = currentBitmap;
                BlurBitmap oldBitmap = currentBitmap;
                blurPaintTop2.setShader(blurPaintTop.getShader());
                blurPaintBottom2.setShader(blurPaintBottom.getShader());

                BitmapShader bitmapShader = new BitmapShader(finalBitmap.topBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                blurPaintTop.setShader(bitmapShader);

                if (finalBitmap.needBlurBottom && finalBitmap.bottomBitmap != null) {
                    bitmapShader = new BitmapShader(finalBitmap.bottomBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    blurPaintBottom.setShader(bitmapShader);
                }

                if (blurCrossfade != null) {
                    blurCrossfade.cancel();
                }
                blurCrossfadeProgress = 0;
                blurCrossfade = ValueAnimator.ofFloat(0, 1f);
                blurCrossfade.addUpdateListener(valueAnimator -> {
                    blurCrossfadeProgress = (float) valueAnimator.getAnimatedValue();
                    invalidateBlurredViews();
                });
                blurCrossfade.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        blurCrossfadeProgress = 1f;
                        unusedBitmaps.add(oldBitmap);
                        blurPaintTop2.setShader(null);
                        blurPaintBottom2.setShader(null);
                        invalidateBlurredViews();
                        super.onAnimationEnd(animation);
                    }
                });
                blurCrossfade.setDuration(50);
                blurCrossfade.start();
                invalidateBlurredViews();
                currentBitmap = finalBitmap;

                AndroidUtilities.runOnUIThread(() -> {
                    blurGeneratingTuskIsRunning = false;
                    startBlur();
                }, 16);
            });
        }
    }

    public void invalidateBlurredViews() {
        for (int i = 0; i < blurBehindViews.size(); i++) {
            blurBehindViews.get(i).invalidate();
        }
    }

    protected float getBottomOffset() {
        return getMeasuredHeight();
    }

    protected float getListTranslationY() {
        return 0f;
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

    public boolean blurWasDrawn() {
        return SharedConfig.chatBlurEnabled() && currentBitmap != null;
    }

    public void drawBlurRect(Canvas canvas, float y, Rect rectTmp, Paint blurScrimPaint, boolean top) {
        int blurAlpha = Color.alpha(Theme.getColor(Theme.key_chat_BlurAlpha));
        if (currentBitmap == null || !SharedConfig.chatBlurEnabled()) {
            canvas.drawRect(rectTmp, blurScrimPaint);
            return;
        }
        updateBlurShaderPosition(y, top);
        blurScrimPaint.setAlpha(255);
        if (blurCrossfadeProgress != 1f && selectedBlurPaint2.getShader() != null) {
            canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, selectedBlurPaint2);
            canvas.saveLayerAlpha(rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom, (int) (blurCrossfadeProgress * 255), Canvas.ALL_SAVE_FLAG);
            canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, selectedBlurPaint);
            canvas.restore();
        } else {
            canvas.drawRect(rectTmp, blurScrimPaint);
            canvas.drawRect(rectTmp, selectedBlurPaint);
        }

        blurScrimPaint.setAlpha(blurAlpha);
        canvas.drawRect(rectTmp, blurScrimPaint);
    }

    public void drawBlurCircle(Canvas canvas, float viewY, float cx, float cy, float radius, Paint blurScrimPaint, boolean top) {
        int blurAlpha = Color.alpha(Theme.getColor(Theme.key_chat_BlurAlpha));
        if (currentBitmap == null || !SharedConfig.chatBlurEnabled()) {
            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
            return;
        }
        updateBlurShaderPosition(viewY, top);
        blurScrimPaint.setAlpha(255);
        if (blurCrossfadeProgress != 1f && selectedBlurPaint2.getShader() != null) {
            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
            canvas.drawCircle(cx, cy, radius, selectedBlurPaint2);
            canvas.saveLayerAlpha(cx - radius, cy - radius, cx + radius, cy + radius, (int) (blurCrossfadeProgress * 255), Canvas.ALL_SAVE_FLAG);
            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
            canvas.drawCircle(cx, cy, radius, selectedBlurPaint);
            canvas.restore();
        } else {
            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
            canvas.drawCircle(cx, cy, radius, selectedBlurPaint);
        }

        blurScrimPaint.setAlpha(blurAlpha);
        canvas.drawCircle(cx, cy, radius, blurScrimPaint);
    }

    private void updateBlurShaderPosition(float viewY, boolean top) {
        selectedBlurPaint = top ? blurPaintTop : blurPaintBottom;
        selectedBlurPaint2 = top ? blurPaintTop2 : blurPaintBottom2;

        if (top) {
            viewY += getTranslationY();
        }

        if (selectedBlurPaint.getShader() != null) {
            matrix.reset();
            matrix2.reset();
            if (!top) {
                float y1 = -viewY + currentBitmap.bottomOffset - currentBitmap.pixelFixOffset - TOP_CLIP_OFFSET - (currentBitmap.drawnLisetTranslationY - (getBottomOffset() + getListTranslationY()));
                matrix.setTranslate(0, y1);
                matrix.preScale(currentBitmap.bottomScaleX, currentBitmap.bottomScaleY);

                if (prevBitmap != null) {
                    y1 = -viewY + prevBitmap.bottomOffset - prevBitmap.pixelFixOffset - TOP_CLIP_OFFSET - (prevBitmap.drawnLisetTranslationY - (getBottomOffset() + getListTranslationY()));
                    matrix2.setTranslate(0, y1);
                    matrix2.preScale(prevBitmap.bottomScaleX, prevBitmap.bottomScaleY);
                }
            } else {
                matrix.setTranslate(0, -viewY - currentBitmap.pixelFixOffset - TOP_CLIP_OFFSET);
                matrix.preScale(currentBitmap.topScaleX, currentBitmap.topScaleY);

                if (prevBitmap != null) {
                    matrix2.setTranslate(0, -viewY - prevBitmap.pixelFixOffset - TOP_CLIP_OFFSET);
                    matrix2.preScale(prevBitmap.topScaleX, prevBitmap.topScaleY);
                }
            }

            selectedBlurPaint.getShader().setLocalMatrix(matrix);
            if (selectedBlurPaint2.getShader() != null) {
                selectedBlurPaint2.getShader().setLocalMatrix(matrix);
            }
        }
    }

    protected float getBottomTranslation() {
        return 0;
    }

    private static class BlurBitmap {
        public boolean needBlurBottom;
        int pixelFixOffset;
        Canvas topCanvas;
        Bitmap topBitmap;
        float topScaleX, topScaleY;
        float bottomScaleX, bottomScaleY;
        float bottomOffset;
        float drawnLisetTranslationY;

        Canvas bottomCanvas;
        Bitmap bottomBitmap;

        public void recycle() {
            topBitmap.recycle();
            if (bottomBitmap != null) {
                bottomBitmap.recycle();
            }
        }
    }

}
