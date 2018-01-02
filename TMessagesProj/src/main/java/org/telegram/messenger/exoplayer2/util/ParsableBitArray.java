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
   * Skips a single bit.
   */
  public void skipBit() {
    if (++bitOffset == 8) {
      bitOffset = 0;
      byteOffset++;
    }
    assertValidOffset();
  }

  /**
   * Skips bits and moves current reading position forward.
   *
   * @param numBits The number of bits to skip.
   */
  public void skipBits(int numBits) {
    int numBytes = numBits / 8;
    byteOffset += numBytes;
    bitOffset += numBits - (numBytes * 8);
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
    boolean returnValue = (data[byteOffset] & (0x80 >> bitOffset)) != 0;
    skipBit();
    return returnValue;
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
    bitOffset += numBits;
    while (bitOffset > 8) {
      bitOffset -= 8;
      returnValue |= (data[byteOffset++] & 0xFF) << bitOffset;
    }
    returnValue |= (data[byteOffset] & 0xFF) >> 8 - bitOffset;
    returnValue &= 0xFFFFFFFF >>> (32 - numBits);
    if (bitOffset == 8) {
      bitOffset = 0;
      byteOffset++;
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
        && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
  }

}
