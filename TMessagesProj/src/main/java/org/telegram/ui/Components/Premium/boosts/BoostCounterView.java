package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

@SuppressLint("ViewConstructor")
public class BoostCounterView extends View {

    private final AnimatedTextView.AnimatedTextDrawable countText;
    private float countScale = 1;
    private ValueAnimator countAnimator;
    private int lastCount;
    private final Paint bgPaint;

    public BoostCounterView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        countText = new AnimatedTextView.AnimatedTextDrawable(false, false, true);
        countText.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        countText.setCallback(this);
        countText.setTextSize(dp(11.5f));
        countText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        countText.setTextColor(Color.WHITE);
        countText.setText("");
        countText.setGravity(Gravity.CENTER);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF967bff);
        setVisibility(GONE);
    }

    private void animateCount() {
        if (countAnimator != null) {
            countAnimator.cancel();
            countAnimator = null;
        }

        countAnimator = ValueAnimator.ofFloat(0, 1);
        countAnimator.addUpdateListener(anm -> {
            countScale = Math.max(1, (float) anm.getAnimatedValue());
            invalidate();
        });
        countAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                countScale = 1;
                invalidate();
            }
        });
        countAnimator.setInterpolator(new OvershootInterpolator(2.0f));
        countAnimator.setDuration(200);
        countAnimator.start();
    }

    public void setCount(int count, boolean animated) {
        if (!BoostRepository.isMultiBoostsAvailable()) {
            count = 0;
        }
        if (count > 0) {
            setVisibility(VISIBLE);
        }
        if (animated) {
            countText.cancelAnimation();
        }
        if (animated && count != lastCount && count > 0) {
            animateCount();
        }
        lastCount = count;
        int oldLength = countText.getText().length();
        countText.setText("x" + count, animated);
        int newLength = countText.getText().length();
        invalidate();
        if (oldLength != newLength) {
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec((int) (dp(8 + 3 + 4) + countText.getWidth()), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(26), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        AndroidUtilities.rectTmp2.set(0, 0, dp(8) + (int) countText.getCurrentWidth(), AndroidUtilities.dp(20));
        AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);

        if (countScale != 1) {
            canvas.save();
            canvas.scale(countScale, countScale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
        }

        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(10), dp(10), bgPaint);
        AndroidUtilities.rectTmp2.set(0, 0, (int) AndroidUtilities.rectTmp.width(), AndroidUtilities.dp(19));
        countText.setBounds(AndroidUtilities.rectTmp2);
        countText.draw(canvas);
        if (countScale != 1) {
            canvas.restore();
        }
        canvas.restore();
    }
}
