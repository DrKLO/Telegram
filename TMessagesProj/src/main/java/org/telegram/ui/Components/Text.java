package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class Text {

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout layout;
    private float width, left;

    public Text(CharSequence text, int textSizeDp) {
        this(text, textSizeDp, null);
    }

    public Text(CharSequence text, int textSizeDp, Typeface typeface) {
        paint.setTextSize(dp(textSizeDp));
        paint.setTypeface(typeface);
        setText(text);
    }

    public void setText(CharSequence text) {
        layout = new StaticLayout(text, paint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        width = layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;
        left = layout.getLineCount() > 0 ? layout.getLineLeft(0) : 0;
    }

    public float getTextSize() {
        return paint.getTextSize();
    }

    public boolean isEmpty() {
        return layout == null || TextUtils.isEmpty(layout.getText());
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    private int ellipsizeWidth = -1;
    public Text ellipsize(int width) {
        ellipsizeWidth = width;
        return this;
    }

    public void draw(Canvas canvas, int color) {
        if (layout == null) {
            return;
        }
        draw(canvas, 0, layout.getHeight() / 2f, color, 1f);
    }

    public void draw(Canvas canvas, float x, float cy, int color, float alpha) {
        if (layout == null) {
            return;
        }
        paint.setColor(color);
        if (alpha != 1f) {
            paint.setAlpha((int) (paint.getAlpha() * alpha));
        }
        canvas.save();
        canvas.translate(x - left, cy - layout.getHeight() / 2f);
        draw(canvas);
        canvas.restore();
    }

    public void draw(Canvas canvas, float x, float cy) {
        if (layout == null) {
            return;
        }
        canvas.save();
        canvas.translate(x - left, cy - layout.getHeight() / 2f);
        draw(canvas);
        canvas.restore();
    }

    private LinearGradient ellipsizeGradient;
    private Matrix ellipsizeMatrix;
    private Paint ellipsizePaint;

    public void draw(Canvas canvas) {
        if (layout == null) {
            return;
        }
        if (ellipsizeWidth >= 0 && width > ellipsizeWidth) {
            canvas.saveLayerAlpha(0, 0, ellipsizeWidth, layout.getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        }
        layout.draw(canvas);
        if (ellipsizeWidth >= 0 && width > ellipsizeWidth) {
            if (ellipsizeGradient == null) {
                ellipsizeGradient = new LinearGradient(0, 0, dp(8), 0, new int[] { 0x00ffffff, 0xffffffff }, new float[] {0, 1}, Shader.TileMode.CLAMP);
                ellipsizeMatrix = new Matrix();
                ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                ellipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                ellipsizePaint.setShader(ellipsizeGradient);
            }
            canvas.save();
            ellipsizeMatrix.reset();
            ellipsizeMatrix.postTranslate(ellipsizeWidth - left - dp(8), 0);
            ellipsizeGradient.setLocalMatrix(ellipsizeMatrix);
            canvas.drawRect(ellipsizeWidth - left - dp(8), 0, ellipsizeWidth - left, layout.getHeight(), ellipsizePaint);
            canvas.restore();
            canvas.restore();
        }
    }

    public float getWidth() {
        return ellipsizeWidth >= 0 ? Math.min(ellipsizeWidth, width) : width;
    }

    public float getCurrentWidth() {
        return width;
    }

    @NonNull
    public CharSequence getText() {
        if (layout == null || layout.getText() == null) {
            return "";
        }
        return layout.getText();
    }
}
