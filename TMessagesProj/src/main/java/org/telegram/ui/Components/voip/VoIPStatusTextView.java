package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPStatusTextView extends FrameLayout {

    TextView[] textView = new TextView[2];
    VoIPTimerView timerView;

    CharSequence nextTextToSet;
    boolean animationInProgress;

    private TextAlphaSpan[] ellSpans = ellSpans = new TextAlphaSpan[]{new TextAlphaSpan(), new TextAlphaSpan(), new TextAlphaSpan()};
    private AnimatorSet ellAnimator;
    private boolean attachedToWindow;

    ValueAnimator animator;
    boolean timerShowing;

    public VoIPStatusTextView(@NonNull Context context) {
        super(context);
        for (int i = 0; i < 2; i++) {
            textView[i] = new TextView(context);
            textView[i].setTextSize(15);
            textView[i].setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
            textView[i].setTextColor(Color.WHITE);
            textView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            addView(textView[i]);
        }
        timerView = new VoIPTimerView(context);
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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
                    if (attachedToWindow) {
                        ellAnimator.start();
                    }
                }
            };

            @Override
            public void onAnimationEnd(Animator animation) {
                if (attachedToWindow) {
                    postDelayed(restarter, 300);
                }
            }
        });
    }

    public void setText(String text, boolean ellipsis, boolean animated) {
        CharSequence nextString = text;
        if (ellipsis) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            for (TextAlphaSpan s : ellSpans) {
                s.setAlpha(0);
            }
            SpannableString ell = new SpannableString("...");
            ell.setSpan(ellSpans[0], 0, 1, 0);
            ell.setSpan(ellSpans[1], 1, 2, 0);
            ell.setSpan(ellSpans[2], 2, 3, 0);
            ssb.append(ell);
            nextString = ssb;
        }

        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }


        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            animationInProgress = false;
            textView[0].setText(nextString);
            textView[0].setVisibility(View.VISIBLE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.GONE);
        } else {
            if (animationInProgress) {
                nextTextToSet = nextString;
                return;
            }

            if (timerShowing) {
                textView[0].setText(nextString);
                replaceViews(timerView, textView[0], null);
            } else {
                if (!textView[0].getText().equals(nextString)) {
                    textView[1].setText(nextString);
                    replaceViews(textView[0], textView[1], () -> {
                        TextView v = textView[0];
                        textView[0] = textView[1];
                        textView[1] = v;
                    });
                }
            }
        }
    }

    public void showTimer(boolean animated) {
        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }
        if (timerShowing) {
            return;
        }
        timerView.updateTimer();
        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            timerShowing = true;
            animationInProgress = false;
            textView[0].setVisibility(View.GONE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.VISIBLE);
        } else {
            if (animationInProgress) {
                nextTextToSet = "timer";
                return;
            }
            timerShowing = true;
            replaceViews(textView[0], timerView, null);
        }

    }


    private void replaceViews(View out, View in, Runnable onEnd) {
        out.setVisibility(View.VISIBLE);
        in.setVisibility(View.VISIBLE);

        in.setTranslationY(AndroidUtilities.dp(15));
        in.setAlpha(0f);
        animationInProgress = true;
        animator = ValueAnimator.ofFloat(0, 1f);
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            float inScale = 0.4f + 0.6f * v;
            float outScale = 0.4f + 0.6f * (1f - v);
            in.setTranslationY(AndroidUtilities.dp(10) * (1f - v));
            in.setAlpha(v);
            in.setScaleX(inScale);
            in.setScaleY(inScale);

            out.setTranslationY(-AndroidUtilities.dp(10) * v);
            out.setAlpha(1f - v);
            out.setScaleX(outScale);
            out.setScaleY(outScale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                out.setVisibility(View.GONE);
                out.setAlpha(1f);
                out.setTranslationY(0);
                out.setScaleY(1f);
                out.setScaleX(1f);

                in.setAlpha(1f);
                in.setTranslationY(0);
                in.setVisibility(View.VISIBLE);
                in.setScaleY(1f);
                in.setScaleX(1f);

                if (onEnd != null) {
                    onEnd.run();
                }
                animationInProgress = false;
                if (nextTextToSet != null) {
                    if (nextTextToSet.equals("timer")) {
                        showTimer(true);
                    } else {
                        textView[1].setText(nextTextToSet);
                        replaceViews(textView[0], textView[1], () -> {
                            TextView v = textView[0];
                            textView[0] = textView[1];
                            textView[1] = v;
                        });
                    }
                    nextTextToSet = null;
                }
            }
        });
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void setSignalBarCount(int count) {
        timerView.setSignalBarCount(count);
    }

    private Animator createEllipsizeAnimator(TextAlphaSpan target, int startVal, int endVal, int startDelay, int duration) {
        ValueAnimator a = ValueAnimator.ofInt(startVal, endVal);
        a.addUpdateListener(valueAnimator -> {
            target.setAlpha((int) valueAnimator.getAnimatedValue());
            if (!(timerShowing && !animationInProgress)){
                textView[0].invalidate();
                textView[1].invalidate();
            }
        });
        a.setDuration(duration);
        a.setStartDelay(startDelay);
        a.setInterpolator(CubicBezierInterpolator.DEFAULT);
        return a;
    }

    private class TextAlphaSpan extends CharacterStyle {
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        if (!ellAnimator.isRunning()) {
            ellAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        ellAnimator.removeAllListeners();
        ellAnimator.cancel();
    }
}
