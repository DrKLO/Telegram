package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class PaintCancelView extends View {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;

    public PaintCancelView(Context context) {
        super(context);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawLine(
            getWidth() / 2f + dp(lerp(-5.33f, -4, progress)),
            getHeight() / 2f + dp(lerp(5.33f, 0, progress)),
            getWidth() / 2f + dp(lerp(5.33f, 3, progress)),
            getHeight() / 2f + dp(lerp(-5.33f, -7, progress)),
            paint
        );
        canvas.drawLine(
            getWidth() / 2f + dp(lerp(5.33f, 3, progress)),
            getHeight() / 2f + dp(lerp(5.33f, 7, progress)),
            getWidth() / 2f + dp(lerp(-5.33f, -4, progress)),
            getHeight() / 2f + dp(lerp(-5.33f, 0, progress)),
            paint
        );
    }
}
