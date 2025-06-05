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
package com.google.android.exoplayer2.decoder;

/** Output buffer decoded by a {@link Decoder}. */
public abstract class DecoderOutputBuffer extends Buffer {

  /** Buffer owner. */
  public interface Owner<S extends DecoderOutputBuffer> {

    /**
     * Releases the buffer.
     *
     * @param outputBuffer Output buffer.
     */
    void releaseOutputBuffer(S outputBuffer);
  }

  /** The presentation timestamp for the buffer, in microseconds. */
  public long timeUs;

  /**
   * The number of buffers immediately prior to this one that were skipped in the {@link Decoder}.
   */
  public int skippedOutputBufferCount;

  /** Releases the output buffer for reuse. Must be called when the buffer is no longer needed. */
  public abstract void release();
}
