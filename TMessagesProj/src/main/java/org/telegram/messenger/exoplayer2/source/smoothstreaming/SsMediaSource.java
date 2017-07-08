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
package org.telegram.messenger.exoplayer2.source.smoothstreaming;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.SinglePeriodTimeline;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.upstream.LoaderErrorThrower;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A SmoothStreaming {@link MediaSource}.
 */
public final class SsMediaSource implements MediaSource,
    Loader.Callback<ParsingLoadable<SsManifest>> {

  /**
   * The default minimum number of times to retry loading data prior to failing.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;
  /**
   * The default presentation delay for live streams. The presentation delay is the duration by
   * which the default start position precedes the end of the live window.
   */
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30000;

  /**
   * The minimum period between manifest refreshes.
   */
  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  /**
   * The minimum default start position for live streams, relative to the start of the live window.
   */
  private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5000000;

  private final Uri manifestUri;
  private final DataSource.Factory manifestDataSourceFactory;
  private final SsChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final long livePresentationDelayMs;
  private final EventDispatcher eventDispatcher;
  private final ParsingLoadable.Parser<? extends SsManifest> manifestParser;
  private final ArrayList<SsMediaPeriod> mediaPeriods;

  private Listener sourceListener;
  private DataSource manifestDataSource;
  private Loader manifestLoader;
  private LoaderErrorThrower manifestLoaderErrorThrower;

  private long manifestLoadStartTimestamp;
  private SsManifest manifest;

  private Handler manifestRefreshHandler;

  /**
   * Constructs an instance to play a given {@link SsManifest}, which must not be live.
   *
   * @param manifest The manifest. {@link SsManifest#isLive} must be false.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public SsMediaSource(SsManifest manifest, SsChunkSource.Factory chunkSourceFactory,
      Handler eventHandler, AdaptiveMediaSourceEventListener eventListener) {
    this(manifest, chunkSourceFactory, DEFAULT_MIN_LOADABLE_RETRY_COUNT,
        eventHandler, eventListener);
  }

  /**
   * Constructs an instance to play a given {@link SsManifest}, which must not be live.
   *
   * @param manifest The manifest. {@link SsManifest#isLive} must be false.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public SsMediaSource(SsManifest manifest, SsChunkSource.Factory chunkSourceFactory,
      int minLoadableRetryCount, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifest, null, null, null, chunkSourceFactory, minLoadableRetryCount,
        DEFAULT_LIVE_PRESENTATION_DELAY_MS, eventHandler, eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be live or
   * on-demand.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public SsMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      SsChunkSource.Factory chunkSourceFactory, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, manifestDataSourceFactory, chunkSourceFactory,
        DEFAULT_MIN_LOADABLE_RETRY_COUNT, DEFAULT_LIVE_PRESENTATION_DELAY_MS, eventHandler,
        eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be live or
   * on-demand.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
   *     default start position should precede the end of the live window.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public SsMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      SsChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
      long livePresentationDelayMs, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(manifestUri, manifestDataSourceFactory, new SsManifestParser(), chunkSourceFactory,
        minLoadableRetryCount, livePresentationDelayMs, eventHandler, eventListener);
  }

  /**
   * Constructs an instance to play the manifest at a given {@link Uri}, which may be live or
   * on-demand.
   *
   * @param manifestUri The manifest {@link Uri}.
   * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
   *     to load (and refresh) the manifest.
   * @param manifestParser A parser for loaded manifest data.
   * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
   * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
   * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
   *     default start position should precede the end of the live window.
   * @param eventHandler A handler for events. May be null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public SsMediaSource(Uri manifestUri, DataSource.Factory manifestDataSourceFactory,
      ParsingLoadable.Parser<? extends SsManifest> manifestParser,
      SsChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
      long livePresentationDelayMs, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    this(null, manifestUri, manifestDataSourceFactory, manifestParser, chunkSourceFactory,
        minLoadableRetryCount, livePresentationDelayMs, eventHandler, eventListener);
  }

  private SsMediaSource(SsManifest manifest, Uri manifestUri,
      DataSource.Factory manifestDataSourceFactory,
      ParsingLoadable.Parser<? extends SsManifest> manifestParser,
      SsChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
      long livePresentationDelayMs, Handler eventHandler,
      AdaptiveMediaSourceEventListener eventListener) {
    Assertions.checkState(manifest == null || !manifest.isLive);
    this.manifest = manifest;
    this.manifestUri = manifestUri == null ? null
        : Util.toLowerInvariant(manifestUri.getLastPathSegment()).equals("manifest") ? manifestUri
            : Uri.withAppendedPath(manifestUri, "Manifest");
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.manifestParser = manifestParser;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.livePresentationDelayMs = livePresentationDelayMs;
    this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    mediaPeriods = new ArrayList<>();
  }

  // MediaSource implementation.

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    sourceListener = listener;
    if (manifest != null) {
      manifestLoaderErrorThrower = new LoaderErrorThrower.Dummy();
      processManifest();
    } else {
      manifestDataSource = manifestDataSourceFactory.createDataSource();
      manifestLoader = new Loader("Loader:Manifest");
      manifestLoaderErrorThrower = manifestLoader;
      manifestRefreshHandler = new Handler();
      startLoadingManifest();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    Assertions.checkArgument(index == 0);
    SsMediaPeriod period = new SsMediaPeriod(manifest, chunkSourceFactory, minLoadableRetryCount,
        eventDispatcher, manifestLoaderErrorThrower, allocator);
    mediaPeriods.add(period);
    return period;
  }

  @Override
  public void releasePeriod(MediaPeriod period) {
    ((SsMediaPeriod) period).release();
    mediaPeriods.remove(period);
  }

  @Override
  public void releaseSource() {
    sourceListener = null;
    manifest = null;
    manifestDataSource = null;
    manifestLoadStartTimestamp = 0;
    if (manifestLoader != null) {
      manifestLoader.release();
      manifestLoader = null;
    }
    if (manifestRefreshHandler != null) {
      manifestRefreshHandler.removeCallbacksAndMessages(null);
      manifestRefreshHandler = null;
    }
  }

  // Loader.Callback implementation

  @Override
  public void onLoadCompleted(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
    manifest = loadable.getResult();
    manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
    processManifest();
    scheduleManifestRefresh();
  }

  @Override
  public void onLoadCanceled(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    eventDispatcher.loadCompleted(loadable.dataSpec, loadable.type, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
  }

  @Override
  public int onLoadError(ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    boolean isFatal = error instanceof ParserException;
    eventDispatcher.loadError(loadable.dataSpec, loadable.type, elapsedRealtimeMs, loadDurationMs,
        loadable.bytesLoaded(), error, isFatal);
    return isFatal ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
  }

  // Internal methods

  private void processManifest() {
    for (int i = 0; i < mediaPeriods.size(); i++) {
      mediaPeriods.get(i).updateManifest(manifest);
    }
    Timeline timeline;
    if (manifest.isLive) {
      long startTimeUs = Long.MAX_VALUE;
      long endTimeUs = Long.MIN_VALUE;
      for (int i = 0; i < manifest.streamElements.length; i++) {
        StreamElement element = manifest.streamElements[i];
        if (element.chunkCount > 0) {
          startTimeUs = Math.min(startTimeUs, element.getStartTimeUs(0));
          endTimeUs = Math.max(endTimeUs, element.getStartTimeUs(element.chunkCount - 1)
              + element.getChunkDurationUs(element.chunkCount - 1));
        }
      }
      if (startTimeUs == Long.MAX_VALUE) {
        timeline = new SinglePeriodTimeline(C.TIME_UNSET, false);
      } else {
        if (manifest.dvrWindowLengthUs != C.TIME_UNSET
            && manifest.dvrWindowLengthUs > 0) {
          startTimeUs = Math.max(startTimeUs, endTimeUs - manifest.dvrWindowLengthUs);
        }
        long durationUs = endTimeUs - startTimeUs;
        long defaultStartPositionUs = durationUs - C.msToUs(livePresentationDelayMs);
        if (defaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
          // The default start position is too close to the start of the live window. Set it to the
          // minimum default start position provided the window is at least twice as big. Else set
          // it to the middle of the window.
          defaultStartPositionUs = Math.min(MIN_LIVE_DEFAULT_START_POSITION_US, durationUs / 2);
        }
        timeline = new SinglePeriodTimeline(C.TIME_UNSET, durationUs, startTimeUs,
            defaultStartPositionUs, true /* isSeekable */, true /* isDynamic */);
      }
    } else {
      boolean isSeekable = manifest.durationUs != C.TIME_UNSET;
      timeline = new SinglePeriodTimeline(manifest.durationUs, isSeekable);
    }
    sourceListener.onSourceInfoRefreshed(timeline, manifest);
  }

  private void scheduleManifestRefresh() {
    if (!manifest.isLive) {
      return;
    }
    long nextLoadTimestamp = manifestLoadStartTimestamp + MINIMUM_MANIFEST_REFRESH_PERIOD_MS;
    long delayUntilNextLoad = Math.max(0, nextLoadTimestamp - SystemClock.elapsedRealtime());
    manifestRefreshHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        startLoadingManifest();
      }
    }, delayUntilNextLoad);
  }

  private void startLoadingManifest() {
    ParsingLoadable<SsManifest> loadable = new ParsingLoadable<>(manifestDataSource,
        manifestUri, C.DATA_TYPE_MANIFEST, manifestParser);
    long elapsedRealtimeMs = manifestLoader.startLoading(loadable, this, minLoadableRetryCount);
    eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs);
  }

}
