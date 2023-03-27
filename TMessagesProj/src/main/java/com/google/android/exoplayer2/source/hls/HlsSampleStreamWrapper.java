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

import static com.google.android.exoplayer2.source.hls.HlsChunkSource.CHUNK_PUBLICATION_STATE_PUBLISHED;
import static com.google.android.exoplayer2.source.hls.HlsChunkSource.CHUNK_PUBLICATION_STATE_REMOVED;
import static com.google.android.exoplayer2.trackselection.TrackSelectionUtil.createFallbackOptions;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleQueue.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SampleStream.ReadFlags;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Loads {@link HlsMediaChunk}s obtained from a {@link HlsChunkSource}, and provides {@link
 * SampleStream}s from which the loaded media can be consumed.
 */
/* package */ final class HlsSampleStreamWrapper
    implements Loader.Callback<Chunk>,
        Loader.ReleaseCallback,
        SequenceableLoader,
        ExtractorOutput,
        UpstreamFormatChangedListener {

  /** A callback to be notified of events. */
  public interface Callback extends SequenceableLoader.Callback<HlsSampleStreamWrapper> {

    /**
     * Called when the wrapper has been prepared.
     *
     * <p>Note: This method will be called on a later handler loop than the one on which either
     * {@link #prepareWithMultivariantPlaylistInfo} or {@link #continuePreparing} are invoked.
     */
    void onPrepared();

    /**
     * Called to schedule a {@link #continueLoading(long)} call when the playlist referred by the
     * given url changes.
     */
    void onPlaylistRefreshRequired(Uri playlistUrl);
  }

  private static final String TAG = "HlsSampleStreamWrapper";

  public static final int SAMPLE_QUEUE_INDEX_PENDING = -1;
  public static final int SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL = -2;
  public static final int SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL = -3;

  private static final Set<Integer> MAPPABLE_TYPES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_METADATA)));

  private final String uid;
  private final @C.TrackType int trackType;
  private final Callback callback;
  private final HlsChunkSource chunkSource;
  private final Allocator allocator;
  @Nullable private final Format muxedAudioFormat;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final Loader loader;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final @HlsMediaSource.MetadataType int metadataType;
  private final HlsChunkSource.HlsChunkHolder nextChunkHolder;
  private final ArrayList<HlsMediaChunk> mediaChunks;
  private final List<HlsMediaChunk> readOnlyMediaChunks;
  // Using runnables rather than in-line method references to avoid repeated allocations.
  private final Runnable maybeFinishPrepareRunnable;
  private final Runnable onTracksEndedRunnable;
  private final Handler handler;
  private final ArrayList<HlsSampleStream> hlsSampleStreams;
  private final Map<String, DrmInitData> overridingDrmInitData;

  @Nullable private Chunk loadingChunk;
  private HlsSampleQueue[] sampleQueues;
  private int[] sampleQueueTrackIds;
  private Set<Integer> sampleQueueMappingDoneByType;
  private SparseIntArray sampleQueueIndicesByType;
  private @MonotonicNonNull TrackOutput emsgUnwrappingTrackOutput;
  private int primarySampleQueueType;
  private int primarySampleQueueIndex;
  private boolean sampleQueuesBuilt;
  private boolean prepared;
  private int enabledTrackGroupCount;
  private @MonotonicNonNull Format upstreamTrackFormat;
  @Nullable private Format downstreamTrackFormat;
  private boolean released;

  // Tracks are complicated in HLS. See documentation of buildTracksFromSampleStreams for details.
  // Indexed by track (as exposed by this source).
  private @MonotonicNonNull TrackGroupArray trackGroups;
  private @MonotonicNonNull Set<TrackGroup> optionalTrackGroups;
  // Indexed by track group.
  private int @MonotonicNonNull [] trackGroupToSampleQueueIndex;
  private int primaryTrackGroupIndex;
  private boolean haveAudioVideoSampleQueues;
  private boolean[] sampleQueuesEnabledStates;
  private boolean[] sampleQueueIsAudioVideoFlags;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private boolean pendingResetUpstreamFormats;
  private boolean seenFirstTrackSelection;
  private boolean loadingFinished;

  // Accessed only by the loading thread.
  private boolean tracksEnded;
  private long sampleOffsetUs;
  @Nullable private DrmInitData drmInitData;
  @Nullable private HlsMediaChunk sourceChunk;

  /**
   * @param uid A identifier for this sample stream wrapper. Identifiers must be unique within the
   *     period.
   * @param trackType The {@link C.TrackType track type}.
   * @param callback A callback for the wrapper.
   * @param chunkSource A {@link HlsChunkSource} from which chunks to load are obtained.
   * @param overridingDrmInitData Overriding {@link DrmInitData}, keyed by protection scheme type
   *     (i.e. {@link DrmInitData#schemeType}). If the stream has {@link DrmInitData} and uses a
   *     protection scheme type for which overriding {@link DrmInitData} is provided, then the
   *     stream's {@link DrmInitData} will be overridden.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param positionUs The position from which to start loading media.
   * @param muxedAudioFormat Optional muxed audio {@link Format} as defined by the multivariant
   *     playlist.
   * @param drmSessionManager The {@link DrmSessionManager} to acquire {@link DrmSession
   *     DrmSessions} with.
   * @param drmEventDispatcher A dispatcher to notify of {@link DrmSessionEventListener} events.
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
   * @param mediaSourceEventDispatcher A dispatcher to notify of {@link MediaSourceEventListener}
   *     events.
   */
  public HlsSampleStreamWrapper(
      String uid,
      @C.TrackType int trackType,
      Callback callback,
      HlsChunkSource chunkSource,
      Map<String, DrmInitData> overridingDrmInitData,
      Allocator allocator,
      long positionUs,
      @Nullable Format muxedAudioFormat,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      @HlsMediaSource.MetadataType int metadataType) {
    this.uid = uid;
    this.trackType = trackType;
    this.callback = callback;
    this.chunkSource = chunkSource;
    this.overridingDrmInitData = overridingDrmInitData;
    this.allocator = allocator;
    this.muxedAudioFormat = muxedAudioFormat;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.metadataType = metadataType;
    loader = new Loader("Loader:HlsSampleStreamWrapper");
    nextChunkHolder = new HlsChunkSource.HlsChunkHolder();
    sampleQueueTrackIds = new int[0];
    sampleQueueMappingDoneByType = new HashSet<>(MAPPABLE_TYPES.size());
    sampleQueueIndicesByType = new SparseIntArray(MAPPABLE_TYPES.size());
    sampleQueues = new HlsSampleQueue[0];
    sampleQueueIsAudioVideoFlags = new boolean[0];
    sampleQueuesEnabledStates = new boolean[0];
    mediaChunks = new ArrayList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    hlsSampleStreams = new ArrayList<>();
    // Suppressions are needed because `this` is not initialized here.
    @SuppressWarnings("nullness:methodref.receiver.bound")
    Runnable maybeFinishPrepareRunnable = this::maybeFinishPrepare;
    this.maybeFinishPrepareRunnable = maybeFinishPrepareRunnable;
    @SuppressWarnings("nullness:methodref.receiver.bound")
    Runnable onTracksEndedRunnable = this::onTracksEnded;
    this.onTracksEndedRunnable = onTracksEndedRunnable;
    handler = Util.createHandlerForCurrentLooper();
    lastSeekPositionUs = positionUs;
    pendingResetPositionUs = positionUs;
  }

  public void continuePreparing() {
    if (!prepared) {
      continueLoading(lastSeekPositionUs);
    }
  }

  /**
   * Prepares the sample stream wrapper with multivariant playlist information.
   *
   * @param trackGroups The {@link TrackGroup TrackGroups} to expose through {@link
   *     #getTrackGroups()}.
   * @param primaryTrackGroupIndex The index of the adaptive track group.
   * @param optionalTrackGroupsIndices The indices of any {@code trackGroups} that should not
   *     trigger a failure if not found in the media playlist's segments.
   */
  public void prepareWithMultivariantPlaylistInfo(
      TrackGroup[] trackGroups, int primaryTrackGroupIndex, int... optionalTrackGroupsIndices) {
    this.trackGroups = createTrackGroupArrayWithDrmInfo(trackGroups);
    optionalTrackGroups = new HashSet<>();
    for (int optionalTrackGroupIndex : optionalTrackGroupsIndices) {
      optionalTrackGroups.add(this.trackGroups.get(optionalTrackGroupIndex));
    }
    this.primaryTrackGroupIndex = primaryTrackGroupIndex;
    handler.post(callback::onPrepared);
    setIsPrepared();
  }

  public void maybeThrowPrepareError() throws IOException {
    maybeThrowError();
    if (loadingFinished && !prepared) {
      throw ParserException.createForMalformedContainer(
          "Loading finished before preparation is complete.", /* cause= */ null);
    }
  }

  public TrackGroupArray getTrackGroups() {
    assertIsPrepared();
    return trackGroups;
  }

  public int getPrimaryTrackGroupIndex() {
    return primaryTrackGroupIndex;
  }

  public int bindSampleQueueToSampleStream(int trackGroupIndex) {
    assertIsPrepared();
    Assertions.checkNotNull(trackGroupToSampleQueueIndex);

    int sampleQueueIndex = trackGroupToSampleQueueIndex[trackGroupIndex];
    if (sampleQueueIndex == C.INDEX_UNSET) {
      return optionalTrackGroups.contains(trackGroups.get(trackGroupIndex))
          ? SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL
          : SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
    }
    if (sampleQueuesEnabledStates[sampleQueueIndex]) {
      // This sample queue is already bound to a different sample stream.
      return SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
    }
    sampleQueuesEnabledStates[sampleQueueIndex] = true;
    return sampleQueueIndex;
  }

  public void unbindSampleQueue(int trackGroupIndex) {
    assertIsPrepared();
    Assertions.checkNotNull(trackGroupToSampleQueueIndex);
    int sampleQueueIndex = trackGroupToSampleQueueIndex[trackGroupIndex];
    Assertions.checkState(sampleQueuesEnabledStates[sampleQueueIndex]);
    sampleQueuesEnabledStates[sampleQueueIndex] = false;
  }

  /**
   * Called by the parent {@link HlsMediaPeriod} when a track selection occurs.
   *
   * @param selections The renderer track selections.
   * @param mayRetainStreamFlags Flags indicating whether the existing sample stream can be retained
   *     for each selection. A {@code true} value indicates that the selection is unchanged, and
   *     that the caller does not require that the sample stream be recreated.
   * @param streams The existing sample streams, which will be updated to reflect the provided
   *     selections.
   * @param streamResetFlags Will be updated to indicate new sample streams, and sample streams that
   *     have been retained but with the requirement that the consuming renderer be reset.
   * @param positionUs The current playback position in microseconds.
   * @param forceReset If true then a reset is forced (i.e. a seek will be performed with in-buffer
   *     seeking disabled).
   * @return Whether this wrapper requires the parent {@link HlsMediaPeriod} to perform a seek as
   *     part of the track selection.
   */
  public boolean selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs,
      boolean forceReset) {
    assertIsPrepared();
    int oldEnabledTrackGroupCount = enabledTrackGroupCount;
    // Deselect old tracks.
    for (int i = 0; i < selections.length; i++) {
      HlsSampleStream stream = (HlsSampleStream) streams[i];
      if (stream != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        enabledTrackGroupCount--;
        stream.unbindSampleQueue();
        streams[i] = null;
      }
    }
    // We'll always need to seek if we're being forced to reset, or if this is a first selection to
    // a position other than the one we started preparing with, or if we're making a selection
    // having previously disabled all tracks.
    boolean seekRequired =
        forceReset
            || (seenFirstTrackSelection
                ? oldEnabledTrackGroupCount == 0
                : positionUs != lastSeekPositionUs);
    // Get the old (i.e. current before the loop below executes) primary track selection. The new
    // primary selection will equal the old one unless it's changed in the loop.
    ExoTrackSelection oldPrimaryTrackSelection = chunkSource.getTrackSelection();
    ExoTrackSelection primaryTrackSelection = oldPrimaryTrackSelection;
    // Select new tracks.
    for (int i = 0; i < selections.length; i++) {
      ExoTrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }
      int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
      if (trackGroupIndex == primaryTrackGroupIndex) {
        primaryTrackSelection = selection;
        chunkSource.setTrackSelection(selection);
      }
      if (streams[i] == null) {
        enabledTrackGroupCount++;
        streams[i] = new HlsSampleStream(this, trackGroupIndex);
        streamResetFlags[i] = true;
        if (trackGroupToSampleQueueIndex != null) {
          ((HlsSampleStream) streams[i]).bindSampleQueue();
          // If there's still a chance of avoiding a seek, try and seek within the sample queue.
          if (!seekRequired) {
            SampleQueue sampleQueue = sampleQueues[trackGroupToSampleQueueIndex[trackGroupIndex]];
            // A seek can be avoided if we're able to seek to the current playback position in
            // the sample queue, or if we haven't read anything from the queue since the previous
            // seek (this case is common for sparse tracks such as metadata tracks). In all other
            // cases a seek is required.
            seekRequired =
                !sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ true)
                    && sampleQueue.getReadIndex() != 0;
          }
        }
      }
    }

    if (enabledTrackGroupCount == 0) {
      chunkSource.reset();
      downstreamTrackFormat = null;
      pendingResetUpstreamFormats = true;
      mediaChunks.clear();
      if (loader.isLoading()) {
        if (sampleQueuesBuilt) {
          // Discard as much as we can synchronously.
          for (SampleQueue sampleQueue : sampleQueues) {
            sampleQueue.discardToEnd();
          }
        }
        loader.cancelLoading();
      } else {
        resetSampleQueues();
      }
    } else {
      if (!mediaChunks.isEmpty()
          && !Util.areEqual(primaryTrackSelection, oldPrimaryTrackSelection)) {
        // The primary track selection has changed and we have buffered media. The buffered media
        // may need to be discarded.
        boolean primarySampleQueueDirty = false;
        if (!seenFirstTrackSelection) {
          long bufferedDurationUs = positionUs < 0 ? -positionUs : 0;
          HlsMediaChunk lastMediaChunk = getLastMediaChunk();
          MediaChunkIterator[] mediaChunkIterators =
              chunkSource.createMediaChunkIterators(lastMediaChunk, positionUs);
          primaryTrackSelection.updateSelectedTrack(
              positionUs,
              bufferedDurationUs,
              C.TIME_UNSET,
              readOnlyMediaChunks,
              mediaChunkIterators);
          int chunkIndex = chunkSource.getTrackGroup().indexOf(lastMediaChunk.trackFormat);
          if (primaryTrackSelection.getSelectedIndexInTrackGroup() != chunkIndex) {
            // This is the first selection and the chunk loaded during preparation does not match
            // the initially selected format.
            primarySampleQueueDirty = true;
          }
        } else {
          // The primary sample queue contains media buffered for the old primary track selection.
          primarySampleQueueDirty = true;
        }
        if (primarySampleQueueDirty) {
          forceReset = true;
          seekRequired = true;
          pendingResetUpstreamFormats = true;
        }
      }
      if (seekRequired) {
        seekToUs(positionUs, forceReset);
        // We'll need to reset renderers consuming from all streams due to the seek.
        for (int i = 0; i < streams.length; i++) {
          if (streams[i] != null) {
            streamResetFlags[i] = true;
          }
        }
      }
    }

    updateSampleStreams(streams);
    seenFirstTrackSelection = true;
    return seekRequired;
  }

  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (!sampleQueuesBuilt || isPendingReset()) {
      return;
    }
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      sampleQueues[i].discardTo(positionUs, toKeyframe, sampleQueuesEnabledStates[i]);
    }
  }

  /**
   * Attempts to seek to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   * @param forceReset If true then a reset is forced (i.e. in-buffer seeking is disabled).
   * @return Whether the wrapper was reset, meaning the wrapped sample queues were reset. If false,
   *     an in-buffer seek was performed.
   */
  public boolean seekToUs(long positionUs, boolean forceReset) {
    lastSeekPositionUs = positionUs;
    if (isPendingReset()) {
      // A reset is already pending. We only need to update its position.
      pendingResetPositionUs = positionUs;
      return true;
    }

    // If we're not forced to reset, try and seek within the buffer.
    if (sampleQueuesBuilt && !forceReset && seekInsideBufferUs(positionUs)) {
      return false;
    }

    // We can't seek inside the buffer, and so need to reset.
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    mediaChunks.clear();
    if (loader.isLoading()) {
      if (sampleQueuesBuilt) {
        // Discard as much as we can synchronously.
        for (SampleQueue sampleQueue : sampleQueues) {
          sampleQueue.discardToEnd();
        }
      }
      loader.cancelLoading();
    } else {
      loader.clearFatalError();
      resetSampleQueues();
    }
    return true;
  }

  /** Called when the playlist is updated. */
  public void onPlaylistUpdated() {
    if (mediaChunks.isEmpty()) {
      return;
    }
    HlsMediaChunk lastMediaChunk = Iterables.getLast(mediaChunks);
    @HlsChunkSource.ChunkPublicationState
    int chunkState = chunkSource.getChunkPublicationState(lastMediaChunk);
    if (chunkState == CHUNK_PUBLICATION_STATE_PUBLISHED) {
      lastMediaChunk.publish();
    } else if (chunkState == CHUNK_PUBLICATION_STATE_REMOVED
        && !loadingFinished
        && loader.isLoading()) {
      loader.cancelLoading();
    }
  }

  public void release() {
    if (prepared) {
      // Discard as much as we can synchronously. We only do this if we're prepared, since otherwise
      // sampleQueues may still be being modified by the loading thread.
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.preRelease();
      }
    }
    loader.release(this);
    handler.removeCallbacksAndMessages(null);
    released = true;
    hlsSampleStreams.clear();
  }

  @Override
  public void onLoaderReleased() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.release();
    }
  }

  public void setIsTimestampMaster(boolean isTimestampMaster) {
    chunkSource.setIsTimestampMaster(isTimestampMaster);
  }

  /**
   * Called if an error is encountered while loading a playlist.
   *
   * @param playlistUrl The {@link Uri} of the playlist whose load encountered an error.
   * @param loadErrorInfo The load error info.
   * @param forceRetry Whether retry should be forced without considering exclusion.
   * @return True if excluding did not encounter errors. False otherwise.
   */
  public boolean onPlaylistError(Uri playlistUrl, LoadErrorInfo loadErrorInfo, boolean forceRetry) {
    if (!chunkSource.obtainsChunksForPlaylist(playlistUrl)) {
      // Return early if the chunk source doesn't deliver chunks for the failing playlist.
      return true;
    }
    long exclusionDurationMs = C.TIME_UNSET;
    if (!forceRetry) {
      @Nullable
      LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
          loadErrorHandlingPolicy.getFallbackSelectionFor(
              createFallbackOptions(chunkSource.getTrackSelection()), loadErrorInfo);
      if (fallbackSelection != null
          && fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
        exclusionDurationMs = fallbackSelection.exclusionDurationMs;
      }
    }
    // We must call ChunkSource.onPlaylistError in any case to give the chunk source the chance to
    // mark the playlist as failing.
    return chunkSource.onPlaylistError(playlistUrl, exclusionDurationMs)
        && exclusionDurationMs != C.TIME_UNSET;
  }

  /** Returns whether the primary sample stream is {@link C#TRACK_TYPE_VIDEO}. */
  public boolean isVideoSampleStream() {
    return primarySampleQueueType == C.TRACK_TYPE_VIDEO;
  }

  /**
   * Adjusts a seek position given the specified {@link SeekParameters}.
   *
   * @param positionUs The seek position in microseconds.
   * @param seekParameters Parameters that control how the seek is performed.
   * @return The adjusted seek position, in microseconds.
   */
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return chunkSource.getAdjustedSeekPositionUs(positionUs, seekParameters);
  }

  // SampleStream implementation.

  public boolean isReady(int sampleQueueIndex) {
    return !isPendingReset() && sampleQueues[sampleQueueIndex].isReady(loadingFinished);
  }

  public void maybeThrowError(int sampleQueueIndex) throws IOException {
    maybeThrowError();
    sampleQueues[sampleQueueIndex].maybeThrowError();
  }

  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
  }

  public int readData(
      int sampleQueueIndex,
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      @ReadFlags int readFlags) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }

    // TODO: Split into discard (in discardBuffer) and format change (here and in skipData) steps.
    if (!mediaChunks.isEmpty()) {
      int discardToMediaChunkIndex = 0;
      while (discardToMediaChunkIndex < mediaChunks.size() - 1
          && finishedReadingChunk(mediaChunks.get(discardToMediaChunkIndex))) {
        discardToMediaChunkIndex++;
      }
      Util.removeRange(mediaChunks, 0, discardToMediaChunkIndex);
      HlsMediaChunk currentChunk = mediaChunks.get(0);
      Format trackFormat = currentChunk.trackFormat;
      if (!trackFormat.equals(downstreamTrackFormat)) {
        mediaSourceEventDispatcher.downstreamFormatChanged(
            trackType,
            trackFormat,
            currentChunk.trackSelectionReason,
            currentChunk.trackSelectionData,
            currentChunk.startTimeUs);
      }
      downstreamTrackFormat = trackFormat;
    }

    if (!mediaChunks.isEmpty() && !mediaChunks.get(0).isPublished()) {
      // Don't read into preload chunks until we can be sure they are permanently published.
      return C.RESULT_NOTHING_READ;
    }

    int result =
        sampleQueues[sampleQueueIndex].read(formatHolder, buffer, readFlags, loadingFinished);
    if (result == C.RESULT_FORMAT_READ) {
      Format format = Assertions.checkNotNull(formatHolder.format);
      if (sampleQueueIndex == primarySampleQueueIndex) {
        // Fill in primary sample format with information from the track format.
        int chunkUid = sampleQueues[sampleQueueIndex].peekSourceId();
        int chunkIndex = 0;
        while (chunkIndex < mediaChunks.size() && mediaChunks.get(chunkIndex).uid != chunkUid) {
          chunkIndex++;
        }
        Format trackFormat =
            chunkIndex < mediaChunks.size()
                ? mediaChunks.get(chunkIndex).trackFormat
                : Assertions.checkNotNull(upstreamTrackFormat);
        format = format.withManifestFormatInfo(trackFormat);
      }
      formatHolder.format = format;
    }
    return result;
  }

  public int skipData(int sampleQueueIndex, long positionUs) {
    if (isPendingReset()) {
      return 0;
    }

    SampleQueue sampleQueue = sampleQueues[sampleQueueIndex];
    int skipCount = sampleQueue.getSkipCount(positionUs, loadingFinished);

    // Ensure we don't skip into preload chunks until we can be sure they are permanently published.
    @Nullable HlsMediaChunk lastChunk = Iterables.getLast(mediaChunks, /* defaultValue= */ null);
    if (lastChunk != null && !lastChunk.isPublished()) {
      int readIndex = sampleQueue.getReadIndex();
      int firstSampleIndex = lastChunk.getFirstSampleIndex(sampleQueueIndex);
      skipCount = min(skipCount, firstSampleIndex - readIndex);
    }

    sampleQueue.skip(skipCount);
    return skipCount;
  }

  // SequenceableLoader implementation

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      HlsMediaChunk lastMediaChunk = getLastMediaChunk();
      HlsMediaChunk lastCompletedMediaChunk =
          lastMediaChunk.isLoadCompleted()
              ? lastMediaChunk
              : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      if (sampleQueuesBuilt) {
        for (SampleQueue sampleQueue : sampleQueues) {
          bufferedPositionUs = max(bufferedPositionUs, sampleQueue.getLargestQueuedTimestampUs());
        }
      }
      return bufferedPositionUs;
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.TIME_END_OF_SOURCE : getLastMediaChunk().endTimeUs;
    }
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading() || loader.hasFatalError()) {
      return false;
    }

    List<HlsMediaChunk> chunkQueue;
    long loadPositionUs;
    if (isPendingReset()) {
      chunkQueue = Collections.emptyList();
      loadPositionUs = pendingResetPositionUs;
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.setStartTimeUs(pendingResetPositionUs);
      }
    } else {
      chunkQueue = readOnlyMediaChunks;
      HlsMediaChunk lastMediaChunk = getLastMediaChunk();
      loadPositionUs =
          lastMediaChunk.isLoadCompleted()
              ? lastMediaChunk.endTimeUs
              : max(lastSeekPositionUs, lastMediaChunk.startTimeUs);
    }
    nextChunkHolder.clear();
    chunkSource.getNextChunk(
        positionUs,
        loadPositionUs,
        chunkQueue,
        /* allowEndOfStream= */ prepared || !chunkQueue.isEmpty(),
        nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    @Nullable Chunk loadable = nextChunkHolder.chunk;
    @Nullable Uri playlistUrlToLoad = nextChunkHolder.playlistUrl;

    if (endOfStream) {
      pendingResetPositionUs = C.TIME_UNSET;
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      if (playlistUrlToLoad != null) {
        callback.onPlaylistRefreshRequired(playlistUrlToLoad);
      }
      return false;
    }

    if (isMediaChunk(loadable)) {
      initMediaChunkLoad((HlsMediaChunk) loadable);
    }
    loadingChunk = loadable;
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    mediaSourceEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs),
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    return true;
  }

  @Override
  public boolean isLoading() {
    return loader.isLoading();
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    if (loader.hasFatalError() || isPendingReset()) {
      return;
    }

    if (loader.isLoading()) {
      Assertions.checkNotNull(loadingChunk);
      if (chunkSource.shouldCancelLoad(positionUs, loadingChunk, readOnlyMediaChunks)) {
        loader.cancelLoading();
      }
      return;
    }

    int newQueueSize = readOnlyMediaChunks.size();
    while (newQueueSize > 0
        && chunkSource.getChunkPublicationState(readOnlyMediaChunks.get(newQueueSize - 1))
            == CHUNK_PUBLICATION_STATE_REMOVED) {
      newQueueSize--;
    }
    if (newQueueSize < readOnlyMediaChunks.size()) {
      discardUpstream(newQueueSize);
    }

    int preferredQueueSize = chunkSource.getPreferredQueueSize(positionUs, readOnlyMediaChunks);
    if (preferredQueueSize < mediaChunks.size()) {
      discardUpstream(preferredQueueSize);
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    loadingChunk = null;
    chunkSource.onChunkLoadCompleted(loadable);
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    mediaSourceEventDispatcher.loadCompleted(
        loadEventInfo,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    if (!prepared) {
      continueLoading(lastSeekPositionUs);
    } else {
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public void onLoadCanceled(
      Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
    loadingChunk = null;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    mediaSourceEventDispatcher.loadCanceled(
        loadEventInfo,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    if (!released) {
      if (isPendingReset() || enabledTrackGroupCount == 0) {
        resetSampleQueues();
      }
      if (enabledTrackGroupCount > 0) {
        callback.onContinueLoadingRequested(this);
      }
    }
  }

  @Override
  public LoadErrorAction onLoadError(
      Chunk loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    boolean isMediaChunk = isMediaChunk(loadable);
    if (isMediaChunk
        && !((HlsMediaChunk) loadable).isPublished()
        && error instanceof HttpDataSource.InvalidResponseCodeException) {
      int responseCode = ((HttpDataSource.InvalidResponseCodeException) error).responseCode;
      if (responseCode == 410 || responseCode == 404) {
        // According to RFC 8216, Section 6.2.6 a server should respond with an HTTP 404 (Not found)
        // for requests of hinted parts that are replaced and not available anymore. We've seen test
        // streams with HTTP 410 (Gone) also.
        return Loader.RETRY;
      }
    }
    long bytesLoaded = loadable.bytesLoaded();
    boolean exclusionSucceeded = false;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            bytesLoaded);
    MediaLoadData mediaLoadData =
        new MediaLoadData(
            loadable.type,
            trackType,
            loadable.trackFormat,
            loadable.trackSelectionReason,
            loadable.trackSelectionData,
            Util.usToMs(loadable.startTimeUs),
            Util.usToMs(loadable.endTimeUs));
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount);
    LoadErrorAction loadErrorAction;
    @Nullable
    LoadErrorHandlingPolicy.FallbackSelection fallbackSelection =
        loadErrorHandlingPolicy.getFallbackSelectionFor(
            createFallbackOptions(chunkSource.getTrackSelection()), loadErrorInfo);
    if (fallbackSelection != null
        && fallbackSelection.type == LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK) {
      exclusionSucceeded =
          chunkSource.maybeExcludeTrack(loadable, fallbackSelection.exclusionDurationMs);
    }

    if (exclusionSucceeded) {
      if (isMediaChunk && bytesLoaded == 0) {
        HlsMediaChunk removed = mediaChunks.remove(mediaChunks.size() - 1);
        Assertions.checkState(removed == loadable);
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        } else {
          Iterables.getLast(mediaChunks).invalidateExtractor();
        }
      }
      loadErrorAction = Loader.DONT_RETRY;
    } else /* did not exclude */ {
      long retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
      loadErrorAction =
          retryDelayMs != C.TIME_UNSET
              ? Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs)
              : Loader.DONT_RETRY_FATAL;
    }

    boolean wasCanceled = !loadErrorAction.isRetry();
    mediaSourceEventDispatcher.loadError(
        loadEventInfo,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        error,
        wasCanceled);
    if (wasCanceled) {
      loadingChunk = null;
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }

    if (exclusionSucceeded) {
      if (!prepared) {
        continueLoading(lastSeekPositionUs);
      } else {
        callback.onContinueLoadingRequested(this);
      }
    }
    return loadErrorAction;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Performs initialization for a media chunk that's about to start loading.
   *
   * @param chunk The media chunk that's about to start loading.
   */
  private void initMediaChunkLoad(HlsMediaChunk chunk) {
    sourceChunk = chunk;
    upstreamTrackFormat = chunk.trackFormat;
    pendingResetPositionUs = C.TIME_UNSET;
    mediaChunks.add(chunk);
    ImmutableList.Builder<Integer> sampleQueueWriteIndicesBuilder = ImmutableList.builder();
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueueWriteIndicesBuilder.add(sampleQueue.getWriteIndex());
    }
    chunk.init(/* output= */ this, sampleQueueWriteIndicesBuilder.build());
    for (HlsSampleQueue sampleQueue : sampleQueues) {
      sampleQueue.setSourceChunk(chunk);
      if (chunk.shouldSpliceIn) {
        sampleQueue.splice();
      }
    }
  }

  private void discardUpstream(int preferredQueueSize) {
    Assertions.checkState(!loader.isLoading());

    int newQueueSize = C.LENGTH_UNSET;
    for (int i = preferredQueueSize; i < mediaChunks.size(); i++) {
      if (canDiscardUpstreamMediaChunksFromIndex(i)) {
        newQueueSize = i;
        break;
      }
    }
    if (newQueueSize == C.LENGTH_UNSET) {
      return;
    }

    long endTimeUs = getLastMediaChunk().endTimeUs;
    HlsMediaChunk firstRemovedChunk = discardUpstreamMediaChunksFromIndex(newQueueSize);
    if (mediaChunks.isEmpty()) {
      pendingResetPositionUs = lastSeekPositionUs;
    } else {
      Iterables.getLast(mediaChunks).invalidateExtractor();
    }
    loadingFinished = false;

    mediaSourceEventDispatcher.upstreamDiscarded(
        primarySampleQueueType, firstRemovedChunk.startTimeUs, endTimeUs);
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public TrackOutput track(int id, int type) {
    @Nullable TrackOutput trackOutput = null;
    if (MAPPABLE_TYPES.contains(type)) {
      // Track types in MAPPABLE_TYPES are handled manually to ignore IDs.
      trackOutput = getMappedTrackOutput(id, type);
    } else /* non-mappable type track */ {
      for (int i = 0; i < sampleQueues.length; i++) {
        if (sampleQueueTrackIds[i] == id) {
          trackOutput = sampleQueues[i];
          break;
        }
      }
    }

    if (trackOutput == null) {
      if (tracksEnded) {
        return createFakeTrackOutput(id, type);
      } else {
        // The relevant SampleQueue hasn't been constructed yet - so construct it.
        trackOutput = createSampleQueue(id, type);
      }
    }

    if (type == C.TRACK_TYPE_METADATA) {
      if (emsgUnwrappingTrackOutput == null) {
        emsgUnwrappingTrackOutput = new EmsgUnwrappingTrackOutput(trackOutput, metadataType);
      }
      return emsgUnwrappingTrackOutput;
    }
    return trackOutput;
  }

  /**
   * Returns the {@link TrackOutput} for the provided {@code type} and {@code id}, or null if none
   * has been created yet.
   *
   * <p>If a {@link SampleQueue} for {@code type} has been created and is mapped, but it has a
   * different ID, then return a {@link DummyTrackOutput} that does nothing.
   *
   * <p>If a {@link SampleQueue} for {@code type} has been created but is not mapped, then map it to
   * this {@code id} and return it. This situation can happen after a call to {@link
   * #onNewExtractor}.
   *
   * @param id The ID of the track.
   * @param type The type of the track, must be one of {@link #MAPPABLE_TYPES}.
   * @return The mapped {@link TrackOutput}, or null if it's not been created yet.
   */
  @Nullable
  private TrackOutput getMappedTrackOutput(int id, int type) {
    Assertions.checkArgument(MAPPABLE_TYPES.contains(type));
    int sampleQueueIndex = sampleQueueIndicesByType.get(type, C.INDEX_UNSET);
    if (sampleQueueIndex == C.INDEX_UNSET) {
      return null;
    }

    if (sampleQueueMappingDoneByType.add(type)) {
      sampleQueueTrackIds[sampleQueueIndex] = id;
    }
    return sampleQueueTrackIds[sampleQueueIndex] == id
        ? sampleQueues[sampleQueueIndex]
        : createFakeTrackOutput(id, type);
  }

  private SampleQueue createSampleQueue(int id, int type) {
    int trackCount = sampleQueues.length;

    boolean isAudioVideo = type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO;
    HlsSampleQueue sampleQueue =
        new HlsSampleQueue(allocator, drmSessionManager, drmEventDispatcher, overridingDrmInitData);
    sampleQueue.setStartTimeUs(lastSeekPositionUs);
    if (isAudioVideo) {
      sampleQueue.setDrmInitData(drmInitData);
    }
    sampleQueue.setSampleOffsetUs(sampleOffsetUs);
    if (sourceChunk != null) {
      sampleQueue.setSourceChunk(sourceChunk);
    }
    sampleQueue.setUpstreamFormatChangeListener(this);
    sampleQueueTrackIds = Arrays.copyOf(sampleQueueTrackIds, trackCount + 1);
    sampleQueueTrackIds[trackCount] = id;
    sampleQueues = Util.nullSafeArrayAppend(sampleQueues, sampleQueue);
    sampleQueueIsAudioVideoFlags = Arrays.copyOf(sampleQueueIsAudioVideoFlags, trackCount + 1);
    sampleQueueIsAudioVideoFlags[trackCount] = isAudioVideo;
    haveAudioVideoSampleQueues |= sampleQueueIsAudioVideoFlags[trackCount];
    sampleQueueMappingDoneByType.add(type);
    sampleQueueIndicesByType.append(type, trackCount);
    if (getTrackTypeScore(type) > getTrackTypeScore(primarySampleQueueType)) {
      primarySampleQueueIndex = trackCount;
      primarySampleQueueType = type;
    }
    sampleQueuesEnabledStates = Arrays.copyOf(sampleQueuesEnabledStates, trackCount + 1);
    return sampleQueue;
  }

  @Override
  public void endTracks() {
    tracksEnded = true;
    handler.post(onTracksEndedRunnable);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    // Do nothing.
  }

  // UpstreamFormatChangedListener implementation. Called by the loading thread.

  @Override
  public void onUpstreamFormatChanged(Format format) {
    handler.post(maybeFinishPrepareRunnable);
  }

  // Called by the loading thread.

  /** Called when an {@link HlsMediaChunk} starts extracting media with a new {@link Extractor}. */
  public void onNewExtractor() {
    sampleQueueMappingDoneByType.clear();
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples that
   * are subsequently loaded by this wrapper.
   *
   * @param sampleOffsetUs The timestamp offset in microseconds.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    if (this.sampleOffsetUs != sampleOffsetUs) {
      this.sampleOffsetUs = sampleOffsetUs;
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.setSampleOffsetUs(sampleOffsetUs);
      }
    }
  }

  /**
   * Sets default {@link DrmInitData} for samples that are subsequently loaded by this wrapper.
   *
   * <p>This method should be called prior to loading each {@link HlsMediaChunk}. The {@link
   * DrmInitData} passed should be that of an EXT-X-KEY tag that applies to the chunk, or {@code
   * null} otherwise.
   *
   * <p>The final {@link DrmInitData} for subsequently queued samples is determined as followed:
   *
   * <ol>
   *   <li>It is initially set to {@code drmInitData}, unless {@code drmInitData} is null in which
   *       case it's set to {@link Format#drmInitData} of the upstream {@link Format}.
   *   <li>If the initial {@link DrmInitData} is non-null and {@link #overridingDrmInitData}
   *       contains an entry whose key matches the {@link DrmInitData#schemeType}, then the sample's
   *       {@link DrmInitData} is overridden to be this entry's value.
   * </ol>
   *
   * <p>
   *
   * @param drmInitData The default {@link DrmInitData} for samples that are subsequently queued. If
   *     non-null then it takes precedence over {@link Format#drmInitData} of the upstream {@link
   *     Format}, but will still be overridden by a matching override in {@link
   *     #overridingDrmInitData}.
   */
  public void setDrmInitData(@Nullable DrmInitData drmInitData) {
    if (!Util.areEqual(this.drmInitData, drmInitData)) {
      this.drmInitData = drmInitData;
      for (int i = 0; i < sampleQueues.length; i++) {
        if (sampleQueueIsAudioVideoFlags[i]) {
          sampleQueues[i].setDrmInitData(drmInitData);
        }
      }
    }
  }

  // Internal methods.

  private void updateSampleStreams(@NullableType SampleStream[] streams) {
    hlsSampleStreams.clear();
    for (@Nullable SampleStream stream : streams) {
      if (stream != null) {
        hlsSampleStreams.add((HlsSampleStream) stream);
      }
    }
  }

  private boolean finishedReadingChunk(HlsMediaChunk chunk) {
    int chunkUid = chunk.uid;
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      if (sampleQueuesEnabledStates[i] && sampleQueues[i].peekSourceId() == chunkUid) {
        return false;
      }
    }
    return true;
  }

  private boolean canDiscardUpstreamMediaChunksFromIndex(int mediaChunkIndex) {
    for (int i = mediaChunkIndex; i < mediaChunks.size(); i++) {
      if (mediaChunks.get(i).shouldSpliceIn) {
        // Discarding not possible because a spliced-in chunk potentially removed sample metadata
        // from the previous chunks.
        // TODO: Keep sample metadata to allow restoring these chunks [internal b/159904763].
        return false;
      }
    }
    HlsMediaChunk mediaChunk = mediaChunks.get(mediaChunkIndex);
    for (int i = 0; i < sampleQueues.length; i++) {
      int discardFromIndex = mediaChunk.getFirstSampleIndex(/* sampleQueueIndex= */ i);
      if (sampleQueues[i].getReadIndex() > discardFromIndex) {
        // Discarding not possible because we already read from the chunk.
        // TODO: Sparse tracks (e.g. ID3) may prevent discarding in almost all cases because it
        // means that most chunks have been read from already. See [internal b/161126666].
        return false;
      }
    }
    return true;
  }

  private HlsMediaChunk discardUpstreamMediaChunksFromIndex(int chunkIndex) {
    HlsMediaChunk firstRemovedChunk = mediaChunks.get(chunkIndex);
    Util.removeRange(mediaChunks, /* fromIndex= */ chunkIndex, /* toIndex= */ mediaChunks.size());
    for (int i = 0; i < sampleQueues.length; i++) {
      int discardFromIndex = firstRemovedChunk.getFirstSampleIndex(/* sampleQueueIndex= */ i);
      sampleQueues[i].discardUpstreamSamples(discardFromIndex);
    }
    return firstRemovedChunk;
  }

  private void resetSampleQueues() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.reset(pendingResetUpstreamFormats);
    }
    pendingResetUpstreamFormats = false;
  }

  private void onTracksEnded() {
    sampleQueuesBuilt = true;
    maybeFinishPrepare();
  }

  private void maybeFinishPrepare() {
    if (released || trackGroupToSampleQueueIndex != null || !sampleQueuesBuilt) {
      return;
    }
    for (SampleQueue sampleQueue : sampleQueues) {
      if (sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }
    if (trackGroups != null) {
      // The track groups were created with multivariant playlist information. They only need to be
      // mapped to a sample queue.
      mapSampleQueuesToMatchTrackGroups();
    } else {
      // Tracks are created using media segment information.
      buildTracksFromSampleStreams();
      setIsPrepared();
      callback.onPrepared();
    }
  }

  @RequiresNonNull("trackGroups")
  @EnsuresNonNull("trackGroupToSampleQueueIndex")
  private void mapSampleQueuesToMatchTrackGroups() {
    int trackGroupCount = trackGroups.length;
    trackGroupToSampleQueueIndex = new int[trackGroupCount];
    Arrays.fill(trackGroupToSampleQueueIndex, C.INDEX_UNSET);
    for (int i = 0; i < trackGroupCount; i++) {
      for (int queueIndex = 0; queueIndex < sampleQueues.length; queueIndex++) {
        SampleQueue sampleQueue = sampleQueues[queueIndex];
        Format upstreamFormat = Assertions.checkStateNotNull(sampleQueue.getUpstreamFormat());
        if (formatsMatch(upstreamFormat, trackGroups.get(i).getFormat(0))) {
          trackGroupToSampleQueueIndex[i] = queueIndex;
          break;
        }
      }
    }
    for (HlsSampleStream sampleStream : hlsSampleStreams) {
      sampleStream.bindSampleQueue();
    }
  }

  /**
   * Builds tracks that are exposed by this {@link HlsSampleStreamWrapper} instance, as well as
   * internal data-structures required for operation.
   *
   * <p>Tracks in HLS are complicated. A HLS multivariant playlist contains a number of "variants".
   * Each variant stream typically contains muxed video, audio and (possibly) additional audio,
   * metadata and caption tracks. We wish to allow the user to select between an adaptive track that
   * spans all variants, as well as each individual variant. If multiple audio tracks are present
   * within each variant then we wish to allow the user to select between those also.
   *
   * <p>To do this, tracks are constructed as follows. The {@link HlsChunkSource} exposes (N+1)
   * tracks, where N is the number of variants defined in the HLS multivariant playlist. These
   * consist of one adaptive track defined to span all variants and a track for each individual
   * variant. The adaptive track is initially selected. The extractor is then prepared to discover
   * the tracks inside of each variant stream. The two sets of tracks are then combined by this
   * method to create a third set, which is the set exposed by this {@link HlsSampleStreamWrapper}:
   *
   * <ul>
   *   <li>The extractor tracks are inspected to infer a "primary" track type. If a video track is
   *       present then it is always the primary type. If not, audio is the primary type if present.
   *       Else text is the primary type if present. Else there is no primary type.
   *   <li>If there is exactly one extractor track of the primary type, it's expanded into (N+1)
   *       exposed tracks, all of which correspond to the primary extractor track and each of which
   *       corresponds to a different chunk source track. Selecting one of these tracks has the
   *       effect of switching the selected track on the chunk source.
   *   <li>All other extractor tracks are exposed directly. Selecting one of these tracks has the
   *       effect of selecting an extractor track, leaving the selected track on the chunk source
   *       unchanged.
   * </ul>
   */
  @EnsuresNonNull({"trackGroups", "optionalTrackGroups", "trackGroupToSampleQueueIndex"})
  private void buildTracksFromSampleStreams() {
    // Iterate through the extractor tracks to discover the "primary" track type, and the index
    // of the single track of this type.
    int primaryExtractorTrackType = C.TRACK_TYPE_NONE;
    int primaryExtractorTrackIndex = C.INDEX_UNSET;
    int extractorTrackCount = sampleQueues.length;
    for (int i = 0; i < extractorTrackCount; i++) {
      @Nullable
      String sampleMimeType =
          Assertions.checkStateNotNull(sampleQueues[i].getUpstreamFormat()).sampleMimeType;
      int trackType;
      if (MimeTypes.isVideo(sampleMimeType)) {
        trackType = C.TRACK_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        trackType = C.TRACK_TYPE_AUDIO;
      } else if (MimeTypes.isText(sampleMimeType)) {
        trackType = C.TRACK_TYPE_TEXT;
      } else {
        trackType = C.TRACK_TYPE_NONE;
      }
      if (getTrackTypeScore(trackType) > getTrackTypeScore(primaryExtractorTrackType)) {
        primaryExtractorTrackType = trackType;
        primaryExtractorTrackIndex = i;
      } else if (trackType == primaryExtractorTrackType
          && primaryExtractorTrackIndex != C.INDEX_UNSET) {
        // We have multiple tracks of the primary type. We only want an index if there only exists a
        // single track of the primary type, so unset the index again.
        primaryExtractorTrackIndex = C.INDEX_UNSET;
      }
    }

    TrackGroup chunkSourceTrackGroup = chunkSource.getTrackGroup();
    int chunkSourceTrackCount = chunkSourceTrackGroup.length;

    // Instantiate the necessary internal data-structures.
    primaryTrackGroupIndex = C.INDEX_UNSET;
    trackGroupToSampleQueueIndex = new int[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      trackGroupToSampleQueueIndex[i] = i;
    }

    // Construct the set of exposed track groups.
    TrackGroup[] trackGroups = new TrackGroup[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      Format sampleFormat = Assertions.checkStateNotNull(sampleQueues[i].getUpstreamFormat());
      if (i == primaryExtractorTrackIndex) {
        Format[] formats = new Format[chunkSourceTrackCount];
        for (int j = 0; j < chunkSourceTrackCount; j++) {
          Format playlistFormat = chunkSourceTrackGroup.getFormat(j);
          if (primaryExtractorTrackType == C.TRACK_TYPE_AUDIO && muxedAudioFormat != null) {
            playlistFormat = playlistFormat.withManifestFormatInfo(muxedAudioFormat);
          }
          // If there's only a single variant (chunkSourceTrackCount == 1) then we can safely
          // retain all fields from sampleFormat. Else we need to use deriveFormat to retain only
          // the fields that will be the same for all variants.
          formats[j] =
              chunkSourceTrackCount == 1
                  ? sampleFormat.withManifestFormatInfo(playlistFormat)
                  : deriveFormat(playlistFormat, sampleFormat, /* propagateBitrates= */ true);
        }
        trackGroups[i] = new TrackGroup(uid, formats);
        primaryTrackGroupIndex = i;
      } else {
        @Nullable
        Format playlistFormat =
            primaryExtractorTrackType == C.TRACK_TYPE_VIDEO
                    && MimeTypes.isAudio(sampleFormat.sampleMimeType)
                ? muxedAudioFormat
                : null;
        String muxedTrackGroupId = uid + ":muxed:" + (i < primaryExtractorTrackIndex ? i : i - 1);
        trackGroups[i] =
            new TrackGroup(
                muxedTrackGroupId,
                deriveFormat(playlistFormat, sampleFormat, /* propagateBitrates= */ false));
      }
    }
    this.trackGroups = createTrackGroupArrayWithDrmInfo(trackGroups);
    Assertions.checkState(optionalTrackGroups == null);
    optionalTrackGroups = Collections.emptySet();
  }

  private TrackGroupArray createTrackGroupArrayWithDrmInfo(TrackGroup[] trackGroups) {
    for (int i = 0; i < trackGroups.length; i++) {
      TrackGroup trackGroup = trackGroups[i];
      Format[] exposedFormats = new Format[trackGroup.length];
      for (int j = 0; j < trackGroup.length; j++) {
        Format format = trackGroup.getFormat(j);
        exposedFormats[j] = format.copyWithCryptoType(drmSessionManager.getCryptoType(format));
      }
      trackGroups[i] = new TrackGroup(trackGroup.id, exposedFormats);
    }
    return new TrackGroupArray(trackGroups);
  }

  private HlsMediaChunk getLastMediaChunk() {
    return mediaChunks.get(mediaChunks.size() - 1);
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  /**
   * Attempts to seek to the specified position within the sample queues.
   *
   * @param positionUs The seek position in microseconds.
   * @return Whether the in-buffer seek was successful.
   */
  private boolean seekInsideBufferUs(long positionUs) {
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      SampleQueue sampleQueue = sampleQueues[i];
      boolean seekInsideQueue = sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ false);
      // If we have AV tracks then an in-queue seek is successful if the seek into every AV queue
      // is successful. We ignore whether seeks within non-AV queues are successful in this case, as
      // they may be sparse or poorly interleaved. If we only have non-AV tracks then a seek is
      // successful only if the seek into every queue succeeds.
      if (!seekInsideQueue && (sampleQueueIsAudioVideoFlags[i] || !haveAudioVideoSampleQueues)) {
        return false;
      }
    }
    return true;
  }

  @RequiresNonNull({"trackGroups", "optionalTrackGroups"})
  private void setIsPrepared() {
    prepared = true;
  }

  @EnsuresNonNull({"trackGroups", "optionalTrackGroups"})
  private void assertIsPrepared() {
    Assertions.checkState(prepared);
    Assertions.checkNotNull(trackGroups);
    Assertions.checkNotNull(optionalTrackGroups);
  }

  /**
   * Scores a track type. Where multiple tracks are muxed into a container, the track with the
   * highest score is the primary track.
   *
   * @param trackType The track type.
   * @return The score.
   */
  private static int getTrackTypeScore(int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_VIDEO:
        return 3;
      case C.TRACK_TYPE_AUDIO:
        return 2;
      case C.TRACK_TYPE_TEXT:
        return 1;
      default:
        return 0;
    }
  }

  /**
   * Derives a track sample format from the corresponding format in the multivariant playlist, and a
   * sample format that may have been obtained from a chunk belonging to a different track in the
   * same track group.
   *
   * <p>Note: Since the sample format may have been obtained from a chunk belonging to a different
   * track, it should not be used as a source for data that may vary between tracks.
   *
   * @param playlistFormat The format information obtained from the multivariant playlist.
   * @param sampleFormat The format information obtained from samples within a chunk. The chunk may
   *     belong to a different track in the same track group.
   * @param propagateBitrates Whether the bitrates from the playlist format should be included in
   *     the derived format.
   * @return The derived track format.
   */
  private static Format deriveFormat(
      @Nullable Format playlistFormat, Format sampleFormat, boolean propagateBitrates) {
    if (playlistFormat == null) {
      return sampleFormat;
    }

    int sampleTrackType = MimeTypes.getTrackType(sampleFormat.sampleMimeType);
    @Nullable String sampleMimeType;
    @Nullable String codecs;
    if (Util.getCodecCountOfType(playlistFormat.codecs, sampleTrackType) == 1) {
      // We can unequivocally map this track to a playlist variant because only one codec string
      // matches this track's type.
      codecs = Util.getCodecsOfType(playlistFormat.codecs, sampleTrackType);
      sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    } else {
      // The variant assigns more than one codec string to this track. We choose whichever codec
      // string matches the sample mime type. This can happen when different languages are encoded
      // using different codecs.
      codecs =
          MimeTypes.getCodecsCorrespondingToMimeType(
              playlistFormat.codecs, sampleFormat.sampleMimeType);
      sampleMimeType = sampleFormat.sampleMimeType;
    }

    Format.Builder formatBuilder =
        sampleFormat
            .buildUpon()
            .setId(playlistFormat.id)
            .setLabel(playlistFormat.label)
            .setLanguage(playlistFormat.language)
            .setSelectionFlags(playlistFormat.selectionFlags)
            .setRoleFlags(playlistFormat.roleFlags)
            .setAverageBitrate(propagateBitrates ? playlistFormat.averageBitrate : Format.NO_VALUE)
            .setPeakBitrate(propagateBitrates ? playlistFormat.peakBitrate : Format.NO_VALUE)
            .setCodecs(codecs);

    if (sampleTrackType == C.TRACK_TYPE_VIDEO) {
      formatBuilder
          .setWidth(playlistFormat.width)
          .setHeight(playlistFormat.height)
          .setFrameRate(playlistFormat.frameRate);
    }

    if (sampleMimeType != null) {
      formatBuilder.setSampleMimeType(sampleMimeType);
    }

    if (playlistFormat.channelCount != Format.NO_VALUE && sampleTrackType == C.TRACK_TYPE_AUDIO) {
      formatBuilder.setChannelCount(playlistFormat.channelCount);
    }

    if (playlistFormat.metadata != null) {
      Metadata metadata = playlistFormat.metadata;
      if (sampleFormat.metadata != null) {
        metadata = sampleFormat.metadata.copyWithAppendedEntriesFrom(metadata);
      }
      formatBuilder.setMetadata(metadata);
    }

    return formatBuilder.build();
  }

  private static boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof HlsMediaChunk;
  }

  private static boolean formatsMatch(Format manifestFormat, Format sampleFormat) {
    @Nullable String manifestFormatMimeType = manifestFormat.sampleMimeType;
    @Nullable String sampleFormatMimeType = sampleFormat.sampleMimeType;
    int manifestFormatTrackType = MimeTypes.getTrackType(manifestFormatMimeType);
    if (manifestFormatTrackType != C.TRACK_TYPE_TEXT) {
      return manifestFormatTrackType == MimeTypes.getTrackType(sampleFormatMimeType);
    } else if (!Util.areEqual(manifestFormatMimeType, sampleFormatMimeType)) {
      return false;
    }
    if (MimeTypes.APPLICATION_CEA608.equals(manifestFormatMimeType)
        || MimeTypes.APPLICATION_CEA708.equals(manifestFormatMimeType)) {
      return manifestFormat.accessibilityChannel == sampleFormat.accessibilityChannel;
    }
    return true;
  }

  private static DummyTrackOutput createFakeTrackOutput(int id, int type) {
    Log.w(TAG, "Unmapped track with id " + id + " of type " + type);
    return new DummyTrackOutput();
  }

  /**
   * A {@link SampleQueue} that adds HLS specific functionality:
   *
   * <ul>
   *   <li>Detection of spurious discontinuities, by checking sample timestamps against the range
   *       expected for the currently loading chunk.
   *   <li>Stripping private timestamp metadata from {@link Format Formats} to avoid an excessive
   *       number of format switches in the queue.
   *   <li>Overriding of {@link Format#drmInitData}.
   * </ul>
   */
  private static final class HlsSampleQueue extends SampleQueue {

    // TODO: Uncomment this to reject samples with unexpected timestamps. See
    // https://github.com/google/ExoPlayer/issues/7030.
    // /**
    //  * The fraction of the chunk duration from which timestamps of samples loaded from within a
    //  * chunk are allowed to deviate from the expected range.
    //  */
    // private static final double MAX_TIMESTAMP_DEVIATION_FRACTION = 0.5;
    //
    // /**
    //  * A minimum tolerance for sample timestamps in microseconds. Timestamps of samples loaded
    //  * from within a chunk are always allowed to deviate up to this amount from the expected
    //  * range.
    //  */
    // private static final long MIN_TIMESTAMP_DEVIATION_TOLERANCE_US = 4_000_000;
    //
    // @Nullable private HlsMediaChunk sourceChunk;
    // private long sourceChunkLastSampleTimeUs;
    // private long minAllowedSampleTimeUs;
    // private long maxAllowedSampleTimeUs;

    private final Map<String, DrmInitData> overridingDrmInitData;
    @Nullable private DrmInitData drmInitData;

    private HlsSampleQueue(
        Allocator allocator,
        DrmSessionManager drmSessionManager,
        DrmSessionEventListener.EventDispatcher eventDispatcher,
        Map<String, DrmInitData> overridingDrmInitData) {
      super(allocator, drmSessionManager, eventDispatcher);
      this.overridingDrmInitData = overridingDrmInitData;
    }

    public void setSourceChunk(HlsMediaChunk chunk) {
      sourceId(chunk.uid);

      // TODO: Uncomment this to reject samples with unexpected timestamps. See
      // https://github.com/google/ExoPlayer/issues/7030.
      // sourceChunk = chunk;
      // sourceChunkLastSampleTimeUs = C.TIME_UNSET;
      // long allowedDeviationUs =
      //     Math.max(
      //         (long) ((chunk.endTimeUs - chunk.startTimeUs) * MAX_TIMESTAMP_DEVIATION_FRACTION),
      //         MIN_TIMESTAMP_DEVIATION_TOLERANCE_US);
      // minAllowedSampleTimeUs = chunk.startTimeUs - allowedDeviationUs;
      // maxAllowedSampleTimeUs = chunk.endTimeUs + allowedDeviationUs;
    }

    public void setDrmInitData(@Nullable DrmInitData drmInitData) {
      this.drmInitData = drmInitData;
      invalidateUpstreamFormatAdjustment();
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public Format getAdjustedUpstreamFormat(Format format) {
      @Nullable
      DrmInitData drmInitData = this.drmInitData != null ? this.drmInitData : format.drmInitData;
      if (drmInitData != null) {
        @Nullable
        DrmInitData overridingDrmInitData = this.overridingDrmInitData.get(drmInitData.schemeType);
        if (overridingDrmInitData != null) {
          drmInitData = overridingDrmInitData;
        }
      }
      @Nullable Metadata metadata = getAdjustedMetadata(format.metadata);
      if (drmInitData != format.drmInitData || metadata != format.metadata) {
        format = format.buildUpon().setDrmInitData(drmInitData).setMetadata(metadata).build();
      }
      return super.getAdjustedUpstreamFormat(format);
    }

    /**
     * Strips the private timestamp frame from metadata, if present. See:
     * https://github.com/google/ExoPlayer/issues/5063
     */
    @Nullable
    private Metadata getAdjustedMetadata(@Nullable Metadata metadata) {
      if (metadata == null) {
        return null;
      }
      int length = metadata.length();
      int transportStreamTimestampMetadataIndex = C.INDEX_UNSET;
      for (int i = 0; i < length; i++) {
        Metadata.Entry metadataEntry = metadata.get(i);
        if (metadataEntry instanceof PrivFrame) {
          PrivFrame privFrame = (PrivFrame) metadataEntry;
          if (HlsMediaChunk.PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
            transportStreamTimestampMetadataIndex = i;
            break;
          }
        }
      }
      if (transportStreamTimestampMetadataIndex == C.INDEX_UNSET) {
        return metadata;
      }
      if (length == 1) {
        return null;
      }
      Metadata.Entry[] newMetadataEntries = new Metadata.Entry[length - 1];
      for (int i = 0; i < length; i++) {
        if (i != transportStreamTimestampMetadataIndex) {
          int newIndex = i < transportStreamTimestampMetadataIndex ? i : i - 1;
          newMetadataEntries[newIndex] = metadata.get(i);
        }
      }
      return new Metadata(newMetadataEntries);
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      // TODO: Uncomment this to reject samples with unexpected timestamps. See
      // https://github.com/google/ExoPlayer/issues/7030.
      // if (timeUs < minAllowedSampleTimeUs || timeUs > maxAllowedSampleTimeUs) {
      //   Util.sneakyThrow(
      //       new UnexpectedSampleTimestampException(
      //           sourceChunk, sourceChunkLastSampleTimeUs, timeUs));
      // }
      // sourceChunkLastSampleTimeUs = timeUs;
      super.sampleMetadata(timeUs, flags, size, offset, cryptoData);
    }
  }

  private static class EmsgUnwrappingTrackOutput implements TrackOutput {

    // TODO: Create a Formats util class with common constants like this.
    private static final Format ID3_FORMAT =
        new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_ID3).build();
    private static final Format EMSG_FORMAT =
        new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_EMSG).build();

    private final EventMessageDecoder emsgDecoder;
    private final TrackOutput delegate;
    private final Format delegateFormat;
    private @MonotonicNonNull Format format;

    private byte[] buffer;
    private int bufferPosition;

    public EmsgUnwrappingTrackOutput(
        TrackOutput delegate, @HlsMediaSource.MetadataType int metadataType) {
      this.emsgDecoder = new EventMessageDecoder();
      this.delegate = delegate;
      switch (metadataType) {
        case HlsMediaSource.METADATA_TYPE_ID3:
          delegateFormat = ID3_FORMAT;
          break;
        case HlsMediaSource.METADATA_TYPE_EMSG:
          delegateFormat = EMSG_FORMAT;
          break;
        default:
          throw new IllegalArgumentException("Unknown metadataType: " + metadataType);
      }

      this.buffer = new byte[0];
      this.bufferPosition = 0;
    }

    @Override
    public void format(Format format) {
      this.format = format;
      delegate.format(delegateFormat);
    }

    @Override
    public int sampleData(
        DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
        throws IOException {
      ensureBufferCapacity(bufferPosition + length);
      int numBytesRead = input.read(buffer, bufferPosition, length);
      if (numBytesRead == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) {
          return C.RESULT_END_OF_INPUT;
        } else {
          throw new EOFException();
        }
      }
      bufferPosition += numBytesRead;
      return numBytesRead;
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
      ensureBufferCapacity(bufferPosition + length);
      data.readBytes(this.buffer, bufferPosition, length);
      bufferPosition += length;
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      Assertions.checkNotNull(format);
      ParsableByteArray sample = getSampleAndTrimBuffer(size, offset);
      ParsableByteArray sampleForDelegate;
      if (Util.areEqual(format.sampleMimeType, delegateFormat.sampleMimeType)) {
        // Incoming format matches delegate track's format, so pass straight through.
        sampleForDelegate = sample;
      } else if (MimeTypes.APPLICATION_EMSG.equals(format.sampleMimeType)) {
        // Incoming sample is EMSG, and delegate track is not expecting EMSG, so try unwrapping.
        EventMessage emsg = emsgDecoder.decode(sample);
        if (!emsgContainsExpectedWrappedFormat(emsg)) {
          Log.w(
              TAG,
              String.format(
                  "Ignoring EMSG. Expected it to contain wrapped %s but actual wrapped format: %s",
                  delegateFormat.sampleMimeType, emsg.getWrappedMetadataFormat()));
          return;
        }
        sampleForDelegate =
            new ParsableByteArray(Assertions.checkNotNull(emsg.getWrappedMetadataBytes()));
      } else {
        Log.w(TAG, "Ignoring sample for unsupported format: " + format.sampleMimeType);
        return;
      }

      int sampleSize = sampleForDelegate.bytesLeft();

      delegate.sampleData(sampleForDelegate, sampleSize);
      delegate.sampleMetadata(timeUs, flags, sampleSize, offset, cryptoData);
    }

    private boolean emsgContainsExpectedWrappedFormat(EventMessage emsg) {
      @Nullable Format wrappedMetadataFormat = emsg.getWrappedMetadataFormat();
      return wrappedMetadataFormat != null
          && Util.areEqual(delegateFormat.sampleMimeType, wrappedMetadataFormat.sampleMimeType);
    }

    private void ensureBufferCapacity(int requiredLength) {
      if (buffer.length < requiredLength) {
        buffer = Arrays.copyOf(buffer, requiredLength + requiredLength / 2);
      }
    }

    /**
     * Removes a complete sample from the {@link #buffer} field & reshuffles the tail data skipped
     * by {@code offset} to the head of the array.
     *
     * @param size see {@code size} param of {@link #sampleMetadata}.
     * @param offset see {@code offset} param of {@link #sampleMetadata}.
     * @return A {@link ParsableByteArray} containing the sample removed from {@link #buffer}.
     */
    private ParsableByteArray getSampleAndTrimBuffer(int size, int offset) {
      int sampleEnd = bufferPosition - offset;
      int sampleStart = sampleEnd - size;

      byte[] sampleBytes = Arrays.copyOfRange(buffer, sampleStart, sampleEnd);
      ParsableByteArray sample = new ParsableByteArray(sampleBytes);

      System.arraycopy(buffer, sampleEnd, buffer, 0, offset);
      bufferPosition = offset;
      return sample;
    }
  }
}
