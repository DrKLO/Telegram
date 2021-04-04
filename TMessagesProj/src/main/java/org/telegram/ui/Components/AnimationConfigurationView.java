package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class AnimationConfigurationView extends View {

    private static final int ACTION_MOVE_NONE = -1;
    private static final int ACTION_MOVE_TOP_PROGRESS = 0;
    private static final int ACTION_MOVE_BOTTOM_PROGRESS = 1;
    private static final int ACTION_MOVE_START_TIME = 2;
    private static final int ACTION_MOVE_END_TIMES = 3;

    private final int trackColor;
    private final int fillTrackColor;
    private final int progressTextColor;
    private final int timeLineColor = Color.parseColor("#F8CE46");

    private final RectF chartArea = new RectF();
    private final Paint offTrackPaint = new Paint();
    private final Paint onTrackPaint = new Paint();
    private final Paint timeTrackPaint = new Paint();
    private final Paint timeControlDotPaint = new Paint();
    private final Paint timeControlDotStrokePaint = new Paint();
    private final Paint progressTextPaint = new Paint();
    private final Paint timeTextPaint = new Paint();

    private final Drawable slideDotDrawable;
    private final Drawable slideTimeDrawable;
    private final Path bezierPath = new Path();

    //calculation values
    public MessagesController.AnimationConfig animationConfig = new MessagesController.AnimationConfig();
    public OnAnimationValueChangeListener listener;

    private float topProgress = 100;
    private float bottomProgress = 100;
    private float maxTime = 1000;
    private float startTime = 0;
    private float endTime = maxTime;

    //draw values
    private final float timePointDotRadius = AndroidUtilities.dpr(4);
    private float chartAreaWidth;
    private float startTimeTrackX;
    private float endTimeTrackX;
    private float timeAreaWidth;
    private float topProgressX;
    private float bottomProgressX;
    private float topProgressTextX;
    private float topProgressTextY;
    private float bottomProgressTextX;
    private float bottomProgressTextY;
    private float startTimeTextX;
    private float startTimeTextY;
    private float endTimeTextX;
    private float endTimeTextY;

    //other values
    private final float tapAreaRadius = AndroidUtilities.dpr(32);
    private final float textSize = AndroidUtilities.dpr(16f);
    private final float progressTextMargin;
    private final float progressTextHeight;
    private final float progressTextDescent;
    private final float timeTextMargin;
    private final float maxTimeYShift;
    private final float minTimeSpacePx;
    private final float zeroProgressTextMargin;
    private final float hundredProgressTextMargin;

    private final float startTimeShiftPx;
    private final float endTimeShiftPx;

    private int actionState = ACTION_MOVE_NONE;

    public AnimationConfigurationView(Context context) {
        this(context, null);
    }

    public AnimationConfigurationView(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        this(context, attrs, 0);
    }

    public AnimationConfigurationView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);

        trackColor = Theme.getColor(Theme.key_player_progressBackground);
        fillTrackColor = Theme.getColor(Theme.key_player_progress);
        progressTextColor = Theme.getColor(Theme.key_player_progress);

        offTrackPaint.setAntiAlias(true);
        offTrackPaint.setStyle(Paint.Style.STROKE);
        offTrackPaint.setStrokeWidth(AndroidUtilities.dpf2(2f));
        onTrackPaint.set(offTrackPaint);
        timeTrackPaint.set(offTrackPaint);
        offTrackPaint.setColor(trackColor);
        onTrackPaint.setColor(fillTrackColor);
        timeTrackPaint.setColor(timeLineColor);
        timeTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        timeTrackPaint.setStrokeWidth(AndroidUtilities.dpf2(2f));
        timeTrackPaint
                .setPathEffect(new DashPathEffect(new float[]{1f, 20f}, 0f));
        timeControlDotStrokePaint.set(offTrackPaint);
        timeControlDotStrokePaint.setColor(Color.WHITE);
        timeControlDotPaint.setAntiAlias(true);
        timeControlDotPaint.setStyle(Paint.Style.FILL);
        timeControlDotPaint.setColor(timeLineColor);

        timeTextPaint.setAntiAlias(true);
        timeTextPaint.setColor(timeLineColor);
        timeTextPaint.setTextSize(textSize);
        progressTextPaint.set(timeTextPaint);
        progressTextPaint.setColor(progressTextColor);
        progressTextPaint.setTextAlign(Paint.Align.CENTER);

        slideDotDrawable = ContextCompat.getDrawable(context, R.drawable.zoom_round_b);
        slideDotDrawable.setBounds(
                0,
                0,
                AndroidUtilities.dpr(22f),
                AndroidUtilities.dpr(22f)
        );
        slideTimeDrawable = ContextCompat.getDrawable(context, R.drawable.stickers_back_all);
        slideTimeDrawable.setBounds(
                0,
                0,
                AndroidUtilities.dpr(16f),
                AndroidUtilities.dpr(32f)
        );

        zeroProgressTextMargin = Math.max(
                0,
                (progressTextPaint.measureText("0%") - slideDotDrawable.getBounds().width()) / 2
        );
        hundredProgressTextMargin = Math.max(
                0,
                (progressTextPaint.measureText("1000%") - slideDotDrawable.getBounds().width()) / 2
        );

        Paint.FontMetrics progressFm = progressTextPaint.getFontMetrics();
        progressTextHeight = progressFm.bottom - (progressFm.top - progressFm.ascent);
        progressTextDescent = progressFm.descent;
        progressTextMargin = slideDotDrawable.getBounds().height() / 2f + AndroidUtilities.dpf2(4f);
        timeTextMargin = slideTimeDrawable.getBounds().width() / 2f + AndroidUtilities.dpf2(6f);

        Paint.FontMetrics timeTextFm = timeTextPaint.getFontMetrics();
        maxTimeYShift = timeTextFm.bottom / 2f - (timeTextFm.top - timeTextFm.ascent);
        float txtWidth = timeTextPaint.measureText(" 0000ms ");
        minTimeSpacePx = txtWidth + timeTextMargin * 2;
        startTimeShiftPx = txtWidth * 2 + timeTextMargin * 2 + AndroidUtilities.dpf2(6f);
        endTimeShiftPx = startTimeShiftPx - AndroidUtilities.dpf2(6f);
    }

    public void updateValues(MessagesController.AnimationConfig animationConfig) {
        this.animationConfig.fill(animationConfig);
        topProgress = animationConfig.endProgress;
        bottomProgress = animationConfig.startProgress;
        startTime = animationConfig.startTime;
        endTime = animationConfig.endTime;
        maxTime = animationConfig.duration;
        updateDrawValues();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                boolean result = checkDownEvent(event);
                if (result) {
                    attemptClaimDrag();
                }
                return result;
            case MotionEvent.ACTION_MOVE:
                return checkMoveEvent(event);
            case MotionEvent.ACTION_UP:
                actionState = ACTION_MOVE_TOP_PROGRESS;
                break;
        }
        return super.onTouchEvent(event);
    }

    private void attemptClaimDrag() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private boolean checkDownEvent(MotionEvent event) {
        if (distance(event.getX(), event.getY(), topProgressX, chartArea.top) < tapAreaRadius) {
            actionState = ACTION_MOVE_TOP_PROGRESS;
            return true;
        }
        if (distance(
                event.getX(),
                event.getY(),
                bottomProgressX,
                chartArea.bottom
        ) < tapAreaRadius) {
            actionState = ACTION_MOVE_BOTTOM_PROGRESS;
            return true;
        }
        if (distance(
                event.getX(), event.getY(),
                startTimeTrackX, chartArea.top + chartArea.height() / 2
        ) < tapAreaRadius) {
            actionState = ACTION_MOVE_START_TIME;
            return true;
        }
        if (distance(
                event.getX(), event.getY(),
                endTimeTrackX, chartArea.top + chartArea.height() / 2
        ) < tapAreaRadius) {
            actionState = ACTION_MOVE_END_TIMES;
            return true;
        }
        actionState = ACTION_MOVE_NONE;
        return false;
    }

    private boolean checkMoveEvent(MotionEvent event) {
        if (actionState == ACTION_MOVE_NONE) {
            return false;
        }
        float x = event.getX();
        switch (actionState) {
            case ACTION_MOVE_TOP_PROGRESS:
                moveTopProgress(x);
                notifyChanged();
                return true;
            case ACTION_MOVE_BOTTOM_PROGRESS:
                moveBottomProgress(x);
                notifyChanged();
                return true;
            case ACTION_MOVE_START_TIME:
                moveStartTime(x);
                notifyChanged();
                return true;
            case ACTION_MOVE_END_TIMES:
                moveEndTime(x);
                notifyChanged();
                return true;
            default:
                return false;
        }
    }

    private void moveTopProgress(float newX) {
        if (newX < startTimeTrackX) {
            topProgress = 100;
            animationConfig.endProgress = 100;
            return;
        }
        if (newX > endTimeTrackX) {
            topProgress = 0;
            animationConfig.endProgress = 0;
            return;
        }
        topProgress = (endTimeTrackX - newX) * 100 / (endTimeTrackX - startTimeTrackX);
        animationConfig.endProgress = Math.round(topProgress);
    }

    private void moveBottomProgress(float newX) {
        if (newX < startTimeTrackX) {
            bottomProgress = 0;
            animationConfig.startProgress = 0;
            return;
        }
        if (newX > endTimeTrackX) {
            bottomProgress = 100;
            animationConfig.startProgress = 100;
            return;
        }
        bottomProgress = 100 - (endTimeTrackX - newX) * 100 / (endTimeTrackX - startTimeTrackX);
        animationConfig.startProgress = Math.round(bottomProgress);
    }

    private void moveStartTime(float newX) {
        newX = Math.min(newX, endTimeTrackX - minTimeSpacePx);
        if (newX < chartArea.left) {
            startTime = 0;
            animationConfig.startTime = 0;
            return;
        }
        if (newX > chartArea.right) {
            startTime = maxTime;
            animationConfig.startTime = animationConfig.duration;
            return;
        }
        startTime = maxTime * (newX - chartArea.left) / chartArea.width();
        animationConfig.startTime = Math.round(startTime);
    }

    private void moveEndTime(float newX) {
        newX = Math.max(newX, startTimeTrackX + minTimeSpacePx);
        if (newX < chartArea.left) {
            endTime = 0;
            animationConfig.endTime = 0;
            return;
        }
        if (newX > chartArea.right) {
            endTime = maxTime;
            animationConfig.endTime = animationConfig.duration;
            return;
        }
        endTime = maxTime * (newX - chartArea.left) / chartArea.width();
        animationConfig.endTime = Math.round(endTime);
    }

    private void notifyChanged() {
        updateDrawValues();
        if (listener != null) {
            listener.onChanged(animationConfig);
        }
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        chartArea.set(
                AndroidUtilities.dpf2(28f),
                AndroidUtilities.dpf2(44f),
                getMeasuredWidth() - AndroidUtilities.dpf2(28f),
                getMeasuredHeight() - AndroidUtilities.dpf2(40f)
        );
        updateDrawValues();
    }

    private void updateDrawValues() {
        chartAreaWidth = chartArea.width();
        startTimeTrackX = chartArea.left + chartAreaWidth * startTime / maxTime;
        endTimeTrackX = chartArea.left + chartAreaWidth * endTime / maxTime;
        timeAreaWidth = endTimeTrackX - startTimeTrackX;
        topProgressX = startTimeTrackX + timeAreaWidth * (100 - topProgress) / 100;
        bottomProgressX = startTimeTrackX + timeAreaWidth * bottomProgress / 100;

        float timeVerticalShift;
        if (timeAreaWidth > startTimeShiftPx) {
            timeVerticalShift = 0f;
        } else if (timeAreaWidth < endTimeShiftPx) {
            timeVerticalShift = textSize / 2;
        } else {
            timeVerticalShift =
                    (textSize / 2) * (1 - (timeAreaWidth - endTimeShiftPx) / (startTimeShiftPx - endTimeShiftPx));
        }

        topProgressTextX = clamp(
                topProgressX,
                startTimeTrackX + hundredProgressTextMargin,
                endTimeTrackX - zeroProgressTextMargin
        );
        topProgressTextY = chartArea.top - progressTextMargin;
        bottomProgressTextX = clamp(
                bottomProgressX,
                startTimeTrackX + zeroProgressTextMargin,
                endTimeTrackX - hundredProgressTextMargin
        );
        bottomProgressTextY =
                chartArea.bottom + progressTextMargin + progressTextHeight + progressTextDescent;

        float baseTimeY = chartArea.top + chartArea.height() / 2 + maxTimeYShift;
        startTimeTextX = startTimeTrackX + timeTextMargin;
        startTimeTextY = baseTimeY - timeVerticalShift;

        endTimeTextX = endTimeTrackX - timeTextMargin;
        endTimeTextY = baseTimeY + timeVerticalShift;

        bezierPath.rewind();
        bezierPath.moveTo(startTimeTrackX, chartArea.bottom);
        bezierPath.cubicTo(
                bottomProgressX, chartArea.bottom,
                topProgressX, chartArea.top,
                endTimeTrackX, chartArea.top
        );
        invalidate();
    }

    private float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        } else return Math.min(value, max);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //off track
        canvas.drawLine(
                chartArea.left, chartArea.top, chartArea.right, chartArea.top, offTrackPaint
        );
        canvas.drawLine(
                chartArea.left, chartArea.bottom, chartArea.right, chartArea.bottom, offTrackPaint
        );
        canvas.drawPath(bezierPath, offTrackPaint);

        //on track
        canvas.drawLine(
                topProgressX, chartArea.top, endTimeTrackX, chartArea.top, onTrackPaint
        );
        canvas.drawLine(
                startTimeTrackX, chartArea.bottom, bottomProgressX, chartArea.bottom, onTrackPaint
        );

        //time lines
        canvas.drawLine(
                startTimeTrackX, chartArea.top, startTimeTrackX, chartArea.bottom, timeTrackPaint
        );
        canvas.drawLine(
                endTimeTrackX, chartArea.top, endTimeTrackX, chartArea.bottom, timeTrackPaint
        );

        //start time points
        canvas.drawCircle(
                startTimeTrackX, chartArea.top, timePointDotRadius, timeControlDotPaint);
        canvas.drawCircle(
                startTimeTrackX, chartArea.top, timePointDotRadius, timeControlDotStrokePaint
        );
        canvas.drawCircle(
                startTimeTrackX, chartArea.bottom, timePointDotRadius, timeControlDotPaint
        );
        canvas.drawCircle(
                startTimeTrackX, chartArea.bottom, timePointDotRadius, timeControlDotStrokePaint
        );

        //end time points
        canvas.drawCircle(
                endTimeTrackX, chartArea.top, timePointDotRadius, timeControlDotPaint);
        canvas.drawCircle(
                endTimeTrackX, chartArea.top, timePointDotRadius, timeControlDotStrokePaint
        );
        canvas.drawCircle(
                endTimeTrackX, chartArea.bottom, timePointDotRadius, timeControlDotPaint
        );
        canvas.drawCircle(
                endTimeTrackX, chartArea.bottom, timePointDotRadius, timeControlDotStrokePaint
        );

        //control drawables
        android.graphics.Rect dotBounds = slideDotDrawable.getBounds();
        canvas.save();
        canvas.translate(
                topProgressX - dotBounds.width() / 2f,
                chartArea.top - dotBounds.height() / 2f
        );
        slideDotDrawable.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(
                bottomProgressX - dotBounds.width() / 2f,
                chartArea.bottom - dotBounds.height() / 2f
        );
        slideDotDrawable.draw(canvas);
        canvas.restore();

        //time control drawables
        Rect timeControlDrawableBounds = slideTimeDrawable.getBounds();
        float timeControlY =
                chartArea.top + chartArea.height() / 2f - timeControlDrawableBounds.height() / 2f;
        canvas.save();
        canvas.translate(
                startTimeTrackX - timeControlDrawableBounds.width() / 2f,
                timeControlY
        );
        slideTimeDrawable.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(
                endTimeTrackX - timeControlDrawableBounds.width() / 2f,
                timeControlY
        );
        slideTimeDrawable.draw(canvas);
        canvas.restore();

        //draw percent progress
        canvas.drawText(animationConfig.endProgress + "%", topProgressTextX, topProgressTextY, progressTextPaint);
        canvas.drawText(
                animationConfig.startProgress + "%", bottomProgressTextX, bottomProgressTextY, progressTextPaint
        );
        timeTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(animationConfig.startTime + "ms", startTimeTextX, startTimeTextY, timeTextPaint);
        timeTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(animationConfig.endTime + "ms", endTimeTextX, endTimeTextY, timeTextPaint);
    }

    public interface OnAnimationValueChangeListener {

        void onChanged(MessagesController.AnimationConfig config);
    }
}