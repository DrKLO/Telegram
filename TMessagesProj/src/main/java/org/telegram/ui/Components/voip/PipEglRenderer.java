package org.telegram.ui.Components.voip;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.utils.pip.PipTextureView;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Renders frames into the texture of a {@link PipTextureView} (the floating window's, which
 * then moves into system PiP and back). The texture lives as long as the view keeps it.
 */
public final class PipEglRenderer implements VideoSink {
    private final EglRenderer renderer = new EglRenderer("pip-egl");
    private @Nullable SurfaceTexture surface;
    private boolean released;

    public PipEglRenderer(@NonNull PipTextureView target) {
        renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), EglBase.CONFIG_PLAIN, new GlRectDrawer());
        target.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
                attach(texture, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
                setLayout(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {}
        });
        final SurfaceTexture existing = target.getSurfaceTexture();
        if (existing != null && target.hasSurfaceTexture()) {
            attach(existing, target.getWidth(), target.getHeight());
        }
    }

    private void attach(@NonNull SurfaceTexture texture, int width, int height) {
        if (released || surface == texture) {
            return;
        }
        surface = texture;
        setLayout(width, height);
        renderer.createEglSurface(texture);
    }

    private void setLayout(int width, int height) {
        if (width > 0 && height > 0) {
            // the window has the stream's aspect: fill it, cropping a rounding difference
            renderer.setLayoutAspectRatio(width / (float) height);
        }
    }

    @Override
    public void onFrame(VideoFrame frame) {
        if (!released) {
            renderer.onFrame(frame);
        }
    }

    /** Stops drawing before the texture is released by its owner. */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        renderer.release();
        surface = null;
    }
}
