package org.telegram.widgets.demo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** The few of Telegram's AndroidUtilities the demo needs, so its code reads as it would in TMessagesProj. */
public final class AndroidUtilities {

    public static float density = 1;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private AndroidUtilities() {
    }

    public static void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
    }

    public static int dp(float value) {
        return value == 0 ? 0 : (int) Math.ceil(density * value);
    }

    public static void runOnUIThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            main.post(runnable);
        }
    }
}
