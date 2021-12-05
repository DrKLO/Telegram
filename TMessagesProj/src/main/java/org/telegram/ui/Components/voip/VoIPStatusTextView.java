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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class VoIPStatusTextView extends FrameLayout {

    TextView[] textView = new TextView[2];
    TextView reconnectTextView;
    VoIPTimerView timerView;

    CharSequence nextTextToSet;
    boolean animationInProgress;

    private boolean attachedToWindow;

    ValueAnimator animator;
    boolean timerShowing;

    EllipsizeSpanAnimator ellipsizeAnimator;

    public VoIPStatusTextView(@NonNull Context context) {
        super(context);
        for (int i = 0; i < 2; i++) {
            textView[i] = new TextView(context);
            textView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView[i].setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
            textView[i].setTextColor(Color.WHITE);
            textView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            addView(textView[i]);
        }

        reconnectTextView = new TextView(context);
        reconnectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        reconnectTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        reconnectTextView.setTextColor(Color.WHITE);
        reconnectTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(reconnectTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 22, 0, 0));

        ellipsizeAnimator = new EllipsizeSpanAnimator(this);
        SpannableStringBuilder ssb = new SpannableStringBuilder(LocaleController.getString("VoipReconnecting", R.string.VoipReconnecting));
        SpannableString ell = new SpannableString("...");
        ellipsizeAnimator.wrap(ell, 0);
        ssb.append(ell);
        reconnectTextView.setText(ssb);
        reconnectTextView.setVisibility(View.GONE);

        timerView = new VoIPTimerView(context);
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

    }

    public void setText(String text, boolean ellipsis, boolean animated) {
        CharSequence nextString = text;
        if (ellipsis) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            ellipsizeAnimator.reset();
            SpannableString ell = new SpannableString("...");
            ellipsizeAnimator.wrap(ell, 0);
            ssb.append(ell);
            nextString = ssb;

            ellipsizeAnimator.addView(textView[0]);
            ellipsizeAnimator.addView(textView[1]);
        } else {
            ellipsizeAnimator.removeView(textView[0]);
            ellipsizeAnimator.removeView(textView[1]);
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

        ellipsizeAnimator.removeView(textView[0]);
        ellipsizeAnimator.removeView(textView[1]);
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

    public void showReconnect(boolean showReconnecting, boolean animated) {
        if (!animated) {
            reconnectTextView.animate().setListener(null).cancel();
            reconnectTextView.setVisibility(showReconnecting ? View.VISIBLE : View.GONE);
        } else {
            if (showReconnecting) {
                if (reconnectTextView.getVisibility() != View.VISIBLE) {
                    reconnectTextView.setVisibility(View.VISIBLE);
                    reconnectTextView.setAlpha(0);
                }
                reconnectTextView.animate().setListener(null).cancel();
                reconnectTextView.animate().alpha(1f).setDuration(150).start();
            } else {
                reconnectTextView.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        reconnectTextView.setVisibility(View.GONE);
                    }
                }).setDuration(150).start();
            }
        }

        if (showReconnecting) {
            ellipsizeAnimator.addView(reconnectTextView);
        } else {
            ellipsizeAnimator.removeView(reconnectTextView);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        ellipsizeAnimator.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        ellipsizeAnimator.onDetachedFromWindow();
    }

}
