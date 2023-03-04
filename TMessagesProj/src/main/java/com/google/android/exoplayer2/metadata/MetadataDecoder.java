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
package com.google.android.exoplayer2.metadata;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;

/** Decodes metadata from binary data. */
public interface MetadataDecoder {

  /**
   * Decodes a {@link Metadata} element from the provided input buffer.
   *
   * <p>Respects {@link ByteBuffer#limit()} of {@code inputBuffer.data}, but assumes {@link
   * ByteBuffer#position()} and {@link ByteBuffer#arrayOffset()} are both zero and {@link
   * ByteBuffer#hasArray()} is true.
   *
   * @param inputBuffer The input buffer to decode.
   * @return The decoded metadata object, or {@code null} if the metadata could not be decoded or if
   *     {@link MetadataInputBuffer#isDecodeOnly()} was set on the input buffer.
   */
  @Nullable
  Metadata decode(MetadataInputBuffer inputBuffer);
}
