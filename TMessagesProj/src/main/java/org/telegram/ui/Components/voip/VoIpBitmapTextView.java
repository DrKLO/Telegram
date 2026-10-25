package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.PersianFontApplier;

/**
 * Fixed ANR on Samsung after one and a half minutes.
 * Anr occurs due to drawing long text.
 */
@SuppressLint("ViewConstructor")
public class VoIpBitmapTextView extends View {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float textWidth;
    private final StaticLayout textLayout;
    private volatile Bitmap bitmap;

    public VoIpBitmapTextView(Context context, String text) {
        super(context);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());
        CharSequence styledText = PersianFontApplier.applyForPaint(text, textPaint);
        textWidth = Layout.getDesiredWidth(styledText, textPaint);
        textLayout = new StaticLayout(styledText, textPaint, Math.max(1, (int) Math.ceil(textWidth)), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec((int) Math.ceil(textWidth) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (changed && width > 0 && height > 0) {
            Utilities.globalQueue.postRunnable(() -> {
                Bitmap renderedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(renderedBitmap);
                int xPos = width / 2;
                int yPos = (int) ((height / 2) - ((textPaint.descent() + textPaint.ascent()) / 2));
                canvas.translate(xPos - (textLayout.getLineLeft(0) + textLayout.getLineRight(0)) / 2f,
                        yPos - textLayout.getLineBaseline(0));
                textLayout.draw(canvas);
                bitmap = renderedBitmap;
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
