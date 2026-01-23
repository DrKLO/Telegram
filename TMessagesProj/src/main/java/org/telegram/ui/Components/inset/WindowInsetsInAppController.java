package org.telegram.ui.Components.inset;

import org.telegram.messenger.AndroidUtilities;

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
