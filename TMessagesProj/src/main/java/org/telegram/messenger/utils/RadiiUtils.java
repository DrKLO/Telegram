package org.telegram.messenger.utils;

public class RadiiUtils {
    public static boolean radiiAreSame(float[] radii) {
        if (radii == null || radii.length != 8) {
            return false;
        }

        return radii[0] == radii[1]
                && radii[0] == radii[2]
                && radii[0] == radii[3]
                && radii[0] == radii[4]
                && radii[0] == radii[5]
                && radii[0] == radii[6]
                && radii[0] == radii[7];
    }
}
