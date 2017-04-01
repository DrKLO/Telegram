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

import android.text.TextUtils;
import org.telegram.messenger.exoplayer2.extractor.DefaultExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.TimestampAdjuster;
import org.telegram.messenger.exoplayer2.extractor.mp3.Mp3Extractor;
import org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import org.telegram.messenger.exoplayer2.extractor.ts.Ac3Extractor;
import org.telegram.messenger.exoplayer2.extractor.ts.AdtsExtractor;
import org.telegram.messenger.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import org.telegram.messenger.exoplayer2.extractor.ts.TsExtractor;
import org.telegram.messenger.exoplayer2.source.chunk.MediaChunk;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HLS {@link MediaChunk}.
 */
/* package */ final class HlsMediaChunk extends MediaChunk {

  private static final AtomicInteger UID_SOURCE = new AtomicInteger();

  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final String AC3_FILE_EXTENSION = ".ac3";
  private static final String EC3_FILE_EXTENSION = ".ec3";
  private static final String MP3_FILE_EXTENSION = ".mp3";
  private static final String MP4_FILE_EXTENSION = ".mp4";
  private static final String VTT_FILE_EXTENSION = ".vtt";
  private static final String WEBVTT_FILE_EXTENSION = ".webvtt";

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
  private final TimestampAdjuster timestampAdjuster;
  private final HlsMediaChunk previousChunk;

  private Extractor extractor;
  private int initSegmentBytesLoaded;
  private int bytesLoaded;
  private boolean initLoadCompleted;
  private HlsSampleStreamWrapper extractorOutput;
  private long adjustedEndTimeUs;
  private volatile boolean loadCanceled;
  private volatile boolean loadCompleted;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param initDataSpec Defines the initialization data to be fed to new extractors. May be null.
   * @param hlsUrl The url of the playlist from which this chunk was obtained.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param segment The {@link Segment} for which this media chunk is created.
   * @param chunkIndex The media sequence number of the chunk.
   * @param isMasterTimestampSource True if the chunk can initialize the timestamp adjuster.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param previousChunk The {@link HlsMediaChunk} that preceded this one. May be null.
   * @param encryptionKey For AES encryption chunks, the encryption key.
   * @param encryptionIv For AES encryption chunks, the encryption initialization vector.
   */
  public HlsMediaChunk(DataSource dataSource, DataSpec dataSpec, DataSpec initDataSpec,
      HlsUrl hlsUrl, int trackSelectionReason, Object trackSelectionData, Segment segment,
      int chunkIndex, boolean isMasterTimestampSource, TimestampAdjuster timestampAdjuster,
      HlsMediaChunk previousChunk, byte[] encryptionKey, byte[] encryptionIv) {
    super(buildDataSource(dataSource, encryptionKey, encryptionIv), dataSpec, hlsUrl.format,
        trackSelectionReason, trackSelectionData, segment.startTimeUs,
        segment.startTimeUs + segment.durationUs, chunkIndex);
    this.initDataSpec = initDataSpec;
    this.hlsUrl = hlsUrl;
    this.isMasterTimestampSource = isMasterTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    this.previousChunk = previousChunk;
    // Note: this.dataSource and dataSource may be different.
    this.isEncrypted = this.dataSource instanceof Aes128DataSource;
    initDataSource = dataSource;
    discontinuitySequenceNumber = segment.discontinuitySequenceNumber;
    adjustedEndTimeUs = endTimeUs;
    uid = UID_SOURCE.getAndIncrement();
  }

  /**
   * Initializes the chunk for loading, setting the {@link HlsSampleStreamWrapper} that will receive
   * samples as they are loaded.
   *
   * @param output The output that will receive the loaded samples.
   */
  public void init(HlsSampleStreamWrapper output) {
    extractorOutput = output;
    output.init(uid, previousChunk != null && previousChunk.hlsUrl != hlsUrl);
  }

  /**
   * Returns the presentation time in microseconds of the first sample in the chunk.
   */
  public long getAdjustedStartTimeUs() {
    return adjustedEndTimeUs - getDurationUs();
  }

  /**
   * Returns the presentation time in microseconds of the last sample in the chunk
   */
  public long getAdjustedEndTimeUs() {
    return adjustedEndTimeUs;
  }

  @Override
  public boolean isLoadCompleted() {
    return loadCompleted;
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
    if (extractor == null) {
      extractor = buildExtractor();
    }
    maybeLoadInitData();
    if (!loadCanceled) {
      loadMedia();
    }
  }

  // Private methods.

  private Extractor buildExtractor() {
    // Set the extractor that will read the chunk.
    Extractor extractor;
    boolean needNewExtractor = previousChunk == null
        || previousChunk.discontinuitySequenceNumber != discontinuitySequenceNumber
        || trackFormat != previousChunk.trackFormat;
    boolean usingNewExtractor = true;
    String lastPathSegment = dataSpec.uri.getLastPathSegment();
    if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      // TODO: Inject a timestamp adjuster and use it along with ID3 PRIV tag values with owner
      // identifier com.apple.streaming.transportStreamTimestamp. This may also apply to the MP3
      // case below.
      extractor = new AdtsExtractor(startTimeUs);
    } else if (lastPathSegment.endsWith(AC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(EC3_FILE_EXTENSION)) {
      extractor = new Ac3Extractor(startTimeUs);
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      extractor = new Mp3Extractor(startTimeUs);
    } else if (lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      extractor = new WebvttExtractor(trackFormat.language, timestampAdjuster);
    } else if (!needNewExtractor) {
      // Only reuse TS and fMP4 extractors.
      usingNewExtractor = false;
      extractor = previousChunk.extractor;
    } else if (lastPathSegment.endsWith(MP4_FILE_EXTENSION)) {
      extractor = new FragmentedMp4Extractor(0, timestampAdjuster);
    } else {
      // MPEG-2 TS segments, but we need a new extractor.
      // This flag ensures the change of pid between streams does not affect the sample queues.
      @DefaultTsPayloadReaderFactory.Flags
      int esReaderFactoryFlags = 0;
      String codecs = trackFormat.codecs;
      if (!TextUtils.isEmpty(codecs)) {
        // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
        // exist. If we know from the codec attribute that they don't exist, then we can
        // explicitly ignore them even if they're declared.
        if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
          esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
        }
        if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
          esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
        }
      }
      extractor = new TsExtractor(timestampAdjuster,
          new DefaultTsPayloadReaderFactory(esReaderFactoryFlags), true);
    }
    if (usingNewExtractor) {
      extractor.init(extractorOutput);
    }
    return extractor;
  }

  private void maybeLoadInitData() throws IOException, InterruptedException {
    if (previousChunk == null || previousChunk.extractor != extractor || initLoadCompleted
        || initDataSpec == null) {
      return;
    }
    DataSpec initSegmentDataSpec = Util.getRemainderDataSpec(initDataSpec, initSegmentBytesLoaded);
    try {
      ExtractorInput input = new DefaultExtractorInput(initDataSource,
          initSegmentDataSpec.absoluteStreamPosition, initDataSource.open(initSegmentDataSpec));
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        initSegmentBytesLoaded += (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      Util.closeQuietly(dataSource);
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
        if (!isMasterTimestampSource && timestampAdjuster != null) {
          timestampAdjuster.waitUntilInitialized();
        }
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
        long adjustedEndTimeUs = extractorOutput.getLargestQueuedTimestampUs();
        if (adjustedEndTimeUs != Long.MIN_VALUE) {
          this.adjustedEndTimeUs = adjustedEndTimeUs;
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
    loadCompleted = true;
  }

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
