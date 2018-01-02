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
package org.telegram.messenger.exoplayer2.source.hls;

import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import java.io.IOException;

/**
 * {@link SampleStream} for a particular track group in HLS.
 */
/* package */ final class HlsSampleStream implements SampleStream {

  public final int group;

  private final HlsSampleStreamWrapper sampleStreamWrapper;

  public HlsSampleStream(HlsSampleStreamWrapper sampleStreamWrapper, int group) {
    this.sampleStreamWrapper = sampleStreamWrapper;
    this.group = group;
  }

  @Override
  public boolean isReady() {
    return sampleStreamWrapper.isReady(group);
  }

  @Override
  public void maybeThrowError() throws IOException {
    sampleStreamWrapper.maybeThrowError();
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean requireFormat) {
    return sampleStreamWrapper.readData(group, formatHolder, buffer, requireFormat);
  }

  @Override
  public void skipData(long positionUs) {
    sampleStreamWrapper.skipData(group, positionUs);
  }

}
