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
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.extractor.DefaultExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.mp3.Mp3Extractor;
import org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import org.telegram.messenger.exoplayer2.extractor.ts.Ac3Extractor;
import org.telegram.messenger.exoplayer2.extractor.ts.AdtsExtractor;
import org.telegram.messenger.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import org.telegram.messenger.exoplayer2.extractor.ts.TsExtractor;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.metadata.id3.Id3Decoder;
import org.telegram.messenger.exoplayer2.metadata.id3.PrivFrame;
import org.telegram.messenger.exoplayer2.source.chunk.MediaChunk;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.TimestampAdjuster;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HLS {@link MediaChunk}.
 */
/* package */ final class HlsMediaChunk extends MediaChunk {

  private static final AtomicInteger UID_SOURCE = new AtomicInteger();

  private static final String PRIV_TIMESTAMP_FRAME_OWNER =
      "com.apple.streaming.transportStreamTimestamp";

  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final String AC3_FILE_EXTENSION = ".ac3";
  private static final String EC3_FILE_EXTENSION = ".ec3";
  private static final String MP3_FILE_EXTENSION = ".mp3";
  private static final String MP4_FILE_EXTENSION = ".mp4";
  private static final String M4_FILE_EXTENSION_PREFIX = ".m4";
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
  private final String lastPathSegment;
  private final Extractor previousExtractor;
  private final boolean shouldSpliceIn;
  private final boolean needNewExtractor;
  private final List<Format> muxedCaptionFormats;

  private final boolean isPackedAudio;
  private final Id3Decoder id3Decoder;
  private final ParsableByteArray id3Data;

  private Extractor extractor;
  private int initSegmentBytesLoaded;
  private int bytesLoaded;
  private boolean initLoadCompleted;
  private HlsSampleStreamWrapper extractorOutput;
  private volatile boolean loadCanceled;
  private volatile boolean loadCompleted;

  /**
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
   * @param chunkIndex The media sequence number of the chunk.
   * @param discontinuitySequenceNumber The discontinuity sequence number of the chunk.
   * @param isMasterTimestampSource True if the chunk can initialize the timestamp adjuster.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param previousChunk The {@link HlsMediaChunk} that preceded this one. May be null.
   * @param encryptionKey For AES encryption chunks, the encryption key.
   * @param encryptionIv For AES encryption chunks, the encryption initialization vector.
   */
  public HlsMediaChunk(DataSource dataSource, DataSpec dataSpec, DataSpec initDataSpec,
      HlsUrl hlsUrl, List<Format> muxedCaptionFormats, int trackSelectionReason,
      Object trackSelectionData, long startTimeUs, long endTimeUs, int chunkIndex,
      int discontinuitySequenceNumber, boolean isMasterTimestampSource,
      TimestampAdjuster timestampAdjuster, HlsMediaChunk previousChunk, byte[] encryptionKey,
      byte[] encryptionIv) {
    super(buildDataSource(dataSource, encryptionKey, encryptionIv), dataSpec, hlsUrl.format,
        trackSelectionReason, trackSelectionData, startTimeUs, endTimeUs, chunkIndex);
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.initDataSpec = initDataSpec;
    this.hlsUrl = hlsUrl;
    this.muxedCaptionFormats = muxedCaptionFormats;
    this.isMasterTimestampSource = isMasterTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    // Note: this.dataSource and dataSource may be different.
    this.isEncrypted = this.dataSource instanceof Aes128DataSource;
    lastPathSegment = dataSpec.uri.getLastPathSegment();
    isPackedAudio = lastPathSegment.endsWith(AAC_FILE_EXTENSION)
        || lastPathSegment.endsWith(AC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(EC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(MP3_FILE_EXTENSION);
    if (previousChunk != null) {
      id3Decoder = previousChunk.id3Decoder;
      id3Data = previousChunk.id3Data;
      previousExtractor = previousChunk.extractor;
      shouldSpliceIn = previousChunk.hlsUrl != hlsUrl;
      needNewExtractor = previousChunk.discontinuitySequenceNumber != discontinuitySequenceNumber
          || shouldSpliceIn;
    } else {
      id3Decoder = isPackedAudio ? new Id3Decoder() : null;
      id3Data = isPackedAudio ? new ParsableByteArray(Id3Decoder.ID3_HEADER_LENGTH) : null;
      previousExtractor = null;
      shouldSpliceIn = false;
      needNewExtractor = true;
    }
    initDataSource = dataSource;
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
    output.init(uid, shouldSpliceIn);
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
    if (extractor == null && !isPackedAudio) {
      // See HLS spec, version 20, Section 3.4 for more information on packed audio extraction.
      extractor = createExtractor();
    }
    maybeLoadInitData();
    if (!loadCanceled) {
      loadMedia();
    }
  }

  // Internal loading methods.

  private void maybeLoadInitData() throws IOException, InterruptedException {
    if (previousExtractor == extractor || initLoadCompleted || initDataSpec == null) {
      // According to spec, for packed audio, initDataSpec is expected to be null.
      return;
    }
    DataSpec initSegmentDataSpec = initDataSpec.subrange(initSegmentBytesLoaded);
    try {
      ExtractorInput input = new DefaultExtractorInput(initDataSource,
          initSegmentDataSpec.absoluteStreamPosition, initDataSource.open(initSegmentDataSpec));
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
        }
      } finally {
        initSegmentBytesLoaded = (int) (input.getPosition() - initDataSpec.absoluteStreamPosition);
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
      loadDataSpec = dataSpec.subrange(bytesLoaded);
      skipLoadedBytes = false;
    }
    if (!isMasterTimestampSource) {
      timestampAdjuster.waitUntilInitialized();
    } else if (timestampAdjuster.getFirstSampleTimestampUs() == TimestampAdjuster.DO_NOT_OFFSET) {
      // We're the master and we haven't set the desired first sample timestamp yet.
      timestampAdjuster.setFirstSampleTimestampUs(startTimeUs);
    }
    try {
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (extractor == null) {
        // Media segment format is packed audio.
        long id3Timestamp = peekId3PrivTimestamp(input);
        extractor = buildPackedAudioExtractor(id3Timestamp != C.TIME_UNSET
            ? timestampAdjuster.adjustTsTimestamp(id3Timestamp) : startTimeUs);
      }
      if (skipLoadedBytes) {
        input.skipFully(bytesLoaded);
      }
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, null);
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
          return id3Data.readLong();
        }
      }
    }
    return C.TIME_UNSET;
  }

  // Internal factory methods.

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

  private Extractor createExtractor() {
    // Select the extractor that will read the chunk.
    Extractor extractor;
    boolean usingNewExtractor = true;
    if (MimeTypes.TEXT_VTT.equals(hlsUrl.format.sampleMimeType)
        || lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      extractor = new WebvttExtractor(trackFormat.language, timestampAdjuster);
    } else if (!needNewExtractor) {
      // Only reuse TS and fMP4 extractors.
      usingNewExtractor = false;
      extractor = previousExtractor;
    } else if (lastPathSegment.endsWith(MP4_FILE_EXTENSION)
        || lastPathSegment.startsWith(M4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 4)) {
      extractor = new FragmentedMp4Extractor(0, timestampAdjuster);
    } else {
      // MPEG-2 TS segments, but we need a new extractor.
      // This flag ensures the change of pid between streams does not affect the sample queues.
      @DefaultTsPayloadReaderFactory.Flags
      int esReaderFactoryFlags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM;
      List<Format> closedCaptionFormats = muxedCaptionFormats;
      if (closedCaptionFormats != null) {
        // The playlist declares closed caption renditions, we should ignore descriptors.
        esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
      } else {
        closedCaptionFormats = Collections.emptyList();
      }
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
      extractor = new TsExtractor(TsExtractor.MODE_HLS, timestampAdjuster,
          new DefaultTsPayloadReaderFactory(esReaderFactoryFlags, closedCaptionFormats));
    }
    if (usingNewExtractor) {
      extractor.init(extractorOutput);
    }
    return extractor;
  }

  private Extractor buildPackedAudioExtractor(long startTimeUs) {
    Extractor extractor;
    if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      extractor = new AdtsExtractor(startTimeUs);
    } else if (lastPathSegment.endsWith(AC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(EC3_FILE_EXTENSION)) {
      extractor = new Ac3Extractor(startTimeUs);
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      extractor = new Mp3Extractor(0, startTimeUs);
    } else {
      throw new IllegalArgumentException("Unknown extension for audio file: " + lastPathSegment);
    }
    extractor.init(extractorOutput);
    return extractor;
  }

}
