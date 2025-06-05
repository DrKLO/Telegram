package org.telegram.messenger;

import android.os.Build;

import java.lang.reflect.Field;

public class OneUIUtilities {
    public final static int ONE_UI_4_0 = 40000;

    private static Boolean isOneUI;
    private static int oneUIEncodedVersion;
    private static int oneUIMajorVersion;
    private static float oneUIMinorVersion;

    @SuppressWarnings("JavaReflectionMemberAccess")
    public static boolean isOneUI() {
        if (isOneUI != null) {
            return isOneUI;
        }

        try {
            Field f = Build.VERSION.class.getDeclaredField("SEM_PLATFORM_INT");
            f.setAccessible(true);
            int semPlatformInt = (int) f.get(null);
            if (semPlatformInt < 100000) {
                // Samsung Experience then
                return false;
            }

            oneUIEncodedVersion = semPlatformInt - 90000;
            oneUIMajorVersion = oneUIEncodedVersion / 10000;
            oneUIMinorVersion = (oneUIEncodedVersion % 10000) / 100F;
            isOneUI = true;
        } catch (Exception e) {
            isOneUI = false;
        }
        return isOneUI;
    }

    public static boolean hasBuiltInClipboardToasts() {
        return isOneUI() && getOneUIEncodedVersion() == ONE_UI_4_0;
    }

    public static int getOneUIMajorVersion() {
        if (!isOneUI()) {
            return 0;
        }
        return oneUIMajorVersion;
    }

    public static int getOneUIEncodedVersion() {
        if (!isOneUI()) {
            return 0;
        }
        return oneUIEncodedVersion;
    }

    public static float getOneUIMinorVersion() {
        if (!isOneUI()) {
            return 0;
        }
        return oneUIMinorVersion;
    }
}
