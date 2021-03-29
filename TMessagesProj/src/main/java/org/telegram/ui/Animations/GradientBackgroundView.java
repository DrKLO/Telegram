package org.telegram.ui.Animations;

import android.content.Context;
import android.graphics.SurfaceTexture;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.GLTextureView;

public class GradientBackgroundView extends GLTextureView {

    private final GradientGLDrawer drawer = new GradientGLDrawer(getContext());

    public GradientBackgroundView(@NonNull Context context) {
        super(context);
        setDrawer(drawer);
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureAvailable(surface, width, height);
        setColors(BackgroundAnimationController.getColorsCopy());
    }

    public void setColors(int[] colors) {
        for (int i = 0; i != colors.length; ++i) {
            drawer.setColor(i, colors[i]);
        }
    }
}
