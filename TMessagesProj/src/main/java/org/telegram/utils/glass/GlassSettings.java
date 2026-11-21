package org.telegram.utils.glass;

import java.util.Objects;

public class GlassSettings {

    public final boolean isBlurEnabled;
    public final boolean isLiquidGlassEnabled;

    public GlassSettings(boolean isBlurEnabled, boolean isLiquidGlassEnabled) {
        this.isBlurEnabled = isBlurEnabled;
        this.isLiquidGlassEnabled = isLiquidGlassEnabled;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof GlassSettings)) {
            return false;
        }

        GlassSettings settings = (GlassSettings) object;
        return isBlurEnabled == settings.isBlurEnabled
                && isLiquidGlassEnabled == settings.isLiquidGlassEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isBlurEnabled, isLiquidGlassEnabled);
    }
}