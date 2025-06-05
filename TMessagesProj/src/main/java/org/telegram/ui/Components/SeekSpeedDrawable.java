package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class SeekSpeedDrawable extends Drawable {

    private final boolean isRound, isPiP;

    private Runnable invalidate;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RLottieDrawable hintDrawable;
    private final Path hintArrow = new Path();
    private final Text hintText = new Text(LocaleController.getString(R.string.SeekSpeedHint), 14);

    private final Path leftArrow = new Path();
    private final Path rightArrow = new Path();

    private boolean shown;
    private int direction = +1;
    private final AnimatedFloat animatedShown;
    private final AnimatedFloat animatedDirection;
    private final AnimatedFloat animatedSpeed;
    private final AnimatedFloat animatedHintShown;

    private final AnimatedTextView.AnimatedTextDrawable speedText;

    private boolean showHint;

    public SeekSpeedDrawable(Runnable invalidate, boolean isPiP, boolean isRound) {
        this.invalidate = invalidate;
        this.isPiP = isPiP;
        this.isRound = isRound;

        animatedShown = new AnimatedFloat(invalidate, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedShown.set(false, true);
        animatedDirection = new AnimatedFloat(invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedSpeed = new AnimatedFloat(invalidate, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedHintShown = new AnimatedFloat(invalidate, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedHintShown.set(false, true);

        speedText = new AnimatedTextView.AnimatedTextDrawable(false, true, true, true) {
            @Override
            public void invalidateSelf() {
                invalidate.run();
            }
        };
        speedText.setScaleProperty(.3f);
        speedText.setAnimationProperties(.4f, 0, 650, 1.6f, CubicBezierInterpolator.EASE_OUT_QUINT);
        speedText.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        speedText.setTextSize(dp(16));
        setSpeed(2.0f, false);
        speedText.setTextColor(0xFFFFFFFF);
        speedText.setGravity(Gravity.CENTER);

        arrowPaint.setPathEffect(new CornerPathEffect(dp(1.66f)));

        leftArrow.moveTo(dp(8.66f), -dp(12.66f / 2.0f));
        leftArrow.lineTo(0, 0);
        leftArrow.lineTo(dp(8.66f), dp(12.66f / 2.0f));
        leftArrow.close();

        rightArrow.moveTo(0, -dp(12.66f / 2.0f));
        rightArrow.lineTo(dp(8.66f), 0);
        rightArrow.lineTo(0, dp(12.66f / 2.0f));
        rightArrow.close();

        showHint = !isPiP && !isRound && !MessagesController.getGlobalMainSettings().getBoolean("seekSpeedHintShowed", false);

        hintArrow.moveTo(-dp(6.5f), 0);
        hintArrow.lineTo(0, -dp(6.33f));
        hintArrow.lineTo(dp(6.5f), 0);
        hintArrow.close();
    }

    public boolean isShown() {
        return shown || animatedShown.get() > 0;
    }

    private final RectF speedRect = new RectF();
    private final RectF hintRect = new RectF();

    private float t;
    private long lastFrameTime;

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final float speedRectWidth = speedText.getCurrentWidth() + dp(9 + 8 + 20 + 9);

        final float shown = animatedShown.set(this.shown);
        final float direction = animatedDirection.set(this.direction);
        if (shown <= 0.0f) return;
        final float speed = animatedSpeed.set(Math.abs(this.lastSpeed));

        final long now = System.currentTimeMillis();
        final float deltaTime = Math.min(.016f, (now - lastFrameTime) / 1000.0f);
        lastFrameTime = now;
        t += deltaTime * (1.5f * Math.min(speed, 4.0f));
        invalidate.run();

        speedRect.set(bounds.centerX() - speedRectWidth / 2f, bounds.top + dp(9), bounds.centerX() + speedRectWidth / 2f, bounds.top + dp(9 + 28));
        canvas.save();
        float scale = .6f + .4f * shown;
        if (bounds.width() < AndroidUtilities.displaySize.x * .7f) {
            scale *= .75f;
            if (isPiP) {
                canvas.translate(-dp(45), 0);
            }
        }
        canvas.scale(scale, scale, speedRect.centerX(), speedRect.top);
        canvas.translate(0, -dp(15) * (1.0f - shown));
        canvas.clipRect(speedRect);

        backgroundPaint.setColor(Theme.multAlpha(0xFF000000, 0.4f * shown));
        canvas.drawRoundRect(speedRect, speedRect.height() / 2f, speedRect.height() / 2f, backgroundPaint);
        speedText.setBounds(speedRect);

        float p;
        canvas.save();
        canvas.translate(speedRect.centerX() - speedRectWidth / 2.0f + dp(9) - dp(30) * (1.0f - Math.max(0, -direction)), speedRect.centerY());
        p = ((float)Math.sin((t) * Math.PI)/2.0f+1.0f);
        arrowPaint.setColor(Theme.multAlpha(0xFFFFFFFF, Math.max(0, -direction) * shown * (.2f + .75f * p)));
        canvas.drawPath(leftArrow, arrowPaint);
        canvas.translate(dp(10.66f), 0);
        p = ((float)Math.sin((t+.17f) * Math.PI)/2.0f+1.0f);
        arrowPaint.setColor(Theme.multAlpha(0xFFFFFFFF, Math.max(0, -direction) * shown * (.2f + .75f * p)));
        canvas.drawPath(leftArrow, arrowPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(-dp(20 + 8) / 2.0f * direction, 0.0f);
        speedText.setAlpha((int) (0xFF * shown));
        speedText.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(speedRect.centerX() + speedRectWidth / 2.0f - dp(30) + dp(30) * (1.0f - Math.max(0, direction)), speedRect.centerY());
        p = ((float)Math.sin((t) * Math.PI)/2.0f+1.0f);
        arrowPaint.setColor(Theme.multAlpha(0xFFFFFFFF, Math.max(0, direction) * shown * (.2f + .75f * p)));
        canvas.drawPath(rightArrow, arrowPaint);
        canvas.translate(dp(10.66f), 0);
        p = ((float)Math.sin((t-.17f) * Math.PI)/2.0f+1.0f);
        arrowPaint.setColor(Theme.multAlpha(0xFFFFFFFF, Math.max(0, direction) * shown * (.2f + .75f * p)));
        canvas.drawPath(rightArrow, arrowPaint);
        canvas.restore();

        canvas.restore();

        final float hintShown = animatedHintShown.set(this.showHint && this.shown);
        if (hintShown > 0) {
            if (hintDrawable == null) {
                hintDrawable = new RLottieDrawable(R.raw.seek_speed_hint, "" + R.raw.seek_speed_hint, AndroidUtilities.dp(24), AndroidUtilities.dp(24), true, null);
                hintDrawable.setAllowDecodeSingleFrame(true);
                hintDrawable.setCallback(new Callback() {
                    @Override
                    public void invalidateDrawable(@NonNull Drawable who) {
                        invalidate.run();
                    }
                    @Override
                    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {}
                    @Override
                    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {}
                });
                hintDrawable.setAutoRepeat(1);
                hintDrawable.start();
            }
            final float hintW = hintText.getCurrentWidth() + dp(22 + 24 + 8);
            final float hintH = dp(32);
            hintRect.set(bounds.centerX() - hintW / 2.0f, speedRect.top + speedRect.height() * shown + dp(11), bounds.centerX() + hintW / 2.0f, speedRect.top + speedRect.height() * shown + dp(11) + hintH);

            canvas.save();
            final float hintScale = .75f + .25f * hintShown;
            canvas.scale(hintScale, hintScale, hintRect.centerX(), hintRect.top);
            backgroundPaint.setColor(Theme.multAlpha(0xFF000000, 0.4f * hintShown));

            canvas.save();
            canvas.translate(hintRect.centerX(), hintRect.top);
            canvas.drawPath(hintArrow, backgroundPaint);
            canvas.restore();
            canvas.drawRoundRect(hintRect, dp(8), dp(8), backgroundPaint);

            hintDrawable.setBounds((int) hintRect.left + dp(11), (int) hintRect.centerY() - dp(24) / 2, (int) hintRect.left + dp(11 + 24), (int) hintRect.centerY() + dp(24) / 2);
            hintDrawable.setAlpha((int) (0xFF * hintShown));
            if (!hintDrawable.isRunning()) {
                hintDrawable.restart(true);
            }
            hintDrawable.draw(canvas);

            hintText.draw(canvas, hintRect.left + dp(11 + 24 + 4), hintRect.centerY(), 0xFFFFFFFF, hintShown);

            canvas.restore();
        }
    }

    public void setShown(boolean shown, boolean animated) {
        this.shown = shown;
        if (!animated) {
            animatedShown.set(shown, true);
        }
        invalidate.run();

        if (hintDrawable != null && showHint) {
            if (shown) {
                hintDrawable.restart();
            } else {
                hintDrawable.stop();
            }
        }
    }

    private float lastSpeed;
    public void setSpeed(float speed, boolean animated) {
        if (Math.floor(lastSpeed * 10) != Math.floor(speed * 10)) {
            speedText.cancelAnimation();
            speedText.setText(String.format(Locale.US, "%.1fx", Math.abs(speed)), animated);
            lastSpeed = speed;
        }
        direction = speed > 0 ? +1 : -1;
        if (!animated) {
            animatedDirection.set(direction, true);
        }
        invalidate.run();

        if (showHint && Math.abs(speed) > 3.0f && !hideHintScheduled) {
            hideHintScheduled = true;
            AndroidUtilities.runOnUIThread(hideHintRunnable, 2500);
            MessagesController.getGlobalMainSettings().edit().putBoolean("seekSpeedHintShowed", true).apply();
        }
    }

    private boolean hideHintScheduled;
    private final Runnable hideHintRunnable = () -> {
        showHint = false;
        this.invalidate.run();
    };

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
