package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class VideoCompressButton extends View {

    public static final int STATE_GIF = 0;
    public static final int STATE_SD = 1;
    public static final int STATE_HD = 2;

    private final AnimatedTextView.AnimatedTextDrawable textDrawable;
    private final AnimatedTextView.AnimatedTextDrawable sizeTextDrawable;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean disabled;
    private final AnimatedFloat disabledT = new AnimatedFloat(this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);

    public VideoCompressButton(Context context) {
        super(context);

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, false, false);
        textDrawable.setAnimationProperties(.4f, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setTextSize(dpf2(10.6f));
        textDrawable.setCallback(this);
        textDrawable.setGravity(Gravity.CENTER);

        sizeTextDrawable = new AnimatedTextView.AnimatedTextDrawable(true, false, false);
        sizeTextDrawable.setAnimationProperties(.2f, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        sizeTextDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        sizeTextDrawable.setTextColor(0xffffffff);
        sizeTextDrawable.setTextSize(dpf2(8.6f));
        sizeTextDrawable.setCallback(this);
        sizeTextDrawable.setGravity(Gravity.RIGHT);
        sizeTextDrawable.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        sizeTextDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x);

        strokePaint.setColor(0xffffffff);
        strokePaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(0xffffffff);

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private final int[] sizes = new int[] {
        144, // 144x256
        240,
        360, // 360x640
        480,
        720, // 720x1280
        1080, // 1080x1920
        1440, // 1440x2560, 2K
        2160, // 2160x3840, 4K
    };

    public void setState(boolean enabled, boolean muted, int mn) {
        this.disabled = !enabled || muted;
        if (muted) {
            textDrawable.setText("GIF");
            sizeTextDrawable.setText("", true);
        } else {
            textDrawable.setText(mn >= 720 ? "HD" : "SD");
            int index = -1;
            for (int i = sizes.length - 1; i >= 0; i--) {
                if (mn >= sizes[i]) {
                    index = i;
                    break;
                }
            }
            if (index < 0)
                sizeTextDrawable.setText("", true);
            else if (index == 6)
                sizeTextDrawable.setText("2K", TextUtils.isEmpty(sizeTextDrawable.getText()));
            else if (index == 7)
                sizeTextDrawable.setText("4K", TextUtils.isEmpty(sizeTextDrawable.getText()));
            else
                sizeTextDrawable.setText("" + sizes[index], TextUtils.isEmpty(sizeTextDrawable.getText()));
        }
        setClickable(!this.disabled);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        final float disabled = disabledT.set(this.disabled);
        strokePaint.setAlpha((int) (0xFF * (1f - .35f * disabled)));
        strokePaint.setStrokeWidth(dpf2(1.33f));

        float w = Math.max(dpf2(21.33f), dpf2(6) + textDrawable.getCurrentWidth()), h = dpf2(17.33f);
        AndroidUtilities.rectTmp.set(
            (getWidth() - w) / 2f,
            (getHeight() - h) / 2f,
            (getWidth() + w) / 2f,
            (getHeight() + h) / 2f
        );
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dpf2(4), dpf2(4), strokePaint);

        AndroidUtilities.rectTmp2.set(0, (int) ((getHeight() - h) / 2f), getWidth(), (int) ((getHeight() + h) / 2f));
        textDrawable.setBounds(AndroidUtilities.rectTmp2);
        textDrawable.setAlpha((int) (0xFF * (1f - .35f * disabled)));
        textDrawable.draw(canvas);

        float sw = sizeTextDrawable.isNotEmpty() * dpf2(2) + sizeTextDrawable.getCurrentWidth();
        float sh = dpf2(8.33f);

        AndroidUtilities.rectTmp2.set(
            (int) (getWidth() / 2f + dpf2(16) - sw),
            (int) (getHeight() / 2f - dpf2(14)),
            (int) (getWidth() / 2f + dpf2(16)),
            (int) (getHeight() / 2f - dpf2(14) + sh)
        );
        AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
        AndroidUtilities.rectTmp.inset(-dpf2(1.33f), -dpf2(1.33f));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dpf2(1.66f), dpf2(1.66f), clearPaint);

        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
        fillPaint.setAlpha((int) (0xFF * (1f - .35f * disabled) * sizeTextDrawable.isNotEmpty()));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dpf2(1.66f), dpf2(1.66f), fillPaint);
        AndroidUtilities.rectTmp2.offset((int) (-dpf2(1.33f)), 0);
        canvas.save();
        sizeTextDrawable.setBounds(AndroidUtilities.rectTmp2);
        sizeTextDrawable.draw(canvas);
        canvas.restore();
        canvas.restore();

        canvas.restore();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return textDrawable == who || sizeTextDrawable == who || super.verifyDrawable(who);
    }
}
