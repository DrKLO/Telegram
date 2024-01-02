package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ViewAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Views.RoundView;

import java.io.File;

public class RoundVideoRecorder extends FrameLayout {

    public final CameraView cameraView;
    public final File file;

    private long recordingStarted = -1;
    private long recordingStopped = -1;
    public final long MAX_DURATION = 59_500L;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable stopRunnable = this::stop;

    public RoundVideoRecorder(Context context) {
        super(context);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeJoin(Paint.Join.ROUND);

        file = StoryEntry.makeCacheFile(UserConfig.selectedAccount, true);

        cameraView = new CameraView(context, true, false) {
            private final Path circlePath = new Path();
            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                circlePath.rewind();
                circlePath.addCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth() / 2f, getHeight() / 2f), Path.Direction.CW);
                canvas.clipPath(circlePath);
                super.dispatchDraw(canvas);
                canvas.restore();
            }

            @Override
            protected boolean square() {
                return true;
            }

            @Override
            protected void receivedAmplitude(double amplitude) {
                RoundVideoRecorder.this.receivedAmplitude(amplitude);
            }
        };
        cameraView.setScaleX(0f);
        cameraView.setScaleY(0f);
        addView(cameraView);
        cameraView.setDelegate(() -> {
            if (recordingStarted > 0) return;
            CameraController.getInstance().recordVideo(cameraView.getCameraSession(), file, false, (thumbPath, duration) -> {
                recordingStopped = System.currentTimeMillis();
                AndroidUtilities.cancelRunOnUIThread(stopRunnable);
                if (cancelled) {
                    return;
                }
                if (duration > 1000) {
                    cameraView.destroy(true, null);
                    if (onDoneCallback != null) {
                        onDoneCallback.run(file, thumbPath, duration);
                    }
                } else {
                    destroy(false);
                }
            }, () -> {
                cameraView.animate().scaleX(1f).scaleY(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(280).start();
                recordingStarted = System.currentTimeMillis();
                invalidate();

                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Exception ignore) {}

                AndroidUtilities.runOnUIThread(stopRunnable, MAX_DURATION);
            }, cameraView, true);
        });
        cameraView.initTexture();

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        final int side = (int) (Math.min(width, height) * .43f);
        cameraView.measure(
            MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        );

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int x = (right - left) - cameraView.getMeasuredWidth() - dp(16);
        final int y = dp(72);
        cameraView.layout(x, y, x + cameraView.getMeasuredWidth(), y + cameraView.getMeasuredHeight());
    }

    protected void receivedAmplitude(double amplitude) {

    }

    private Utilities.Callback3<File, String, Long> onDoneCallback;
    public RoundVideoRecorder onDone(Utilities.Callback3<File, String, Long> onDoneCallback) {
        this.onDoneCallback = onDoneCallback;
        return this;
    }

    private Runnable onDestroyCallback;
    public RoundVideoRecorder onDestroy(Runnable onDestroyCallback) {
        this.onDestroyCallback = onDestroyCallback;
        return this;
    }

    private float alpha = 1f;
    private RoundView roundView;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        AndroidUtilities.rectTmp.set(
            cameraView.getX() + cameraView.getWidth() / 2f * (1f - cameraView.getScaleX()),
            cameraView.getY() + cameraView.getHeight() / 2f * (1f - cameraView.getScaleY()),
            cameraView.getX() + cameraView.getWidth() - cameraView.getWidth() / 2f * (1f - cameraView.getScaleX()),
            cameraView.getY() + cameraView.getHeight() - cameraView.getHeight() / 2f * (1f - cameraView.getScaleY())
        );

        shadowPaint.setShadowLayer(dp(2), 0, dp(.66f), Theme.multAlpha(0x20000000, alpha));
        shadowPaint.setAlpha((int) (0xff * alpha));
        canvas.drawCircle(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY(), Math.min(AndroidUtilities.rectTmp.width() / 2f, AndroidUtilities.rectTmp.height() / 2f) - 1, shadowPaint);

        super.dispatchDraw(canvas);
        if (roundView != null && roundView.getWidth() > 0 && roundView.getHeight() > 0) {
            canvas.save();
            canvas.translate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
            canvas.scale(
                AndroidUtilities.rectTmp.width() / roundView.getWidth(),
                AndroidUtilities.rectTmp.height() / roundView.getHeight()
            );
            float wasAlpha = roundView.getAlpha();
            roundView.setDraw(true);
            roundView.setAlpha(1f - alpha);
            roundView.draw(canvas);
            roundView.setAlpha(wasAlpha);
            roundView.setDraw(false);
            canvas.restore();
        }

        if (recordingStarted > 0) {
            float t = Utilities.clamp(sinceRecording() / (float) MAX_DURATION, 1, 0);

            progressPaint.setStrokeWidth(dp(3.33f));
            progressPaint.setColor(Theme.multAlpha(0xbeffffff, alpha));
            progressPaint.setShadowLayer(dp(1), 0, dp(.33f), Theme.multAlpha(0x20000000, alpha));
            AndroidUtilities.rectTmp.inset(-dp(3.33f / 2f + 6), -dp(3.33f / 2f + 6));
            canvas.drawArc(AndroidUtilities.rectTmp, -90f, 360f * t, false, progressPaint);

            if (recordingStopped <= 0)
                invalidate();
        }
    }

    public long sinceRecording() {
        return recordingStarted < 0 ? 0 : Math.min(MAX_DURATION, (recordingStopped < 0 ? System.currentTimeMillis() : recordingStopped) - recordingStarted);
    }

    public String sinceRecordingText() {
        long fullms = sinceRecording();
        int sec = (int) (fullms / 1000);
        int ms = (int) ((fullms - sec * 1000) / 100);
        int min = (int) (sec / 60);
        sec = sec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec + "." + ms;
    }

    private ValueAnimator cameraViewAnimator;
    public void hideTo(RoundView roundView) {
        if (roundView == null) {
            destroy(false);
            return;
        }

        AndroidUtilities.cancelRunOnUIThread(stopRunnable);
        cameraView.destroy(true, null);
        if (roundView != null) {
            roundView.setDraw(false);
        }
        post(() -> {
            if (roundView.getWidth() <= 0) {
                cameraView.animate().scaleX(0).scaleY(1).withEndAction(() -> {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(this);
                    }
                }).start();
                return;
            }

            final float scale = (float) roundView.getWidth() / cameraView.getWidth();
            if (cameraViewAnimator != null) {
                cameraViewAnimator.cancel();
            }
            cameraViewAnimator = ValueAnimator.ofFloat(0, 1);
            final float fromScale = cameraView.getScaleX();
            final float toX = (roundView.getX() + roundView.getWidth() / 2f) - (cameraView.getX() + cameraView.getWidth() / 2f);
            final float toY = (roundView.getY() + roundView.getHeight() / 2f) - (cameraView.getY() + cameraView.getHeight() / 2f);
            cameraViewAnimator.addUpdateListener(anm -> {
                final float t = (float) anm.getAnimatedValue();
                cameraView.setScaleX(AndroidUtilities.lerp(fromScale, scale, t));
                cameraView.setScaleY(AndroidUtilities.lerp(fromScale, scale, t));
                cameraView.setTranslationX(toX * t);
                cameraView.setTranslationY(toY * t);
                cameraView.setAlpha(1f - t);
                alpha = 1f - t;
                invalidate();
            });
            cameraViewAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (roundView != null) {
                        roundView.setDraw(true);
                    }
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(RoundVideoRecorder.this);
                    }
                }
            });
            cameraViewAnimator.setDuration(320);
            cameraViewAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            this.roundView = roundView;
            cameraViewAnimator.start();
        });
    }

    public void stop() {
        AndroidUtilities.cancelRunOnUIThread(stopRunnable);
        if (recordingStarted <= 0) {
            destroy(true);
        } else {
            CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionRecording(), false, false);
        }
    }

    private boolean cancelled = false;
    public void cancel() {
        cancelled = true;
        AndroidUtilities.cancelRunOnUIThread(stopRunnable);
        CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionRecording(), false, false);
        destroy(false);
    }

    private ValueAnimator destroyAnimator;
    private float destroyT;
    public void destroy(boolean instant) {
        if (onDestroyCallback != null) {
            onDestroyCallback.run();
            onDestroyCallback = null;
        }
        AndroidUtilities.cancelRunOnUIThread(stopRunnable);
        cameraView.destroy(true, null);
        try {
            file.delete();
        } catch (Exception ignore) {}
        if (instant) {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
        } else {
            if (destroyAnimator != null) {
                destroyAnimator.cancel();
            }
            destroyAnimator = ValueAnimator.ofFloat(destroyT, 1);
            destroyAnimator.addUpdateListener(anm -> {
                destroyT = (float) anm.getAnimatedValue();
                cameraView.setScaleX(1f - destroyT);
                cameraView.setScaleY(1f - destroyT);
                invalidate();
            });
            destroyAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(RoundVideoRecorder.this);
                    }
                }
            });
            destroyAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            destroyAnimator.setDuration(280);
            destroyAnimator.start();
        }
    }
}
