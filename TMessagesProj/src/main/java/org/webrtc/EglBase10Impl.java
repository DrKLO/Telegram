/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.Nullable;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Holds EGL state and utility methods for handling an egl 1.0 EGLContext, an EGLDisplay,
 * and an EGLSurface.
 */
class EglBase10Impl implements EglBase10 {
    private static final String TAG = "EglBase10Impl";
    // This constant is taken from EGL14.EGL_CONTEXT_CLIENT_VERSION.
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private final EGL10 egl;
    private EGLContext eglContext;
    @Nullable
    private EGLConfig eglConfig;
    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface = EGL10.EGL_NO_SURFACE;
    private EGLSurface eglBackgroundSurface = EGL10.EGL_NO_SURFACE;

    // EGL wrapper for an actual EGLContext.
    private static class Context implements EglBase10.Context {
        private final EGL10 egl;
        private final EGLContext eglContext;
        private final EGLConfig eglContextConfig;

        @Override
        public EGLContext getRawContext() {
            return eglContext;
        }

        @Override
        public long getNativeEglContext() {
            EGLContext previousContext = egl.eglGetCurrentContext();
            EGLDisplay currentDisplay = egl.eglGetCurrentDisplay();
            EGLSurface previousDrawSurface = egl.eglGetCurrentSurface(EGL10.EGL_DRAW);
            EGLSurface previousReadSurface = egl.eglGetCurrentSurface(EGL10.EGL_READ);
            EGLSurface tempEglSurface = null;

            if (currentDisplay == EGL10.EGL_NO_DISPLAY) {
                currentDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            }

            try {
                if (previousContext != eglContext) {
                    int[] surfaceAttribs = {EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE};
                    tempEglSurface =
                            egl.eglCreatePbufferSurface(currentDisplay, eglContextConfig, surfaceAttribs);
                    if (!egl.eglMakeCurrent(currentDisplay, tempEglSurface, tempEglSurface, eglContext)) {
                        throw new RuntimeException(
                                "Failed to make temporary EGL surface active: " + egl.eglGetError());
                    }
                }

                return nativeGetCurrentNativeEGLContext();
            } finally {
                if (tempEglSurface != null) {
                    egl.eglMakeCurrent(
                            currentDisplay, previousDrawSurface, previousReadSurface, previousContext);
                    egl.eglDestroySurface(currentDisplay, tempEglSurface);
                }
            }
        }

        public Context(EGL10 egl, EGLContext eglContext, EGLConfig eglContextConfig) {
            this.egl = egl;
            this.eglContext = eglContext;
            this.eglContextConfig = eglContextConfig;
        }
    }

    // Create a new context with the specified config type, sharing data with sharedContext.
    public EglBase10Impl(EGLContext sharedContext, int[] configAttributes) {
        this.egl = (EGL10) EGLContext.getEGL();
        eglDisplay = getEglDisplay();
        eglConfig = getEglConfig(egl, eglDisplay, configAttributes);
        final int openGlesVersion = EglBase.getOpenGlesVersionFromConfig(configAttributes);
        Logging.d(TAG, "Using OpenGL ES version " + openGlesVersion);
        eglContext = createEglContext(sharedContext, eglDisplay, eglConfig, openGlesVersion);
    }

    @Override
    public void createSurface(Surface surface) {
        createSurfaceInternal(new FakeSurfaceHolder(surface), false);
    }

    // Create EGLSurface from the Android SurfaceTexture.
    @Override
    public void createSurface(SurfaceTexture surfaceTexture) {
        createSurfaceInternal(surfaceTexture, false);
    }

    // Create EGLSurface from either a SurfaceHolder or a SurfaceTexture.
    private void createSurfaceInternal(Object nativeWindow, boolean background) {
        if (!(nativeWindow instanceof SurfaceHolder) && !(nativeWindow instanceof SurfaceTexture)) {
            throw new IllegalStateException("Input must be either a SurfaceHolder or SurfaceTexture");
        }
        checkIsNotReleased();
        if (background) {
            if (eglBackgroundSurface != EGL10.EGL_NO_SURFACE) {
                throw new RuntimeException("Already has an EGLSurface");
            }
            int[] surfaceAttribs = {EGL10.EGL_NONE};
            eglBackgroundSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, nativeWindow, surfaceAttribs);
            if (eglBackgroundSurface == EGL10.EGL_NO_SURFACE) {
                throw new RuntimeException(
                        "Failed to create window surface: 0x" + Integer.toHexString(egl.eglGetError()));
            }
        } else {
            if (eglSurface != EGL10.EGL_NO_SURFACE) {
                throw new RuntimeException("Already has an EGLSurface");
            }
            int[] surfaceAttribs = {EGL10.EGL_NONE};
            eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, nativeWindow, surfaceAttribs);
            if (eglSurface == EGL10.EGL_NO_SURFACE) {
                throw new RuntimeException(
                        "Failed to create window surface: 0x" + Integer.toHexString(egl.eglGetError()));
            }
        }
    }

    // Create dummy 1x1 pixel buffer surface so the context can be made current.
    @Override
    public void createDummyPbufferSurface() {
        createPbufferSurface(1, 1);
    }

    @Override
    public void createPbufferSurface(int width, int height) {
        checkIsNotReleased();
        if (eglSurface != EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("Already has an EGLSurface");
        }
        int[] surfaceAttribs = {EGL10.EGL_WIDTH, width, EGL10.EGL_HEIGHT, height, EGL10.EGL_NONE};
        eglSurface = egl.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs);
        if (eglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("Failed to create pixel buffer surface with size " + width + "x"
                    + height + ": 0x" + Integer.toHexString(egl.eglGetError()));
        }
    }

    @Override
    public org.webrtc.EglBase.Context getEglBaseContext() {
        return new Context(egl, eglContext, eglConfig);
    }

    @Override
    public boolean hasSurface() {
        return eglSurface != EGL10.EGL_NO_SURFACE;
    }

    @Override
    public int surfaceWidth() {
        final int widthArray[] = new int[1];
        egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_WIDTH, widthArray);
        return widthArray[0];
    }

    @Override
    public int surfaceHeight() {
        final int heightArray[] = new int[1];
        egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_HEIGHT, heightArray);
        return heightArray[0];
    }

    @Override
    public void releaseSurface(boolean background) {
        if (background) {
            if (eglBackgroundSurface != EGL10.EGL_NO_SURFACE) {
                egl.eglDestroySurface(eglDisplay, eglBackgroundSurface);
                eglBackgroundSurface = EGL10.EGL_NO_SURFACE;
            }
        } else {
            if (eglSurface != EGL10.EGL_NO_SURFACE) {
                egl.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = EGL10.EGL_NO_SURFACE;
            }
        }
    }

    private void checkIsNotReleased() {
        if (eglDisplay == EGL10.EGL_NO_DISPLAY || eglContext == EGL10.EGL_NO_CONTEXT
                || eglConfig == null) {
            throw new RuntimeException("This object has been released");
        }
    }

    @Override
    public void release() {
        checkIsNotReleased();
        releaseSurface(false);
        releaseSurface(true);
        detachCurrent();
        egl.eglDestroyContext(eglDisplay, eglContext);
        egl.eglTerminate(eglDisplay);
        eglContext = EGL10.EGL_NO_CONTEXT;
        eglDisplay = EGL10.EGL_NO_DISPLAY;
        eglConfig = null;
    }

    @Override
    public void makeCurrent() {
        checkIsNotReleased();
        if (eglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("No EGLSurface - can't make current");
        }
        synchronized (EglBase.lock) {
            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new RuntimeException(
                        "eglMakeCurrent failed: 0x" + Integer.toHexString(egl.eglGetError()));
            }
        }
    }

    // Detach the current EGL context, so that it can be made current on another thread.
    @Override
    public void detachCurrent() {
        synchronized (EglBase.lock) {
            if (!egl.eglMakeCurrent(
                    eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)) {
                throw new RuntimeException(
                        "eglDetachCurrent failed: 0x" + Integer.toHexString(egl.eglGetError()));
            }
        }
    }

    @Override
    public void swapBuffers(boolean background) {
        EGLSurface surface = background ? eglBackgroundSurface : eglSurface;
        checkIsNotReleased();
        if (surface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("No EGLSurface - can't swap buffers");
        }
        synchronized (EglBase.lock) {
            egl.eglSwapBuffers(eglDisplay, surface);
        }
    }

    @Override
    public void swapBuffers(long timeStampNs, boolean background) {
        // Setting presentation time is not supported for EGL 1.0.
        swapBuffers(background);
    }

    @Override
    public void createBackgroundSurface(SurfaceTexture surface) {
        createSurfaceInternal(surface, true);
    }

    @Override
    public void makeBackgroundCurrent() {
        checkIsNotReleased();
        if (eglBackgroundSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("No EGLSurface - can't make current");
        }
        synchronized (EglBase.lock) {
            if (!egl.eglMakeCurrent(eglDisplay, eglBackgroundSurface, eglBackgroundSurface, eglContext)) {
                throw new RuntimeException(
                        "eglMakeCurrent failed: 0x" + Integer.toHexString(egl.eglGetError()));
            }
        }
    }

    @Override
    public boolean hasBackgroundSurface() {
        return eglBackgroundSurface != EGL10.EGL_NO_SURFACE;
    }

    // Return an EGLDisplay, or die trying.
    private EGLDisplay getEglDisplay() {
        EGLDisplay eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException(
                    "Unable to get EGL10 display: 0x" + Integer.toHexString(egl.eglGetError()));
        }
        int[] version = new int[2];
        if (!egl.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException(
                    "Unable to initialize EGL10: 0x" + Integer.toHexString(egl.eglGetError()));
        }
        return eglDisplay;
    }

    // Return an EGLConfig, or die trying.
    private static EGLConfig getEglConfig(EGL10 egl, EGLDisplay eglDisplay, int[] configAttributes) {
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!egl.eglChooseConfig(eglDisplay, configAttributes, configs, configs.length, numConfigs)) {
            throw new RuntimeException(
                    "eglChooseConfig failed: 0x" + Integer.toHexString(egl.eglGetError()));
        }
        if (numConfigs[0] <= 0) {
            throw new RuntimeException("Unable to find any matching EGL config");
        }
        final EGLConfig eglConfig = configs[0];
        if (eglConfig == null) {
            throw new RuntimeException("eglChooseConfig returned null");
        }
        return eglConfig;
    }

    // Return an EGLConfig, or die trying.
    private EGLContext createEglContext(@Nullable EGLContext sharedContext, EGLDisplay eglDisplay,
                                        EGLConfig eglConfig, int openGlesVersion) {
        if (sharedContext != null && sharedContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("Invalid sharedContext");
        }
        int[] contextAttributes = {EGL_CONTEXT_CLIENT_VERSION, openGlesVersion, EGL10.EGL_NONE};
        EGLContext rootContext = sharedContext == null ? EGL10.EGL_NO_CONTEXT : sharedContext;
        final EGLContext eglContext;
        synchronized (EglBase.lock) {
            eglContext = egl.eglCreateContext(eglDisplay, eglConfig, rootContext, contextAttributes);
        }
        if (eglContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException(
                    "Failed to create EGL context: 0x" + Integer.toHexString(egl.eglGetError()));
        }
        return eglContext;
    }


    /**
     * We have to wrap Surface in a SurfaceHolder because for some reason eglCreateWindowSurface
     * couldn't actually take a Surface object until API 17. Older versions fortunately just call
     * SurfaceHolder.getSurface(), so we'll do that. No other methods are relevant.
     */
    private class FakeSurfaceHolder implements SurfaceHolder {
        private final Surface surface;

        FakeSurfaceHolder(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void addCallback(Callback callback) {
        }

        @Override
        public void removeCallback(Callback callback) {
        }

        @Override
        public boolean isCreating() {
            return false;
        }

        @Deprecated
        @Override
        public void setType(int i) {
        }

        @Override
        public void setFixedSize(int i, int i2) {
        }

        @Override
        public void setSizeFromLayout() {
        }

        @Override
        public void setFormat(int i) {
        }

        @Override
        public void setKeepScreenOn(boolean b) {
        }

        @Nullable
        @Override
        public Canvas lockCanvas() {
            return null;
        }

        @Nullable
        @Override
        public Canvas lockCanvas(Rect rect) {
            return null;
        }

        @Override
        public void unlockCanvasAndPost(Canvas canvas) {
        }

        @Nullable
        @Override
        public Rect getSurfaceFrame() {
            return null;
        }

        @Override
        public Surface getSurface() {
            return surface;
        }
    }

    private static native long nativeGetCurrentNativeEGLContext();
}
