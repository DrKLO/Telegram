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

  /**
   * Wraps the throwable which is the cause of the error state.
   */
  class DrmSessionException extends Exception {

    public DrmSessionException(Throwable cause) {
      super(cause);
    }

  }

  /**
   * The state of the DRM session.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_RELEASED, STATE_ERROR, STATE_OPENING, STATE_OPENED, STATE_OPENED_WITH_KEYS})
  public @interface State {}
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
  /**
   * The session is open, but does not yet have the keys required for decryption.
   */
  int STATE_OPENED = 3;
  /**
   * The session is open and has the keys required for decryption.
   */
  int STATE_OPENED_WITH_KEYS = 4;

  /**
   * Returns the current state of the session, which is one of {@link #STATE_ERROR},
   * {@link #STATE_RELEASED}, {@link #STATE_OPENING}, {@link #STATE_OPENED} and
   * {@link #STATE_OPENED_WITH_KEYS}.
   */
  @State int getState();

  /**
   * Returns the cause of the error state.
   */
  DrmSessionException getError();

  /**
   * Returns a {@link ExoMediaCrypto} for the open session, or null if called before the session has
   * been opened or after it's been released.
   */
  T getMediaCrypto();

  /**
   * Returns a map describing the key status for the session, or null if called before the session
   * has been opened or after it's been released.
   * <p>
   * Since DRM license policies vary by vendor, the specific status field names are determined by
   * each DRM vendor. Refer to your DRM provider documentation for definitions of the field names
   * for a particular DRM engine plugin.
   *
   * @return A map describing the key status for the session, or null if called before the session
   *     has been opened or after it's been released.
   * @see MediaDrm#queryKeyStatus(byte[])
   */
  Map<String, String> queryKeyStatus();

  /**
   * Returns the key set id of the offline license loaded into this session, or null if there isn't
   * one.
   */
  byte[] getOfflineLicenseKeySetId();

}
