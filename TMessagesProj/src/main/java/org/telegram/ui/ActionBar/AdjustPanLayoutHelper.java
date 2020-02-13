package org.telegram.ui.ActionBar;

import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

public class AdjustPanLayoutHelper {

    private int[] loc = new int[2];
    private final static int FRAMES_WITHOUT_MOVE_LIMIT = 5;
    private View parentView;
    private int framesWithoutMovement;
    private int prevMovement;
    private boolean wasMovement;

    public AdjustPanLayoutHelper(View parent) {
        parentView = parent;
        parentView.getViewTreeObserver().addOnGlobalLayoutListener(this::onUpdate);
        parentView.getViewTreeObserver().addOnScrollChangedListener(this::onUpdate);
        parentView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> onUpdate());
    }

    private void onUpdate() {
        if (!SharedConfig.smoothKeyboard) {
            return;
        }
        //prevMovement = Integer.MAX_VALUE;
        framesWithoutMovement = 0;
        wasMovement = false;
        parentView.invalidate();
    }

    public void update() {
        if (parentView.getVisibility() != View.VISIBLE || parentView.getParent() == null) {
            return;
        }
        if (!AndroidUtilities.usingHardwareInput && SharedConfig.smoothKeyboard) {
            parentView.getLocationInWindow(loc);
            if (loc[1] <= 0) {
                loc[1] -= parentView.getTranslationY();
                if (Build.VERSION.SDK_INT < 21) {
                    loc[1] -= AndroidUtilities.statusBarHeight;
                }
            } else {
                loc[1] = 0;
            }
            if (loc[1] != prevMovement) {
                if (!wasMovement) {
                    onTransitionStart();
                }
                wasMovement = true;
                onPanTranslationUpdate(-loc[1]);
                framesWithoutMovement = 0;
                prevMovement = loc[1];
            } else {
                framesWithoutMovement++;
            }
            if (framesWithoutMovement < FRAMES_WITHOUT_MOVE_LIMIT) {
                parentView.invalidate();
            } else if (wasMovement) {
                onTransitionEnd();
            }
        }
    }

    protected void onPanTranslationUpdate(int y) {

    }

    protected void onTransitionStart() {

    }

    protected void onTransitionEnd() {

    }
}
