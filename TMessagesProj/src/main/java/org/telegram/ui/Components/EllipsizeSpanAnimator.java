package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.view.View;

import java.util.ArrayList;

public class EllipsizeSpanAnimator {

    private final TextAlphaSpan[] ellSpans = new TextAlphaSpan[]{new TextAlphaSpan(), new TextAlphaSpan(), new TextAlphaSpan()};
    private final AnimatorSet ellAnimator;

    boolean attachedToWindow;
    public ArrayList<View> ellipsizedViews = new ArrayList<>();

    public EllipsizeSpanAnimator(View parentView) {
        ellAnimator = new AnimatorSet();
        ellAnimator.playTogether(
                createEllipsizeAnimator(ellSpans[0], 0, 255, 0, 300),
                createEllipsizeAnimator(ellSpans[1], 0, 255, 150, 300),
                createEllipsizeAnimator(ellSpans[2], 0, 255, 300, 300),
                createEllipsizeAnimator(ellSpans[0], 255, 0, 1000, 400),
                createEllipsizeAnimator(ellSpans[1], 255, 0, 1000, 400),
                createEllipsizeAnimator(ellSpans[2], 255, 0, 1000, 400)
        );
        ellAnimator.addListener(new AnimatorListenerAdapter() {
            private Runnable restarter = new Runnable() {
                @Override
                public void run() {
                    if (attachedToWindow && !ellipsizedViews.isEmpty() && !ellAnimator.isRunning()) {
                        try {
                            ellAnimator.start();
                        } catch (Exception ignored) {

                        }
                    }
                }
            };

            @Override
            public void onAnimationEnd(Animator animation) {
                if (attachedToWindow) {
                    parentView.postDelayed(restarter, 300);
                }
            }
        });
    }

    public void wrap(SpannableString string, int start) {
        string.setSpan(ellSpans[0], start, start + 1, 0);
        string.setSpan(ellSpans[1], start + 1, start + 2, 0);
        string.setSpan(ellSpans[2], start + 2, start + 3, 0);
    }

    public void onAttachedToWindow() {
        attachedToWindow = true;
        if (!ellAnimator.isRunning()) {
            ellAnimator.start();
        }
    }

    public void onDetachedFromWindow() {
        attachedToWindow = false;
        ellAnimator.cancel();
    }

    public void reset() {
        for (TextAlphaSpan s : ellSpans) {
            s.setAlpha(0);
        }
    }

    private Animator createEllipsizeAnimator(TextAlphaSpan target, int startVal, int endVal, int startDelay, int duration) {
        ValueAnimator a = ValueAnimator.ofInt(startVal, endVal);
        a.addUpdateListener(valueAnimator -> {
            target.setAlpha((int) valueAnimator.getAnimatedValue());
            for (int i = 0; i < ellipsizedViews.size(); i++) {
                ellipsizedViews.get(i).invalidate();
            }
        });
        a.setDuration(duration);
        a.setStartDelay(startDelay);
        a.setInterpolator(CubicBezierInterpolator.DEFAULT);
        return a;
    }

    public void addView(View view) {
        if (ellipsizedViews.isEmpty()) {
            ellAnimator.start();
        }
        if (!ellipsizedViews.contains(view)) {
            ellipsizedViews.add(view);
        }
    }

    public void removeView(View view) {
        ellipsizedViews.remove(view);
        if (ellipsizedViews.isEmpty()) {
            ellAnimator.cancel();
        }
    }

    private static class TextAlphaSpan extends CharacterStyle {
        private int alpha;

        public TextAlphaSpan() {
            this.alpha = 0;
        }

        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setAlpha(alpha);
        }
    }
}