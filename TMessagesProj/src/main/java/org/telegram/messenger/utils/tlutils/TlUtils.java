package org.telegram.messenger.utils.tlutils;

public class TlUtils {
    public static boolean isInstance(Object obj, final Class<?>... classes) {
        if (obj == null || classes == null) return false;

        for (Class<?> cls : classes) {
            if (cls.isInstance(obj)) {
                return true;
            }
        }
        return false;
    }
}
