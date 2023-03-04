/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleQueue.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.source.SampleStream.ReadFlags;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.rtsp.RtspClient.PlaybackEventListener;
import com.google.android.exoplayer2.source.rtsp.RtspClient.SessionInfoListener;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource.RtspPlaybackException;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import javax.net.SocketFactory;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that loads an RTSP stream. */
/* package */ final class RtspMediaPeriod implements MediaPeriod {

  /** Listener for information about the period. */
  interface Listener {

    /** Called when the {@link RtspSessionTiming} is available. */
    void onSourceInfoRefreshed(RtspSessionTiming timing);

    /** Called when the RTSP server does not support seeking. */
    default void onSeekingUnsupported() {}
  }

  /** The maximum times to retry if the underlying data channel failed to bind. */
  private static final int PORT_BINDING_MAX_RETRY_COUNT = 3;

  private final Allocator allocator;
  private final Handler handler;
  private final InternalListener internalListener;
  private final RtspClient rtspClient;
  private final List<RtspLoaderWrapper> rtspLoaderWrappers;
  private final List<RtpLoadInfo> selectedLoadInfos;
  private final Listener listener;
  private final RtpDataChannel.Factory rtpDataChannelFactory;

  private @MonotonicNonNull Callback callback;
  private @MonotonicNonNull ImmutableList<TrackGroup> trackGroups;
  @Nullable private IOException preparationError;
  @Nullable private RtspPlaybackException playbackException;

  private long requestedSeekPositionUs;
  private long pendingSeekPositionUs;
  private long pendingSeekPositionUsForTcpRetry;
  private boolean loadingFinished;
  private boolean notifyDiscontinuity;
  private boolean released;
  private boolean prepared;
  private boolean trackSelected;
  private int portBindingRetryCount;
  private boolean isUsingRtpTcp;

  /**
   * Creates an RTSP media period.
   *
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param rtpDataChannelFactory A {@link RtpDataChannel.Factory} for {@link RtpDataChannel}.
   * @param uri The RTSP playback {@link Uri}.
   * @param listener A {@link Listener} to receive session information updates.
   * @param userAgent The user agent.
   * @param socketFactory A socket factory for {@link RtspClient}'s connection.
   * @param debugLoggingEnabled Whether to log RTSP messages.
   */
  public RtspMediaPeriod(
      Allocator allocator,
      RtpDataChannel.Factory rtpDataChannelFactory,
      Uri uri,
      Listener listener,
      String userAgent,
      SocketFactory socketFactory,
      boolean debugLoggingEnabled) {
    this.allocator = allocator;
    this.rtpDataChannelFactory = rtpDataChannelFactory;
    this.listener = listener;

    handler = Util.createHandlerForCurrentLooper();
    internalListener = new InternalListener();
    rtspClient =
        new RtspClient(
            /* sessionInfoListener= */ internalListener,
            /* playbackEventListener= */ internalListener,
            /* userAgent= */ userAgent,
            /* uri= */ uri,
            socketFactory,
            debugLoggingEnabled);
    rtspLoaderWrappers = new ArrayList<>();
    selectedLoadInfos = new ArrayList<>();

    pendingSeekPositionUs = C.TIME_UNSET;
    requestedSeekPositionUs = C.TIME_UNSET;
    pendingSeekPositionUsForTcpRetry = C.TIME_UNSET;
  }

  /** Releases the {@link RtspMediaPeriod}. */
  public void release() {
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      rtspLoaderWrappers.get(i).release();
    }
    Util.closeQuietly(rtspClient);
    released = true;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;

    try {
      rtspClient.start();
    } catch (IOException e) {
      preparationError = e;
      Util.closeQuietly(rtspClient);
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    if (preparationError != null) {
      throw preparationError;
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    checkState(prepared);
    return new TrackGroupArray(checkNotNull(trackGroups).toArray(new TrackGroup[0]));
  }

  @Override
  public ImmutableList<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    return ImmutableList.of();
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {

    // Deselect old tracks.
    // Input array streams contains the streams selected in the previous track selection.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
    }

    // Select new tracks.
    selectedLoadInfos.clear();
    for (int i = 0; i < selections.length; i++) {
      TrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }

      TrackGroup trackGroup = selection.getTrackGroup();
      int trackGroupIndex = checkNotNull(trackGroups).indexOf(trackGroup);
      selectedLoadInfos.add(checkNotNull(rtspLoaderWrappers.get(trackGroupIndex)).loadInfo);

      // Find the sampleStreamWrapper that contains this track group.
      if (trackGroups.contains(trackGroup)) {
        if (streams[i] == null) {
          streams[i] = new SampleStreamImpl(trackGroupIndex);
          // Update flag for newly created SampleStream.
          streamResetFlags[i] = true;
        }
      }
    }

    // Cancel non-selected loadables.
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loadControl = rtspLoaderWrappers.get(i);
      if (!selectedLoadInfos.contains(loadControl.loadInfo)) {
        loadControl.cancelLoad();
      }
    }

    trackSelected = true;
    if (positionUs != 0) {
      // Track selection is performed only once in RTSP streams.
      requestedSeekPositionUs = positionUs;
      pendingSeekPositionUs = positionUs;
      pendingSeekPositionUsForTcpRetry = positionUs;
    }
    maybeSetupTracks();
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (isSeekPending()) {
      return;
    }

    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);
      if (!loaderWrapper.canceled) {
        loaderWrapper.sampleQueue.discardTo(positionUs, toKeyframe, /* stopAtReadPosition= */ true);
      }
    }
  }

  @Override
  public long readDiscontinuity() {
    // Discontinuity only happens in RTSP when seeking an unexpectedly un-seekable RTSP server (a
    // server that doesn't include the required RTP-Info header in its PLAY responses). This only
    // applies to seeks made before receiving the first RTSP PLAY response. The playback can only
    // start from time zero in this case.
    if (notifyDiscontinuity) {
      notifyDiscontinuity = false;
      return 0;
    }
    return C.TIME_UNSET;
  }

  @Override
  public long seekToUs(long positionUs) {
    // Handles all RTSP seeking cases:
    // 1. Seek before the first RTP/UDP packet is received. The seek position is cached to be used
    //    after retrying playback with RTP/TCP.
    // 2a. Normal RTSP seek: if no additional seek is requested after the first seek. Request RTSP
    //   PAUSE and then PLAY at the seek position.
    // 2b. If additional seek is requested after the first seek, records the new seek position,
    //   2b.1. If RTSP PLAY (for the first seek) is already sent, the new seek position is used to
    //     initiate another seek upon receiving PLAY response by invoking this method again.
    //   2b.2. If RTSP PLAY (for the first seek) has not been sent, the new seek position will be
    //     used in the following PLAY request.

    // TODO(internal: b/213153670) Handle dropped seek position.
    if (getBufferedPositionUs() == 0 && !isUsingRtpTcp) {
      // Stores the seek position for later, if no RTP packet is received when using UDP.
      pendingSeekPositionUsForTcpRetry = positionUs;
      return positionUs;
    }

    discardBuffer(positionUs, /* toKeyframe= */ false);
    requestedSeekPositionUs = positionUs;

    if (isSeekPending()) {
      switch (rtspClient.getState()) {
        case RtspClient.RTSP_STATE_READY:
          // PLAY request is sent, yet to receive the response. requestedSeekPositionUs stores the
          // new position to do another seek upon receiving the PLAY response.
          return positionUs;
        case RtspClient.RTSP_STATE_PLAYING:
          // Pending PAUSE response, updates client with the newest seek position for the following
          // PLAY request.
          pendingSeekPositionUs = positionUs;
          rtspClient.seekToUs(pendingSeekPositionUs);
          return positionUs;
        case RtspClient.RTSP_STATE_UNINITIALIZED:
        case RtspClient.RTSP_STATE_INIT:
        default:
          // Never happens.
          throw new IllegalStateException();
      }
    }

    if (seekInsideBufferUs(positionUs)) {
      return positionUs;
    }

    pendingSeekPositionUs = positionUs;
    rtspClient.seekToUs(positionUs);
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      rtspLoaderWrappers.get(i).seekTo(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished || rtspLoaderWrappers.isEmpty()) {
      return C.TIME_END_OF_SOURCE;
    }

    if (requestedSeekPositionUs != C.TIME_UNSET) {
      return requestedSeekPositionUs;
    }

    boolean allLoaderWrappersAreCanceled = true;
    long bufferedPositionUs = Long.MAX_VALUE;
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);
      if (!loaderWrapper.canceled) {
        bufferedPositionUs = min(bufferedPositionUs, loaderWrapper.getBufferedPositionUs());
        allLoaderWrappersAreCanceled = false;
      }
    }

    return allLoaderWrappersAreCanceled || bufferedPositionUs == Long.MIN_VALUE
        ? 0
        : bufferedPositionUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    return getBufferedPositionUs();
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return isLoading();
  }

  @Override
  public boolean isLoading() {
    return !loadingFinished;
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  // SampleStream methods.

  /* package */ boolean isReady(int trackGroupIndex) {
    return !suppressRead() && rtspLoaderWrappers.get(trackGroupIndex).isSampleQueueReady();
  }

  @ReadDataResult
  /* package */ int readData(
      int sampleQueueIndex,
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      @ReadFlags int readFlags) {
    if (suppressRead()) {
      return C.RESULT_NOTHING_READ;
    }
    return rtspLoaderWrappers.get(sampleQueueIndex).read(formatHolder, buffer, readFlags);
  }

  /* package */ int skipData(int sampleQueueIndex, long positionUs) {
    if (suppressRead()) {
      return C.RESULT_NOTHING_READ;
    }
    return rtspLoaderWrappers.get(sampleQueueIndex).skipData(positionUs);
  }

  private boolean suppressRead() {
    return notifyDiscontinuity;
  }

  // Internal methods.

  @Nullable
  private RtpDataLoadable getLoadableByTrackUri(Uri trackUri) {
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      if (!rtspLoaderWrappers.get(i).canceled) {
        RtpLoadInfo loadInfo = rtspLoaderWrappers.get(i).loadInfo;
        if (loadInfo.getTrackUri().equals(trackUri)) {
          return loadInfo.loadable;
        }
      }
    }
    return null;
  }

  private boolean isSeekPending() {
    return pendingSeekPositionUs != C.TIME_UNSET;
  }

  private void maybeFinishPrepare() {
    if (released || prepared) {
      return;
    }

    // Make sure all sample queues have got format assigned.
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      if (rtspLoaderWrappers.get(i).sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }

    prepared = true;
    trackGroups = buildTrackGroups(ImmutableList.copyOf(rtspLoaderWrappers));
    checkNotNull(callback).onPrepared(/* mediaPeriod= */ this);
  }

  /**
   * Attempts to seek to the specified position within the sample queues.
   *
   * @param positionUs The seek position in microseconds.
   * @return Whether the in-buffer seek was successful for all loading RTSP tracks.
   */
  private boolean seekInsideBufferUs(long positionUs) {
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      SampleQueue sampleQueue = rtspLoaderWrappers.get(i).sampleQueue;
      if (!sampleQueue.seekTo(positionUs, /* allowTimeBeyondBuffer= */ false)) {
        return false;
      }
    }
    return true;
  }

  private void maybeSetupTracks() {
    boolean transportReady = true;
    for (int i = 0; i < selectedLoadInfos.size(); i++) {
      transportReady &= selectedLoadInfos.get(i).isTransportReady();
    }

    if (transportReady && trackSelected) {
      rtspClient.setupSelectedTracks(selectedLoadInfos);
    }
  }

  private void updateLoadingFinished() {
    loadingFinished = true;
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      loadingFinished &= rtspLoaderWrappers.get(i).canceled;
    }
  }

  private static ImmutableList<TrackGroup> buildTrackGroups(
      ImmutableList<RtspLoaderWrapper> rtspLoaderWrappers) {
    ImmutableList.Builder<TrackGroup> listBuilder = new ImmutableList.Builder<>();
    SampleQueue sampleQueue;
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      sampleQueue = rtspLoaderWrappers.get(i).sampleQueue;
      listBuilder.add(
          new TrackGroup(
              /* id= */ Integer.toString(i), checkNotNull(sampleQueue.getUpstreamFormat())));
    }
    return listBuilder.build();
  }

  private final class InternalListener
      implements ExtractorOutput,
          Loader.Callback<RtpDataLoadable>,
          UpstreamFormatChangedListener,
          SessionInfoListener,
          PlaybackEventListener {

    // ExtractorOutput implementation.

    @Override
    public TrackOutput track(int id, int type) {
      return checkNotNull(rtspLoaderWrappers.get(id)).sampleQueue;
    }

    @Override
    public void endTracks() {
      handler.post(RtspMediaPeriod.this::maybeFinishPrepare);
    }

    @Override
    public void seekMap(SeekMap seekMap) {
      // RTSP does not support seek map.
    }

    // Loadable.Callback implementation.

    @Override
    public void onLoadCompleted(
        RtpDataLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
      if (getBufferedPositionUs() == 0) {
        if (!isUsingRtpTcp) {
          // Retry playback with TCP if no sample has been received so far, and we are not already
          // using TCP. Retrying will setup new loadables, so will not retry with the current
          // loadables.
          retryWithRtpTcp();
          isUsingRtpTcp = true;
        }
        return;
      }

      // Cancel the loader wrapper associated with the completed loadable.
      for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
        RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);
        if (loaderWrapper.loadInfo.loadable == loadable) {
          loaderWrapper.cancelLoad();
          break;
        }
      }
    }

    @Override
    public void onLoadCanceled(
        RtpDataLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {}

    @Override
    public Loader.LoadErrorAction onLoadError(
        RtpDataLoadable loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      if (!prepared) {
        preparationError = error;
      } else {
        if (error.getCause() instanceof BindException) {
          // Allow for retry on RTP port open failure by catching BindException. Two ports are
          // opened for each RTP stream, the first port number is auto assigned by the system, while
          // the second is manually selected. It is thus possible that the second port fails to
          // bind. Failing is more likely when running in a server-side testing environment, it is
          // less likely on real devices.
          if (portBindingRetryCount++ < PORT_BINDING_MAX_RETRY_COUNT) {
            return Loader.RETRY;
          }
        } else {
          playbackException =
              new RtspPlaybackException(
                  /* message= */ loadable.rtspMediaTrack.uri.toString(), error);
        }
      }
      return Loader.DONT_RETRY;
    }

    // SampleQueue.UpstreamFormatChangedListener implementation.

    @Override
    public void onUpstreamFormatChanged(Format format) {
      handler.post(RtspMediaPeriod.this::maybeFinishPrepare);
    }

    // RtspClient.PlaybackEventListener implementation.

    @Override
    public void onRtspSetupCompleted() {
      long offsetMs = 0;
      if (pendingSeekPositionUs != C.TIME_UNSET) {
        offsetMs = Util.usToMs(pendingSeekPositionUs);
      } else if (pendingSeekPositionUsForTcpRetry != C.TIME_UNSET) {
        offsetMs = Util.usToMs(pendingSeekPositionUsForTcpRetry);
      }
      rtspClient.startPlayback(offsetMs);
    }

    @Override
    public void onPlaybackStarted(
        long startPositionUs, ImmutableList<RtspTrackTiming> trackTimingList) {

      // Validate that the trackTimingList contains timings for the selected tracks, and notify the
      // listener.
      ArrayList<String> trackUrisWithTiming = new ArrayList<>(trackTimingList.size());
      for (int i = 0; i < trackTimingList.size(); i++) {
        trackUrisWithTiming.add(checkNotNull(trackTimingList.get(i).uri.getPath()));
      }
      for (int i = 0; i < selectedLoadInfos.size(); i++) {
        RtpLoadInfo loadInfo = selectedLoadInfos.get(i);
        if (!trackUrisWithTiming.contains(loadInfo.getTrackUri().getPath())) {
          listener.onSeekingUnsupported();
          if (isSeekPending()) {
            notifyDiscontinuity = true;
            pendingSeekPositionUs = C.TIME_UNSET;
            requestedSeekPositionUs = C.TIME_UNSET;
            pendingSeekPositionUsForTcpRetry = C.TIME_UNSET;
          }
        }
      }

      for (int i = 0; i < trackTimingList.size(); i++) {
        RtspTrackTiming trackTiming = trackTimingList.get(i);
        @Nullable RtpDataLoadable dataLoadable = getLoadableByTrackUri(trackTiming.uri);
        if (dataLoadable == null) {
          continue;
        }

        dataLoadable.setTimestamp(trackTiming.rtpTimestamp);
        dataLoadable.setSequenceNumber(trackTiming.sequenceNumber);

        if (isSeekPending() && pendingSeekPositionUs == requestedSeekPositionUs) {
          // Seek loadable only when all pending seeks are processed, or SampleQueues will report
          // inconsistent bufferedPosition.
          // Seeks to the start position when the initial seek position is set.
          dataLoadable.seekToUs(startPositionUs, trackTiming.rtpTimestamp);
        }
      }

      if (isSeekPending()) {
        if (pendingSeekPositionUs == requestedSeekPositionUs) {
          // No seek request was made after the current pending seek.
          pendingSeekPositionUs = C.TIME_UNSET;
          requestedSeekPositionUs = C.TIME_UNSET;
        } else {
          // Resets pendingSeekPositionUs to perform a fresh RTSP seek.
          pendingSeekPositionUs = C.TIME_UNSET;
          seekToUs(requestedSeekPositionUs);
        }
      } else if (pendingSeekPositionUsForTcpRetry != C.TIME_UNSET && isUsingRtpTcp) {
        seekToUs(pendingSeekPositionUsForTcpRetry);
        pendingSeekPositionUsForTcpRetry = C.TIME_UNSET;
      }
    }

    @Override
    public void onPlaybackError(RtspPlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onSessionTimelineUpdated(
        RtspSessionTiming timing, ImmutableList<RtspMediaTrack> tracks) {
      for (int i = 0; i < tracks.size(); i++) {
        RtspMediaTrack rtspMediaTrack = tracks.get(i);
        RtspLoaderWrapper loaderWrapper =
            new RtspLoaderWrapper(rtspMediaTrack, /* trackId= */ i, rtpDataChannelFactory);
        rtspLoaderWrappers.add(loaderWrapper);
        loaderWrapper.startLoading();
      }

      listener.onSourceInfoRefreshed(timing);
    }

    @Override
    public void onSessionTimelineRequestFailed(String message, @Nullable Throwable cause) {
      preparationError = cause == null ? new IOException(message) : new IOException(message, cause);
    }
  }

  private void retryWithRtpTcp() {
    rtspClient.retryWithRtpTcp();

    @Nullable
    RtpDataChannel.Factory fallbackRtpDataChannelFactory =
        rtpDataChannelFactory.createFallbackDataChannelFactory();
    if (fallbackRtpDataChannelFactory == null) {
      playbackException =
          new RtspPlaybackException("No fallback data channel factory for TCP retry");
      return;
    }

    ArrayList<RtspLoaderWrapper> newLoaderWrappers = new ArrayList<>(rtspLoaderWrappers.size());
    ArrayList<RtpLoadInfo> newSelectedLoadInfos = new ArrayList<>(selectedLoadInfos.size());

    // newLoaderWrappers' elements and orders must match those of rtspLoaderWrappers'.
    for (int i = 0; i < rtspLoaderWrappers.size(); i++) {
      RtspLoaderWrapper loaderWrapper = rtspLoaderWrappers.get(i);

      if (!loaderWrapper.canceled) {
        RtspLoaderWrapper newLoaderWrapper =
            new RtspLoaderWrapper(
                loaderWrapper.loadInfo.mediaTrack, /* trackId= */ i, fallbackRtpDataChannelFactory);
        newLoaderWrappers.add(newLoaderWrapper);
        newLoaderWrapper.startLoading();
        if (selectedLoadInfos.contains(loaderWrapper.loadInfo)) {
          newSelectedLoadInfos.add(newLoaderWrapper.loadInfo);
        }
      } else {
        newLoaderWrappers.add(loaderWrapper);
      }
    }

    // Switch to new LoaderWrappers.
    ImmutableList<RtspLoaderWrapper> oldRtspLoaderWrappers =
        ImmutableList.copyOf(rtspLoaderWrappers);
    rtspLoaderWrappers.clear();
    rtspLoaderWrappers.addAll(newLoaderWrappers);
    selectedLoadInfos.clear();
    selectedLoadInfos.addAll(newSelectedLoadInfos);

    // Cancel old loadable wrappers after switching, so that buffered position is always read from
    // active sample queues.
    for (int i = 0; i < oldRtspLoaderWrappers.size(); i++) {
      oldRtspLoaderWrappers.get(i).cancelLoad();
    }
  }

  private final class SampleStreamImpl implements SampleStream {
    private final int track;

    public SampleStreamImpl(int track) {
      this.track = track;
    }

    @Override
    public boolean isReady() {
      return RtspMediaPeriod.this.isReady(track);
    }

    @Override
    public void maybeThrowError() throws RtspPlaybackException {
      if (playbackException != null) {
        throw playbackException;
      }
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      return RtspMediaPeriod.this.readData(track, formatHolder, buffer, readFlags);
    }

    @Override
    public int skipData(long positionUs) {
      return RtspMediaPeriod.this.skipData(track, positionUs);
    }
  }

  /** Manages the loading of an RTSP track. */
  private final class RtspLoaderWrapper {
    /** The {@link RtpLoadInfo} of the RTSP track to load. */
    public final RtpLoadInfo loadInfo;

    private final Loader loader;
    private final SampleQueue sampleQueue;
    private boolean canceled;
    private boolean released;

    /**
     * Creates a new instance.
     *
     * <p>Instances must be {@link #release() released} after loadings conclude.
     */
    public RtspLoaderWrapper(
        RtspMediaTrack mediaTrack, int trackId, RtpDataChannel.Factory rtpDataChannelFactory) {
      loadInfo = new RtpLoadInfo(mediaTrack, trackId, rtpDataChannelFactory);
      loader = new Loader("ExoPlayer:RtspMediaPeriod:RtspLoaderWrapper " + trackId);
      sampleQueue = SampleQueue.createWithoutDrm(allocator);
      sampleQueue.setUpstreamFormatChangeListener(internalListener);
    }

    /**
     * Returns the largest buffered position in microseconds; or {@link Long#MIN_VALUE} if no sample
     * has been queued.
     */
    public long getBufferedPositionUs() {
      return sampleQueue.getLargestQueuedTimestampUs();
    }

    /** Starts loading. */
    public void startLoading() {
      loader.startLoading(
          loadInfo.loadable, /* callback= */ internalListener, /* defaultMinRetryCount= */ 0);
    }

    public boolean isSampleQueueReady() {
      return sampleQueue.isReady(/* loadingFinished= */ canceled);
    }

    public @ReadDataResult int read(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      return sampleQueue.read(formatHolder, buffer, readFlags, /* loadingFinished= */ canceled);
    }

    public int skipData(long positionUs) {
      int skipCount = sampleQueue.getSkipCount(positionUs, /* allowEndOfQueue= */ canceled);
      sampleQueue.skip(skipCount);
      return skipCount;
    }

    /** Cancels loading. */
    public void cancelLoad() {
      if (!canceled) {
        loadInfo.loadable.cancelLoad();
        canceled = true;

        // Update loadingFinished every time loading is canceled.
        updateLoadingFinished();
      }
    }

    /** Resets the {@link Loadable} and {@link SampleQueue} to prepare for an RTSP seek. */
    public void seekTo(long positionUs) {
      if (!canceled) {
        loadInfo.loadable.resetForSeek();
        sampleQueue.reset();
        sampleQueue.setStartTimeUs(positionUs);
      }
    }

    /** Releases the instance. */
    public void release() {
      if (released) {
        return;
      }
      loader.release();
      sampleQueue.release();
      released = true;
    }
  }

  /** Groups the info needed for loading one RTSP track in RTP. */
  /* package */ final class RtpLoadInfo {
    /** The {@link RtspMediaTrack}. */
    public final RtspMediaTrack mediaTrack;

    private final RtpDataLoadable loadable;

    @Nullable private String transport;

    /** Creates a new instance. */
    public RtpLoadInfo(
        RtspMediaTrack mediaTrack, int trackId, RtpDataChannel.Factory rtpDataChannelFactory) {
      this.mediaTrack = mediaTrack;

      // This listener runs on the playback thread, posted by the Loader thread.
      RtpDataLoadable.EventListener transportEventListener =
          (transport, rtpDataChannel) -> {
            RtpLoadInfo.this.transport = transport;

            @Nullable
            RtspMessageChannel.InterleavedBinaryDataListener interleavedBinaryDataListener =
                rtpDataChannel.getInterleavedBinaryDataListener();
            if (interleavedBinaryDataListener != null) {
              rtspClient.registerInterleavedDataChannel(
                  rtpDataChannel.getLocalPort(), interleavedBinaryDataListener);
              isUsingRtpTcp = true;
            }
            maybeSetupTracks();
          };

      this.loadable =
          new RtpDataLoadable(
              trackId,
              mediaTrack,
              /* eventListener= */ transportEventListener,
              /* output= */ internalListener,
              rtpDataChannelFactory);
    }

    /**
     * Returns whether RTP transport is ready. Call {@link #getTransport()} only after transport is
     * ready.
     */
    public boolean isTransportReady() {
      return transport != null;
    }

    /**
     * Gets the transport string for RTP loading.
     *
     * @throws IllegalStateException When transport for this RTP stream is not set.
     */
    public String getTransport() {
      checkStateNotNull(transport);
      return transport;
    }

    /** Gets the {@link Uri} for the loading RTSP track. */
    public Uri getTrackUri() {
      return loadable.rtspMediaTrack.uri;
    }
  }
}
