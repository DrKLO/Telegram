package org.telegram.ui.Components.blur3.drawable.color;

import androidx.annotation.ColorInt;
import androidx.annotation.Px;

public interface BlurredBackgroundProvider extends BlurredBackgroundColorProvider {
    @ColorInt int getShadowColor();
    @ColorInt int getBackgroundColor();
    @ColorInt int getStrokeColorTop();
    @ColorInt int getStrokeColorBottom();

    @Px float getStrokeWidthTop();
    @Px float getStrokeWidthBottom();
    @Px float getShadowRadius();
    @Px float getShadowDx();
    @Px float getShadowDy();
}
