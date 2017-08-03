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
 * Wraps a byte array, providing methods that allow it to be read as a NAL unit bitstream.
 * <p>
 * Whenever the byte sequence [0, 0, 3] appears in the wrapped byte array, it is treated as [0, 0]
 * for all reading/skipping operations, which makes the bitstream appear to be unescaped.
 */
public final class ParsableNalUnitBitArray {

  private byte[] data;
  private int byteLimit;

  // The byte offset is never equal to the offset of the 3rd byte in a subsequence [0, 0, 3].
  private int byteOffset;
  private int bitOffset;

  /**
   * @param data The data to wrap.
   * @param offset The byte offset in {@code data} to start reading from.
   * @param limit The byte offset of the end of the bitstream in {@code data}.
   */
  public ParsableNalUnitBitArray(byte[] data, int offset, int limit) {
    reset(data, offset, limit);
  }

  /**
   * Resets the wrapped data, limit and offset.
   *
   * @param data The data to wrap.
   * @param offset The byte offset in {@code data} to start reading from.
   * @param limit The byte offset of the end of the bitstream in {@code data}.
   */
  public void reset(byte[] data, int offset, int limit) {
    this.data = data;
    byteOffset = offset;
    byteLimit = limit;
    bitOffset = 0;
    assertValidOffset();
  }

  /**
   * Skips bits and moves current reading position forward.
   *
   * @param n The number of bits to skip.
   */
  public void skipBits(int n) {
    int oldByteOffset = byteOffset;
    byteOffset += (n / 8);
    bitOffset += (n % 8);
    if (bitOffset > 7) {
      byteOffset++;
      bitOffset -= 8;
    }
    for (int i = oldByteOffset + 1; i <= byteOffset; i++) {
      if (shouldSkipByte(i)) {
        // Skip the byte and move forward to check three bytes ahead.
        byteOffset++;
        i += 2;
      }
    }
    assertValidOffset();
  }

  /**
   * Returns whether it's possible to read {@code n} bits starting from the current offset. The
   * offset is not modified.
   *
   * @param n The number of bits.
   * @return Whether it is possible to read {@code n} bits.
   */
  public boolean canReadBits(int n) {
    int oldByteOffset = byteOffset;
    int newByteOffset = byteOffset + (n / 8);
    int newBitOffset = bitOffset + (n % 8);
    if (newBitOffset > 7) {
      newByteOffset++;
      newBitOffset -= 8;
    }
    for (int i = oldByteOffset + 1; i <= newByteOffset && newByteOffset < byteLimit; i++) {
      if (shouldSkipByte(i)) {
        // Skip the byte and move forward to check three bytes ahead.
        newByteOffset++;
        i += 2;
      }
    }
    return newByteOffset < byteLimit || (newByteOffset == byteLimit && newBitOffset == 0);
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
      int nextByteOffset = shouldSkipByte(byteOffset + 1) ? byteOffset + 2 : byteOffset + 1;
      int byteValue;
      if (bitOffset != 0) {
        byteValue = ((data[byteOffset] & 0xFF) << bitOffset)
            | ((data[nextByteOffset] & 0xFF) >>> (8 - bitOffset));
      } else {
        byteValue = data[byteOffset];
      }
      numBits -= 8;
      returnValue |= (byteValue & 0xFF) << numBits;
      byteOffset = nextByteOffset;
    }

    // Read any remaining bits.
    if (numBits > 0) {
      int nextBit = bitOffset + numBits;
      byte writeMask = (byte) (0xFF >> (8 - numBits));
      int nextByteOffset = shouldSkipByte(byteOffset + 1) ? byteOffset + 2 : byteOffset + 1;

      if (nextBit > 8) {
        // Combine bits from current byte and next byte.
        returnValue |= ((((data[byteOffset] & 0xFF) << (nextBit - 8)
            | ((data[nextByteOffset] & 0xFF) >> (16 - nextBit))) & writeMask));
        byteOffset = nextByteOffset;
      } else {
        // Bits to be read only within current byte.
        returnValue |= (((data[byteOffset] & 0xFF) >> (8 - nextBit)) & writeMask);
        if (nextBit == 8) {
          byteOffset = nextByteOffset;
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
    return !hitLimit && canReadBits(leadingZeros * 2 + 1);
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

  private boolean shouldSkipByte(int offset) {
    return 2 <= offset && offset < byteLimit && data[offset] == (byte) 0x03
        && data[offset - 2] == (byte) 0x00 && data[offset - 1] == (byte) 0x00;
  }

  private void assertValidOffset() {
    // It is fine for position to be at the end of the array, but no further.
    Assertions.checkState(byteOffset >= 0
        && (bitOffset >= 0 && bitOffset < 8)
        && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
  }

}
