package org.telegram.ui.Components.Crop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class CropRotationWheel extends FrameLayout {

    public interface RotationWheelListener {
        void onStart();
        void onChange(float angle);
        void onEnd(float angle);

        void aspectRatioPressed();
        boolean rotate90Pressed();
        boolean mirror();
    }

    private static final int MAX_ANGLE = 45;
    private static final int DELTA_ANGLE = 5;

    private Paint whitePaint;
    private Paint bluePaint;

    private ImageView aspectRatioButton;
    private ImageView rotation90Button;
    private ImageView mirrorButton;
//    private TextView degreesLabel;
    private String degreesText;
    private TextPaint degreesTextPaint;

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

        mirrorButton = new ImageView(context);
        mirrorButton.setImageResource(R.drawable.msg_photo_flip);
        mirrorButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        mirrorButton.setScaleType(ImageView.ScaleType.CENTER);
        mirrorButton.setOnClickListener(v -> {
            if (rotationListener != null) {
                setMirrored(rotationListener.mirror());
            }
        });
        mirrorButton.setOnLongClickListener(v -> {
            aspectRatioButton.callOnClick();
            return true;
        });
        mirrorButton.setContentDescription(LocaleController.getString("AccDescrMirror", R.string.AccDescrMirror));
        addView(mirrorButton, LayoutHelper.createFrame(70, 64, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        aspectRatioButton = new ImageView(context);
        aspectRatioButton.setImageResource(R.drawable.msg_photo_cropfix);
        aspectRatioButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        aspectRatioButton.setScaleType(ImageView.ScaleType.CENTER);
        aspectRatioButton.setOnClickListener(v -> {
            if (rotationListener != null) {
                rotationListener.aspectRatioPressed();
            }
        });
        aspectRatioButton.setVisibility(GONE);
        aspectRatioButton.setContentDescription(LocaleController.getString("AccDescrAspectRatio", R.string.AccDescrAspectRatio));
        addView(aspectRatioButton, LayoutHelper.createFrame(70, 64, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        rotation90Button = new ImageView(context);
        rotation90Button.setImageResource(R.drawable.msg_photo_rotate);
        rotation90Button.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        rotation90Button.setScaleType(ImageView.ScaleType.CENTER);
        rotation90Button.setOnClickListener(v -> {
            if (rotationListener != null) {
                setRotated(rotationListener.rotate90Pressed());
            }
        });
        rotation90Button.setContentDescription(LocaleController.getString("AccDescrRotate", R.string.AccDescrRotate));
        addView(rotation90Button, LayoutHelper.createFrame(70, 64, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        degreesTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        degreesTextPaint.setColor(Color.WHITE);
        degreesTextPaint.setTextSize(AndroidUtilities.dp(14));

        setWillNotDraw(false);

        setRotation(0.0f, false);
    }

    public void setFreeform(boolean freeform) {
        //aspectRatioButton.setVisibility(freeform ? VISIBLE : GONE);
    }

    public void setMirrored(boolean value) {
        mirrorButton.setColorFilter(value ? new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY) : null);
    }

    public void setRotated(boolean value) {
        rotation90Button.setColorFilter(value ? new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY) : null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(width, AndroidUtilities.dp(400)), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public void reset(boolean resetMirror) {
        setRotation(0.0f, false);
        if (resetMirror) {
            setMirrored(false);
        }
        setRotated(false);
    }

    public void setListener(RotationWheelListener listener) {
        rotationListener = listener;
    }

    public void setRotation(float rotation, boolean animated) {
        this.rotation = rotation;
        float value = rotation;
        if (Math.abs(value) < 0.1 - 0.001)
            value = Math.abs(value);
        degreesText = String.format("%.1fº", value);

        invalidate();
    }

    public float getRotation() {
        return this.rotation;
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

            if (rotationListener != null) {
                rotationListener.onStart();
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (rotationListener != null)
                rotationListener.onEnd(this.rotation);
            AndroidUtilities.makeAccessibilityAnnouncement(String.format("%.1f°", this.rotation));
        } else if (action == MotionEvent.ACTION_MOVE) {
            float delta = prevX - x;

            float newAngle = this.rotation + (float)(delta / AndroidUtilities.density / Math.PI / 1.65f);
            newAngle = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, newAngle));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    if (Math.abs(newAngle - MAX_ANGLE) < 0.001f && Math.abs(this.rotation - MAX_ANGLE) >= 0.001f ||
                        Math.abs(newAngle - -MAX_ANGLE) < 0.001f && Math.abs(this.rotation - -MAX_ANGLE) >= 0.001f) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } else if (Math.floor(this.rotation / 2.5f) != Math.floor(newAngle / 2.5f)) {
                        AndroidUtilities.vibrateCursor(this);
                    }
                } catch (Exception ignore) {}
            }

            if (Math.abs(newAngle - this.rotation) > 0.001) {
                if (Math.abs(newAngle) < 0.05)
                    newAngle = 0;

                setRotation(newAngle, false);

                if (rotationListener != null) {
                    rotationListener.onChange(this.rotation);
                }

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

        float tx = (width - degreesTextPaint.measureText(degreesText)) / 2;
        float ty = AndroidUtilities.dp(14);
        canvas.drawText(degreesText, tx, ty, degreesTextPaint);
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
