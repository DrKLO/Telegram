package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

@SuppressLint("AppCompatCustomView")
public class MarqueeTextView extends TextView {
    private static final int BORDER_DP = 10;
    private static final int MARGIN_DP = 40;
    private static final int SPEED_DP = 60;

    private final Matrix gradientMatrix = new Matrix();
    private LinearGradient gradient;
    private int originalWidth;
    private boolean needMarquee;
    private boolean marqueeIsStarted;
    private float scrollX;

    public MarqueeTextView(Context context) {
        super(context);
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        invalidateGradient();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec);
        originalWidth = MeasureSpec.getSize(widthMeasureSpec);
        needMarquee = getMeasuredWidth() > (originalWidth - rightPadding);
        invalidateGradient();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        stopMarqueeInternal();
    }

    private void invalidateGradient() {
        final float edgeSize = Math.min((float) dp(BORDER_DP) / originalWidth, 0.49f);
        final int color = getCurrentTextColor();

        gradient = new LinearGradient(
            0, 0, originalWidth, 0,
            new int[]{
                color & 0x00FFFFF,
                color,
                color,
                color & 0x00FFFFF
            },
            new float[]{0f, edgeSize, 1f - edgeSize, 1f},
            Shader.TileMode.CLAMP
        );
        if (needMarquee) {
            getPaint().setShader(gradient);
        } else {
            getPaint().setShader(null);
        }
        gradient.setLocalMatrix(gradientMatrix);
        invalidate();
    }

    public boolean isNeedMarquee() {
        return needMarquee;
    }

    private long lastFrameTime;

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final int textWidth = getMeasuredWidth();
        final int textMargin = dp(MARGIN_DP);

        final float shadowVisibility;
        if (scrollX < textWidth) {
            shadowVisibility = MathUtils.clamp(scrollX / dp(BORDER_DP), 0, 1);
        } else {
            shadowVisibility = 0;
        }

        gradientMatrix.reset();
        gradientMatrix.postScale(1 + ((float) dp(BORDER_DP) / originalWidth) * (1f - shadowVisibility), 1f, originalWidth, 0);
        gradientMatrix.postScale(1 - ((float) rightPadding / originalWidth), 1, 0, 0);
        gradientMatrix.postTranslate(scrollX, 0);
        gradient.setLocalMatrix(gradientMatrix);
        canvas.save();
        canvas.translate(-scrollX, 0);
        super.onDraw(canvas);
        canvas.restore();

        if (textWidth > 0 && scrollX > 0 && scrollX + getWidth() > textWidth && needMarquee && marqueeIsStarted) {
            gradientMatrix.postTranslate(-scrollX - (-scrollX + textWidth + textMargin), 0);
            gradient.setLocalMatrix(gradientMatrix);
            canvas.save();
            canvas.translate(-scrollX + textWidth + textMargin, 0);
            super.onDraw(canvas);
            canvas.restore();
        }

        final boolean isFirstFrame = scrollX < 0.0001;
        final long time = SystemClock.uptimeMillis();
        final long dt = lastFrameTime != 0 && !isFirstFrame ? Math.min(time - lastFrameTime, 120): 16;

        lastFrameTime = time;
        if (needMarquee && marqueeIsStarted || !isFirstFrame) {
            scrollX += dp(SPEED_DP) * ((float) dt / 1000);
            if (scrollX > textWidth + textMargin) {
                stopMarqueeInternal();
            }
            invalidate();
        }

        if (needMarquee && !marqueeIsStarted && !marqueeIsPending) {
            pendingMarqueeInternal();
        }
    }



    private final Runnable startMarquee = this::startMarqueeInternal;
    private boolean marqueeIsPending;

    private void pendingMarqueeInternal() {
        if (!marqueeIsPending) {
            marqueeIsPending = true;
            AndroidUtilities.runOnUIThread(startMarquee, 1500);
        }
    }

    private void stopMarqueeInternal() {
        AndroidUtilities.cancelRunOnUIThread(startMarquee);
        marqueeIsPending = false;
        marqueeIsStarted = false;
        scrollX = 0f;
    }

    private void startMarqueeInternal() {
        if (needMarquee) {
            marqueeIsStarted = true;
            marqueeIsPending = false;
            scrollX = 0f;
            lastFrameTime = SystemClock.uptimeMillis();
            invalidate();
        }
    }


    private int rightPadding;
    public void setCustomPaddingRight(int padding) {
        rightPadding = padding;
        needMarquee = getMeasuredWidth() > (originalWidth - rightPadding);
        if (needMarquee) {
            getPaint().setShader(gradient);
        } else {
            getPaint().setShader(null);
        }
        invalidate();
    }
}

