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
package org.telegram.messenger.exoplayer;

import java.nio.ByteBuffer;

/**
 * Holds sample data and corresponding metadata.
 */
public final class SampleHolder {

  /**
   * Disallows buffer replacement.
   */
  public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;

  /**
   * Allows buffer replacement using {@link ByteBuffer#allocate(int)}.
   */
  public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;

  /**
   * Allows buffer replacement using {@link ByteBuffer#allocateDirect(int)}.
   */
  public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

  public final CryptoInfo cryptoInfo;

  /**
   * A buffer holding the sample data.
   */
  public ByteBuffer data;

  /**
   * The size of the sample in bytes.
   */
  public int size;

  /**
   * Flags that accompany the sample. A combination of {@link C#SAMPLE_FLAG_SYNC},
   * {@link C#SAMPLE_FLAG_ENCRYPTED} and {@link C#SAMPLE_FLAG_DECODE_ONLY}.
   */
  public int flags;

  /**
   * The time at which the sample should be presented.
   */
  public long timeUs;

  private final int bufferReplacementMode;

  /**
   * @param bufferReplacementMode Determines the behavior of {@link #ensureSpaceForWrite(int)}. One
   *     of {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
   *     {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
   */
  public SampleHolder(int bufferReplacementMode) {
    this.cryptoInfo = new CryptoInfo();
    this.bufferReplacementMode = bufferReplacementMode;
  }

  /**
   * Ensures that {@link #data} is large enough to accommodate a write of a given length at its
   * current position.
   * <p>
   * If the capacity of {@link #data} is sufficient this method does nothing. If the capacity is
   * insufficient then an attempt is made to replace {@link #data} with a new {@link ByteBuffer}
   * whose capacity is sufficient. Data up to the current position is copied to the new buffer.
   *
   * @param length The length of the write that must be accommodated, in bytes.
   * @throws IllegalStateException If there is insufficient capacity to accommodate the write and
   *     the buffer replacement mode of the holder is {@link #BUFFER_REPLACEMENT_MODE_DISABLED}.
   */
  public void ensureSpaceForWrite(int length) throws IllegalStateException {
    if (data == null) {
      data = createReplacementBuffer(length);
      return;
    }
    // Check whether the current buffer is sufficient.
    int capacity = data.capacity();
    int position = data.position();
    int requiredCapacity = position + length;
    if (capacity >= requiredCapacity) {
      return;
    }
    // Instantiate a new buffer if possible.
    ByteBuffer newData = createReplacementBuffer(requiredCapacity);
    // Copy data up to the current position from the old buffer to the new one.
    if (position > 0) {
      data.position(0);
      data.limit(position);
      newData.put(data);
    }
    // Set the new buffer.
    data = newData;
  }

  /**
   * Returns whether {@link #flags} has {@link C#SAMPLE_FLAG_ENCRYPTED} set.
   */
  public boolean isEncrypted() {
    return (flags & C.SAMPLE_FLAG_ENCRYPTED) != 0;
  }

  /**
   * Returns whether {@link #flags} has {@link C#SAMPLE_FLAG_DECODE_ONLY} set.
   */
  public boolean isDecodeOnly() {
    return (flags & C.SAMPLE_FLAG_DECODE_ONLY) != 0;
  }

  /**
   * Returns whether {@link #flags} has {@link C#SAMPLE_FLAG_SYNC} set.
   */
  public boolean isSyncFrame() {
    return (flags & C.SAMPLE_FLAG_SYNC) != 0;
  }

  /**
   * Clears {@link #data}. Does nothing if {@link #data} is null.
   */
  public void clearData() {
    if (data != null) {
      data.clear();
    }
  }

  private ByteBuffer createReplacementBuffer(int requiredCapacity) {
    if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_NORMAL) {
      return ByteBuffer.allocate(requiredCapacity);
    } else if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_DIRECT) {
      return ByteBuffer.allocateDirect(requiredCapacity);
    } else {
      int currentCapacity = data == null ? 0 : data.capacity();
      throw new IllegalStateException("Buffer too small (" + currentCapacity + " < "
          + requiredCapacity + ")");
    }
  }

}
