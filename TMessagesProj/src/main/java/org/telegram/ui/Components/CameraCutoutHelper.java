package org.telegram.ui.Components;

import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import org.telegram.messenger.AndroidUtilities;
import java.util.List;

public class CameraCutoutHelper {

    public static android.graphics.Rect cutout = new android.graphics.Rect();
    public static boolean isTop = false;
    public static boolean FORCE_TOP = false;

    public static void update(View decorView) {
        cutout = getFrontCameraCutout(decorView);
    }

    private static android.graphics.Rect getFrontCameraCutout(View rootView) {
        android.graphics.Rect rect = calculateRealCameraCutout(rootView);
        float realCenter = AndroidUtilities.displaySize.x / 2f;
        if (FORCE_TOP || rect.width() == 0 && rect.height() == 0 || Math.abs(rect.centerX() - realCenter) > AndroidUtilities.dp(20)) {
            isTop = true;
            int r = AndroidUtilities.dp(30);
            int centerX = (int) realCenter;
            int centerY = -r;
            rect.set(
                    centerX - r * 2,
                    centerY - r,
                    centerX + r * 2,
                    centerY + r
            );
        } else {
            int centerX = rect.centerX() - AndroidUtilities.dp(1);
            int centerY = rect.centerY() - AndroidUtilities.dp(7);
            int newHeight = rect.height();
            if (newHeight > 0 && newHeight < AndroidUtilities.dp(16)) {
                newHeight = AndroidUtilities.dp(16);
            }
            int newWidth = newHeight;

            rect.set(
                    centerX - newWidth  / 2,
                    centerY - newHeight / 2,
                    centerX + newWidth  / 2,
                    centerY + newHeight / 2
            );
        }
        return rect;
    }

    private static android.graphics.Rect calculateRealCameraCutout(View rootView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return new android.graphics.Rect();
        }
        WindowInsets insets = rootView.getRootWindowInsets();
        if (insets == null) {
            return new android.graphics.Rect();
        }
        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout == null) {
            return new android.graphics.Rect();
        }
        List<android.graphics.Rect> bounds = cutout.getBoundingRects();
        if (bounds == null || bounds.isEmpty()) {
            return new android.graphics.Rect();
        }
        android.graphics.Rect best = null;
        float winCenterX = rootView.getWidth() / 2f;
        for (android.graphics.Rect r : bounds) {
            if (r.top != 0) continue;
            if (best == null || Math.abs(r.centerX() - winCenterX) < Math.abs(best.centerX() - winCenterX)) {
                best = r;
            }
        }
        if (best == null) {
            return new android.graphics.Rect();
        }

        android.graphics.Rect win = new android.graphics.Rect(best);
        int[] loc = new int[2];
        rootView.getLocationInWindow(loc);
        win.offset(-loc[0], -loc[1]);

        return calculateLensArea(win);
    }

    private static android.graphics.Rect calculateLensArea(android.graphics.Rect full) {
        int height = full.height();
        if (height <= 0) return new android.graphics.Rect();

        int lensH = Math.max(1, (int) (height * 0.20f));
        return new android.graphics.Rect(
                full.left,
                full.bottom - lensH,
                full.right,
                full.bottom
        );
    }
}