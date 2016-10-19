/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor.ogg;

import org.telegram.messenger.exoplayer.util.Assertions;

/**
 * Wraps a byte array, providing methods that allow it to be read as a vorbis bitstream.
 *
 * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-360002">Vorbis bitpacking
 *     specification</a>
 */
/* package */ final class VorbisBitArray {

  public final byte[] data;
  private int limit;
  private int byteOffset;
  private int bitOffset;

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data the array to wrap.
   */
  public VorbisBitArray(byte[] data) {
    this(data, data.length);
  }

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data the array to wrap.
   * @param limit the limit in bytes.
   */
  public VorbisBitArray(byte[] data, int limit) {
    this.data = data;
    this.limit = limit * 8;
  }

  /** Resets the reading position to zero. */
  public void reset() {
    byteOffset = 0;
    bitOffset = 0;
  }

  /**
   * Reads a single bit.
   *
   * @return {@code true} if the bit is set, {@code false} otherwise.
   */
  public boolean readBit() {
    return readBits(1) == 1;
  }

  /**
   * Reads up to 32 bits.
   *
   * @param numBits The number of bits to read.
   * @return An int whose bottom {@code numBits} bits hold the read data.
   */
  public int readBits(int numBits) {
    Assertions.checkState(getPosition() + numBits <= limit);
    if (numBits == 0) {
      return 0;
    }
    int result = 0;
    int bitCount = 0;
    if (bitOffset != 0) {
      bitCount = Math.min(numBits, 8 - bitOffset);
      int mask = 0xFF >>> (8 - bitCount);
      result = (data[byteOffset] >>> bitOffset) & mask;
      bitOffset += bitCount;
      if (bitOffset == 8) {
        byteOffset++;
        bitOffset = 0;
      }
    }

    if (numBits - bitCount > 7) {
      int numBytes = (numBits - bitCount) / 8;
      for (int i = 0; i < numBytes; i++) {
        result |= (data[byteOffset++] & 0xFFL) << bitCount;
        bitCount += 8;
      }
    }

    if (numBits > bitCount) {
      int bitsOnNextByte = numBits - bitCount;
      int mask = 0xFF >>> (8 - bitsOnNextByte);
      result |= (data[byteOffset] & mask) << bitCount;
      bitOffset += bitsOnNextByte;
    }
    return result;
  }

  /**
   * Skips {@code numberOfBits} bits.
   *
   * @param numberOfBits the number of bits to skip.
   */
  public void skipBits(int numberOfBits) {
    Assertions.checkState(getPosition() + numberOfBits <= limit);
    byteOffset += numberOfBits / 8;
    bitOffset += numberOfBits % 8;
    if (bitOffset > 7) {
      byteOffset++;
      bitOffset -= 8;
    }
  }

  /**
   * Gets the current reading position in bits.
   *
   * @return the current reading position in bits.
   */
  public int getPosition() {
    return byteOffset * 8 + bitOffset;
  }

  /**
   * Sets the index of the current reading position in bits.
   *
   * @param position the new reading position in bits.
   */
  public void setPosition(int position) {
    Assertions.checkArgument(position < limit && position >= 0);
    byteOffset = position / 8;
    bitOffset = position - (byteOffset * 8);
  }

  /**
   * Gets the number of remaining bits.
   *
   * @return number of remaining bits.
   */
  public int bitsLeft() {
    return limit - getPosition();
  }

  /**
   * Returns the limit in bits.
   *
   * @return the limit in bits.
   **/
  public int limit() {
    return limit;
  }

}
