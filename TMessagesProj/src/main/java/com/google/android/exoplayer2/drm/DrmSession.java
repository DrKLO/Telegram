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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaDrm;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.UUID;

/** A DRM session. */
public interface DrmSession {

  /**
   * Acquires {@code newSession} then releases {@code previousSession}.
   *
   * <p>Invokes {@code newSession's} {@link #acquire(DrmSessionEventListener.EventDispatcher)} and
   * {@code previousSession's} {@link #release(DrmSessionEventListener.EventDispatcher)} in that
   * order (passing {@code eventDispatcher = null}). Null arguments are ignored. Does nothing if
   * {@code previousSession} and {@code newSession} are the same session.
   */
  static void replaceSession(
      @Nullable DrmSession previousSession, @Nullable DrmSession newSession) {
    if (previousSession == newSession) {
      // Do nothing.
      return;
    }
    if (newSession != null) {
      newSession.acquire(/* eventDispatcher= */ null);
    }
    if (previousSession != null) {
      previousSession.release(/* eventDispatcher= */ null);
    }
  }

  /** Wraps the throwable which is the cause of the error state. */
  class DrmSessionException extends IOException {

    /** The {@link PlaybackException.ErrorCode} that corresponds to the failure. */
    public final @PlaybackException.ErrorCode int errorCode;

    public DrmSessionException(Throwable cause, @PlaybackException.ErrorCode int errorCode) {
      super(cause);
      this.errorCode = errorCode;
    }
  }

  /**
   * The state of the DRM session. One of {@link #STATE_RELEASED}, {@link #STATE_ERROR}, {@link
   * #STATE_OPENING}, {@link #STATE_OPENED} or {@link #STATE_OPENED_WITH_KEYS}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({STATE_RELEASED, STATE_ERROR, STATE_OPENING, STATE_OPENED, STATE_OPENED_WITH_KEYS})
  @interface State {}
  /** The session has been released. This is a terminal state. */
  int STATE_RELEASED = 0;
  /**
   * The session has encountered an error. {@link #getError()} can be used to retrieve the cause.
   * This is a terminal state.
   */
  int STATE_ERROR = 1;
  /** The session is being opened. */
  int STATE_OPENING = 2;
  /** The session is open, but does not have keys required for decryption. */
  int STATE_OPENED = 3;
  /** The session is open and has keys required for decryption. */
  int STATE_OPENED_WITH_KEYS = 4;

  /**
   * Returns the current state of the session, which is one of {@link #STATE_ERROR}, {@link
   * #STATE_RELEASED}, {@link #STATE_OPENING}, {@link #STATE_OPENED} and {@link
   * #STATE_OPENED_WITH_KEYS}.
   */
  @State
  int getState();

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

  /** Returns the DRM scheme UUID for this session. */
  UUID getSchemeUuid();

  /**
   * Returns a {@link CryptoConfig} for the open session, or null if called before the session has
   * been opened or after it's been released.
   */
  @Nullable
  CryptoConfig getCryptoConfig();

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
   * Returns whether this session requires use of a secure decoder for the given MIME type. Assumes
   * a license policy that requires the highest level of security supported by the session.
   *
   * <p>The session must be in {@link #getState() state} {@link #STATE_OPENED} or {@link
   * #STATE_OPENED_WITH_KEYS}.
   */
  boolean requiresSecureDecoder(String mimeType);

  /**
   * Increments the reference count. When the caller no longer needs to use the instance, it must
   * call {@link #release(DrmSessionEventListener.EventDispatcher)} to decrement the reference
   * count.
   *
   * @param eventDispatcher The {@link DrmSessionEventListener.EventDispatcher} used to route
   *     DRM-related events dispatched from this session, or null if no event handling is needed.
   */
  void acquire(@Nullable DrmSessionEventListener.EventDispatcher eventDispatcher);

  /**
   * Decrements the reference count. If the reference count drops to 0 underlying resources are
   * released, and the instance cannot be re-used.
   *
   * @param eventDispatcher The {@link DrmSessionEventListener.EventDispatcher} to disconnect when
   *     the session is released (the same instance (possibly null) that was passed by the caller to
   *     {@link #acquire(DrmSessionEventListener.EventDispatcher)}).
   */
  void release(@Nullable DrmSessionEventListener.EventDispatcher eventDispatcher);
}
