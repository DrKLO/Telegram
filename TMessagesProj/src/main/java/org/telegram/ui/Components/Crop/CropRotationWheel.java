package org.telegram.ui.Components.Crop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class CropRotationWheel extends FrameLayout {

    public interface RotationWheelListener {
        void onStart();
        void onChange(float angle);
        void onEnd(float angle);

        void aspectRatioPressed();
        void rotate90Pressed();
    }

    private static final int MAX_ANGLE = 45;
    private static final int DELTA_ANGLE = 5;

    private Paint whitePaint;
    private Paint bluePaint;

    private ImageView aspectRatioButton;
    private ImageView rotation90Button;
    private TextView degreesLabel;

    protected float rotation;
    private RectF tempRect;
    private float prevX;

    private RotationWheelListener rotationListener;

    public CropRotationWheel(Context context) {
        super(context);

        tempRect = new RectF(0, 0, 0, 0);

        whitePaint = new Paint();
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setAlpha(255);
        whitePaint.setAntiAlias(true);

        bluePaint = new Paint();
        bluePaint.setStyle(Paint.Style.FILL);
        bluePaint.setColor(0xff51bdf3);
        bluePaint.setAlpha(255);
        bluePaint.setAntiAlias(true);

        aspectRatioButton = new ImageView(context);
        aspectRatioButton.setImageResource(R.drawable.tool_cropfix);
        aspectRatioButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        aspectRatioButton.setScaleType(ImageView.ScaleType.CENTER);
        aspectRatioButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rotationListener != null)
                    rotationListener.aspectRatioPressed();
            }
        });
        addView(aspectRatioButton, LayoutHelper.createFrame(70, 64, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        rotation90Button = new ImageView(context);
        rotation90Button.setImageResource(R.drawable.tool_rotate);
        rotation90Button.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        rotation90Button.setScaleType(ImageView.ScaleType.CENTER);
        rotation90Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rotationListener != null)
                    rotationListener.rotate90Pressed();
            }
        });
        addView(rotation90Button, LayoutHelper.createFrame(70, 64, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        degreesLabel = new TextView(context);
        degreesLabel.setTextColor(Color.WHITE);
        addView(degreesLabel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        setWillNotDraw(false);

        setRotation(0.0f, false);
    }

    public void setFreeform(boolean freeform) {
        aspectRatioButton.setVisibility(freeform ? VISIBLE : GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(width, AndroidUtilities.dp(400)), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public void reset() {
        setRotation(0.0f, false);
    }

    public void setListener(RotationWheelListener listener) {
        rotationListener = listener;
    }

    public void setRotation(float rotation, boolean animated) {
        this.rotation = rotation;
        float value = this.rotation;
        if (Math.abs(value) < 0.1 - 0.001)
            value = Math.abs(value);
        degreesLabel.setText(String.format("%.1fº", value));

        invalidate();
    }

    public void setAspectLock(boolean enabled) {
        aspectRatioButton.setColorFilter(enabled ? new PorterDuffColorFilter(0xff51bdf3, PorterDuff.Mode.MULTIPLY) : null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float x = ev.getX();

        if (action == MotionEvent.ACTION_DOWN) {
            prevX = x;

            if (rotationListener != null)
                rotationListener.onStart();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (rotationListener != null)
                rotationListener.onEnd(this.rotation);
        } else if (action == MotionEvent.ACTION_MOVE) {
            float delta = prevX - x;

            float newAngle = this.rotation + (float)(delta / AndroidUtilities.density / Math.PI / 1.65f);
            newAngle = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, newAngle));

            if (Math.abs(newAngle - this.rotation) > 0.001) {
                if (Math.abs(newAngle) < 0.05)
                    newAngle = 0;

                setRotation(newAngle, false);

                if (rotationListener != null)
                    rotationListener.onChange(this.rotation);

                prevX = x;
            }
        }

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float angle = -rotation * 2;
        float delta = angle % DELTA_ANGLE;
        int segments = (int)Math.floor(angle / DELTA_ANGLE);

        for (int i = 0; i < 16; i++) {
            Paint paint = whitePaint;
            int a = i;
            if (a < segments || (a == 0 && delta < 0))
                paint = bluePaint;

            drawLine(canvas, a, delta, width, height, (a == segments || a == 0 && segments == - 1), paint);

            if (i != 0) {
                a = -i;
                paint = a > segments ? bluePaint : whitePaint;
                drawLine(canvas, a, delta, width, height, a == segments + 1, paint);
            }
        }

        bluePaint.setAlpha(255);

        tempRect.left = (width - AndroidUtilities.dp(2.5f)) / 2;
        tempRect.top = (height - AndroidUtilities.dp(22)) / 2;
        tempRect.right = (width + AndroidUtilities.dp(2.5f)) / 2;
        tempRect.bottom =  (height + AndroidUtilities.dp(22)) / 2;
        canvas.drawRoundRect(tempRect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), bluePaint);
    }

    protected void drawLine(Canvas canvas, int i, float delta, int width, int height, boolean center, Paint paint) {
        int radius = (int)(width / 2.0f - AndroidUtilities.dp(70));

        float angle = 90 - (i * DELTA_ANGLE + delta);
        int val = (int)(radius * Math.cos(Math.toRadians(angle)));
        int x = width / 2 + val;

        float f = Math.abs(val) / (float)radius;
        int alpha = Math.min(255, Math.max(0, (int)((1.0f - f * f) * 255)));

        if (center)
            paint = bluePaint;

        paint.setAlpha(alpha);

        int w = center ? 4 : 2;
        int h = center ? AndroidUtilities.dp(16) : AndroidUtilities.dp(12);

        canvas.drawRect(x - w / 2, (height - h) / 2, x + w / 2, (height + h) / 2, paint);
    }
}
