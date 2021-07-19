package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class SubstringLayoutAnimator {

    private final View parentView;

    private StaticLayout animateInLayout;
    private StaticLayout animateOutLayout;
    private StaticLayout animateStableLayout;
    public boolean animateTextChange;
    private boolean animateTextChangeOut;
    private boolean replaceAnimation;
    private float xOffset;
    private float hintProgress;

    ValueAnimator valueAnimator;

    public SubstringLayoutAnimator(View parentView) {
        this.parentView = parentView;
    }

    public void create(StaticLayout hintLayout, CharSequence hint, CharSequence text, TextPaint paint) {
        if (hintLayout != null && !hint.equals(text)) {

            if (valueAnimator != null) {
                valueAnimator.cancel();
            }

            boolean animateOut;
            String maxStr;
            String substring;
            if (hint.length() > text.length()) {
                animateOut = true;
                maxStr = hint.toString();
                substring = text.toString();
            } else {
                animateOut = false;
                maxStr = text.toString();
                substring = hint.toString();
            }
            int startFrom = maxStr.indexOf(substring);
            if (startFrom >= 0) {
                SpannableStringBuilder inStr = new SpannableStringBuilder(maxStr);
                SpannableStringBuilder stabeStr = new SpannableStringBuilder(maxStr);
                if (startFrom != 0) {
                    stabeStr.setSpan(new EmptyStubSpan(), 0, startFrom, 0);
                }
                if (startFrom + substring.length() != maxStr.length()) {
                    stabeStr.setSpan(new EmptyStubSpan(), startFrom + substring.length(), maxStr.length(), 0);
                }
                inStr.setSpan(new EmptyStubSpan(), startFrom, startFrom + substring.length(), 0);

                animateInLayout = new StaticLayout(inStr, paint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                animateStableLayout = new StaticLayout(stabeStr, paint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                animateTextChange = true;
                animateTextChangeOut = animateOut;
                xOffset = startFrom == 0 ? 0 : -animateStableLayout.getPrimaryHorizontal(startFrom);
                animateOutLayout = null;
                replaceAnimation = false;
            } else {
                animateInLayout = new StaticLayout(text, paint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                animateOutLayout = new StaticLayout(hint, paint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                animateStableLayout = null;
                animateTextChange = true;
                replaceAnimation = true;
                xOffset = 0;
            }

            hintProgress = 0f;
            valueAnimator = ValueAnimator.ofFloat(0, 1f);
            valueAnimator.addUpdateListener(valueAnimator1 -> {
                hintProgress = (float) valueAnimator1.getAnimatedValue();
                parentView.invalidate();
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animateTextChange = false;
                }
            });
            valueAnimator.setDuration(150);
            valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            valueAnimator.start();
        }
    }

    public void draw(Canvas canvas, TextPaint paint) {
        if (animateTextChange) {
            float titleOffsetX = xOffset * (animateTextChangeOut ? hintProgress : 1f - hintProgress);
            int alpha = paint.getAlpha();
            if (animateStableLayout != null) {
                canvas.save();
                canvas.translate(titleOffsetX, 0);
                animateStableLayout.draw(canvas);
                canvas.restore();
            }
            if (animateInLayout != null) {
                float p = animateTextChangeOut ? 1f - hintProgress : hintProgress;
                canvas.save();
                paint.setAlpha((int) (alpha * p));
                canvas.translate(titleOffsetX, 0);
                if (replaceAnimation) {
                    float s = 0.9f + 0.1f * p;
                    canvas.scale(s, s, titleOffsetX, parentView.getMeasuredHeight() / 2f);
                }
                animateInLayout.draw(canvas);
                canvas.restore();
                paint.setAlpha(alpha);
            }
            if (animateOutLayout != null) {
                float p = animateTextChangeOut ? hintProgress : 1f - hintProgress;
                canvas.save();
                paint.setAlpha((int) (alpha * (animateTextChangeOut ? hintProgress : 1f - hintProgress)));
                canvas.translate(titleOffsetX, 0);
                if (replaceAnimation) {
                    float s = 0.9f + 0.1f * p;
                    canvas.scale(s, s, titleOffsetX, parentView.getMeasuredHeight() / 2f);
                }
                animateOutLayout.draw(canvas);
                canvas.restore();
                paint.setAlpha(alpha);
            }
        }
    }

    public void cancel() {
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        animateTextChange = false;
    }
}
