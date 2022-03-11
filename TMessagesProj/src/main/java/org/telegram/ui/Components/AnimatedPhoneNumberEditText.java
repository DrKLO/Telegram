package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.Keep;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AnimatedPhoneNumberEditText extends HintEditText {
    private final static float SPRING_MULTIPLIER = 100f;

    private final static boolean USE_NUMBERS_ANIMATION = false;

    private ArrayList<StaticLayout> letters = new ArrayList<>();
    private ArrayList<StaticLayout> oldLetters = new ArrayList<>();
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private ObjectAnimator animator;
    private float progress;
    private String oldText = "";

    private HintFadeProperty hintFadeProperty = new HintFadeProperty();
    private List<Float> hintAnimationValues = new ArrayList<>();
    private List<SpringAnimation> hintAnimations = new ArrayList<>();

    private Boolean wasHintVisible;
    private String wasHint;

    private Runnable hintAnimationCallback;

    public AnimatedPhoneNumberEditText(Context context) {
        super(context);
    }

    @Override
    public void setHintText(String value) {
        boolean show = !TextUtils.isEmpty(value);
        boolean runAnimation = false;

        if (wasHintVisible == null || wasHintVisible != show) {
            hintAnimationValues.clear();
            for (SpringAnimation a : hintAnimations) {
                a.cancel();
            }
            hintAnimations.clear();
            wasHintVisible = show;
            runAnimation = TextUtils.isEmpty(getText());
        }

        String str = show ? value : wasHint;
        if (str == null) str = "";
        wasHint = value;

        if (show || !runAnimation) {
            super.setHintText(value);
        }

        if (runAnimation) {
            runHintAnimation(str.length(), show, () -> {
                hintAnimationValues.clear();
                for (SpringAnimation a : hintAnimations) {
                    a.cancel();
                }

                if (!show) {
                    super.setHintText(value);
                }
            });
        }
    }

    @Override
    public String getHintText() {
        return wasHint;
    }

    private void runHintAnimation(int length, boolean show, Runnable callback) {
        if (hintAnimationCallback != null) {
            removeCallbacks(hintAnimationCallback);
        }
        for (int i = 0; i < length; i++) {
            float startValue = show ? 0 : 1, finalValue = show ? 1 : 0;

            SpringAnimation springAnimation = new SpringAnimation(i, hintFadeProperty)
                    .setSpring(new SpringForce(finalValue * SPRING_MULTIPLIER)
                            .setStiffness(500)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                            .setFinalPosition(finalValue * SPRING_MULTIPLIER))
                    .setStartValue(startValue * SPRING_MULTIPLIER);
            hintAnimations.add(springAnimation);
            hintAnimationValues.add(startValue);
            postDelayed(springAnimation::start, i * 5L);
        }
        postDelayed(hintAnimationCallback = callback, length * 5L + 150L);
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);

        textPaint.setTextSize(TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics()));
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);

        textPaint.setColor(color);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);

        if (USE_NUMBERS_ANIMATION && !isTextWatchersSuppressed()) {
            setNewText(text.toString().trim());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (USE_NUMBERS_ANIMATION) {
            int color = getCurrentTextColor();
            setTextColor(Color.TRANSPARENT);
            super.onDraw(canvas);
            setTextColor(color);

            if (letters.isEmpty() && oldLetters.isEmpty()) {
                return;
            }
            float height = letters.isEmpty() ? oldLetters.get(0).getHeight() : letters.get(0).getHeight();

            float x = 0;
            float oldDx = 0;
            canvas.save();
            canvas.translate(getPaddingLeft() + x, (getMeasuredHeight() - height) / 2);
            int count = Math.max(letters.size(), oldLetters.size());
            for (int a = 0; a < count; a++) {
                canvas.save();
                StaticLayout old = a < oldLetters.size() ? oldLetters.get(a) : null;
                StaticLayout layout = a < letters.size() ? letters.get(a) : null;
                if (progress < 0) {
                    if (old != null) {
                        textPaint.setAlpha((int) (255 * -progress));
                        canvas.save();
                        canvas.translate(oldDx, (1f + progress) * height);
                        old.draw(canvas);
                        canvas.restore();
                    }
                    if (layout != null) {
                        if (a == count - 1 || old != null) {
                            textPaint.setAlpha((int) (255 * (1f + progress)));
                            canvas.translate(0, -progress * height);
                        } else {
                            textPaint.setAlpha(255);
                        }
                    }
                } else if (layout != null) {
                    textPaint.setAlpha(255);
                }
                if (layout != null) {
                    layout.draw(canvas);
                }
                canvas.restore();
                canvas.translate(layout != null ? layout.getLineWidth(0) : old.getLineWidth(0) + AndroidUtilities.dp(1), 0);
                if (layout != null && old != null) {
                    oldDx += old.getLineWidth(0) - layout.getLineWidth(0);
                }
            }
            canvas.restore();
        } else super.onDraw(canvas);
    }

    public void setNewText(String text) {
        if (oldLetters == null || letters == null || Objects.equals(oldText, text)) return;

        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        oldLetters.clear();
        oldLetters.addAll(letters);
        letters.clear();
        boolean replace = TextUtils.isEmpty(oldText) && !TextUtils.isEmpty(text);

        progress = 0;
        for (int a = 0; a < text.length(); a++) {
            String ch = text.substring(a, a + 1);
            String oldCh = !oldLetters.isEmpty() && a < oldText.length() ? oldText.substring(a, a + 1) : null;
            if (!replace && oldCh != null && oldCh.equals(ch)) {
                letters.add(oldLetters.get(a));
                oldLetters.set(a, null);
            } else {
                if (replace && oldCh == null) {
                    oldLetters.add(new StaticLayout("", textPaint, 0, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false));
                }
                StaticLayout layout = new StaticLayout(ch, textPaint, (int) Math.ceil(textPaint.measureText(ch)), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                letters.add(layout);
            }
        }
        if (!oldLetters.isEmpty()) {
            animator = ObjectAnimator.ofFloat(this, "progress", -1, 0);
            animator.setDuration(150);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animator = null;
                    oldLetters.clear();
                }
            });
            animator.start();
        }
        oldText = text;
        invalidate();
    }

    @Override
    protected void onPreDrawHintCharacter(int index, Canvas canvas, float pivotX, float pivotY) {
        if (index < hintAnimationValues.size()) {
            hintPaint.setAlpha((int) (hintAnimationValues.get(index) * 0xFF));
        }
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    private final class HintFadeProperty extends FloatPropertyCompat<Integer> {
        public HintFadeProperty() {
            super("hint_fade");
        }

        @Override
        public float getValue(Integer object) {
            return object < hintAnimationValues.size() ? hintAnimationValues.get(object) * SPRING_MULTIPLIER : 0;
        }

        @Override
        public void setValue(Integer object, float value) {
            if (object < hintAnimationValues.size()) {
                hintAnimationValues.set((int) object, value / SPRING_MULTIPLIER);
                invalidate();
            }
        }
    }
}
