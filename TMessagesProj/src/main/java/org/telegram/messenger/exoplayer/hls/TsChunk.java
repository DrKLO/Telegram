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
package org.telegram.messenger.exoplayer.hls;

import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.chunk.MediaChunk;
import org.telegram.messenger.exoplayer.extractor.DefaultExtractorInput;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;

/**
 * An MPEG2TS chunk.
 */
public final class TsChunk extends MediaChunk {

  /**
   * The discontinuity sequence number of the chunk.
   */
  public final int discontinuitySequenceNumber;

  /**
   * The wrapped extractor into which this chunk is being consumed.
   */
  public final HlsExtractorWrapper extractorWrapper;

  private final boolean isEncrypted;

  private int bytesLoaded;
  private long adjustedEndTimeUs;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource A {@link DataSource} for loading the data.
   * @param dataSpec Defines the data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param format The format of the stream to which this chunk belongs.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param discontinuitySequenceNumber The discontinuity sequence number of the chunk.
   * @param chunkIndex The index of the chunk.
   * @param extractorWrapper A wrapped extractor to parse samples from the data.
   * @param encryptionKey For AES encryption chunks, the encryption key.
   * @param encryptionIv For AES encryption chunks, the encryption initialization vector.
   */
  public TsChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      long startTimeUs, long endTimeUs, int chunkIndex, int discontinuitySequenceNumber,
      HlsExtractorWrapper extractorWrapper, byte[] encryptionKey, byte[] encryptionIv) {
    super(buildDataSource(dataSource, encryptionKey, encryptionIv), dataSpec, trigger, format,
        startTimeUs, endTimeUs, chunkIndex);
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.extractorWrapper = extractorWrapper;
    // Note: this.dataSource and dataSource may be different.
    this.isEncrypted = this.dataSource instanceof Aes128DataSource;
    adjustedEndTimeUs = startTimeUs;
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  // Loadable implementation

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    // If we previously fed part of this chunk to the extractor, we need to skip it this time. For
    // encrypted content we need to skip the data by reading it through the source, so as to ensure
    // correct decryption of the remainder of the chunk. For clear content, we can request the
    // remainder of the chunk directly.
    DataSpec loadDataSpec;
    boolean skipLoadedBytes;
    if (isEncrypted) {
      loadDataSpec = dataSpec;
      skipLoadedBytes = bytesLoaded != 0;
    } else {
      loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
      skipLoadedBytes = false;
    }

    try {
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (skipLoadedBytes) {
        input.skipFully(bytesLoaded);
      }
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractorWrapper.read(input);
        }
        long tsChunkEndTimeUs = extractorWrapper.getAdjustedEndTimeUs();
        if (tsChunkEndTimeUs != Long.MIN_VALUE) {
          adjustedEndTimeUs = tsChunkEndTimeUs;
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      dataSource.close();
    }
  }

  public long getAdjustedEndTimeUs() {
    return adjustedEndTimeUs;
  }

  // Private methods

  /**
   * If the content is encrypted, returns an {@link Aes128DataSource} that wraps the original in
   * order to decrypt the loaded data. Else returns the original.
   */
  private static DataSource buildDataSource(DataSource dataSource, byte[] encryptionKey,
      byte[] encryptionIv) {
    if (encryptionKey == null || encryptionIv == null) {
      return dataSource;
    }
    return new Aes128DataSource(dataSource, encryptionKey, encryptionIv);
  }

}
