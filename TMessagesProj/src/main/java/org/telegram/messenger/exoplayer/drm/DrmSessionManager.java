/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.drm;

import android.annotation.TargetApi;
import android.media.MediaCrypto;

/**
 * Manages a DRM session.
 */
@TargetApi(16)
public interface DrmSessionManager<T extends ExoMediaCrypto> {

  /**
   * The error state. {@link #getError()} can be used to retrieve the cause.
   */
  public static final int STATE_ERROR = 0;
  /**
   * The session is closed.
   */
  public static final int STATE_CLOSED = 1;
  /**
   * The session is being opened (i.e. {@link #open(DrmInitData)} has been called, but the session
   * is not yet open).
   */
  public static final int STATE_OPENING = 2;
  /**
   * The session is open, but does not yet have the keys required for decryption.
   */
  public static final int STATE_OPENED = 3;
  /**
   * The session is open and has the keys required for decryption.
   */
  public static final int STATE_OPENED_WITH_KEYS = 4;

  /**
   * Opens the session, possibly asynchronously.
   *
   * @param drmInitData DRM initialization data.
   */
  void open(DrmInitData drmInitData);

  /**
   * Closes the session.
   */
  void close();

  /**
   * Gets the current state of the session.
   *
   * @return One of {@link #STATE_ERROR}, {@link #STATE_CLOSED}, {@link #STATE_OPENING},
   *     {@link #STATE_OPENED} and {@link #STATE_OPENED_WITH_KEYS}.
   */
  int getState();

  /**
   * Gets an {@link ExoMediaCrypto} for the open session.
   * <p>
   * This method may be called when the manager is in the following states:
   * {@link #STATE_OPENED}, {@link #STATE_OPENED_WITH_KEYS}
   *
   * @return An {@link ExoMediaCrypto} for the open session.
   * @throws IllegalStateException If called when a session isn't opened.
   */
  T getMediaCrypto();

  /**
   * Whether the session requires a secure decoder for the specified mime type.
   * <p>
   * Normally this method should return {@link MediaCrypto#requiresSecureDecoderComponent(String)},
   * however in some cases implementations  may wish to modify the return value (i.e. to force a
   * secure decoder even when one is not required).
   * <p>
   * This method may be called when the manager is in the following states:
   * {@link #STATE_OPENED}, {@link #STATE_OPENED_WITH_KEYS}
   *
   * @return Whether the open session requires a secure decoder for the specified mime type.
   * @throws IllegalStateException If called when a session isn't opened.
   */
  boolean requiresSecureDecoderComponent(String mimeType);

  /**
   * Gets the cause of the error state.
   * <p>
   * This method may be called when the manager is in any state.
   *
   * @return An exception if the state is {@link #STATE_ERROR}. Null otherwise.
   */
  Exception getError();

}
