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

import android.os.Handler;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.LoadControl;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.MediaFormatHolder;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.SampleSource;
import org.telegram.messenger.exoplayer.SampleSource.SampleSourceReader;
import org.telegram.messenger.exoplayer.TrackRenderer;
import org.telegram.messenger.exoplayer.chunk.BaseChunkSampleSourceEventListener;
import org.telegram.messenger.exoplayer.chunk.Chunk;
import org.telegram.messenger.exoplayer.chunk.ChunkOperationHolder;
import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.upstream.Loader;
import org.telegram.messenger.exoplayer.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * A {@link SampleSource} for HLS streams.
 */
public final class HlsSampleSource implements SampleSource, SampleSourceReader, Loader.Callback {

  /**
   * Interface definition for a callback to be notified of {@link HlsSampleSource} events.
   */
  public interface EventListener extends BaseChunkSampleSourceEventListener {}

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private static final long NO_RESET_PENDING = Long.MIN_VALUE;

  private static final int PRIMARY_TYPE_NONE = 0;
  private static final int PRIMARY_TYPE_TEXT = 1;
  private static final int PRIMARY_TYPE_AUDIO = 2;
  private static final int PRIMARY_TYPE_VIDEO = 3;

  private final HlsChunkSource chunkSource;
  private final LinkedList<HlsExtractorWrapper> extractors;
  private final int minLoadableRetryCount;
  private final int bufferSizeContribution;
  private final ChunkOperationHolder chunkOperationHolder;

  private final int eventSourceId;
  private final LoadControl loadControl;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private int remainingReleaseCount;
  private boolean prepared;
  private boolean loadControlRegistered;
  private int trackCount;
  private int enabledTrackCount;

  private Format downstreamFormat;

  // Tracks are complicated in HLS. See documentation of buildTracks for details.
  // Indexed by track (as exposed by this source).
  private MediaFormat[] trackFormats;
  private boolean[] trackEnabledStates;
  private boolean[] pendingDiscontinuities;
  private MediaFormat[] downstreamMediaFormats;
  // Maps track index (as exposed by this source) to the corresponding chunk source track index for
  // primary tracks, or to -1 otherwise.
  private int[] chunkSourceTrackIndices;
  // Maps track index (as exposed by this source) to the corresponding extractor track index.
  private int[] extractorTrackIndices;
  // Indexed by extractor track index.
  private boolean[] extractorTrackEnabledStates;

  private long downstreamPositionUs;
  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean loadingFinished;
  private Chunk currentLoadable;
  private TsChunk currentTsLoadable;
  private TsChunk previousTsLoadable;

  private Loader loader;
  private IOException currentLoadableException;
  private int currentLoadableExceptionCount;
  private long currentLoadableExceptionTimestamp;
  private long currentLoadStartTimeMs;

  public HlsSampleSource(HlsChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution) {
    this(chunkSource, loadControl, bufferSizeContribution, null, null, 0);
  }

  public HlsSampleSource(HlsChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this(chunkSource, loadControl, bufferSizeContribution, eventHandler, eventListener,
        eventSourceId, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  public HlsSampleSource(HlsChunkSource chunkSource, LoadControl loadControl,
      int bufferSizeContribution, Handler eventHandler, EventListener eventListener,
      int eventSourceId, int minLoadableRetryCount) {
    this.chunkSource = chunkSource;
    this.loadControl = loadControl;
    this.bufferSizeContribution = bufferSizeContribution;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    this.pendingResetPositionUs = NO_RESET_PENDING;
    extractors = new LinkedList<>();
    chunkOperationHolder = new ChunkOperationHolder();
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
    } else if (!chunkSource.prepare()) {
      return false;
    }
    if (!extractors.isEmpty()) {
      while (true) {
        // We're not prepared, but we might have loaded what we need.
        HlsExtractorWrapper extractor = extractors.getFirst();
        if (extractor.isPrepared()) {
          buildTracks(extractor);
          prepared = true;
          maybeStartLoading(); // Update the load control.
          return true;
        } else if (extractors.size() > 1) {
          extractors.removeFirst().clear();
        } else {
          break;
        }
      }
    }
    // We're not prepared and we haven't loaded what we need.
    if (loader == null) {
      loader = new Loader("Loader:HLS");
      loadControl.register(this, bufferSizeContribution);
      loadControlRegistered = true;
    }
    if (!loader.isLoading()) {
      // We're going to have to start loading a chunk to get what we need for preparation. We should
      // attempt to load the chunk at positionUs, so that we'll already be loading the correct chunk
      // in the common case where the renderer is subsequently enabled at this position.
      pendingResetPositionUs = positionUs;
      downstreamPositionUs = positionUs;
    }
    maybeStartLoading();
    return false;
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return trackCount;
  }

  @Override
  public MediaFormat getFormat(int track) {
    Assertions.checkState(prepared);
    return trackFormats[track];
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(prepared);
    setTrackEnabledState(track, true);
    downstreamMediaFormats[track] = null;
    pendingDiscontinuities[track] = false;
    downstreamFormat = null;
    boolean wasLoadControlRegistered = loadControlRegistered;
    if (!loadControlRegistered) {
      loadControl.register(this, bufferSizeContribution);
      loadControlRegistered = true;
    }
    // Treat enabling of a live stream as occurring at t=0 in both of the blocks below.
    positionUs = chunkSource.isLive() ? 0 : positionUs;
    int chunkSourceTrack = chunkSourceTrackIndices[track];
    if (chunkSourceTrack != -1 && chunkSourceTrack != chunkSource.getSelectedTrackIndex()) {
      // This is a primary track whose corresponding chunk source track is different to the one
      // currently selected. We need to change the selection and restart. Since other exposed tracks
      // may be enabled too, we need to implement the restart as a seek so that all downstream
      // renderers receive a discontinuity event.
      chunkSource.selectTrack(chunkSourceTrack);
      seekToInternal(positionUs);
      return;
    }
    if (enabledTrackCount == 1) {
      lastSeekPositionUs = positionUs;
      if (wasLoadControlRegistered && downstreamPositionUs == positionUs) {
        // TODO: Address [Internal: b/21743989] to remove the need for this kind of hack.
        // This is the first track to be enabled after preparation and the position is the same as
        // was passed to prepare. In this case we can avoid restarting, which would reload the same
        // chunks as were loaded during preparation.
        maybeStartLoading();
      } else {
        downstreamPositionUs = positionUs;
        restartFrom(positionUs);
      }
    }
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(prepared);
    setTrackEnabledState(track, false);
    if (enabledTrackCount == 0) {
      chunkSource.reset();
      downstreamPositionUs = Long.MIN_VALUE;
      if (loadControlRegistered) {
        loadControl.unregister(this);
        loadControlRegistered = false;
      }
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        clearState();
        loadControl.trimAllocator();
      }
    }
  }

  @Override
  public boolean continueBuffering(int track, long playbackPositionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackEnabledStates[track]);
    downstreamPositionUs = playbackPositionUs;
    if (!extractors.isEmpty()) {
      discardSamplesForDisabledTracks(getCurrentExtractor(), downstreamPositionUs);
    }
    maybeStartLoading();
    if (loadingFinished) {
      return true;
    }
    if (isPendingReset() || extractors.isEmpty()) {
      return false;
    }
    for (int extractorIndex = 0; extractorIndex < extractors.size(); extractorIndex++) {
      HlsExtractorWrapper extractor = extractors.get(extractorIndex);
      if (!extractor.isPrepared()) {
        break;
      }
      int extractorTrack = extractorTrackIndices[track];
      if (extractor.hasSamples(extractorTrack)) {
        return true;
      }
    }
    return false;
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
    Assertions.checkState(prepared);
    downstreamPositionUs = playbackPositionUs;

    if (pendingDiscontinuities[track] || isPendingReset()) {
      return NOTHING_READ;
    }

    HlsExtractorWrapper extractor = getCurrentExtractor();
    if (!extractor.isPrepared()) {
      return NOTHING_READ;
    }

    Format format = extractor.format;
    if (!format.equals(downstreamFormat)) {
      notifyDownstreamFormatChanged(format, extractor.trigger, extractor.startTimeUs);
    }
    downstreamFormat = format;

    if (extractors.size() > 1) {
      // If there's more than one extractor, attempt to configure a seamless splice from the
      // current one to the next one.
      extractor.configureSpliceTo(extractors.get(1));
    }

    int extractorTrack = extractorTrackIndices[track];
    int extractorIndex = 0;
    while (extractors.size() > extractorIndex + 1 && !extractor.hasSamples(extractorTrack)) {
      // We're finished reading from the extractor for this particular track, so advance to the
      // next one for the current read.
      extractor = extractors.get(++extractorIndex);
      if (!extractor.isPrepared()) {
        return NOTHING_READ;
      }
    }

    MediaFormat mediaFormat = extractor.getMediaFormat(extractorTrack);
    if (mediaFormat != null) {
      if (!mediaFormat.equals(downstreamMediaFormats[track])) {
        formatHolder.format = mediaFormat;
        downstreamMediaFormats[track] = mediaFormat;
        return FORMAT_READ;
      }
      // If mediaFormat and downstreamMediaFormat[track] are equal but different objects then the
      // equality check above will have been expensive, comparing the fields in each format. We
      // update downstreamMediaFormat here so that referential equality can be cheaply established
      // during subsequent calls.
      downstreamMediaFormats[track] = mediaFormat;
    }

    if (extractor.getSample(extractorTrack, sampleHolder)) {
      boolean decodeOnly = sampleHolder.timeUs < lastSeekPositionUs;
      sampleHolder.flags |= decodeOnly ? C.SAMPLE_FLAG_DECODE_ONLY : 0;
      return SAMPLE_READ;
    }

    if (loadingFinished) {
      return END_OF_STREAM;
    }

    return NOTHING_READ;
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (currentLoadableException != null && currentLoadableExceptionCount > minLoadableRetryCount) {
      throw currentLoadableException;
    } else if (currentLoadable == null) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);
    // Treat all seeks into live streams as being to t=0.
    positionUs = chunkSource.isLive() ? 0 : positionUs;

    // Ignore seeks to the current position.
    long currentPositionUs = isPendingReset() ? pendingResetPositionUs : downstreamPositionUs;
    downstreamPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
    if (currentPositionUs == positionUs) {
      return;
    }

    seekToInternal(positionUs);
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(prepared);
    Assertions.checkState(enabledTrackCount > 0);
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else if (loadingFinished) {
      return TrackRenderer.END_OF_TRACK_US;
    } else {
      long largestParsedTimestampUs = extractors.getLast().getLargestParsedTimestampUs();
      if (extractors.size() > 1) {
        // When adapting from one format to the next, the penultimate extractor may have the largest
        // parsed timestamp (e.g. if the last extractor hasn't parsed any timestamps yet).
        largestParsedTimestampUs = Math.max(largestParsedTimestampUs,
            extractors.get(extractors.size() - 2).getLargestParsedTimestampUs());
      }
      return largestParsedTimestampUs == Long.MIN_VALUE ? downstreamPositionUs
          : largestParsedTimestampUs;
    }
  }

  @Override
  public void release() {
    Assertions.checkState(remainingReleaseCount > 0);
    if (--remainingReleaseCount == 0 && loader != null) {
      if (loadControlRegistered) {
        loadControl.unregister(this);
        loadControlRegistered = false;
      }
      loader.release();
      loader = null;
    }
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Loadable loadable) {
    Assertions.checkState(loadable == currentLoadable);
    long now = SystemClock.elapsedRealtime();
    long loadDurationMs = now - currentLoadStartTimeMs;
    chunkSource.onChunkLoadCompleted(currentLoadable);
    if (isTsChunk(currentLoadable)) {
      Assertions.checkState(currentLoadable == currentTsLoadable);
      previousTsLoadable = currentTsLoadable;
      notifyLoadCompleted(currentLoadable.bytesLoaded(), currentTsLoadable.type,
          currentTsLoadable.trigger, currentTsLoadable.format, currentTsLoadable.startTimeUs,
          currentTsLoadable.endTimeUs, now, loadDurationMs);
    } else {
      notifyLoadCompleted(currentLoadable.bytesLoaded(), currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1, now, loadDurationMs);
    }
    clearCurrentLoadable();
    maybeStartLoading();
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    notifyLoadCanceled(currentLoadable.bytesLoaded());
    if (enabledTrackCount > 0) {
      restartFrom(pendingResetPositionUs);
    } else {
      clearState();
      loadControl.trimAllocator();
    }
  }

  @Override
  public void onLoadError(Loadable loadable, IOException e) {
    if (chunkSource.onChunkLoadError(currentLoadable, e)) {
      // Error handled by source.
      if (previousTsLoadable == null && !isPendingReset()) {
        pendingResetPositionUs = lastSeekPositionUs;
      }
      clearCurrentLoadable();
    } else {
      currentLoadableException = e;
      currentLoadableExceptionCount++;
      currentLoadableExceptionTimestamp = SystemClock.elapsedRealtime();
    }
    notifyLoadError(e);
    maybeStartLoading();
  }

  // Internal stuff.

  /**
   * Builds tracks that are exposed by this {@link HlsSampleSource} instance, as well as internal
   * data-structures required for operation.
   * <p>
   * Tracks in HLS are complicated. A HLS master playlist contains a number of "variants". Each
   * variant stream typically contains muxed video, audio and (possibly) additional audio, metadata
   * and caption tracks. We wish to allow the user to select between an adaptive track that spans
   * all variants, as well as each individual variant. If multiple audio tracks are present within
   * each variant then we wish to allow the user to select between those also.
   * <p>
   * To do this, tracks are constructed as follows. The {@link HlsChunkSource} exposes (N+1) tracks,
   * where N is the number of variants defined in the HLS master playlist. These consist of one
   * adaptive track defined to span all variants and a track for each individual variant. The
   * adaptive track is initially selected. The extractor is then prepared to discover the tracks
   * inside of each variant stream. The two sets of tracks are then combined by this method to
   * create a third set, which is the set exposed by this {@link HlsSampleSource}:
   * <ul>
   * <li>The extractor tracks are inspected to infer a "primary" track type. If a video track is
   * present then it is always the primary type. If not, audio is the primary type if present.
   * Else text is the primary type if present. Else there is no primary type.</li>
   * <li>If there is exactly one extractor track of the primary type, it's expanded into (N+1)
   * exposed tracks, all of which correspond to the primary extractor track and each of which
   * corresponds to a different chunk source track. Selecting one of these tracks has the effect
   * of switching the selected track on the chunk source.</li>
   * <li>All other extractor tracks are exposed directly. Selecting one of these tracks has the
   * effect of selecting an extractor track, leaving the selected track on the chunk source
   * unchanged.</li>
   * </ul>
   *
   * @param extractor The prepared extractor.
   */
  private void buildTracks(HlsExtractorWrapper extractor) {
    // Iterate through the extractor tracks to discover the "primary" track type, and the index
    // of the single track of this type.
    int primaryExtractorTrackType = PRIMARY_TYPE_NONE;
    int primaryExtractorTrackIndex = -1;
    int extractorTrackCount = extractor.getTrackCount();
    for (int i = 0; i < extractorTrackCount; i++) {
      String mimeType = extractor.getMediaFormat(i).mimeType;
      int trackType;
      if (MimeTypes.isVideo(mimeType)) {
        trackType = PRIMARY_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(mimeType)) {
        trackType = PRIMARY_TYPE_AUDIO;
      } else if (MimeTypes.isText(mimeType)) {
        trackType = PRIMARY_TYPE_TEXT;
      } else {
        trackType = PRIMARY_TYPE_NONE;
      }
      if (trackType > primaryExtractorTrackType) {
        primaryExtractorTrackType = trackType;
        primaryExtractorTrackIndex = i;
      } else if (trackType == primaryExtractorTrackType && primaryExtractorTrackIndex != -1) {
        // We have multiple tracks of the primary type. We only want an index if there only
        // exists a single track of the primary type, so set the index back to -1.
        primaryExtractorTrackIndex = -1;
      }
    }

    // Calculate the number of tracks that will be exposed.
    int chunkSourceTrackCount = chunkSource.getTrackCount();
    boolean expandPrimaryExtractorTrack = primaryExtractorTrackIndex != -1;
    trackCount = extractorTrackCount;
    if (expandPrimaryExtractorTrack) {
      trackCount += chunkSourceTrackCount - 1;
    }

    // Instantiate the necessary internal data-structures.
    trackFormats = new MediaFormat[trackCount];
    trackEnabledStates = new boolean[trackCount];
    pendingDiscontinuities = new boolean[trackCount];
    downstreamMediaFormats = new MediaFormat[trackCount];
    chunkSourceTrackIndices = new int[trackCount];
    extractorTrackIndices = new int[trackCount];
    extractorTrackEnabledStates = new boolean[extractorTrackCount];

    // Construct the set of exposed tracks.
    long durationUs = chunkSource.getDurationUs();
    int trackIndex = 0;
    for (int i = 0; i < extractorTrackCount; i++) {
      MediaFormat format = extractor.getMediaFormat(i).copyWithDurationUs(durationUs);
      String language = null;
      if (MimeTypes.isAudio(format.mimeType)) {
        language = chunkSource.getMuxedAudioLanguage();
      } else if (MimeTypes.APPLICATION_EIA608.equals(format.mimeType)) {
        language = chunkSource.getMuxedCaptionLanguage();
      }
      if (i == primaryExtractorTrackIndex) {
        for (int j = 0; j < chunkSourceTrackCount; j++) {
          extractorTrackIndices[trackIndex] = i;
          chunkSourceTrackIndices[trackIndex] = j;
          Variant fixedTrackVariant = chunkSource.getFixedTrackVariant(j);
          trackFormats[trackIndex++] = fixedTrackVariant == null ? format.copyAsAdaptive(null)
              : copyWithFixedTrackInfo(format, fixedTrackVariant.format, language);
        }
      } else {
        extractorTrackIndices[trackIndex] = i;
        chunkSourceTrackIndices[trackIndex] = -1;
        trackFormats[trackIndex++] = format.copyWithLanguage(language);
      }
    }
  }

  /**
   * Enables or disables the track at a given index.
   *
   * @param track The index of the track.
   * @param enabledState True if the track is being enabled, or false if it's being disabled.
   */
  private void setTrackEnabledState(int track, boolean enabledState) {
    Assertions.checkState(trackEnabledStates[track] != enabledState);
    int extractorTrack = extractorTrackIndices[track];
    Assertions.checkState(extractorTrackEnabledStates[extractorTrack] != enabledState);
    trackEnabledStates[track] = enabledState;
    extractorTrackEnabledStates[extractorTrack] = enabledState;
    enabledTrackCount = enabledTrackCount + (enabledState ? 1 : -1);
  }

  /**
   * Copies a provided {@link MediaFormat}, incorporating information from the {@link Format} of
   * a fixed (i.e. non-adaptive) track, as well as a language.
   *
   * @param format The {@link MediaFormat} to copy.
   * @param fixedTrackFormat The {@link Format} to incorporate into the copy.
   * @param languageOverride The language to incorporate into the copy.
   * @return The copied {@link MediaFormat}.
   */
  private static MediaFormat copyWithFixedTrackInfo(MediaFormat format, Format fixedTrackFormat,
      String languageOverride) {
    int width = fixedTrackFormat.width == -1 ? MediaFormat.NO_VALUE : fixedTrackFormat.width;
    int height = fixedTrackFormat.height == -1 ? MediaFormat.NO_VALUE : fixedTrackFormat.height;
    String language = languageOverride == null ? fixedTrackFormat.language : languageOverride;
    return format.copyWithFixedTrackInfo(fixedTrackFormat.id, fixedTrackFormat.bitrate, width,
        height, language);
  }

  /**
   * Performs a seek. The operation is performed even if the seek is to the current position.
   *
   * @param positionUs The position to seek to.
   */
  private void seekToInternal(long positionUs) {
    lastSeekPositionUs = positionUs;
    downstreamPositionUs = positionUs;
    Arrays.fill(pendingDiscontinuities, true);
    chunkSource.seek();
    restartFrom(positionUs);
  }

  /**
   * Gets the current extractor from which samples should be read.
   * <p>
   * Calling this method discards extractors without any samples from the front of the queue. The
   * last extractor is retained even if it doesn't have any samples.
   * <p>
   * This method must not be called unless {@link #extractors} is non-empty.
   *
   * @return The current extractor from which samples should be read. Guaranteed to be non-null.
   */
  private HlsExtractorWrapper getCurrentExtractor() {
    HlsExtractorWrapper extractor = extractors.getFirst();
    while (extractors.size() > 1 && !haveSamplesForEnabledTracks(extractor)) {
      // We're finished reading from the extractor for all tracks, and so can discard it.
      extractors.removeFirst().clear();
      extractor = extractors.getFirst();
    }
    return extractor;
  }

  private void discardSamplesForDisabledTracks(HlsExtractorWrapper extractor, long timeUs) {
    if (!extractor.isPrepared()) {
      return;
    }
    for (int i = 0; i < extractorTrackEnabledStates.length; i++) {
      if (!extractorTrackEnabledStates[i]) {
        extractor.discardUntil(i, timeUs);
      }
    }
  }

  private boolean haveSamplesForEnabledTracks(HlsExtractorWrapper extractor) {
    if (!extractor.isPrepared()) {
      return false;
    }
    for (int i = 0; i < extractorTrackEnabledStates.length; i++) {
      if (extractorTrackEnabledStates[i] && extractor.hasSamples(i)) {
        return true;
      }
    }
    return false;
  }

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

  private void clearState() {
    for (int i = 0; i < extractors.size(); i++) {
      extractors.get(i).clear();
    }
    extractors.clear();
    clearCurrentLoadable();
    previousTsLoadable = null;
  }

  private void clearCurrentLoadable() {
    currentTsLoadable = null;
    currentLoadable = null;
    currentLoadableException = null;
    currentLoadableExceptionCount = 0;
  }

  private void maybeStartLoading() {
    long now = SystemClock.elapsedRealtime();
    long nextLoadPositionUs = getNextLoadPositionUs();
    boolean isBackedOff = currentLoadableException != null;
    boolean loadingOrBackedOff = loader.isLoading() || isBackedOff;

    // Update the control with our current state, and determine whether we're the next loader.
    boolean nextLoader = loadControl.update(this, downstreamPositionUs, nextLoadPositionUs,
        loadingOrBackedOff);

    if (isBackedOff) {
      long elapsedMillis = now - currentLoadableExceptionTimestamp;
      if (elapsedMillis >= getRetryDelayMillis(currentLoadableExceptionCount)) {
        currentLoadableException = null;
        loader.startLoading(currentLoadable, this);
      }
      return;
    }

    if (loader.isLoading() || !nextLoader || (prepared && enabledTrackCount == 0)) {
      return;
    }

    chunkSource.getChunkOperation(previousTsLoadable,
        pendingResetPositionUs != NO_RESET_PENDING ? pendingResetPositionUs : downstreamPositionUs,
        chunkOperationHolder);
    boolean endOfStream = chunkOperationHolder.endOfStream;
    Chunk nextLoadable = chunkOperationHolder.chunk;
    chunkOperationHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
      loadControl.update(this, downstreamPositionUs, -1, false);
      return;
    }
    if (nextLoadable == null) {
      return;
    }

    currentLoadStartTimeMs = now;
    currentLoadable = nextLoadable;
    if (isTsChunk(currentLoadable)) {
      TsChunk tsChunk = (TsChunk) currentLoadable;
      if (isPendingReset()) {
        pendingResetPositionUs = NO_RESET_PENDING;
      }
      HlsExtractorWrapper extractorWrapper = tsChunk.extractorWrapper;
      if (extractors.isEmpty() || extractors.getLast() != extractorWrapper) {
        extractorWrapper.init(loadControl.getAllocator());
        extractors.addLast(extractorWrapper);
      }
      notifyLoadStarted(tsChunk.dataSpec.length, tsChunk.type, tsChunk.trigger, tsChunk.format,
          tsChunk.startTimeUs, tsChunk.endTimeUs);
      currentTsLoadable = tsChunk;
    } else {
      notifyLoadStarted(currentLoadable.dataSpec.length, currentLoadable.type,
          currentLoadable.trigger, currentLoadable.format, -1, -1);
    }
    loader.startLoading(currentLoadable, this);
  }

  /**
   * Gets the next load time, assuming that the next load starts where the previous chunk ended (or
   * from the pending reset time, if there is one).
   */
  private long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished || (prepared && enabledTrackCount == 0) ? -1
          : currentTsLoadable != null ? currentTsLoadable.endTimeUs : previousTsLoadable.endTimeUs;
    }
  }

  private boolean isTsChunk(Chunk chunk) {
    return chunk instanceof TsChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != NO_RESET_PENDING;
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  /* package */ long usToMs(long timeUs) {
    return timeUs / 1000;
  }

  private void notifyLoadStarted(final long length, final int type, final int trigger,
      final Format format, final long mediaStartTimeUs, final long mediaEndTimeUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadStarted(eventSourceId, length, type, trigger, format,
              usToMs(mediaStartTimeUs), usToMs(mediaEndTimeUs));
        }
      });
    }
  }

  private void notifyLoadCompleted(final long bytesLoaded, final int type, final int trigger,
      final Format format, final long mediaStartTimeUs, final long mediaEndTimeUs,
      final long elapsedRealtimeMs, final long loadDurationMs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCompleted(eventSourceId, bytesLoaded, type, trigger, format,
              usToMs(mediaStartTimeUs), usToMs(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs);
        }
      });
    }
  }

  private void notifyLoadCanceled(final long bytesLoaded) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadCanceled(eventSourceId, bytesLoaded);
        }
      });
    }
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

  private void notifyDownstreamFormatChanged(final Format format, final int trigger,
      final long positionUs) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onDownstreamFormatChanged(eventSourceId, format, trigger,
              usToMs(positionUs));
        }
      });
    }
  }

}
