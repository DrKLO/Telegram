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

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;

/** Holds input for a decoder. */
public class DecoderInputBuffer extends Buffer {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.decoder");
  }

  /**
   * Thrown when an attempt is made to write into a {@link DecoderInputBuffer} whose {@link
   * #bufferReplacementMode} is {@link #BUFFER_REPLACEMENT_MODE_DISABLED} and who {@link #data}
   * capacity is smaller than required.
   */
  public static final class InsufficientCapacityException extends IllegalStateException {

    /** The current capacity of the buffer. */
    public final int currentCapacity;
    /** The required capacity of the buffer. */
    public final int requiredCapacity;

    /**
     * Creates an instance.
     *
     * @param currentCapacity The current capacity of the buffer.
     * @param requiredCapacity The required capacity of the buffer.
     */
    public InsufficientCapacityException(int currentCapacity, int requiredCapacity) {
      super("Buffer too small (" + currentCapacity + " < " + requiredCapacity + ")");
      this.currentCapacity = currentCapacity;
      this.requiredCapacity = requiredCapacity;
    }
  }

  /**
   * The buffer replacement mode. This controls how {@link #ensureSpaceForWrite} generates
   * replacement buffers when the capacity of the existing buffer is insufficient. One of {@link
   * #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} or {@link
   * #BUFFER_REPLACEMENT_MODE_DIRECT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    BUFFER_REPLACEMENT_MODE_DISABLED,
    BUFFER_REPLACEMENT_MODE_NORMAL,
    BUFFER_REPLACEMENT_MODE_DIRECT
  })
  public @interface BufferReplacementMode {}
  /** Disallows buffer replacement. */
  public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;
  /** Allows buffer replacement using {@link ByteBuffer#allocate(int)}. */
  public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;
  /** Allows buffer replacement using {@link ByteBuffer#allocateDirect(int)}. */
  public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

  /** The {@link Format}. */
  @Nullable public Format format;

  /** {@link CryptoInfo} for encrypted data. */
  public final CryptoInfo cryptoInfo;

  /** The buffer's data, or {@code null} if no data has been set. */
  @Nullable public ByteBuffer data;

  // TODO: Remove this temporary signaling once end-of-stream propagation for clips using content
  // protection is fixed. See [Internal: b/153326944] for details.
  /**
   * Whether the last attempt to read a sample into this buffer failed due to not yet having the DRM
   * keys associated with the next sample.
   */
  public boolean waitingForKeys;

  /** The time at which the sample should be presented. */
  public long timeUs;

  /**
   * Supplemental data related to the buffer, if {@link #hasSupplementalData()} returns true. If
   * present, the buffer is populated with supplemental data from position 0 to its limit.
   */
  @Nullable public ByteBuffer supplementalData;

  private final @BufferReplacementMode int bufferReplacementMode;
  private final int paddingSize;

  /** Returns a new instance that's not able to hold any data. */
  public static DecoderInputBuffer newNoDataInstance() {
    return new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  /**
   * Creates a new instance.
   *
   * @param bufferReplacementMode The {@link BufferReplacementMode} replacement mode.
   */
  public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode) {
    this(bufferReplacementMode, /* paddingSize= */ 0);
  }

  /**
   * Creates a new instance.
   *
   * @param bufferReplacementMode The {@link BufferReplacementMode} replacement mode.
   * @param paddingSize If non-zero, {@link #ensureSpaceForWrite(int)} will ensure that the buffer
   *     is this number of bytes larger than the requested length. This can be useful for decoders
   *     that consume data in fixed size blocks, for efficiency. Setting the padding size to the
   *     decoder's fixed read size is necessary to prevent such a decoder from trying to read beyond
   *     the end of the buffer.
   */
  public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode, int paddingSize) {
    this.cryptoInfo = new CryptoInfo();
    this.bufferReplacementMode = bufferReplacementMode;
    this.paddingSize = paddingSize;
  }

  /**
   * Clears {@link #supplementalData} and ensures that it's large enough to accommodate {@code
   * length} bytes.
   *
   * @param length The length of the supplemental data that must be accommodated, in bytes.
   */
  @EnsuresNonNull("supplementalData")
  public void resetSupplementalData(int length) {
    if (supplementalData == null || supplementalData.capacity() < length) {
      supplementalData = ByteBuffer.allocate(length);
    } else {
      supplementalData.clear();
    }
  }

  /**
   * Ensures that {@link #data} is large enough to accommodate a write of a given length at its
   * current position.
   *
   * <p>If the capacity of {@link #data} is sufficient this method does nothing. If the capacity is
   * insufficient then an attempt is made to replace {@link #data} with a new {@link ByteBuffer}
   * whose capacity is sufficient. Data up to the current position is copied to the new buffer.
   *
   * @param length The length of the write that must be accommodated, in bytes.
   * @throws InsufficientCapacityException If there is insufficient capacity to accommodate the
   *     write and {@link #bufferReplacementMode} is {@link #BUFFER_REPLACEMENT_MODE_DISABLED}.
   */
  @EnsuresNonNull("data")
  public void ensureSpaceForWrite(int length) {
    length += paddingSize;
    @Nullable ByteBuffer currentData = data;
    if (currentData == null) {
      data = createReplacementByteBuffer(length);
      return;
    }
    // Check whether the current buffer is sufficient.
    int capacity = currentData.capacity();
    int position = currentData.position();
    int requiredCapacity = position + length;
    if (capacity >= requiredCapacity) {
      data = currentData;
      return;
    }
    // Instantiate a new buffer if possible.
    ByteBuffer newData = createReplacementByteBuffer(requiredCapacity);
    newData.order(currentData.order());
    // Copy data up to the current position from the old buffer to the new one.
    if (position > 0) {
      currentData.flip();
      newData.put(currentData);
    }
    // Set the new buffer.
    data = newData;
  }

  /** Returns whether the {@link C#BUFFER_FLAG_ENCRYPTED} flag is set. */
  public final boolean isEncrypted() {
    return getFlag(C.BUFFER_FLAG_ENCRYPTED);
  }

  /**
   * Flips {@link #data} and {@link #supplementalData} in preparation for being queued to a decoder.
   *
   * @see java.nio.Buffer#flip()
   */
  public final void flip() {
    if (data != null) {
      data.flip();
    }
    if (supplementalData != null) {
      supplementalData.flip();
    }
  }

  @Override
  public void clear() {
    super.clear();
    if (data != null) {
      data.clear();
    }
    if (supplementalData != null) {
      supplementalData.clear();
    }
    waitingForKeys = false;
  }

  private ByteBuffer createReplacementByteBuffer(int requiredCapacity) {
    if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_NORMAL) {
      return ByteBuffer.allocate(requiredCapacity);
    } else if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_DIRECT) {
      return ByteBuffer.allocateDirect(requiredCapacity);
    } else {
      int currentCapacity = data == null ? 0 : data.capacity();
      throw new InsufficientCapacityException(currentCapacity, requiredCapacity);
    }
  }
}
