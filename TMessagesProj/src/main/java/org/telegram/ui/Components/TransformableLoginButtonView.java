package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TransformableLoginButtonView extends View {
    public final static int TRANSFORM_OPEN_ARROW = 0, TRANSFORM_ARROW_CHECK = 1;
    private final static float BUTTON_RADIUS_DP = 6, CIRCLE_RADIUS_DP = 32, ARROW_PADDING = 21,
            ARROW_BACK_SIZE = 9, LEFT_CHECK_LINE = 8, RIGHT_CHECK_LINE = 16;
    private final static float BUTTON_TEXT_IN = 0.6f, ARROW_IN = 0.4f;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TRANSFORM_OPEN_ARROW,
            TRANSFORM_ARROW_CHECK
    })
    private @interface TransformType {}

    private float progress;
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TextPaint textPaint;
    private String buttonText;
    private float buttonWidth;

    private Drawable rippleDrawable;
    private boolean drawBackground = true;

    @TransformType
    private int transformType = TRANSFORM_OPEN_ARROW;

    private RectF rect = new RectF();

    public TransformableLoginButtonView(Context context) {
        super(context);

        backgroundPaint.setColor(Theme.getColor(Theme.key_chats_actionBackground));

        outlinePaint.setStrokeWidth(AndroidUtilities.dp(2));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setDrawBackground(boolean drawBackground) {
        this.drawBackground = drawBackground;
    }

    public void setRippleDrawable(Drawable d) {
        rippleDrawable = d;
        invalidate();
    }

    public void setTransformType(@TransformType int transformType) {
        this.transformType = transformType;
        invalidate();
    }

    public void setBackgroundColor(@ColorInt int color) {
        backgroundPaint.setColor(color);
        invalidate();
    }

    public void setColor(@ColorInt int color) {
        outlinePaint.setColor(color);
        invalidate();
    }

    public void setButtonText(TextPaint textPaint, String buttonText) {
        this.textPaint = textPaint;
        this.buttonText = buttonText;
        outlinePaint.setColor(textPaint.getColor());
        buttonWidth = textPaint.measureText(buttonText);
    }

    public void setProgress(float p) {
        progress = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawBackground) {
            boolean animateCornerRadius = transformType == TRANSFORM_OPEN_ARROW;
            float rad = AndroidUtilities.dp(BUTTON_RADIUS_DP + (CIRCLE_RADIUS_DP - BUTTON_RADIUS_DP) * (animateCornerRadius ? progress : 1f));
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, rad, rad, backgroundPaint);
        }

        switch (transformType) {
            case TRANSFORM_OPEN_ARROW:
                if (textPaint != null && buttonText != null) {
                    int alpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (alpha * (1f - Math.min(BUTTON_TEXT_IN, progress) / BUTTON_TEXT_IN)));
                    canvas.drawText(buttonText, (getWidth() - buttonWidth) / 2f, getHeight() / 2f + textPaint.getTextSize() / 2f - AndroidUtilities.dp(1.75f), textPaint);
                    textPaint.setAlpha(alpha);
                }

                float arrowProgress = (Math.max(ARROW_IN, progress) - ARROW_IN) / (1f - ARROW_IN);
                if (arrowProgress != 0) {
                    float endX = AndroidUtilities.dp(ARROW_PADDING) + (getWidth() - AndroidUtilities.dp(ARROW_PADDING) * 2) * arrowProgress;
                    float centerY = getHeight() / 2f;
                    canvas.drawLine(AndroidUtilities.dp(ARROW_PADDING), centerY, endX, centerY, outlinePaint);

                    float backSize = AndroidUtilities.dp(ARROW_BACK_SIZE) * arrowProgress;
                    float backX = (float) (endX - Math.cos(Math.PI / 4) * backSize);
                    float backY = (float) (Math.sin(Math.PI / 4) * backSize);

                    canvas.drawLine(endX, centerY, backX, centerY - backY, outlinePaint);
                    canvas.drawLine(endX, centerY, backX, centerY + backY, outlinePaint);
                }
                break;
            case TRANSFORM_ARROW_CHECK:
                float startX = AndroidUtilities.dp(ARROW_PADDING);
                float endX = getWidth() - AndroidUtilities.dp(ARROW_PADDING);
                float centerY = getHeight() / 2f;

                canvas.save();
                canvas.translate(-AndroidUtilities.dp(2) * progress, 0);
                canvas.rotate(90f * progress, getWidth() / 2f, getHeight() / 2f);

                canvas.drawLine(startX + (endX - startX) * progress, centerY, endX, centerY, outlinePaint);

                int leftSize = AndroidUtilities.dp(ARROW_BACK_SIZE + (LEFT_CHECK_LINE - ARROW_BACK_SIZE) * progress);
                int rightSize = AndroidUtilities.dp(ARROW_BACK_SIZE + (RIGHT_CHECK_LINE - ARROW_BACK_SIZE) * progress);

                canvas.drawLine(endX, centerY, (float) (endX - leftSize * Math.cos(Math.PI / 4)), (float) (centerY + leftSize * Math.sin(Math.PI / 4)), outlinePaint);
                canvas.drawLine(endX, centerY, (float) (endX - rightSize * Math.cos(Math.PI / 4)), (float) (centerY - rightSize * Math.sin(Math.PI / 4)), outlinePaint);

                canvas.restore();
                break;
        }

        if (rippleDrawable != null) {
            rippleDrawable.setBounds(0, 0, getWidth(), getHeight());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rippleDrawable.setHotspotBounds(0, 0, getWidth(), getHeight());
            }
            rippleDrawable.draw(canvas);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (rippleDrawable != null) {
            rippleDrawable.setState(getDrawableState());
            invalidate();
        }
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (rippleDrawable != null) {
            rippleDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (rippleDrawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rippleDrawable.setHotspot(x, y);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || rippleDrawable != null && who == rippleDrawable;
    }
}
