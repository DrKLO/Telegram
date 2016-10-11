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
package org.telegram.messenger.exoplayer.extractor.mp4;

/**
 * Encapsulates information parsed from a track encryption (tenc) box or sample group description 
 * (sgpd) box in an MP4 stream.
 */
public final class TrackEncryptionBox {

  /**
   * Indicates the encryption state of the samples in the sample group.
   */
  public final boolean isEncrypted;

  /**
   * The initialization vector size in bytes for the samples in the corresponding sample group.
   */
  public final int initializationVectorSize;

  /**
   * The key identifier for the samples in the corresponding sample group.
   */
  public final byte[] keyId;

  /**
   * @param isEncrypted Indicates the encryption state of the samples in the sample group.
   * @param initializationVectorSize The initialization vector size in bytes for the samples in the
   *     corresponding sample group.
   * @param keyId The key identifier for the samples in the corresponding sample group.
   */
  public TrackEncryptionBox(boolean isEncrypted, int initializationVectorSize, byte[] keyId) {
    this.isEncrypted = isEncrypted;
    this.initializationVectorSize = initializationVectorSize;
    this.keyId = keyId;
  }

}
