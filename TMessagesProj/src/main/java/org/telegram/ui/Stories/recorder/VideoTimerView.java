package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class VideoTimerView extends View implements FlashViews.Invertable {

    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint recordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AnimatedTextView.AnimatedTextDrawable textDrawable;

    public VideoTimerView(Context context) {
        super(context);

        recordPaint.setColor(0xFFF22828);

        backgroundPaint.setColor(0x3f000000);

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        textDrawable.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextSize(AndroidUtilities.dp(13));
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setTypeface(AndroidUtilities.bold());
        textDrawable.setCallback(this);
        textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);

        setDuration(0, false);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return textDrawable == who || super.verifyDrawable(who);
    }

    private boolean recording;
    private AnimatedFloat recordingT = new AnimatedFloat(this, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
    public void setRecording(boolean value, boolean animated) {
        recording = value;
        if (!animated) {
            recordingT.set(recording ? 1 : 0, true);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(45), MeasureSpec.EXACTLY));
    }

    public void setDuration(long duration, boolean animated) {
        long s = duration % 60, m = (duration - s) / 60;
        StringBuilder str = new StringBuilder(5);
        if (m < 10)
            str.append('0');
        str.append(m).append(':');
        if (s < 10)
            str.append('0');
        str.append(s);
        textDrawable.setText(str, animated);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float recordingT = this.recordingT.set(recording ? 1 : 0);

        float recordingPad = recordingT * AndroidUtilities.dp(12.66f);
        float w = textDrawable.getCurrentWidth() + recordingPad;

        AndroidUtilities.rectTmp.set(
            (getWidth() - w) / 2 - AndroidUtilities.dp(8),
            AndroidUtilities.dp(18),
            (getWidth() + w) / 2 + AndroidUtilities.dp(8),
            AndroidUtilities.dp(18 + 22)
        );
        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(18), AndroidUtilities.dp(18), backgroundPaint);

        if (recordingT > 0) {
            long t = System.currentTimeMillis() % 2000L;
            recordPaint.setAlpha((int) (0xFF * Utilities.clamp((float) Math.sin(t / 1000f * Math.PI) / 4f + .75f, 1f, 0f)));
            invalidate();
            canvas.drawCircle(AndroidUtilities.rectTmp.left + AndroidUtilities.dp(6.66f + 4), AndroidUtilities.rectTmp.centerY(), AndroidUtilities.dp(4) * recordingT, recordPaint);
        }

        textDrawable.setBounds((int) (AndroidUtilities.rectTmp.left + recordingPad), (int) AndroidUtilities.rectTmp.top - AndroidUtilities.dp(1), (int) AndroidUtilities.rectTmp.right, (int) AndroidUtilities.rectTmp.bottom);
        textDrawable.draw(canvas);
    }

    public void setInvert(float invert) {
        backgroundPaint.setColor(ColorUtils.blendARGB(0x3f000000, 0x10000000, invert));
        textDrawable.setTextColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
    }

}
