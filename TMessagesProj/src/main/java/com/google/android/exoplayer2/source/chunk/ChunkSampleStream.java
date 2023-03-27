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
package com.google.android.exoplayer2.source.chunk;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link SampleStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 * May also be configured to expose additional embedded {@link SampleStream}s.
 */
public class ChunkSampleStream<T extends ChunkSource>
    implements SampleStream, SequenceableLoader, Loader.Callback<Chunk>, Loader.ReleaseCallback {

  /** A callback to be notified when a sample stream has finished being released. */
  public interface ReleaseCallback<T extends ChunkSource> {

    /**
     * Called when the {@link ChunkSampleStream} has finished being released.
     *
     * @param chunkSampleStream The released sample stream.
     */
    void onSampleStreamReleased(ChunkSampleStream<T> chunkSampleStream);
  }

  private static final String TAG = "ChunkSampleStream";

  public final @C.TrackType int primaryTrackType;

  private final int[] embeddedTrackTypes;
  private final Format[] embeddedTrackFormats;
  private final boolean[] embeddedTracksSelected;
  private final T chunkSource;
  private final SequenceableLoader.Callback<ChunkSampleStream<T>> callback;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final Loader loader;
  private final ChunkHolder nextChunkHolder;
  private final ArrayList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final SampleQueue primarySampleQueue;
  private final SampleQueue[] embeddedSampleQueues;
  private final BaseMediaChunkOutput chunkOutput;

  @Nullable private Chunk loadingChunk;
  private @MonotonicNonNull Format primaryDownstreamTrackFormat;
  @Nullable private ReleaseCallback<T> releaseCallback;
  private long pendingResetPositionUs;
  private long lastSeekPositionUs;
  private int nextNotifyPrimaryFormatMediaChunkIndex;
  @Nullable private BaseMediaChunk canceledMediaChunk;

  /* package */ boolean loadingFinished;

  /**
   * Constructs an instance.
   *
   * @param primaryTrackType The {@link C.TrackType type} of the primary track.
   * @param embeddedTrackTypes The types of any embedded tracks, or null.
   * @param embeddedTrackFormats The formats of the embedded tracks, or null.
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param callback An {@link Callback} for the stream.
   * @param allocator An {@link Allocator} from which allocations can be obtained.
   * @param positionUs The position from which to start loading media.
   * @param drmSessionManager The {@link DrmSessionManager} to obtain {@link DrmSession DrmSessions}
   *     from.
   * @param drmEventDispatcher A dispatcher to notify of {@link DrmSessionEventListener} events.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param mediaSourceEventDispatcher A dispatcher to notify of {@link MediaSourceEventListener}
   *     events.
   */
  public ChunkSampleStream(
      @C.TrackType int primaryTrackType,
      @Nullable int[] embeddedTrackTypes,
      @Nullable Format[] embeddedTrackFormats,
      T chunkSource,
      Callback<ChunkSampleStream<T>> callback,
      Allocator allocator,
      long positionUs,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher) {
    this.primaryTrackType = primaryTrackType;
    this.embeddedTrackTypes = embeddedTrackTypes == null ? new int[0] : embeddedTrackTypes;
    this.embeddedTrackFormats = embeddedTrackFormats == null ? new Format[0] : embeddedTrackFormats;
    this.chunkSource = chunkSource;
    this.callback = callback;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    loader = new Loader("ChunkSampleStream");
    nextChunkHolder = new ChunkHolder();
    mediaChunks = new ArrayList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);

    int embeddedTrackCount = this.embeddedTrackTypes.length;
    embeddedSampleQueues = new SampleQueue[embeddedTrackCount];
    embeddedTracksSelected = new boolean[embeddedTrackCount];
    int[] trackTypes = new int[1 + embeddedTrackCount];
    SampleQueue[] sampleQueues = new SampleQueue[1 + embeddedTrackCount];

    primarySampleQueue =
        SampleQueue.createWithDrm(allocator, drmSessionManager, drmEventDispatcher);
    trackTypes[0] = primaryTrackType;
    sampleQueues[0] = primarySampleQueue;

    for (int i = 0; i < embeddedTrackCount; i++) {
      SampleQueue sampleQueue = SampleQueue.createWithoutDrm(allocator);
      embeddedSampleQueues[i] = sampleQueue;
      sampleQueues[i + 1] = sampleQueue;
      trackTypes[i + 1] = this.embeddedTrackTypes[i];
    }

    chunkOutput = new BaseMediaChunkOutput(trackTypes, sampleQueues);
    pendingResetPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
  }

  /**
   * Discards buffered media up to the specified position.
   *
   * @param positionUs The position to discard up to, in microseconds.
   * @param toKeyframe If true then for each track discards samples up to the keyframe before or at
   *     the specified position, rather than any sample before or at that position.
   */
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (isPendingReset()) {
      return;
    }
    int oldFirstSampleIndex = primarySampleQueue.getFirstIndex();
    primarySampleQueue.discardTo(positionUs, toKeyframe, true);
    int newFirstSampleIndex = primarySampleQueue.getFirstIndex();
    if (newFirstSampleIndex > oldFirstSampleIndex) {
      long discardToUs = primarySampleQueue.getFirstTimestampUs();
      for (int i = 0; i < embeddedSampleQueues.length; i++) {
        embeddedSampleQueues[i].discardTo(discardToUs, toKeyframe, embeddedTracksSelected[i]);
      }
    }
    discardDownstreamMediaChunks(newFirstSampleIndex);
  }

  /**
   * Selects the embedded track, returning a new {@link EmbeddedSampleStream} from which the track's
   * samples can be consumed. {@link EmbeddedSampleStream#release()} must be called on the returned
   * stream when the track is no longer required, and before calling this method again to obtain
   * another stream for the same track.
   *
   * @param positionUs The current playback position in microseconds.
   * @param trackType The type of the embedded track to enable.
   * @return The {@link EmbeddedSampleStream} for the embedded track.
   */
  public EmbeddedSampleStream selectEmbeddedTrack(long positionUs, int trackType) {
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      if (embeddedTrackTypes[i] == trackType) {
        Assertions.checkState(!embeddedTracksSelected[i]);
        embeddedTracksSelected[i] = true;
        embeddedSampleQueues[i].seekTo(positionUs, /* allowTimeBeyondBuffer= */ true);
        return new EmbeddedSampleStream(this, embeddedSampleQueues[i], i);
      }
    }
    // Should never happen.
    throw new IllegalStateException();
  }

  /** Returns the {@link ChunkSource} used by this stream. */
  public T getChunkSource() {
    return chunkSource;
  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
   */
  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      BaseMediaChunk lastMediaChunk = getLastMediaChunk();
      BaseMediaChunk lastCompletedMediaChunk =
          lastMediaChunk.isLoadCompleted()
              ? lastMediaChunk
              : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      return max(bufferedPositionUs, primarySampleQueue.getLargestQueuedTimestampUs());
    }
  }

  /**
   * Adjusts a seek position given the specified {@link SeekParameters}. Chunk boundaries are used
   * as sync points.
   *
   * @param positionUs The seek position in microseconds.
   * @param seekParameters Parameters that control how the seek is performed.
   * @return The adjusted seek position, in microseconds.
   */
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return chunkSource.getAdjustedSeekPositionUs(positionUs, seekParameters);
  }

  /**
   * Seeks to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   */
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    if (isPendingReset()) {
      // A reset is already pending. We only need to update its position.
      pendingResetPositionUs = positionUs;
      return;
    }

    // Detect whether the seek is to the start of a chunk that's at least partially buffered.
    @Nullable BaseMediaChunk seekToMediaChunk = null;
    for (int i = 0; i < mediaChunks.size(); i++) {
      BaseMediaChunk mediaChunk = mediaChunks.get(i);
      long mediaChunkStartTimeUs = mediaChunk.startTimeUs;
      if (mediaChunkStartTimeUs == positionUs && mediaChunk.clippedStartTimeUs == C.TIME_UNSET) {
        seekToMediaChunk = mediaChunk;
        break;
      } else if (mediaChunkStartTimeUs > positionUs) {
        // We're not going to find a chunk with a matching start time.
        break;
      }
    }

    // See if we can seek inside the primary sample queue.
    boolean seekInsideBuffer;
    if (seekToMediaChunk != null) {
      // When seeking to the start of a chunk we use the index of the first sample in the chunk
      // rather than the seek position. This ensures we seek to the keyframe at the start of the
      // chunk even if its timestamp is slightly earlier than the advertised chunk start time.
      seekInsideBuffer = primarySampleQueue.seekTo(seekToMediaChunk.getFirstSampleIndex(0));
    } else {
      seekInsideBuffer =
          primarySampleQueue.seekTo(
              positionUs, /* allowTimeBeyondBuffer= */ positionUs < getNextLoadPositionUs());
    }

    if (seekInsideBuffer) {
      // We can seek inside the buffer.
      nextNotifyPrimaryFormatMediaChunkIndex =
          primarySampleIndexToMediaChunkIndex(
              primarySampleQueue.getReadIndex(), /* minChunkIndex= */ 0);
      // Seek the embedded sample queues.
      for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ true);
      }
    } else {
      // We can't seek inside the buffer, and so need to reset.
      pendingResetPositionUs = positionUs;
      loadingFinished = false;
      mediaChunks.clear();
      nextNotifyPrimaryFormatMediaChunkIndex = 0;
      if (loader.isLoading()) {
        // Discard as much as we can synchronously.
        primarySampleQueue.discardToEnd();
        for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
          embeddedSampleQueue.discardToEnd();
        }
        loader.cancelLoading();
      } else {
        loader.clearFatalError();
        resetSampleQueues();
      }
    }
  }

  /**
   * Releases the stream.
   *
   * <p>This method should be called when the stream is no longer required. Either this method or
   * {@link #release(ReleaseCallback)} can be used to release this stream.
   */
  public void release() {
    release(null);
  }

  /**
   * Releases the stream.
   *
   * <p>This method should be called when the stream is no longer required. Either this method or
   * {@link #release()} can be used to release this stream.
   *
   * @param callback An optional callback to be called on the loading thread once the loader has
   *     been released.
   */
  public void release(@Nullable ReleaseCallback<T> callback) {
    this.releaseCallback = callback;
    // Discard as much as we can synchronously.
    primarySampleQueue.preRelease();
    for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.preRelease();
    }
    loader.release(this);
  }

  @Override
  public void onLoaderReleased() {
    primarySampleQueue.release();
    for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.release();
    }
    chunkSource.release();
    if (releaseCallback != null) {
      releaseCallback.onSampleStreamReleased(this);
    }
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return !isPendingReset() && primarySampleQueue.isReady(loadingFinished);
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    primarySampleQueue.maybeThrowError();
    if (!loader.isLoading()) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public int readData(
      FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }
    if (canceledMediaChunk != null
        && canceledMediaChunk.getFirstSampleIndex(/* trackIndex= */ 0)
            <= primarySampleQueue.getReadIndex()) {
      // Don't read into chunk that's going to be discarded.
      // TODO: Support splicing to allow this. See [internal b/161130873].
      return C.RESULT_NOTHING_READ;
    }
    maybeNotifyPrimaryTrackFormatChanged();

    return primarySampleQueue.read(formatHolder, buffer, readFlags, loadingFinished);
  }

  @Override
  public int skipData(long positionUs) {
    if (isPendingReset()) {
      return 0;
    }
    int skipCount = primarySampleQueue.getSkipCount(positionUs, loadingFinished);
    if (canceledMediaChunk != null) {
      // Don't skip into chunk that's going to be discarded.
      // TODO: Support splicing to allow this. See [internal b/161130873].
      int maxSkipCount =
          canceledMediaChunk.getFirstSampleIndex(/* trackIndex= */ 0)
              - primarySampleQueue.getReadIndex();
      skipCount = min(skipCount, maxSkipCount);
    }
    primarySampleQueue.skip(skipCount);
    maybeNotifyPrimaryTrackFormatChanged();
    return skipCount;
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
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(
      Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
    loadingChunk = null;
    canceledMediaChunk = null;
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
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs);
    if (!released) {
      if (isPendingReset()) {
        resetSampleQueues();
      } else if (isMediaChunk(loadable)) {
        // TODO: Support splicing to keep data from canceled chunk. See [internal b/161130873].
        discardUpstreamMediaChunksFromIndex(mediaChunks.size() - 1);
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public LoadErrorAction onLoadError(
      Chunk loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    int lastChunkIndex = mediaChunks.size() - 1;
    boolean cancelable =
        bytesLoaded == 0 || !isMediaChunk || !haveReadFromMediaChunk(lastChunkIndex);
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
            primaryTrackType,
            loadable.trackFormat,
            loadable.trackSelectionReason,
            loadable.trackSelectionData,
            Util.usToMs(loadable.startTimeUs),
            Util.usToMs(loadable.endTimeUs));
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount);

    @Nullable LoadErrorAction loadErrorAction = null;
    if (chunkSource.onChunkLoadError(
        loadable, cancelable, loadErrorInfo, loadErrorHandlingPolicy)) {
      if (cancelable) {
        loadErrorAction = Loader.DONT_RETRY;
        if (isMediaChunk) {
          BaseMediaChunk removed = discardUpstreamMediaChunksFromIndex(lastChunkIndex);
          Assertions.checkState(removed == loadable);
          if (mediaChunks.isEmpty()) {
            pendingResetPositionUs = lastSeekPositionUs;
          }
        }
      } else {
        Log.w(TAG, "Ignoring attempt to cancel non-cancelable load.");
      }
    }

    if (loadErrorAction == null) {
      // The load was not cancelled. Either the load must be retried or the error propagated.
      long retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
      loadErrorAction =
          retryDelayMs != C.TIME_UNSET
              ? Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs)
              : Loader.DONT_RETRY_FATAL;
    }

    boolean canceled = !loadErrorAction.isRetry();
    mediaSourceEventDispatcher.loadError(
        loadEventInfo,
        loadable.type,
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        error,
        canceled);
    if (canceled) {
      loadingChunk = null;
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
      callback.onContinueLoadingRequested(this);
    }
    return loadErrorAction;
  }

  // SequenceableLoader implementation

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading() || loader.hasFatalError()) {
      return false;
    }

    boolean pendingReset = isPendingReset();
    List<BaseMediaChunk> chunkQueue;
    long loadPositionUs;
    if (pendingReset) {
      chunkQueue = Collections.emptyList();
      loadPositionUs = pendingResetPositionUs;
    } else {
      chunkQueue = readOnlyMediaChunks;
      loadPositionUs = getLastMediaChunk().endTimeUs;
    }
    chunkSource.getNextChunk(positionUs, loadPositionUs, chunkQueue, nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    @Nullable Chunk loadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

    if (endOfStream) {
      pendingResetPositionUs = C.TIME_UNSET;
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      return false;
    }

    loadingChunk = loadable;
    if (isMediaChunk(loadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
      if (pendingReset) {
        // Only set the queue start times if we're not seeking to a chunk boundary. If we are
        // seeking to a chunk boundary then we want the queue to pass through all of the samples in
        // the chunk. Doing this ensures we'll always output the keyframe at the start of the chunk,
        // even if its timestamp is slightly earlier than the advertised chunk start time.
        if (mediaChunk.startTimeUs != pendingResetPositionUs) {
          primarySampleQueue.setStartTimeUs(pendingResetPositionUs);
          for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
            embeddedSampleQueue.setStartTimeUs(pendingResetPositionUs);
          }
        }
        pendingResetPositionUs = C.TIME_UNSET;
      }
      mediaChunk.init(chunkOutput);
      mediaChunks.add(mediaChunk);
    } else if (loadable instanceof InitializationChunk) {
      ((InitializationChunk) loadable).init(chunkOutput);
    }
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    mediaSourceEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs),
        loadable.type,
        primaryTrackType,
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
  public long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.TIME_END_OF_SOURCE : getLastMediaChunk().endTimeUs;
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    if (loader.hasFatalError() || isPendingReset()) {
      return;
    }

    if (loader.isLoading()) {
      Chunk loadingChunk = checkNotNull(this.loadingChunk);
      if (isMediaChunk(loadingChunk)
          && haveReadFromMediaChunk(/* mediaChunkIndex= */ mediaChunks.size() - 1)) {
        // Can't cancel anymore because the renderers have read from this chunk.
        return;
      }
      if (chunkSource.shouldCancelLoad(positionUs, loadingChunk, readOnlyMediaChunks)) {
        loader.cancelLoading();
        if (isMediaChunk(loadingChunk)) {
          canceledMediaChunk = (BaseMediaChunk) loadingChunk;
        }
      }
      return;
    }

    int preferredQueueSize = chunkSource.getPreferredQueueSize(positionUs, readOnlyMediaChunks);
    if (preferredQueueSize < mediaChunks.size()) {
      discardUpstream(preferredQueueSize);
    }
  }

  private void discardUpstream(int preferredQueueSize) {
    Assertions.checkState(!loader.isLoading());

    int currentQueueSize = mediaChunks.size();
    int newQueueSize = C.LENGTH_UNSET;
    for (int i = preferredQueueSize; i < currentQueueSize; i++) {
      if (!haveReadFromMediaChunk(i)) {
        // TODO: Sparse tracks (e.g. ESMG) may prevent discarding in almost all cases because it
        // means that most chunks have been read from already. See [internal b/161126666].
        newQueueSize = i;
        break;
      }
    }
    if (newQueueSize == C.LENGTH_UNSET) {
      return;
    }

    long endTimeUs = getLastMediaChunk().endTimeUs;
    BaseMediaChunk firstRemovedChunk = discardUpstreamMediaChunksFromIndex(newQueueSize);
    if (mediaChunks.isEmpty()) {
      pendingResetPositionUs = lastSeekPositionUs;
    }
    loadingFinished = false;
    mediaSourceEventDispatcher.upstreamDiscarded(
        primaryTrackType, firstRemovedChunk.startTimeUs, endTimeUs);
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  private void resetSampleQueues() {
    primarySampleQueue.reset();
    for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.reset();
    }
  }

  /** Returns whether samples have been read from media chunk at given index. */
  private boolean haveReadFromMediaChunk(int mediaChunkIndex) {
    BaseMediaChunk mediaChunk = mediaChunks.get(mediaChunkIndex);
    if (primarySampleQueue.getReadIndex() > mediaChunk.getFirstSampleIndex(0)) {
      return true;
    }
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      if (embeddedSampleQueues[i].getReadIndex() > mediaChunk.getFirstSampleIndex(i + 1)) {
        return true;
      }
    }
    return false;
  }

  /* package */ boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private void discardDownstreamMediaChunks(int discardToSampleIndex) {
    int discardToMediaChunkIndex =
        primarySampleIndexToMediaChunkIndex(discardToSampleIndex, /* minChunkIndex= */ 0);
    // Don't discard any chunks that we haven't reported the primary format change for yet.
    discardToMediaChunkIndex =
        min(discardToMediaChunkIndex, nextNotifyPrimaryFormatMediaChunkIndex);
    if (discardToMediaChunkIndex > 0) {
      Util.removeRange(mediaChunks, /* fromIndex= */ 0, /* toIndex= */ discardToMediaChunkIndex);
      nextNotifyPrimaryFormatMediaChunkIndex -= discardToMediaChunkIndex;
    }
  }

  private void maybeNotifyPrimaryTrackFormatChanged() {
    int readSampleIndex = primarySampleQueue.getReadIndex();
    int notifyToMediaChunkIndex =
        primarySampleIndexToMediaChunkIndex(
            readSampleIndex, /* minChunkIndex= */ nextNotifyPrimaryFormatMediaChunkIndex - 1);
    while (nextNotifyPrimaryFormatMediaChunkIndex <= notifyToMediaChunkIndex) {
      maybeNotifyPrimaryTrackFormatChanged(nextNotifyPrimaryFormatMediaChunkIndex++);
    }
  }

  private void maybeNotifyPrimaryTrackFormatChanged(int mediaChunkReadIndex) {
    BaseMediaChunk currentChunk = mediaChunks.get(mediaChunkReadIndex);
    Format trackFormat = currentChunk.trackFormat;
    if (!trackFormat.equals(primaryDownstreamTrackFormat)) {
      mediaSourceEventDispatcher.downstreamFormatChanged(
          primaryTrackType,
          trackFormat,
          currentChunk.trackSelectionReason,
          currentChunk.trackSelectionData,
          currentChunk.startTimeUs);
    }
    primaryDownstreamTrackFormat = trackFormat;
  }

  /**
   * Returns the media chunk index corresponding to a given primary sample index.
   *
   * @param primarySampleIndex The primary sample index for which the corresponding media chunk
   *     index is required.
   * @param minChunkIndex A minimum chunk index from which to start searching, or -1 if no hint can
   *     be provided.
   * @return The index of the media chunk corresponding to the sample index, or -1 if the list of
   *     media chunks is empty, or {@code minChunkIndex} if the sample precedes the first chunk in
   *     the search (i.e. the chunk at {@code minChunkIndex}, or at index 0 if {@code minChunkIndex}
   *     is -1.
   */
  private int primarySampleIndexToMediaChunkIndex(int primarySampleIndex, int minChunkIndex) {
    for (int i = minChunkIndex + 1; i < mediaChunks.size(); i++) {
      if (mediaChunks.get(i).getFirstSampleIndex(0) > primarySampleIndex) {
        return i - 1;
      }
    }
    return mediaChunks.size() - 1;
  }

  private BaseMediaChunk getLastMediaChunk() {
    return mediaChunks.get(mediaChunks.size() - 1);
  }

  /**
   * Discard upstream media chunks from {@code chunkIndex} and corresponding samples from sample
   * queues.
   *
   * @param chunkIndex The index of the first chunk to discard.
   * @return The chunk at given index.
   */
  private BaseMediaChunk discardUpstreamMediaChunksFromIndex(int chunkIndex) {
    BaseMediaChunk firstRemovedChunk = mediaChunks.get(chunkIndex);
    Util.removeRange(mediaChunks, /* fromIndex= */ chunkIndex, /* toIndex= */ mediaChunks.size());
    nextNotifyPrimaryFormatMediaChunkIndex =
        max(nextNotifyPrimaryFormatMediaChunkIndex, mediaChunks.size());
    primarySampleQueue.discardUpstreamSamples(firstRemovedChunk.getFirstSampleIndex(0));
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      embeddedSampleQueues[i].discardUpstreamSamples(firstRemovedChunk.getFirstSampleIndex(i + 1));
    }
    return firstRemovedChunk;
  }

  /** A {@link SampleStream} embedded in a {@link ChunkSampleStream}. */
  public final class EmbeddedSampleStream implements SampleStream {

    public final ChunkSampleStream<T> parent;

    private final SampleQueue sampleQueue;
    private final int index;

    private boolean notifiedDownstreamFormat;

    public EmbeddedSampleStream(ChunkSampleStream<T> parent, SampleQueue sampleQueue, int index) {
      this.parent = parent;
      this.sampleQueue = sampleQueue;
      this.index = index;
    }

    @Override
    public boolean isReady() {
      return !isPendingReset() && sampleQueue.isReady(loadingFinished);
    }

    @Override
    public int skipData(long positionUs) {
      if (isPendingReset()) {
        return 0;
      }
      int skipCount = sampleQueue.getSkipCount(positionUs, loadingFinished);
      if (canceledMediaChunk != null) {
        // Don't skip into chunk that's going to be discarded.
        // TODO: Support splicing to allow this. See [internal b/161130873].
        int maxSkipCount =
            canceledMediaChunk.getFirstSampleIndex(/* trackIndex= */ 1 + index)
                - sampleQueue.getReadIndex();
        skipCount = min(skipCount, maxSkipCount);
      }
      sampleQueue.skip(skipCount);
      if (skipCount > 0) {
        maybeNotifyDownstreamFormat();
      }
      return skipCount;
    }

    @Override
    public void maybeThrowError() {
      // Do nothing. Errors will be thrown from the primary stream.
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      if (isPendingReset()) {
        return C.RESULT_NOTHING_READ;
      }
      if (canceledMediaChunk != null
          && canceledMediaChunk.getFirstSampleIndex(/* trackIndex= */ 1 + index)
              <= sampleQueue.getReadIndex()) {
        // Don't read into chunk that's going to be discarded.
        // TODO: Support splicing to allow this. See [internal b/161130873].
        return C.RESULT_NOTHING_READ;
      }
      maybeNotifyDownstreamFormat();
      return sampleQueue.read(formatHolder, buffer, readFlags, loadingFinished);
    }

    public void release() {
      Assertions.checkState(embeddedTracksSelected[index]);
      embeddedTracksSelected[index] = false;
    }

    private void maybeNotifyDownstreamFormat() {
      if (!notifiedDownstreamFormat) {
        mediaSourceEventDispatcher.downstreamFormatChanged(
            embeddedTrackTypes[index],
            embeddedTrackFormats[index],
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            lastSeekPositionUs);
        notifiedDownstreamFormat = true;
      }
    }
  }
}
