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
import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class Text {

    private final TextPaint paint;
    private StaticLayout layout;
    private float width, left;
    private float maxWidth = 9999;

    public Text(CharSequence text, TextPaint paint) {
        this.paint = paint;
        setText(text);
    }

    public Text(CharSequence text, float textSizeDp) {
        this(text, textSizeDp, null);
    }

    public Text(CharSequence text, float textSizeDp, Typeface typeface) {
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(dp(textSizeDp));
        paint.setTypeface(typeface);
        setText(text);
    }

    public Text setTextSizePx(float px) {
        paint.setTextSize(px);
        return this;
    }

    public void setText(CharSequence text) {
        layout = new StaticLayout(AndroidUtilities.replaceNewLines(text), paint, (int) Math.max(maxWidth, 1), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        width = 0;
        left = layout.getWidth();
        for (int i = 0; i < layout.getLineCount(); ++i) {
            width = Math.max(width, layout.getLineWidth(i));
            left = Math.min(left, layout.getLineLeft(i));
        }
    }

    public Text setMaxWidth(float maxWidth) {
        this.maxWidth = maxWidth;
        setText(layout.getText());
        return this;
    }

    public int getLineCount() {
        return layout.getLineCount();
    }

    private boolean hackClipBounds;
    public Text hackClipBounds() {
        this.hackClipBounds = true;
        return this;
    }

    private boolean doNotSave;
    public Text doNotSave() {
        this.doNotSave = true;
        return this;
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

    private float ellipsizeWidth = -1;
    public Text ellipsize(float width) {
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
        final int wasAlpha = paint.getAlpha();
        if (alpha != 1f) {
            paint.setAlpha((int) (wasAlpha * alpha));
        }
        if (!doNotSave) {
            canvas.save();
        }
        canvas.translate(x, cy - layout.getHeight() / 2f);
        draw(canvas);
        if (!doNotSave) {
            canvas.restore();
        }
        paint.setAlpha(wasAlpha);
    }

    public void draw(Canvas canvas, float x, float cy) {
        if (layout == null) {
            return;
        }
        if (!doNotSave) {
            canvas.save();
        }
        canvas.translate(x, cy - layout.getHeight() / 2f);
        draw(canvas);
        if (!doNotSave) {
            canvas.restore();
        }
    }

    private LinearGradient ellipsizeGradient;
    private Matrix ellipsizeMatrix;
    private Paint ellipsizePaint;
    private int vertPad;

    public Text setVerticalClipPadding(int pad) {
        vertPad = pad;
        return this;
    }

    public Text setShadow(float shadowAlpha) {
        paint.setShadowLayer(dp(1), 0, dp(.66f), Theme.multAlpha(0xFF000000, shadowAlpha));
        return this;
    }


    public void draw(Canvas canvas) {
        if (layout == null) {
            return;
        }
        if (!doNotSave && ellipsizeWidth >= 0 && width > ellipsizeWidth) {
            canvas.saveLayerAlpha(0, -vertPad, ellipsizeWidth - 1, layout.getHeight() + vertPad, 0xFF, Canvas.ALL_SAVE_FLAG);
        }
        canvas.save();
        canvas.translate(-left, 0);
        if (hackClipBounds) {
            canvas.drawText(layout.getText().toString(), 0, -paint.getFontMetricsInt().ascent, paint);
        } else {
            layout.draw(canvas);
        }
        canvas.restore();
        if (!doNotSave && ellipsizeWidth >= 0 && width > ellipsizeWidth) {
            if (ellipsizeGradient == null) {
                ellipsizeGradient = new LinearGradient(0, 0, dp(8), 0, new int[] { 0x00ffffff, 0xffffffff }, new float[] {0, 1}, Shader.TileMode.CLAMP);
                ellipsizeMatrix = new Matrix();
                ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                ellipsizePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                ellipsizePaint.setShader(ellipsizeGradient);
            }
            canvas.save();
            ellipsizeMatrix.reset();
            ellipsizeMatrix.postTranslate(ellipsizeWidth - dp(8), 0);
            ellipsizeGradient.setLocalMatrix(ellipsizeMatrix);
            canvas.drawRect(ellipsizeWidth - dp(8), 0, ellipsizeWidth, layout.getHeight(), ellipsizePaint);
            canvas.restore();
            canvas.restore();
        }
    }

    public Paint.FontMetricsInt getFontMetricsInt() {
        return paint.getFontMetricsInt();
    }

    public float getWidth() {
        return ellipsizeWidth >= 0 ? Math.min(ellipsizeWidth, width) : width;
    }

    public float getCurrentWidth() {
        return width;
    }

    public float getHeight() {
        return layout.getHeight();
    }

    @NonNull
    public CharSequence getText() {
        if (layout == null || layout.getText() == null) {
            return "";
        }
        return layout.getText();
    }
}
