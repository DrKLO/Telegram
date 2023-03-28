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
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FloatSeekBarAccessibilityDelegate;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.SeekBarAccessibilityDelegate;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.SpeedIconDrawable;

public class ActionBarMenuSlider extends FrameLayout {

    private static final float BLUR_RADIUS = 8f;

    private float value = .5f;
    private Utilities.Callback2<Float, Boolean> onValueChange;

    private AnimatedTextView.AnimatedTextDrawable textDrawable;

    private AnimatedFloat blurBitmapAlpha = new AnimatedFloat(1, this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Matrix blurBitmapMatrix;

    private int[] location = new int[2];

    private float roundRadiusDp = 0;
    private boolean drawShadow, drawBlur;

    protected Theme.ResourcesProvider resourcesProvider;

    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint brightenBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint darkenBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pseudoBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean backgroundDark;

    private boolean blurIsInChat = true;

    private LinearGradient pseudoBlurGradient;
    private int pseudoBlurColor1, pseudoBlurColor2;
    private Matrix pseudoBlurMatrix;
    private int pseudoBlurWidth;

    public ActionBarMenuSlider(Context context) {
        this(context, null);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    public ActionBarMenuSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true) {
            @Override
            public void invalidateSelf() {
                ActionBarMenuSlider.this.invalidate();
            }
        };
        textDrawable.setCallback(this);
        textDrawable.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textDrawable.setAnimationProperties(.3f, 0, 165, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextSize(AndroidUtilities.dpf2(14));
        textDrawable.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
        textDrawable.getPaint().setStrokeWidth(AndroidUtilities.dpf2(.3f));
        textDrawable.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        shadowPaint.setColor(Color.TRANSPARENT);
        shadowPaint.setShadowLayer(dpf2(1.33f), 0, dpf2(.33f), 0x3f000000);

        ColorMatrix colorMatrix = new ColorMatrix();
        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, -0.4f);
        AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, .1f);
        pseudoBlurPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        backgroundDark = AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= 0.721f;
        textDrawable.setTextColor(backgroundDark ? 0xffffffff : 0xff000000);
        darkenBlurPaint.setColor(Theme.multAlpha(0xff000000, .025f));
        brightenBlurPaint.setColor(Theme.multAlpha(0xffffffff, .35f));
    }

    public float getValue() {
        return value;
    }

    private ValueAnimator valueAnimator;

    public void setValue(float value, boolean animated) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }

        value = MathUtils.clamp(value, 0, 1);

        if (!animated) {
            this.value = value;
            invalidate();
        } else {
            final float newValue = value;
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
            valueAnimator.setDuration(220);
            valueAnimator.start();
        }

        String stringValue = getStringValue(value);
        if (stringValue != null && !TextUtils.equals(textDrawable.getText(), stringValue)) {
            textDrawable.cancelAnimation();
            textDrawable.setText(stringValue, true);
        }
        fillPaint.setColor(getColorValue(value));
    }

    public void setBackgroundColor(int color) {
        backgroundPaint.setColor(color);
        backgroundDark = AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= 0.721f;
        textDrawable.setTextColor(backgroundDark ? 0xffffffff : 0xff000000);
    }

    public void setTextColor(int color) {
        textDrawable.setTextColor(color);
    }

    protected String getStringValue(float value) {
        return null;
    }

    protected int getColorValue(float value) {
        return Color.WHITE;
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

    public void setDrawBlur(boolean draw) {
        drawBlur = draw;
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
        super.onMeasure(drawShadow ? MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) + getPaddingRight() + getPaddingLeft(), MeasureSpec.EXACTLY) : widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(44) + getPaddingTop() + getPaddingBottom(), MeasureSpec.EXACTLY));

        final boolean canDoBlur = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
        if (drawBlur && blurBitmap == null && !preparingBlur && canDoBlur) {
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

        if (drawBlur) {
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

            fillPaint.setColor(Color.WHITE);
        } else {
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), backgroundPaint);
        }

        if (!backgroundDark) {
            drawText(canvas, false);
        }

        if (value < 1) {
            canvas.save();
            canvas.clipRect(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) * value, getHeight() - getPaddingBottom());
        }
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(roundRadiusDp), dp(roundRadiusDp), fillPaint);

        if (!backgroundDark) {
            drawText(canvas, true);
        }

        if (value < 1) {
            canvas.restore();
        }

        if (backgroundDark) {
            drawText(canvas, false);
        }
    }

    private ColorFilter whiteColorFilter;

    private void drawText(Canvas canvas, boolean white) {
        textDrawable.setColorFilter(white ? (whiteColorFilter == null ? whiteColorFilter = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) : whiteColorFilter) : null);
        textDrawable.setBounds(getPaddingLeft() + dp(20), getMeasuredHeight() / 2, getMeasuredWidth() - getPaddingRight() - dp(20), getMeasuredHeight() / 2);
        textDrawable.draw(canvas);
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
    private long tapStart;
    private boolean dragging;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX() - getPaddingLeft();

        final int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            dragging = true;
            fromX = x;
            fromValue = value;
            tapStart = System.currentTimeMillis();
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (action == MotionEvent.ACTION_UP) {
                dragging = false;
                if (System.currentTimeMillis() - tapStart < ViewConfiguration.getTapTimeout()) {
                    final float value = (x - getPaddingLeft()) / (getWidth() - getPaddingLeft() - getPaddingRight());
                    if (onValueChange != null) {
                        onValueChange.run(value, true);
                    }
                    return true;
                }
            }
            final float value = fromValue + (x - fromX) / Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
            updateValue(value, !dragging);
        }

        return true;
    }

    public static class SpeedSlider extends ActionBarMenuSlider {

        private final SeekBarAccessibilityDelegate seekBarAccessibilityDelegate;

        public static final float MIN_SPEED = 0.2f;
        public static final float MAX_SPEED = 2.5f;

        public SpeedSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            setFocusable(true);
            setFocusableInTouchMode(true);

            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setAccessibilityDelegate(seekBarAccessibilityDelegate = new FloatSeekBarAccessibilityDelegate(false) {
                @Override
                public float getProgress() {
                    return getSpeed();
                }

                @Override
                public void setProgress(float progress) {
                    setSpeed(progress, true);
                }

                @Override
                protected float getMinValue() {
                    return MIN_SPEED;
                }

                @Override
                protected float getMaxValue() {
                    return MAX_SPEED;
                }

                @Override
                protected float getDelta() {
                    return 0.2f;
                }

                @Override
                public CharSequence getContentDescription(View host) {
                    return SpeedIconDrawable.formatNumber(getSpeed()) + "x  " + LocaleController.getString("AccDescrSpeedSlider", R.string.AccDescrSpeedSlider);
                }
            });
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            seekBarAccessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || seekBarAccessibilityDelegate.performAccessibilityActionInternal(this, action, arguments);
        }

        public float getSpeed(float value) {
            return MIN_SPEED + (MAX_SPEED - MIN_SPEED) * value;
        }

        public float getSpeed() {
            return getSpeed(getValue());
        }

        public void setSpeed(float speed, boolean animated) {
            setValue((speed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED), animated);
        }

        @Override
        protected String getStringValue(float value) {
            return SpeedIconDrawable.formatNumber(MIN_SPEED + value * (MAX_SPEED - MIN_SPEED)) + "x";
        }

        @Override
        protected int getColorValue(float value) {
            final float speed = MIN_SPEED + value * (MAX_SPEED - MIN_SPEED);
//            if (speed <= 0.3f) {
//                return Theme.getColor(Theme.key_color_red, resourcesProvider);
//            } else if (speed <= 0.5f) {
//                return ColorUtils.blendARGB(
//                        Theme.getColor(Theme.key_color_red, resourcesProvider),
//                        Theme.getColor(Theme.key_color_yellow, resourcesProvider),
//                        (speed - 0.3f) / (0.5f - 0.3f)
//                );
//            } else if (speed <= 1f) {
//                return ColorUtils.blendARGB(
//                        Theme.getColor(Theme.key_color_yellow, resourcesProvider),
//                        Theme.getColor(Theme.key_color_lightblue, resourcesProvider),
//                        MathUtils.clamp((speed - 0.5f) / (1f - 0.5f), 0, 1)
//                );
//            } else {
                return ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_color_lightblue, resourcesProvider),
                        Theme.getColor(Theme.key_color_blue, resourcesProvider),
                        MathUtils.clamp((speed - 1f) / (2f - 1f), 0, 1)
                );
//            }
        }
    }
}
