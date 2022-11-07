package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.checkerframework.checker.units.qual.C;
import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public class LoadingAnimatedTextView extends View {

    private static class State {
        private StaticLayout[] words;
        private int[] hashCodes;
        private int[] lengths;
    }

    private TextPaint paint;
    private State currentState;
    private State nextState;
    private Rect loadingRect;
    private float stateTransition = 0;
    private ValueAnimator stateTransitionAnimator;

    public LoadingAnimatedTextView(Context context, TextPaint textPaint) {
        super(context);
        paint = textPaint;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


    private static CharSequence[] splitToWords(CharSequence text) {
        ArrayList<Integer> spaces = new ArrayList<>();
        for (int i = 0; i < text.length(); ++i)
            if (text.charAt(i) == ' ')
                spaces.add(i);
        CharSequence[] words = new CharSequence[spaces.size() + 1];
        for (int i = 0, s = -1; i < words.length; ++i)
            words[i] = text.subSequence(++s, s = i >= spaces.size() ? text.length() : spaces.get(i));
        return words;
    }

    private static State makeState(CharSequence before, CharSequence loaded, CharSequence after) {
        State state = new State();
        CharSequence[] beforeWords = splitToWords(before);
        CharSequence[] loadedWords = loaded == null ? null : splitToWords(loaded);
        CharSequence[] afterWords = splitToWords(after);
        return state;
    }


    public void setText(CharSequence before, CharSequence after) {
        setText(before, null, after);
    }

    public void setText(CharSequence before, CharSequence loaded, CharSequence after) {
        if (stateTransitionAnimator != null) {
            stateTransitionAnimator.cancel();
        }

        stateTransitionAnimator = ValueAnimator.ofFloat(stateTransition, loaded == null ? 1f : 0f);
        stateTransitionAnimator.addUpdateListener(anm -> {
            stateTransition = (float) anm.getAnimatedValue();
            invalidate();
        });
        stateTransitionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator anm) {
                stateTransition = (float) stateTransitionAnimator.getAnimatedValue();
                invalidate();
                stateTransitionAnimator = null;
            }
        });
        stateTransitionAnimator.start();
    }
}
