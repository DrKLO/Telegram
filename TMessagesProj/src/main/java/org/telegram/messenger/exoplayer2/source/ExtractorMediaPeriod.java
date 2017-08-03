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
package org.telegram.messenger.exoplayer2.source;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.extractor.DefaultExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.DefaultTrackOutput;
import org.telegram.messenger.exoplayer2.extractor.DefaultTrackOutput.UpstreamFormatChangedListener;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.PositionHolder;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.ConditionVariable;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;

/**
 * A {@link MediaPeriod} that extracts data using an {@link Extractor}.
 */
/* package */ final class ExtractorMediaPeriod implements MediaPeriod, ExtractorOutput,
    Loader.Callback<ExtractorMediaPeriod.ExtractingLoadable>, UpstreamFormatChangedListener {

  /**
   * When the source's duration is unknown, it is calculated by adding this value to the largest
   * sample timestamp seen when buffering completes.
   */
  private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10000;

  private final Uri uri;
  private final DataSource dataSource;
  private final int minLoadableRetryCount;
  private final Handler eventHandler;
  private final ExtractorMediaSource.EventListener eventListener;
  private final MediaSource.Listener sourceListener;
  private final Allocator allocator;
  private final String customCacheKey;
  private final Loader loader;
  private final ExtractorHolder extractorHolder;
  private final ConditionVariable loadCondition;
  private final Runnable maybeFinishPrepareRunnable;
  private final Runnable onContinueLoadingRequestedRunnable;
  private final Handler handler;
  private final SparseArray<DefaultTrackOutput> sampleQueues;

  private Callback callback;
  private SeekMap seekMap;
  private boolean tracksBuilt;
  private boolean prepared;

  private boolean seenFirstTrackSelection;
  private boolean notifyReset;
  private int enabledTrackCount;
  private TrackGroupArray tracks;
  private long durationUs;
  private boolean[] trackEnabledStates;
  private boolean[] trackIsAudioVideoFlags;
  private boolean haveAudioVideoTracks;
  private long length;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private int extractedSamplesCountAtStartOfLoad;
  private boolean loadingFinished;
  private boolean released;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource The data source to read the media.
   * @param extractors The extractors to use to read the data source.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param sourceListener A listener to notify when the timeline has been loaded.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param customCacheKey A custom key that uniquely identifies the original stream. Used for cache
   *     indexing. May be null.
   */
  public ExtractorMediaPeriod(Uri uri, DataSource dataSource, Extractor[] extractors,
      int minLoadableRetryCount, Handler eventHandler,
      ExtractorMediaSource.EventListener eventListener, MediaSource.Listener sourceListener,
      Allocator allocator, String customCacheKey) {
    this.uri = uri;
    this.dataSource = dataSource;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.sourceListener = sourceListener;
    this.allocator = allocator;
    this.customCacheKey = customCacheKey;
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

    pendingResetPositionUs = C.TIME_UNSET;
    sampleQueues = new SparseArray<>();
    length = C.LENGTH_UNSET;
  }

  public void release() {
    final ExtractorHolder extractorHolder = this.extractorHolder;
    loader.release(new Runnable() {
      @Override
      public void run() {
        extractorHolder.release();
        int trackCount = sampleQueues.size();
        for (int i = 0; i < trackCount; i++) {
          sampleQueues.valueAt(i).disable();
        }
      }
    });
    handler.removeCallbacksAndMessages(null);
    released = true;
  }

  @Override
  public void prepare(Callback callback) {
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
    // Disable old tracks.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        int track = ((SampleStreamImpl) streams[i]).track;
        Assertions.checkState(trackEnabledStates[track]);
        enabledTrackCount--;
        trackEnabledStates[track] = false;
        sampleQueues.valueAt(track).disable();
        streams[i] = null;
      }
    }
    // Enable new tracks.
    boolean selectedNewTracks = false;
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
        selectedNewTracks = true;
      }
    }
    if (!seenFirstTrackSelection) {
      // At the time of the first track selection all queues will be enabled, so we need to disable
      // any that are no longer required.
      int trackCount = sampleQueues.size();
      for (int i = 0; i < trackCount; i++) {
        if (!trackEnabledStates[i]) {
          sampleQueues.valueAt(i).disable();
        }
      }
    }
    if (enabledTrackCount == 0) {
      notifyReset = false;
      if (loader.isLoading()) {
        loader.cancelLoading();
      }
    } else if (seenFirstTrackSelection ? selectedNewTracks : positionUs != 0) {
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
  public void discardBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public boolean continueLoading(long playbackPositionUs) {
    if (loadingFinished || (prepared && enabledTrackCount == 0)) {
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
    if (notifyReset) {
      notifyReset = false;
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
      int trackCount = sampleQueues.size();
      for (int i = 0; i < trackCount; i++) {
        if (trackIsAudioVideoFlags[i]) {
          largestQueuedTimestampUs = Math.min(largestQueuedTimestampUs,
              sampleQueues.valueAt(i).getLargestQueuedTimestampUs());
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
    int trackCount = sampleQueues.size();
    // If we're not pending a reset, see if we can seek within the sample queues.
    boolean seekInsideBuffer = !isPendingReset();
    for (int i = 0; seekInsideBuffer && i < trackCount; i++) {
      if (trackEnabledStates[i]) {
        seekInsideBuffer = sampleQueues.valueAt(i).skipToKeyframeBefore(positionUs, false);
      }
    }
    // If we failed to seek within the sample queues, we need to restart.
    if (!seekInsideBuffer) {
      pendingResetPositionUs = positionUs;
      loadingFinished = false;
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        for (int i = 0; i < trackCount; i++) {
          sampleQueues.valueAt(i).reset(trackEnabledStates[i]);
        }
      }
    }
    notifyReset = false;
    return positionUs;
  }

  // SampleStream methods.

  /* package */ boolean isReady(int track) {
    return loadingFinished || (!isPendingReset() && !sampleQueues.valueAt(track).isEmpty());
  }

  /* package */ void maybeThrowError() throws IOException {
    loader.maybeThrowError();
  }

  /* package */ int readData(int track, FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired) {
    if (notifyReset || isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }

    return sampleQueues.valueAt(track).readData(formatHolder, buffer, formatRequired,
        loadingFinished, lastSeekPositionUs);
  }

  /* package */ void skipData(int track, long positionUs) {
    DefaultTrackOutput sampleQueue = sampleQueues.valueAt(track);
    if (loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
      sampleQueue.skipAll();
    } else {
      sampleQueue.skipToKeyframeBefore(positionUs, true);
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    copyLengthFromLoader(loadable);
    loadingFinished = true;
    if (durationUs == C.TIME_UNSET) {
      long largestQueuedTimestampUs = getLargestQueuedTimestampUs();
      durationUs = largestQueuedTimestampUs == Long.MIN_VALUE ? 0
          : largestQueuedTimestampUs + DEFAULT_LAST_SAMPLE_DURATION_US;
      sourceListener.onSourceInfoRefreshed(
          new SinglePeriodTimeline(durationUs, seekMap.isSeekable()), null);
    }
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    copyLengthFromLoader(loadable);
    if (!released && enabledTrackCount > 0) {
      int trackCount = sampleQueues.size();
      for (int i = 0; i < trackCount; i++) {
        sampleQueues.valueAt(i).reset(trackEnabledStates[i]);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public int onLoadError(ExtractingLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    copyLengthFromLoader(loadable);
    notifyLoadError(error);
    if (isLoadableExceptionFatal(error)) {
      return Loader.DONT_RETRY_FATAL;
    }
    int extractedSamplesCount = getExtractedSamplesCount();
    boolean madeProgress = extractedSamplesCount > extractedSamplesCountAtStartOfLoad;
    configureRetry(loadable); // May reset the sample queues.
    extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();
    return madeProgress ? Loader.RETRY_RESET_ERROR_COUNT : Loader.RETRY;
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public TrackOutput track(int id, int type) {
    DefaultTrackOutput trackOutput = sampleQueues.get(id);
    if (trackOutput == null) {
      trackOutput = new DefaultTrackOutput(allocator);
      trackOutput.setUpstreamFormatChangeListener(this);
      sampleQueues.put(id, trackOutput);
    }
    return trackOutput;
  }

  @Override
  public void endTracks() {
    tracksBuilt = true;
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
    if (released || prepared || seekMap == null || !tracksBuilt) {
      return;
    }
    int trackCount = sampleQueues.size();
    for (int i = 0; i < trackCount; i++) {
      if (sampleQueues.valueAt(i).getUpstreamFormat() == null) {
        return;
      }
    }
    loadCondition.close();
    TrackGroup[] trackArray = new TrackGroup[trackCount];
    trackIsAudioVideoFlags = new boolean[trackCount];
    trackEnabledStates = new boolean[trackCount];
    durationUs = seekMap.getDurationUs();
    for (int i = 0; i < trackCount; i++) {
      Format trackFormat = sampleQueues.valueAt(i).getUpstreamFormat();
      trackArray[i] = new TrackGroup(trackFormat);
      String mimeType = trackFormat.sampleMimeType;
      boolean isAudioVideo = MimeTypes.isVideo(mimeType) || MimeTypes.isAudio(mimeType);
      trackIsAudioVideoFlags[i] = isAudioVideo;
      haveAudioVideoTracks |= isAudioVideo;
    }
    tracks = new TrackGroupArray(trackArray);
    prepared = true;
    sourceListener.onSourceInfoRefreshed(
        new SinglePeriodTimeline(durationUs, seekMap.isSeekable()), null);
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
      loadable.setLoadPosition(seekMap.getPosition(pendingResetPositionUs), pendingResetPositionUs);
      pendingResetPositionUs = C.TIME_UNSET;
    }
    extractedSamplesCountAtStartOfLoad = getExtractedSamplesCount();

    int minRetryCount = minLoadableRetryCount;
    if (minRetryCount == ExtractorMediaSource.MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA) {
      // We assume on-demand before we're prepared.
      minRetryCount = !prepared || length != C.LENGTH_UNSET
          || (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET)
          ? ExtractorMediaSource.DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND
          : ExtractorMediaSource.DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE;
    }
    loader.startLoading(loadable, this, minRetryCount);
  }

  private void configureRetry(ExtractingLoadable loadable) {
    if (length != C.LENGTH_UNSET
        || (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET)) {
      // We're playing an on-demand stream. Resume the current loadable, which will
      // request data starting from the point it left off.
    } else {
      // We're playing a stream of unknown length and duration. Assume it's live, and
      // therefore that the data at the uri is a continuously shifting window of the latest
      // available media. For this case there's no way to continue loading from where a
      // previous load finished, so it's necessary to load from the start whenever commencing
      // a new load.
      lastSeekPositionUs = 0;
      notifyReset = prepared;
      int trackCount = sampleQueues.size();
      for (int i = 0; i < trackCount; i++) {
        sampleQueues.valueAt(i).reset(!prepared || trackEnabledStates[i]);
      }
      loadable.setLoadPosition(0, 0);
    }
  }

  private int getExtractedSamplesCount() {
    int extractedSamplesCount = 0;
    int trackCount = sampleQueues.size();
    for (int i = 0; i < trackCount; i++) {
      extractedSamplesCount += sampleQueues.valueAt(i).getWriteIndex();
    }
    return extractedSamplesCount;
  }

  private long getLargestQueuedTimestampUs() {
    long largestQueuedTimestampUs = Long.MIN_VALUE;
    int trackCount = sampleQueues.size();
    for (int i = 0; i < trackCount; i++) {
      largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs,
          sampleQueues.valueAt(i).getLargestQueuedTimestampUs());
    }
    return largestQueuedTimestampUs;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private boolean isLoadableExceptionFatal(IOException e) {
    return e instanceof UnrecognizedInputFormatException;
  }

  private void notifyLoadError(final IOException error) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadError(error);
        }
      });
    }
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
    public void skipData(long positionUs) {
      ExtractorMediaPeriod.this.skipData(track, positionUs);
    }

  }

  /**
   * Loads the media stream and extracts sample data from it.
   */
  /* package */ final class ExtractingLoadable implements Loadable {

    /**
     * The number of bytes that should be loaded between each each invocation of
     * {@link Callback#onContinueLoadingRequested(SequenceableLoader)}.
     */
    private static final int CONTINUE_LOADING_CHECK_INTERVAL_BYTES = 1024 * 1024;

    private final Uri uri;
    private final DataSource dataSource;
    private final ExtractorHolder extractorHolder;
    private final ConditionVariable loadCondition;
    private final PositionHolder positionHolder;

    private volatile boolean loadCanceled;

    private boolean pendingExtractorSeek;
    private long seekTimeUs;
    private long length;

    public ExtractingLoadable(Uri uri, DataSource dataSource, ExtractorHolder extractorHolder,
        ConditionVariable loadCondition) {
      this.uri = Assertions.checkNotNull(uri);
      this.dataSource = Assertions.checkNotNull(dataSource);
      this.extractorHolder = Assertions.checkNotNull(extractorHolder);
      this.loadCondition = loadCondition;
      this.positionHolder = new PositionHolder();
      this.pendingExtractorSeek = true;
      this.length = C.LENGTH_UNSET;
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
    public boolean isLoadCanceled() {
      return loadCanceled;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      int result = Extractor.RESULT_CONTINUE;
      while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
        ExtractorInput input = null;
        try {
          long position = positionHolder.position;
          length = dataSource.open(new DataSpec(uri, position, C.LENGTH_UNSET, customCacheKey));
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
            if (input.getPosition() > position + CONTINUE_LOADING_CHECK_INTERVAL_BYTES) {
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
