/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.video;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Surface;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;

public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {

    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private EGL10 mEGL;
    private EGLDisplay mEGLDisplay = null;
    private EGLContext mEGLContext = null;
    private EGLSurface mEGLSurface = null;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private final Object mFrameSyncObject = new Object();
    private boolean mFrameAvailable;
    private TextureRenderer mTextureRender;

    private android.opengl.EGLContext parentContext;
    private Handler handler;

    public OutputSurface(android.opengl.EGLContext context, MediaController.SavedFilterState savedFilterState, String imagePath, String paintPath, String blurPath, ArrayList<VideoEditedInfo.MediaEntity> mediaEntities, MediaController.CropState cropState, int w, int h, int originalW, int originalH, int rotation, float fps, boolean photo, Integer gradientTopColor, Integer gradientBottomColor, StoryEntry.HDRInfo hdrInfo, MediaCodecVideoConvertor.ConvertVideoParams params, Handler handler) {
        this.parentContext = context;
        this.handler = handler;
        final CountDownLatch latch = new CountDownLatch(1);
        handler.post(() -> {
            setupBackground(context);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        mTextureRender = new TextureRenderer(savedFilterState, imagePath, paintPath, blurPath, mediaEntities, cropState, w, h, originalW, originalH, rotation, fps, photo, gradientTopColor, gradientBottomColor, hdrInfo, params, handler, bgEGLContext);
        mTextureRender.surfaceCreated();
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mSurface = new Surface(mSurfaceTexture);
    }

    private EGLDisplay bgEGLDisplay;
    private EGLContext bgEGLContext;
    private void setupBackground(EGLContext ctx) {
        bgEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (bgEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(bgEGLDisplay, version, 0, version, 1)) {
            bgEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        int[] attribList = {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(bgEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        bgEGLContext = EGL14.eglCreateContext(bgEGLDisplay, configs[0], ctx, attrib_list, 0);
        checkEglError("eglCreateContext");
        if (bgEGLContext == null) {
            throw new RuntimeException("null context");
        }
        checkEglError("before makeCurrent");
        if (!EGL14.eglMakeCurrent(bgEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, bgEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

//    private void eglSetup(int width, int height) {
//        mEGL = (EGL10) EGLContext.getEGL();
//        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
//
//        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
//            throw new RuntimeException("unable to get EGL10 display");
//        }
//
//        if (!mEGL.eglInitialize(mEGLDisplay, null)) {
//            mEGLDisplay = null;
//            throw new RuntimeException("unable to initialize EGL10");
//        }
//
//        int[] attribList = {
//                EGL10.EGL_RED_SIZE, 8,
//                EGL10.EGL_GREEN_SIZE, 8,
//                EGL10.EGL_BLUE_SIZE, 8,
//                EGL10.EGL_ALPHA_SIZE, 8,
//                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
//                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
//                EGL10.EGL_NONE
//        };
//        EGLConfig[] configs = new EGLConfig[1];
//        int[] numConfigs = new int[1];
//        if (!mEGL.eglChooseConfig(mEGLDisplay, attribList, configs, configs.length, numConfigs)) {
//            throw new RuntimeException("unable to find RGB888+pbuffer EGL config");
//        }
//        int[] attrib_list = {
//                EGL_CONTEXT_CLIENT_VERSION, 2,
//                EGL10.EGL_NONE
//        };
//        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT, attrib_list);
//        checkEglError("eglCreateContext");
//        if (mEGLContext == null) {
//            throw new RuntimeException("null context");
//        }
//        int[] surfaceAttribs = {
//                EGL10.EGL_WIDTH, width,
//                EGL10.EGL_HEIGHT, height,
//                EGL10.EGL_NONE
//        };
//        mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs);
//        checkEglError("eglCreatePbufferSurface");
//        if (mEGLSurface == null) {
//            throw new RuntimeException("surface was null");
//        }
//    }

    public void release() {
//        if (mEGL != null) {
//        if (EGL14.eglGetCurrentContext().equals(mEGLContext)) {
//            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
//        }
//        if (mEGLSurface != null) {
//            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
//        }
//        if (mEGLContext != null) {
//            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
//        }
//        }
        if (mTextureRender != null) {
            mTextureRender.release();
        }
        mSurface.release();
        mEGLDisplay = null;
        mEGLContext = null;
        mEGLSurface = null;
        mEGL = null;
        mTextureRender = null;
        mSurface = null;
        mSurfaceTexture = null;
    }

//    public void makeCurrent() {
//        if (mEGL == null) {
//            throw new RuntimeException("not configured for makeCurrent");
//        }
//        checkEglError("before makeCurrent");
//        if (!mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
//            throw new RuntimeException("eglMakeCurrent failed");
//        }
//    }

    public Surface getSurface() {
        return mSurface;
    }

    public void awaitNewImage() {
        final int TIMEOUT_MS = 2500;
        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        throw new RuntimeException("Surface frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }
        mSurfaceTexture.updateTexImage();
    }

    public void drawImage(long time) {
        mTextureRender.drawFrame(mSurfaceTexture, time);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        synchronized (mFrameSyncObject) {
            if (mFrameAvailable) {
                throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
            }
            mFrameAvailable = true;
            mFrameSyncObject.notifyAll();
        }
    }

    private void checkEglError(String msg) {
        if (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("EGL error encountered (see log) at: " + msg);
        }
    }

    public void changeFragmentShader(String fragmentExternalShader, String fragmentShader, boolean is300) {
        mTextureRender.changeFragmentShader(fragmentExternalShader, fragmentShader, is300);
    }

    public boolean supportsEXTYUV() {
        try {
            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            return extensions.contains("GL_EXT_YUV_target");
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}
