package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class ProxyDrawable extends Drawable {

    private Drawable emptyDrawable;
    private Drawable fullDrawable;

    private Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF cicleRect = new RectF();
    private int radOffset = 0;
    private long lastUpdateTime;
    private int currentColorType;

    private float connectedAnimationProgress;

    private boolean connected;
    private boolean isEnabled;

    private int colorKey = -1;

    public ProxyDrawable(Context context) {
        super();
        emptyDrawable = context.getResources().getDrawable(R.drawable.msg2_proxy_off);
        fullDrawable = context.getResources().getDrawable(R.drawable.msg2_proxy_on);

        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(AndroidUtilities.dp(1.66f));
        outerPaint.setStrokeCap(Paint.Cap.ROUND);
        lastUpdateTime = SystemClock.elapsedRealtime();
    }

    public void setConnected(boolean enabled, boolean value, boolean animated) {
        isEnabled = enabled;
        connected = value;
        lastUpdateTime = SystemClock.elapsedRealtime();
        if (!animated) {
            connectedAnimationProgress = connected ? 1.0f : 0.0f;
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;

        if (!isEnabled) {
            setBounds(emptyDrawable);
            emptyDrawable.draw(canvas);
        } else if (!connected || connectedAnimationProgress != 1.0f) {
            setBounds(emptyDrawable);
            emptyDrawable.draw(canvas);

            outerPaint.setColor(Theme.getColor(colorKey < 0 ? Theme.key_contextProgressOuter2 : colorKey));
            outerPaint.setAlpha((int) (255 * (1.0f - connectedAnimationProgress)));

            radOffset += 360 * dt / 1000.0f;

            int width = getBounds().width();
            int height = getBounds().height();

            int r = AndroidUtilities.dp(4);
            int x = width / 2 - r;
            int y = height / 2 - r;
            cicleRect.set(x, y, x + r + r, y + r + r);
            canvas.drawArc(cicleRect, -90 + radOffset, 90, false, outerPaint);
            invalidateSelf();
        }

        if (isEnabled && (connected || connectedAnimationProgress != 0.0f)) {
            fullDrawable.setAlpha((int) (255 * connectedAnimationProgress));
            setBounds(fullDrawable);
            fullDrawable.draw(canvas);
        }

        if (connected && connectedAnimationProgress != 1.0f) {
            connectedAnimationProgress += dt / 300.0f;
            if (connectedAnimationProgress > 1.0f) {
                connectedAnimationProgress = 1.0f;
            }
            invalidateSelf();
        } else if (!connected && connectedAnimationProgress != 0.0f) {
            connectedAnimationProgress -= dt / 300.0f;
            if (connectedAnimationProgress < 0.0f) {
                connectedAnimationProgress = 0.0f;
            }
            invalidateSelf();
        }
    }

    private void setBounds(Drawable drawable) {
        Rect bounds = getBounds();
        drawable.setBounds(
            bounds.centerX() - drawable.getIntrinsicWidth() / 2,
            bounds.centerY() - drawable.getIntrinsicHeight() / 2,
            bounds.centerX() + drawable.getIntrinsicWidth() / 2,
            bounds.centerY() + drawable.getIntrinsicHeight() / 2
        );
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        emptyDrawable.setColorFilter(cf);
        fullDrawable.setColorFilter(cf);
    }

    public void setColorKey(int colorKey) {
        this.colorKey = colorKey;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }
}
