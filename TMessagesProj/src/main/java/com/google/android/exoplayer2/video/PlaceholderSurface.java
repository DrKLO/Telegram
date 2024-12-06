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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.util.EGLSurfaceTexture.SECURE_MODE_NONE;
import static com.google.android.exoplayer2.util.EGLSurfaceTexture.SECURE_MODE_PROTECTED_PBUFFER;
import static com.google.android.exoplayer2.util.EGLSurfaceTexture.SECURE_MODE_SURFACELESS_CONTEXT;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.EGLSurfaceTexture;
import com.google.android.exoplayer2.util.EGLSurfaceTexture.SecureMode;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A placeholder {@link Surface}. */
@RequiresApi(17)
public final class PlaceholderSurface extends Surface {

  private static final String TAG = "PlaceholderSurface";

  /** Whether the surface is secure. */
  public final boolean secure;
  public final EGLContext parentContext;

  private static @SecureMode int secureMode;
  private static boolean secureModeInitialized;

  private final PlaceholderSurfaceThread thread;
  private boolean threadReleased;

  /**
   * Returns whether the device supports secure placeholder surfaces.
   *
   * @param context Any {@link Context}.
   * @return Whether the device supports secure placeholder surfaces.
   */
  public static synchronized boolean isSecureSupported(Context context) {
    if (!secureModeInitialized) {
      secureMode = getSecureMode(context);
      secureModeInitialized = true;
    }
    return secureMode != SECURE_MODE_NONE;
  }

  /**
   * Returns a newly created placeholder surface. The surface must be released by calling {@link
   * #release} when it's no longer required.
   *
   * <p>Must only be called if {@link Util#SDK_INT} is 17 or higher.
   *
   * @param context Any {@link Context}.
   * @param secure Whether a secure surface is required. Must only be requested if {@link
   *     #isSecureSupported(Context)} returns {@code true}.
   * @throws IllegalStateException If a secure surface is requested on a device for which {@link
   *     #isSecureSupported(Context)} returns {@code false}.
   */
  public static PlaceholderSurface newInstanceV17(Context context, boolean secure, EGLContext parentContext) {
    Assertions.checkState(!secure || isSecureSupported(context));
    PlaceholderSurfaceThread thread = new PlaceholderSurfaceThread();
    return thread.init(secure ? secureMode : SECURE_MODE_NONE, parentContext);
  }

  private PlaceholderSurface(
      PlaceholderSurfaceThread thread, SurfaceTexture surfaceTexture, boolean secure, EGLContext parentContext) {
    super(surfaceTexture);
    this.thread = thread;
    this.secure = secure;
    this.parentContext = parentContext;
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

  private static @SecureMode int getSecureMode(Context context) {
    if (GlUtil.isProtectedContentExtensionSupported(context)) {
      if (GlUtil.isSurfacelessContextExtensionSupported()) {
        return SECURE_MODE_SURFACELESS_CONTEXT;
      } else {
        // If we can't use surfaceless contexts, we use a protected 1 * 1 pixel buffer surface.
        // This may require support for EXT_protected_surface, but in practice it works on some
        // devices that don't have that extension. See also
        // https://github.com/google/ExoPlayer/issues/3558.
        return SECURE_MODE_PROTECTED_PBUFFER;
      }
    } else {
      return SECURE_MODE_NONE;
    }
  }

  private static class PlaceholderSurfaceThread extends HandlerThread implements Handler.Callback {

    private static final int MSG_INIT = 1;
    private static final int MSG_RELEASE = 2;

    private @MonotonicNonNull EGLSurfaceTexture eglSurfaceTexture;
    private @MonotonicNonNull Handler handler;
    @Nullable private Error initError;
    @Nullable private RuntimeException initException;
    @Nullable private PlaceholderSurface surface;

    public PlaceholderSurfaceThread() {
      super("ExoPlayer:PlaceholderSurface");
    }

    public PlaceholderSurface init(@SecureMode int secureMode, EGLContext parentContext) {
      start();
      handler = new Handler(getLooper(), /* callback= */ this);
      eglSurfaceTexture = new EGLSurfaceTexture(handler);
      boolean wasInterrupted = false;
      synchronized (this) {
        handler.obtainMessage(MSG_INIT, secureMode, 0, parentContext).sendToTarget();
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
        return Assertions.checkNotNull(surface);
      }
    }

    public void release() {
      Assertions.checkNotNull(handler);
      handler.sendEmptyMessage(MSG_RELEASE);
    }

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_INIT:
          try {
            initInternal(/* secureMode= */ msg.arg1, msg.obj == null ? null : (EGLContext) msg.obj);
          } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize placeholder surface", e);
            initException = e;
          } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to initialize placeholder surface", e);
            initException = new IllegalStateException(e);
          } catch (Error e) {
            Log.e(TAG, "Failed to initialize placeholder surface", e);
            initError = e;
          } finally {
            synchronized (this) {
              notify();
            }
          }
          return true;
        case MSG_RELEASE:
          try {
            releaseInternal();
          } catch (Throwable e) {
            Log.e(TAG, "Failed to release placeholder surface", e);
          } finally {
            quit();
          }
          return true;
        default:
          return true;
      }
    }

    private void initInternal(@SecureMode int secureMode, EGLContext parentContext) throws GlUtil.GlException {
      Assertions.checkNotNull(eglSurfaceTexture);
      eglSurfaceTexture.init(secureMode, parentContext);
      this.surface =
          new PlaceholderSurface(
              this, eglSurfaceTexture.getSurfaceTexture(), secureMode != SECURE_MODE_NONE, parentContext);
    }

    private void releaseInternal() {
      Assertions.checkNotNull(eglSurfaceTexture);
      eglSurfaceTexture.release();
    }
  }
}
