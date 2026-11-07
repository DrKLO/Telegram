package org.telegram.widgets.demo;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Layout params in dp, as Telegram's LayoutHelper makes them. */
public final class LayoutHelper {

    public static final int MATCH_PARENT = -1;
    public static final int WRAP_CONTENT = -2;

    private LayoutHelper() {
    }

    private static int size(float size) {
        return size < 0 ? (int) size : AndroidUtilities.dp(size);
    }

    public static FrameLayout.LayoutParams createFrame(float width, float height, int gravity, float left, float top, float right, float bottom) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size(width), size(height), gravity);
        params.setMargins(AndroidUtilities.dp(left), AndroidUtilities.dp(top), AndroidUtilities.dp(right), AndroidUtilities.dp(bottom));
        return params;
    }

    public static LinearLayout.LayoutParams createLinear(float width, float height, float left, float top, float right, float bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size(width), size(height));
        params.setMargins(AndroidUtilities.dp(left), AndroidUtilities.dp(top), AndroidUtilities.dp(right), AndroidUtilities.dp(bottom));
        return params;
    }

    public static LinearLayout.LayoutParams createLinear(float width, float height, float weight) {
        return new LinearLayout.LayoutParams(size(width), size(height), weight);
    }
}
