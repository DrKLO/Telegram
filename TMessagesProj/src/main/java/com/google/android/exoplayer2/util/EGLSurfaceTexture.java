/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Generates a {@link SurfaceTexture} using EGL/GLES functions. */
@RequiresApi(17)
public final class EGLSurfaceTexture implements SurfaceTexture.OnFrameAvailableListener, Runnable {

  /** Listener to be called when the texture image on {@link SurfaceTexture} has been updated. */
  public interface TextureImageListener {
    /** Called when the {@link SurfaceTexture} receives a new frame from its image producer. */
    void onFrameAvailable();
  }

  /**
   * Secure mode to be used by the EGL surface and context. One of {@link #SECURE_MODE_NONE}, {@link
   * #SECURE_MODE_SURFACELESS_CONTEXT} or {@link #SECURE_MODE_PROTECTED_PBUFFER}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({SECURE_MODE_NONE, SECURE_MODE_SURFACELESS_CONTEXT, SECURE_MODE_PROTECTED_PBUFFER})
  public @interface SecureMode {}

  /** No secure EGL surface and context required. */
  public static final int SECURE_MODE_NONE = 0;
  /** Creating a surfaceless, secured EGL context. */
  public static final int SECURE_MODE_SURFACELESS_CONTEXT = 1;
  /** Creating a secure surface backed by a pixel buffer. */
  public static final int SECURE_MODE_PROTECTED_PBUFFER = 2;

  private static final int EGL_SURFACE_WIDTH = 1;
  private static final int EGL_SURFACE_HEIGHT = 1;

  private static final int[] EGL_CONFIG_ATTRIBUTES =
      new int[] {
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_DEPTH_SIZE, 0,
        EGL14.EGL_CONFIG_CAVEAT, EGL14.EGL_NONE,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
        EGL14.EGL_NONE
      };

  private static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;

  private final Handler handler;
  private final int[] textureIdHolder;
  @Nullable private final TextureImageListener callback;

  @Nullable private EGLDisplay display;
  @Nullable private EGLContext context;
  @Nullable private EGLSurface surface;
  @Nullable private SurfaceTexture texture;

  /**
   * @param handler The {@link Handler} that will be used to call {@link
   *     SurfaceTexture#updateTexImage()} to update images on the {@link SurfaceTexture}. Note that
   *     {@link #init(int, EGLContext)} has to be called on the same looper thread as the {@link Handler}'s
   *     looper.
   */
  public EGLSurfaceTexture(Handler handler) {
    this(handler, /* callback= */ null);
  }

  /**
   * @param handler The {@link Handler} that will be used to call {@link
   *     SurfaceTexture#updateTexImage()} to update images on the {@link SurfaceTexture}. Note that
   *     {@link #init(int, EGLContext)} has to be called on the same looper thread as the looper of the {@link
   *     Handler}.
   * @param callback The {@link TextureImageListener} to be called when the texture image on {@link
   *     SurfaceTexture} has been updated. This callback will be called on the same handler thread
   *     as the {@code handler}.
   */
  public EGLSurfaceTexture(Handler handler, @Nullable TextureImageListener callback) {
    this.handler = handler;
    this.callback = callback;
    textureIdHolder = new int[1];
  }

  /**
   * Initializes required EGL parameters and creates the {@link SurfaceTexture}.
   *
   * @param secureMode The {@link SecureMode} to be used for EGL surface.
   */
  public void init(@SecureMode int secureMode, EGLContext parentContext) throws GlUtil.GlException {
    display = getDefaultDisplay();
    EGLConfig config = chooseEGLConfig(display);
    context = createEGLContext(display, config, secureMode, parentContext);
    surface = createEGLSurface(display, config, context, secureMode);
    generateTextureIds(textureIdHolder);
    texture = new SurfaceTexture(textureIdHolder[0]);
    texture.setOnFrameAvailableListener(this);
  }

  /** Releases all allocated resources. */
  @SuppressWarnings("nullness:argument")
  public void release() {
    handler.removeCallbacks(this);
    try {
      if (texture != null) {
        texture.release();
        GLES20.glDeleteTextures(1, textureIdHolder, 0);
      }
    } finally {
      if (display != null && !display.equals(EGL14.EGL_NO_DISPLAY)) {
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      }
      if (surface != null && !surface.equals(EGL14.EGL_NO_SURFACE)) {
        EGL14.eglDestroySurface(display, surface);
      }
      if (context != null) {
        EGL14.eglDestroyContext(display, context);
      }
      // EGL14.eglReleaseThread could crash before Android K (see [internal: b/11327779]).
      if (Util.SDK_INT >= 19) {
        EGL14.eglReleaseThread();
      }
      if (display != null && !display.equals(EGL14.EGL_NO_DISPLAY)) {
        // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
        // every eglInitialize() we need an eglTerminate().
        EGL14.eglTerminate(display);
      }
      display = null;
      context = null;
      surface = null;
      texture = null;
    }
  }

  /**
   * Returns the wrapped {@link SurfaceTexture}. This can only be called after {@link #init(int, EGLContext)}.
   */
  public SurfaceTexture getSurfaceTexture() {
    return Assertions.checkNotNull(texture);
  }

  // SurfaceTexture.OnFrameAvailableListener

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    handler.post(this);
  }

  // Runnable

  @Override
  public void run() {
    // Run on the provided handler thread when a new image frame is available.
    dispatchOnFrameAvailable();
    if (texture != null) {
      try {
        texture.updateTexImage();
      } catch (RuntimeException e) {
        // Ignore
      }
    }
  }

  private void dispatchOnFrameAvailable() {
    if (callback != null) {
      callback.onFrameAvailable();
    }
  }

  private static EGLDisplay getDefaultDisplay() throws GlUtil.GlException {
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    GlUtil.checkGlException(display != null, "eglGetDisplay failed");

    int[] version = new int[2];
    boolean eglInitialized =
        EGL14.eglInitialize(display, version, /* majorOffset= */ 0, version, /* minorOffset= */ 1);
    GlUtil.checkGlException(eglInitialized, "eglInitialize failed");
    return display;
  }

  private static EGLConfig chooseEGLConfig(EGLDisplay display) throws GlUtil.GlException {
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    boolean success =
        EGL14.eglChooseConfig(
            display,
            EGL_CONFIG_ATTRIBUTES,
            /* attrib_listOffset= */ 0,
            configs,
            /* configsOffset= */ 0,
            /* config_size= */ 1,
            numConfigs,
            /* num_configOffset= */ 0);
    GlUtil.checkGlException(
        success && numConfigs[0] > 0 && configs[0] != null,
        Util.formatInvariant(
            /* format= */ "eglChooseConfig failed: success=%b, numConfigs[0]=%d, configs[0]=%s",
            success, numConfigs[0], configs[0]));

    return configs[0];
  }

  private static EGLContext createEGLContext(
      EGLDisplay display, EGLConfig config, @SecureMode int secureMode, EGLContext eglContext) throws GlUtil.GlException {
    int[] glAttributes;
    if (secureMode == SECURE_MODE_NONE) {
      glAttributes = new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
    } else {
      glAttributes =
          new int[] {
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            2,
            EGL_PROTECTED_CONTENT_EXT,
            EGL14.EGL_TRUE,
            EGL14.EGL_NONE
          };
    }
    EGLContext context =
        EGL14.eglCreateContext(
            display, config, eglContext == null ? android.opengl.EGL14.EGL_NO_CONTEXT : eglContext, glAttributes, 0);
    GlUtil.checkGlException(context != null, "eglCreateContext failed");
    return context;
  }

  private static EGLSurface createEGLSurface(
      EGLDisplay display, EGLConfig config, EGLContext context, @SecureMode int secureMode)
      throws GlUtil.GlException {
    EGLSurface surface;
    if (secureMode == SECURE_MODE_SURFACELESS_CONTEXT) {
      surface = EGL14.EGL_NO_SURFACE;
    } else {
      int[] pbufferAttributes;
      if (secureMode == SECURE_MODE_PROTECTED_PBUFFER) {
        pbufferAttributes =
            new int[] {
              EGL14.EGL_WIDTH,
              EGL_SURFACE_WIDTH,
              EGL14.EGL_HEIGHT,
              EGL_SURFACE_HEIGHT,
              EGL_PROTECTED_CONTENT_EXT,
              EGL14.EGL_TRUE,
              EGL14.EGL_NONE
            };
      } else {
        pbufferAttributes =
            new int[] {
              EGL14.EGL_WIDTH,
              EGL_SURFACE_WIDTH,
              EGL14.EGL_HEIGHT,
              EGL_SURFACE_HEIGHT,
              EGL14.EGL_NONE
            };
      }
      surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttributes, /* offset= */ 0);
      GlUtil.checkGlException(surface != null, "eglCreatePbufferSurface failed");
    }

    boolean eglMadeCurrent =
        EGL14.eglMakeCurrent(display, /* draw= */ surface, /* read= */ surface, context);
    GlUtil.checkGlException(eglMadeCurrent, "eglMakeCurrent failed");
    return surface;
  }

  private static void generateTextureIds(int[] textureIdHolder) throws GlUtil.GlException {
    GLES20.glGenTextures(/* n= */ 1, textureIdHolder, /* offset= */ 0);
    GlUtil.checkGlError();
  }
}
