package org.telegram.utils.glass.fade;

import androidx.annotation.NonNull;

public final class GlassFadeRangeParams {
    public static final GlassFadeRangeParams FILL =
        new GlassFadeRangeParams(-1, Gravity.START, 0, 0);

    public enum Gravity {
        START, CENTER, END
    }

    public final int height;
    public final Gravity gravity;
    public final int marginStart;
    public final int marginEnd;

    public GlassFadeRangeParams(int height, @NonNull Gravity gravity, int marginStart, int marginEnd) {
        if (height != -1 && height <= 0) {
            throw new IllegalArgumentException("height must be positive or -1");
        }
        if (marginStart < 0 || marginEnd < 0) {
            throw new IllegalArgumentException("invalid gradient range");
        }
        this.height = height;
        this.gravity = gravity;
        this.marginStart = marginStart;
        this.marginEnd = marginEnd;
    }
}
