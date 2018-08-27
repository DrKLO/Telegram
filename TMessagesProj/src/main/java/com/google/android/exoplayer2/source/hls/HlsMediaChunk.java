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
package com.google.android.exoplayer2.source.hls;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HLS {@link MediaChunk}.
 */
/* package */ final class HlsMediaChunk extends MediaChunk {


  private static final String PRIV_TIMESTAMP_FRAME_OWNER =
      "com.apple.streaming.transportStreamTimestamp";

  private static final AtomicInteger uidSource = new AtomicInteger();

  /**
   * A unique identifier for the chunk.
   */
  public final int uid;

  /**
   * The discontinuity sequence number of the chunk.
   */
  public final int discontinuitySequenceNumber;

  /**
   * The url of the playlist from which this chunk was obtained.
   */
  public final HlsUrl hlsUrl;

  private final DataSource initDataSource;
  private final DataSpec initDataSpec;
  private final boolean isEncrypted;
  private final boolean isMasterTimestampSource;
  private final boolean hasGapTag;
  private final TimestampAdjuster timestampAdjuster;
  private final boolean shouldSpliceIn;
  private final HlsExtractorFactory extractorFactory;
  private final List<Format> muxedCaptionFormats;
  private final DrmInitData drmInitData;
  private final Extractor previousExtractor;

  private Extractor extractor;
  private boolean isPackedAudioExtractor;
  private Id3Decoder id3Decoder;
  private ParsableByteArray id3Data;
  private HlsSampleStreamWrapper output;
  private int initSegmentBytesLoaded;
  private int nextLoadPosition;
  private boolean id3TimestampPeeked;
  private boolean initLoadCompleted;
  private volatile boolean loadCanceled;
  private boolean loadCompleted;

  /**
   * @param extractorFactory A {@link HlsExtractorFactory} from which the HLS media chunk extractor
   *     is obtained.
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param initDataSpec Defines the initialization data to be fed to new extractors. May be null.
   * @param hlsUrl The url of the playlist from which this chunk was obtained.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the master playlist.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param startTimeUs The start time of the chunk in microseconds.
   * @param endTimeUs The end time of the chunk in microseconds.
   * @param chunkMediaSequence The media sequence number of the chunk.
   * @param discontinuitySequenceNumber The discontinuity sequence number of the chunk.
   * @param hasGapTag Whether the chunk is tagged with EXT-X-GAP.
   * @param isMasterTimestampSource True if the chunk can initialize the timestamp adjuster.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param previousChunk The {@link HlsMediaChunk} that preceded this one. May be null.
   * @param drmInitData A {@link DrmInitData} to sideload to the extractor.
   * @param fullSegmentEncryptionKey The key to decrypt the full segment, or null if the segment is
   *     not fully encrypted.
   * @param encryptionIv The AES initialization vector, or null if the segment is not fully
   *     encrypted.
   */
  public HlsMediaChunk(
      HlsExtractorFactory extractorFactory,
      DataSource dataSource,
      DataSpec dataSpec,
      DataSpec initDataSpec,
      HlsUrl hlsUrl,
      List<Format> muxedCaptionFormats,
      int trackSelectionReason,
      Object trackSelectionData,
      long startTimeUs,
      long endTimeUs,
      long chunkMediaSequence,
      int discontinuitySequenceNumber,
      boolean hasGapTag,
      boolean isMasterTimestampSource,
      TimestampAdjuster timestampAdjuster,
      HlsMediaChunk previousChunk,
      DrmInitData drmInitData,
      byte[] fullSegmentEncryptionKey,
      byte[] encryptionIv) {
    super(
        buildDataSource(dataSource, fullSegmentEncryptionKey, encryptionIv),
        dataSpec,
        hlsUrl.format,
        trackSelectionReason,
        trackSelectionData,
        startTimeUs,
        endTimeUs,
        chunkMediaSequence);
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.initDataSpec = initDataSpec;
    this.hlsUrl = hlsUrl;
    this.isMasterTimestampSource = isMasterTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    this.isEncrypted = fullSegmentEncryptionKey != null;
    this.hasGapTag = hasGapTag;
    this.extractorFactory = extractorFactory;
    this.muxedCaptionFormats = muxedCaptionFormats;
    this.drmInitData = drmInitData;
    Extractor previousExtractor = null;
    if (previousChunk != null) {
      id3Decoder = previousChunk.id3Decoder;
      id3Data = previousChunk.id3Data;
      shouldSpliceIn = previousChunk.hlsUrl != hlsUrl;
      previousExtractor = previousChunk.discontinuitySequenceNumber != discontinuitySequenceNumber
          || shouldSpliceIn ? null : previousChunk.extractor;
    } else {
      shouldSpliceIn = false;
    }
    this.previousExtractor = previousExtractor;
    initDataSource = dataSource;
    uid = uidSource.getAndIncrement();
  }

  /**
   * Initializes the chunk for loading, setting the {@link HlsSampleStreamWrapper} that will receive
   * samples as they are loaded.
   *
   * @param output The output that will receive the loaded samples.
   */
  public void init(HlsSampleStreamWrapper output) {
    this.output = output;
  }

  @Override
  public boolean isLoadCompleted() {
    return loadCompleted;
  }

  // Loadable implementation

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    maybeLoadInitData();
    if (!loadCanceled) {
      if (!hasGapTag) {
        loadMedia();
      }
      loadCompleted = true;
    }
  }

  // Internal methods.

  private void maybeLoadInitData() throws IOException, InterruptedException {
    if (initLoadCompleted || initDataSpec == null) {
      // Note: The HLS spec forbids initialization segments for packed audio.
      return;
    }
    DataSpec initSegmentDataSpec = initDataSpec.subrange(initSegmentBytesLoaded);
    try {
      DefaultExtractorInput input = prepareExtraction(initDataSource, initSegmentDataSpec);
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        initSegmentBytesLoaded = (int) (input.getPosition() - initDataSpec.absoluteStreamPosition);
      }
    } finally {
      Util.closeQuietly(initDataSource);
    }
    initLoadCompleted = true;
  }

  private void loadMedia() throws IOException, InterruptedException {
    // If we previously fed part of this chunk to the extractor, we need to skip it this time. For
    // encrypted content we need to skip the data by reading it through the source, so as to ensure
    // correct decryption of the remainder of the chunk. For clear content, we can request the
    // remainder of the chunk directly.
    DataSpec loadDataSpec;
    boolean skipLoadedBytes;
    if (isEncrypted) {
      loadDataSpec = dataSpec;
      skipLoadedBytes = nextLoadPosition != 0;
    } else {
      loadDataSpec = dataSpec.subrange(nextLoadPosition);
      skipLoadedBytes = false;
    }
    if (!isMasterTimestampSource) {
      timestampAdjuster.waitUntilInitialized();
    } else if (timestampAdjuster.getFirstSampleTimestampUs() == TimestampAdjuster.DO_NOT_OFFSET) {
      // We're the master and we haven't set the desired first sample timestamp yet.
      timestampAdjuster.setFirstSampleTimestampUs(startTimeUs);
    }
    try {
      ExtractorInput input = prepareExtraction(dataSource, loadDataSpec);
      if (isPackedAudioExtractor && !id3TimestampPeeked) {
        long id3Timestamp = peekId3PrivTimestamp(input);
        id3TimestampPeeked = true;
        output.setSampleOffsetUs(id3Timestamp != C.TIME_UNSET
            ? timestampAdjuster.adjustTsTimestamp(id3Timestamp) : startTimeUs);
      }
      if (skipLoadedBytes) {
        input.skipFully(nextLoadPosition);
      }
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        nextLoadPosition = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
  }

  private DefaultExtractorInput prepareExtraction(DataSource dataSource, DataSpec dataSpec)
      throws IOException {
    long bytesToRead = dataSource.open(dataSpec);

    if (extractor == null) {
      Pair<Extractor, Boolean> extractorData =
          extractorFactory.createExtractor(
              previousExtractor,
              dataSpec.uri,
              trackFormat,
              muxedCaptionFormats,
              drmInitData,
              timestampAdjuster,
              dataSource.getResponseHeaders());
      extractor = extractorData.first;
      isPackedAudioExtractor = extractorData.second;
      boolean reusingExtractor = extractor == previousExtractor;
      initLoadCompleted = reusingExtractor && initDataSpec != null;
      if (isPackedAudioExtractor && id3Data == null) {
        id3Decoder = new Id3Decoder();
        id3Data = new ParsableByteArray(Id3Decoder.ID3_HEADER_LENGTH);
      }
      output.init(uid, shouldSpliceIn, reusingExtractor);
      if (!reusingExtractor) {
        extractor.init(output);
      }
    }

    return new DefaultExtractorInput(dataSource, dataSpec.absoluteStreamPosition, bytesToRead);
  }

  /**
   * Peek the presentation timestamp of the first sample in the chunk from an ID3 PRIV as defined
   * in the HLS spec, version 20, Section 3.4. Returns {@link C#TIME_UNSET} if the frame is not
   * found. This method only modifies the peek position.
   *
   * @param input The {@link ExtractorInput} to obtain the PRIV frame from.
   * @return The parsed, adjusted timestamp in microseconds
   * @throws IOException If an error occurred peeking from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  private long peekId3PrivTimestamp(ExtractorInput input) throws IOException, InterruptedException {
    input.resetPeekPosition();
    if (!input.peekFully(id3Data.data, 0, Id3Decoder.ID3_HEADER_LENGTH, true)) {
      return C.TIME_UNSET;
    }
    id3Data.reset(Id3Decoder.ID3_HEADER_LENGTH);
    int id = id3Data.readUnsignedInt24();
    if (id != Id3Decoder.ID3_TAG) {
      return C.TIME_UNSET;
    }
    id3Data.skipBytes(3); // version(2), flags(1).
    int id3Size = id3Data.readSynchSafeInt();
    int requiredCapacity = id3Size + Id3Decoder.ID3_HEADER_LENGTH;
    if (requiredCapacity > id3Data.capacity()) {
      byte[] data = id3Data.data;
      id3Data.reset(requiredCapacity);
      System.arraycopy(data, 0, id3Data.data, 0, Id3Decoder.ID3_HEADER_LENGTH);
    }
    if (!input.peekFully(id3Data.data, Id3Decoder.ID3_HEADER_LENGTH, id3Size, true)) {
      return C.TIME_UNSET;
    }
    Metadata metadata = id3Decoder.decode(id3Data.data, id3Size);
    if (metadata == null) {
      return C.TIME_UNSET;
    }
    int metadataLength = metadata.length();
    for (int i = 0; i < metadataLength; i++) {
      Metadata.Entry frame = metadata.get(i);
      if (frame instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) frame;
        if (PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
          System.arraycopy(privFrame.privateData, 0, id3Data.data, 0, 8 /* timestamp size */);
          id3Data.reset(8);
          // The top 31 bits should be zeros, but explicitly zero them to wrap in the case that the
          // streaming provider forgot. See: https://github.com/google/ExoPlayer/pull/3495.
          return id3Data.readLong() & 0x1FFFFFFFFL;
        }
      }
    }
    return C.TIME_UNSET;
  }

  // Internal factory methods.

  /**
   * If the segment is fully encrypted, returns an {@link Aes128DataSource} that wraps the original
   * in order to decrypt the loaded data. Else returns the original.
   */
  private static DataSource buildDataSource(DataSource dataSource, byte[] fullSegmentEncryptionKey,
      byte[] encryptionIv) {
    if (fullSegmentEncryptionKey != null) {
      return new Aes128DataSource(dataSource, fullSegmentEncryptionKey, encryptionIv);
    }
    return dataSource;
  }

}
