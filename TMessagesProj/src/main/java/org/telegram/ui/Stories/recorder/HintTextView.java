package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
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

    public HintTextView(Context context) {
        super(context);

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        textDrawable.setAnimationProperties(.35f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setTextSize(AndroidUtilities.dp(14));
        textDrawable.setShadowLayer(AndroidUtilities.dp(1.4f), 0, AndroidUtilities.dp(.4f), 0x4C000000);
        textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);
        textDrawable.setCallback(this);
        textDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x);
    }

    public void setText(CharSequence text, boolean animated) {
        textDrawable.setText(text, animated);
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

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
