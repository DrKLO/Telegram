package org.telegram.utils.pip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * TextureView that keeps its SurfaceTexture alive across detach / reattach, so the view
 * can move between windows while the producer (ExoPlayer) keeps rendering into it.
 *
 * The texture is either adopted from another TextureView ({@link #adoptSurfaceTexture}, after
 * that view has been detached) or created by this view itself on its first draw. In both cases
 * it is never released on detach; the external listener (e.g. ExoPlayer) does not get
 * {@code onSurfaceTextureDestroyed} for it, so the producer keeps its Surface.
 */
@SuppressLint("ViewConstructor")
public class PipTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private @Nullable SurfaceTexture surfaceTexture;
    private @Nullable SurfaceTextureListener externalListener;
    private @Nullable Runnable firstFrameCallback;
    private long firstFrameAfterTimestamp;

    public PipTextureView(@NonNull Context context) {
        super(context);
        setOpaque(false);
        super.setSurfaceTextureListener(this);
    }

    public void adoptSurfaceTexture(@NonNull SurfaceTexture texture) {
        surfaceTexture = texture;
        if (getSurfaceTexture() != texture) {
            setSurfaceTexture(texture);
        }
    }

    /** Must be called while the view is detached from the window. */
    public @Nullable SurfaceTexture takeSurfaceTexture() {
        final SurfaceTexture texture = surfaceTexture;
        surfaceTexture = null;
        return texture;
    }

    /** Releases the texture after its producer is gone. Must be called while detached. */
    public void releaseSurfaceTexture() {
        final SurfaceTexture texture = takeSurfaceTexture();
        if (texture != null) {
            texture.release();
        }
    }

    public boolean hasSurfaceTexture() {
        return surfaceTexture != null;
    }

    /** Called on the next frame produced after this call (layer re-creation does not count). */
    public void setFirstFrameCallback(@Nullable Runnable callback) {
        firstFrameCallback = callback;
        firstFrameAfterTimestamp = surfaceTexture != null ? surfaceTexture.getTimestamp() : 0;
    }

    @Override
    protected void onAttachedToWindow() {
        if (surfaceTexture != null && getSurfaceTexture() != surfaceTexture) {
            setSurfaceTexture(surfaceTexture);
        }
        super.onAttachedToWindow();
    }

    @Override
    public void setSurfaceTextureListener(@Nullable SurfaceTextureListener listener) {
        externalListener = listener;
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        // created by this view: keep it from now on
        surfaceTexture = surface;
        if (externalListener != null) {
            externalListener.onSurfaceTextureAvailable(surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        if (externalListener != null) {
            externalListener.onSurfaceTextureSizeChanged(surface, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (surface == surfaceTexture) {
            // hardware resources are also destroyed while the window is hidden (activity stopped),
            // without a detach: put the same texture back, otherwise a new one is created on the next draw
            post(() -> {
                if (surfaceTexture == surface && getSurfaceTexture() == null) {
                    setSurfaceTexture(surface);
                }
            });
            return false;
        }
        return externalListener == null || externalListener.onSurfaceTextureDestroyed(surface);
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        if (externalListener != null) {
            externalListener.onSurfaceTextureUpdated(surface);
        }
        // a new GL layer reports an update without a new frame: the timestamp stays the same
        if (firstFrameCallback != null && (firstFrameAfterTimestamp == 0 || surface.getTimestamp() != firstFrameAfterTimestamp)) {
            final Runnable callback = firstFrameCallback;
            firstFrameCallback = null;
            callback.run();
        }
    }
}
