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
package org.telegram.messenger.exoplayer.extractor;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.MediaFormatHolder;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.SampleSource;
import org.telegram.messenger.exoplayer.SampleSource.SampleSourceReader;
import org.telegram.messenger.exoplayer.TrackRenderer;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.upstream.Allocator;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.upstream.Loader;
import org.telegram.messenger.exoplayer.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SampleSource} that extracts sample data using an {@link Extractor}.
 *
 * <p>If no {@link Extractor} instances are passed to the constructor, the input stream container
 * format will be detected automatically from the following supported formats:
 *
 * <ul>
 * <li>MP4, including M4A ({@link org.telegram.messenger.exoplayer.extractor.mp4.Mp4Extractor})</li>
 * <li>fMP4 ({@link org.telegram.messenger.exoplayer.extractor.mp4.FragmentedMp4Extractor})</li>
 * <li>Matroska and WebM ({@link org.telegram.messenger.exoplayer.extractor.webm.WebmExtractor})</li>
 * <li>Ogg Vorbis/FLAC ({@link org.telegram.messenger.exoplayer.extractor.ogg.OggExtractor}</li>
 * <li>MP3 ({@link org.telegram.messenger.exoplayer.extractor.mp3.Mp3Extractor})</li>
 * <li>AAC ({@link org.telegram.messenger.exoplayer.extractor.ts.AdtsExtractor})</li>
 * <li>MPEG TS ({@link org.telegram.messenger.exoplayer.extractor.ts.TsExtractor})</li>
 * <li>MPEG PS ({@link org.telegram.messenger.exoplayer.extractor.ts.PsExtractor})</li>
 * <li>FLV ({@link org.telegram.messenger.exoplayer.extractor.flv.FlvExtractor})</li>
 * <li>WAV ({@link org.telegram.messenger.exoplayer.extractor.wav.WavExtractor})</li>
 * <li>FLAC (only available if the FLAC extension is built and included)</li>
 * </ul>
 *
 * <p>Seeking in AAC, MPEG TS and FLV streams is not supported.
 *
 * <p>To override the default extractors, pass one or more {@link Extractor} instances to the
 * constructor. When reading a new stream, the first {@link Extractor} that returns {@code true}
 * from {@link Extractor#sniff(ExtractorInput)} will be used.
 */
public final class ExtractorSampleSource implements SampleSource, SampleSourceReader,
    ExtractorOutput, Loader.Callback {

  /**
   * Interface definition for a callback to be notified of {@link ExtractorSampleSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SampleSource}.
     * @param e The cause of the failure.
     */
    void onLoadError(int sourceId, IOException e);

  }

  /**
   * Thrown if the input format could not recognized.
   */
  public static final class UnrecognizedInputFormatException extends ParserException {

    public UnrecognizedInputFormatException(Extractor[] extractors) {
      super("None of the available extractors ("
          + Util.getCommaDelimitedSimpleClassNames(extractors) + ") could read the stream.");
    }

  }

  /**
   * The default minimum number of times to retry loading prior to failing for on-demand streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND = 3;

  /**
   * The default minimum number of times to retry loading prior to failing for live streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE = 6;

  private static final int MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA = -1;
  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  /**
   * Default extractor classes in priority order. They are referred to indirectly so that it is
   * possible to remove unused extractors.
   */
  private static final List<Class<? extends Extractor>> DEFAULT_EXTRACTOR_CLASSES;
  static {
    DEFAULT_EXTRACTOR_CLASSES = new ArrayList<>();
    // Load extractors using reflection so that they can be deleted cleanly.
    // Class.forName(<class name>) appears for each extractor so that automated tools like proguard
    // can detect the use of reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.webm.WebmExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.mp4.FragmentedMp4Extractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.mp4.Mp4Extractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.mp3.Mp3Extractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.ts.AdtsExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.ts.TsExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.flv.FlvExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.ogg.OggExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.ts.PsExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.extractor.wav.WavExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
    try {
      DEFAULT_EXTRACTOR_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.ext.flac.FlacExtractor")
              .asSubclass(Extractor.class));
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    }
  }

  private final ExtractorHolder extractorHolder;
  private final Allocator allocator;
  private final int requestedBufferSize;
  private final SparseArray<InternalTrackOutput> sampleQueues;
  private final int minLoadableRetryCount;
  private final Uri uri;
  private final DataSource dataSource;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int eventSourceId;

  private volatile boolean tracksBuilt;
  private volatile SeekMap seekMap;
  private volatile DrmInitData drmInitData;

  private boolean prepared;
  private int enabledTrackCount;
  private MediaFormat[] mediaFormats;
  private long maxTrackDurationUs;
  private boolean[] pendingMediaFormat;
  private boolean[] pendingDiscontinuities;
  private boolean[] trackEnabledStates;

  private int remainingReleaseCount;
  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean havePendingNextSampleUs;
  private long pendingNextSampleUs;
  private long sampleTimeOffsetUs;

  private Loader loader;
  private ExtractingLoadable loadable;
  private IOException currentLoadableException;
  // TODO: Set this back to 0 in the correct place (some place indicative of making progress).
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;
  private boolean loadingFinished;

  private int extractedSampleCount;
  private int extractedSampleCountAtStartOfLoad;

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource A data source to read the media stream.
   * @param allocator An {@link Allocator} from which to obtain memory allocations.
   * @param requestedBufferSize The requested total buffer size for storing sample data, in bytes.
   *     The actual allocated size may exceed the value passed in if the implementation requires it.
   * @param extractors {@link Extractor}s to extract the media stream, in order of decreasing
   *     priority. If omitted, the default extractors will be used.
   */
  public ExtractorSampleSource(Uri uri, DataSource dataSource, Allocator allocator,
      int requestedBufferSize, Extractor... extractors) {
    this(uri, dataSource, allocator, requestedBufferSize, MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA,
        extractors);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource A data source to read the media stream.
   * @param allocator An {@link Allocator} from which to obtain memory allocations.
   * @param requestedBufferSize The requested total buffer size for storing sample data, in bytes.
   *     The actual allocated size may exceed the value passed in if the implementation requires it.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   * @param extractors {@link Extractor}s to extract the media stream, in order of decreasing
   *     priority. If omitted, the default extractors will be used.
   */
  public ExtractorSampleSource(Uri uri, DataSource dataSource, Allocator allocator,
      int requestedBufferSize, Handler eventHandler, EventListener eventListener,
      int eventSourceId, Extractor... extractors) {
    this(uri, dataSource, allocator, requestedBufferSize, MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA,
        eventHandler, eventListener, eventSourceId, extractors);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource A data source to read the media stream.
   * @param allocator An {@link Allocator} from which to obtain memory allocations.
   * @param requestedBufferSize The requested total buffer size for storing sample data, in bytes.
   *     The actual allocated size may exceed the value passed in if the implementation requires it.
   * @param minLoadableRetryCount The minimum number of times that the sample source will retry
   *     if a loading error occurs.
   * @param extractors {@link Extractor}s to extract the media stream, in order of decreasing
   *     priority. If omitted, the default extractors will be used.
   */
  public ExtractorSampleSource(Uri uri, DataSource dataSource, Allocator allocator,
      int requestedBufferSize, int minLoadableRetryCount, Extractor... extractors) {
    this(uri, dataSource, allocator, requestedBufferSize, minLoadableRetryCount, null, null, 0,
        extractors);
  }

  /**
   * @param uri The {@link Uri} of the media stream.
   * @param dataSource A data source to read the media stream.
   * @param allocator An {@link Allocator} from which to obtain memory allocations.
   * @param requestedBufferSize The requested total buffer size for storing sample data, in bytes.
   *     The actual allocated size may exceed the value passed in if the implementation requires it.
   * @param minLoadableRetryCount The minimum number of times that the sample source will retry
   *     if a loading error occurs.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param eventSourceId An identifier that gets passed to {@code eventListener} methods.
   * @param extractors {@link Extractor}s to extract the media stream, in order of decreasing
   *     priority. If omitted, the default extractors will be used.
   */
  public ExtractorSampleSource(Uri uri, DataSource dataSource, Allocator allocator,
      int requestedBufferSize, int minLoadableRetryCount, Handler eventHandler,
      EventListener eventListener, int eventSourceId, Extractor... extractors) {
    this.uri = uri;
    this.dataSource = dataSource;
    this.eventListener = eventListener;
    this.eventHandler = eventHandler;
    this.eventSourceId = eventSourceId;
    this.allocator = allocator;
    this.requestedBufferSize = requestedBufferSize;
    this.minLoadableRetryCount = minLoadableRetryCount;
    if (extractors == null || extractors.length == 0) {
      extractors = new Extractor[DEFAULT_EXTRACTOR_CLASSES.size()];
      for (int i = 0; i < extractors.length; i++) {
        try {
          extractors[i] = DEFAULT_EXTRACTOR_CLASSES.get(i).newInstance();
        } catch (InstantiationException e) {
          throw new IllegalStateException("Unexpected error creating default extractor", e);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Unexpected error creating default extractor", e);
        }
      }
    }
    extractorHolder = new ExtractorHolder(extractors, this);
    sampleQueues = new SparseArray<>();
    pendingResetPositionUs = NO_RESET_PENDING;
  }

  @Override
  public SampleSourceReader register() {
    remainingReleaseCount++;
    return this;
  }

  @Override
  public boolean prepare(long positionUs) {
    if (prepared) {
      return true;
    }
    if (loader == null) {
      loader = new Loader("Loader:ExtractorSampleSource");
    }

    maybeStartLoading();

    if (seekMap != null && tracksBuilt && haveFormatsForAllTracks()) {
      int trackCount = sampleQueues.size();
      trackEnabledStates = new boolean[trackCount];
      pendingDiscontinuities = new boolean[trackCount];
      pendingMediaFormat = new boolean[trackCount];
      mediaFormats = new MediaFormat[trackCount];
      maxTrackDurationUs = C.UNKNOWN_TIME_US;
      for (int i = 0; i < trackCount; i++) {
        MediaFormat format = sampleQueues.valueAt(i).getFormat();
        mediaFormats[i] = format;
        if (format.durationUs != C.UNKNOWN_TIME_US && format.durationUs > maxTrackDurationUs) {
          maxTrackDurationUs = format.durationUs;
        }
      }
      prepared = true;
      return true;
    }

    return false;
  }

  @Override
  public int getTrackCount() {
    return sampleQueues.size();
  }

  @Override
  public MediaFormat getFormat(int track) {
    Assertions.checkState(prepared);
    return mediaFormats[track];
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(!trackEnabledStates[track]);
    enabledTrackCount++;
    trackEnabledStates[track] = true;
    pendingMediaFormat[track] = true;
    pendingDiscontinuities[track] = false;
    if (enabledTrackCount == 1) {
      // Treat all enables in non-seekable media as being from t=0.
      positionUs = !seekMap.isSeekable() ? 0 : positionUs;
      downstreamPositionUs = positionUs;
      lastSeekPositionUs = positionUs;
      restartFrom(positionUs);
    }
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackEnabledStates[track]);
    enabledTrackCount--;
    trackEnabledStates[track] = false;
    if (enabledTrackCount == 0) {
      downstreamPositionUs = Long.MIN_VALUE;
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        clearState();
        allocator.trim(0);
      }
    }
  }

  @Override
  public boolean continueBuffering(int track, long playbackPositionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackEnabledStates[track]);
    downstreamPositionUs = playbackPositionUs;
    discardSamplesForDisabledTracks(downstreamPositionUs);
    if (loadingFinished) {
      return true;
    }
    maybeStartLoading();
    if (isPendingReset()) {
      return false;
    }
    return !sampleQueues.valueAt(track).isEmpty();
  }

  @Override
  public long readDiscontinuity(int track) {
    if (pendingDiscontinuities[track]) {
      pendingDiscontinuities[track] = false;
      return lastSeekPositionUs;
    }
    return NO_DISCONTINUITY;
  }

  @Override
  public int readData(int track, long playbackPositionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder) {
    downstreamPositionUs = playbackPositionUs;

    if (pendingDiscontinuities[track] || isPendingReset()) {
      return NOTHING_READ;
    }

    InternalTrackOutput sampleQueue = sampleQueues.valueAt(track);
    if (pendingMediaFormat[track]) {
      formatHolder.format = sampleQueue.getFormat();
      formatHolder.drmInitData = drmInitData;
      pendingMediaFormat[track] = false;
      return FORMAT_READ;
    }

    if (sampleQueue.getSample(sampleHolder)) {
      boolean decodeOnly = sampleHolder.timeUs < lastSeekPositionUs;
      sampleHolder.flags |= decodeOnly ? C.SAMPLE_FLAG_DECODE_ONLY : 0;
      if (havePendingNextSampleUs) {
        // Set the offset to make the timestamp of this sample equal to pendingNextSampleUs.
        sampleTimeOffsetUs = pendingNextSampleUs - sampleHolder.timeUs;
        havePendingNextSampleUs = false;
      }
      sampleHolder.timeUs += sampleTimeOffsetUs;
      return SAMPLE_READ;
    }

    if (loadingFinished) {
      return END_OF_STREAM;
    }

    return NOTHING_READ;
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (currentLoadableException == null) {
      return;
    }
    if (isCurrentLoadableExceptionFatal()) {
      throw currentLoadableException;
    }
    int minLoadableRetryCountForMedia;
    if (minLoadableRetryCount != MIN_RETRY_COUNT_DEFAULT_FOR_MEDIA) {
      minLoadableRetryCountForMedia = minLoadableRetryCount;
    } else {
      minLoadableRetryCountForMedia = seekMap != null && !seekMap.isSeekable()
          ? DEFAULT_MIN_LOADABLE_RETRY_COUNT_LIVE
          : DEFAULT_MIN_LOADABLE_RETRY_COUNT_ON_DEMAND;
    }
    if (currentLoadableExceptionCount > minLoadableRetryCountForMedia) {
      throw currentLoadableException;
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);
    // Treat all seeks into non-seekable media as being to t=0.
    positionUs = !seekMap.isSeekable() ? 0 : positionUs;

    long currentPositionUs = isPendingReset() ? pendingResetPositionUs : downstreamPositionUs;
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    if (currentPositionUs == positionUs) {
      return;
    }

    // If we're not pending a reset, see if we can seek within the sample queues.
    boolean seekInsideBuffer = !isPendingReset();
    for (int i = 0; seekInsideBuffer && i < sampleQueues.size(); i++) {
      seekInsideBuffer &= sampleQueues.valueAt(i).skipToKeyframeBefore(positionUs);
    }

    // If we failed to seek within the sample queues, we need to restart.
    if (!seekInsideBuffer) {
      restartFrom(positionUs);
    }

    // Either way, we need to send discontinuities to the downstream components.
    for (int i = 0; i < pendingDiscontinuities.length; i++) {
      pendingDiscontinuities[i] = true;
    }
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return TrackRenderer.END_OF_TRACK_US;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long largestParsedTimestampUs = Long.MIN_VALUE;
      for (int i = 0; i < sampleQueues.size(); i++) {
        largestParsedTimestampUs = Math.max(largestParsedTimestampUs,
            sampleQueues.valueAt(i).getLargestParsedTimestampUs());
      }
      return largestParsedTimestampUs == Long.MIN_VALUE ? downstreamPositionUs
          : largestParsedTimestampUs;
    }
  }

  @Override
  public void release() {
    Assertions.checkState(remainingReleaseCount > 0);
    if (--remainingReleaseCount == 0) {
      if (loader != null) {
        loader.release(new Runnable() {
          @Override
          public void run() {
            extractorHolder.release();
          }
        });
        loader = null;
      }
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Loadable loadable) {
    loadingFinished = true;
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    if (enabledTrackCount > 0) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearState();
      allocator.trim(0);
    }
  }

  @Override
  public void onLoadError(Loadable ignored, IOException e) {
    currentLoadableException = e;
    currentLoadableExceptionCount = extractedSampleCount > extractedSampleCountAtStartOfLoad ? 1
        : currentLoadableExceptionCount + 1;
    currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    notifyLoadError(e);
    maybeStartLoading();
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id) {
    InternalTrackOutput sampleQueue = sampleQueues.get(id);
    if (sampleQueue == null) {
      sampleQueue = new InternalTrackOutput(allocator);
      sampleQueues.put(id, sampleQueue);
    }
    return sampleQueue;
  }

  @Override
  public void endTracks() {
    tracksBuilt = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  @Override
  public void drmInitData(DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
  }

  // Internal stuff.

  private void restartFrom(long positionUs) {
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      clearState();
      maybeStartLoading();
    }
  }

  private void maybeStartLoading() {
    if (loadingFinished || loader.isLoading()) {
      return;
    }

    if (currentLoadableException != null) {
      if (isCurrentLoadableExceptionFatal()) {
        return;
      }
      Assertions.checkState(loadable != null);
      long elapsedMillis = SystemClock.elapsedRealtime() - currentLoadableExceptionTimestamp;
      if (elapsedMillis >= getRetryDelayMillis(currentLoadableExceptionCount)) {
        currentLoadableException = null;
        if (!prepared) {
          // We don't know whether we're playing an on-demand or a live stream. For a live stream
          // we need to load from the start, as outlined below. Since we might be playing a live
          // stream, play it safe and load from the start.
          for (int i = 0; i < sampleQueues.size(); i++) {
            sampleQueues.valueAt(i).clear();
          }
          loadable = createLoadableFromStart();
        } else if (!seekMap.isSeekable() && maxTrackDurationUs == C.UNKNOWN_TIME_US) {
          // We're playing a non-seekable stream with unknown duration. Assume it's live, and
          // therefore that the data at the uri is a continuously shifting window of the latest
          // available media. For this case there's no way to continue loading from where a previous
          // load finished, so it's necessary to load from the start whenever commencing a new load.
          for (int i = 0; i < sampleQueues.size(); i++) {
            sampleQueues.valueAt(i).clear();
          }
          loadable = createLoadableFromStart();
          // To avoid introducing a discontinuity, we shift the sample timestamps so that they will
          // continue from the current downstream position.
          pendingNextSampleUs = downstreamPositionUs;
          havePendingNextSampleUs = true;
        } else {
          // We're playing a seekable on-demand stream. Resume the current loadable, which will
          // request data starting from the point it left off.
        }
        extractedSampleCountAtStartOfLoad = extractedSampleCount;
        loader.startLoading(loadable, this);
      }
      return;
    }

    // We're not retrying, so we're either starting a playback or responding to an explicit seek.
    // In both cases sampleTimeOffsetUs should be reset to zero, and any pending adjustment to
    // sample timestamps should be discarded.
    sampleTimeOffsetUs = 0;
    havePendingNextSampleUs = false;

    if (!prepared) {
      loadable = createLoadableFromStart();
    } else {
      Assertions.checkState(isPendingReset());
      if (maxTrackDurationUs != C.UNKNOWN_TIME_US && pendingResetPositionUs >= maxTrackDurationUs) {
        loadingFinished = true;
        pendingResetPositionUs = NO_RESET_PENDING;
        return;
      }
      loadable = createLoadableFromPositionUs(pendingResetPositionUs);
      pendingResetPositionUs = NO_RESET_PENDING;
    }
    extractedSampleCountAtStartOfLoad = extractedSampleCount;
    loader.startLoading(loadable, this);
  }

  private ExtractingLoadable createLoadableFromStart() {
    return new ExtractingLoadable(uri, dataSource, extractorHolder, allocator, requestedBufferSize,
        0);
  }

  private ExtractingLoadable createLoadableFromPositionUs(long positionUs) {
    return new ExtractingLoadable(uri, dataSource, extractorHolder, allocator, requestedBufferSize,
        seekMap.getPosition(positionUs));
  }

  private boolean haveFormatsForAllTracks() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      if (!sampleQueues.valueAt(i).hasFormat()) {
        return false;
      }
    }
    return true;
  }

  private void discardSamplesForDisabledTracks(long timeUs) {
    for (int i = 0; i < trackEnabledStates.length; i++) {
      if (!trackEnabledStates[i]) {
        sampleQueues.valueAt(i).discardUntil(timeUs);
      }
    }
  }

  private void clearState() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).clear();
    }
    loadable = null;
    currentLoadableException = null;
    currentLoadableExceptionCount = 0;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

  private boolean isCurrentLoadableExceptionFatal() {
    return currentLoadableException instanceof UnrecognizedInputFormatException;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  private void notifyLoadError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadError(eventSourceId, e);
        }
      });
    }
  }

  /**
   * Extension of {@link DefaultTrackOutput} that increments a shared counter of the total number
   * of extracted samples.
   */
  private class InternalTrackOutput extends DefaultTrackOutput {

    public InternalTrackOutput(Allocator allocator) {
      super(allocator);
    }

    @Override
    public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
      super.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
      extractedSampleCount++;
    }

  }

  /**
   * Loads the media stream and extracts sample data from it.
   */
  private static class ExtractingLoadable implements Loadable {

    private final Uri uri;
    private final DataSource dataSource;
    private final ExtractorHolder extractorHolder;
    private final Allocator allocator;
    private final int requestedBufferSize;
    private final PositionHolder positionHolder;

    private volatile boolean loadCanceled;

    private boolean pendingExtractorSeek;

    public ExtractingLoadable(Uri uri, DataSource dataSource, ExtractorHolder extractorHolder,
        Allocator allocator, int requestedBufferSize, long position) {
      this.uri = Assertions.checkNotNull(uri);
      this.dataSource = Assertions.checkNotNull(dataSource);
      this.extractorHolder = Assertions.checkNotNull(extractorHolder);
      this.allocator = Assertions.checkNotNull(allocator);
      this.requestedBufferSize = requestedBufferSize;
      positionHolder = new PositionHolder();
      positionHolder.position = position;
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
          long length = dataSource.open(new DataSpec(uri, position, C.LENGTH_UNBOUNDED, null));
          if (length != C.LENGTH_UNBOUNDED) {
            length += position;
          }
          input = new DefaultExtractorInput(dataSource, position, length);
          Extractor extractor = extractorHolder.selectExtractor(input);
          if (pendingExtractorSeek) {
            extractor.seek();
            pendingExtractorSeek = false;
          }
          while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
            allocator.blockWhileTotalBytesAllocatedExceeds(requestedBufferSize);
            result = extractor.read(input, positionHolder);
            // TODO: Implement throttling to stop us from buffering data too often.
          }
        } finally {
          if (result == Extractor.RESULT_SEEK) {
            result = Extractor.RESULT_CONTINUE;
          } else if (input != null) {
            positionHolder.position = input.getPosition();
          }
          dataSource.close();
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
     * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
     * @throws IOException Thrown if the input could not be read.
     * @throws InterruptedException Thrown if the thread was interrupted.
     */
    public Extractor selectExtractor(ExtractorInput input)
        throws UnrecognizedInputFormatException, IOException, InterruptedException {
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
        throw new UnrecognizedInputFormatException(extractors);
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
