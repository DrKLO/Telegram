package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class FlatCheckBox extends View {

    boolean attached;
    public boolean checked;
    public boolean enabled = true;

    String text;
    TextPaint textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint outLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    int colorActive;
    int colorInactive;

    int colorTextActive;

    int HEIGHT = AndroidUtilities.dp(36);
    int INNER_PADDING = AndroidUtilities.dp(22);
    int TRANSLETE_TEXT = AndroidUtilities.dp(8);

    int P = AndroidUtilities.dp(2);

    RectF rectF = new RectF();

    float progress = 0;

    ValueAnimator checkAnimator;

    public FlatCheckBox(Context context) {
        super(context);
        textPaint.setTextSize(AndroidUtilities.dp(14));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        outLinePaint.setStrokeWidth(AndroidUtilities.dpf2(1.5f));
        outLinePaint.setStyle(Paint.Style.STROKE);

        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void recolor(int c) {
        colorActive = Theme.getColor(Theme.key_windowBackgroundWhite);
        colorTextActive = Color.WHITE;
        colorInactive = c;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }

    public void setChecked(boolean enabled) {
        setChecked(enabled, true);
    }

    public void setChecked(boolean enabled, boolean animate) {
        checked = enabled;
        if (!attached || !animate) {
            progress = enabled ? 1f : 0f;
        } else {
            if (checkAnimator != null) {
                checkAnimator.removeAllListeners();
                checkAnimator.cancel();
            }
            checkAnimator = ValueAnimator.ofFloat(progress, enabled ? 1 : 0);
            checkAnimator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            checkAnimator.setDuration(300);
            checkAnimator.start();
        }
    }

    public void setText(String text) {
        this.text = text;
        requestLayout();
    }

    int lastW = 0;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int textW = text == null ? 0 : (int) textPaint.measureText(text);
        textW += INNER_PADDING << 1;

        setMeasuredDimension(textW + P * 2, HEIGHT + AndroidUtilities.dp(4));

        if (getMeasuredWidth() != lastW) {
            rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            rectF.inset(P + outLinePaint.getStrokeWidth() / 2, P + outLinePaint.getStrokeWidth() / 2 + AndroidUtilities.dp(2));
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);


        float textTranslation = 0f;

        if (progress <= 0.5f) {
            float checkProgress = textTranslation = progress / 0.5f;
            int rD = (int) ((Color.red(colorInactive) - Color.red(colorActive)) * checkProgress);
            int gD = (int) ((Color.green(colorInactive) - Color.green(colorActive)) * checkProgress);
            int bD = (int) ((Color.blue(colorInactive) - Color.blue(colorActive)) * checkProgress);
            int c = Color.rgb(Color.red(colorActive) + rD, Color.green(colorActive) + gD, Color.blue(colorActive) + bD);

            fillPaint.setColor(c);

            rD = (int) ((Color.red(colorTextActive) - Color.red(colorInactive)) * checkProgress);
            gD = (int) ((Color.green(colorTextActive) - Color.green(colorInactive)) * checkProgress);
            bD = (int) ((Color.blue(colorTextActive) - Color.blue(colorInactive)) * checkProgress);
            c = Color.rgb(Color.red(colorInactive) + rD, Color.green(colorInactive) + gD, Color.blue(colorInactive) + bD);

            textPaint.setColor(c);
        } else {
            textTranslation = 1f;
            textPaint.setColor(colorTextActive);
            fillPaint.setColor(colorInactive);
        }


        int heightHalf = (getMeasuredHeight() >> 1);

        outLinePaint.setColor(colorInactive);
        canvas.drawRoundRect(rectF, HEIGHT / 2f, HEIGHT / 2f, fillPaint);
        canvas.drawRoundRect(rectF, HEIGHT / 2f, HEIGHT / 2f, outLinePaint);
        if (text != null) {
            canvas.drawText(text,
                    (getMeasuredWidth() >> 1) + (textTranslation * TRANSLETE_TEXT),
                    heightHalf + (textPaint.getTextSize() * 0.35f),
                    textPaint
            );
        }

        float bounceProgress = 2.0f - progress / 0.5f;
        canvas.save();
        canvas.scale(0.9f, 0.9f, AndroidUtilities.dpf2(7f), heightHalf);
        canvas.translate(AndroidUtilities.dp(12), heightHalf - AndroidUtilities.dp(9));

        if (progress > 0.5f) {
            checkPaint.setColor(colorTextActive);
            int endX = (int) (AndroidUtilities.dpf2(7f) - AndroidUtilities.dp(4) * (1.0f - bounceProgress));
            int endY = (int) (AndroidUtilities.dpf2(13f) - AndroidUtilities.dp(4) * (1.0f - bounceProgress));
            canvas.drawLine(AndroidUtilities.dpf2(7f), (int) AndroidUtilities.dpf2(13f), endX, endY, checkPaint);
            endX = (int) (AndroidUtilities.dpf2(7f) + AndroidUtilities.dp(8) * (1.0f - bounceProgress));
            endY = (int) (AndroidUtilities.dpf2(13f) - AndroidUtilities.dp(8) * (1.0f - bounceProgress));
            canvas.drawLine((int) AndroidUtilities.dpf2(7f), (int) AndroidUtilities.dpf2(13f), endX, endY, checkPaint);
        }
        canvas.restore();
    }

    public void denied() {
        AndroidUtilities.shakeView(this);
    }
}
