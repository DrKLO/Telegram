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
package com.google.android.exoplayer2.upstream;

import com.google.android.exoplayer2.C;
import java.io.IOException;

/** Reads bytes from a data stream. */
public interface DataReader {
  /**
   * Reads up to {@code length} bytes of data from the input.
   *
   * <p>If {@code readLength} is zero then 0 is returned. Otherwise, if no data is available because
   * the end of the opened range has been reached, then {@link C#RESULT_END_OF_INPUT} is returned.
   * Otherwise, the call will block until at least one byte of data has been read and the number of
   * bytes read is returned.
   *
   * @param buffer A target array into which data should be written.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes to read from the input.
   * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the input has ended. This
   *     may be less than {@code length} because the end of the input (or available data) was
   *     reached, the method was interrupted, or the operation was aborted early for another reason.
   * @throws IOException If an error occurs reading from the input.
   */
  int read(byte[] buffer, int offset, int length) throws IOException;
}
