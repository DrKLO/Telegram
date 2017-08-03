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

import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;

/**
 * A base implementation of {@link MediaChunk} that outputs to a {@link BaseMediaChunkOutput}.
 */
public abstract class BaseMediaChunk extends MediaChunk {

  private BaseMediaChunkOutput output;
  private int[] firstSampleIndices;

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
  public BaseMediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs,
      int chunkIndex) {
    super(dataSource, dataSpec, trackFormat, trackSelectionReason, trackSelectionData, startTimeUs,
        endTimeUs, chunkIndex);
  }

  /**
   * Initializes the chunk for loading, setting the {@link BaseMediaChunkOutput} that will receive
   * samples as they are loaded.
   *
   * @param output The output that will receive the loaded media samples.
   */
  public void init(BaseMediaChunkOutput output) {
    this.output = output;
    firstSampleIndices = output.getWriteIndices();
  }

  /**
   * Returns the index of the first sample in the specified track of the output that will originate
   * from this chunk.
   */
  public final int getFirstSampleIndex(int trackIndex) {
    return firstSampleIndices[trackIndex];
  }

  /**
   * Returns the output most recently passed to {@link #init(BaseMediaChunkOutput)}.
   */
  protected final BaseMediaChunkOutput getOutput() {
    return output;
  }

}
