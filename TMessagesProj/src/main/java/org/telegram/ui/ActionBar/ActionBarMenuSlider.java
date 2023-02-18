package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MotionBackgroundDrawable;

public class ActionBarMenuSlider extends FrameLayout {

    private static final float BLUR_RADIUS = 8f;

    private float value = .5f;
    private Utilities.Callback2<Float, Boolean> onValueChange;

    private AnimatedFloat blurBitmapAlpha = new AnimatedFloat(1, this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Matrix blurBitmapMatrix;

    private int[] location = new int[2];

    private float roundRadiusDp = 6f;
    private boolean drawShadow;

    private Theme.ResourcesProvider resourcesProvider;

    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint brightenBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint darkenBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pseudoBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean blurIsInChat = true;

    private LinearGradient pseudoBlurGradient;
    private int pseudoBlurColor1, pseudoBlurColor2;
    private Matrix pseudoBlurMatrix;
    private int pseudoBlurWidth;

    public ActionBarMenuSlider(Context context) {
        this(context, null);
    }

    public ActionBarMenuSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        shadowPaint.setColor(Color.TRANSPARENT);
        shadowPaint.setShadowLayer(dpf2(1.33f), 0, dpf2(.33f), 0x3f000000);
        setDrawShadow(true);

        ColorMatrix colorMatrix = new ColorMatrix();
        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, -0.4f);
        AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, .1f);
        pseudoBlurPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        darkenBlurPaint.setColor(Theme.multAlpha(0xff000000, .025f));
        brightenBlurPaint.setColor(Theme.multAlpha(0xffffffff, .35f));
    }

    private ValueAnimator valueAnimator;

    public void setValue(float value, boolean animated) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }

        if (!animated) {
            this.value = MathUtils.clamp(value, 0, 1);
            invalidate();
        } else {
            final float newValue = MathUtils.clamp(value, 0, 1);
            valueAnimator = ValueAnimator.ofFloat(this.value, newValue);
            valueAnimator.addUpdateListener(anm -> {
                ActionBarMenuSlider.this.value = (float) anm.getAnimatedValue();
                invalidate();
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    valueAnimator = null;
                    ActionBarMenuSlider.this.value = newValue;
                    invalidate();
                }
            });
            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            valueAnimator.setDuration(320);
            valueAnimator.start();
        }
    }

    private void updateValue(float value, boolean isFinal) {
        setValue(value, false);
        if (onValueChange != null) {
            onValueChange.run(this.value, isFinal);
        }
    }

    public void setOnValueChange(Utilities.Callback2<Float, Boolean> onValueChange) {
        this.onValueChange = onValueChange;
    }

    public void setDrawShadow(boolean draw) {
        drawShadow = draw;
        final int pad = drawShadow ? dp(8) : 0;
        setPadding(pad, pad, pad, pad);
        invalidate();
    }

    public void setRoundRadiusDp(float roundRadiusDp) {
        this.roundRadiusDp = roundRadiusDp;
        invalidate();
    }

    private boolean preparingBlur = false;
    private Runnable prepareBlur = () -> {
        preparingBlur = true;
        AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
            preparingBlur = false;
            blurBitmapShader = new BitmapShader(blurBitmap = bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            if (blurBitmapMatrix == null) {
                blurBitmapMatrix = new Matrix();
            } else {
                blurBitmapMatrix.reset();
            }
            blurBitmapMatrix.postScale(BLUR_RADIUS, BLUR_RADIUS);
            blurBitmapMatrix.postTranslate(-location[0], -location[1]);
            blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
            blurPaint.setShader(blurBitmapShader);
            ColorMatrix colorMatrix = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, -.2f);
            blurPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            invalidate();
        }, BLUR_RADIUS);
    };

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) + getPaddingRight() + getPaddingLeft(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(44) + getPaddingTop() + getPaddingBottom(), MeasureSpec.EXACTLY));

        final boolean canDoBlur = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH && !SharedConfig.getLiteMode().enabled;
        if (blurBitmap == null && !preparingBlur && canDoBlur) {
            this.prepareBlur.run();
//            removeCallbacks(this.prepareBlur);
//            post(this.prepareBlur);
        }
    }

    public void invalidateBlur() {
        invalidateBlur(true);
    }

    public void invalidateBlur(boolean isInChat) {
        blurIsInChat = isInChat;

        blurPaint.setShader(null);
        blurBitmapShader = null;
        if (blurBitmap != null) {
            blurBitmap.recycle();
            blurBitmap = null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        getLocationOnScreen(location);
        if (blurBitmapMatrix != null) {
            blurBitmapMatrix.reset();
            blurBitmapMatrix.postScale(BLUR_RADIUS, BLUR_RADIUS);
            blurBitmapMatrix.postTranslate(-location[0], -location[1]);
            if (blurBitmapShader != null) {
                blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
                invalidate();
            }
        }
        updatePseudoBlurColors();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        if (drawShadow) {
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), shadowPaint);
        }

        final float blurAlpha = blurBitmapAlpha.set(blurBitmap != null ? 1 : 0);
        if (blurAlpha < 1f) {
            if (pseudoBlurMatrix == null || pseudoBlurWidth != (int) AndroidUtilities.rectTmp.width()) {
                if (pseudoBlurMatrix == null) {
                    pseudoBlurMatrix = new Matrix();
                } else {
                    pseudoBlurMatrix.reset();
                }
                pseudoBlurMatrix.postScale(pseudoBlurWidth = (int) AndroidUtilities.rectTmp.width(), 1);
                pseudoBlurGradient.setLocalMatrix(pseudoBlurMatrix);
            }

            pseudoBlurPaint.setAlpha((int) (0xFF * (1f - blurAlpha)));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), pseudoBlurPaint);
        }

        if (blurBitmap != null && value < 1 && blurAlpha > 0) {
            blurPaint.setAlpha((int) (0xFF * blurAlpha));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), blurPaint);
        }

        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), brightenBlurPaint);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), darkenBlurPaint);

//        fillPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        fillPaint.setColor(Color.WHITE);
        if (value < 1) {
            canvas.save();
            canvas.clipRect(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) * value, getHeight() - getPaddingBottom());
        }
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), fillPaint);
        if (value < 1) {
            canvas.restore();
        }
    }

    private Pair<Integer, Integer> getBitmapGradientColors(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        final float sx1 = location[0] / (float) AndroidUtilities.displaySize.x;
        final float sx2 = (location[0] + getMeasuredWidth()) / (float) AndroidUtilities.displaySize.x;
        final float sy = (location[1] - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight()) / (float) AndroidUtilities.displaySize.y;

        final int x1 = (int) (sx1 * bitmap.getWidth());
        final int x2 = (int) (sx2 * bitmap.getWidth());
        final int y = (int) (sy * bitmap.getHeight());

        if (x1 < 0 || x1 >= bitmap.getWidth() || x2 < 0 || x2 >= bitmap.getWidth() || y < 0 || y >= bitmap.getHeight()) {
            return null;
        }

        return new Pair<>(
            bitmap.getPixel(x1, y),
            bitmap.getPixel(x2, y)
        );
    }

    private void updatePseudoBlurColors() {
        int fromColor, toColor;

        if (blurIsInChat) {
            Drawable drawable = Theme.getCachedWallpaper();
            if (drawable instanceof ColorDrawable) {
                fromColor = toColor = ((ColorDrawable) drawable).getColor();
            } else {
                Bitmap bitmap = null;
                if (drawable instanceof MotionBackgroundDrawable) {
                    bitmap = ((MotionBackgroundDrawable) drawable).getBitmap();
                } else if (drawable instanceof BitmapDrawable) {
                    bitmap = ((BitmapDrawable) drawable).getBitmap();
                }

                Pair<Integer, Integer> colors = getBitmapGradientColors(bitmap);
                if (colors != null) {
                    fromColor = colors.first;
                    toColor = colors.second;
                } else {
                    fromColor = toColor = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), .25f);
                }
            }
        } else {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            if (!Theme.isCurrentThemeDark()) {
                color = Theme.blendOver(color, Theme.multAlpha(0xff000000, .18f));
            }
            fromColor = toColor = color;
        }

        if (pseudoBlurGradient == null || pseudoBlurColor1 != fromColor || pseudoBlurColor2 != toColor) {
            pseudoBlurGradient = new LinearGradient(0, 0, 1, 0, new int[] { pseudoBlurColor1 = fromColor, pseudoBlurColor2 = toColor }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
            pseudoBlurPaint.setShader(pseudoBlurGradient);
        }
    }

    private float fromX;
    private float fromValue;

    private boolean dragging;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX() - getPaddingLeft();

        final int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            dragging = true;
            fromX = x;
            fromValue = value;
            return true;
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (action == MotionEvent.ACTION_UP) {
                dragging = false;
            }
            final float value = fromValue + (x - fromX) / Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
            updateValue(value, !dragging);
            return true;
        }

        return super.onTouchEvent(event);
    }
}
