package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

/**
 * Fixed ANR on Samsung after one and a half minutes.
 * Anr occurs due to drawing long text.
 */
@SuppressLint("ViewConstructor")
public class VoIpBitmapTextView extends View {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float textWidth;
    private final String text;
    private volatile Bitmap bitmap;

    public VoIpBitmapTextView(Context context, String text) {
        super(context);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());
        textWidth = textPaint.measureText(text);
        this.text = text;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec((int) textWidth + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            Utilities.globalQueue.postRunnable(() -> {
                bitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                int xPos = (getMeasuredWidth() / 2);
                int yPos = (int) ((getMeasuredHeight() / 2) - ((textPaint.descent() + textPaint.ascent()) / 2));
                canvas.drawText(text, xPos, yPos, textPaint);
                postInvalidate();
            });
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0, 0, paint);
        }
    }
}
