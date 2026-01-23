package org.telegram.ui.Components.inset;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

public interface WindowInsetsProvider {

    float getAnimatedMaxBottomInset();
    float getAnimatedImeBottomInset();
    float getAnimatedKeyboardVisibility();


    int getCurrentMaxBottomInset();




    Insets getInsets(int type);
    default int getCurrentNavigationBarInset() {
        return getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
    }

    boolean inAppViewIsVisible();

    int getInAppKeyboardRecommendedViewHeight();
}
