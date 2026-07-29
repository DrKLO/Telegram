package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;

import me.vkryl.core.reference.ReferenceList;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class LayeredCaptureSource implements BlurredBackgroundSource {

    public interface OverlayRenderer {
        void renderOverlay(Canvas canvas);
    }

    private final BlurredBackgroundSource source;
    private final OverlayRenderer overlayRenderer;
    private final ReferenceList<BlurredBackgroundDrawableRenderNode> drawables = new ReferenceList<>();

    public LayeredCaptureSource(BlurredBackgroundSource source, OverlayRenderer overlayRenderer) {
        this.source = source;
        this.overlayRenderer = overlayRenderer;
    }

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        source.draw(canvas, left, top, right, bottom);
        overlayRenderer.renderOverlay(canvas);
    }

    @Override
    public void dispatchOnDrawablesRelativePositionChange() {
        source.dispatchOnDrawablesRelativePositionChange();
    }

    public void invalidateConsumers() {
        for (BlurredBackgroundDrawableRenderNode drawable : drawables) {
            drawable.invalidateDisplayList();
        }
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        final BlurredBackgroundDrawableRenderNode drawable = new BlurredBackgroundDrawableRenderNode(this);
        drawables.add(drawable);
        return drawable;
    }
}
