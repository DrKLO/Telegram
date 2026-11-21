package org.telegram.utils.glass.source;

import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.fade.GlassFadeBlurredDrawable;

import me.vkryl.core.reference.ReferenceList;

/**
 * One source going over to another by alpha, e.g. a menu growing out of a glass button into frosted glass.
 * Its drawables record it into their display lists: they are recorded again as the progress changes.
 */
public final class GlassSourceCrossfade implements GlassSource, BlurredBackgroundDrawableViewFactory.DrawableOwner, GlassFadeBlurredDrawable.GlassFadeDownsampledSource {
    private final GlassSource from;
    private final GlassSource to;
    private final ReferenceList<BlurredBackgroundDrawable> drawables = new ReferenceList<>();
    private float progress;

    public GlassSourceCrossfade(@NonNull GlassSource from, @NonNull GlassSource to) {
        this.from = from;
        this.to = to;
    }

    /** 0 is the first source, 1 the second one. */
    public void setProgress(float progress) {
        if (this.progress != progress) {
            this.progress = progress;
            for (BlurredBackgroundDrawable drawable : drawables) {
                drawable.updateColors();
                drawable.invalidateSelf();
            }
        }
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom) {
        if (progress < 1f) {
            from.draw(canvas, left, top, right, bottom);
        }
        if (progress > 0f) {
            if (progress < 1f) {
                canvas.saveLayerAlpha(left, top, right, bottom, (int) (0xFF * progress));
            }
            to.draw(canvas, left, top, right, bottom);
            if (progress < 1f) {
                canvas.restore();
            }
        }
    }

    /* Downsampled, for the lens: the finer of the two, the other one is on its grid */

    @Nullable
    private static GlassFadeBlurredDrawable.GlassFadeDownsampledSource downsampled(GlassSource source) {
        if (source instanceof GlassFadeBlurredDrawable.GlassFadeDownsampledSource) {
            final GlassFadeBlurredDrawable.GlassFadeDownsampledSource d = (GlassFadeBlurredDrawable.GlassFadeDownsampledSource) source;
            return d.isDownsampledSourceSupported() ? d : null;
        }
        return null;
    }

    @Override
    public boolean isDownsampledSourceSupported() {
        return downsampled(from) != null && downsampled(to) != null;
    }

    private GlassFadeBlurredDrawable.GlassFadeDownsampledSource finer() {
        final GlassFadeBlurredDrawable.GlassFadeDownsampledSource a = downsampled(from), b = downsampled(to);
        return a.getDownsample() <= b.getDownsample() ? a : b;
    }

    @Override
    public int getDownsample() {
        return finer().getDownsample();
    }

    @Override
    public int getDownsampleOffsetX() {
        return finer().getDownsampleOffsetX();
    }

    @Override
    public int getDownsampleOffsetY() {
        return finer().getDownsampleOffsetY();
    }

    @Override
    public void drawDownsampled(@NonNull Canvas canvas, @NonNull RectF boundsRelativeRoot, int targetDownsample, float targetOriginX, float targetOriginY) {
        if (progress < 1f) {
            downsampled(from).drawDownsampled(canvas, boundsRelativeRoot, targetDownsample, targetOriginX, targetOriginY);
        }
        if (progress > 0f) {
            if (progress < 1f) {
                canvas.saveLayerAlpha(null, (int) (0xFF * progress));
            }
            downsampled(to).drawDownsampled(canvas, boundsRelativeRoot, targetDownsample, targetOriginX, targetOriginY);
            if (progress < 1f) {
                canvas.restore();
            }
        }
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        final BlurredBackgroundDrawable drawable = new BlurredBackgroundDrawable(this);
        attachDrawable(drawable);
        return drawable;
    }

    @Override
    public void attachDrawable(BlurredBackgroundDrawable drawable) {
        drawables.add(drawable);
    }
}
