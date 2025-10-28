package org.telegram.messenger.pip.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.PipSource;

public class PipUtils {
    public static final String TAG = "PIP_DEBUG";

    public static WindowManager.LayoutParams createWindowLayoutParams(Context context, boolean inAppOnly) {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.type = getWindowLayoutParamsType(context, inAppOnly);
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        return windowLayoutParams;
    }

    public static int getWindowLayoutParamsType(Context context, boolean inAppOnly) {
        if (!inAppOnly && AndroidUtilities.checkInlinePermissions(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        } else {
            return WindowManager.LayoutParams.TYPE_APPLICATION;
        }
    }

    public static @PipPermissions int checkPermissions(Context context) {
        if (AndroidUtilities.checkInlinePermissions(context)) {
            return PipPermissions.PIP_GRANTED_OVERLAY;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (AndroidUtilities.checkPipPermissions(context)) {
                return PipPermissions.PIP_GRANTED_PIP;
            } else {
                return PipPermissions.PIP_DENIED_PIP;
            }
        } else {
            return PipPermissions.PIP_DENIED_OVERLAY;
        }
    }

    public static boolean checkAnyPipPermissions(Context context) {
        return checkPermissions(context) > 0;
    }

    public static boolean useAutoEnterInPictureInPictureMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void applyPictureInPictureParams(Activity activity, PipSource source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (source != null) {
                AndroidUtilities.setPictureInPictureParams(activity, source.buildPictureInPictureParams());
            } else {
                AndroidUtilities.resetPictureInPictureParams(activity);
            }
        }
    }

    private static final int[] tmpCords = new int[2];
    public static void getPipSourceRectHintPosition(Activity activity, View view, Rect out) {
        int l, t, r, b;
        view.getLocationOnScreen(tmpCords);
        l = tmpCords[0];
        t = tmpCords[1];

        final View activityView = activity.getWindow().getDecorView();

        activityView.getLocationOnScreen(tmpCords);
        l -= tmpCords[0];
        t -= tmpCords[1];
        r = l + view.getWidth();
        b = t + view.getHeight();

        out.set(
            MathUtils.clamp(l, tmpCords[0], tmpCords[0] + activityView.getWidth()),
            MathUtils.clamp(t, tmpCords[1], tmpCords[1] + activityView.getHeight()),
            MathUtils.clamp(r, tmpCords[0], tmpCords[0] + activityView.getWidth()),
            MathUtils.clamp(b, tmpCords[1], tmpCords[1] + activityView.getHeight())
        );
    }
}
