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
package org.telegram.messenger.exoplayer2.drm;

import android.annotation.TargetApi;
import android.media.MediaDrm;
import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/**
 * A DRM session.
 */
@TargetApi(16)
public interface DrmSession<T extends ExoMediaCrypto> {

  /** Wraps the exception which is the cause of the error state. */
  class DrmSessionException extends Exception {

    public DrmSessionException(Exception e) {
      super(e);
    }

  }

  /**
   * The state of the DRM session.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_ERROR, STATE_CLOSED, STATE_OPENING, STATE_OPENED, STATE_OPENED_WITH_KEYS})
  @interface State {}
  /**
   * The session has encountered an error. {@link #getError()} can be used to retrieve the cause.
   */
  int STATE_ERROR = 0;
  /**
   * The session is closed.
   */
  int STATE_CLOSED = 1;
  /**
   * The session is being opened.
   */
  int STATE_OPENING = 2;
  /**
   * The session is open, but does not yet have the keys required for decryption.
   */
  int STATE_OPENED = 3;
  /**
   * The session is open and has the keys required for decryption.
   */
  int STATE_OPENED_WITH_KEYS = 4;

  /**
   * Returns the current state of the session.
   *
   * @return One of {@link #STATE_ERROR}, {@link #STATE_CLOSED}, {@link #STATE_OPENING},
   *     {@link #STATE_OPENED} and {@link #STATE_OPENED_WITH_KEYS}.
   */
  @State int getState();

  /**
   * Returns a {@link ExoMediaCrypto} for the open session.
   * <p>
   * This method may be called when the session is in the following states:
   * {@link #STATE_OPENED}, {@link #STATE_OPENED_WITH_KEYS}
   *
   * @return A {@link ExoMediaCrypto} for the open session.
   * @throws IllegalStateException If called when a session isn't opened.
   */
  T getMediaCrypto();

  /**
   * Whether the session requires a secure decoder for the specified mime type.
   * <p>
   * Normally this method should return
   * {@link ExoMediaCrypto#requiresSecureDecoderComponent(String)}, however in some cases
   * implementations may wish to modify the return value (i.e. to force a secure decoder even when
   * one is not required).
   * <p>
   * This method may be called when the session is in the following states:
   * {@link #STATE_OPENED}, {@link #STATE_OPENED_WITH_KEYS}
   *
   * @return Whether the open session requires a secure decoder for the specified mime type.
   * @throws IllegalStateException If called when a session isn't opened.
   */
  boolean requiresSecureDecoderComponent(String mimeType);

  /**
   * Returns the cause of the error state.
   * <p>
   * This method may be called when the session is in any state.
   *
   * @return An exception if the state is {@link #STATE_ERROR}. Null otherwise.
   */
  DrmSessionException getError();

  /**
   * Returns an informative description of the key status for the session. The status is in the form
   * of {name, value} pairs.
   *
   * <p>Since DRM license policies vary by vendor, the specific status field names are determined by
   * each DRM vendor. Refer to your DRM provider documentation for definitions of the field names
   * for a particular DRM engine plugin.
   *
   * @return A map of key status.
   * @throws IllegalStateException If called when the session isn't opened.
   * @see MediaDrm#queryKeyStatus(byte[])
   */
  Map<String, String> queryKeyStatus();

  /**
   * Returns the key set id of the offline license loaded into this session, if there is one. Null
   * otherwise.
   */
  byte[] getOfflineLicenseKeySetId();

}
