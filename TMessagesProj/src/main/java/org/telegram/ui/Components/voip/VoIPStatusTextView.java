package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
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
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class VoIPStatusTextView extends FrameLayout {

    TextView[] textView = new TextView[2];
    TextView reconnectTextView;
    TextView badConnectionTextView;
    FrameLayout badConnectionLayer;
    VoIPTimerView timerView;

    CharSequence nextTextToSet;
    boolean animationInProgress;

    ValueAnimator animator;
    boolean timerShowing;

    VoIPBackgroundProvider backgroundProvider;

    public VoIPStatusTextView(@NonNull Context context, VoIPBackgroundProvider backgroundProvider) {
        super(context);
        this.backgroundProvider = backgroundProvider;

        for (int i = 0; i < 2; i++) {
            textView[i] = new TextView(context);
            textView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView[i].setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
            textView[i].setTextColor(Color.WHITE);
            textView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            addView(textView[i]);
        }

        badConnectionLayer = new FrameLayout(context);
        badConnectionTextView = new TextView(context) {

            private final RectF bgRect = new RectF();

            {
                backgroundProvider.attach(this);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                bgRect.set(0, 0, getWidth(), getHeight());
                float x = getX() + ((View) getParent()).getX() + VoIPStatusTextView.this.getX() + ((View) VoIPStatusTextView.this.getParent()).getX();
                float y = getY() + ((View) getParent()).getY() + VoIPStatusTextView.this.getY() + ((View) VoIPStatusTextView.this.getParent()).getY();
                backgroundProvider.setDarkTranslation(x, y);
                canvas.drawRoundRect(bgRect, dp(16), dp(16), backgroundProvider.getDarkPaint());
                super.onDraw(canvas);
            }
        };
        badConnectionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        badConnectionTextView.setTextColor(Color.WHITE);
        badConnectionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        badConnectionTextView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(2), AndroidUtilities.dp(12), AndroidUtilities.dp(2));
        badConnectionTextView.setText(LocaleController.getString("VoipWeakNetwork", R.string.VoipWeakNetwork));
        badConnectionLayer.addView(badConnectionTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        badConnectionLayer.setVisibility(View.GONE);
        addView(badConnectionLayer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 44, 0, 0));

        reconnectTextView = new TextView(context);
        reconnectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        reconnectTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        reconnectTextView.setTextColor(Color.WHITE);
        reconnectTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(reconnectTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 22, 0, 0));

        SpannableStringBuilder ssb = new SpannableStringBuilder(LocaleController.getString("VoipReconnecting", R.string.VoipReconnecting));
        SpannableString ell = new SpannableString(".");
        ell.setSpan(new VoIPEllipsizeSpan(reconnectTextView), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            SpannableString ell = new SpannableString(".");
            ell.setSpan(new VoIPEllipsizeSpan(textView), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            in.setTranslationY(AndroidUtilities.dp(8) * (1f - v));
            in.setAlpha(v);
            out.setTranslationY(-AndroidUtilities.dp(6) * v);
            out.setAlpha(1f - v);
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
    }

    public void showBadConnection(boolean showBadConnection, boolean animated) {
        if (!animated) {
            badConnectionLayer.animate().setListener(null).cancel();
            badConnectionLayer.setVisibility(showBadConnection ? View.VISIBLE : View.GONE);
        } else {
            if (showBadConnection) {
                if (badConnectionLayer.getVisibility() == View.VISIBLE) {
                    return;
                }
                badConnectionLayer.setVisibility(View.VISIBLE);
                badConnectionLayer.setAlpha(0f);
                badConnectionLayer.setScaleY(0.6f);
                badConnectionLayer.setScaleX(0.6f);
                badConnectionLayer.animate().setListener(null).cancel();
                badConnectionLayer.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK)
                        .setDuration(300).start();
            } else {
                if (badConnectionLayer.getVisibility() == View.GONE) {
                    return;
                }
                badConnectionLayer.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        badConnectionLayer.setVisibility(View.GONE);
                    }
                }).setDuration(300).start();
            }
        }
    }

    public void setDrawCallIcon() {
        timerView.setDrawCallIcon();
    }
}
