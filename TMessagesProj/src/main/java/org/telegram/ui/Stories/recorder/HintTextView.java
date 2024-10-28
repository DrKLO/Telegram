package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class HintTextView extends View implements FlashViews.Invertable {

    private final AnimatedTextView.AnimatedTextDrawable textDrawable;

    private Paint backgroundPaint;
    private RectF rect;

    public static final int TYPE_QUICK_SHARE = 3;
    private int type = 0;
    public HintTextView(Context context, int type) {
        super(context);

        this.type = type;

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        textDrawable.setAnimationProperties(.35f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setTextSize(dp(14));
        textDrawable.setShadowLayer(dp(1.4f), 0, dp(.4f), 0x4C000000);
        textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);
        textDrawable.setCallback(this);
        textDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x);

        if (type == TYPE_QUICK_SHARE) {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textDrawable.setTextSize(dp(12));
            backgroundPaint.setColor(Color.parseColor("#A1E87D"));
            backgroundPaint.setMaskFilter(new BlurMaskFilter(dp(8), BlurMaskFilter.Blur.NORMAL));

            rect = new RectF();
            setPadding(30, 15, 30, 15);
            textDrawable.setTypeface(Typeface.DEFAULT_BOLD);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
    }

    public void setText(CharSequence text, boolean animated) {
        textDrawable.setText(text, animated);
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (type == TYPE_QUICK_SHARE) {
            int padding = 20;
            rect.set(0, 0, getWidth(), getHeight());
            //rect.inset(padding, padding);

            // Draw rounded rectangle background
            canvas.drawRoundRect(rect, getHeight() / 2f, getHeight() / 2f, backgroundPaint);
        }
        textDrawable.setBounds(0, 0, getWidth(), getHeight());
        textDrawable.draw(canvas);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || super.verifyDrawable(who);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        textDrawable.setOverrideFullWidth(getMeasuredWidth());
    }

    public void setInvert(float invert) {
        textDrawable.setTextColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
    }
}
