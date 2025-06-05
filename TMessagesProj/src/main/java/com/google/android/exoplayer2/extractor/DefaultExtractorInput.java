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
package com.google.android.exoplayer2.extractor;

import static java.lang.Math.min;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;

/** An {@link ExtractorInput} that wraps a {@link DataReader}. */
public final class DefaultExtractorInput implements ExtractorInput {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.extractor");
  }

  private static final int PEEK_MIN_FREE_SPACE_AFTER_RESIZE = 64 * 1024;
  private static final int PEEK_MAX_FREE_SPACE = 512 * 1024;
  private static final int SCRATCH_SPACE_SIZE = 4096;

  private final byte[] scratchSpace;
  private final DataReader dataReader;
  private final long streamLength;

  private long position;
  private byte[] peekBuffer;
  private int peekBufferPosition;
  private int peekBufferLength;

  /**
   * @param dataReader The wrapped {@link DataReader}.
   * @param position The initial position in the stream.
   * @param length The length of the stream, or {@link C#LENGTH_UNSET} if it is unknown.
   */
  public DefaultExtractorInput(DataReader dataReader, long position, long length) {
    this.dataReader = dataReader;
    this.position = position;
    this.streamLength = length;
    peekBuffer = new byte[PEEK_MIN_FREE_SPACE_AFTER_RESIZE];
    scratchSpace = new byte[SCRATCH_SPACE_SIZE];
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    int bytesRead = readFromPeekBuffer(buffer, offset, length);
    if (bytesRead == 0) {
      bytesRead =
          readFromUpstream(
              buffer, offset, length, /* bytesAlreadyRead= */ 0, /* allowEndOfInput= */ true);
    }
    commitBytesRead(bytesRead);
    return bytesRead;
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    int bytesRead = readFromPeekBuffer(target, offset, length);
    while (bytesRead < length && bytesRead != C.RESULT_END_OF_INPUT) {
      bytesRead = readFromUpstream(target, offset, length, bytesRead, allowEndOfInput);
    }
    commitBytesRead(bytesRead);
    return bytesRead != C.RESULT_END_OF_INPUT;
  }

  @Override
  public void readFully(byte[] target, int offset, int length) throws IOException {
    readFully(target, offset, length, false);
  }

  @Override
  public int skip(int length) throws IOException {
    int bytesSkipped = skipFromPeekBuffer(length);
    if (bytesSkipped == 0) {
      bytesSkipped = readFromUpstream(scratchSpace, 0, min(length, scratchSpace.length), 0, true);
    }
    commitBytesRead(bytesSkipped);
    return bytesSkipped;
  }

  @Override
  public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
    int bytesSkipped = skipFromPeekBuffer(length);
    while (bytesSkipped < length && bytesSkipped != C.RESULT_END_OF_INPUT) {
      int minLength = min(length, bytesSkipped + scratchSpace.length);
      bytesSkipped =
          readFromUpstream(scratchSpace, -bytesSkipped, minLength, bytesSkipped, allowEndOfInput);
    }
    commitBytesRead(bytesSkipped);
    return bytesSkipped != C.RESULT_END_OF_INPUT;
  }

  @Override
  public void skipFully(int length) throws IOException {
    skipFully(length, false);
  }

  @Override
  public int peek(byte[] target, int offset, int length) throws IOException {
    ensureSpaceForPeek(length);
    int peekBufferRemainingBytes = peekBufferLength - peekBufferPosition;
    int bytesPeeked;
    if (peekBufferRemainingBytes == 0) {
      bytesPeeked =
          readFromUpstream(
              peekBuffer,
              peekBufferPosition,
              length,
              /* bytesAlreadyRead= */ 0,
              /* allowEndOfInput= */ true);
      if (bytesPeeked == C.RESULT_END_OF_INPUT) {
        return C.RESULT_END_OF_INPUT;
      }
      peekBufferLength += bytesPeeked;
    } else {
      bytesPeeked = min(length, peekBufferRemainingBytes);
    }
    System.arraycopy(peekBuffer, peekBufferPosition, target, offset, bytesPeeked);
    peekBufferPosition += bytesPeeked;
    return bytesPeeked;
  }

  @Override
  public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    if (!advancePeekPosition(length, allowEndOfInput)) {
      return false;
    }
    System.arraycopy(peekBuffer, peekBufferPosition - length, target, offset, length);
    return true;
  }

  @Override
  public void peekFully(byte[] target, int offset, int length) throws IOException {
    peekFully(target, offset, length, false);
  }

  @Override
  public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
    ensureSpaceForPeek(length);
    int bytesPeeked = peekBufferLength - peekBufferPosition;
    while (bytesPeeked < length) {
      bytesPeeked =
          readFromUpstream(peekBuffer, peekBufferPosition, length, bytesPeeked, allowEndOfInput);
      if (bytesPeeked == C.RESULT_END_OF_INPUT) {
        return false;
      }
      peekBufferLength = peekBufferPosition + bytesPeeked;
    }
    peekBufferPosition += length;
    return true;
  }

  @Override
  public void advancePeekPosition(int length) throws IOException {
    advancePeekPosition(length, false);
  }

  @Override
  public void resetPeekPosition() {
    peekBufferPosition = 0;
  }

  @Override
  public long getPeekPosition() {
    return position + peekBufferPosition;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getLength() {
    return streamLength;
  }

  @Override
  public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
    Assertions.checkArgument(position >= 0);
    this.position = position;
    throw e;
  }

  /**
   * Ensures {@code peekBuffer} is large enough to store at least {@code length} bytes from the
   * current peek position.
   */
  private void ensureSpaceForPeek(int length) {
    int requiredLength = peekBufferPosition + length;
    if (requiredLength > peekBuffer.length) {
      int newPeekCapacity =
          Util.constrainValue(
              peekBuffer.length * 2,
              requiredLength + PEEK_MIN_FREE_SPACE_AFTER_RESIZE,
              requiredLength + PEEK_MAX_FREE_SPACE);
      peekBuffer = Arrays.copyOf(peekBuffer, newPeekCapacity);
    }
  }

  /**
   * Skips from the peek buffer.
   *
   * @param length The maximum number of bytes to skip from the peek buffer.
   * @return The number of bytes skipped.
   */
  private int skipFromPeekBuffer(int length) {
    int bytesSkipped = min(peekBufferLength, length);
    updatePeekBuffer(bytesSkipped);
    return bytesSkipped;
  }

  /**
   * Reads from the peek buffer.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the peek buffer.
   * @return The number of bytes read.
   */
  private int readFromPeekBuffer(byte[] target, int offset, int length) {
    if (peekBufferLength == 0) {
      return 0;
    }
    int peekBytes = min(peekBufferLength, length);
    System.arraycopy(peekBuffer, 0, target, offset, peekBytes);
    updatePeekBuffer(peekBytes);
    return peekBytes;
  }

  /**
   * Updates the peek buffer's length, position and contents after consuming data.
   *
   * @param bytesConsumed The number of bytes consumed from the peek buffer.
   */
  private void updatePeekBuffer(int bytesConsumed) {
    peekBufferLength -= bytesConsumed;
    peekBufferPosition = 0;
    byte[] newPeekBuffer = peekBuffer;
    if (peekBufferLength < peekBuffer.length - PEEK_MAX_FREE_SPACE) {
      newPeekBuffer = new byte[peekBufferLength + PEEK_MIN_FREE_SPACE_AFTER_RESIZE];
    }
    System.arraycopy(peekBuffer, bytesConsumed, newPeekBuffer, 0, peekBufferLength);
    peekBuffer = newPeekBuffer;
  }

  /**
   * Starts or continues a read from the data reader.
   *
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the input.
   * @param bytesAlreadyRead The number of bytes already read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @return The total number of bytes read so far, or {@link C#RESULT_END_OF_INPUT} if {@code
   *     allowEndOfInput} is true and the input has ended having read no bytes.
   * @throws EOFException If the end of input was encountered having partially satisfied the read
   *     (i.e. having read at least one byte, but fewer than {@code length}), or if no bytes were
   *     read and {@code allowEndOfInput} is false.
   * @throws IOException If an error occurs reading from the input.
   */
  private int readFromUpstream(
      byte[] target, int offset, int length, int bytesAlreadyRead, boolean allowEndOfInput)
      throws IOException {
    if (Thread.interrupted()) {
      throw new InterruptedIOException();
    }
    int bytesRead = dataReader.read(target, offset + bytesAlreadyRead, length - bytesAlreadyRead);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      if (bytesAlreadyRead == 0 && allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    return bytesAlreadyRead + bytesRead;
  }

  /**
   * Advances the position by the specified number of bytes read.
   *
   * @param bytesRead The number of bytes read.
   */
  private void commitBytesRead(int bytesRead) {
    if (bytesRead != C.RESULT_END_OF_INPUT) {
      position += bytesRead;
    }
  }
}
