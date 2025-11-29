package org.telegram.ui.Components.inset;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.android.animator.VariableRect;

public class WindowInsetsStateHolder implements WindowInsetsProvider, WindowInsetsInAppController {
    private final FactorAnimator insetsAnimator;
    private final VariableFloat keyboardVisibility = new VariableFloat(0);
    private final VariableRect insetsMaxRect = new VariableRect();
    private final VariableRect insetsImeRect = new VariableRect();

    private final KeyboardState keyboardState = new KeyboardState(this::onKeyboardStateChanged);
    private final Runnable onUpdateListener;

    public WindowInsetsStateHolder(Runnable onUpdateListener) {
        this.onUpdateListener = onUpdateListener;
        this.insetsAnimator = new FactorAnimator(0, new FactorAnimator.Target() {
            @Override
            public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
                insetsMaxRect.applyAnimation(factor);
                insetsImeRect.applyAnimation(factor);
                keyboardVisibility.applyAnimation(factor);
                onUpdateListener.run();
            }

            @Override
            public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
                boolean changed = false;
                if (getAnimatedImeBottomInset() == 0 && inAppKeyboardState == IN_APP_IME_STATE_HIDE_AFTER_ANIMATION_END || inAppKeyboardState == IN_APP_IME_STATE_HIDE_AFTER_KEYBOARD_OPEN) {
                    inAppKeyboardState = IN_APP_IME_STATE_HIDDEN;
                    changed = true;
                }
                if (finalFactor == 1f) {
                    if (inAppKeyboardViewHeight != inAppKeyboardHeight) {
                        inAppKeyboardViewHeight = inAppKeyboardHeight;
                        changed = true;
                    }
                }
                if (changed) {
                    onUpdateListener.run();
                }
            }
        }, AdjustPanLayoutHelper.keyboardInterpolator, AdjustPanLayoutHelper.keyboardDuration);
    }

    private void onKeyboardStateChanged(KeyboardState.State state) {
        if (state == KeyboardState.State.STATE_FULLY_VISIBLE && (inAppKeyboardState == IN_APP_IME_STATE_HIDE_AFTER_ANIMATION_END || inAppKeyboardState == IN_APP_IME_STATE_HIDE_AFTER_KEYBOARD_OPEN)) {
            inAppKeyboardState = IN_APP_IME_STATE_HIDDEN;
        }

        onUpdateListener.run();
    }


    private Insets rootAnimatedInsetsIme;

    public void attach(View view) {

    }







    private WindowInsetsCompat lastInsets;

    public void setInsets(@Nullable WindowInsetsCompat insets) {
        final boolean animated = lastInsets != null;
        this.lastInsets = insets;


        final Insets systemInsets = insets != null ? insets.getInsets(WindowInsetsCompat.Type.systemBars()) : Insets.NONE;
        final Insets imeInsets = insets != null ? insets.getInsets(WindowInsetsCompat.Type.ime()) : Insets.NONE;

        final KeyboardState.State oldKeyboardState = keyboardState.getState();
        final KeyboardState.State newKeyboardState = keyboardState.setKeyboardVisibility(imeInsets.bottom > 0, !animated, false);

        if (inAppKeyboardState == IN_APP_IME_STATE_HIDE_AFTER_ANIMATION_END) {
            inAppKeyboardHeight = 0;
        }
        if (inAppKeyboardState == IN_APP_IME_STATE_HIDE_AFTER_KEYBOARD_OPEN && imeInsets.bottom > 0) {
            inAppKeyboardHeight = 0;
        }

        final Insets inAppInsets = Insets.of(0, 0, 0, inAppKeyboardHeight);
        final Insets inputInsets = Insets.max(imeInsets, inAppInsets);
        final Insets maxInsets = Insets.max(systemInsets, inputInsets);

        if (animated) {
            final boolean changed = keyboardVisibility.differs(inputInsets.bottom > 0 ? 1f : 0f)
                || insetsMaxRect.differs(maxInsets.left, maxInsets.top, maxInsets.right, maxInsets.bottom)
                || insetsImeRect.differs(inputInsets.left, inputInsets.top, inputInsets.right, inputInsets.bottom);

            if (changed) {
                insetsAnimator.cancel();
                keyboardVisibility.finishAnimation(false);
                insetsMaxRect.finishAnimation(false);
                insetsImeRect.finishAnimation(false);

                keyboardVisibility.setTo(inputInsets.bottom > 0 ? 1f : 0f);
                insetsMaxRect.setTo(maxInsets.left, maxInsets.top, maxInsets.right, maxInsets.bottom);
                insetsImeRect.setTo(inputInsets.left, inputInsets.top, inputInsets.right, inputInsets.bottom);

                insetsAnimator.forceFactor(0);
                insetsAnimator.animateTo(1);
            } else {
                if (oldKeyboardState != newKeyboardState) {
                    onUpdateListener.run();
                }
            }
        } else {
            insetsAnimator.cancel();

            keyboardVisibility.set(inputInsets.bottom > 0 ? 1 : 0);
            insetsMaxRect.set(maxInsets.left, maxInsets.top, maxInsets.right, maxInsets.bottom);
            insetsImeRect.set(inputInsets.left, inputInsets.top, inputInsets.right, inputInsets.bottom);
            onUpdateListener.run();
        }
    }



    @Override
    public boolean inAppViewIsVisible() {
        return inAppKeyboardState != IN_APP_IME_STATE_HIDDEN;
    }

    @Override
    public int getInAppKeyboardRecommendedViewHeight() {
        return inAppKeyboardViewHeight;
    }


    @Override
    public int getCurrentNavigationBarInset() {
        return lastInsets != null ?
            lastInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom : 0;
    }

    @Override
    public Insets getInsets(int type) {
        return lastInsets != null ? lastInsets.getInsets(type) : Insets.NONE;
    }

    @Override
    public float getAnimatedMaxBottomInset() {
        return insetsMaxRect.getBottom();
    }

    @Override
    public int getCurrentMaxBottomInset() {
        return Math.max(getInsets(WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.systemBars()).bottom, inAppKeyboardHeight);
    }

    @Override
    public float getAnimatedImeBottomInset() {
        return insetsImeRect.getBottom();
    }

    @Override
    public float getAnimatedKeyboardVisibility() {
        return keyboardVisibility.get();
    }



    private static final int IN_APP_IME_STATE_VISIBLE = 0;
    private static final int IN_APP_IME_STATE_HIDDEN = 1;
    private static final int IN_APP_IME_STATE_HIDE_AFTER_ANIMATION_END = 2; // wait when insets animate to zero
    private static final int IN_APP_IME_STATE_HIDE_AFTER_KEYBOARD_OPEN = 3; // wait keyboard fully visible

    private int inAppKeyboardState = IN_APP_IME_STATE_HIDDEN;
    private int inAppKeyboardHeight;
    private int inAppKeyboardViewHeight;

    @Override
    public void requestInAppKeyboardHeight(int inAppKeyboardHeight) {
        if (this.inAppKeyboardHeight != inAppKeyboardHeight || inAppKeyboardState != IN_APP_IME_STATE_VISIBLE) {
            AndroidUtilities.cancelRunOnUIThread(closeInAppKeyboard);

            inAppKeyboardViewHeight = Math.max(this.inAppKeyboardHeight, inAppKeyboardHeight);

            this.inAppKeyboardHeight = inAppKeyboardHeight;
            this.inAppKeyboardState = IN_APP_IME_STATE_VISIBLE;

            setInsets(lastInsets);
        }
    }

    private final Runnable closeInAppKeyboard = () -> {
        if (inAppKeyboardHeight != 0) {
            resetInAppKeyboardHeight(false);
        }
    };

    @Override
    public void resetInAppKeyboardHeight(boolean waitKeyboardOpen) {
        if (inAppKeyboardHeight == 0) {
            return;
        }

        AndroidUtilities.cancelRunOnUIThread(closeInAppKeyboard);

        this.inAppKeyboardState = waitKeyboardOpen ?
            IN_APP_IME_STATE_HIDE_AFTER_KEYBOARD_OPEN :
            IN_APP_IME_STATE_HIDE_AFTER_ANIMATION_END;

        setInsets(lastInsets);

        if (waitKeyboardOpen) {
            AndroidUtilities.runOnUIThread(closeInAppKeyboard, 1000);
        }

    }
}
