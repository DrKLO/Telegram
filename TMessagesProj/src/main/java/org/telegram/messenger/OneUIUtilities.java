package org.telegram.messenger;

import android.os.Build;

public class OneUIUtilities {
    private static Boolean isOneUI;

    @SuppressWarnings("JavaReflectionMemberAccess")
    public static boolean isOneUI() {
        if (isOneUI != null) {
            return isOneUI;
        }

        try {
            Build.VERSION.class.getDeclaredField("SEM_PLATFORM_INT");
            isOneUI = true;
        } catch (NoSuchFieldException e) {
            isOneUI = false;
        }
        return isOneUI;
    }
}
