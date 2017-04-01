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

import android.net.Uri;
import android.os.Handler;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.SinglePeriodTimeline;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.List;

/**
 * An HLS {@link MediaSource}.
 */
public final class HlsMediaSource implements MediaSource,
    HlsPlaylistTracker.PrimaryPlaylistListener {

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private final Uri manifestUri;
  private final DataSource.Factory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;

  private HlsPlaylistTracker playlistTracker;
  private MediaSource.Listener sourceListener;

  public HlsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, dataSourceFactory, DEFAULT_MIN_LOADABLE_RETRY_COUNT, eventHandler,
        eventListener);
  }

  public HlsMediaSource(Uri manifestUri, DataSource.Factory dataSourceFactory,
      int minLoadableRetryCount, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this.manifestUri = manifestUri;
    this.dataSourceFactory = dataSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
  }

  @Override
  public void prepareSource(MediaSource.Listener listener) {
    Assertions.checkState(playlistTracker == null);
    playlistTracker = new HlsPlaylistTracker(manifestUri, dataSourceFactory, eventDispatcher,
        minLoadableRetryCount, this);
    sourceListener = listener;
    playlistTracker.start();
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    Assertions.checkArgument(index == 0);
    return new HlsMediaPeriod(playlistTracker, dataSourceFactory, minLoadableRetryCount,
        eventDispatcher, allocator, positionUs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSource() {
    playlistTracker.release();
    playlistTracker = null;
    sourceListener = null;
  }

  @Override
  public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist playlist) {
    SinglePeriodTimeline timeline;
    if (playlistTracker.isLive()) {
      // TODO: fix windowPositionInPeriodUs when playlist is empty.
      long windowPositionInPeriodUs = playlist.getStartTimeUs();
      List<HlsMediaPlaylist.Segment> segments = playlist.segments;
      long windowDefaultStartPositionUs = segments.isEmpty() ? 0
          : segments.get(Math.max(0, segments.size() - 3)).startTimeUs - windowPositionInPeriodUs;
      timeline = new SinglePeriodTimeline(C.TIME_UNSET, playlist.durationUs,
          windowPositionInPeriodUs, windowDefaultStartPositionUs, true, !playlist.hasEndTag);
    } else /* not live */ {
      timeline = new SinglePeriodTimeline(playlist.durationUs, playlist.durationUs, 0, 0, true,
          false);
    }
    sourceListener.onSourceInfoRefreshed(timeline, playlist);
  }

}
