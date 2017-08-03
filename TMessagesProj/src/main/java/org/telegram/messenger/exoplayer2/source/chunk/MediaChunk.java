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
package org.telegram.messenger.exoplayer2.source.chunk;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.util.Assertions;

/**
 * An abstract base class for {@link Chunk}s that contain media samples.
 */
public abstract class MediaChunk extends Chunk {

  /**
   * The chunk index.
   */
  public final int chunkIndex;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param trackFormat See {@link #trackFormat}.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   */
  public MediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs,
      int chunkIndex) {
    super(dataSource, dataSpec, C.DATA_TYPE_MEDIA, trackFormat, trackSelectionReason,
        trackSelectionData, startTimeUs, endTimeUs);
    Assertions.checkNotNull(trackFormat);
    this.chunkIndex = chunkIndex;
  }

  /**
   * Returns the next chunk index.
   */
  public int getNextChunkIndex() {
    return chunkIndex + 1;
  }

  /**
   * Returns whether the chunk has been fully loaded.
   */
  public abstract boolean isLoadCompleted();

}
