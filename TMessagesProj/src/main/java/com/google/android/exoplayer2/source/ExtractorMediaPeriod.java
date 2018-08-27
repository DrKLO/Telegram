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

import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekMap.SeekPoints;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleQueue.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.upstream.StatsDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * A {@link MediaPeriod} that extracts data using an {@link Extractor}.
 */
/* package */ final class ExtractorMediaPeriod implements MediaPeriod, ExtractorOutput,
    Loader.Callback<ExtractorMediaPeriod.ExtractingLoadable>, Loader.ReleaseCallback,
    UpstreamFormatChangedListener {

  /**
   * Listener for information about the period.
   */
  interface Listener {

    /**
     * Called when the duration or ability to seek within the period changes.
     *
     * @param durationUs The duration of the period, or {@link C#TIME_UNSET}.
     * @param isSeekable Whether the period is seekable.
     */
    void onSourceInfoRefreshed(long durationUs, boolean isSeekable);

  }

  /**
   * When the source's duration is unknown, it is calculated by adding this value to the largest
   * sample timestamp seen when buffering completes.
   */
  private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10000;

  private final Uri uri;
  private final DataSource dataSource;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final Listener listener;
  private final Allocator allocator;
  @Nullable private final String customCacheKey;
  private final long continueLoadingCheckIntervalBytes;
  private final Loader loader;
  private final ExtractorHolder extractorHolder;
  private final ConditionVariable loadCondition;
  private final Runnable maybeFinishPrepareRunnable;
  private final Runnable onContinueLoadingRequestedRunnable;
  private final Handler handler;

  private @Nullable Callback callback;
  private SeekMap seekMap;
  private SampleQueue[] sampleQueues;
  private int[] sampleQueueTrackIds;
  private boolean sampleQueuesBuilt;
  private boolean prepared;
  private int actualMinLoadableRetryCount;

  private boolean seenFirstTrackSelection;
  private boolean notifyDiscontinuity;
  private boolean notifiedReadingStarted;
  private int enabledTrackCount;
  private TrackGroupArray tracks;
  private long durationUs;
  private boolean[] trackEnabledStates;
  private boolean[] trackIsAudioVideoFlags;
  private boolean[] trackFormatNotificationSent;
  private boolean haveAudioVideoTracks;
  private long length;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private boolean pendingDeferredRetry;

  private int extractedSamplesCountAtStartOfLoad;
  private boolean loadingFinished;
  private boolean released;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource The data source to read the media.
   * @param extractors The extractors to use to read the data source.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventDispatcher A dispatcher to notify of events.
   * @param listener A listener to notify when information about the period changes.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between each
   *     invocation of {@link Callback#onContinueLoadingRequested(SequenceableLoader)}.
   */
  public ExtractorMediaPeriod(
      Uri uri,
      DataSource dataSource,
      Extractor[] extractors,
      int minLoadableRetryCount,
      EventDispatcher eventDispatcher,
      Listener listener,
      Allocator allocator,
      @Nullable String customCacheKey,
      int continueLoadingCheckIntervalBytes) {
    this.uri = uri;
    this.dataSource = dataSource;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.listener = listener;
    this.allocator = allocator;
    this.customCacheKey = customCacheKey;
    this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
    loader = new Loader("Loader:ExtractorMediaPeriod");
    extractorHolder = new ExtractorHolder(extractors, this);
    loadCondition = new ConditionVariable();
    maybeFinishPrepareRunnable = new Runnable() {
      @Override
      public void run() {
        maybeFinishPrepare();
      }
    };
    onContinueLoadingRequestedRunnable = new Runnable() {
      @Override
      public void run() {
        if (!released) {
          callback.onContinueLoadingRequested(ExtractorMediaPeriod.this);
        }
      }
    };
    handler = new Handler();
    sampleQueueTrackIds = new int[0];
    sampleQueues = new SampleQueue[0];
    pendingResetPositionUs = C.TIME_UNSET;
    length = C.LENGTH_UNSET;
    durationUs = C.TIME_UNSET;
    // Assume on-demand for MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA, until prepared.
    actualMinLoadableRetryCount =
        minLoadableRetryCount == ExtractorMediaSource.MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA
        ? ExtractorMediaSource.DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND
        : minLoadableRetryCount;
    eventDispatcher.mediaPeriodCreated();
  }

  public void release() {
    if (prepared) {
      // Discard as much as we can synchronously. We only do this if we're prepared, since otherwise
      // sampleQueues may still be being modified by the loading thread.
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.discardToEnd();
      }
    }
    loader.release(this);
    handler.removeCallbacksAndMessages(null);
    callback = null;
    released = true;
    eventDispatcher.mediaPeriodReleased();
  }

  @Override
  public void onLoaderReleased() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.reset();
    }
    extractorHolder.release();
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
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    Assertions.checkState(prepared);
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
        TrackSelection selection = selections[i];
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
          sampleQueue.rewind();
          // A seek can be avoided if we're able to advance to the current playback position in the
          // sample queue, or if we haven't read anything from the queue since the previous seek
          // (this case is common for sparse tracks such as metadata tracks). In all other cases a
          // seek is required.
          seekRequired = sampleQueue.advanceTo(positionUs, true, true) == SampleQueue.ADVANCE_FAILED
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
    if (loadingFinished || pendingDeferredRetry || (prepared && enabledTrackCount == 0)) {
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
  public long getNextLoadPositionUs() {
    return enabledTrackCount == 0 ? C.TIME_END_OF_SOURCE : getBufferedPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    if (!notifiedReadingStarted) {
      eventDispatcher.readingStarted();
      notifiedReadingStarted = true;
    }
    if (notifyDiscontinuity
        && (loadingFinished || getExtractedSamplesCount() > extractedSamplesCountAtStartOfLoad)) {
      notifyDiscontinuity = false;
      return lastSeekPositionUs;
    }
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    }
    long largestQueuedTimestampUs;
    if (haveAudioVideoTracks) {
      // Ignore non-AV tracks, which may be sparse or poorly interleaved.
      largestQueuedTimestampUs = Long.MAX_VALUE;
      int trackCount = sampleQueues.length;
      for (int i = 0; i < trackCount; i++) {
        if (trackIsAudioVideoFlags[i]) {
          largestQueuedTimestampUs = Math.min(largestQueuedTimestampUs,
              sampleQueues[i].getLargestQueuedTimestampUs());
        }
      }
    } else {
      largestQueuedTimestampUs = getLargestQueuedTimestampUs();
    }
    return largestQueuedTimestampUs == Long.MIN_VALUE ? lastSeekPositionUs
        : largestQueuedTimestampUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = seekMap.isSeekable() ? positionUs : 0;
    lastSeekPositionUs = positionUs;
    notifyDiscontinuity = false;
    // If we're not pending a reset, see if we can seek within the buffer.
    if (!isPendingReset() && seekInsideBufferUs(positionUs)) {
      return positionUs;
    }
    // We were unable to seek within the buffer, so need to reset.
    pendingDeferredRetry = false;
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.reset();
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    if (!seekMap.isSeekable()) {
      // Treat all seeks into non-seekable media as being to t=0.
      return 0;
    }
    SeekPoints seekPoints = seekMap.getSeekPoints(positionUs);
    return Util.resolveSeekPositionUs(
        positionUs, seekParameters, seekPoints.first.timeUs, seekPoints.second.timeUs);
  }

  // SampleStream methods.

  /* package */ boolean isReady(int track) {
    return !suppressRead() && (loadingFinished || sampleQueues[track].hasNextSample());
  }

  /* package */ void maybeThrowError() throws IOException {
    loader.maybeThrowError(actualMinLoadableRetryCount);
  }

  /* package */ int readData(int track, FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired) {
    if (suppressRead()) {
      return C.RESULT_NOTHING_READ;
    }
    int result =
        sampleQueues[track].read(
            formatHolder, buffer, formatRequired, loadingFinished, lastSeekPositionUs);
    if (result == C.RESULT_BUFFER_READ) {
      maybeNotifyTrackFormat(track);
    } else if (result == C.RESULT_NOTHING_READ) {
      maybeStartDeferredRetry(track);
    }
    return result;
  }

  /* package */ int skipData(int track, long positionUs) {
    if (suppressRead()) {
      return 0;
    }
    SampleQueue sampleQueue = sampleQueues[track];
    int skipCount;
    if (loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
      skipCount = sampleQueue.advanceToEnd();
    } else {
      skipCount = sampleQueue.advanceTo(positionUs, true, true);
      if (skipCount == SampleQueue.ADVANCE_FAILED) {
        skipCount = 0;
      }
    }
    if (skipCount > 0) {
      maybeNotifyTrackFormat(track);
    } else {
      maybeStartDeferredRetry(track);
    }
    return skipCount;
  }

  private void maybeNotifyTrackFormat(int track) {
    if (!trackFormatNotificationSent[track]) {
      Format trackFormat = tracks.get(track).getFormat(0);
      eventDispatcher.downstreamFormatChanged(
          MimeTypes.getTrackType(trackFormat.sampleMimeType),
          trackFormat,
          C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          lastSeekPositionUs);
      trackFormatNotificationSent[track] = true;
    }
  }

  private void maybeStartDeferredRetry(int track) {
    if (!pendingDeferredRetry
        || !trackIsAudioVideoFlags[track]
        || sampleQueues[track].hasNextSample()) {
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
    callback.onContinueLoadingRequested(this);
  }

  private boolean suppressRead() {
    return notifyDiscontinuity || isPendingReset();
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    if (durationUs == C.TIME_UNSET) {
      long largestQueuedTimestampUs = getLargestQueuedTimestampUs();
      durationUs = largestQueuedTimestampUs == Long.MIN_VALUE ? 0
          : largestQueuedTimestampUs + DEFAULT_LAST_SAMPLE_DURATION_US;
      listener.onSourceInfoRefreshed(durationUs, seekMap.isSeekable());
    }
    eventDispatcher.loadCompleted(
        loadable.dataSpec,
        loadable.dataSource.getLastOpenedUri(),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.dataSource.getBytesRead());
    copyLengthFromLoader(loadable);
    loadingFinished = true;
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    eventDispatcher.loadCanceled(
        loadable.dataSpec,
        loadable.dataSource.getLastOpenedUri(),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.dataSource.getBytesRead());
    if (!released) {
      copyLengthFromLoader(loadable);
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.reset();
      }
      if (enabledTrackCount > 0) {
        callback.onContinueLoadingRequested(this);
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
    copyLengthFromLoader(loadable);
    LoadErrorAction retryAction;
    if (isLoadableExceptionFatal(error)) {
      retryAction = Loader.DONT_RETRY_FATAL;
    } else {
      int extractedSamplesCount = getExtractedSamplesCount();
      boolean madeProgress = extractedSamplesCount > extractedSamplesCountAtStartOfLoad;
      retryAction =
          configureRetry(loadable, extractedSamplesCount)
              ? (madeProgress ? Loader.RETRY_RESET_ERROR_COUNT : Loader.RETRY)
              : Loader.DONT_RETRY;
    }
    boolean wasCanceled =
        retryAction == Loader.DONT_RETRY || retryAction == Loader.DONT_RETRY_FATAL;
    eventDispatcher.loadError(
        loadable.dataSpec,
        loadable.dataSource.getLastOpenedUri(),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.dataSource.getBytesRead(),
        error,
        wasCanceled);
    return retryAction;
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public TrackOutput track(int id, int type) {
    int trackCount = sampleQueues.length;
    for (int i = 0; i < trackCount; i++) {
      if (sampleQueueTrackIds[i] == id) {
        return sampleQueues[i];
      }
    }
    SampleQueue trackOutput = new SampleQueue(allocator);
    trackOutput.setUpstreamFormatChangeListener(this);
    sampleQueueTrackIds = Arrays.copyOf(sampleQueueTrackIds, trackCount + 1);
    sampleQueueTrackIds[trackCount] = id;
    sampleQueues = Arrays.copyOf(sampleQueues, trackCount + 1);
    sampleQueues[trackCount] = trackOutput;
    return trackOutput;
  }

  @Override
  public void endTracks() {
    sampleQueuesBuilt = true;
    handler.post(maybeFinishPrepareRunnable);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
    handler.post(maybeFinishPrepareRunnable);
  }

  // UpstreamFormatChangedListener implementation. Called by the loading thread.

  @Override
  public void onUpstreamFormatChanged(Format format) {
    handler.post(maybeFinishPrepareRunnable);
  }

  // Internal methods.

  private void maybeFinishPrepare() {
    if (released || prepared || seekMap == null || !sampleQueuesBuilt) {
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
    trackIsAudioVideoFlags = new boolean[trackCount];
    trackEnabledStates = new boolean[trackCount];
    trackFormatNotificationSent = new boolean[trackCount];
    durationUs = seekMap.getDurationUs();
    for (int i = 0; i < trackCount; i++) {
      Format trackFormat = sampleQueues[i].getUpstreamFormat();
      trackArray[i] = new TrackGroup(trackFormat);
      String mimeType = trackFormat.sampleMimeType;
      boolean isAudioVideo = MimeTypes.isVideo(mimeType) || MimeTypes.isAudio(mimeType);
      trackIsAudioVideoFlags[i] = isAudioVideo;
      haveAudioVideoTracks |= isAudioVideo;
    }
    tracks = new TrackGroupArray(trackArray);
    if (minLoadableRetryCount == ExtractorMediaSource.MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA
        && length == C.LENGTH_UNSET && seekMap.getDurationUs() == C.TIME_UNSET) {
      actualMinLoadableRetryCount = ExtractorMediaSource.DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE;
    }
    prepared = true;
    listener.onSourceInfoRefreshed(durationUs, seekMap.isSeekable());
    callback.onPrepared(this);
  }

  private void copyLengthFromLoader(ExtractingLoadable loadable) {
    if (length == C.LENGTH_UNSET) {
      length = loadable.length;
    }
  }

  private void startLoading() {
    ExtractingLoadable loadable = new ExtractingLoadable(uri, dataSource, extractorHolder,
        loadCondition);
    if (prepared) {
      Assertions.checkState(isPendingReset());
      if (durationUs != C.TIME_UNSET && pendingResetPositionUs >= durationUs) {
        loadingFinished = true;
        pendingResetPositionUs = C.TIME_UNSET;
        return;
      }
      loadable.setLoadPosition(
          seekMap.getSeekPoints(pendingResetPositionUs).first.position, pendingResetPositionUs);
      pendingResetPositionUs = C.TIME_UNSET;
    }
    extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();
    long elapsedRealtimeMs = loader.startLoading(loadable, this, actualMinLoadableRetryCount);
    eventDispatcher.loadStarted(
        loadable.dataSpec,
        loadable.dataSpec.uri,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ loadable.seekTimeUs,
        durationUs,
        elapsedRealtimeMs);
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
    if (length != C.LENGTH_UNSET
        || (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET)) {
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
   * @param positionUs The seek position in microseconds.
   * @return Whether the in-buffer seek was successful.
   */
  private boolean seekInsideBufferUs(long positionUs) {
    int trackCount = sampleQueues.length;
    for (int i = 0; i < trackCount; i++) {
      SampleQueue sampleQueue = sampleQueues[i];
      sampleQueue.rewind();
      boolean seekInsideQueue = sampleQueue.advanceTo(positionUs, true, false)
          != SampleQueue.ADVANCE_FAILED;
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

  private long getLargestQueuedTimestampUs() {
    long largestQueuedTimestampUs = Long.MIN_VALUE;
    for (SampleQueue sampleQueue : sampleQueues) {
      largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs,
          sampleQueue.getLargestQueuedTimestampUs());
    }
    return largestQueuedTimestampUs;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private static boolean isLoadableExceptionFatal(IOException e) {
    return e instanceof UnrecognizedInputFormatException;
  }

  private final class SampleStreamImpl implements SampleStream {

    private final int track;

    public SampleStreamImpl(int track) {
      this.track = track;
    }

    @Override
    public boolean isReady() {
      return ExtractorMediaPeriod.this.isReady(track);
    }

    @Override
    public void maybeThrowError() throws IOException {
      ExtractorMediaPeriod.this.maybeThrowError();
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
        boolean formatRequired) {
      return ExtractorMediaPeriod.this.readData(track, formatHolder, buffer, formatRequired);
    }

    @Override
    public int skipData(long positionUs) {
      return ExtractorMediaPeriod.this.skipData(track, positionUs);
    }

  }

  /**
   * Loads the media stream and extracts sample data from it.
   */
  /* package */ final class ExtractingLoadable implements Loadable {

    private final Uri uri;
    private final StatsDataSource dataSource;
    private final ExtractorHolder extractorHolder;
    private final ConditionVariable loadCondition;
    private final PositionHolder positionHolder;

    private volatile boolean loadCanceled;

    private boolean pendingExtractorSeek;
    private long seekTimeUs;
    private DataSpec dataSpec;
    private long length;

    public ExtractingLoadable(Uri uri, DataSource dataSource, ExtractorHolder extractorHolder,
        ConditionVariable loadCondition) {
      this.uri = Assertions.checkNotNull(uri);
      this.dataSource = new StatsDataSource(dataSource);
      this.extractorHolder = Assertions.checkNotNull(extractorHolder);
      this.loadCondition = loadCondition;
      this.positionHolder = new PositionHolder();
      this.pendingExtractorSeek = true;
      this.length = C.LENGTH_UNSET;
      dataSpec = new DataSpec(uri, positionHolder.position, C.LENGTH_UNSET, customCacheKey);
    }

    public void setLoadPosition(long position, long timeUs) {
      positionHolder.position = position;
      seekTimeUs = timeUs;
      pendingExtractorSeek = true;
    }

    @Override
    public void cancelLoad() {
      loadCanceled = true;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      int result = Extractor.RESULT_CONTINUE;
      while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
        ExtractorInput input = null;
        try {
          long position = positionHolder.position;
          dataSpec = new DataSpec(uri, position, C.LENGTH_UNSET, customCacheKey);
          length = dataSource.open(dataSpec);
          if (length != C.LENGTH_UNSET) {
            length += position;
          }
          input = new DefaultExtractorInput(dataSource, position, length);
          Extractor extractor = extractorHolder.selectExtractor(input, dataSource.getUri());
          if (pendingExtractorSeek) {
            extractor.seek(position, seekTimeUs);
            pendingExtractorSeek = false;
          }
          while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
            loadCondition.block();
            result = extractor.read(input, positionHolder);
            if (input.getPosition() > position + continueLoadingCheckIntervalBytes) {
              position = input.getPosition();
              loadCondition.close();
              handler.post(onContinueLoadingRequestedRunnable);
            }
          }
        } finally {
          if (result == Extractor.RESULT_SEEK) {
            result = Extractor.RESULT_CONTINUE;
          } else if (input != null) {
            positionHolder.position = input.getPosition();
          }
          Util.closeQuietly(dataSource);
        }
      }
    }

  }

  /**
   * Stores a list of extractors and a selected extractor when the format has been detected.
   */
  private static final class ExtractorHolder {

    private final Extractor[] extractors;
    private final ExtractorOutput extractorOutput;
    private Extractor extractor;

    /**
     * Creates a holder that will select an extractor and initialize it using the specified output.
     *
     * @param extractors One or more extractors to choose from.
     * @param extractorOutput The output that will be used to initialize the selected extractor.
     */
    public ExtractorHolder(Extractor[] extractors, ExtractorOutput extractorOutput) {
      this.extractors = extractors;
      this.extractorOutput = extractorOutput;
    }

    /**
     * Returns an initialized extractor for reading {@code input}, and returns the same extractor on
     * later calls.
     *
     * @param input The {@link ExtractorInput} from which data should be read.
     * @param uri The {@link Uri} of the data.
     * @return An initialized extractor for reading {@code input}.
     * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
     * @throws IOException Thrown if the input could not be read.
     * @throws InterruptedException Thrown if the thread was interrupted.
     */
    public Extractor selectExtractor(ExtractorInput input, Uri uri)
        throws IOException, InterruptedException {
      if (extractor != null) {
        return extractor;
      }
      for (Extractor extractor : extractors) {
        try {
          if (extractor.sniff(input)) {
            this.extractor = extractor;
            break;
          }
        } catch (EOFException e) {
          // Do nothing.
        } finally {
          input.resetPeekPosition();
        }
      }
      if (extractor == null) {
        throw new UnrecognizedInputFormatException("None of the available extractors ("
            + Util.getCommaDelimitedSimpleClassNames(extractors) + ") could read the stream.", uri);
      }
      extractor.init(extractorOutput);
      return extractor;
    }

    public void release() {
      if (extractor != null) {
        extractor.release();
        extractor = null;
      }
    }

  }

}
