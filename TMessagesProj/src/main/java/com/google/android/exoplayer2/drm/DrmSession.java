/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.drm;

import android.media.MediaDrm;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/**
 * A DRM session.
 */
public interface DrmSession<T extends ExoMediaCrypto> {

  /**
   * Invokes {@code newSession's} {@link #acquire()} and {@code previousSession's} {@link
   * #release()} in that order. Null arguments are ignored. Does nothing if {@code previousSession}
   * and {@code newSession} are the same session.
   */
  static <T extends ExoMediaCrypto> void replaceSession(
      @Nullable DrmSession<T> previousSession, @Nullable DrmSession<T> newSession) {
    if (previousSession == newSession) {
      // Do nothing.
      return;
    }
    if (newSession != null) {
      newSession.acquire();
    }
    if (previousSession != null) {
      previousSession.release();
    }
  }

  /** Wraps the throwable which is the cause of the error state. */
  class DrmSessionException extends IOException {

    public DrmSessionException(Throwable cause) {
      super(cause);
    }

  }

  /**
   * The state of the DRM session. One of {@link #STATE_RELEASED}, {@link #STATE_ERROR}, {@link
   * #STATE_OPENING}, {@link #STATE_OPENED} or {@link #STATE_OPENED_WITH_KEYS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_RELEASED, STATE_ERROR, STATE_OPENING, STATE_OPENED, STATE_OPENED_WITH_KEYS})
  @interface State {}
  /**
   * The session has been released.
   */
  int STATE_RELEASED = 0;
  /**
   * The session has encountered an error. {@link #getError()} can be used to retrieve the cause.
   */
  int STATE_ERROR = 1;
  /**
   * The session is being opened.
   */
  int STATE_OPENING = 2;
  /** The session is open, but does not have keys required for decryption. */
  int STATE_OPENED = 3;
  /** The session is open and has keys required for decryption. */
  int STATE_OPENED_WITH_KEYS = 4;

  /**
   * Returns the current state of the session, which is one of {@link #STATE_ERROR},
   * {@link #STATE_RELEASED}, {@link #STATE_OPENING}, {@link #STATE_OPENED} and
   * {@link #STATE_OPENED_WITH_KEYS}.
   */
  @State int getState();

  /** Returns whether this session allows playback of clear samples prior to keys being loaded. */
  default boolean playClearSamplesWithoutKeys() {
    return false;
  }

  /**
   * Returns the cause of the error state, or null if {@link #getState()} is not {@link
   * #STATE_ERROR}.
   */
  @Nullable
  DrmSessionException getError();

  /**
   * Returns a {@link ExoMediaCrypto} for the open session, or null if called before the session has
   * been opened or after it's been released.
   */
  @Nullable
  T getMediaCrypto();

  /**
   * Returns a map describing the key status for the session, or null if called before the session
   * has been opened or after it's been released.
   *
   * <p>Since DRM license policies vary by vendor, the specific status field names are determined by
   * each DRM vendor. Refer to your DRM provider documentation for definitions of the field names
   * for a particular DRM engine plugin.
   *
   * @return A map describing the key status for the session, or null if called before the session
   *     has been opened or after it's been released.
   * @see MediaDrm#queryKeyStatus(byte[])
   */
  @Nullable
  Map<String, String> queryKeyStatus();

  /**
   * Returns the key set id of the offline license loaded into this session, or null if there isn't
   * one.
   */
  @Nullable
  byte[] getOfflineLicenseKeySetId();

  /**
   * Increments the reference count. When the caller no longer needs to use the instance, it must
   * call {@link #release()} to decrement the reference count.
   */
  void acquire();

  /**
   * Decrements the reference count. If the reference count drops to 0 underlying resources are
   * released, and the instance cannot be re-used.
   */
  void release();
}
