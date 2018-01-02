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
package org.telegram.messenger.exoplayer2.source.chunk;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.SampleQueue;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.source.SequenceableLoader;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link SampleStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 * May also be configured to expose additional embedded {@link SampleStream}s.
 */
public class ChunkSampleStream<T extends ChunkSource> implements SampleStream, SequenceableLoader,
    Loader.Callback<Chunk>, Loader.ReleaseCallback {

  private final int primaryTrackType;
  private final int[] embeddedTrackTypes;
  private final boolean[] embeddedTracksSelected;
  private final T chunkSource;
  private final SequenceableLoader.Callback<ChunkSampleStream<T>> callback;
  private final EventDispatcher eventDispatcher;
  private final int minLoadableRetryCount;
  private final Loader loader;
  private final ChunkHolder nextChunkHolder;
  private final LinkedList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final SampleQueue primarySampleQueue;
  private final SampleQueue[] embeddedSampleQueues;
  private final BaseMediaChunkOutput mediaChunkOutput;

  private Format primaryDownstreamTrackFormat;
  private long pendingResetPositionUs;
  /* package */ long lastSeekPositionUs;
  /* package */ boolean loadingFinished;

  /**
   * @param primaryTrackType The type of the primary track. One of the {@link C}
   *     {@code TRACK_TYPE_*} constants.
   * @param embeddedTrackTypes The types of any embedded tracks, or null.
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param callback An {@link Callback} for the stream.
   * @param allocator An {@link Allocator} from which allocations can be obtained.
   * @param positionUs The position from which to start loading media.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   * @param eventDispatcher A dispatcher to notify of events.
   */
  public ChunkSampleStream(int primaryTrackType, int[] embeddedTrackTypes, T chunkSource,
      Callback<ChunkSampleStream<T>> callback, Allocator allocator, long positionUs,
      int minLoadableRetryCount, EventDispatcher eventDispatcher) {
    this.primaryTrackType = primaryTrackType;
    this.embeddedTrackTypes = embeddedTrackTypes;
    this.chunkSource = chunkSource;
    this.callback = callback;
    this.eventDispatcher = eventDispatcher;
    this.minLoadableRetryCount = minLoadableRetryCount;
    loader = new Loader("Loader:ChunkSampleStream");
    nextChunkHolder = new ChunkHolder();
    mediaChunks = new LinkedList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);

    int embeddedTrackCount = embeddedTrackTypes == null ? 0 : embeddedTrackTypes.length;
    embeddedSampleQueues = new SampleQueue[embeddedTrackCount];
    embeddedTracksSelected = new boolean[embeddedTrackCount];
    int[] trackTypes = new int[1 + embeddedTrackCount];
    SampleQueue[] sampleQueues = new SampleQueue[1 + embeddedTrackCount];

    primarySampleQueue = new SampleQueue(allocator);
    trackTypes[0] = primaryTrackType;
    sampleQueues[0] = primarySampleQueue;

    for (int i = 0; i < embeddedTrackCount; i++) {
      SampleQueue sampleQueue = new SampleQueue(allocator);
      embeddedSampleQueues[i] = sampleQueue;
      sampleQueues[i + 1] = sampleQueue;
      trackTypes[i + 1] = embeddedTrackTypes[i];
    }

    mediaChunkOutput = new BaseMediaChunkOutput(trackTypes, sampleQueues);
    pendingResetPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
  }

  // TODO: Generalize this method to also discard from the primary sample queue and stop discarding
  // from this queue in readData and skipData. This will cause samples to be kept in the queue until
  // they've been rendered, rather than being discarded as soon as they're read by the renderer.
  // This will make in-buffer seeks more likely when seeking slightly forward from the current
  // position. This change will need handling with care, in particular when considering removal of
  // chunks from the front of the mediaChunks list.
  /**
   * Discards buffered media for embedded tracks, up to the specified position.
   *
   * @param positionUs The position to discard up to, in microseconds.
   */
  public void discardEmbeddedTracksTo(long positionUs) {
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      embeddedSampleQueues[i].discardTo(positionUs, true, embeddedTracksSelected[i]);
    }
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
        embeddedSampleQueues[i].rewind();
        embeddedSampleQueues[i].advanceTo(positionUs, true, true);
        return new EmbeddedSampleStream(this, embeddedSampleQueues[i], i);
      }
    }
    // Should never happen.
    throw new IllegalStateException();
  }

  /**
   * Returns the {@link ChunkSource} used by this stream.
   */
  public T getChunkSource() {
    return chunkSource;
  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
   */
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      BaseMediaChunk lastMediaChunk = mediaChunks.getLast();
      BaseMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
          : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      return Math.max(bufferedPositionUs, primarySampleQueue.getLargestQueuedTimestampUs());
    }
  }

  /**
   * Seeks to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   */
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    // If we're not pending a reset, see if we can seek within the primary sample queue.
    boolean seekInsideBuffer = !isPendingReset() && primarySampleQueue.advanceTo(positionUs, true,
        positionUs < getNextLoadPositionUs());
    if (seekInsideBuffer) {
      // We succeeded. Discard samples and corresponding chunks prior to the seek position.
      discardDownstreamMediaChunks(primarySampleQueue.getReadIndex());
      primarySampleQueue.discardToRead();
      for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.rewind();
        embeddedSampleQueue.discardTo(positionUs, true, false);
      }
    } else {
      // We failed, and need to restart.
      pendingResetPositionUs = positionUs;
      loadingFinished = false;
      mediaChunks.clear();
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        primarySampleQueue.reset();
        for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
          embeddedSampleQueue.reset();
        }
      }
    }
  }

  /**
   * Releases the stream.
   * <p>
   * This method should be called when the stream is no longer required.
   */
  public void release() {
    boolean releasedSynchronously = loader.release(this);
    if (!releasedSynchronously) {
      // Discard as much as we can synchronously.
      primarySampleQueue.discardToEnd();
      for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.discardToEnd();
      }
    }
  }

  @Override
  public void onLoaderReleased() {
    primarySampleQueue.reset();
    for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.reset();
    }
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished || (!isPendingReset() && primarySampleQueue.hasNextSample());
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    if (!loader.isLoading()) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }
    discardDownstreamMediaChunks(primarySampleQueue.getReadIndex());
    int result = primarySampleQueue.read(formatHolder, buffer, formatRequired, loadingFinished,
        lastSeekPositionUs);
    if (result == C.RESULT_BUFFER_READ) {
      primarySampleQueue.discardToRead();
    }
    return result;
  }

  @Override
  public void skipData(long positionUs) {
    if (loadingFinished && positionUs > primarySampleQueue.getLargestQueuedTimestampUs()) {
      primarySampleQueue.advanceToEnd();
    } else {
      primarySampleQueue.advanceTo(positionUs, true, true);
    }
    primarySampleQueue.discardToRead();
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    chunkSource.onChunkLoadCompleted(loadable);
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, primaryTrackType,
        loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
        loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded());
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(loadable.dataSpec, loadable.type, primaryTrackType,
        loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
        loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded());
    if (!released) {
      primarySampleQueue.reset();
      for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.reset();
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      IOException error) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0 || mediaChunks.size() > 1;
    boolean canceled = false;
    if (chunkSource.onChunkLoadError(loadable, cancelable, error)) {
      canceled = true;
      if (isMediaChunk) {
        BaseMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == loadable);
        primarySampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex(0));
        for (int i = 0; i < embeddedSampleQueues.length; i++) {
          embeddedSampleQueues[i].discardUpstreamSamples(removed.getFirstSampleIndex(i + 1));
        }
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
    }
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, primaryTrackType,
        loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
        loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, bytesLoaded,
        error, canceled);
    if (canceled) {
      callback.onContinueLoadingRequested(this);
      return Loader.DONT_RETRY;
    } else {
      return Loader.RETRY;
    }
  }

  // SequenceableLoader implementation

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading()) {
      return false;
    }

    chunkSource.getNextChunk(mediaChunks.isEmpty() ? null : mediaChunks.getLast(),
        pendingResetPositionUs != C.TIME_UNSET ? pendingResetPositionUs : positionUs,
        nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk loadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

    if (endOfStream) {
      pendingResetPositionUs = C.TIME_UNSET;
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      return false;
    }

    if (isMediaChunk(loadable)) {
      pendingResetPositionUs = C.TIME_UNSET;
      BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
      mediaChunk.init(mediaChunkOutput);
      mediaChunks.add(mediaChunk);
    }
    long elapsedRealtimeMs = loader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, primaryTrackType,
        loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData,
        loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs);
    return true;
  }

  @Override
  public long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.TIME_END_OF_SOURCE : mediaChunks.getLast().endTimeUs;
    }
  }

  // Internal methods

  // TODO[REFACTOR]: Call maybeDiscardUpstream for DASH and SmoothStreaming.
  /**
   * Discards media chunks from the back of the buffer if conditions have changed such that it's
   * preferable to re-buffer the media at a different quality.
   *
   * @param positionUs The current playback position in microseconds.
   */
  private void maybeDiscardUpstream(long positionUs) {
    int queueSize = chunkSource.getPreferredQueueSize(positionUs, readOnlyMediaChunks);
    discardUpstreamMediaChunks(Math.max(1, queueSize));
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  /* package */ boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private void discardDownstreamMediaChunks(int primaryStreamReadIndex) {
    if (!mediaChunks.isEmpty()) {
      while (mediaChunks.size() > 1
          && mediaChunks.get(1).getFirstSampleIndex(0) <= primaryStreamReadIndex) {
        mediaChunks.removeFirst();
      }
      BaseMediaChunk currentChunk = mediaChunks.getFirst();
      Format trackFormat = currentChunk.trackFormat;
      if (!trackFormat.equals(primaryDownstreamTrackFormat)) {
        eventDispatcher.downstreamFormatChanged(primaryTrackType, trackFormat,
            currentChunk.trackSelectionReason, currentChunk.trackSelectionData,
            currentChunk.startTimeUs);
      }
      primaryDownstreamTrackFormat = trackFormat;
    }
  }

  /**
   * Discard upstream media chunks until the queue length is equal to the length specified.
   *
   * @param queueLength The desired length of the queue.
   * @return Whether chunks were discarded.
   */
  private boolean discardUpstreamMediaChunks(int queueLength) {
    if (mediaChunks.size() <= queueLength) {
      return false;
    }
    BaseMediaChunk removed;
    long startTimeUs;
    long endTimeUs = mediaChunks.getLast().endTimeUs;
    do {
      removed = mediaChunks.removeLast();
      startTimeUs = removed.startTimeUs;
    } while (mediaChunks.size() > queueLength);
    primarySampleQueue.discardUpstreamSamples(removed.getFirstSampleIndex(0));
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      embeddedSampleQueues[i].discardUpstreamSamples(removed.getFirstSampleIndex(i + 1));
    }
    loadingFinished = false;
    eventDispatcher.upstreamDiscarded(primaryTrackType, startTimeUs, endTimeUs);
    return true;
  }

  /**
   * A {@link SampleStream} embedded in a {@link ChunkSampleStream}.
   */
  public final class EmbeddedSampleStream implements SampleStream {

    public final ChunkSampleStream<T> parent;

    private final SampleQueue sampleQueue;
    private final int index;

    public EmbeddedSampleStream(ChunkSampleStream<T> parent, SampleQueue sampleQueue, int index) {
      this.parent = parent;
      this.sampleQueue = sampleQueue;
      this.index = index;
    }

    @Override
    public boolean isReady() {
      return loadingFinished || (!isPendingReset() && sampleQueue.hasNextSample());
    }

    @Override
    public void skipData(long positionUs) {
      if (loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
        sampleQueue.advanceToEnd();
      } else {
        sampleQueue.advanceTo(positionUs, true, true);
      }
    }

    @Override
    public void maybeThrowError() throws IOException {
      // Do nothing. Errors will be thrown from the primary stream.
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
        boolean formatRequired) {
      if (isPendingReset()) {
        return C.RESULT_NOTHING_READ;
      }
      return sampleQueue.read(formatHolder, buffer, formatRequired, loadingFinished,
          lastSeekPositionUs);
    }

    public void release() {
      Assertions.checkState(embeddedTracksSelected[index]);
      embeddedTracksSelected[index] = false;
    }

  }

}
