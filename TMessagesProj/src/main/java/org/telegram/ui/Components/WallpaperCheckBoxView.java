package org.telegram.ui.Components;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.util.Property;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class WallpaperCheckBoxView extends View {

    private Paint eraserPaint;
    private Paint checkPaint;
    private TextPaint textPaint;
    private Paint backgroundPaint;

    private String currentText;
    private int currentTextSize;
    private int maxTextSize;
    private RectF rect;

    private boolean isChecked;
    private Canvas drawCanvas;
    private Bitmap drawBitmap;
    private float progress;
    private ObjectAnimator checkAnimator;

    private int backgroundColor;
    private int backgroundGradientColor;
    private LinearGradient colorGradient;

    private final static float progressBounceDiff = 0.2f;

    public final Property<WallpaperCheckBoxView, Float> PROGRESS_PROPERTY = new AnimationProperties.FloatProperty<WallpaperCheckBoxView>("progress") {
        @Override
        public void setValue(WallpaperCheckBoxView object, float value) {
            progress = value;
            invalidate();
        }

        @Override
        public Float get(WallpaperCheckBoxView object) {
            return progress;
        }
    };

    public WallpaperCheckBoxView(Context context, boolean check) {
        super(context);
        rect = new RectF();

        if (check) {
            drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Bitmap.Config.ARGB_4444);
            drawCanvas = new Canvas(drawBitmap);
        }

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(14));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
        checkPaint.setColor(0);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setColor(0);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setText(String text, int current, int max) {
        currentText = text;
        currentTextSize = current;
        maxTextSize = max;
    }

    public void setBackgroundColor(int color) {
        colorGradient = null;
        backgroundColor = color;
        invalidate();
    }

    public void setBackgroundGradientColor(int color) {
        colorGradient = null;
        backgroundGradientColor = color;
        invalidate();
    }

    public TextPaint getTextPaint() {
        return textPaint;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(maxTextSize + AndroidUtilities.dp(14 * 2 + 28), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_actionBackgroundPaint);

        textPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        int x = (getMeasuredWidth() - currentTextSize - AndroidUtilities.dp(28)) / 2;
        canvas.drawText(currentText, x + AndroidUtilities.dp(28), AndroidUtilities.dp(21), textPaint);

        canvas.save();
        canvas.translate(x, AndroidUtilities.dp(7));
        if (drawBitmap != null) {
            float checkProgress;
            float bounceProgress;
            if (progress <= 0.5f) {
                bounceProgress = checkProgress = progress / 0.5f;
            } else {
                bounceProgress = 2.0f - progress / 0.5f;
                checkProgress = 1.0f;
            }

            float bounce = AndroidUtilities.dp(1) * bounceProgress;
            rect.set(bounce, bounce, AndroidUtilities.dp(18) - bounce, AndroidUtilities.dp(18) - bounce);

            drawBitmap.eraseColor(0);
            backgroundPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
            drawCanvas.drawRoundRect(rect, rect.width() / 2, rect.height() / 2, backgroundPaint);

            if (checkProgress != 1) {
                float rad = Math.min(AndroidUtilities.dp(7), AndroidUtilities.dp(7) * checkProgress + bounce);
                rect.set(AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(16) - rad, AndroidUtilities.dp(16) - rad);
                drawCanvas.drawRoundRect(rect, rect.width() / 2, rect.height() / 2, eraserPaint);
            }

            if (progress > 0.5f) {
                int endX = (int) (AndroidUtilities.dp(7.3f) - AndroidUtilities.dp(2.5f) * (1.0f - bounceProgress));
                int endY = (int) (AndroidUtilities.dp(13) - AndroidUtilities.dp(2.5f) * (1.0f - bounceProgress));
                drawCanvas.drawLine(AndroidUtilities.dp(7.3f), AndroidUtilities.dp(13), endX, endY, checkPaint);
                endX = (int) (AndroidUtilities.dp(7.3f) + AndroidUtilities.dp(6) * (1.0f - bounceProgress));
                endY = (int) (AndroidUtilities.dp(13) - AndroidUtilities.dp(6) * (1.0f - bounceProgress));
                drawCanvas.drawLine(AndroidUtilities.dp(7.3f), AndroidUtilities.dp(13), endX, endY, checkPaint);
            }
            canvas.drawBitmap(drawBitmap, 0, 0, null);
        } else {
            rect.set(0, 0, AndroidUtilities.dp(18), AndroidUtilities.dp(18));
            if (backgroundGradientColor != 0) {
                if (colorGradient == null) {
                    colorGradient = new LinearGradient(rect.left, rect.bottom, rect.left, rect.top, new int[]{backgroundColor, backgroundGradientColor}, null, Shader.TileMode.CLAMP);
                    backgroundPaint.setShader(colorGradient);
                }
                backgroundPaint.setColor(backgroundColor);
            } else {
                backgroundPaint.setColor(backgroundColor);
                backgroundPaint.setShader(null);
            }
            canvas.drawRoundRect(rect, rect.width() / 2, rect.height() / 2, backgroundPaint);
        }
        canvas.restore();
    }

    private void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, PROGRESS_PROPERTY, newCheckedState ? 1.0f : 0.0f);
        checkAnimator.setDuration(300);
        checkAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checked == isChecked) {
            return;
        }
        isChecked = checked;
        if (animated) {
            animateToCheckedState(checked);
        } else {
            cancelCheckAnimator();
            progress = checked ? 1.0f : 0.0f;
            invalidate();
        }
    }

    public boolean isChecked() {
        return isChecked;
    }
}