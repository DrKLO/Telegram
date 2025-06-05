/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

/** An empty {@link SampleStream}. */
public final class EmptySampleStream implements SampleStream {

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void maybeThrowError() {
    // Do nothing.
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
    buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    return C.RESULT_BUFFER_READ;
  }

  @Override
  public int skipData(long positionUs) {
    return 0;
  }
}
