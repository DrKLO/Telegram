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

import android.os.Handler;
import android.text.TextUtils;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.extractor.DefaultTrackOutput;
import org.telegram.messenger.exoplayer2.extractor.DefaultTrackOutput.UpstreamFormatChangedListener;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.source.SequenceableLoader;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.chunk.Chunk;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Loads {@link HlsMediaChunk}s obtained from a {@link HlsChunkSource}, and provides
 * {@link SampleStream}s from which the loaded media can be consumed.
 */
/* package */ final class HlsSampleStreamWrapper implements Loader.Callback<Chunk>,
    SequenceableLoader, ExtractorOutput, UpstreamFormatChangedListener {

  /**
   * A callback to be notified of events.
   */
  public interface Callback extends SequenceableLoader.Callback<HlsSampleStreamWrapper> {

    /**
     * Called when the wrapper has been prepared.
     */
    void onPrepared();

    /**
     * Called to schedule a {@link #continueLoading(long)} call when the playlist referred by the
     * given url changes.
     */
    void onPlaylistRefreshRequired(HlsMasterPlaylist.HlsUrl playlistUrl);

  }

  private static final int PRIMARY_TYPE_NONE = 0;
  private static final int PRIMARY_TYPE_TEXT = 1;
  private static final int PRIMARY_TYPE_AUDIO = 2;
  private static final int PRIMARY_TYPE_VIDEO = 3;

  private final int trackType;
  private final Callback callback;
  private final HlsChunkSource chunkSource;
  private final Allocator allocator;
  private final Format muxedAudioFormat;
  private final int minLoadableRetryCount;
  private final Loader loader;
  private final EventDispatcher eventDispatcher;
  private final HlsChunkSource.HlsChunkHolder nextChunkHolder;
  private final SparseArray<DefaultTrackOutput> sampleQueues;
  private final LinkedList<HlsMediaChunk> mediaChunks;
  private final Runnable maybeFinishPrepareRunnable;
  private final Handler handler;

  private boolean sampleQueuesBuilt;
  private boolean prepared;
  private int enabledTrackCount;
  private Format downstreamTrackFormat;
  private int upstreamChunkUid;
  private boolean released;

  // Tracks are complicated in HLS. See documentation of buildTracks for details.
  // Indexed by track (as exposed by this source).
  private TrackGroupArray trackGroups;
  private int primaryTrackGroupIndex;
  // Indexed by group.
  private boolean[] groupEnabledStates;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;

  private boolean loadingFinished;

  /**
   * @param trackType The type of the track. One of the {@link C} {@code TRACK_TYPE_*} constants.
   * @param callback A callback for the wrapper.
   * @param chunkSource A {@link HlsChunkSource} from which chunks to load are obtained.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param positionUs The position from which to start loading media.
   * @param muxedAudioFormat Optional muxed audio {@link Format} as defined by the master playlist.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   * @param eventDispatcher A dispatcher to notify of events.
   */
  public HlsSampleStreamWrapper(int trackType, Callback callback, HlsChunkSource chunkSource,
      Allocator allocator, long positionUs, Format muxedAudioFormat, int minLoadableRetryCount,
      EventDispatcher eventDispatcher) {
    this.trackType = trackType;
    this.callback = callback;
    this.chunkSource = chunkSource;
    this.allocator = allocator;
    this.muxedAudioFormat = muxedAudioFormat;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    loader = new Loader("Loader:HlsSampleStreamWrapper");
    nextChunkHolder = new HlsChunkSource.HlsChunkHolder();
    sampleQueues = new SparseArray<>();
    mediaChunks = new LinkedList<>();
    maybeFinishPrepareRunnable = new Runnable() {
      @Override
      public void run() {
        maybeFinishPrepare();
      }
    };
    handler = new Handler();
    lastSeekPositionUs = positionUs;
    pendingResetPositionUs = positionUs;
  }

  public void continuePreparing() {
    if (!prepared) {
      continueLoading(lastSeekPositionUs);
    }
  }

  /**
   * Prepares a sample stream wrapper for which the master playlist provides enough information to
   * prepare.
   */
  public void prepareSingleTrack(Format format) {
    track(0, C.TRACK_TYPE_UNKNOWN).format(format);
    sampleQueuesBuilt = true;
    maybeFinishPrepare();
  }

  public void maybeThrowPrepareError() throws IOException {
    maybeThrowError();
  }

  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  public boolean selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, boolean isFirstTrackSelection) {
    Assertions.checkState(prepared);
    // Disable old tracks.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        int group = ((HlsSampleStream) streams[i]).group;
        setTrackGroupEnabledState(group, false);
        sampleQueues.valueAt(group).disable();
        streams[i] = null;
      }
    }
    // Enable new tracks.
    TrackSelection primaryTrackSelection = null;
    boolean selectedNewTracks = false;
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] == null && selections[i] != null) {
        TrackSelection selection = selections[i];
        int group = trackGroups.indexOf(selection.getTrackGroup());
        setTrackGroupEnabledState(group, true);
        if (group == primaryTrackGroupIndex) {
          primaryTrackSelection = selection;
          chunkSource.selectTracks(selection);
        }
        streams[i] = new HlsSampleStream(this, group);
        streamResetFlags[i] = true;
        selectedNewTracks = true;
      }
    }
    if (isFirstTrackSelection) {
      // At the time of the first track selection all queues will be enabled, so we need to disable
      // any that are no longer required.
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        if (!groupEnabledStates[i]) {
          sampleQueues.valueAt(i).disable();
        }
      }
      if (primaryTrackSelection != null && !mediaChunks.isEmpty()) {
        primaryTrackSelection.updateSelectedTrack(0);
        int chunkIndex = chunkSource.getTrackGroup().indexOf(mediaChunks.getLast().trackFormat);
        if (primaryTrackSelection.getSelectedIndexInTrackGroup() != chunkIndex) {
          // The loaded preparation chunk does match the selection. We discard it.
          seekTo(lastSeekPositionUs);
        }
      }
    }
    // Cancel requests if necessary.
    if (enabledTrackCount == 0) {
      chunkSource.reset();
      downstreamTrackFormat = null;
      mediaChunks.clear();
      if (loader.isLoading()) {
        loader.cancelLoading();
      }
    }
    return selectedNewTracks;
  }

  public void seekTo(long positionUs) {
    lastSeekPositionUs = positionUs;
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    mediaChunks.clear();
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        sampleQueues.valueAt(i).reset(groupEnabledStates[i]);
      }
    }
  }

  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      HlsMediaChunk lastMediaChunk = mediaChunks.getLast();
      HlsMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
          : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        bufferedPositionUs = Math.max(bufferedPositionUs,
            sampleQueues.valueAt(i).getLargestQueuedTimestampUs());
      }
      return bufferedPositionUs;
    }
  }

  public void release() {
    int sampleQueueCount = sampleQueues.size();
    for (int i = 0; i < sampleQueueCount; i++) {
      sampleQueues.valueAt(i).disable();
    }
    loader.release();
    handler.removeCallbacksAndMessages(null);
    released = true;
  }

  public void setIsTimestampMaster(boolean isTimestampMaster) {
    chunkSource.setIsTimestampMaster(isTimestampMaster);
  }

  public void onPlaylistBlacklisted(HlsUrl url, long blacklistMs) {
    chunkSource.onPlaylistBlacklisted(url, blacklistMs);
  }

  // SampleStream implementation.

  /* package */ boolean isReady(int group) {
    return loadingFinished || (!isPendingReset() && !sampleQueues.valueAt(group).isEmpty());
  }

  /* package */ void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
  }

  /* package */ int readData(int group, FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean requireFormat) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }

    while (mediaChunks.size() > 1 && finishedReadingChunk(mediaChunks.getFirst())) {
      mediaChunks.removeFirst();
    }
    HlsMediaChunk currentChunk = mediaChunks.getFirst();
    Format trackFormat = currentChunk.trackFormat;
    if (!trackFormat.equals(downstreamTrackFormat)) {
      eventDispatcher.downstreamFormatChanged(trackType, trackFormat,
          currentChunk.trackSelectionReason, currentChunk.trackSelectionData,
          currentChunk.startTimeUs);
    }
    downstreamTrackFormat = trackFormat;

    return sampleQueues.valueAt(group).readData(formatHolder, buffer, requireFormat,
        loadingFinished, lastSeekPositionUs);
  }

  /* package */ void skipData(int group, long positionUs) {
    DefaultTrackOutput sampleQueue = sampleQueues.valueAt(group);
    if (loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
      sampleQueue.skipAll();
    } else {
      sampleQueue.skipToKeyframeBefore(positionUs, true);
    }
  }

  private boolean finishedReadingChunk(HlsMediaChunk chunk) {
    int chunkUid = chunk.uid;
    for (int i = 0; i < sampleQueues.size(); i++) {
      if (groupEnabledStates[i] && sampleQueues.valueAt(i).peekSourceId() == chunkUid) {
        return false;
      }
    }
    return true;
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
    HlsMasterPlaylist.HlsUrl playlistToLoad = nextChunkHolder.playlist;
    nextChunkHolder.clear();

    if (endOfStream) {
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      if (playlistToLoad != null) {
        callback.onPlaylistRefreshRequired(playlistToLoad);
      }
      return false;
    }

    if (isMediaChunk(loadable)) {
      pendingResetPositionUs = C.TIME_UNSET;
      HlsMediaChunk mediaChunk = (HlsMediaChunk) loadable;
      mediaChunk.init(this);
      mediaChunks.add(mediaChunk);
    }
    long elapsedRealtimeMs = loader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, trackType, loadable.trackFormat,
        loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs);
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

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    chunkSource.onChunkLoadCompleted(loadable);
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, trackType, loadable.trackFormat,
        loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
    if (!prepared) {
      continueLoading(lastSeekPositionUs);
    } else {
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(loadable.dataSpec, loadable.type, trackType, loadable.trackFormat,
        loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
    if (!released) {
      int sampleQueueCount = sampleQueues.size();
      for (int i = 0; i < sampleQueueCount; i++) {
        sampleQueues.valueAt(i).reset(groupEnabledStates[i]);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      IOException error) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean cancelable = !isMediaChunk || bytesLoaded == 0;
    boolean canceled = false;
    if (chunkSource.onChunkLoadError(loadable, cancelable, error)) {
      if (isMediaChunk) {
        HlsMediaChunk removed = mediaChunks.removeLast();
        Assertions.checkState(removed == loadable);
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
      canceled = true;
    }
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, trackType, loadable.trackFormat,
        loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs,
        loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded(), error,
        canceled);
    if (canceled) {
      if (!prepared) {
        continueLoading(lastSeekPositionUs);
      } else {
        callback.onContinueLoadingRequested(this);
      }
      return Loader.DONT_RETRY;
    } else {
      return Loader.RETRY;
    }
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Initializes the wrapper for loading a chunk.
   *
   * @param chunkUid The chunk's uid.
   * @param shouldSpliceIn Whether the samples parsed from the chunk should be spliced into any
   *     samples already queued to the wrapper.
   */
  public void init(int chunkUid, boolean shouldSpliceIn) {
    upstreamChunkUid = chunkUid;
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).sourceId(chunkUid);
    }
    if (shouldSpliceIn) {
      for (int i = 0; i < sampleQueues.size(); i++) {
        sampleQueues.valueAt(i).splice();
      }
    }
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public DefaultTrackOutput track(int id, int type) {
    if (sampleQueues.indexOfKey(id) >= 0) {
      return sampleQueues.get(id);
    }
    DefaultTrackOutput trackOutput = new DefaultTrackOutput(allocator);
    trackOutput.setUpstreamFormatChangeListener(this);
    trackOutput.sourceId(upstreamChunkUid);
    sampleQueues.put(id, trackOutput);
    return trackOutput;
  }

  @Override
  public void endTracks() {
    sampleQueuesBuilt = true;
    handler.post(maybeFinishPrepareRunnable);
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

  // Internal methods.

  private void maybeFinishPrepare() {
    if (released || prepared || !sampleQueuesBuilt) {
      return;
    }
    int sampleQueueCount = sampleQueues.size();
    for (int i = 0; i < sampleQueueCount; i++) {
      if (sampleQueues.valueAt(i).getUpstreamFormat() == null) {
        return;
      }
    }
    buildTracks();
    prepared = true;
    callback.onPrepared();
  }

  /**
   * Builds tracks that are exposed by this {@link HlsSampleStreamWrapper} instance, as well as
   * internal data-structures required for operation.
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
   * create a third set, which is the set exposed by this {@link HlsSampleStreamWrapper}:
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
   */
  private void buildTracks() {
    // Iterate through the extractor tracks to discover the "primary" track type, and the index
    // of the single track of this type.
    int primaryExtractorTrackType = PRIMARY_TYPE_NONE;
    int primaryExtractorTrackIndex = C.INDEX_UNSET;
    int extractorTrackCount = sampleQueues.size();
    for (int i = 0; i < extractorTrackCount; i++) {
      String sampleMimeType = sampleQueues.valueAt(i).getUpstreamFormat().sampleMimeType;
      int trackType;
      if (MimeTypes.isVideo(sampleMimeType)) {
        trackType = PRIMARY_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        trackType = PRIMARY_TYPE_AUDIO;
      } else if (MimeTypes.isText(sampleMimeType)) {
        trackType = PRIMARY_TYPE_TEXT;
      } else {
        trackType = PRIMARY_TYPE_NONE;
      }
      if (trackType > primaryExtractorTrackType) {
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
    groupEnabledStates = new boolean[extractorTrackCount];

    // Construct the set of exposed track groups.
    TrackGroup[] trackGroups = new TrackGroup[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      Format sampleFormat = sampleQueues.valueAt(i).getUpstreamFormat();
      if (i == primaryExtractorTrackIndex) {
        Format[] formats = new Format[chunkSourceTrackCount];
        for (int j = 0; j < chunkSourceTrackCount; j++) {
          formats[j] = deriveFormat(chunkSourceTrackGroup.getFormat(j), sampleFormat);
        }
        trackGroups[i] = new TrackGroup(formats);
        primaryTrackGroupIndex = i;
      } else {
        Format trackFormat = primaryExtractorTrackType == PRIMARY_TYPE_VIDEO
            && MimeTypes.isAudio(sampleFormat.sampleMimeType) ? muxedAudioFormat : null;
        trackGroups[i] = new TrackGroup(deriveFormat(trackFormat, sampleFormat));
      }
    }
    this.trackGroups = new TrackGroupArray(trackGroups);
  }

  /**
   * Enables or disables a specified track group.
   *
   * @param group The index of the track group.
   * @param enabledState True if the group is being enabled, or false if it's being disabled.
   */
  private void setTrackGroupEnabledState(int group, boolean enabledState) {
    Assertions.checkState(groupEnabledStates[group] != enabledState);
    groupEnabledStates[group] = enabledState;
    enabledTrackCount = enabledTrackCount + (enabledState ? 1 : -1);
  }

  /**
   * Derives a track format corresponding to a given container format, by combining it with sample
   * level information obtained from the samples.
   *
   * @param containerFormat The container format for which the track format should be derived.
   * @param sampleFormat A sample format from which to obtain sample level information.
   * @return The derived track format.
   */
  private static Format deriveFormat(Format containerFormat, Format sampleFormat) {
    if (containerFormat == null) {
      return sampleFormat;
    }
    String codecs = null;
    int sampleTrackType = MimeTypes.getTrackType(sampleFormat.sampleMimeType);
    if (sampleTrackType == C.TRACK_TYPE_AUDIO) {
      codecs = getAudioCodecs(containerFormat.codecs);
    } else if (sampleTrackType == C.TRACK_TYPE_VIDEO) {
      codecs = getVideoCodecs(containerFormat.codecs);
    }
    return sampleFormat.copyWithContainerInfo(containerFormat.id, codecs, containerFormat.bitrate,
        containerFormat.width, containerFormat.height, containerFormat.selectionFlags,
        containerFormat.language);
  }

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof HlsMediaChunk;
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private static String getAudioCodecs(String codecs) {
    return getCodecsOfType(codecs, C.TRACK_TYPE_AUDIO);
  }

  private static String getVideoCodecs(String codecs) {
    return getCodecsOfType(codecs, C.TRACK_TYPE_VIDEO);
  }

  private static String getCodecsOfType(String codecs, int trackType) {
    if (TextUtils.isEmpty(codecs)) {
      return null;
    }
    String[] codecArray = codecs.split("(\\s*,\\s*)|(\\s*$)");
    StringBuilder builder = new StringBuilder();
    for (String codec : codecArray) {
      if (trackType == MimeTypes.getTrackTypeOfCodec(codec)) {
        if (builder.length() > 0) {
          builder.append(",");
        }
        builder.append(codec);
      }
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

}
