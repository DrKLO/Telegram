package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;
import android.view.View;

public class CustomRevealView extends View {
    private Paint overlayPaint = new Paint();
    private Paint clearPaint = new Paint();
    private float radius = 0f;
    private PointF center = new PointF();

    public CustomRevealView(Context ctx) {
        super(ctx);
        overlayPaint.setColor(Color.BLACK);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setRadius(float r) {
        radius = r;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int saveCount = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            saveCount = canvas.saveLayer(0,0,getWidth(),getHeight(), null);
        }
        // 1) To‘liq qora overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        // 2) Clear doira
        canvas.drawCircle(center.x, center.y, radius, clearPaint);
        canvas.restoreToCount(saveCount);
    }
}
