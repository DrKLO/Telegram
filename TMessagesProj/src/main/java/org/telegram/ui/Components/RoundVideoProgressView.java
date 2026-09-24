package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

/** Draws recording progress, trim handles and the recorded-preview position. */
public final class RoundVideoProgressView extends FrameLayout {
    /** Receives normalized trim values in the inclusive range from zero to one. */
    public interface TrimListener {
        /** Called while either trim handle is dragged. */
        void onTrimChanged(float start, float end, boolean startChanged);
    }

    private static final int HANDLE_NONE = 0;
    private static final int HANDLE_START = 1;
    private static final int HANDLE_END = 2;
    private static final float MIN_TRIM_PROGRESS = 800f / 60_000f;
    private static final float OVERLAP_THRESHOLD = 0.006f;
    private static final float OVERLAP_OFFSET = 0.008f;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recordedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final float strokeWidth;
    private final float handleRadius;
    private final float playbackRadius;

    private float progress;
    private float trimStart;
    private float trimEnd;
    private float playbackProgress;
    private float loadingRotation;
    private boolean trimEnabled;
    private boolean loading;
    private int activeHandle;
    private float handleTouchOffset;
    private TrimListener trimListener;
    private final Runnable loadingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!loading) return;
            loadingRotation = (loadingRotation + 4f) % 360f;
            invalidate();
            postOnAnimation(this);
        }
    };

    /** Creates a green round-video progress view. */
    public RoundVideoProgressView(Context context) {
        super(context);
        strokeWidth = AndroidUtilities.dp(4);
        handleRadius = AndroidUtilities.dp(6);
        playbackRadius = AndroidUtilities.dp(3);
        configureStroke(backgroundPaint, 0.20f);
        configureStroke(recordedPaint, 0.60f);
        configureStroke(selectedPaint, 1f);
        handlePaint.setColor(Color.GREEN);
        playbackPaint.setColor(Color.WHITE);
        setWillNotDraw(false);
    }

    /** Sets the trim interaction listener. */
    public void setTrimListener(@Nullable TrimListener trimListener) {
        this.trimListener = trimListener;
    }

    /** Enables trim handles and initializes them to the recorded range. */
    public void setTrimEnabled(boolean enabled) {
        trimEnabled = enabled;
        activeHandle = HANDLE_NONE;
        if (enabled) {
            trimStart = 0f;
            trimEnd = progress;
            playbackProgress = trimStart;
            notifyTrimChanged(true);
        }
        invalidate();
    }

    /** Shows or hides the indeterminate processing indicator. */
    public void setLoading(boolean loading) {
        if (this.loading == loading) return;
        this.loading = loading;
        removeCallbacks(loadingRunnable);
        if (loading) postOnAnimation(loadingRunnable);
        invalidate();
    }

    /** Sets normalized recorded progress. */
    public void setProgress(float progress) {
        float value = clamp(progress, 0f, 1f);
        if (this.progress == value) return;
        this.progress = value;
        invalidate();
    }

    /** Sets normalized recorded-preview playback position. */
    public void setPlaybackProgress(float progress) {
        float value = clamp(progress, trimStart, trimEnd);
        if (playbackProgress == value) return;
        playbackProgress = value;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float inset = handleRadius + strokeWidth * 0.25f;
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawOval(arcBounds, backgroundPaint);
        if (loading) {
            canvas.drawArc(arcBounds, -90f + loadingRotation, 90f, false, selectedPaint);
        } else if (trimEnabled) {
            canvas.drawArc(arcBounds, -90f, progress * 360f, false, recordedPaint);
            canvas.drawArc(arcBounds, -90f + trimStart * 360f,
                    (trimEnd - trimStart) * 360f, false, selectedPaint);
            drawMarker(canvas, getStartHandleProgress(), handleRadius, handlePaint);
            drawMarker(canvas, getEndHandleProgress(), handleRadius, handlePaint);
            drawMarker(canvas, playbackProgress, playbackRadius, playbackPaint);
        } else {
            canvas.drawArc(arcBounds, -90f, progress * 360f, false, selectedPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!trimEnabled || loading) return false;
        float value = positionToProgress(event.getX(), event.getY());
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float startHandle = getStartHandleProgress();
            float endHandle = getEndHandleProgress();
            activeHandle = circularDistance(value, startHandle) <= circularDistance(value, endHandle)
                    ? HANDLE_START
                    : HANDLE_END;
            float trimValue = activeHandle == HANDLE_START ? trimStart : trimEnd;
            handleTouchOffset = signedCircularDistance(trimValue, value);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            updateActiveHandle(adjustTouchValue(value));
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            updateActiveHandle(adjustTouchValue(value));
            activeHandle = HANDLE_NONE;
            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return true;
    }

    private void updateActiveHandle(float value) {
        float minGap = Math.min(progress, MIN_TRIM_PROGRESS);
        if (activeHandle == HANDLE_START) {
            trimStart = clamp(value, 0f, trimEnd - minGap);
            playbackProgress = trimStart;
            notifyTrimChanged(true);
        } else if (activeHandle == HANDLE_END) {
            trimEnd = clamp(value, trimStart + minGap, progress);
            playbackProgress = trimEnd;
            notifyTrimChanged(false);
        }
        invalidate();
    }

    private void notifyTrimChanged(boolean startChanged) {
        if (trimListener != null) trimListener.onTrimChanged(trimStart, trimEnd, startChanged);
    }

    private float adjustTouchValue(float value) {
        float adjusted = value + handleTouchOffset;
        float anchor = activeHandle == HANDLE_START ? trimStart : trimEnd;
        if (adjusted - anchor > 0.5f) adjusted -= 1f;
        else if (anchor - adjusted > 0.5f) adjusted += 1f;
        return adjusted;
    }

    private float getStartHandleProgress() {
        return circularDistance(trimStart, trimEnd) < OVERLAP_THRESHOLD
                ? wrap(trimStart - OVERLAP_OFFSET)
                : trimStart;
    }

    private float getEndHandleProgress() {
        return circularDistance(trimStart, trimEnd) < OVERLAP_THRESHOLD
                ? wrap(trimEnd + OVERLAP_OFFSET)
                : trimEnd;
    }

    private void drawMarker(Canvas canvas, float value, float markerRadius, Paint markerPaint) {
        double angle = Math.toRadians(value * 360f - 90f);
        float radius = arcBounds.width() * 0.5f;
        float x = arcBounds.centerX() + (float) Math.cos(angle) * radius;
        float y = arcBounds.centerY() + (float) Math.sin(angle) * radius;
        canvas.drawCircle(x, y, markerRadius, markerPaint);
    }

    private float positionToProgress(float x, float y) {
        double angle = Math.toDegrees(Math.atan2(y - getHeight() * 0.5f, x - getWidth() * 0.5f));
        return (float) ((angle + 90d + 360d) % 360d / 360d);
    }

    private static void configureStroke(Paint paint, float alpha) {
        paint.setColor(Color.GREEN);
        paint.setAlpha(Math.round(255f * alpha));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(4));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    private static float circularDistance(float left, float right) {
        float distance = Math.abs(left - right);
        return Math.min(distance, 1f - distance);
    }

    private static float signedCircularDistance(float left, float right) {
        float distance = left - right;
        if (distance > 0.5f) distance -= 1f;
        else if (distance < -0.5f) distance += 1f;
        return distance;
    }

    private static float wrap(float value) {
        if (value < 0f) return value + 1f;
        if (value > 1f) return value - 1f;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
