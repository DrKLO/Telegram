package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;

public class FadingTextViewLayout extends FrameLayout {

    private final ValueAnimator animator;

    private CharSequence text;
    private TextView foregroundView;
    private TextView currentView;
    private TextView nextView;

    public FadingTextViewLayout(Context context) {
        this(context, false);
    }

    public FadingTextViewLayout(Context context, boolean hasStaticChars) {
        super(context);
        for (int i = 0; i < 2 + (hasStaticChars ? 1 : 0); i++) {
            final TextView textView = new TextView(context);
            onTextViewCreated(textView);
            addView(textView);
            if (i == 0) {
                currentView = textView;
            } else {
                textView.setVisibility(GONE);
                if (i == 1) {
                    textView.setAlpha(0f);
                    nextView = textView;
                } else {
                    foregroundView = textView;
                }
            }
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(200);
        animator.setInterpolator(null);
        animator.addUpdateListener(a -> {
            final float fraction = a.getAnimatedFraction();
            currentView.setAlpha(CubicBezierInterpolator.DEFAULT.getInterpolation(fraction));
            nextView.setAlpha(CubicBezierInterpolator.DEFAULT.getInterpolation(1f - fraction));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentView.setLayerType(LAYER_TYPE_NONE, null);
                nextView.setLayerType(LAYER_TYPE_NONE, null);
                nextView.setVisibility(GONE);
                if (foregroundView != null) {
                    currentView.setText(text); // remove spans
                    foregroundView.setVisibility(GONE);
                }
            }

            @Override
            public void onAnimationStart(Animator animation) {
                currentView.setLayerType(LAYER_TYPE_HARDWARE, null);
                nextView.setLayerType(LAYER_TYPE_HARDWARE, null);
                if (ViewCompat.isAttachedToWindow(currentView)) {
                    currentView.buildLayer();
                }
                if (ViewCompat.isAttachedToWindow(nextView)) {
                    nextView.buildLayer();
                }
            }
        });
    }

    public void setText(CharSequence text) {
        setText(text, true, true);
    }

    public void setText(CharSequence text, boolean animated) {
        setText(text, animated, true);
    }

    public void setText(CharSequence text, boolean animated, boolean dontAnimateUnchangedStaticChars) {
        if (!TextUtils.equals(text, currentView.getText())) {
            if (animator != null) {
                animator.end();
            }
            this.text = text;
            if (animated) {
                if (dontAnimateUnchangedStaticChars && foregroundView != null) {
                    final int staticCharsCount = getStaticCharsCount();

                    if (staticCharsCount > 0) {
                        final CharSequence currentText = currentView.getText();
                        final int length = Math.min(staticCharsCount, Math.min(text.length(), currentText.length()));

                        final List<Point> points = new ArrayList<>();

                        int startIndex = -1;
                        for (int i = 0; i < length; i++) {
                            final char c = currentText.charAt(i);
                            if (text.charAt(i) == c) {
                                if (startIndex >= 0) {
                                    points.add(new Point(startIndex, i));
                                    startIndex = -1;
                                }
                            } else if (startIndex == -1) {
                                startIndex = i;
                            }
                        }

                        if (startIndex == 0) {
                            // no unchanged static chars
                        } else if (startIndex > 0) {
                            points.add(new Point(startIndex, length));
                        } else { // fully the same
                            points.add(new Point(length, 0));
                        }

                        if (!points.isEmpty()) {
                            final SpannableString foregroundText = new SpannableString(text.subSequence(0, length));
                            final SpannableString currentSpannableText = new SpannableString(currentText);
                            final SpannableString spannableText = new SpannableString(text);

                            int lastIndex = 0;
                            for (int i = 0, N = points.size(); i < N; i++) {
                                final Point point = points.get(i);
                                if (point.y > point.x) {
                                    foregroundText.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), point.x, point.y, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                                }
                                if (point.x > lastIndex) {
                                    currentSpannableText.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), lastIndex, point.x, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                                    spannableText.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), lastIndex, point.x, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                                }
                                lastIndex = point.y;
                            }

                            foregroundView.setVisibility(VISIBLE);
                            foregroundView.setText(foregroundText);

                            currentView.setText(currentSpannableText);
                            text = spannableText;
                        }
                    }
                }
                nextView.setVisibility(VISIBLE);
                nextView.setText(text);
                showNext();
            } else {
                currentView.setText(text);
            }
        }
    }

    public CharSequence getText() {
        return text;
    }

    public TextView getCurrentView() {
        return currentView;
    }

    public TextView getNextView() {
        return nextView;
    }

    private void showNext() {
        final TextView prevView = currentView;
        currentView = nextView;
        nextView = prevView;
        animator.start();
    }

    protected void onTextViewCreated(TextView textView) {
        textView.setSingleLine(true);
        textView.setMaxLines(1);
    }

    protected int getStaticCharsCount() {
        return 0;
    }
}
