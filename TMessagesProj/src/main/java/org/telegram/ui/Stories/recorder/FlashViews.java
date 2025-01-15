package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Camera;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ViewAnimator;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.gms.vision.Frame;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class FlashViews {

    public static final int[] COLORS = new int[]{0xffffffff, 0xfffeee8c, 0xff8cdfff};
    public static int getColor(float warmth) {
        if (warmth < .5f) {
            return ColorUtils.blendARGB(0xff8cdfff, 0xffffffff, Utilities.clamp(warmth / .5f, 1, 0));
        }
        return ColorUtils.blendARGB(0xffffffff, 0xfffeee8c, Utilities.clamp((warmth - .5f) / .5f, 1, 0));
    }

    private final Context context;
    public final View backgroundView;
    public final View foregroundView;

    private final ArrayList<Invertable> invertableViews = new ArrayList<>();

    @Nullable
    private final WindowManager windowManager;
    private final View windowView;
    @Nullable
    private final WindowManager.LayoutParams windowViewParams;

    public FlashViews(Context context, @Nullable WindowManager windowManager, View windowView, @Nullable WindowManager.LayoutParams windowViewParams) {
        this.context = context;
        this.windowManager = windowManager;
        this.windowView = windowView;
        this.windowViewParams = windowViewParams;

        backgroundView = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                invalidateGradient();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                gradientMatrix.reset();
                drawGradient(canvas, true);
            }
        };
        foregroundView = new View(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                gradientMatrix.reset();
                gradientMatrix.postTranslate(-getX(), -getY() + AndroidUtilities.statusBarHeight);
                gradientMatrix.postScale(1f / getScaleX(), 1f / getScaleY(), getPivotX(), getPivotY());
                drawGradient(canvas, false);
            }
        };

        paint.setAlpha(0);
    }

    public void flash(Utilities.Callback<Utilities.Callback<Runnable>> takePicture) {
        setScreenBrightness(intensityValue());
        flashTo(1f, 320, () -> {
            AndroidUtilities.runOnUIThread(() -> {
                takePicture.run(done -> {
                    setScreenBrightness(-1f);
                    AndroidUtilities.runOnUIThread(() -> {
                        flashTo(0f, 240, done);
                    }, 80);
                });
            }, 320);
        });
    }

    private void setScreenBrightness(float value) {
        if (windowViewParams != null) {
            windowViewParams.screenBrightness = value;
            if (windowManager != null) {
                windowManager.updateViewLayout(windowView, windowViewParams);
            }
        } else {
            Activity activity = AndroidUtilities.findActivity(context);
            if (activity == null) activity = LaunchActivity.instance;
            if (activity == null || activity.isFinishing()) return;
            final Window window = activity.getWindow();
            if (window == null) return;
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.screenBrightness = value;
            window.setAttributes(layoutParams);
        }
    }

    public void previewStart() {
        flashTo(.85f, 240, null);
    }

    public void previewEnd() {
        flashTo(0, 240, null);
    }

    public void flashIn(Runnable done) {
        setScreenBrightness(intensityValue());
        flashTo(1f, 320, done);
    }

    public void flashOut() {
        setScreenBrightness(-1f);
        flashTo(0f, 240, null);
    }

    private float invert = 0f;
    private ValueAnimator animator;

    private void flashTo(float value, long duration, Runnable whenDone) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (duration <= 0) {
            invert = value;
            update();
            if (whenDone != null) {
                whenDone.run();
            }
        } else {
            animator = ValueAnimator.ofFloat(invert, value);
            animator.addUpdateListener(anm -> {
                invert = (float) anm.getAnimatedValue();
                update();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    invert = value;
                    update();
                    if (whenDone != null) {
                        whenDone.run();
                    }
                }
            });
            animator.setDuration(duration);
            animator.setInterpolator(CubicBezierInterpolator.EASE_IN);
            animator.start();
        }
    }

    private void update() {
        for (int i = 0; i < invertableViews.size(); i++) {
            invertableViews.get(i).setInvert(invert);
            invertableViews.get(i).invalidate();
        }
        paint.setAlpha((int) (0xff * intensityValue() * invert));
        backgroundView.invalidate();
        foregroundView.invalidate();
    }

    private float intensityValue() {
        return intensity;
    }

    public void add(Invertable view) {
        view.setInvert(invert);
        invertableViews.add(view);
    }

    public void remove(Invertable view) {
        invertableViews.remove(view);
    }

    private int lastWidth, lastHeight, lastColor;
    private float lastInvert;
    private int color;
    public int colorIndex;

    public float warmth = .75f, intensity = 1f;

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        update();
    }

    public void setWarmth(float warmth) {
        this.warmth = warmth;
        this.color = getColor(warmth);
        invalidateGradient();
    }

    private final Matrix gradientMatrix = new Matrix();
    private RadialGradient gradient;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void invalidateGradient() {
        if (lastColor != color || lastWidth != backgroundView.getMeasuredWidth() || lastHeight != backgroundView.getMeasuredHeight() || Math.abs(lastInvert - invert) > 0.005f) {
            lastColor = color;
            lastWidth = backgroundView.getMeasuredWidth();
            lastHeight = backgroundView.getMeasuredHeight();
            lastInvert = invert;

            if (lastWidth > 0 && lastHeight > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    gradient = new RadialGradient(
                        lastWidth * .5f, lastHeight * .4f,
                        Math.min(lastWidth, lastHeight) / 2f * 1.35f * (2f - invert),
                        new long[]{
                            Color.valueOf(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, 0.0f, ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)).pack(),
                            Color.valueOf(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, 1.0f, ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)).pack()
                        },
                        new float[]{AndroidUtilities.lerp(.9f, 0.22f, invert), 1},
                        Shader.TileMode.CLAMP
                    );
                } else {
                    gradient = new RadialGradient(
                        lastWidth * .5f, lastHeight * .4f,
                        Math.min(lastWidth, lastHeight) / 2f * 1.35f * (2f - invert),
                        new int[]{ ColorUtils.setAlphaComponent(color, 0), color },
                        new float[]{AndroidUtilities.lerp(.9f, 0.22f, invert), 1},
                        Shader.TileMode.CLAMP
                    );
                }
                paint.setShader(gradient);
                invalidate();
            }
        }
    }

    private void invalidate() {
        backgroundView.invalidate();
        foregroundView.invalidate();
    }

    public void drawGradient(Canvas canvas, boolean bg) {
        if (gradient != null) {
            invalidateGradient();
            gradient.setLocalMatrix(gradientMatrix);
            if (bg) {
                canvas.drawRect(0, 0, lastWidth, lastHeight, paint);
            } else {
                AndroidUtilities.rectTmp.set(0, 0, foregroundView.getMeasuredWidth(), foregroundView.getMeasuredHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12) - 2, dp(12) - 2, paint);
            }
        }
    }

    public interface Invertable {
        void setInvert(float invert);

        void invalidate();
    }

    public static class ImageViewInvertable extends ImageView implements Invertable {
        public ImageViewInvertable(Context context) {
            super(context);
        }

        public void setInvert(float invert) {
            setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, invert), PorterDuff.Mode.MULTIPLY));
        }
    }
}
