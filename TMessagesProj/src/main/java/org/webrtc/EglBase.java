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

import android.graphics.SurfaceTexture;
import androidx.annotation.Nullable;
import android.view.Surface;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGL10;

/**
 * Holds EGL state and utility methods for handling an egl 1.0 EGLContext, an EGLDisplay,
 * and an EGLSurface.
 */
public interface EglBase {
  // EGL wrapper for an actual EGLContext.
  public interface Context {
    public final static long NO_CONTEXT = 0;

    /**
     * Returns an EGL context that can be used by native code. Returns NO_CONTEXT if the method is
     * unsupported.
     *
     * @note This is currently only supported for EGL 1.4 and not for EGL 1.0.
     */
    long getNativeEglContext();
  }

  // According to the documentation, EGL can be used from multiple threads at the same time if each
  // thread has its own EGLContext, but in practice it deadlocks on some devices when doing this.
  // Therefore, synchronize on this global lock before calling dangerous EGL functions that might
  // deadlock. See https://bugs.chromium.org/p/webrtc/issues/detail?id=5702 for more info.
  public static final Object lock = new Object();

  // These constants are taken from EGL14.EGL_OPENGL_ES2_BIT and EGL14.EGL_CONTEXT_CLIENT_VERSION.
  // https://android.googlesource.com/platform/frameworks/base/+/master/opengl/java/android/opengl/EGL14.java
  // This is similar to how GlSurfaceView does:
  // http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.1.1_r1/android/opengl/GLSurfaceView.java#760
  public static final int EGL_OPENGL_ES2_BIT = 4;
  public static final int EGL_OPENGL_ES3_BIT = 0x40;
  // Android-specific extension.
  public static final int EGL_RECORDABLE_ANDROID = 0x3142;

  public static ConfigBuilder configBuilder() {
    return new ConfigBuilder();
  }

  public static class ConfigBuilder {
    private int openGlesVersion = 2;
    private boolean hasAlphaChannel;
    private boolean supportsPixelBuffer;
    private boolean isRecordable;

    public ConfigBuilder setOpenGlesVersion(int version) {
      if (version < 1 || version > 3) {
        throw new IllegalArgumentException("OpenGL ES version " + version + " not supported");
      }
      this.openGlesVersion = version;
      return this;
    }

    public ConfigBuilder setHasAlphaChannel(boolean hasAlphaChannel) {
      this.hasAlphaChannel = hasAlphaChannel;
      return this;
    }

    public ConfigBuilder setSupportsPixelBuffer(boolean supportsPixelBuffer) {
      this.supportsPixelBuffer = supportsPixelBuffer;
      return this;
    }

    public ConfigBuilder setIsRecordable(boolean isRecordable) {
      this.isRecordable = isRecordable;
      return this;
    }

    public int[] createConfigAttributes() {
      ArrayList<Integer> list = new ArrayList<>();
      list.add(EGL10.EGL_RED_SIZE);
      list.add(8);
      list.add(EGL10.EGL_GREEN_SIZE);
      list.add(8);
      list.add(EGL10.EGL_BLUE_SIZE);
      list.add(8);
      if (hasAlphaChannel) {
        list.add(EGL10.EGL_ALPHA_SIZE);
        list.add(8);
      }
      if (openGlesVersion == 2 || openGlesVersion == 3) {
        list.add(EGL10.EGL_RENDERABLE_TYPE);
        list.add(openGlesVersion == 3 ? EGL_OPENGL_ES3_BIT : EGL_OPENGL_ES2_BIT);
      }
      if (supportsPixelBuffer) {
        list.add(EGL10.EGL_SURFACE_TYPE);
        list.add(EGL10.EGL_PBUFFER_BIT);
      }
      if (isRecordable) {
        list.add(EGL_RECORDABLE_ANDROID);
        list.add(1);
      }
      list.add(EGL10.EGL_NONE);

      final int[] res = new int[list.size()];
      for (int i = 0; i < list.size(); ++i) {
        res[i] = list.get(i);
      }
      return res;
    }
  }

  public static final int[] CONFIG_PLAIN = configBuilder().createConfigAttributes();
  public static final int[] CONFIG_RGBA =
      configBuilder().setHasAlphaChannel(true).createConfigAttributes();
  public static final int[] CONFIG_PIXEL_BUFFER =
      configBuilder().setSupportsPixelBuffer(true).createConfigAttributes();
  public static final int[] CONFIG_PIXEL_RGBA_BUFFER = configBuilder()
                                                           .setHasAlphaChannel(true)
                                                           .setSupportsPixelBuffer(true)
                                                           .createConfigAttributes();
  public static final int[] CONFIG_RECORDABLE =
      configBuilder().setIsRecordable(true).createConfigAttributes();

  static int getOpenGlesVersionFromConfig(int[] configAttributes) {
    for (int i = 0; i < configAttributes.length - 1; ++i) {
      if (configAttributes[i] == EGL10.EGL_RENDERABLE_TYPE) {
        switch (configAttributes[i + 1]) {
          case EGL_OPENGL_ES2_BIT:
            return 2;
          case EGL_OPENGL_ES3_BIT:
            return 3;
          default:
            return 1;
        }
      }
    }
    // Default to V1 if no renderable type is specified.
    return 1;
  }

  /**
   * Create a new context with the specified config attributes, sharing data with |sharedContext|.
   * If |sharedContext| is null, a root context is created. This function will try to create an EGL
   * 1.4 context if possible, and an EGL 1.0 context otherwise.
   */
  public static EglBase create(@Nullable Context sharedContext, int[] configAttributes) {
    if (sharedContext == null) {
      return EglBase14Impl.isEGL14Supported() ? createEgl14(configAttributes)
                                              : createEgl10(configAttributes);
    } else if (sharedContext instanceof EglBase14.Context) {
      return createEgl14((EglBase14.Context) sharedContext, configAttributes);
    } else if (sharedContext instanceof EglBase10.Context) {
      return createEgl10((EglBase10.Context) sharedContext, configAttributes);
    }
    throw new IllegalArgumentException("Unrecognized Context");
  }

  /**
   * Helper function for creating a plain root context. This function will try to create an EGL 1.4
   * context if possible, and an EGL 1.0 context otherwise.
   */
  public static EglBase create() {
    return create(null /* shaderContext */, CONFIG_PLAIN);
  }

  /**
   * Helper function for creating a plain context, sharing data with |sharedContext|. This function
   * will try to create an EGL 1.4 context if possible, and an EGL 1.0 context otherwise.
   */
  public static EglBase create(Context sharedContext) {
    return create(sharedContext, CONFIG_PLAIN);
  }

  /** Explicitly create a root EGl 1.0 context with the specified config attributes. */
  public static EglBase10 createEgl10(int[] configAttributes) {
    return new EglBase10Impl(/* sharedContext= */ null, configAttributes);
  }

  /**
   * Explicitly create a root EGl 1.0 context with the specified config attributes and shared
   * context.
   */
  public static EglBase10 createEgl10(EglBase10.Context sharedContext, int[] configAttributes) {
    return new EglBase10Impl(
        sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
  }

  /**
   * Explicitly create a root EGl 1.0 context with the specified config attributes
   * and shared context.
   */
  public static EglBase10 createEgl10(
      javax.microedition.khronos.egl.EGLContext sharedContext, int[] configAttributes) {
    return new EglBase10Impl(sharedContext, configAttributes);
  }

  /** Explicitly create a root EGl 1.4 context with the specified config attributes. */
  public static EglBase14 createEgl14(int[] configAttributes) {
    return new EglBase14Impl(/* sharedContext= */ null, configAttributes);
  }

  /**
   * Explicitly create a root EGl 1.4 context with the specified config attributes and shared
   * context.
   */
  public static EglBase14 createEgl14(EglBase14.Context sharedContext, int[] configAttributes) {
    return new EglBase14Impl(
        sharedContext == null ? null : sharedContext.getRawContext(), configAttributes);
  }

  /**
   * Explicitly create a root EGl 1.4 context with the specified config attributes
   * and shared context.
   */
  public static EglBase14 createEgl14(
      android.opengl.EGLContext sharedContext, int[] configAttributes) {
    return new EglBase14Impl(sharedContext, configAttributes);
  }

  void createSurface(Surface surface);

  // Create EGLSurface from the Android SurfaceTexture.
  void createSurface(SurfaceTexture surfaceTexture);

  // Create dummy 1x1 pixel buffer surface so the context can be made current.
  void createDummyPbufferSurface();

  void createPbufferSurface(int width, int height);

  Context getEglBaseContext();

  boolean hasSurface();

  int surfaceWidth();

  int surfaceHeight();

  void releaseSurface();

  void release();

  void makeCurrent();

  // Detach the current EGL context, so that it can be made current on another thread.
  void detachCurrent();

  void swapBuffers();

  void swapBuffers(long presentationTimeStampNs);
}
