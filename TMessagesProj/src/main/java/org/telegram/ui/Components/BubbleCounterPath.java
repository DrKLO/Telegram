package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Path;
import android.graphics.RectF;

public class BubbleCounterPath {

    private static RectF tmpRect;

    public static void addBubbleRect(Path path, RectF bounds, float radius) {
        if (path == null) {
            return;
        }
        if (tmpRect == null) {
            tmpRect = new RectF();
        }

        final float D = radius * 2;

        path.rewind();

        tmpRect.set(0, -bounds.height(), D, -bounds.height() + D);
        path.arcTo(tmpRect, 180, 90);

        tmpRect.set(bounds.width() - D, -bounds.height(), bounds.width(), -bounds.height() + D);
        path.arcTo(tmpRect, 270, 90);

        tmpRect.set(bounds.width() - D, -D, bounds.width(), 0);
        path.arcTo(tmpRect, 0, 90);

        path.quadTo(radius, 0, radius, 0);
        path.cubicTo(dp(7.62f), dp(-.5f), dp(5.807f), dp(-1.502f), dp(6.02f), dp(-1.386f));
        path.cubicTo(dp(4.814f), dp(-.81f), dp(2.706f), dp(-.133f), dp(3.6f), dp(-.44f));
        path.cubicTo(dp(1.004f), dp(-.206f), dp(-.047f), dp(-.32f), dp(.247f), dp(-.29f));
        path.cubicTo(dp(-.334f), dp(-1.571f), 0, dp(-1.155f), dp(-.06f), dp(-1.154f));
        path.cubicTo(dp(1.083f), dp(-2.123f), dp(1.667f), dp(-3.667f), dp(1.453f), dp(-3.12f));
        path.cubicTo(dp(2.1f), dp(-4.793f), dp(1.24f), dp(-6.267f), dp(1.67f), dp(-5.53f));
        path.quadTo(0, -radius + dp(2.187f), 0, -radius);
        path.close();

        path.offset(bounds.left, bounds.bottom);
    }
}
