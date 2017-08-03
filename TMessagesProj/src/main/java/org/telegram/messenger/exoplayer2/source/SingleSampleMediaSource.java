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
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Loads data at a given {@link Uri} as a single sample belonging to a single {@link MediaPeriod}.
 */
public final class SingleSampleMediaSource implements MediaSource {

  /**
   * Listener of {@link SingleSampleMediaSource} events.
   */
  public interface EventListener {

    /**
     * Called when an error occurs loading media data.
     *
     * @param sourceId The id of the reporting {@link SingleSampleMediaSource}.
     * @param e The cause of the failure.
     */
    void onLoadError(int sourceId, IOException e);

  }

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  private final Uri uri;
  private final DataSource.Factory dataSourceFactory;
  private final Format format;
  private final int minLoadableRetryCount;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int eventSourceId;
  private final Timeline timeline;

  public SingleSampleMediaSource(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      long durationUs) {
    this(uri, dataSourceFactory, format, durationUs, DEFAULT_MIN_LOADABLE_RETRY_COUNT);
  }

  public SingleSampleMediaSource(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      long durationUs, int minLoadableRetryCount) {
    this(uri, dataSourceFactory, format, durationUs, minLoadableRetryCount, null, null, 0);
  }

  public SingleSampleMediaSource(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      long durationUs, int minLoadableRetryCount, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this.uri = uri;
    this.dataSourceFactory = dataSourceFactory;
    this.format = format;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    timeline = new SinglePeriodTimeline(durationUs, true);
  }

  // MediaSource implementation.

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    listener.onSourceInfoRefreshed(timeline, null);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    Assertions.checkArgument(index == 0);
    return new SingleSampleMediaPeriod(uri, dataSourceFactory, format, minLoadableRetryCount,
        eventHandler, eventListener, eventSourceId);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((SingleSampleMediaPeriod) mediaPeriod).release();
  }

  @Override
  public void releaseSource() {
    // Do nothing.
  }

}
