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

import com.google.android.exoplayer2.C;
import java.io.IOException;

/** Extractor related utility methods. */
/* package */ final class ExtractorUtil {

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
   * @throws InterruptedException If the thread has been interrupted.
   */
  public static int peekToLength(ExtractorInput input, byte[] target, int offset, int length)
      throws IOException, InterruptedException {
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

  private ExtractorUtil() {}
}
