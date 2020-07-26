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

import android.media.MediaCrypto;
import com.google.android.exoplayer2.util.Util;
import java.util.UUID;

/**
 * An {@link ExoMediaCrypto} implementation that contains the necessary information to build or
 * update a framework {@link MediaCrypto}.
 */
public final class FrameworkMediaCrypto implements ExoMediaCrypto {

  /**
   * Whether the device needs keys to have been loaded into the {@link DrmSession} before codec
   * configuration.
   */
  public static final boolean WORKAROUND_DEVICE_NEEDS_KEYS_TO_CONFIGURE_CODEC =
      "Amazon".equals(Util.MANUFACTURER)
          && ("AFTM".equals(Util.MODEL) // Fire TV Stick Gen 1
              || "AFTB".equals(Util.MODEL)); // Fire TV Gen 1

  /** The DRM scheme UUID. */
  public final UUID uuid;
  /** The DRM session id. */
  public final byte[] sessionId;
  /**
   * Whether to allow use of insecure decoder components even if the underlying platform says
   * otherwise.
   */
  public final boolean forceAllowInsecureDecoderComponents;

  /**
   * @param uuid The DRM scheme UUID.
   * @param sessionId The DRM session id.
   * @param forceAllowInsecureDecoderComponents Whether to allow use of insecure decoder components
   *     even if the underlying platform says otherwise.
   */
  public FrameworkMediaCrypto(
      UUID uuid, byte[] sessionId, boolean forceAllowInsecureDecoderComponents) {
    this.uuid = uuid;
    this.sessionId = sessionId;
    this.forceAllowInsecureDecoderComponents = forceAllowInsecureDecoderComponents;
  }
}
