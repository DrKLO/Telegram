/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer2.video;

import static android.opengl.EGL14.EGL_ALPHA_SIZE;
import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONFIG_CAVEAT;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_DEPTH_SIZE;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.EGL_RENDERABLE_TYPE;
import static android.opengl.EGL14.EGL_SURFACE_TYPE;
import static android.opengl.EGL14.EGL_TRUE;
import static android.opengl.EGL14.EGL_WIDTH;
import static android.opengl.EGL14.EGL_WINDOW_BIT;
import static android.opengl.EGL14.eglChooseConfig;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglCreatePbufferSurface;
import static android.opengl.EGL14.eglDestroyContext;
import static android.opengl.EGL14.eglDestroySurface;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glGenTextures;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import javax.microedition.khronos.egl.EGL10;

/**
 * A dummy {@link Surface}.
 */
@TargetApi(17)
public final class DummySurface extends Surface {

  private static final String TAG = "DummySurface";

  private static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;

  private static boolean secureSupported;
  private static boolean secureSupportedInitialized;

  /**
   * Whether the surface is secure.
   */
  public final boolean secure;

  private final DummySurfaceThread thread;
  private boolean threadReleased;

  /**
   * Returns whether the device supports secure dummy surfaces.
   *
   * @param context Any {@link Context}.
   * @return Whether the device supports secure dummy surfaces.
   */
  public static synchronized boolean isSecureSupported(Context context) {
    if (!secureSupportedInitialized) {
      secureSupported = Util.SDK_INT >= 24 && enableSecureDummySurfaceV24(context);
      secureSupportedInitialized = true;
    }
    return secureSupported;
  }

  /**
   * Returns a newly created dummy surface. The surface must be released by calling {@link #release}
   * when it's no longer required.
   * <p>
   * Must only be called if {@link Util#SDK_INT} is 17 or higher.
   *
   * @param context Any {@link Context}.
   * @param secure Whether a secure surface is required. Must only be requested if
   *     {@link #isSecureSupported(Context)} returns {@code true}.
   * @throws IllegalStateException If a secure surface is requested on a device for which
   *     {@link #isSecureSupported(Context)} returns {@code false}.
   */
  public static DummySurface newInstanceV17(Context context, boolean secure) {
    assertApiLevel17OrHigher();
    Assertions.checkState(!secure || isSecureSupported(context));
    DummySurfaceThread thread = new DummySurfaceThread();
    return thread.init(secure);
  }

  private DummySurface(DummySurfaceThread thread, SurfaceTexture surfaceTexture, boolean secure) {
    super(surfaceTexture);
    this.thread = thread;
    this.secure = secure;
  }

  @Override
  public void release() {
    super.release();
    // The Surface may be released multiple times (explicitly and by Surface.finalize()). The
    // implementation of super.release() has its own deduplication logic. Below we need to
    // deduplicate ourselves. Synchronization is required as we don't control the thread on which
    // Surface.finalize() is called.
    synchronized (thread) {
      if (!threadReleased) {
        thread.release();
        threadReleased = true;
      }
    }
  }

  private static void assertApiLevel17OrHigher() {
    if (Util.SDK_INT < 17) {
      throw new UnsupportedOperationException("Unsupported prior to API level 17");
    }
  }

  /**
   * Returns whether use of secure dummy surfaces should be enabled.
   *
   * @param context Any {@link Context}.
   */
  @TargetApi(24)
  private static boolean enableSecureDummySurfaceV24(Context context) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    if (eglExtensions == null || !eglExtensions.contains("EGL_EXT_protected_content")) {
      // EGL_EXT_protected_content is required to enable secure dummy surfaces.
      return false;
    }
    if (Util.SDK_INT == 24 && "samsung".equals(Util.MANUFACTURER)) {
      // Samsung devices running API level 24 are known to be broken [Internal: b/37197802].
      return false;
    }
    if (Util.SDK_INT < 26 && !context.getPackageManager().hasSystemFeature(
        PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
      // Pre API level 26 devices were not well tested unless they supported VR mode.
      return false;
    }
    return true;
  }

  private static class DummySurfaceThread extends HandlerThread implements OnFrameAvailableListener,
      Callback {

    private static final int MSG_INIT = 1;
    private static final int MSG_UPDATE_TEXTURE = 2;
    private static final int MSG_RELEASE = 3;

    private final int[] textureIdHolder;
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface pbuffer;
    private Handler handler;
    private SurfaceTexture surfaceTexture;

    private Error initError;
    private RuntimeException initException;
    private DummySurface surface;

    public DummySurfaceThread() {
      super("dummySurface");
      textureIdHolder = new int[1];
    }

    public DummySurface init(boolean secure) {
      start();
      handler = new Handler(getLooper(), this);
      boolean wasInterrupted = false;
      synchronized (this) {
        handler.obtainMessage(MSG_INIT, secure ? 1 : 0, 0).sendToTarget();
        while (surface == null && initException == null && initError == null) {
          try {
            wait();
          } catch (InterruptedException e) {
            wasInterrupted = true;
          }
        }
      }
      if (wasInterrupted) {
        // Restore the interrupted status.
        Thread.currentThread().interrupt();
      }
      if (initException != null) {
        throw initException;
      } else if (initError != null) {
        throw initError;
      } else {
        return surface;
      }
    }

    public void release() {
      handler.sendEmptyMessage(MSG_RELEASE);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
      handler.sendEmptyMessage(MSG_UPDATE_TEXTURE);
    }

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_INIT:
          try {
            initInternal(msg.arg1 != 0);
          } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize dummy surface", e);
            initException = e;
          } catch (Error e) {
            Log.e(TAG, "Failed to initialize dummy surface", e);
            initError = e;
          } finally {
            synchronized (this) {
              notify();
            }
          }
          return true;
        case MSG_UPDATE_TEXTURE:
          surfaceTexture.updateTexImage();
          return true;
        case MSG_RELEASE:
          try {
            releaseInternal();
          } catch (Throwable e) {
            Log.e(TAG, "Failed to release dummy surface", e);
          } finally {
            quit();
          }
          return true;
        default:
          return true;
      }
    }

    private void initInternal(boolean secure) {
      display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
      Assertions.checkState(display != null, "eglGetDisplay failed");

      int[] version = new int[2];
      boolean eglInitialized = eglInitialize(display, version, 0, version, 1);
      Assertions.checkState(eglInitialized, "eglInitialize failed");

      int[] eglAttributes = new int[] {
          EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
          EGL_RED_SIZE, 8,
          EGL_GREEN_SIZE, 8,
          EGL_BLUE_SIZE, 8,
          EGL_ALPHA_SIZE, 8,
          EGL_DEPTH_SIZE, 0,
          EGL_CONFIG_CAVEAT, EGL_NONE,
          EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
          EGL_NONE
      };
      EGLConfig[] configs = new EGLConfig[1];
      int[] numConfigs = new int[1];
      boolean eglChooseConfigSuccess = eglChooseConfig(display, eglAttributes, 0, configs, 0, 1,
          numConfigs, 0);
      Assertions.checkState(eglChooseConfigSuccess && numConfigs[0] > 0 && configs[0] != null,
          "eglChooseConfig failed");

      EGLConfig config = configs[0];
      int[] glAttributes;
      if (secure) {
        glAttributes = new int[] {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_PROTECTED_CONTENT_EXT, EGL_TRUE,
            EGL_NONE};
      } else {
        glAttributes = new int[] {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE};
      }
      context = eglCreateContext(display, config, android.opengl.EGL14.EGL_NO_CONTEXT, glAttributes,
          0);
      Assertions.checkState(context != null, "eglCreateContext failed");

      int[] pbufferAttributes;
      if (secure) {
        pbufferAttributes = new int[] {
            EGL_WIDTH, 1,
            EGL_HEIGHT, 1,
            EGL_PROTECTED_CONTENT_EXT, EGL_TRUE,
            EGL_NONE};
      } else {
        pbufferAttributes = new int[] {
            EGL_WIDTH, 1,
            EGL_HEIGHT, 1,
            EGL_NONE};
      }
      pbuffer = eglCreatePbufferSurface(display, config, pbufferAttributes, 0);
      Assertions.checkState(pbuffer != null, "eglCreatePbufferSurface failed");

      boolean eglMadeCurrent = eglMakeCurrent(display, pbuffer, pbuffer, context);
      Assertions.checkState(eglMadeCurrent, "eglMakeCurrent failed");

      glGenTextures(1, textureIdHolder, 0);
      surfaceTexture = new SurfaceTexture(textureIdHolder[0]);
      surfaceTexture.setOnFrameAvailableListener(this);
      surface = new DummySurface(this, surfaceTexture, secure);
    }

    private void releaseInternal() {
      try {
        if (surfaceTexture != null) {
          surfaceTexture.release();
          glDeleteTextures(1, textureIdHolder, 0);
        }
      } finally {
        if (pbuffer != null) {
          eglDestroySurface(display, pbuffer);
        }
        if (context != null) {
          eglDestroyContext(display, context);
        }
        pbuffer = null;
        context = null;
        display = null;
        surface = null;
        surfaceTexture = null;
      }
    }

  }

}
