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
package org.telegram.messenger.exoplayer.chunk;

import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.util.Assertions;

/**
 * An abstract base class for {@link Chunk}s that contain media samples.
 */
public abstract class MediaChunk extends Chunk {

  /**
   * The start time of the media contained by the chunk.
   */
  public final long startTimeUs;
  /**
   * The end time of the media contained by the chunk.
   */
  public final long endTimeUs;
  /**
   * The chunk index.
   */
  public final int chunkIndex;

  public MediaChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      long startTimeUs, long endTimeUs, int chunkIndex) {
    this(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs, chunkIndex,
        Chunk.NO_PARENT_ID);
  }

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param format The format of the stream to which this chunk belongs.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   * @param parentId Identifier for a parent from which this chunk originates.
   */
  public MediaChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      long startTimeUs, long endTimeUs, int chunkIndex, int parentId) {
    super(dataSource, dataSpec, Chunk.TYPE_MEDIA, trigger, format, parentId);
    Assertions.checkNotNull(format);
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.chunkIndex = chunkIndex;
  }

  public int getNextChunkIndex() {
    return chunkIndex + 1;
  }

  public long getDurationUs() {
    return endTimeUs - startTimeUs;
  }

}
