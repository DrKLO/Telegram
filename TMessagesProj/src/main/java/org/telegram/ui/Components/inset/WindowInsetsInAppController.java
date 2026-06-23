package org.Tajgram.ui.Components.inset;

import org.Tajgram.messenger.AndroidUtilities;

public interface WindowInsetsInAppController {

    default void requestInAppKeyboardHeightIncludeNavbar(int inAppKeyboardHeight) {
        if (inAppKeyboardHeight > 0) {
            requestInAppKeyboardHeight(inAppKeyboardHeight + AndroidUtilities.navigationBarHeight);
        } else {
            resetInAppKeyboardHeight(true);
        }
    }

    void requestInAppKeyboardHeight(int inAppKeyboardHeight);
    void resetInAppKeyboardHeight(boolean waitKeyboardOpen);
}
