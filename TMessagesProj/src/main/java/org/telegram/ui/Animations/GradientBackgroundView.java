package org.telegram.ui.Animations;

import android.content.Context;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.GLTextureView;

public class GradientBackgroundView extends GLTextureView {

    private final GradientGLDrawer drawer = new GradientGLDrawer(getContext());

    public GradientBackgroundView(@NonNull Context context) {
        super(context);
        setDrawer(drawer);
    }

    public void setColors(int[] colors) {
        for (int i = 0; i != colors.length; ++i) {
            drawer.setColor(i, colors[i]);
        }
    }
}
