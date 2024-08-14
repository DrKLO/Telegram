package org.telegram.ui.Stars;

import android.view.View;

public abstract class ISuperRipple {

    public final View view;

    public ISuperRipple(View view) {
        this.view = view;
    }

    public void animate(float cx, float cy, float intensity) {

    }

}
