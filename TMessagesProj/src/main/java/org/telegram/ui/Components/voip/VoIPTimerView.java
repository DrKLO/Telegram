package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;

public class VoIPTimerView extends View {

    StaticLayout timerLayout;
    RectF rectF = new RectF();
    Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    String currentTimeStr;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int signalBarCount = 4;
    private boolean isDrawCallIcon = false;
    private final Drawable callsDeclineDrawable;

    Runnable updater = () -> {
        if (getVisibility() == View.VISIBLE) {
            updateTimer();
        }
    };

    public VoIPTimerView(Context context) {
        super(context);
        textPaint.setTextSize(AndroidUtilities.dp(15));
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        activePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.9f)));
        inactivePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.4f)));
        callsDeclineDrawable = ContextCompat.getDrawable(context, R.drawable.calls_decline);
        callsDeclineDrawable.setBounds(0, 0, AndroidUtilities.dp(24), AndroidUtilities.dp(24));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final StaticLayout timerLayout = this.timerLayout;
        if (timerLayout != null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), timerLayout.getHeight());
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(15));
        }
    }

    public void updateTimer() {
        removeCallbacks(updater);
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        String str = AndroidUtilities.formatLongDuration((int) (service.getCallDuration() / 1000));
        if (currentTimeStr == null || !currentTimeStr.equals(str)) {
            currentTimeStr = str;
            if (timerLayout == null) {
                requestLayout();
            }
            timerLayout = new StaticLayout(currentTimeStr, textPaint, (int) textPaint.measureText(currentTimeStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        postDelayed(updater, 300);

        invalidate();
    }

    @Override
    public void setVisibility(int visibility) {
        if (getVisibility() != visibility) {
            if (visibility == VISIBLE) {
                currentTimeStr = "00:00";
                timerLayout = new StaticLayout(currentTimeStr, textPaint, (int) textPaint.measureText(currentTimeStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                updateTimer();
            } else {
                currentTimeStr = null;
                timerLayout = null;
            }
        }
        super.setVisibility(visibility);

    }

    @Override
    protected void onDraw(Canvas canvas) {
        final StaticLayout timerLayout = this.timerLayout;
        int totalWidth = timerLayout == null ? 0 : timerLayout.getWidth() + AndroidUtilities.dp(21);
        canvas.save();
        canvas.translate((getMeasuredWidth() - totalWidth) / 2f, 0);
        canvas.save();
        if (isDrawCallIcon) {
            canvas.translate(-AndroidUtilities.dp(7), -AndroidUtilities.dp(3));
            callsDeclineDrawable.draw(canvas);
        } else {
            canvas.translate(0, (getMeasuredHeight() - AndroidUtilities.dp(11)) / 2f);
            for (int i = 0; i < 4; i++) {
                Paint p = i + 1 > signalBarCount ? inactivePaint : activePaint;
                rectF.set(AndroidUtilities.dpf2(4.16f) * i, AndroidUtilities.dpf2(2.75f) * (3 - i), AndroidUtilities.dpf2(4.16f) * i + AndroidUtilities.dpf2(2.75f), AndroidUtilities.dp(11));
                canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(0.7f), AndroidUtilities.dpf2(0.7f), p);
            }
        }
        canvas.restore();

        if (timerLayout != null) {
            canvas.translate(AndroidUtilities.dp(21), 0);
            timerLayout.draw(canvas);
        }
        canvas.restore();
    }

    public void setSignalBarCount(int count) {
        signalBarCount = count;
        invalidate();
    }

    public void setDrawCallIcon() {
        isDrawCallIcon = true;
        invalidate();
    }
}
