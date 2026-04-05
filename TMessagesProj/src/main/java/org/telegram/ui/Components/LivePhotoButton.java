package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;

public class LivePhotoButton extends View {

    private final Drawable icon;
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat animatedValue = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    private boolean value;

    public LivePhotoButton(Context context) {
        super(context);

        ScaleStateListAnimator.apply(this);

        icon = context.getResources().getDrawable(R.drawable.media_live_on).mutate();
        cutPaint.setStyle(Paint.Style.STROKE);
        cutPaint.setColor(0xFFFF0000);
        cutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        whitePaint.setStyle(Paint.Style.STROKE);
        whitePaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float value = animatedValue.set(!this.value);
        icon.setBounds(
            (getWidth()  - icon.getIntrinsicWidth()) / 2,
            (getHeight() - icon.getIntrinsicHeight()) / 2,
            (getWidth()  + icon.getIntrinsicWidth()) / 2,
            (getHeight() + icon.getIntrinsicHeight()) / 2
        );
        final Rect b = icon.getBounds();
        float left = b.left + b.width() * 0.325f;
        float top = b.top + b.height() * 0.152f;
        float bottom = b.bottom - b.height() * 0.152f;
        float right = b.right - b.width() * 0.101f;
        if (value > 0) {
            cutPaint.setStrokeWidth(dp(4));
            canvas.saveLayerAlpha(b.left, b.top, b.right, b.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);
            icon.draw(canvas);
            if (this.value) {
                canvas.drawLine(right - dp(4), bottom - dp(4), lerp(right - dp(4), left + dp(4), value), lerp(bottom - dp(4), top + dp(4), value), cutPaint);
            } else {
                canvas.drawLine(left + dp(4), top + dp(4), lerp(left + dp(4), right - dp(4), value), lerp(top + dp(4), bottom - dp(4), value), cutPaint);
            }
            canvas.restore();
        } else {
            icon.draw(canvas);
        }

        if (value > 0) {
            whitePaint.setStrokeWidth(dp(2));
            if (this.value) {
                canvas.drawLine(right, bottom, lerp(right, left, value), lerp(bottom, top, value), whitePaint);
            } else {
                canvas.drawLine(left, top, lerp(left, right, value), lerp(top, bottom, value), whitePaint);
            }
        }
    }

    public void setValue(boolean value, boolean animated) {
        if (this.value == value) return;
        this.value = value;
        if (!animated) {
            animatedValue.force(value);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(dp(45), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(45), MeasureSpec.EXACTLY)
        );
    }
}
