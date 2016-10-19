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
package org.telegram.messenger.exoplayer.util;

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

  /** Creates a new instance that initially has no backing data. */
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
   * Gets the current bit offset.
   *
   * @return The current bit offset.
   */
  public int getPosition() {
    return byteOffset * 8 + bitOffset;
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
   * @return True if the bit is set. False otherwise.
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
   * Returns whether it is possible to read an Exp-Golomb-coded integer starting from the current
   * offset. The offset is not modified.
   *
   * @return Whether it is possible to read an Exp-Golomb-coded integer.
   */
  public boolean canReadExpGolombCodedNum() {
    int initialByteOffset = byteOffset;
    int initialBitOffset = bitOffset;
    int leadingZeros = 0;
    while (byteOffset < byteLimit && !readBit()) {
      leadingZeros++;
    }
    boolean hitLimit = byteOffset == byteLimit;
    byteOffset = initialByteOffset;
    bitOffset = initialBitOffset;
    return !hitLimit && bitsLeft() >= leadingZeros * 2 + 1;
  }

  /**
   * Reads an unsigned Exp-Golomb-coded format integer.
   *
   * @return The value of the parsed Exp-Golomb-coded integer.
   */
  public int readUnsignedExpGolombCodedInt() {
    return readExpGolombCodeNum();
  }

  /**
   * Reads an signed Exp-Golomb-coded format integer.
   *
   * @return The value of the parsed Exp-Golomb-coded integer.
   */
  public int readSignedExpGolombCodedInt() {
    int codeNum = readExpGolombCodeNum();
    return ((codeNum % 2) == 0 ? -1 : 1) * ((codeNum + 1) / 2);
  }

  private int readExpGolombCodeNum() {
    int leadingZeros = 0;
    while (!readBit()) {
      leadingZeros++;
    }
    return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? readBits(leadingZeros) : 0);
  }

  private void assertValidOffset() {
    // It is fine for position to be at the end of the array, but no further.
    Assertions.checkState(byteOffset >= 0
        && (bitOffset >= 0 && bitOffset < 8)
        && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
  }

}
