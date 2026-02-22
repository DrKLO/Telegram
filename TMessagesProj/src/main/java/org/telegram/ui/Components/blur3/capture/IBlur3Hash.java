package org.telegram.ui.Components.blur3.capture;

import android.os.Build;
import android.view.View;

public interface IBlur3Hash {

    void add(long value);

    void unsupported();

    default void add(View v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(v.getUniqueDrawingId());
        } else {
            unsupported();
        }
    }

    default void addF(float value) {
        add(Float.floatToIntBits(value));
    }

    default void add(boolean value) {
        add(value ? 1 : 0);
    }
}
