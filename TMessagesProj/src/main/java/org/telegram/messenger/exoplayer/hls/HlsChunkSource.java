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

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import org.telegram.messenger.exoplayer.BehindLiveWindowException;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.chunk.Chunk;
import org.telegram.messenger.exoplayer.chunk.ChunkOperationHolder;
import org.telegram.messenger.exoplayer.chunk.DataChunk;
import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.mp3.Mp3Extractor;
import org.telegram.messenger.exoplayer.extractor.ts.AdtsExtractor;
import org.telegram.messenger.exoplayer.extractor.ts.PtsTimestampAdjuster;
import org.telegram.messenger.exoplayer.extractor.ts.TsExtractor;
import org.telegram.messenger.exoplayer.upstream.BandwidthMeter;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.upstream.HttpDataSource.InvalidResponseCodeException;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.UriUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A temporary test source of HLS chunks.
 */
public class HlsChunkSource implements HlsTrackSelector.Output {

  /**
   * Interface definition for a callback to be notified of {@link HlsChunkSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when a media playlist has been loaded.
    *
     * @param rawResponse The raw data of the media playlist
     */
    void onMediaPlaylistLoadCompleted(byte[] rawResponse);

  }

  /**
   * The default minimum duration of media that needs to be buffered for a switch to a higher
   * quality variant to be considered.
   */
  public static final long DEFAULT_MIN_BUFFER_TO_SWITCH_UP_MS = 5000;

  /**
   * The default maximum duration of media that needs to be buffered for a switch to a lower
   * quality variant to be considered.
   */
  public static final long DEFAULT_MAX_BUFFER_TO_SWITCH_DOWN_MS = 20000;

  /**
   * The default time for which a media playlist should be blacklisted.
   */
  public static final long DEFAULT_PLAYLIST_BLACKLIST_MS = 60000;

  /**
   * Subtracted value to lookup position when switching between variants in live streams to avoid
   * gaps in playback in case playlist drift apart.
   */
  private static final double LIVE_VARIANT_SWITCH_SAFETY_EXTRA_SECS = 2.0;

  private static final String TAG = "HlsChunkSource";
  private static final String AAC_FILE_EXTENSION = ".aac";
  private static final String MP3_FILE_EXTENSION = ".mp3";
  private static final String VTT_FILE_EXTENSION = ".vtt";
  private static final String WEBVTT_FILE_EXTENSION = ".webvtt";
  private static final float BANDWIDTH_FRACTION = 0.8f;

  private final boolean isMaster;
  private final DataSource dataSource;
  private final HlsPlaylistParser playlistParser;
  private final HlsMasterPlaylist masterPlaylist;
  private final HlsTrackSelector trackSelector;
  private final BandwidthMeter bandwidthMeter;
  private final PtsTimestampAdjusterProvider timestampAdjusterProvider;
  private final String baseUri;
  private final long minBufferDurationToSwitchUpUs;
  private final long maxBufferDurationToSwitchDownUs;

  // TODO: Expose tracks.
  private final ArrayList<ExposedTrack> tracks;

  private int selectedTrackIndex;

  // A list of variants considered during playback, ordered by decreasing bandwidth. The following
  // three arrays are of the same length and are ordered in the same way (i.e. variantPlaylists[i],
  // variantLastPlaylistLoadTimesMs[i] and variantBlacklistTimes[i] all correspond to variants[i]).
  private Variant[] variants;
  private HlsMediaPlaylist[] variantPlaylists;
  private long[] variantLastPlaylistLoadTimesMs;
  private long[] variantBlacklistTimes;

  // The index in variants of the currently selected variant.
  private int selectedVariantIndex;

  private boolean prepareCalled;
  private byte[] scratchSpace;
  private boolean live;
  private long durationUs;
  private IOException fatalError;

  private Uri encryptionKeyUri;
  private byte[] encryptionKey;
  private String encryptionIvString;
  private byte[] encryptionIv;
  private final EventListener eventListener;
  private final Handler eventHandler;

  /**
   * @param isMaster True if this is the master source for the playback. False otherwise. Each
   *     playback must have exactly one master source, which should be the source providing video
   *     chunks (or audio chunks for audio only playbacks).
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param playlist The HLS playlist.
   * @param trackSelector Selects tracks to be exposed by this source.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   * @param timestampAdjusterProvider A provider of {@link PtsTimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   */
  public HlsChunkSource(boolean isMaster, DataSource dataSource, HlsPlaylist playlist,
      HlsTrackSelector trackSelector, BandwidthMeter bandwidthMeter,
      PtsTimestampAdjusterProvider timestampAdjusterProvider) {
    this(isMaster, dataSource, playlist, trackSelector, bandwidthMeter,
        timestampAdjusterProvider, DEFAULT_MIN_BUFFER_TO_SWITCH_UP_MS,
        DEFAULT_MAX_BUFFER_TO_SWITCH_DOWN_MS, null, null);
  }

  /**
   * @param isMaster True if this is the master source for the playback. False otherwise. Each
   *     playback must have exactly one master source, which should be the source providing video
   *     chunks (or audio chunks for audio only playbacks).
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param playlist The HLS playlist.
   * @param trackSelector Selects tracks to be exposed by this source.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   * @param timestampAdjusterProvider A provider of {@link PtsTimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   * @param minBufferDurationToSwitchUpMs The minimum duration of media that needs to be buffered
   *     for a switch to a higher quality variant to be considered.
   * @param maxBufferDurationToSwitchDownMs The maximum duration of media that needs to be buffered
   *     for a switch to a lower quality variant to be considered.
   */
  public HlsChunkSource(boolean isMaster, DataSource dataSource, HlsPlaylist playlist,
      HlsTrackSelector trackSelector, BandwidthMeter bandwidthMeter,
      PtsTimestampAdjusterProvider timestampAdjusterProvider, long minBufferDurationToSwitchUpMs,
      long maxBufferDurationToSwitchDownMs) {
    this(isMaster, dataSource, playlist, trackSelector, bandwidthMeter,
         timestampAdjusterProvider, minBufferDurationToSwitchUpMs,
         maxBufferDurationToSwitchDownMs, null, null);
  }

  /**
   * @param isMaster True if this is the master source for the playback. False otherwise. Each
   *     playback must have exactly one master source, which should be the source providing video
   *     chunks (or audio chunks for audio only playbacks).
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param playlist The HLS playlist.
   * @param trackSelector Selects tracks to be exposed by this source.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   * @param timestampAdjusterProvider A provider of {@link PtsTimestampAdjuster} instances. If
   *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
   *     same provider.
   * @param minBufferDurationToSwitchUpMs The minimum duration of media that needs to be buffered
   *     for a switch to a higher quality variant to be considered.
   * @param maxBufferDurationToSwitchDownMs The maximum duration of media that needs to be buffered
   *     for a switch to a lower quality variant to be considered.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public HlsChunkSource(boolean isMaster, DataSource dataSource, HlsPlaylist playlist,
      HlsTrackSelector trackSelector, BandwidthMeter bandwidthMeter,
      PtsTimestampAdjusterProvider timestampAdjusterProvider,
      long minBufferDurationToSwitchUpMs, long maxBufferDurationToSwitchDownMs,
      Handler eventHandler, EventListener eventListener) {
    this.isMaster = isMaster;
    this.dataSource = dataSource;
    this.trackSelector = trackSelector;
    this.bandwidthMeter = bandwidthMeter;
    this.timestampAdjusterProvider = timestampAdjusterProvider;
    this.eventListener = eventListener;
    this.eventHandler = eventHandler;
    minBufferDurationToSwitchUpUs = minBufferDurationToSwitchUpMs * 1000;
    maxBufferDurationToSwitchDownUs = maxBufferDurationToSwitchDownMs * 1000;
    baseUri = playlist.baseUri;
    playlistParser = new HlsPlaylistParser();
    tracks = new ArrayList<>();

    if (playlist.type == HlsPlaylist.TYPE_MASTER) {
      masterPlaylist = (HlsMasterPlaylist) playlist;
    } else {
      Format format = new Format("0", MimeTypes.APPLICATION_M3U8, -1, -1, -1, -1, -1, -1, null,
          null);
      List<Variant> variants = new ArrayList<>();
      variants.add(new Variant(baseUri, format));
      masterPlaylist = new HlsMasterPlaylist(baseUri, variants,
          Collections.<Variant>emptyList(), Collections.<Variant>emptyList(), null, null);
    }
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
  }

  /**
   * Prepares the source.
   *
   * @return True if the source was prepared, false otherwise.
   */
  public boolean prepare() {
    if (!prepareCalled) {
      prepareCalled = true;
      try {
        trackSelector.selectTracks(masterPlaylist, this);
        selectTrack(0);
      } catch (IOException e) {
        fatalError = e;
      }
    }
    return fatalError == null;
  }

  /**
   * Returns whether this is a live playback.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return True if this is a live playback. False otherwise.
   */
  public boolean isLive() {
    return live;
  }

  /**
   * Returns the duration of the source, or {@link C#UNKNOWN_TIME_US} if the duration is unknown.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The number of tracks.
   */
  public long getDurationUs() {
    return durationUs;
  }

  /**
   * Returns the number of tracks exposed by the source.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The number of tracks.
   */
  public int getTrackCount() {
    return tracks.size();
  }

  /**
   * Returns the variant corresponding to the fixed track at the specified index, or null if the
   * track at the specified index is adaptive.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param index The track index.
   * @return The variant corresponding to the fixed track, or null if the track is adaptive.
   */
  public Variant getFixedTrackVariant(int index) {
    Variant[] variants = tracks.get(index).variants;
    return variants.length == 1 ? variants[0] : null;
  }

  /**
   * Returns the language of the audio muxed into variants, or null if unknown.
   *
   * @return The language of the audio muxed into variants, or null if unknown.
   */
  public String getMuxedAudioLanguage() {
    return masterPlaylist.muxedAudioLanguage;
  }

  /**
   * Returns the language of the captions muxed into variants, or null if unknown.
   *
   * @return The language of the captions muxed into variants, or null if unknown.
   */
  public String getMuxedCaptionLanguage() {
    return masterPlaylist.muxedCaptionLanguage;
  }

  /**
   * Returns the currently selected track index.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The currently selected track index.
   */
  public int getSelectedTrackIndex() {
    return selectedTrackIndex;
  }

  /**
   * Selects a track for use.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param index The track index.
   */
  public void selectTrack(int index) {
    selectedTrackIndex = index;
    ExposedTrack selectedTrack = tracks.get(selectedTrackIndex);
    selectedVariantIndex = selectedTrack.defaultVariantIndex;
    variants = selectedTrack.variants;
    variantPlaylists = new HlsMediaPlaylist[variants.length];
    variantLastPlaylistLoadTimesMs = new long[variants.length];
    variantBlacklistTimes = new long[variants.length];
  }

  /**
   * Notifies the source that a seek has occurred.
   * <p>
   * This method should only be called after the source has been prepared.
   */
  public void seek() {
    if (isMaster) {
      timestampAdjusterProvider.reset();
    }
  }

  /**
   * Resets the source.
   * <p>
   * This method should only be called after the source has been prepared.
   */
  public void reset() {
    fatalError = null;
  }

  /**
   * Updates the provided {@link ChunkOperationHolder} to contain the next operation that should
   * be performed by the calling {@link HlsSampleSource}.
   *
   * @param previousTsChunk The previously loaded chunk that the next chunk should follow.
   * @param playbackPositionUs The current playback position. If previousTsChunk is null then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out The holder to populate with the result. {@link ChunkOperationHolder#queueSize} is
   *     unused.
   */
  public void getChunkOperation(TsChunk previousTsChunk, long playbackPositionUs,
      ChunkOperationHolder out) {
    int previousChunkVariantIndex =
        previousTsChunk == null ? -1 : getVariantIndex(previousTsChunk.format);
    int nextVariantIndex = getNextVariantIndex(previousTsChunk, playbackPositionUs);
    boolean switchingVariant = previousTsChunk != null
        && previousChunkVariantIndex != nextVariantIndex;

    HlsMediaPlaylist mediaPlaylist = variantPlaylists[nextVariantIndex];
    if (mediaPlaylist == null) {
      // We don't have the media playlist for the next variant. Request it now.
      out.chunk = newMediaPlaylistChunk(nextVariantIndex);
      return;
    }

    selectedVariantIndex = nextVariantIndex;
    int chunkMediaSequence;
    if (live) {
      if (previousTsChunk == null) {
        chunkMediaSequence = getLiveStartChunkSequenceNumber(selectedVariantIndex);
      } else {
        chunkMediaSequence = getLiveNextChunkSequenceNumber(previousTsChunk.chunkIndex,
            previousChunkVariantIndex, selectedVariantIndex);
        if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
          fatalError = new BehindLiveWindowException();
          return;
        }
      }
    } else {
      // Not live.
      if (previousTsChunk == null) {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments, playbackPositionUs,
            true, true) + mediaPlaylist.mediaSequence;
      } else if (switchingVariant) {
        chunkMediaSequence = Util.binarySearchFloor(mediaPlaylist.segments,
            previousTsChunk.startTimeUs, true, true) + mediaPlaylist.mediaSequence;
      } else {
        chunkMediaSequence = previousTsChunk.getNextChunkIndex();
      }
    }

    int chunkIndex = chunkMediaSequence - mediaPlaylist.mediaSequence;
    if (chunkIndex >= mediaPlaylist.segments.size()) {
      if (!mediaPlaylist.live) {
        out.endOfStream = true;
      } else if (shouldRerequestLiveMediaPlaylist(selectedVariantIndex)) {
        out.chunk = newMediaPlaylistChunk(selectedVariantIndex);
      }
      return;
    }

    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(chunkIndex);
    Uri chunkUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.url);

    // Check if encryption is specified.
    if (segment.isEncrypted) {
      Uri keyUri = UriUtil.resolveToUri(mediaPlaylist.baseUri, segment.encryptionKeyUri);
      if (!keyUri.equals(encryptionKeyUri)) {
        // Encryption is specified and the key has changed.
        out.chunk = newEncryptionKeyChunk(keyUri, segment.encryptionIV, selectedVariantIndex);
        return;
      }
      if (!Util.areEqual(segment.encryptionIV, encryptionIvString)) {
        setEncryptionData(keyUri, segment.encryptionIV, encryptionKey);
      }
    } else {
      clearEncryptionData();
    }

    // Configure the data source and spec for the chunk.
    DataSpec dataSpec = new DataSpec(chunkUri, segment.byterangeOffset, segment.byterangeLength,
        null);

    // Compute start and end times, and the sequence number of the next chunk.
    long startTimeUs;
    if (live) {
      if (previousTsChunk == null) {
        startTimeUs = 0;
      } else {
        startTimeUs = previousTsChunk.getAdjustedEndTimeUs()
            - (switchingVariant ? previousTsChunk.getDurationUs() : 0);
      }
    } else /* Not live */ {
      startTimeUs = segment.startTimeUs;
    }
    long endTimeUs = startTimeUs + (long) (segment.durationSecs * C.MICROS_PER_SECOND);
    int trigger = Chunk.TRIGGER_UNSPECIFIED;
    Format format = variants[selectedVariantIndex].format;

    // Configure the extractor that will read the chunk.
    HlsExtractorWrapper extractorWrapper;
    String lastPathSegment = chunkUri.getLastPathSegment();
    if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      // TODO: Inject a timestamp adjuster and use it along with ID3 PRIV tag values with owner
      // identifier com.apple.streaming.transportStreamTimestamp. This may also apply to the MP3
      // case below.
      Extractor extractor = new AdtsExtractor(startTimeUs);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariant, MediaFormat.NO_VALUE, MediaFormat.NO_VALUE);
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      Extractor extractor = new Mp3Extractor(startTimeUs);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariant, MediaFormat.NO_VALUE, MediaFormat.NO_VALUE);
    } else if (lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      PtsTimestampAdjuster timestampAdjuster = timestampAdjusterProvider.getAdjuster(isMaster,
          segment.discontinuitySequenceNumber, startTimeUs);
      if (timestampAdjuster == null) {
        // The master source has yet to instantiate an adjuster for the discontinuity sequence.
        // TODO: There's probably an edge case if the master starts playback at a chunk belonging to
        // a discontinuity sequence greater than the one that this source is trying to start at.
        return;
      }
      Extractor extractor = new WebvttExtractor(timestampAdjuster);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariant, MediaFormat.NO_VALUE, MediaFormat.NO_VALUE);
    } else if (previousTsChunk == null
        || previousTsChunk.discontinuitySequenceNumber != segment.discontinuitySequenceNumber
        || !format.equals(previousTsChunk.format)) {
      // MPEG-2 TS segments, but we need a new extractor.
      PtsTimestampAdjuster timestampAdjuster = timestampAdjusterProvider.getAdjuster(isMaster,
          segment.discontinuitySequenceNumber, startTimeUs);
      if (timestampAdjuster == null) {
        // The master source has yet to instantiate an adjuster for the discontinuity sequence.
        return;
      }
      int workaroundFlags = 0;
      String codecs = format.codecs;
      if (!TextUtils.isEmpty(codecs)) {
        // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
        // exist. If we know from the codec attribute that they don't exist, then we can explicitly
        // ignore them even if they're declared.
        if (MimeTypes.getAudioMediaMimeType(codecs) != MimeTypes.AUDIO_AAC) {
          workaroundFlags |= TsExtractor.WORKAROUND_IGNORE_AAC_STREAM;
        }
        if (MimeTypes.getVideoMediaMimeType(codecs) != MimeTypes.VIDEO_H264) {
          workaroundFlags |= TsExtractor.WORKAROUND_IGNORE_H264_STREAM;
        }
      }
      Extractor extractor = new TsExtractor(timestampAdjuster, workaroundFlags);
      ExposedTrack selectedTrack = tracks.get(selectedTrackIndex);
      extractorWrapper = new HlsExtractorWrapper(trigger, format, startTimeUs, extractor,
          switchingVariant, selectedTrack.adaptiveMaxWidth, selectedTrack.adaptiveMaxHeight);
    } else {
      // MPEG-2 TS segments, and we need to continue using the same extractor.
      extractorWrapper = previousTsChunk.extractorWrapper;
    }
    out.chunk = new TsChunk(dataSource, dataSpec, trigger, format, startTimeUs, endTimeUs,
        chunkMediaSequence, segment.discontinuitySequenceNumber, extractorWrapper, encryptionKey,
        encryptionIv);
  }

  /**
   * Invoked when the {@link HlsSampleSource} has finished loading a chunk obtained from this
   * source.
   *
   * @param chunk The chunk whose load has been completed.
   */
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof MediaPlaylistChunk) {
      MediaPlaylistChunk mediaPlaylistChunk = (MediaPlaylistChunk) chunk;
      scratchSpace = mediaPlaylistChunk.getDataHolder();
      setMediaPlaylist(mediaPlaylistChunk.variantIndex, mediaPlaylistChunk.getResult());
      if (eventHandler != null && eventListener != null) {
        final byte[] rawResponse = mediaPlaylistChunk.getRawResponse();
        eventHandler.post(new Runnable()  {
          @Override
          public void run() {
            eventListener.onMediaPlaylistLoadCompleted(rawResponse);
          }
        });
      }
    } else if (chunk instanceof EncryptionKeyChunk) {
      EncryptionKeyChunk encryptionKeyChunk = (EncryptionKeyChunk) chunk;
      scratchSpace = encryptionKeyChunk.getDataHolder();
      setEncryptionData(encryptionKeyChunk.dataSpec.uri, encryptionKeyChunk.iv,
          encryptionKeyChunk.getResult());
    }
  }

  /**
   * Invoked when the {@link HlsSampleSource} encounters an error loading a chunk obtained from
   * this source.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param e The error.
   * @return True if the error was handled by the source. False otherwise.
   */
  public boolean onChunkLoadError(Chunk chunk, IOException e) {
    if (chunk.bytesLoaded() == 0
        && (chunk instanceof TsChunk || chunk instanceof MediaPlaylistChunk
            || chunk instanceof EncryptionKeyChunk)
        && (e instanceof InvalidResponseCodeException)) {
      InvalidResponseCodeException responseCodeException = (InvalidResponseCodeException) e;
      int responseCode = responseCodeException.responseCode;
      if (responseCode == 404 || responseCode == 410) {
        int variantIndex;
        if (chunk instanceof TsChunk) {
          TsChunk tsChunk = (TsChunk) chunk;
          variantIndex = getVariantIndex(tsChunk.format);
        } else if (chunk instanceof MediaPlaylistChunk) {
          MediaPlaylistChunk playlistChunk = (MediaPlaylistChunk) chunk;
          variantIndex = playlistChunk.variantIndex;
        } else {
          EncryptionKeyChunk encryptionChunk = (EncryptionKeyChunk) chunk;
          variantIndex = encryptionChunk.variantIndex;
        }
        boolean alreadyBlacklisted = variantBlacklistTimes[variantIndex] != 0;
        variantBlacklistTimes[variantIndex] = SystemClock.elapsedRealtime();
        if (alreadyBlacklisted) {
          // The playlist was already blacklisted.
          Log.w(TAG, "Already blacklisted variant (" + responseCode + "): "
              + chunk.dataSpec.uri);
          return false;
        } else if (!allVariantsBlacklisted()) {
          // We've handled the 404/410 by blacklisting the variant.
          Log.w(TAG, "Blacklisted variant (" + responseCode + "): "
              + chunk.dataSpec.uri);
          return true;
        } else {
          // This was the last non-blacklisted playlist. Don't blacklist it.
          Log.w(TAG, "Final variant not blacklisted (" + responseCode + "): "
              + chunk.dataSpec.uri);
          variantBlacklistTimes[variantIndex] = 0;
          return false;
        }
      }
    }
    return false;
  }

  // HlsTrackSelector.Output implementation.

  @Override
  public void adaptiveTrack(HlsMasterPlaylist playlist, Variant[] variants) {
    Arrays.sort(variants, new Comparator<Variant>() {
      private final Comparator<Format> formatComparator =
          new Format.DecreasingBandwidthComparator();
      @Override
      public int compare(Variant first, Variant second) {
        return formatComparator.compare(first.format, second.format);
      }
    });

    int defaultVariantIndex = computeDefaultVariantIndex(playlist, variants, bandwidthMeter);
    int maxWidth = -1;
    int maxHeight = -1;

    for (int i = 0; i < variants.length; i++) {
      Format variantFormat = variants[i].format;
      maxWidth = Math.max(variantFormat.width, maxWidth);
      maxHeight = Math.max(variantFormat.height, maxHeight);
    }
    // TODO: We should allow the default values to be passed through the constructor.
    // TODO: Print a warning if resolution tags are omitted.
    maxWidth = maxWidth > 0 ? maxWidth : 1920;
    maxHeight = maxHeight > 0 ? maxHeight : 1080;
    tracks.add(new ExposedTrack(variants, defaultVariantIndex, maxWidth, maxHeight));
  }

  @Override
  public void fixedTrack(HlsMasterPlaylist playlist, Variant variant) {
    tracks.add(new ExposedTrack(variant));
  }

  protected int computeDefaultVariantIndex(HlsMasterPlaylist playlist, Variant[] variants,
      BandwidthMeter bandwidthMeter) {
    int defaultVariantIndex = 0;
    int minOriginalVariantIndex = Integer.MAX_VALUE;

    for (int i = 0; i < variants.length; i++) {
      int originalVariantIndex = playlist.variants.indexOf(variants[i]);
      if (originalVariantIndex < minOriginalVariantIndex) {
        minOriginalVariantIndex = originalVariantIndex;
        defaultVariantIndex = i;
      }
    }

    return  defaultVariantIndex;
  }
  
  // Private methods.

  private int getLiveStartChunkSequenceNumber(int variantIndex) {
    // For live start playback from the third chunk from the end.
    HlsMediaPlaylist mediaPlaylist = variantPlaylists[variantIndex];
    int chunkIndex = mediaPlaylist.segments.size() > 3 ? mediaPlaylist.segments.size() - 3 : 0;
    return chunkIndex + mediaPlaylist.mediaSequence;
  }

  /**
   * Returns the media sequence number of a chunk in a new variant for a live stream variant switch.
   *
   * @param previousChunkIndex The index of the last chunk in the old variant.
   * @param oldVariantIndex The index of the old variant.
   * @param newVariantIndex The index of the new variant.
   * @return Media sequence number of the chunk to switch to in a live stream in the variant that
   *     corresponds to the given {@code newVariantIndex}.
   */
  private int getLiveNextChunkSequenceNumber(int previousChunkIndex, int oldVariantIndex,
      int newVariantIndex) {
    if (oldVariantIndex == newVariantIndex) {
      return previousChunkIndex + 1;
    }
    HlsMediaPlaylist oldMediaPlaylist = variantPlaylists[oldVariantIndex];
    HlsMediaPlaylist newMediaPlaylist = variantPlaylists[newVariantIndex];
    double offsetToLiveInstantSecs = 0;
    for (int i = previousChunkIndex - oldMediaPlaylist.mediaSequence;
         i < oldMediaPlaylist.segments.size(); i++) {
      offsetToLiveInstantSecs += oldMediaPlaylist.segments.get(i).durationSecs;
    }
    long currentTimeMs = SystemClock.elapsedRealtime();
    offsetToLiveInstantSecs +=
        (double) (currentTimeMs - variantLastPlaylistLoadTimesMs[oldVariantIndex]) / 1000;
    offsetToLiveInstantSecs += LIVE_VARIANT_SWITCH_SAFETY_EXTRA_SECS;
    offsetToLiveInstantSecs -=
        (double) (currentTimeMs - variantLastPlaylistLoadTimesMs[newVariantIndex]) / 1000;
    if (offsetToLiveInstantSecs < 0) {
      // The instant we are looking for is not contained in the playlist, we need it to be
      // refreshed.
      return newMediaPlaylist.mediaSequence + newMediaPlaylist.segments.size() + 1;
    }
    for (int i = newMediaPlaylist.segments.size() - 1; i >= 0; i--) {
      offsetToLiveInstantSecs -= newMediaPlaylist.segments.get(i).durationSecs;
      if (offsetToLiveInstantSecs < 0) {
        return newMediaPlaylist.mediaSequence + i;
      }
    }
    // We have fallen behind the live window.
    return newMediaPlaylist.mediaSequence - 1;
  }

  private int getNextVariantIndex(TsChunk previousTsChunk, long playbackPositionUs) {
    clearStaleBlacklistedVariants();
    long bitrateEstimate = bandwidthMeter.getBitrateEstimate();
    if (variantBlacklistTimes[selectedVariantIndex] != 0) {
      // The current variant has been blacklisted, so we have no choice but to re-evaluate.
      return getVariantIndexForBandwidth(bitrateEstimate);
    }
    if (previousTsChunk == null) {
      // Don't consider switching if we don't have a previous chunk.
      return selectedVariantIndex;
    }
    if (bitrateEstimate == BandwidthMeter.NO_ESTIMATE) {
      // Don't consider switching if we don't have a bandwidth estimate.
      return selectedVariantIndex;
    }
    int idealIndex = getVariantIndexForBandwidth(bitrateEstimate);
    if (idealIndex == selectedVariantIndex) {
      // We're already using the ideal variant.
      return selectedVariantIndex;
    }
    // We're not using the ideal variant for the available bandwidth, but only switch if the
    // conditions are appropriate.
    long bufferedUs = previousTsChunk.getAdjustedEndTimeUs() - previousTsChunk.getDurationUs()
        - playbackPositionUs;
    if (variantBlacklistTimes[selectedVariantIndex] != 0
        || (idealIndex > selectedVariantIndex && bufferedUs < maxBufferDurationToSwitchDownUs)
        || (idealIndex < selectedVariantIndex && bufferedUs > minBufferDurationToSwitchUpUs)) {
      // Switch variant.
      return idealIndex;
    }
    // Stick with the current variant for now.
    return selectedVariantIndex;
  }

  private int getVariantIndexForBandwidth(long bitrateEstimate) {
    if (bitrateEstimate == BandwidthMeter.NO_ESTIMATE) {
      // Select the lowest quality.
      bitrateEstimate = 0;
    }
    int effectiveBitrate = (int) (bitrateEstimate * BANDWIDTH_FRACTION);
    int lowestQualityEnabledVariantIndex = -1;
    for (int i = 0; i < variants.length; i++) {
      if (variantBlacklistTimes[i] == 0) {
        if (variants[i].format.bitrate <= effectiveBitrate) {
          return i;
        }
        lowestQualityEnabledVariantIndex = i;
      }
    }
    // At least one variant should always be enabled.
    Assertions.checkState(lowestQualityEnabledVariantIndex != -1);
    return lowestQualityEnabledVariantIndex;
  }

  private boolean shouldRerequestLiveMediaPlaylist(int nextVariantIndex) {
    // Don't re-request media playlist more often than one-half of the target duration.
    HlsMediaPlaylist mediaPlaylist = variantPlaylists[nextVariantIndex];
    long timeSinceLastMediaPlaylistLoadMs =
        SystemClock.elapsedRealtime() - variantLastPlaylistLoadTimesMs[nextVariantIndex];
    return timeSinceLastMediaPlaylistLoadMs >= (mediaPlaylist.targetDurationSecs * 1000) / 2;
  }

  private MediaPlaylistChunk newMediaPlaylistChunk(int variantIndex) {
    Uri mediaPlaylistUri = UriUtil.resolveToUri(baseUri, variants[variantIndex].url);
    DataSpec dataSpec = new DataSpec(mediaPlaylistUri, 0, C.LENGTH_UNBOUNDED, null,
        DataSpec.FLAG_ALLOW_GZIP);
    return new MediaPlaylistChunk(dataSource, dataSpec, scratchSpace, playlistParser, variantIndex,
        mediaPlaylistUri.toString());
  }

  private EncryptionKeyChunk newEncryptionKeyChunk(Uri keyUri, String iv, int variantIndex) {
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNBOUNDED, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(dataSource, dataSpec, scratchSpace, iv, variantIndex);
  }

  private void setEncryptionData(Uri keyUri, String iv, byte[] secretKey) {
    String trimmedIv;
    if (iv.toLowerCase(Locale.getDefault()).startsWith("0x")) {
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

  private void setMediaPlaylist(int variantIndex, HlsMediaPlaylist mediaPlaylist) {
    variantLastPlaylistLoadTimesMs[variantIndex] = SystemClock.elapsedRealtime();
    variantPlaylists[variantIndex] = mediaPlaylist;
    live |= mediaPlaylist.live;
    durationUs = live ? C.UNKNOWN_TIME_US : mediaPlaylist.durationUs;
  }

  private boolean allVariantsBlacklisted() {
    for (long variantBlacklistTime : variantBlacklistTimes) {
      if (variantBlacklistTime == 0) {
        return false;
      }
    }
    return true;
  }

  private void clearStaleBlacklistedVariants() {
    long currentTime = SystemClock.elapsedRealtime();
    for (int i = 0; i < variantBlacklistTimes.length; i++) {
      if (variantBlacklistTimes[i] != 0
          && currentTime - variantBlacklistTimes[i] > DEFAULT_PLAYLIST_BLACKLIST_MS) {
        variantBlacklistTimes[i] = 0;
      }
    }
  }

  private int getVariantIndex(Format format) {
    for (int i = 0; i < variants.length; i++) {
      if (variants[i].format.equals(format)) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  // Private classes.

  private static final class ExposedTrack {

    private final Variant[] variants;
    private final int defaultVariantIndex;

    private final int adaptiveMaxWidth;
    private final int adaptiveMaxHeight;

    public ExposedTrack(Variant fixedVariant) {
      this.variants = new Variant[] {fixedVariant};
      this.defaultVariantIndex = 0;
      this.adaptiveMaxWidth = MediaFormat.NO_VALUE;
      this.adaptiveMaxHeight = MediaFormat.NO_VALUE;
    }

    public ExposedTrack(Variant[] adaptiveVariants, int defaultVariantIndex, int maxWidth,
        int maxHeight) {
      this.variants = adaptiveVariants;
      this.defaultVariantIndex = defaultVariantIndex;
      this.adaptiveMaxWidth = maxWidth;
      this.adaptiveMaxHeight = maxHeight;
    }

  }

  private static final class MediaPlaylistChunk extends DataChunk {

    public final int variantIndex;

    private final HlsPlaylistParser playlistParser;
    private final String playlistUrl;

    private byte[] rawResponse;
    private HlsMediaPlaylist result;

    public MediaPlaylistChunk(DataSource dataSource, DataSpec dataSpec, byte[] scratchSpace,
        HlsPlaylistParser playlistParser, int variantIndex, String playlistUrl) {
      super(dataSource, dataSpec, Chunk.TYPE_MANIFEST, Chunk.TRIGGER_UNSPECIFIED, null,
          Chunk.NO_PARENT_ID, scratchSpace);
      this.variantIndex = variantIndex;
      this.playlistParser = playlistParser;
      this.playlistUrl = playlistUrl;
    }

    @Override
    protected void consume(byte[] data, int limit) throws IOException {
      rawResponse = Arrays.copyOf(data, limit);
      result = (HlsMediaPlaylist) playlistParser.parse(playlistUrl,
          new ByteArrayInputStream(rawResponse));
    }

    public byte[] getRawResponse() {
      return rawResponse;
    }

    public HlsMediaPlaylist getResult() {
      return result;
    }

  }

  private static final class EncryptionKeyChunk extends DataChunk {

    public final String iv;
    public final int variantIndex;

    private byte[] result;

    public EncryptionKeyChunk(DataSource dataSource, DataSpec dataSpec, byte[] scratchSpace,
        String iv, int variantIndex) {
      super(dataSource, dataSpec, Chunk.TYPE_DRM, Chunk.TRIGGER_UNSPECIFIED, null,
          Chunk.NO_PARENT_ID, scratchSpace);
      this.iv = iv;
      this.variantIndex = variantIndex;
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
