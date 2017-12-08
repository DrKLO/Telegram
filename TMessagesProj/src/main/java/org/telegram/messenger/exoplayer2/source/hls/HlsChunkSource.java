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

import android.net.Uri;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.source.BehindLiveWindowException;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.chunk.Chunk;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkedTrackBlacklistUtil;
import org.telegram.messenger.exoplayer2.source.chunk.DataChunk;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import org.telegram.messenger.exoplayer2.trackselection.BaseTrackSelection;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.util.TimestampAdjuster;
import org.telegram.messenger.exoplayer2.util.UriUtil;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * Source of Hls (possibly adaptive) chunks.
 */
/* package */ class HlsChunkSource {

  /**
   * Chunk holder that allows the scheduling of retries.
   */
  public static final class HlsChunkHolder {

    public HlsChunkHolder() {
      clear();
    }

    /**
     * The chunk to be loaded next.
     */
    public Chunk chunk;

    /**
     * Indicates that the end of the stream has been reached.
     */
    public boolean endOfStream;

    /**
     * Indicates that the chunk source is waiting for the referred playlist to be refreshed.
     */
    public HlsUrl playlist;

    /**
     * Clears the holder.
     */
    public void clear() {
      chunk = null;
      endOfStream = false;
      playlist = null;
    }

  }

  private final DataSource mediaDataSource;
  private final DataSource encryptionDataSource;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final HlsUrl[] variants;
  private final HlsPlaylistTracker playlistTracker;
  private final TrackGroup trackGroup;
  private final List<Format> muxedCaptionFormats;

  private boolean isTimestampMaster;
  private byte[] scratchSpace;
  private IOException fatalError;
  private HlsUrl expectedPlaylistUrl;
  private boolean independentSegments;

  private Uri encryptionKeyUri;
  private byte[] encryptionKey;
  private String encryptionIvString;
  private byte[] encryptionIv;

  // Note: The track group in the selection is typically *not* equal to trackGroup. This is due to
  // the way in which HlsSampleStreamWrapper generates track groups. Use only index based methods
  // in TrackSelection to avoid unexpected behavior.
  private TrackSelection trackSelection;

  /**
   * @param playlistTracker The {@link HlsPlaylistTracker} from which to obtain media playlists.
   * @param variants The available variants.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} to create {@link DataSource}s for the
   *     chunks.
   * @param timestampAdjusterProvider A provider of {@link TimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the master playlist.
   */
  public HlsChunkSource(HlsPlaylistTracker playlistTracker, HlsUrl[] variants,
      HlsDataSourceFactory dataSourceFactory, TimestampAdjusterProvider timestampAdjusterProvider,
      List<Format> muxedCaptionFormats) {
    this.playlistTracker = playlistTracker;
    this.variants = variants;
    this.timestampAdjusterProvider = timestampAdjusterProvider;
    this.muxedCaptionFormats = muxedCaptionFormats;
    Format[] variantFormats = new Format[variants.length];
    int[] initialTrackSelection = new int[variants.length];
    for (int i = 0; i < variants.length; i++) {
      variantFormats[i] = variants[i].format;
      initialTrackSelection[i] = i;
    }
    mediaDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_MEDIA);
    encryptionDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_DRM);
    trackGroup = new TrackGroup(variantFormats);
    trackSelection = new InitializationTrackSelection(trackGroup, initialTrackSelection);
  }

  /**
   * If the source is currently having difficulty providing chunks, then this method throws the
   * underlying error. Otherwise does nothing.
   *
   * @throws IOException The underlying error.
   */
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    }
    if (expectedPlaylistUrl != null) {
      playlistTracker.maybeThrowPlaylistRefreshError(expectedPlaylistUrl);
    }
  }

  /**
   * Returns the track group exposed by the source.
   */
  public TrackGroup getTrackGroup() {
    return trackGroup;
  }

  /**
   * Selects tracks for use.
   *
   * @param trackSelection The track selection.
   */
  public void selectTracks(TrackSelection trackSelection) {
    this.trackSelection = trackSelection;
  }

  /**
   * Returns the current track selection.
   */
  public TrackSelection getTrackSelection() {
    return trackSelection;
  }

  /**
   * Resets the source.
   */
  public void reset() {
    fatalError = null;
  }

  /**
   * Sets whether this chunk source is responsible for initializing timestamp adjusters.
   *
   * @param isTimestampMaster True if this chunk source is responsible for initializing timestamp
   *     adjusters.
   */
  public void setIsTimestampMaster(boolean isTimestampMaster) {
    this.isTimestampMaster = isTimestampMaster;
  }

  /**
   * Returns the next chunk to load.
   * <p>
   * If a chunk is available then {@link HlsChunkHolder#chunk} is set. If the end of the stream has
   * been reached then {@link HlsChunkHolder#endOfStream} is set. If a chunk is not available but
   * the end of the stream has not been reached, {@link HlsChunkHolder#playlist} is set to
   * contain the {@link HlsUrl} that refers to the playlist that needs refreshing.
   *
   * @param previous The most recently loaded media chunk.
   * @param playbackPositionUs The current playback position. If {@code previous} is null then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out A holder to populate.
   */
  public void getNextChunk(HlsMediaChunk previous, long playbackPositionUs, HlsChunkHolder out) {
    int oldVariantIndex = previous == null ? C.INDEX_UNSET
        : trackGroup.indexOf(previous.trackFormat);
    expectedPlaylistUrl = null;
    // Unless segments are known to be independent, switching variant will require downloading
    // overlapping segments. Hence we use the start time of the previous chunk rather than its end
    // time for this case.
    long bufferedDurationUs = previous == null ? 0 : Math.max(0,
        (independentSegments ? previous.endTimeUs : previous.startTimeUs) - playbackPositionUs);

    // Select the variant.
    trackSelection.updateSelectedTrack(bufferedDurationUs);
    int selectedVariantIndex = trackSelection.getSelectedIndexInTrackGroup();

    boolean switchingVariant = oldVariantIndex != selectedVariantIndex;
    HlsUrl selectedUrl = variants[selectedVariantIndex];
    if (!playlistTracker.isSnapshotValid(selectedUrl)) {
      out.playlist = selectedUrl;
      expectedPlaylistUrl = selectedUrl;
      // Retry when playlist is refreshed.
      return;
    }
    HlsMediaPlaylist mediaPlaylist = playlistTracker.getPlaylistSnapshot(selectedUrl);
    independentSegments = mediaPlaylist.hasIndependentSegmentsTag;

    // Select the chunk.
    int chunkMediaSequence;
    if (previous == null || switchingVariant) {
      long targetPositionUs = previous == null ? playbackPositionUs
          : independentSegments ? previous.endTimeUs : previous.startTimeUs;
      if (!mediaPlaylist.hasEndTag && targetPositionUs >= mediaPlaylist.getEndTimeUs()) {
        // If the playlist is too old to contain the chunk, we need to refresh it.
        chunkMediaSequence = mediaPlaylist.mediaSequence + mediaPlaylist.segments.size();
      } else {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments,
            targetPositionUs - mediaPlaylist.startTimeUs, true,
            !playlistTracker.isLive() || previous == null) + mediaPlaylist.mediaSequence;
        if (chunkMediaSequence < mediaPlaylist.mediaSequence && previous != null) {
          // We try getting the next chunk without adapting in case that's the reason for falling
          // behind the live window.
          selectedVariantIndex = oldVariantIndex;
          selectedUrl = variants[selectedVariantIndex];
          mediaPlaylist = playlistTracker.getPlaylistSnapshot(selectedUrl);
          chunkMediaSequence = previous.getNextChunkIndex();
        }
      }
    } else {
      chunkMediaSequence = previous.getNextChunkIndex();
    }
    if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
      fatalError = new BehindLiveWindowException();
      return;
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (mediaPlaylist.hasEndTag) {
        out.endOfStream = true;
      } else /* Live */ {
        out.playlist = selectedUrl;
        expectedPlaylistUrl = selectedUrl;
      }
      return;
    }

    // Handle encryption.
    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);

    // Check if encryption is specified.
    if (segment.isEncrypted) {
      Uri keyUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.encryptionKeyUri);
      if (!keyUri.equals(encryptionKeyUri)) {
        // Encryption is specified and the key has changed.
        out.chunk = newEncryptionKeyChunk(keyUri, segment.encryptionIV, selectedVariantIndex,
            trackSelection.getSelectionReason(), trackSelection.getSelectionData());
        return;
      }
      if (!Util.areEqual(segment.encryptionIV, encryptionIvString)) {
        setEncryptionData(keyUri, segment.encryptionIV, encryptionKey);
      }
    } else {
      clearEncryptionData();
    }

    DataSpec initDataSpec = null;
    Segment initSegment = mediaPlaylist.initializationSegment;
    if (initSegment != null) {
      Uri initSegmentUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, initSegment.url);
      initDataSpec = new DataSpec(initSegmentUri, initSegment.byterangeOffset,
          initSegment.byterangeLength, null);
    }

    // Compute start time of the next chunk.
    long startTimeUs = mediaPlaylist.startTimeUs + segment.relativeStartTimeUs;
    int discontinuitySequence = mediaPlaylist.discontinuitySequence
        + segment.relativeDiscontinuitySequence;
    TimestampAdjuster timestampAdjuster = timestampAdjusterProvider.getAdjuster(
        discontinuitySequence);

    // Configure the data source and spec for the chunk.
    Uri chunkUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url);
    DataSpec dataSpec = new DataSpec(chunkUri, segment.byterangeOffset, segment.byterangeLength,
        null);
    out.chunk = new HlsMediaChunk(mediaDataSource, dataSpec, initDataSpec, selectedUrl,
        muxedCaptionFormats, trackSelection.getSelectionReason(), trackSelection.getSelectionData(),
        startTimeUs, startTimeUs + segment.durationUs, chunkMediaSequence, discontinuitySequence,
        isTimestampMaster, timestampAdjuster, previous, encryptionKey, encryptionIv);
  }

  /**
   * Called when the {@link HlsSampleStreamWrapper} has finished loading a chunk obtained from this
   * source.
   *
   * @param chunk The chunk whose load has been completed.
   */
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof EncryptionKeyChunk) {
      EncryptionKeyChunk encryptionKeyChunk = (EncryptionKeyChunk) chunk;
      scratchSpace = encryptionKeyChunk.getDataHolder();
      setEncryptionData(encryptionKeyChunk.dataSpec.uri, encryptionKeyChunk.iv,
          encryptionKeyChunk.getResult());
    }
  }

  /**
   * Called when the {@link HlsSampleStreamWrapper} encounters an error loading a chunk obtained
   * from this source.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param cancelable Whether the load can be canceled.
   * @param error The error.
   * @return Whether the load should be canceled.
   */
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, IOException error) {
    return cancelable && ChunkedTrackBlacklistUtil.maybeBlacklistTrack(trackSelection,
        trackSelection.indexOf(trackGroup.indexOf(chunk.trackFormat)), error);
  }

  /**
   * Called when a playlist is blacklisted.
   *
   * @param url The url that references the blacklisted playlist.
   * @param blacklistMs The amount of milliseconds for which the playlist was blacklisted.
   */
  public void onPlaylistBlacklisted(HlsUrl url, long blacklistMs) {
    int trackGroupIndex = trackGroup.indexOf(url.format);
    if (trackGroupIndex != C.INDEX_UNSET) {
      int trackSelectionIndex = trackSelection.indexOf(trackGroupIndex);
      if (trackSelectionIndex != C.INDEX_UNSET) {
        trackSelection.blacklist(trackSelectionIndex, blacklistMs);
      }
    }
  }

  // Private methods.

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv, int variantIndex,
      int trackSelectionReason, Object trackSelectionData) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNSET, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(encryptionDataSource, dataSpec, variants[variantIndex].format,
        trackSelectionReason, trackSelectionData, scratchSpace, iv);
  }

  private void setEncryptionData(Uri keyUri, String iv, byte[] secretKey) {
    String trimmedIv;
    if (Util.toLowerInvariant(iv).startsWith("0x")) {
      trimmedIv = iv.substring(2);
    } else {
      trimmedIv = iv;
    }

    byte[] ivData = new BigInteger(trimmedIv, 16).toByteArray();
    byte[] ivDataWithPadding = new byte[16];
    int offset = ivData.length > 16 ? ivData.length - 16 : 0;
    System.arraycopy(ivData, offset, ivDataWithPadding, ivDataWithPadding.length - ivData.length
        + offset, ivData.length - offset);

    encryptionKeyUri = keyUri;
    encryptionKey = secretKey;
    encryptionIvString = iv;
    encryptionIv = ivDataWithPadding;
  }

  private void clearEncryptionData() {
    encryptionKeyUri = null;
    encryptionKey = null;
    encryptionIvString = null;
    encryptionIv = null;
  }

  // Private classes.

  /**
   * A {@link TrackSelection} to use for initialization.
   */
  private static final class InitializationTrackSelection extends BaseTrackSelection {

    private int selectedIndex;

    public InitializationTrackSelection(TrackGroup group, int[] tracks) {
      super(group, tracks);
      selectedIndex = indexOf(group.getFormat(0));
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
      long nowMs = SystemClock.elapsedRealtime();
      if (!isBlacklisted(selectedIndex, nowMs)) {
        return;
      }
      // Try from lowest bitrate to highest.
      for (int i = length - 1; i >= 0; i--) {
        if (!isBlacklisted(i, nowMs)) {
          selectedIndex = i;
          return;
        }
      }
      // Should never happen.
      throw new IllegalStateException();
    }

    @Override
    public int getSelectedIndex() {
      return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Override
    public Object getSelectionData() {
      return null;
    }

  }

  private static final class EncryptionKeyChunk extends DataChunk {

    public final String iv;

    private byte[] result;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat,
        int trackSelectionReason, Object trackSelectionData, byte[] scratchSpace, String iv) {
      super(dataSource, dataSpec, C.DATA_TYPE_DRM, trackFormat, trackSelectionReason,
          trackSelectionData, scratchSpace);
      this.iv = iv;
    }

    @Override
    protected void consume(byte[] data, int limit) throws IOException {
      result = Arrays.copyOf(data, limit);
    }

    public byte[] getResult() {
      return result;
    }

  }

}
