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
package org.telegram.messenger.exoplayer.dash;

import org.telegram.messenger.exoplayer.dash.mpd.RangedUri;
import org.telegram.messenger.exoplayer.extractor.ChunkIndex;

/**
 * An implementation of {@link DashSegmentIndex} that wraps a {@link ChunkIndex} parsed from a
 * media stream.
 */
/* package */ final class DashWrappingSegmentIndex implements DashSegmentIndex {

  private final ChunkIndex chunkIndex;
  private final String uri;

  /**
   * @param chunkIndex The {@link ChunkIndex} to wrap.
   * @param uri The URI where the data is located.
   */
  public DashWrappingSegmentIndex(ChunkIndex chunkIndex, String uri) {
    this.chunkIndex = chunkIndex;
    this.uri = uri;
  }

  @Override
  public int getFirstSegmentNum() {
    return 0;
  }

  @Override
  public int getLastSegmentNum(long periodDurationUs) {
    return chunkIndex.length - 1;
  }

  @Override
  public long getTimeUs(int segmentNum) {
    return chunkIndex.timesUs[segmentNum];
  }

  @Override
  public long getDurationUs(int segmentNum, long periodDurationUs) {
    return chunkIndex.durationsUs[segmentNum];
  }

  @Override
  public RangedUri getSegmentUrl(int segmentNum) {
    return new RangedUri(uri, null, chunkIndex.offsets[segmentNum], chunkIndex.sizes[segmentNum]);
  }

  @Override
  public int getSegmentNum(long timeUs, long periodDurationUs) {
    return chunkIndex.getChunkIndex(timeUs);
  }

  @Override
  public boolean isExplicit() {
    return true;
  }

}
