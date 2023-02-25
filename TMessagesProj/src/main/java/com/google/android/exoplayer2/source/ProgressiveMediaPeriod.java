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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.DataType;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekMap.SeekPoints;
import com.google.android.exoplayer2.extractor.SeekMap.Unseekable;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.source.SampleQueue.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.source.SampleStream.ReadFlags;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.upstream.StatsDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that extracts data using an {@link Extractor}. */
/* package */ final class ProgressiveMediaPeriod
    implements MediaPeriod,
        ExtractorOutput,
        Loader.Callback<ProgressiveMediaPeriod.ExtractingLoadable>,
        Loader.ReleaseCallback,
        UpstreamFormatChangedListener {

  /** Listener for information about the period. */
  interface Listener {

    /**
     * Called when the duration, the ability to seek within the period, or the categorization as
     * live stream changes.
     *
     * @param durationUs The duration of the period, or {@link C#TIME_UNSET}.
     * @param isSeekable Whether the period is seekable.
     * @param isLive Whether the period is live.
     */
    void onSourceInfoRefreshed(long durationUs, boolean isSeekable, boolean isLive);
  }

  /**
   * When the source's duration is unknown, it is calculated by adding this value to the largest
   * sample timestamp seen when buffering completes.
   */
  private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10_000;

  private static final Map<String, String> ICY_METADATA_HEADERS = createIcyMetadataHeaders();

  private static final Format ICY_FORMAT =
      new Format.Builder().setId("icy").setSampleMimeType(MimeTypes.APPLICATION_ICY).build();

  private final Uri uri;
  private final DataSource dataSource;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final Listener listener;
  private final Allocator allocator;
  @Nullable private final String customCacheKey;
  private final long continueLoadingCheckIntervalBytes;
  private final Loader loader;
  private final ProgressiveMediaExtractor progressiveMediaExtractor;
  private final ConditionVariable loadCondition;
  private final Runnable maybeFinishPrepareRunnable;
  private final Runnable onContinueLoadingRequestedRunnable;
  private final Handler handler;

  @Nullable private Callback callback;
  @Nullable private IcyHeaders icyHeaders;
  private SampleQueue[] sampleQueues;
  private TrackId[] sampleQueueTrackIds;
  private boolean sampleQueuesBuilt;

  private boolean prepared;
  private boolean haveAudioVideoTracks;
  private @MonotonicNonNull TrackState trackState;
  private @MonotonicNonNull SeekMap seekMap;
  private long durationUs;
  private boolean isLive;
  private @DataType int dataType;

  private boolean seenFirstTrackSelection;
  private boolean notifyDiscontinuity;
  private int enabledTrackCount;
  private boolean isLengthKnown;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private boolean pendingDeferredRetry;

  private int extractedSamplesCountAtStartOfLoad;
  private boolean loadingFinished;
  private boolean released;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource The data source to read the media.
   * @param progressiveMediaExtractor The {@link ProgressiveMediaExtractor} to use to read the data
   *     source.
   * @param drmSessionManager A {@link DrmSessionManager} to allow DRM interactions.
   * @param drmEventDispatcher A dispatcher to notify of {@link DrmSessionEventListener} events.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param mediaSourceEventDispatcher A dispatcher to notify of {@link MediaSourceEventListener}
   *     events.
   * @param listener A listener to notify when information about the period changes.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between each
   *     invocation of {@link Callback#onContinueLoadingRequested(SequenceableLoader)}.
   */
  // maybeFinishPrepare is not posted to the handler until initialization completes.
  @SuppressWarnings({"nullness:argument", "nullness:methodref.receiver.bound"})
  public ProgressiveMediaPeriod(
      Uri uri,
      DataSource dataSource,
      ProgressiveMediaExtractor progressiveMediaExtractor,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
      Listener listener,
      Allocator allocator,
      @Nullable String customCacheKey,
      int continueLoadingCheckIntervalBytes) {
    this.uri = uri;
    this.dataSource = dataSource;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.listener = listener;
    this.allocator = allocator;
    this.customCacheKey = customCacheKey;
    this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
    loader = new Loader("ProgressiveMediaPeriod");
    this.progressiveMediaExtractor = progressiveMediaExtractor;
    loadCondition = new ConditionVariable();
    maybeFinishPrepareRunnable = this::maybeFinishPrepare;
    onContinueLoadingRequestedRunnable =
        () -> {
          if (!released) {
            checkNotNull(callback).onContinueLoadingRequested(ProgressiveMediaPeriod.this);
          }
        };
    handler = Util.createHandlerForCurrentLooper();
    sampleQueueTrackIds = new TrackId[0];
    sampleQueues = new SampleQueue[0];
    pendingResetPositionUs = C.TIME_UNSET;
    durationUs = C.TIME_UNSET;
    dataType = C.DATA_TYPE_MEDIA;
  }

  public void release() {
    if (prepared) {
      // Discard as much as we can synchronously. We only do this if we're prepared, since otherwise
      // sampleQueues may still be being modified by the loading thread.
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.preRelease();
      }
    }
    loader.release(/* callback= */ this);
    handler.removeCallbacksAndMessages(null);
    callback = null;
    released = true;
  }

  @Override
  public void onLoaderReleased() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.release();
    }
    progressiveMediaExtractor.release();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    loadCondition.open();
    startLoading();
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    maybeThrowError();
    if (loadingFinished && !prepared) {
      throw ParserException.createForMalformedContainer(
          "Loading finished before preparation is complete.", /* cause= */ null);
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    assertPrepared();
    return trackState.tracks;
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    assertPrepared();
    TrackGroupArray tracks = trackState.tracks;
    boolean[] trackEnabledStates = trackState.trackEnabledStates;
    int oldEnabledTrackCount = enabledTrackCount;
    // Deselect old tracks.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        int track = ((SampleStreamImpl) streams[i]).track;
        Assertions.checkState(trackEnabledStates[track]);
        enabledTrackCount--;
        trackEnabledStates[track] = false;
        streams[i] = null;
      }
    }
    // We'll always need to seek if this is a first selection to a non-zero position, or if we're
    // making a selection having previously disabled all tracks.
    boolean seekRequired = seenFirstTrackSelection ? oldEnabledTrackCount == 0 : positionUs != 0;
    // Select new tracks.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] == null && selections[i] != null) {
        ExoTrackSelection selection = selections[i];
        Assertions.checkState(selection.length() == 1);
        Assertions.checkState(selection.getIndexInTrackGroup(0) == 0);
        int track = tracks.indexOf(selection.getTrackGroup());
        Assertions.checkState(!trackEnabledStates[track]);
        enabledTrackCount++;
        trackEnabledStates[track] = true;
        streams[i] = new SampleStreamImpl(track);
        streamResetFlags[i] = true;
        // If there's still a chance of avoiding a seek, try and seek within the sample queue.
        if (!seekRequired) {
          SampleQueue sampleQueue = sampleQueues[track];
          // A seek can be avoided if we're able to seek to the current playback position in the
          // sample queue, or if we haven't read anything from the queue since the previous seek
          // (this case is common for sparse tracks such as metadata tracks). In all other cases a
          // seek is required.
          seekRequired =
              !sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ true)
                  && sampleQueue.getReadIndex() != 0;
        }
      }
    }
    if (enabledTrackCount == 0) {
      pendingDeferredRetry = false;
      notifyDiscontinuity = false;
      if (loader.isLoading()) {
        // Discard as much as we can synchronously.
        for (SampleQueue sampleQueue : sampleQueues) {
          sampleQueue.discardToEnd();
        }
        loader.cancelLoading();
      } else {
        for (SampleQueue sampleQueue : sampleQueues) {
          sampleQueue.reset();
        }
      }
    } else if (seekRequired) {
      positionUs = seekToUs(positionUs);
      // We'll need to reset renderers consuming from all streams due to the seek.
      for (int i = 0; i < streams.length; i++) {
        if (streams[i] != null) {
          streamResetFlags[i] = true;
        }
      }
    }
    seenFirstTrackSelection = true;
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    assertPrepared();
    if (isPendingReset()) {
      return;
    }
    boolean[] trackEnabledStates = trackState.trackEnabledStates;
    int trackCount = sampleQueues.length;
    for (int i = 0; i < trackCount; i++) {
      sampleQueues[i].discardTo(positionUs, toKeyframe, trackEnabledStates[i]);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public boolean continueLoading(long playbackPositionUs) {
    if (loadingFinished
        || loader.hasFatalError()
        || pendingDeferredRetry
        || (prepared && enabledTrackCount == 0)) {
      return false;
    }
    boolean continuedLoading = loadCondition.open();
    if (!loader.isLoading()) {
      startLoading();
      continuedLoading = true;
    }
    return continuedLoading;
  }

  @Override
  public boolean isLoading() {
    return loader.isLoading() && loadCondition.isOpen();
  }

  @Override
  public long getNextLoadPositionUs() {
    return getBufferedPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    if (notifyDiscontinuity
        && (loadingFinished || getExtractedSamplesCount() > extractedSamplesCountAtStartOfLoad)) {
      notifyDiscontinuity = false;
      return lastSeekPositionUs;
    }
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    assertPrepared();
    if (loadingFinished || enabledTrackCount == 0) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    }
    long largestQueuedTimestampUs = Long.MAX_VALUE;
    if (haveAudioVideoTracks) {
      // Ignore non-AV tracks, which may be sparse or poorly interleaved.
      int trackCount = sampleQueues.length;
      for (int i = 0; i < trackCount; i++) {
        if (trackState.trackIsAudioVideoFlags[i]
            && trackState.trackEnabledStates[i]
            && !sampleQueues[i].isLastSampleQueued()) {
          largestQueuedTimestampUs =
              min(largestQueuedTimestampUs, sampleQueues[i].getLargestQueuedTimestampUs());
        }
      }
    }
    if (largestQueuedTimestampUs == Long.MAX_VALUE) {
      largestQueuedTimestampUs = getLargestQueuedTimestampUs(/* includeDisabledTracks= */ false);
    }
    return largestQueuedTimestampUs == Long.MIN_VALUE
        ? lastSeekPositionUs
        : largestQueuedTimestampUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    assertPrepared();
    boolean[] trackIsAudioVideoFlags = trackState.trackIsAudioVideoFlags;
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = seekMap.isSeekable() ? positionUs : 0;

    notifyDiscontinuity = false;
    lastSeekPositionUs = positionUs;
    if (isPendingReset()) {
      // A reset is already pending. We only need to update its position.
      pendingResetPositionUs = positionUs;
      return positionUs;
    }

    // If we're not playing a live stream, try and seek within the buffer.
    if (dataType != C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE
        && seekInsideBufferUs(trackIsAudioVideoFlags, positionUs)) {
      return positionUs;
    }

    // We can't seek inside the buffer, and so need to reset.
    pendingDeferredRetry = false;
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    if (loader.isLoading()) {
      // Discard as much as we can synchronously.
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.discardToEnd();
      }
      loader.cancelLoading();
    } else {
      loader.clearFatalError();
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.reset();
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    assertPrepared();
    if (!seekMap.isSeekable()) {
      // Treat all seeks into non-seekable media as being to t=0.
      return 0;
    }
    SeekPoints seekPoints = seekMap.getSeekPoints(positionUs);
    return seekParameters.resolveSeekPositionUs(
        positionUs, seekPoints.first.timeUs, seekPoints.second.timeUs);
  }

  // SampleStream methods.

  /* package */ boolean isReady(int track) {
    return !suppressRead() && sampleQueues[track].isReady(loadingFinished);
  }

  /* package */ void maybeThrowError(int sampleQueueIndex) throws IOException {
    sampleQueues[sampleQueueIndex].maybeThrowError();
    maybeThrowError();
  }

  /* package */ void maybeThrowError() throws IOException {
    loader.maybeThrowError(loadErrorHandlingPolicy.getMinimumLoadableRetryCount(dataType));
  }

  /* package */ int readData(
      int sampleQueueIndex,
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      @ReadFlags int readFlags) {
    if (suppressRead()) {
      return C.RESULT_NOTHING_READ;
    }
    maybeNotifyDownstreamFormat(sampleQueueIndex);
    int result =
        sampleQueues[sampleQueueIndex].read(formatHolder, buffer, readFlags, loadingFinished);
    if (result == C.RESULT_NOTHING_READ) {
      maybeStartDeferredRetry(sampleQueueIndex);
    }
    return result;
  }

  /* package */ int skipData(int track, long positionUs) {
    if (suppressRead()) {
      return 0;
    }
    maybeNotifyDownstreamFormat(track);
    SampleQueue sampleQueue = sampleQueues[track];
    int skipCount = sampleQueue.getSkipCount(positionUs, loadingFinished);
    sampleQueue.skip(skipCount);
    if (skipCount == 0) {
      maybeStartDeferredRetry(track);
    }
    return skipCount;
  }

  private void maybeNotifyDownstreamFormat(int track) {
    assertPrepared();
    boolean[] trackNotifiedDownstreamFormats = trackState.trackNotifiedDownstreamFormats;
    if (!trackNotifiedDownstreamFormats[track]) {
      Format trackFormat = trackState.tracks.get(track).getFormat(/* index= */ 0);
      mediaSourceEventDispatcher.downstreamFormatChanged(
          MimeTypes.getTrackType(trackFormat.sampleMimeType),
          trackFormat,
          C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          lastSeekPositionUs);
      trackNotifiedDownstreamFormats[track] = true;
    }
  }

  private void maybeStartDeferredRetry(int track) {
    assertPrepared();
    boolean[] trackIsAudioVideoFlags = trackState.trackIsAudioVideoFlags;
    if (!pendingDeferredRetry
        || !trackIsAudioVideoFlags[track]
        || sampleQueues[track].isReady(/* loadingFinished= */ false)) {
      return;
    }
    pendingResetPositionUs = 0;
    pendingDeferredRetry = false;
    notifyDiscontinuity = true;
    lastSeekPositionUs = 0;
    extractedSamplesCountAtStartOfLoad = 0;
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.reset();
    }
    checkNotNull(callback).onContinueLoadingRequested(this);
  }

  private boolean suppressRead() {
    return notifyDiscontinuity || isPendingReset();
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(
      ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
    if (durationUs == C.TIME_UNSET && seekMap != null) {
      boolean isSeekable = seekMap.isSeekable();
      long largestQueuedTimestampUs =
          getLargestQueuedTimestampUs(/* includeDisabledTracks= */ true);
      durationUs =
          largestQueuedTimestampUs == Long.MIN_VALUE
              ? 0
              : largestQueuedTimestampUs + DEFAULT_LAST_SAMPLE_DURATION_US;
      listener.onSourceInfoRefreshed(durationUs, isSeekable, isLive);
    }
    StatsDataSource dataSource = loadable.dataSource;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            dataSource.getLastOpenedUri(),
            dataSource.getLastResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            dataSource.getBytesRead());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    mediaSourceEventDispatcher.loadCompleted(
        loadEventInfo,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs);
    loadingFinished = true;
    checkNotNull(callback).onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(
      ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
    StatsDataSource dataSource = loadable.dataSource;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            dataSource.getLastOpenedUri(),
            dataSource.getLastResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            dataSource.getBytesRead());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    mediaSourceEventDispatcher.loadCanceled(
        loadEventInfo,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs);
    if (!released) {
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.reset();
      }
      if (enabledTrackCount > 0) {
        checkNotNull(callback).onContinueLoadingRequested(this);
      }
    }
  }

  @Override
  public LoadErrorAction onLoadError(
      ExtractingLoadable loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    StatsDataSource dataSource = loadable.dataSource;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            dataSource.getLastOpenedUri(),
            dataSource.getLastResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            dataSource.getBytesRead());
    MediaLoadData mediaLoadData =
        new MediaLoadData(
            C.DATA_TYPE_MEDIA,
            C.TRACK_TYPE_UNKNOWN,
            /* trackFormat= */ null,
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            /* mediaStartTimeMs= */ Util.usToMs(loadable.seekTimeUs),
            Util.usToMs(durationUs));
    LoadErrorAction loadErrorAction;
    long retryDelayMs =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
            new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount));
    if (retryDelayMs == C.TIME_UNSET) {
      loadErrorAction = Loader.DONT_RETRY_FATAL;
    } else /* the load should be retried */ {
      int extractedSamplesCount = getExtractedSamplesCount();
      boolean madeProgress = extractedSamplesCount > extractedSamplesCountAtStartOfLoad;
      loadErrorAction =
          configureRetry(loadable, extractedSamplesCount)
              ? Loader.createRetryAction(/* resetErrorCount= */ madeProgress, retryDelayMs)
              : Loader.DONT_RETRY;
    }

    boolean wasCanceled = !loadErrorAction.isRetry();
    mediaSourceEventDispatcher.loadError(
        loadEventInfo,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs,
        error,
        wasCanceled);
    if (wasCanceled) {
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }
    return loadErrorAction;
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public TrackOutput track(int id, int type) {
    return prepareTrackOutput(new TrackId(id, /* isIcyTrack= */ false));
  }

  @Override
  public void endTracks() {
    sampleQueuesBuilt = true;
    handler.post(maybeFinishPrepareRunnable);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    handler.post(() -> setSeekMap(seekMap));
  }

  // Icy metadata. Called by the loading thread.

  /* package */ TrackOutput icyTrack() {
    return prepareTrackOutput(new TrackId(0, /* isIcyTrack= */ true));
  }

  // UpstreamFormatChangedListener implementation. Called by the loading thread.

  @Override
  public void onUpstreamFormatChanged(Format format) {
    handler.post(maybeFinishPrepareRunnable);
  }

  // Internal methods.

  private void onLengthKnown() {
    handler.post(() -> isLengthKnown = true);
  }

  private TrackOutput prepareTrackOutput(TrackId id) {
    int trackCount = sampleQueues.length;
    for (int i = 0; i < trackCount; i++) {
      if (id.equals(sampleQueueTrackIds[i])) {
        return sampleQueues[i];
      }
    }
    SampleQueue trackOutput =
        SampleQueue.createWithDrm(allocator, drmSessionManager, drmEventDispatcher);
    trackOutput.setUpstreamFormatChangeListener(this);
    @NullableType
    TrackId[] sampleQueueTrackIds = Arrays.copyOf(this.sampleQueueTrackIds, trackCount + 1);
    sampleQueueTrackIds[trackCount] = id;
    this.sampleQueueTrackIds = Util.castNonNullTypeArray(sampleQueueTrackIds);
    @NullableType SampleQueue[] sampleQueues = Arrays.copyOf(this.sampleQueues, trackCount + 1);
    sampleQueues[trackCount] = trackOutput;
    this.sampleQueues = Util.castNonNullTypeArray(sampleQueues);
    return trackOutput;
  }

  private void setSeekMap(SeekMap seekMap) {
    this.seekMap = icyHeaders == null ? seekMap : new Unseekable(/* durationUs= */ C.TIME_UNSET);
    durationUs = seekMap.getDurationUs();
    isLive = !isLengthKnown && seekMap.getDurationUs() == C.TIME_UNSET;
    dataType = isLive ? C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE : C.DATA_TYPE_MEDIA;
    listener.onSourceInfoRefreshed(durationUs, seekMap.isSeekable(), isLive);
    if (!prepared) {
      maybeFinishPrepare();
    }
  }

  private void maybeFinishPrepare() {
    if (released || prepared || !sampleQueuesBuilt || seekMap == null) {
      return;
    }
    for (SampleQueue sampleQueue : sampleQueues) {
      if (sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }
    loadCondition.close();
    int trackCount = sampleQueues.length;
    TrackGroup[] trackArray = new TrackGroup[trackCount];
    boolean[] trackIsAudioVideoFlags = new boolean[trackCount];
    for (int i = 0; i < trackCount; i++) {
      Format trackFormat = checkNotNull(sampleQueues[i].getUpstreamFormat());
      @Nullable String mimeType = trackFormat.sampleMimeType;
      boolean isAudio = MimeTypes.isAudio(mimeType);
      boolean isAudioVideo = isAudio || MimeTypes.isVideo(mimeType);
      trackIsAudioVideoFlags[i] = isAudioVideo;
      haveAudioVideoTracks |= isAudioVideo;
      @Nullable IcyHeaders icyHeaders = this.icyHeaders;
      if (icyHeaders != null) {
        if (isAudio || sampleQueueTrackIds[i].isIcyTrack) {
          @Nullable Metadata metadata = trackFormat.metadata;
          if (metadata == null) {
            metadata = new Metadata(icyHeaders);
          } else {
            metadata = metadata.copyWithAppendedEntries(icyHeaders);
          }
          trackFormat = trackFormat.buildUpon().setMetadata(metadata).build();
        }
        // Update the track format with the bitrate from the ICY header only if it declares neither
        // an average or peak bitrate of its own.
        if (isAudio
            && trackFormat.averageBitrate == Format.NO_VALUE
            && trackFormat.peakBitrate == Format.NO_VALUE
            && icyHeaders.bitrate != Format.NO_VALUE) {
          trackFormat = trackFormat.buildUpon().setAverageBitrate(icyHeaders.bitrate).build();
        }
      }
      trackFormat = trackFormat.copyWithCryptoType(drmSessionManager.getCryptoType(trackFormat));
      trackArray[i] = new TrackGroup(/* id= */ Integer.toString(i), trackFormat);
    }
    trackState = new TrackState(new TrackGroupArray(trackArray), trackIsAudioVideoFlags);
    prepared = true;
    checkNotNull(callback).onPrepared(this);
  }

  private void startLoading() {
    ExtractingLoadable loadable =
        new ExtractingLoadable(
            uri, dataSource, progressiveMediaExtractor, /* extractorOutput= */ this, loadCondition);
    if (prepared) {
      Assertions.checkState(isPendingReset());
      if (durationUs != C.TIME_UNSET && pendingResetPositionUs > durationUs) {
        loadingFinished = true;
        pendingResetPositionUs = C.TIME_UNSET;
        return;
      }
      loadable.setLoadPosition(
          checkNotNull(seekMap).getSeekPoints(pendingResetPositionUs).first.position,
          pendingResetPositionUs);
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.setStartTimeUs(pendingResetPositionUs);
      }
      pendingResetPositionUs = C.TIME_UNSET;
    }
    extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(dataType));
    DataSpec dataSpec = loadable.dataSpec;
    mediaSourceEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, dataSpec, elapsedRealtimeMs),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs);
  }

  /**
   * Called to configure a retry when a load error occurs.
   *
   * @param loadable The current loadable for which the error was encountered.
   * @param currentExtractedSampleCount The current number of samples that have been extracted into
   *     the sample queues.
   * @return Whether the loader should retry with the current loadable. False indicates a deferred
   *     retry.
   */
  private boolean configureRetry(ExtractingLoadable loadable, int currentExtractedSampleCount) {
    if (isLengthKnown || (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET)) {
      // We're playing an on-demand stream. Resume the current loadable, which will
      // request data starting from the point it left off.
      extractedSamplesCountAtStartOfLoad = currentExtractedSampleCount;
      return true;
    } else if (prepared && !suppressRead()) {
      // We're playing a stream of unknown length and duration. Assume it's live, and therefore that
      // the data at the uri is a continuously shifting window of the latest available media. For
      // this case there's no way to continue loading from where a previous load finished, so it's
      // necessary to load from the start whenever commencing a new load. Deferring the retry until
      // we run out of buffered data makes for a much better user experience. See:
      // https://github.com/google/ExoPlayer/issues/1606.
      // Note that the suppressRead() check means only a single deferred retry can occur without
      // progress being made. Any subsequent failures without progress will go through the else
      // block below.
      pendingDeferredRetry = true;
      return false;
    } else {
      // This is the same case as above, except in this case there's no value in deferring the retry
      // because there's no buffered data to be read. This case also covers an on-demand stream with
      // unknown length that has yet to be prepared. This case cannot be disambiguated from the live
      // stream case, so we have no option but to load from the start.
      notifyDiscontinuity = prepared;
      lastSeekPositionUs = 0;
      extractedSamplesCountAtStartOfLoad = 0;
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.reset();
      }
      loadable.setLoadPosition(0, 0);
      return true;
    }
  }

  /**
   * Attempts to seek to the specified position within the sample queues.
   *
   * @param trackIsAudioVideoFlags Whether each track is audio/video.
   * @param positionUs The seek position in microseconds.
   * @return Whether the in-buffer seek was successful.
   */
  private boolean seekInsideBufferUs(boolean[] trackIsAudioVideoFlags, long positionUs) {
    int trackCount = sampleQueues.length;
    for (int i = 0; i < trackCount; i++) {
      SampleQueue sampleQueue = sampleQueues[i];
      boolean seekInsideQueue = sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ false);
      // If we have AV tracks then an in-buffer seek is successful if the seek into every AV queue
      // is successful. We ignore whether seeks within non-AV queues are successful in this case, as
      // they may be sparse or poorly interleaved. If we only have non-AV tracks then a seek is
      // successful only if the seek into every queue succeeds.
      if (!seekInsideQueue && (trackIsAudioVideoFlags[i] || !haveAudioVideoTracks)) {
        return false;
      }
    }
    return true;
  }

  private int getExtractedSamplesCount() {
    int extractedSamplesCount = 0;
    for (SampleQueue sampleQueue : sampleQueues) {
      extractedSamplesCount += sampleQueue.getWriteIndex();
    }
    return extractedSamplesCount;
  }

  private long getLargestQueuedTimestampUs(boolean includeDisabledTracks) {
    long largestQueuedTimestampUs = Long.MIN_VALUE;
    for (int i = 0; i < sampleQueues.length; i++) {
      if (includeDisabledTracks || checkNotNull(trackState).trackEnabledStates[i]) {
        largestQueuedTimestampUs =
            max(largestQueuedTimestampUs, sampleQueues[i].getLargestQueuedTimestampUs());
      }
    }
    return largestQueuedTimestampUs;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  @EnsuresNonNull({"trackState", "seekMap"})
  private void assertPrepared() {
    Assertions.checkState(prepared);
    checkNotNull(trackState);
    checkNotNull(seekMap);
  }

  private final class SampleStreamImpl implements SampleStream {

    private final int track;

    public SampleStreamImpl(int track) {
      this.track = track;
    }

    @Override
    public boolean isReady() {
      return ProgressiveMediaPeriod.this.isReady(track);
    }

    @Override
    public void maybeThrowError() throws IOException {
      ProgressiveMediaPeriod.this.maybeThrowError(track);
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      return ProgressiveMediaPeriod.this.readData(track, formatHolder, buffer, readFlags);
    }

    @Override
    public int skipData(long positionUs) {
      return ProgressiveMediaPeriod.this.skipData(track, positionUs);
    }
  }

  /** Loads the media stream and extracts sample data from it. */
  /* package */ final class ExtractingLoadable implements Loadable, IcyDataSource.Listener {

    private final long loadTaskId;
    private final Uri uri;
    private final StatsDataSource dataSource;
    private final ProgressiveMediaExtractor progressiveMediaExtractor;
    private final ExtractorOutput extractorOutput;
    private final ConditionVariable loadCondition;
    private final PositionHolder positionHolder;

    private volatile boolean loadCanceled;

    private boolean pendingExtractorSeek;
    private long seekTimeUs;
    private DataSpec dataSpec;
    @Nullable private TrackOutput icyTrackOutput;
    private boolean seenIcyMetadata;

    @SuppressWarnings("nullness:method.invocation")
    public ExtractingLoadable(
        Uri uri,
        DataSource dataSource,
        ProgressiveMediaExtractor progressiveMediaExtractor,
        ExtractorOutput extractorOutput,
        ConditionVariable loadCondition) {
      this.uri = uri;
      this.dataSource = new StatsDataSource(dataSource);
      this.progressiveMediaExtractor = progressiveMediaExtractor;
      this.extractorOutput = extractorOutput;
      this.loadCondition = loadCondition;
      this.positionHolder = new PositionHolder();
      this.pendingExtractorSeek = true;
      loadTaskId = LoadEventInfo.getNewId();
      dataSpec = buildDataSpec(/* position= */ 0);
    }

    // Loadable implementation.

    @Override
    public void cancelLoad() {
      loadCanceled = true;
    }

    @Override
    public void load() throws IOException {
      int result = Extractor.RESULT_CONTINUE;
      while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
        try {
          long position = positionHolder.position;
          dataSpec = buildDataSpec(position);
          long length = dataSource.open(dataSpec);
          if (length != C.LENGTH_UNSET) {
            length += position;
            onLengthKnown();
          }
          icyHeaders = IcyHeaders.parse(dataSource.getResponseHeaders());
          DataSource extractorDataSource = dataSource;
          if (icyHeaders != null && icyHeaders.metadataInterval != C.LENGTH_UNSET) {
            extractorDataSource = new IcyDataSource(dataSource, icyHeaders.metadataInterval, this);
            icyTrackOutput = icyTrack();
            icyTrackOutput.format(ICY_FORMAT);
          }
          progressiveMediaExtractor.init(
              extractorDataSource,
              uri,
              dataSource.getResponseHeaders(),
              position,
              length,
              extractorOutput);

          if (icyHeaders != null) {
            progressiveMediaExtractor.disableSeekingOnMp3Streams();
          }

          if (pendingExtractorSeek) {
            progressiveMediaExtractor.seek(position, seekTimeUs);
            pendingExtractorSeek = false;
          }
          while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
            try {
              loadCondition.block();
            } catch (InterruptedException e) {
              throw new InterruptedIOException();
            }
            result = progressiveMediaExtractor.read(positionHolder);
            long currentInputPosition = progressiveMediaExtractor.getCurrentInputPosition();
            if (currentInputPosition > position + continueLoadingCheckIntervalBytes) {
              position = currentInputPosition;
              loadCondition.close();
              handler.post(onContinueLoadingRequestedRunnable);
            }
          }
        } finally {
          if (result == Extractor.RESULT_SEEK) {
            result = Extractor.RESULT_CONTINUE;
          } else if (progressiveMediaExtractor.getCurrentInputPosition() != C.POSITION_UNSET) {
            positionHolder.position = progressiveMediaExtractor.getCurrentInputPosition();
          }
          DataSourceUtil.closeQuietly(dataSource);
        }
      }
    }

    // IcyDataSource.Listener

    @Override
    public void onIcyMetadata(ParsableByteArray metadata) {
      // Always output the first ICY metadata at the start time. This helps minimize any delay
      // between the start of playback and the first ICY metadata event.
      long timeUs =
          !seenIcyMetadata
              ? seekTimeUs
              : max(getLargestQueuedTimestampUs(/* includeDisabledTracks= */ true), seekTimeUs);
      int length = metadata.bytesLeft();
      TrackOutput icyTrackOutput = checkNotNull(this.icyTrackOutput);
      icyTrackOutput.sampleData(metadata, length);
      icyTrackOutput.sampleMetadata(
          timeUs, C.BUFFER_FLAG_KEY_FRAME, length, /* offset= */ 0, /* cryptoData= */ null);
      seenIcyMetadata = true;
    }

    // Internal methods.

    private DataSpec buildDataSpec(long position) {
      // Disable caching if the content length cannot be resolved, since this is indicative of a
      // progressive live stream.
      return new DataSpec.Builder()
          .setUri(uri)
          .setPosition(position)
          .setKey(customCacheKey)
          .setFlags(
              DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN | DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
          .setHttpRequestHeaders(ICY_METADATA_HEADERS)
          .build();
    }

    private void setLoadPosition(long position, long timeUs) {
      positionHolder.position = position;
      seekTimeUs = timeUs;
      pendingExtractorSeek = true;
      seenIcyMetadata = false;
    }
  }

  /** Stores track state. */
  private static final class TrackState {

    public final TrackGroupArray tracks;
    public final boolean[] trackIsAudioVideoFlags;
    public final boolean[] trackEnabledStates;
    public final boolean[] trackNotifiedDownstreamFormats;

    public TrackState(TrackGroupArray tracks, boolean[] trackIsAudioVideoFlags) {
      this.tracks = tracks;
      this.trackIsAudioVideoFlags = trackIsAudioVideoFlags;
      this.trackEnabledStates = new boolean[tracks.length];
      this.trackNotifiedDownstreamFormats = new boolean[tracks.length];
    }
  }

  /** Identifies a track. */
  private static final class TrackId {

    public final int id;
    public final boolean isIcyTrack;

    public TrackId(int id, boolean isIcyTrack) {
      this.id = id;
      this.isIcyTrack = isIcyTrack;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TrackId other = (TrackId) obj;
      return id == other.id && isIcyTrack == other.isIcyTrack;
    }

    @Override
    public int hashCode() {
      return 31 * id + (isIcyTrack ? 1 : 0);
    }
  }

  private static Map<String, String> createIcyMetadataHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(
        IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME,
        IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE);
    return Collections.unmodifiableMap(headers);
  }
}
