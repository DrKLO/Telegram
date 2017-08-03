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
package org.telegram.messenger.exoplayer2.util;

/**
 * Wraps a byte array, providing methods that allow it to be read as a bitstream.
 */
public final class ParsableBitArray {

  public byte[] data;

  // The offset within the data, stored as the current byte offset, and the bit offset within that
  // byte (from 0 to 7).
  private int byteOffset;
  private int bitOffset;
  private int byteLimit;

  /**
   * Creates a new instance that initially has no backing data.
   */
  public ParsableBitArray() {}

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   */
  public ParsableBitArray(byte[] data) {
    this(data, data.length);
  }

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   * @param limit The limit in bytes.
   */
  public ParsableBitArray(byte[] data, int limit) {
    this.data = data;
    byteLimit = limit;
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   */
  public void reset(byte[] data) {
    reset(data, data.length);
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   * @param limit The limit in bytes.
   */
  public void reset(byte[] data, int limit) {
    this.data = data;
    byteOffset = 0;
    bitOffset = 0;
    byteLimit = limit;
  }

  /**
   * Returns the number of bits yet to be read.
   */
  public int bitsLeft() {
    return (byteLimit - byteOffset) * 8 - bitOffset;
  }

  /**
   * Returns the current bit offset.
   */
  public int getPosition() {
    return byteOffset * 8 + bitOffset;
  }

  /**
   * Returns the current byte offset. Must only be called when the position is byte aligned.
   *
   * @throws IllegalStateException If the position isn't byte aligned.
   */
  public int getBytePosition() {
    Assertions.checkState(bitOffset == 0);
    return byteOffset;
  }

  /**
   * Sets the current bit offset.
   *
   * @param position The position to set.
   */
  public void setPosition(int position) {
    byteOffset = position / 8;
    bitOffset = position - (byteOffset * 8);
    assertValidOffset();
  }

  /**
   * Skips bits and moves current reading position forward.
   *
   * @param n The number of bits to skip.
   */
  public void skipBits(int n) {
    byteOffset += (n / 8);
    bitOffset += (n % 8);
    if (bitOffset > 7) {
      byteOffset++;
      bitOffset -= 8;
    }
    assertValidOffset();
  }

  /**
   * Reads a single bit.
   *
   * @return Whether the bit is set.
   */
  public boolean readBit() {
    return readBits(1) == 1;
  }

  /**
   * Reads up to 32 bits.
   *
   * @param numBits The number of bits to read.
   * @return An integer whose bottom n bits hold the read data.
   */
  public int readBits(int numBits) {
    if (numBits == 0) {
      return 0;
    }

    int returnValue = 0;

    // Read as many whole bytes as we can.
    int wholeBytes = (numBits / 8);
    for (int i = 0; i < wholeBytes; i++) {
      int byteValue;
      if (bitOffset != 0) {
        byteValue = ((data[byteOffset] & 0xFF) << bitOffset)
            | ((data[byteOffset + 1] & 0xFF) >>> (8 - bitOffset));
      } else {
        byteValue = data[byteOffset];
      }
      numBits -= 8;
      returnValue |= (byteValue & 0xFF) << numBits;
      byteOffset++;
    }

    // Read any remaining bits.
    if (numBits > 0) {
      int nextBit = bitOffset + numBits;
      byte writeMask = (byte) (0xFF >> (8 - numBits));

      if (nextBit > 8) {
        // Combine bits from current byte and next byte.
        returnValue |= ((((data[byteOffset] & 0xFF) << (nextBit - 8)
            | ((data[byteOffset + 1] & 0xFF) >> (16 - nextBit))) & writeMask));
        byteOffset++;
      } else {
        // Bits to be read only within current byte.
        returnValue |= (((data[byteOffset] & 0xFF) >> (8 - nextBit)) & writeMask);
        if (nextBit == 8) {
          byteOffset++;
        }
      }

      bitOffset = nextBit % 8;
    }

    assertValidOffset();
    return returnValue;
  }

  /**
   * Aligns the position to the next byte boundary. Does nothing if the position is already aligned.
   */
  public void byteAlign() {
    if (bitOffset == 0) {
      return;
    }
    bitOffset = 0;
    byteOffset++;
    assertValidOffset();
  }

  /**
   * Reads the next {@code length} bytes into {@code buffer}. Must only be called when the position
   * is byte aligned.
   *
   * @see System#arraycopy(Object, int, Object, int, int)
   * @param buffer The array into which the read data should be written.
   * @param offset The offset in {@code buffer} at which the read data should be written.
   * @param length The number of bytes to read.
   * @throws IllegalStateException If the position isn't byte aligned.
   */
  public void readBytes(byte[] buffer, int offset, int length) {
    Assertions.checkState(bitOffset == 0);
    System.arraycopy(data, byteOffset, buffer, offset, length);
    byteOffset += length;
    assertValidOffset();
  }

  /**
   * Skips the next {@code length} bytes. Must only be called when the position is byte aligned.
   *
   * @param length The number of bytes to read.
   * @throws IllegalStateException If the position isn't byte aligned.
   */
  public void skipBytes(int length) {
    Assertions.checkState(bitOffset == 0);
    byteOffset += length;
    assertValidOffset();
  }

  private void assertValidOffset() {
    // It is fine for position to be at the end of the array, but no further.
    Assertions.checkState(byteOffset >= 0
        && (bitOffset >= 0 && bitOffset < 8)
        && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
  }

}
