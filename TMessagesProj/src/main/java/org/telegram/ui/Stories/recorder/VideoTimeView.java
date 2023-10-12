package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class VideoTimeView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedTextView.AnimatedTextDrawable textDrawable;

    public VideoTimeView(Context context) {
        super(context);

        backgroundPaint.setColor(0x80000000);

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        textDrawable.setAnimationProperties(.2f, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextSize(dp(13));
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textDrawable.setCallback(this);
        textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);

        setTime(0, false);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return textDrawable == who || super.verifyDrawable(who);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(25), MeasureSpec.EXACTLY));
    }

    public void setTime(long time, boolean animated) {
        long ms = time % 1000;
        time /= 1000L;
        long s = time % 60, m = (time - s) / 60, h = (time - s - m * 60) / 60;
        StringBuilder str = new StringBuilder(8);
        if (h < 10)
            str.append('0');
        str.append(h).append(':');
        if (m < 10)
            str.append('0');
        str.append(m).append(':');
        if (s < 10)
            str.append('0');
        str.append(s);
//        str.append('.');
//        if (ms < 100)
//            str.append("00");
//        else if (ms < 10)
//            str.append('0');
//        str.append(ms);
        if (!TextUtils.equals(str, textDrawable.getText())) {
            textDrawable.cancelAnimation();
            textDrawable.setText(str, animated && !LocaleController.isRTL);
        }
    }

    private boolean shown = true;
    public void show(boolean show, boolean animated) {
        if (show == shown && animated) {
            return;
        }
        shown = show;
        animate().cancel();
        if (animated) {
            animate()
                .translationY(show ? 0 : dp(6))
                .alpha(show ? 1 : 0)
                .scaleX(show ? 1 : .8f)
                .scaleY(show ? 1 : .8f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(220)
                .start();
        } else {
            setTranslationY(show ? 0 : dp(6));
            setScaleX(show ? 1 : .8f);
            setScaleY(show ? 1 : .8f);
            setAlpha(show ? 1 : 0);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = textDrawable.getCurrentWidth();

        AndroidUtilities.rectTmp.set(
                (getWidth() - w) / 2 - dp(6),
                dp(2),
                (getWidth() + w) / 2 + dp(6),
                dp(2 + 21)
        );
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), backgroundPaint);

        textDrawable.setBounds((int) (AndroidUtilities.rectTmp.left), (int) AndroidUtilities.rectTmp.top - dp(1), (int) AndroidUtilities.rectTmp.right, (int) AndroidUtilities.rectTmp.bottom);
        textDrawable.draw(canvas);
    }

}
