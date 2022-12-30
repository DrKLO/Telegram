package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class PaintDoneView extends View {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;

    public PaintDoneView(Context context) {
        super(context);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(2));
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawLine(
            getWidth() / 2f + dp(lerp(-6.7f, -7, progress)),
            getHeight() / 2f + dp(lerp(.71f, 0, progress)),
            getWidth() / 2f + dp(lerp(-2.45f, 7, progress)),
            getHeight() / 2f + dp(lerp(4.79f, 0, progress)),
            paint
        );
        canvas.drawLine(
            getWidth() / 2f + dp(lerp(-2.45f, 0, progress)),
            getHeight() / 2f + dp(lerp(4.79f, 7, progress)),
            getWidth() / 2f + dp(lerp(6.59f, 0, progress)),
            getHeight() / 2f + dp(lerp(-4.27f, -7, progress)),
            paint
        );
    }
}
