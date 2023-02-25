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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;

/** A fake {@link TrackOutput} implementation. */
public final class DummyTrackOutput implements TrackOutput {

  // Even though read data is discarded, data source implementations could be making use of the
  // buffer contents. For example, caches. So we cannot use a static field for this which could be
  // shared between different threads.
  private final byte[] readBuffer;

  public DummyTrackOutput() {
    readBuffer = new byte[4096];
  }

  @Override
  public void format(Format format) {
    // Do nothing.
  }

  @Override
  public int sampleData(
      DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
      throws IOException {
    int bytesToSkipByReading = min(readBuffer.length, length);
    int bytesSkipped = input.read(readBuffer, /* offset= */ 0, bytesToSkipByReading);
    if (bytesSkipped == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    return bytesSkipped;
  }

  @Override
  public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
    data.skipBytes(length);
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    // Do nothing.
  }
}
