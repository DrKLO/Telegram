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
import org.telegram.messenger.exoplayer.chunk.ChunkExtractorWrapper.SingleTrackOutput;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.extractor.DefaultExtractorInput;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;

/**
 * A {@link BaseMediaChunk} that uses an {@link Extractor} to parse sample data.
 */
public class ContainerMediaChunk extends BaseMediaChunk implements SingleTrackOutput {

  private final ChunkExtractorWrapper extractorWrapper;
  private final long sampleOffsetUs;
  private final int adaptiveMaxWidth;
  private final int adaptiveMaxHeight;

  private MediaFormat mediaFormat;
  private DrmInitData drmInitData;

  private volatile int bytesLoaded;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param format The format of the stream to which this chunk belongs.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk.
   * @param sampleOffsetUs An offset to add to the sample timestamps parsed by the extractor.
   * @param extractorWrapper A wrapped extractor to use for parsing the data.
   * @param mediaFormat The {@link MediaFormat} of the chunk, if known. May be null if the data is
   *     known to define its own format.
   * @param adaptiveMaxWidth If this chunk contains video and is part of an adaptive playback, this
   *     is the maximum width of the video in pixels that will be encountered during the playback.
   *     {@link MediaFormat#NO_VALUE} otherwise.
   * @param adaptiveMaxHeight If this chunk contains video and is part of an adaptive playback, this
   *     is the maximum height of the video in pixels that will be encountered during the playback.
   *     {@link MediaFormat#NO_VALUE} otherwise.
   * @param drmInitData The {@link DrmInitData} for the chunk. Null if the media is not drm
   *     protected. May also be null if the data is known to define its own initialization data.
   * @param isMediaFormatFinal True if {@code mediaFormat} and {@code drmInitData} are known to be
   *     correct and final. False if the data may define its own format or initialization data.
   * @param parentId Identifier for a parent from which this chunk originates.
   */
  public ContainerMediaChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      long startTimeUs, long endTimeUs, int chunkIndex, long sampleOffsetUs,
      ChunkExtractorWrapper extractorWrapper, MediaFormat mediaFormat, int adaptiveMaxWidth,
      int adaptiveMaxHeight, DrmInitData drmInitData, boolean isMediaFormatFinal, int parentId) {
    super(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs, chunkIndex,
        isMediaFormatFinal, parentId);
    this.extractorWrapper = extractorWrapper;
    this.sampleOffsetUs = sampleOffsetUs;
    this.adaptiveMaxWidth = adaptiveMaxWidth;
    this.adaptiveMaxHeight = adaptiveMaxHeight;
    this.mediaFormat = getAdjustedMediaFormat(mediaFormat, sampleOffsetUs, adaptiveMaxWidth,
        adaptiveMaxHeight);
    this.drmInitData = drmInitData;
  }

  @Override
  public final long bytesLoaded() {
    return bytesLoaded;
  }

  @Override
  public final MediaFormat getMediaFormat() {
    return mediaFormat;
  }

  @Override
  public final DrmInitData getDrmInitData() {
    return drmInitData;
  }

  // SingleTrackOutput implementation.

  @Override
  public final void seekMap(SeekMap seekMap) {
    // Do nothing.
  }

  @Override
  public final void drmInitData(DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
  }

  @Override
  public final void format(MediaFormat mediaFormat) {
    this.mediaFormat = getAdjustedMediaFormat(mediaFormat, sampleOffsetUs, adaptiveMaxWidth,
        adaptiveMaxHeight);
  }

  @Override
  public final int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return getOutput().sampleData(input, length, allowEndOfInput);
  }

  @Override
  public final void sampleData(ParsableByteArray data, int length) {
    getOutput().sampleData(data, length);
  }

  @Override
  public final void sampleMetadata(long timeUs, int flags, int size, int offset,
      byte[] encryptionKey) {
    getOutput().sampleMetadata(timeUs + sampleOffsetUs, flags, size, offset, encryptionKey);
  }

  // Loadable implementation.

  @Override
  public final void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public final boolean isLoadCanceled() {
    return loadCanceled;
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public final void load() throws IOException, InterruptedException {
    DataSpec loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
    try {
      // Create and open the input.
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (bytesLoaded == 0) {
        // Set the target to ourselves.
        extractorWrapper.init(this);
      }
      // Load and parse the sample data.
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractorWrapper.read(input);
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      dataSource.close();
    }
  }

  // Private methods.

  private static MediaFormat getAdjustedMediaFormat(MediaFormat format, long sampleOffsetUs,
      int adaptiveMaxWidth, int adaptiveMaxHeight) {
    if (format == null) {
      return null;
    }
    if (sampleOffsetUs != 0 && format.subsampleOffsetUs != MediaFormat.OFFSET_SAMPLE_RELATIVE) {
      format = format.copyWithSubsampleOffsetUs(format.subsampleOffsetUs + sampleOffsetUs);
    }
    if (adaptiveMaxWidth != MediaFormat.NO_VALUE || adaptiveMaxHeight != MediaFormat.NO_VALUE) {
      format = format.copyWithMaxVideoDimensions(adaptiveMaxWidth, adaptiveMaxHeight);
    }
    return format;
  }

}
