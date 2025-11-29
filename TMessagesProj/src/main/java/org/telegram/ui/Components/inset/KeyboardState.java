package org.telegram.ui.Components.inset;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;

class KeyboardState {
    public enum State {
        STATE_FULLY_HIDDEN,
        STATE_ANIMATING_TO_FULLY_HIDDEN,
        STATE_ANIMATING_TO_FULLY_VISIBLE,
        STATE_FULLY_VISIBLE
    }

    private final Utilities.Callback<State> onUpdateListener;
    private final long keyboardDuration;

    private State state = State.STATE_FULLY_HIDDEN;

    KeyboardState(Utilities.Callback<State> onKeyboardStateChanged) {
        this.onUpdateListener = onKeyboardStateChanged;
        this.keyboardDuration = (long) (AdjustPanLayoutHelper.keyboardDuration * AndroidUtilities.getAnimatorDurationScale() * 1.1f);
    }

    public State setKeyboardVisibility(boolean isVisible, boolean immediately, boolean notify) {
        final State newState;
        if (immediately) {
            newState = isVisible ?
                State.STATE_FULLY_VISIBLE :
                State.STATE_FULLY_HIDDEN;
        } else {
            newState = isVisible ?
                State.STATE_ANIMATING_TO_FULLY_VISIBLE :
                State.STATE_ANIMATING_TO_FULLY_HIDDEN;
        }

        if (state != newState) {
            setState(newState, notify);
        }

        return newState;
    }

    public State getState() {
        return state;
    }

    private void setState(State state, boolean notify) {
        if (this.state != state) {
            AndroidUtilities.cancelRunOnUIThread(applyPendingStateR);
            this.state = state;
            if (notify) {
                this.onUpdateListener.run(state);
            }
            if (state == State.STATE_ANIMATING_TO_FULLY_HIDDEN || state == State.STATE_ANIMATING_TO_FULLY_VISIBLE) {
                AndroidUtilities.runOnUIThread(applyPendingStateR, keyboardDuration);
            }
        }
    }

    private final Runnable applyPendingStateR = this::applyPendingState;

    private void applyPendingState() {
        if (state == State.STATE_ANIMATING_TO_FULLY_HIDDEN) {
            setState(State.STATE_FULLY_HIDDEN, true);
        } else if (state == State.STATE_ANIMATING_TO_FULLY_VISIBLE) {
            setState(State.STATE_FULLY_VISIBLE, true);
        }
    }
}
