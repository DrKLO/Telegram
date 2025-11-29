package org.telegram.ui.Components.inset;

public interface WindowInsetsInAppController {
    void requestInAppKeyboardHeight(int inAppKeyboardHeight);
    void resetInAppKeyboardHeight(boolean waitKeyboardOpen);
}
