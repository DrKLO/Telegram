/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import java.io.EOFException;
import java.io.IOException;
import org.checkerframework.dataflow.qual.Pure;

/** Extractor related utility methods. */
public final class ExtractorUtil {

  /**
   * If {@code expression} is false, throws a {@link ParserException#createForMalformedContainer
   * container malformed ParserException} with the given message. Otherwise, does nothing.
   */
  @Pure
  public static void checkContainerInput(boolean expression, @Nullable String message)
      throws ParserException {
    if (!expression) {
      throw ParserException.createForMalformedContainer(message, /* cause= */ null);
    }
  }

  /**
   * Peeks {@code length} bytes from the input peek position, or all the bytes to the end of the
   * input if there was less than {@code length} bytes left.
   *
   * <p>If an exception is thrown, there is no guarantee on the peek position.
   *
   * @param input The stream input to peek the data from.
   * @param target A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to peek from the input.
   * @return The number of bytes peeked.
   * @throws IOException If an error occurs peeking from the input.
   */
  public static int peekToLength(ExtractorInput input, byte[] target, int offset, int length)
      throws IOException {
    int totalBytesPeeked = 0;
    while (totalBytesPeeked < length) {
      int bytesPeeked = input.peek(target, offset + totalBytesPeeked, length - totalBytesPeeked);
      if (bytesPeeked == C.RESULT_END_OF_INPUT) {
        break;
      }
      totalBytesPeeked += bytesPeeked;
    }
    return totalBytesPeeked;
  }

  /**
   * Equivalent to {@link ExtractorInput#readFully(byte[], int, int)} except that it returns {@code
   * false} instead of throwing an {@link EOFException} if the end of input is encountered without
   * having fully satisfied the read.
   */
  public static boolean readFullyQuietly(
      ExtractorInput input, byte[] output, int offset, int length) throws IOException {
    try {
      input.readFully(output, offset, length);
    } catch (EOFException e) {
      return false;
    }
    return true;
  }

  /**
   * Equivalent to {@link ExtractorInput#skipFully(int)} except that it returns {@code false}
   * instead of throwing an {@link EOFException} if the end of input is encountered without having
   * fully satisfied the skip.
   */
  public static boolean skipFullyQuietly(ExtractorInput input, int length) throws IOException {
    try {
      input.skipFully(length);
    } catch (EOFException e) {
      return false;
    }
    return true;
  }

  /**
   * Peeks data from {@code input}, respecting {@code allowEndOfInput}. Returns true if the peek is
   * successful.
   *
   * <p>If {@code allowEndOfInput=false} then encountering the end of the input (whether before or
   * after reading some data) will throw {@link EOFException}.
   *
   * <p>If {@code allowEndOfInput=true} then encountering the end of the input (even after reading
   * some data) will return {@code false}.
   *
   * <p>This is slightly different to the behaviour of {@link ExtractorInput#peekFully(byte[], int,
   * int, boolean)}, where {@code allowEndOfInput=true} only returns false (and suppresses the
   * exception) if the end of the input is reached before reading any data.
   */
  public static boolean peekFullyQuietly(
      ExtractorInput input, byte[] output, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    try {
      return input.peekFully(output, offset, length, /* allowEndOfInput= */ allowEndOfInput);
    } catch (EOFException e) {
      if (allowEndOfInput) {
        return false;
      } else {
        throw e;
      }
    }
  }

  private ExtractorUtil() {}
}
