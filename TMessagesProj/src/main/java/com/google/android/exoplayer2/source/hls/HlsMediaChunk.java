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

import static com.google.android.exoplayer2.upstream.DataSpec.FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.UriUtil;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** An HLS {@link MediaChunk}. */
/* package */ final class HlsMediaChunk extends MediaChunk {

  /**
   * Creates a new instance.
   *
   * @param extractorFactory A {@link HlsExtractorFactory} from which the {@link
   *     HlsMediaChunkExtractor} is obtained.
   * @param dataSource The source from which the data should be loaded.
   * @param format The chunk format.
   * @param startOfPlaylistInPeriodUs The position of the playlist in the period in microseconds.
   * @param mediaPlaylist The media playlist from which this chunk was obtained.
   * @param segmentBaseHolder The segment holder.
   * @param playlistUrl The url of the playlist from which this chunk was obtained.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the multivariant playlist.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param isMasterTimestampSource True if the chunk can initialize the timestamp adjuster.
   * @param timestampAdjusterProvider The provider from which to obtain the {@link
   *     TimestampAdjuster}.
   * @param previousChunk The {@link HlsMediaChunk} that preceded this one. May be null.
   * @param mediaSegmentKey The media segment decryption key, if fully encrypted. Null otherwise.
   * @param initSegmentKey The initialization segment decryption key, if fully encrypted. Null
   *     otherwise.
   * @param shouldSpliceIn Whether samples for this chunk should be spliced into existing samples.
   */
  public static HlsMediaChunk createInstance(
      HlsExtractorFactory extractorFactory,
      DataSource dataSource,
      Format format,
      long startOfPlaylistInPeriodUs,
      HlsMediaPlaylist mediaPlaylist,
      HlsChunkSource.SegmentBaseHolder segmentBaseHolder,
      Uri playlistUrl,
      @Nullable List<Format> muxedCaptionFormats,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      boolean isMasterTimestampSource,
      TimestampAdjusterProvider timestampAdjusterProvider,
      @Nullable HlsMediaChunk previousChunk,
      @Nullable byte[] mediaSegmentKey,
      @Nullable byte[] initSegmentKey,
      boolean shouldSpliceIn,
      PlayerId playerId) {
    // Media segment.
    HlsMediaPlaylist.SegmentBase mediaSegment = segmentBaseHolder.segmentBase;
    DataSpec dataSpec =
        new DataSpec.Builder()
            .setUri(UriUtil.resolveToUri(mediaPlaylist.baseUri, mediaSegment.url))
            .setPosition(mediaSegment.byteRangeOffset)
            .setLength(mediaSegment.byteRangeLength)
            .setFlags(segmentBaseHolder.isPreload ? FLAG_MIGHT_NOT_USE_FULL_NETWORK_SPEED : 0)
            .build();
    boolean mediaSegmentEncrypted = mediaSegmentKey != null;
    @Nullable
    byte[] mediaSegmentIv =
        mediaSegmentEncrypted
            ? getEncryptionIvArray(Assertions.checkNotNull(mediaSegment.encryptionIV))
            : null;
    DataSource mediaDataSource = buildDataSource(dataSource, mediaSegmentKey, mediaSegmentIv);

    // Init segment.
    HlsMediaPlaylist.Segment initSegment = mediaSegment.initializationSegment;
    DataSpec initDataSpec = null;
    boolean initSegmentEncrypted = false;
    @Nullable DataSource initDataSource = null;
    if (initSegment != null) {
      initSegmentEncrypted = initSegmentKey != null;
      @Nullable
      byte[] initSegmentIv =
          initSegmentEncrypted
              ? getEncryptionIvArray(Assertions.checkNotNull(initSegment.encryptionIV))
              : null;
      Uri initSegmentUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, initSegment.url);
      initDataSpec =
          new DataSpec(initSegmentUri, initSegment.byteRangeOffset, initSegment.byteRangeLength);
      initDataSource = buildDataSource(dataSource, initSegmentKey, initSegmentIv);
    }

    long segmentStartTimeInPeriodUs = startOfPlaylistInPeriodUs + mediaSegment.relativeStartTimeUs;
    long segmentEndTimeInPeriodUs = segmentStartTimeInPeriodUs + mediaSegment.durationUs;
    int discontinuitySequenceNumber =
        mediaPlaylist.discontinuitySequence + mediaSegment.relativeDiscontinuitySequence;

    @Nullable HlsMediaChunkExtractor previousExtractor = null;
    Id3Decoder id3Decoder;
    ParsableByteArray scratchId3Data;

    if (previousChunk != null) {
      boolean isSameInitData =
          initDataSpec == previousChunk.initDataSpec
              || (initDataSpec != null
                  && previousChunk.initDataSpec != null
                  && initDataSpec.uri.equals(previousChunk.initDataSpec.uri)
                  && initDataSpec.position == previousChunk.initDataSpec.position);
      boolean isFollowingChunk =
          playlistUrl.equals(previousChunk.playlistUrl) && previousChunk.loadCompleted;
      id3Decoder = previousChunk.id3Decoder;
      scratchId3Data = previousChunk.scratchId3Data;
      previousExtractor =
          isSameInitData
                  && isFollowingChunk
                  && !previousChunk.extractorInvalidated
                  && previousChunk.discontinuitySequenceNumber == discontinuitySequenceNumber
              ? previousChunk.extractor
              : null;
    } else {
      id3Decoder = new Id3Decoder();
      scratchId3Data = new ParsableByteArray(Id3Decoder.ID3_HEADER_LENGTH);
    }
    return new HlsMediaChunk(
        extractorFactory,
        mediaDataSource,
        dataSpec,
        format,
        mediaSegmentEncrypted,
        initDataSource,
        initDataSpec,
        initSegmentEncrypted,
        playlistUrl,
        muxedCaptionFormats,
        trackSelectionReason,
        trackSelectionData,
        segmentStartTimeInPeriodUs,
        segmentEndTimeInPeriodUs,
        segmentBaseHolder.mediaSequence,
        segmentBaseHolder.partIndex,
        /* isPublished= */ !segmentBaseHolder.isPreload,
        discontinuitySequenceNumber,
        mediaSegment.hasGapTag,
        isMasterTimestampSource,
        /* timestampAdjuster= */ timestampAdjusterProvider.getAdjuster(discontinuitySequenceNumber),
        mediaSegment.drmInitData,
        previousExtractor,
        id3Decoder,
        scratchId3Data,
        shouldSpliceIn,
        playerId);
  }

  /**
   * Returns whether samples of a new HLS media chunk should be spliced into existing samples.
   *
   * @param previousChunk The previous existing media chunk, or null if the new chunk is the first
   *     in the queue.
   * @param playlistUrl The URL of the playlist from which the new chunk will be obtained.
   * @param mediaPlaylist The {@link HlsMediaPlaylist} containing the new chunk.
   * @param segmentBaseHolder The {@link HlsChunkSource.SegmentBaseHolder} with information about
   *     the new chunk.
   * @param startOfPlaylistInPeriodUs The start time of the playlist in the period, in microseconds.
   * @return Whether samples of the new chunk should be spliced into existing samples.
   */
  public static boolean shouldSpliceIn(
      @Nullable HlsMediaChunk previousChunk,
      Uri playlistUrl,
      HlsMediaPlaylist mediaPlaylist,
      HlsChunkSource.SegmentBaseHolder segmentBaseHolder,
      long startOfPlaylistInPeriodUs) {
    if (previousChunk == null) {
      // First chunk doesn't require splicing.
      return false;
    }
    if (playlistUrl.equals(previousChunk.playlistUrl) && previousChunk.loadCompleted) {
      // Continuing with the next chunk in the same playlist after fully loading the previous chunk
      // (i.e. the load wasn't cancelled or failed) is always possible.
      return false;
    }
    // Changing playlists or continuing after a chunk cancellation/failure requires independent,
    // non-overlapping segments to avoid the splice.
    long segmentStartTimeInPeriodUs =
        startOfPlaylistInPeriodUs + segmentBaseHolder.segmentBase.relativeStartTimeUs;
    return !isIndependent(segmentBaseHolder, mediaPlaylist)
        || segmentStartTimeInPeriodUs < previousChunk.endTimeUs;
  }

  public static final String PRIV_TIMESTAMP_FRAME_OWNER =
      "com.apple.streaming.transportStreamTimestamp";

  private static final AtomicInteger uidSource = new AtomicInteger();

  /** A unique identifier for the chunk. */
  public final int uid;

  /** The discontinuity sequence number of the chunk. */
  public final int discontinuitySequenceNumber;

  /** The url of the playlist from which this chunk was obtained. */
  public final Uri playlistUrl;

  /** Whether samples for this chunk should be spliced into existing samples. */
  public final boolean shouldSpliceIn;

  /** The part index or {@link C#INDEX_UNSET} if the chunk is a full segment */
  public final int partIndex;

  @Nullable private final DataSource initDataSource;
  @Nullable private final DataSpec initDataSpec;
  @Nullable private final HlsMediaChunkExtractor previousExtractor;

  private final boolean isMasterTimestampSource;
  private final boolean hasGapTag;
  private final TimestampAdjuster timestampAdjuster;
  private final HlsExtractorFactory extractorFactory;
  @Nullable private final List<Format> muxedCaptionFormats;
  @Nullable private final DrmInitData drmInitData;
  private final Id3Decoder id3Decoder;
  private final ParsableByteArray scratchId3Data;
  private final boolean mediaSegmentEncrypted;
  private final boolean initSegmentEncrypted;
  private final PlayerId playerId;

  private @MonotonicNonNull HlsMediaChunkExtractor extractor;
  private @MonotonicNonNull HlsSampleStreamWrapper output;
  // nextLoadPosition refers to the init segment if initDataLoadRequired is true.
  // Otherwise, nextLoadPosition refers to the media segment.
  private int nextLoadPosition;
  private boolean initDataLoadRequired;
  private volatile boolean loadCanceled;
  private boolean loadCompleted;
  private ImmutableList<Integer> sampleQueueFirstSampleIndices;
  private boolean extractorInvalidated;
  private boolean isPublished;

  private HlsMediaChunk(
      HlsExtractorFactory extractorFactory,
      DataSource mediaDataSource,
      DataSpec dataSpec,
      Format format,
      boolean mediaSegmentEncrypted,
      @Nullable DataSource initDataSource,
      @Nullable DataSpec initDataSpec,
      boolean initSegmentEncrypted,
      Uri playlistUrl,
      @Nullable List<Format> muxedCaptionFormats,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      long startTimeUs,
      long endTimeUs,
      long chunkMediaSequence,
      int partIndex,
      boolean isPublished,
      int discontinuitySequenceNumber,
      boolean hasGapTag,
      boolean isMasterTimestampSource,
      TimestampAdjuster timestampAdjuster,
      @Nullable DrmInitData drmInitData,
      @Nullable HlsMediaChunkExtractor previousExtractor,
      Id3Decoder id3Decoder,
      ParsableByteArray scratchId3Data,
      boolean shouldSpliceIn,
      PlayerId playerId) {
    super(
        mediaDataSource,
        dataSpec,
        format,
        trackSelectionReason,
        trackSelectionData,
        startTimeUs,
        endTimeUs,
        chunkMediaSequence);
    this.mediaSegmentEncrypted = mediaSegmentEncrypted;
    this.partIndex = partIndex;
    this.isPublished = isPublished;
    this.discontinuitySequenceNumber = discontinuitySequenceNumber;
    this.initDataSpec = initDataSpec;
    this.initDataSource = initDataSource;
    this.initDataLoadRequired = initDataSpec != null;
    this.initSegmentEncrypted = initSegmentEncrypted;
    this.playlistUrl = playlistUrl;
    this.isMasterTimestampSource = isMasterTimestampSource;
    this.timestampAdjuster = timestampAdjuster;
    this.hasGapTag = hasGapTag;
    this.extractorFactory = extractorFactory;
    this.muxedCaptionFormats = muxedCaptionFormats;
    this.drmInitData = drmInitData;
    this.previousExtractor = previousExtractor;
    this.id3Decoder = id3Decoder;
    this.scratchId3Data = scratchId3Data;
    this.shouldSpliceIn = shouldSpliceIn;
    this.playerId = playerId;
    sampleQueueFirstSampleIndices = ImmutableList.of();
    uid = uidSource.getAndIncrement();
  }

  /**
   * Initializes the chunk for loading.
   *
   * @param output The {@link HlsSampleStreamWrapper} that will receive the loaded samples.
   * @param sampleQueueWriteIndices The current write indices in the existing sample queues of the
   *     output.
   */
  public void init(HlsSampleStreamWrapper output, ImmutableList<Integer> sampleQueueWriteIndices) {
    this.output = output;
    this.sampleQueueFirstSampleIndices = sampleQueueWriteIndices;
  }

  /**
   * Returns the first sample index of this chunk in the specified sample queue in the output.
   *
   * <p>Must not be used if {@link #shouldSpliceIn} is true.
   *
   * @param sampleQueueIndex The index of the sample queue in the output.
   * @return The first sample index of this chunk in the specified sample queue.
   */
  public int getFirstSampleIndex(int sampleQueueIndex) {
    Assertions.checkState(!shouldSpliceIn);
    if (sampleQueueIndex >= sampleQueueFirstSampleIndices.size()) {
      // The sample queue was created by this chunk or a later chunk.
      return 0;
    }
    return sampleQueueFirstSampleIndices.get(sampleQueueIndex);
  }

  /** Prevents the extractor from being reused by a following media chunk. */
  public void invalidateExtractor() {
    extractorInvalidated = true;
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
  public void load() throws IOException {
    // output == null means init() hasn't been called.
    Assertions.checkNotNull(output);
    if (extractor == null && previousExtractor != null && previousExtractor.isReusable()) {
      extractor = previousExtractor;
      initDataLoadRequired = false;
    }
    maybeLoadInitData();
    if (!loadCanceled) {
      if (!hasGapTag) {
        loadMedia();
      }
      loadCompleted = !loadCanceled;
    }
  }

  /**
   * Whether the chunk is a published chunk as opposed to a preload hint that may change when the
   * playlist updates.
   */
  public boolean isPublished() {
    return isPublished;
  }

  /**
   * Sets the publish flag of the media chunk to indicate that it is not based on a part that is a
   * preload hint in the playlist.
   */
  public void publish() {
    isPublished = true;
  }

  // Internal methods.

  @RequiresNonNull("output")
  private void maybeLoadInitData() throws IOException {
    if (!initDataLoadRequired) {
      return;
    }
    // initDataLoadRequired =>  initDataSource != null && initDataSpec != null
    Assertions.checkNotNull(initDataSource);
    Assertions.checkNotNull(initDataSpec);
    feedDataToExtractor(
        initDataSource,
        initDataSpec,
        initSegmentEncrypted,
        /* initializeTimestampAdjuster= */ false);
    nextLoadPosition = 0;
    initDataLoadRequired = false;
  }

  @RequiresNonNull("output")
  private void loadMedia() throws IOException {
    feedDataToExtractor(
        dataSource, dataSpec, mediaSegmentEncrypted, /* initializeTimestampAdjuster= */ true);
  }

  /**
   * Attempts to feed the given {@code dataSpec} to {@code this.extractor}. Whenever the operation
   * concludes (because of a thrown exception or because the operation finishes), the number of fed
   * bytes is written to {@code nextLoadPosition}.
   */
  @RequiresNonNull("output")
  private void feedDataToExtractor(
      DataSource dataSource,
      DataSpec dataSpec,
      boolean dataIsEncrypted,
      boolean initializeTimestampAdjuster)
      throws IOException {
    // If we previously fed part of this chunk to the extractor, we need to skip it this time. For
    // encrypted content we need to skip the data by reading it through the source, so as to ensure
    // correct decryption of the remainder of the chunk. For clear content, we can request the
    // remainder of the chunk directly.
    DataSpec loadDataSpec;
    boolean skipLoadedBytes;
    if (dataIsEncrypted) {
      loadDataSpec = dataSpec;
      skipLoadedBytes = nextLoadPosition != 0;
    } else {
      loadDataSpec = dataSpec.subrange(nextLoadPosition);
      skipLoadedBytes = false;
    }
    try {
      ExtractorInput input =
          prepareExtraction(dataSource, loadDataSpec, initializeTimestampAdjuster);
      if (skipLoadedBytes) {
        input.skipFully(nextLoadPosition);
      }
      try {
        while (!loadCanceled && extractor.read(input)) {}
      } catch (EOFException e) {
        if ((trackFormat.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
          // See onTruncatedSegmentParsed's javadoc for more info on why we are swallowing the EOF
          // exception for trick play tracks.
          extractor.onTruncatedSegmentParsed();
        } else {
          throw e;
        }
      } finally {
        nextLoadPosition = (int) (input.getPosition() - dataSpec.position);
      }
    } finally {
      DataSourceUtil.closeQuietly(dataSource);
    }
  }

  @RequiresNonNull("output")
  @EnsuresNonNull("extractor")
  private DefaultExtractorInput prepareExtraction(
      DataSource dataSource, DataSpec dataSpec, boolean initializeTimestampAdjuster)
      throws IOException {
    long bytesToRead = dataSource.open(dataSpec);
    if (initializeTimestampAdjuster) {
      try {
        timestampAdjuster.sharedInitializeOrWait(isMasterTimestampSource, startTimeUs);
      } catch (InterruptedException e) {
        throw new InterruptedIOException();
      }
    }
    DefaultExtractorInput extractorInput =
        new DefaultExtractorInput(dataSource, dataSpec.position, bytesToRead);

    if (extractor == null) {
      long id3Timestamp = peekId3PrivTimestamp(extractorInput);
      extractorInput.resetPeekPosition();

      extractor =
          previousExtractor != null
              ? previousExtractor.recreate()
              : extractorFactory.createExtractor(
                  dataSpec.uri,
                  trackFormat,
                  muxedCaptionFormats,
                  timestampAdjuster,
                  dataSource.getResponseHeaders(),
                  extractorInput,
                  playerId);
      if (extractor.isPackedAudioExtractor()) {
        output.setSampleOffsetUs(
            id3Timestamp != C.TIME_UNSET
                ? timestampAdjuster.adjustTsTimestamp(id3Timestamp)
                : startTimeUs);
      } else {
        // In case the container format changes mid-stream to non-packed-audio, we need to reset
        // the timestamp offset.
        output.setSampleOffsetUs(/* sampleOffsetUs= */ 0L);
      }
      output.onNewExtractor();
      extractor.init(output);
    }
    output.setDrmInitData(drmInitData);
    return extractorInput;
  }

  /**
   * Peek the presentation timestamp of the first sample in the chunk from an ID3 PRIV as defined in
   * the HLS spec, version 20, Section 3.4. Returns {@link C#TIME_UNSET} if the frame is not found.
   * This method only modifies the peek position.
   *
   * @param input The {@link ExtractorInput} to obtain the PRIV frame from.
   * @return The parsed, adjusted timestamp in microseconds
   * @throws IOException If an error occurred peeking from the input.
   */
  private long peekId3PrivTimestamp(ExtractorInput input) throws IOException {
    input.resetPeekPosition();
    try {
      scratchId3Data.reset(Id3Decoder.ID3_HEADER_LENGTH);
      input.peekFully(scratchId3Data.getData(), 0, Id3Decoder.ID3_HEADER_LENGTH);
    } catch (EOFException e) {
      // The input isn't long enough for there to be any ID3 data.
      return C.TIME_UNSET;
    }
    int id = scratchId3Data.readUnsignedInt24();
    if (id != Id3Decoder.ID3_TAG) {
      return C.TIME_UNSET;
    }
    scratchId3Data.skipBytes(3); // version(2), flags(1).
    int id3Size = scratchId3Data.readSynchSafeInt();
    int requiredCapacity = id3Size + Id3Decoder.ID3_HEADER_LENGTH;
    if (requiredCapacity > scratchId3Data.capacity()) {
      byte[] data = scratchId3Data.getData();
      scratchId3Data.reset(requiredCapacity);
      System.arraycopy(data, 0, scratchId3Data.getData(), 0, Id3Decoder.ID3_HEADER_LENGTH);
    }
    input.peekFully(scratchId3Data.getData(), Id3Decoder.ID3_HEADER_LENGTH, id3Size);
    Metadata metadata = id3Decoder.decode(scratchId3Data.getData(), id3Size);
    if (metadata == null) {
      return C.TIME_UNSET;
    }
    int metadataLength = metadata.length();
    for (int i = 0; i < metadataLength; i++) {
      Metadata.Entry frame = metadata.get(i);
      if (frame instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) frame;
        if (PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
          System.arraycopy(
              privFrame.privateData, 0, scratchId3Data.getData(), 0, 8 /* timestamp size */);
          scratchId3Data.setPosition(0);
          scratchId3Data.setLimit(8);
          // The top 31 bits should be zeros, but explicitly zero them to wrap in the case that the
          // streaming provider forgot. See: https://github.com/google/ExoPlayer/pull/3495.
          return scratchId3Data.readLong() & 0x1FFFFFFFFL;
        }
      }
    }
    return C.TIME_UNSET;
  }

  // Internal methods.

  private static byte[] getEncryptionIvArray(String ivString) {
    String trimmedIv;
    if (Ascii.toLowerCase(ivString).startsWith("0x")) {
      trimmedIv = ivString.substring(2);
    } else {
      trimmedIv = ivString;
    }

    byte[] ivData = new BigInteger(trimmedIv, /* radix= */ 16).toByteArray();
    byte[] ivDataWithPadding = new byte[16];
    int offset = ivData.length > 16 ? ivData.length - 16 : 0;
    System.arraycopy(
        ivData,
        offset,
        ivDataWithPadding,
        ivDataWithPadding.length - ivData.length + offset,
        ivData.length - offset);
    return ivDataWithPadding;
  }

  /**
   * If the segment is fully encrypted, returns an {@link Aes128DataSource} that wraps the original
   * in order to decrypt the loaded data. Else returns the original.
   *
   * <p>{@code fullSegmentEncryptionKey} & {@code encryptionIv} can either both be null, or neither.
   */
  private static DataSource buildDataSource(
      DataSource dataSource,
      @Nullable byte[] fullSegmentEncryptionKey,
      @Nullable byte[] encryptionIv) {
    if (fullSegmentEncryptionKey != null) {
      Assertions.checkNotNull(encryptionIv);
      return new Aes128DataSource(dataSource, fullSegmentEncryptionKey, encryptionIv);
    }
    return dataSource;
  }

  private static boolean isIndependent(
      HlsChunkSource.SegmentBaseHolder segmentBaseHolder, HlsMediaPlaylist mediaPlaylist) {
    if (segmentBaseHolder.segmentBase instanceof HlsMediaPlaylist.Part) {
      return ((HlsMediaPlaylist.Part) segmentBaseHolder.segmentBase).isIndependent
          || (segmentBaseHolder.partIndex == 0 && mediaPlaylist.hasIndependentSegments);
    }
    return mediaPlaylist.hasIndependentSegments;
  }
}
