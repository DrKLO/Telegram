package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.annotation.Keep;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CheckBoxBase {

    private View parentView;
    private Rect bounds = new Rect();
    private RectF rect = new RectF();

    private static Paint paint;
    private static Paint eraser;
    private static Paint checkPaint;
    private static Paint backgroundPaint;

    private Path path = new Path();

    private Bitmap drawBitmap;
    private Canvas bitmapCanvas;

    private boolean attachedToWindow;

    private float backgroundAlpha = 1.0f;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean isChecked;

    private String checkColorKey = Theme.key_checkboxCheck;
    private String backgroundColorKey = Theme.key_chat_serviceBackground;
    private String background2ColorKey = Theme.key_chat_serviceBackground;

    private boolean drawUnchecked = true;
    private int drawBackgroundAsArc;

    private float size = 21;

    private ProgressDelegate progressDelegate;

    public interface ProgressDelegate {
        void setProgress(float progress);
    }

    public CheckBoxBase(View parent) {
        parentView = parent;
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser.setColor(0);
            eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setStyle(Paint.Style.STROKE);

            checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
        }
        checkPaint.setStrokeWidth(AndroidUtilities.dp(1.9f));
        backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));

        drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(size), AndroidUtilities.dp(size), Bitmap.Config.ARGB_4444);
        bitmapCanvas = new Canvas(drawBitmap);
    }

    public void onAttachedToWindow() {
        attachedToWindow = true;
    }

    public void onDetachedFromWindow() {
        attachedToWindow = false;
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds.left = x;
        bounds.top = y;
        bounds.right = x + width;
        bounds.bottom = y + height;
    }

    public void setDrawUnchecked(boolean value) {
        drawUnchecked = value;
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        if (parentView.getParent() != null) {
            View parent = (View) parentView.getParent();
            parent.invalidate();
        }
        parentView.invalidate();
        if (progressDelegate != null) {
            progressDelegate.setProgress(value);
        }
    }

    public void setProgressDelegate(ProgressDelegate delegate) {
        progressDelegate = delegate;
    }

    public void setSize(int value) {
        size = value;
    }

    public float getProgress() {
        return progress;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setDrawBackgroundAsArc(int type) {
        drawBackgroundAsArc = type;
        if (type == 4) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.9f));
        } else if (type == 3) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
        } else if (type != 0) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        }
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(checkAnimator)) {
                    checkAnimator = null;
                }
            }
        });
        checkAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        checkAnimator.setDuration(200);
        checkAnimator.start();
    }

    public void setColor(String background, String background2, String check) {
        backgroundColorKey = background;
        background2ColorKey = background2;
        checkColorKey = check;
    }

    public void setBackgroundAlpha(float alpha) {
        backgroundAlpha = alpha;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checked == isChecked) {
            return;
        }
        isChecked = checked;

        if (attachedToWindow && animated) {
            animateToCheckedState(checked);
        } else {
            cancelCheckAnimator();
            setProgress(checked ? 1.0f : 0.0f);
        }
    }

    public void draw(Canvas canvas) {
        if (drawBitmap == null) {
            return;
        }

        drawBitmap.eraseColor(0);
        float rad = AndroidUtilities.dp(size / 2);
        float outerRad = rad;
        if (drawBackgroundAsArc != 0) {
            outerRad -= AndroidUtilities.dp(0.2f);
        }

        float roundProgress = progress >= 0.5f ? 1.0f : progress / 0.5f;

        int cx = bounds.centerX();
        int cy = bounds.centerY();

        if (backgroundColorKey != null) {
            if (drawUnchecked) {
                paint.setColor((Theme.getServiceMessageColor() & 0x00ffffff) | 0x28000000);
                backgroundPaint.setColor(Theme.getColor(checkColorKey));
            } else {
                backgroundPaint.setColor(AndroidUtilities.getOffsetColor(0x00ffffff, Theme.getColor(background2ColorKey != null ? background2ColorKey : checkColorKey), progress, backgroundAlpha));
            }
        } else {
            if (drawUnchecked) {
                paint.setColor(Color.argb((int) (25 * backgroundAlpha), 0, 0, 0));
                backgroundPaint.setColor(AndroidUtilities.getOffsetColor(0xffffffff, Theme.getColor(checkColorKey), progress, backgroundAlpha));
            } else {
                backgroundPaint.setColor(AndroidUtilities.getOffsetColor(0x00ffffff, Theme.getColor(background2ColorKey != null ? background2ColorKey : checkColorKey), progress, backgroundAlpha));
            }
        }

        if (drawUnchecked) {
            canvas.drawCircle(cx, cy, rad, paint);
        }
        paint.setColor(Theme.getColor(checkColorKey));
        if (drawBackgroundAsArc == 0) {
            canvas.drawCircle(cx, cy, rad, backgroundPaint);
        } else {
            rect.set(cx - outerRad, cy - outerRad, cx + outerRad, cy + outerRad);
            int startAngle;
            int sweepAngle;
            if (drawBackgroundAsArc == 1) {
                startAngle = -90;
                sweepAngle = (int) (-270 * progress);
            } else {
                startAngle = 90;
                sweepAngle = (int) (270 * progress);
            }
            canvas.drawArc(rect, startAngle, sweepAngle, false, backgroundPaint);
        }

        if (roundProgress > 0) {
            float checkProgress = progress < 0.5f ? 0.0f : (progress - 0.5f) / 0.5f;

            if (!drawUnchecked && backgroundColorKey != null) {
                paint.setColor(Theme.getColor(backgroundColorKey));
            } else {
                paint.setColor(Theme.getColor(Theme.key_checkbox));
            }
            if (checkColorKey != null) {
                checkPaint.setColor(Theme.getColor(checkColorKey));
            } else {
                checkPaint.setColor(Theme.getColor(Theme.key_checkboxCheck));
            }

            rad -= AndroidUtilities.dp(0.5f);
            bitmapCanvas.drawCircle(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2, rad, paint);
            bitmapCanvas.drawCircle(drawBitmap.getWidth() / 2, drawBitmap.getWidth() / 2, rad * (1.0f - roundProgress), eraser);
            canvas.drawBitmap(drawBitmap, cx - drawBitmap.getWidth() / 2, cy - drawBitmap.getHeight() / 2, null);

            if (checkProgress != 0) {
                path.reset();

                float checkSide = AndroidUtilities.dp(9) * checkProgress;
                float smallCheckSide = AndroidUtilities.dp(4) * checkProgress;
                int x = cx - AndroidUtilities.dp(1.5f);
                int y = cy + AndroidUtilities.dp(4);
                float side = (float) Math.sqrt(smallCheckSide * smallCheckSide / 2.0f);
                path.moveTo(x - side, y - side);
                path.lineTo(x, y);
                side = (float) Math.sqrt(checkSide * checkSide / 2.0f);
                path.lineTo(x + side, y - side);
                canvas.drawPath(path, checkPaint);
            }
        }
    }
}
