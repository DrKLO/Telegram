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

import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.extractor.DefaultTrackOutput;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;

/**
 * A base implementation of {@link MediaChunk}, for chunks that contain a single track.
 * <p>
 * Loaded samples are output to a {@link DefaultTrackOutput}.
 */
public abstract class BaseMediaChunk extends MediaChunk {

  /**
   * Whether {@link #getMediaFormat()} and {@link #getDrmInitData()} can be called at any time to
   * obtain the chunk's media format and drm initialization data. If false, these methods are only
   * guaranteed to return correct data after the first sample data has been output from the chunk.
   */
  public final boolean isMediaFormatFinal;

  private DefaultTrackOutput output;
  private int firstSampleIndex;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param format The format of the stream to which this chunk belongs.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   * @param isMediaFormatFinal True if {@link #getMediaFormat()} and {@link #getDrmInitData()} can
   *     be called at any time to obtain the media format and drm initialization data. False if
   *     these methods are only guaranteed to return correct data after the first sample data has
   *     been output from the chunk.
   * @param parentId Identifier for a parent from which this chunk originates.
   */
  public BaseMediaChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      long startTimeUs, long endTimeUs, int chunkIndex, boolean isMediaFormatFinal, int parentId) {
    super(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs, chunkIndex, parentId);
    this.isMediaFormatFinal = isMediaFormatFinal;
  }

  /**
   * Initializes the chunk for loading, setting the {@link DefaultTrackOutput} that will receive
   * samples as they are loaded.
   *
   * @param output The output that will receive the loaded samples.
   */
  public void init(DefaultTrackOutput output) {
    this.output = output;
    this.firstSampleIndex = output.getWriteIndex();
  }

  /**
   * Returns the index of the first sample in the output that was passed to
   * {@link #init(DefaultTrackOutput)} that will originate from this chunk.
   */
  public final int getFirstSampleIndex() {
    return firstSampleIndex;
  }

  /**
   * Gets the {@link MediaFormat} corresponding to the chunk.
   * <p>
   * See {@link #isMediaFormatFinal} for information about when this method is guaranteed to return
   * correct data.
   *
   * @return The {@link MediaFormat} corresponding to this chunk.
   */
  public abstract MediaFormat getMediaFormat();

  /**
   * Gets the {@link DrmInitData} corresponding to the chunk.
   * <p>
   * See {@link #isMediaFormatFinal} for information about when this method is guaranteed to return
   * correct data.
   *
   * @return The {@link DrmInitData} corresponding to this chunk.
   */
  public abstract DrmInitData getDrmInitData();

  /**
   * Returns the output most recently passed to {@link #init(DefaultTrackOutput)}.
   */
  protected final DefaultTrackOutput getOutput() {
    return output;
  }

}
