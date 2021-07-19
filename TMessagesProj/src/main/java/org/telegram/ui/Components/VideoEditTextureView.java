package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;

public class VideoEditTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private VideoPlayer currentVideoPlayer;

    private FilterGLThread eglThread;
    private Rect viewRect = new Rect();
    private int videoWidth;
    private int videoHeight;

    private VideoEditTextureViewDelegate delegate;

    public interface VideoEditTextureViewDelegate {
        void onEGLThreadAvailable(FilterGLThread eglThread);
    }

    public VideoEditTextureView(Context context, VideoPlayer videoPlayer) {
        super(context);

        currentVideoPlayer = videoPlayer;
        setSurfaceTextureListener(this);
    }

    public void setDelegate(VideoEditTextureViewDelegate videoEditTextureViewDelegate) {
        delegate = videoEditTextureViewDelegate;
        if (eglThread != null) {
            if (delegate == null) {
                eglThread.setFilterGLThreadDelegate(null);
            } else {
                delegate.onEGLThreadAvailable(eglThread);
            }
        }
    }

    public void setVideoSize(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        if (eglThread == null) {
            return;
        }
        eglThread.setVideoSize(videoWidth, videoHeight);
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (eglThread == null && surface != null && currentVideoPlayer != null) {
            eglThread = new FilterGLThread(surface, surfaceTexture -> {
                if (currentVideoPlayer == null) {
                    return;
                }
                Surface s = new Surface(surfaceTexture);
                currentVideoPlayer.setSurface(s);
            });
            if (videoWidth != 0 && videoHeight != 0) {
                eglThread.setVideoSize(videoWidth, videoHeight);
            }
            eglThread.setSurfaceTextureSize(width, height);
            eglThread.requestRender(true, true, false);
            if (delegate != null) {
                delegate.onEGLThreadAvailable(eglThread);
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
        if (eglThread != null) {
            eglThread.setSurfaceTextureSize(width, height);
            eglThread.requestRender(false, true, false);
            eglThread.postRunnable(() -> {
                if (eglThread != null) {
                    eglThread.requestRender(false, true, false);
                }
            });
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (eglThread != null) {
            eglThread.shutdown();
            eglThread = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    public void release() {
        if (eglThread != null) {
            eglThread.shutdown();
        }
        currentVideoPlayer = null;
    }

    public void setViewRect(float x, float y, float w, float h) {
        viewRect.x = x;
        viewRect.y = y;
        viewRect.width = w;
        viewRect.height = h;
    }

    public boolean containsPoint(float x, float y) {
        return x >= viewRect.x && x <= viewRect.x + viewRect.width && y >= viewRect.y && y <= viewRect.y + viewRect.height;
    }
}
