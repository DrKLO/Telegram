package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class SearchStateDrawable extends Drawable {

    @IntDef({ State.STATE_SEARCH, State.STATE_BACK, State.STATE_PROGRESS })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
        int STATE_SEARCH = 0;
        int STATE_BACK = 1;
        int STATE_PROGRESS = 2;
    }

    private int alpha = 0xFF;
    private Paint paint;

    private Path path = new Path();

    private RectF progressRect = new RectF();
    private final float progressRadius = .25f;
    private long progressStart = -1;
    private boolean progressStartedWithOverTo;
    private float progressAngleFrom = 0, progressAngleTo = 0;
    private float[] progressSegments = new float[2];

    private @State int fromState;
    private @State int toState = State.STATE_SEARCH;
    private boolean waitingForProgressToEnd = false, wereNotWaitingForProgressToEnd;

    private AnimatedFloat progress = new AnimatedFloat(1, this::invalidateSelf, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Runnable delaySetProgress;

    public SearchStateDrawable() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(AndroidUtilities.dp(1.333f));
    }

    public @State int getIconState() {
        return this.toState;
    }

    public void setIconState(@State int state) {
        setIconState(state, true);
    }
    public void setIconState(@State int state, boolean animated) {
        setIconState(state, animated, false);
    }

    private void setIconState(@State int state, boolean animated, boolean skipProgressDelay) {
        if (getIconState() == state) {
            if (state != State.STATE_PROGRESS) {
                AndroidUtilities.cancelRunOnUIThread(delaySetProgress);
                delaySetProgress = null;
            }
            return;
        }

        if (!skipProgressDelay && state == State.STATE_PROGRESS) {
            if (delaySetProgress == null) {
                AndroidUtilities.runOnUIThread(delaySetProgress = () -> {
                    delaySetProgress = null;
                    setIconState(state, animated, true);
                }, 65);
            }
            return;
        } else if (delaySetProgress != null) {
            AndroidUtilities.cancelRunOnUIThread(delaySetProgress);
        }

        if (progress.get() < 1 && animated) {
            setIconState(toState, false);
        }

        if (state == State.STATE_PROGRESS) {
            progressAngleFrom = 180;
            progressStart = -1;
        } else if (toState == State.STATE_PROGRESS) {
            if (state == State.STATE_SEARCH) {
                progressAngleTo = -45;
            } else {
                progressAngleTo = 0;
            }
        }

        if (animated) {
            fromState = toState;
            toState = state;
            waitingForProgressToEnd = fromState == State.STATE_PROGRESS && state != State.STATE_PROGRESS;
            progress.set(0, true);
        } else {
            fromState = toState = state;
            waitingForProgressToEnd = false;
            progress.set(1, true);
        }
        invalidateSelf();
    }

    public void setStrokeWidth(float strokeWidth) {
        paint.setStrokeWidth(strokeWidth);
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        this.mn = Math.min(bounds.width(), bounds.height());
        this.cx = bounds.centerX();
        this.cy = bounds.centerY();

        if (alpha < 0xFF) {
            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha, Canvas.ALL_SAVE_FLAG);
        }

        float value = progress.set(waitingForProgressToEnd ? 0 : 1);

        float searchValue =   toState == State.STATE_SEARCH   ? fromState == State.STATE_SEARCH   ? 1 : value : fromState == State.STATE_SEARCH     ? 1f - value : 0;
        float backValue   =   toState == State.STATE_BACK     ? fromState == State.STATE_BACK     ? 1 : value : fromState == State.STATE_BACK       ? 1f - value : 0;
        float progressValue = toState == State.STATE_PROGRESS ? fromState == State.STATE_PROGRESS ? 1 : value : fromState == State.STATE_PROGRESS   ? 1f - value : 0;

        if (searchValue > 0) {
            // o
            drawCircle(
                canvas,
                lerp(x(.25f), x(.444f), searchValue),
                lerp(y(.5f), y(.444f), searchValue),
                lerp(0, w(.208f), searchValue)
            );
        }

        if (searchValue > 0 || backValue > 0) {
            // —
            canvas.save();
            canvas.rotate(searchValue * 45, cx, cy);
            drawLine(
                canvas,
                lerp3(
                    x(.914f), x(.7638f), fromState == State.STATE_PROGRESS ? x(.5f + progressRadius) : x(.2409f),
                    searchValue, backValue,    progressValue
                ), y(.5f),
                lerp3(
                    x(.658f), x(.2409f), fromState == State.STATE_PROGRESS ? x(.5f + progressRadius) : x(.2409f),
                    searchValue, backValue,    progressValue
                ), y(.5f)
            );
            canvas.restore();
        }

        if (backValue > 0) {
            // <
            float ax = fromState == State.STATE_PROGRESS ? lerp(x(.5f + progressRadius), x(.2409f), backValue) : x(.2409f);

            canvas.save();
            canvas.rotate(searchValue * 45, cx, cy);
            drawLines(
                canvas,

                ax + x(.2452f) * backValue,
                lerp(y(.5f), y(.25f), backValue),

                ax, y(.5f),

                ax + x(.2452f) * backValue,
                lerp(y(.5f), y(.75f), backValue)
            );
            canvas.restore();
        }

        if (progressValue > 0) {
            if (progressStart < 0 && progressValue > .8f) {
                progressStart = System.currentTimeMillis();
                wereNotWaitingForProgressToEnd = waitingForProgressToEnd;
            }
            if (progressStart > 0) {

                CircularProgressDrawable.getSegments(
                    (System.currentTimeMillis() - progressStart) % 5400f,
                    progressSegments
                );
                float fromAngle = progressSegments[0], toAngle = progressSegments[1];
                if (getIconState() != State.STATE_PROGRESS && !waitingForProgressToEnd) {
                    float m = Math.max(0, (float) Math.floor((fromAngle - 180) / 360f) * 360f + 180);
                    toAngle = Math.min(toAngle, m + progressAngleTo);
                    fromAngle = Math.min(fromAngle, m + progressAngleTo);
                    fromAngle = lerp(toAngle, fromAngle, progressValue);
                }

                boolean progressOverTo = containsAngle(progressAngleTo, progressAngleFrom + fromAngle, progressAngleFrom + toAngle);
                if (waitingForProgressToEnd && !wereNotWaitingForProgressToEnd) {
                    wereNotWaitingForProgressToEnd = waitingForProgressToEnd;
                    progressStartedWithOverTo = progressOverTo;
                }
                if (progressStartedWithOverTo && !progressOverTo) {
                    progressStartedWithOverTo = false;
                }
                if (waitingForProgressToEnd && progressOverTo && !progressStartedWithOverTo) {
                    waitingForProgressToEnd = false;
                }

                progressRect.set(x(.5f - progressRadius), y(.5f - progressRadius), x(.5f + progressRadius), y(.5f + progressRadius));
                canvas.drawArc(
                    progressRect,
                    progressAngleFrom + fromAngle,
                    toAngle - fromAngle,
                    false,
                    paint
                );

                invalidateSelf();
            }
        }

        if (alpha < 0xFF) {
            canvas.restore();
        }

        if (value < 1) {
            invalidateSelf();
        }
    }

    private boolean containsAngle(float angle, float angleFrom, float angleTo) {
        angleFrom = angleFrom % 360;
        if (angleFrom < 0) {
            angleFrom = 360 + angleFrom;
        }
        angleTo = angleTo % 360;
        if (angleTo < 0) {
            angleTo = 360 + angleTo;
        }
        if (angleFrom > angleTo)
            return angle >= angleFrom || angle <= angleTo;
        return angle >= angleFrom && angle <= angleTo;
    }

    private void drawCircle(Canvas canvas, float x, float y, float r) {
        if (r < w(.075f)) {
            return;
        }
        canvas.drawCircle(x, y, r, paint);
    }

    private void drawLine(Canvas canvas, float x1, float y1, float x2, float y2) {
        if (MathUtils.distance(x1, y1, x2, y2) <= w(.075f)) {
            return;
        }
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private void drawLines(Canvas canvas, float x1, float y1, float x2, float y2, float x3, float y3) {
        if (Math.max(MathUtils.distance(x1, y1, x2, y2), MathUtils.distance(x3, y3, x2, y2)) <= w(.075f)) {
            return;
        }
        path.rewind();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        canvas.drawPath(path, paint);
    }

    private float lerp3(
        float x1,
        float x2,
        float x3,
        // t1 + t2 + t3 = 1
        float t1,
        float t2,
        float t3
    ) {
        return x1 * t1 + x2 * t2 + x3 * t3;
    }

    private float mn, cx, cy;

    private float x(float t) {
        return cx - mn * (.5f - t);
    }

    private float y(float t) {
        return cy - mn * (.5f - t);
    }

    private float w(float t) {
        return mn * t;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }
}
