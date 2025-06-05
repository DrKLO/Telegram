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
package com.google.android.exoplayer2.source.smoothstreaming;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.offline.FilteringManifestParser;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A SmoothStreaming {@link MediaSource}. */
public final class SsMediaSource extends BaseMediaSource
    implements Loader.Callback<ParsingLoadable<SsManifest>> {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.smoothstreaming");
  }

  /** Factory for {@link SsMediaSource}. */
  @SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
  public static final class Factory implements MediaSourceFactory {

    private final SsChunkSource.Factory chunkSourceFactory;
    @Nullable private final DataSource.Factory manifestDataSourceFactory;

    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private long livePresentationDelayMs;
    @Nullable private ParsingLoadable.Parser<? extends SsManifest> manifestParser;

    /**
     * Creates a new factory for {@link SsMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultSsChunkSource.Factory}
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param dataSourceFactory A factory for {@link DataSource} instances that will be used to load
     *     manifest and media data.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultSsChunkSource.Factory(dataSourceFactory), dataSourceFactory);
    }

    /**
     * Creates a new factory for {@link SsMediaSource}s. The factory will use the following default
     * components:
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param chunkSourceFactory A factory for {@link SsChunkSource} instances.
     * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
     *     to load (and refresh) the manifest. May be {@code null} if the factory will only ever be
     *     used to create create media sources with sideloaded manifests via {@link
     *     #createMediaSource(SsManifest, MediaItem)}.
     */
    public Factory(
        SsChunkSource.Factory chunkSourceFactory,
        @Nullable DataSource.Factory manifestDataSourceFactory) {
      this.chunkSourceFactory = checkNotNull(chunkSourceFactory);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      livePresentationDelayMs = DEFAULT_LIVE_PRESENTATION_DELAY_MS;
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      this.loadErrorHandlingPolicy =
          checkNotNull(
              loadErrorHandlingPolicy,
              "MediaSource.Factory#setLoadErrorHandlingPolicy no longer handles null by"
                  + " instantiating a new DefaultLoadErrorHandlingPolicy. Explicitly construct and"
                  + " pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Sets the duration in milliseconds by which the default start position should precede the end
     * of the live window for live playbacks. The default value is {@link
     * #DEFAULT_LIVE_PRESENTATION_DELAY_MS}.
     *
     * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
     *     default start position should precede the end of the live window.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setLivePresentationDelayMs(long livePresentationDelayMs) {
      this.livePresentationDelayMs = livePresentationDelayMs;
      return this;
    }

    /**
     * Sets the manifest parser to parse loaded manifest data when loading a manifest URI.
     *
     * @param manifestParser A parser for loaded manifest data.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setManifestParser(
        @Nullable ParsingLoadable.Parser<? extends SsManifest> manifestParser) {
      this.manifestParser = manifestParser;
      return this;
    }

    /**
     * Sets the factory to create composite {@link SequenceableLoader}s for when this media source
     * loads data from multiple streams (video, audio etc.).
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc.).
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      this.compositeSequenceableLoaderFactory =
          checkNotNull(
              compositeSequenceableLoaderFactory,
              "SsMediaSource.Factory#setCompositeSequenceableLoaderFactory no longer handles null"
                  + " by instantiating a new DefaultCompositeSequenceableLoaderFactory. Explicitly"
                  + " construct and pass an instance in order to retain the old behavior.");
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public Factory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      this.drmSessionManagerProvider =
          checkNotNull(
              drmSessionManagerProvider,
              "MediaSource.Factory#setDrmSessionManagerProvider no longer handles null by"
                  + " instantiating a new DefaultDrmSessionManagerProvider. Explicitly construct"
                  + " and pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Returns a new {@link SsMediaSource} using the current parameters and the specified sideloaded
     * manifest.
     *
     * @param manifest The manifest. {@link SsManifest#isLive} must be false.
     * @return The new {@link SsMediaSource}.
     * @throws IllegalArgumentException If {@link SsManifest#isLive} is true.
     */
    public SsMediaSource createMediaSource(SsManifest manifest) {
      return createMediaSource(manifest, MediaItem.fromUri(Uri.EMPTY));
    }

    /**
     * Returns a new {@link SsMediaSource} using the current parameters and the specified sideloaded
     * manifest.
     *
     * @param manifest The manifest. {@link SsManifest#isLive} must be false.
     * @param mediaItem The {@link MediaItem} to be included in the timeline.
     * @return The new {@link SsMediaSource}.
     * @throws IllegalArgumentException If {@link SsManifest#isLive} is true.
     */
    public SsMediaSource createMediaSource(SsManifest manifest, MediaItem mediaItem) {
      Assertions.checkArgument(!manifest.isLive);
      List<StreamKey> streamKeys =
          mediaItem.localConfiguration != null
              ? mediaItem.localConfiguration.streamKeys
              : ImmutableList.of();
      if (!streamKeys.isEmpty()) {
        manifest = manifest.copy(streamKeys);
      }
      boolean hasUri = mediaItem.localConfiguration != null;
      mediaItem =
          mediaItem
              .buildUpon()
              .setMimeType(MimeTypes.APPLICATION_SS)
              .setUri(hasUri ? mediaItem.localConfiguration.uri : Uri.EMPTY)
              .build();
      return new SsMediaSource(
          mediaItem,
          manifest,
          /* manifestDataSourceFactory= */ null,
          /* manifestParser= */ null,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          livePresentationDelayMs);
    }

    /**
     * Returns a new {@link SsMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link SsMediaSource}.
     * @throws NullPointerException if {@link MediaItem#localConfiguration} is {@code null}.
     */
    @Override
    public SsMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      @Nullable ParsingLoadable.Parser<? extends SsManifest> manifestParser = this.manifestParser;
      if (manifestParser == null) {
        manifestParser = new SsManifestParser();
      }
      List<StreamKey> streamKeys = mediaItem.localConfiguration.streamKeys;
      if (!streamKeys.isEmpty()) {
        manifestParser = new FilteringManifestParser<>(manifestParser, streamKeys);
      }

      return new SsMediaSource(
          mediaItem,
          /* manifest= */ null,
          manifestDataSourceFactory,
          manifestParser,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          livePresentationDelayMs);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_SS};
    }
  }

  /**
   * The default presentation delay for live streams. The presentation delay is the duration by
   * which the default start position precedes the end of the live window.
   */
  public static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30_000;

  /** The minimum period between manifest refreshes. */
  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  /**
   * The minimum default start position for live streams, relative to the start of the live window.
   */
  private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5_000_000;

  private final boolean sideloadedManifest;
  private final Uri manifestUri;
  private final MediaItem.LocalConfiguration localConfiguration;
  private final MediaItem mediaItem;
  private final DataSource.Factory manifestDataSourceFactory;
  private final SsChunkSource.Factory chunkSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final long livePresentationDelayMs;
  private final EventDispatcher manifestEventDispatcher;
  private final ParsingLoadable.Parser<? extends SsManifest> manifestParser;
  private final ArrayList<SsMediaPeriod> mediaPeriods;

  private DataSource manifestDataSource;
  private Loader manifestLoader;
  private LoaderErrorThrower manifestLoaderErrorThrower;
  @Nullable private TransferListener mediaTransferListener;

  private long manifestLoadStartTimestamp;
  private SsManifest manifest;

  private Handler manifestRefreshHandler;

  private SsMediaSource(
      MediaItem mediaItem,
      @Nullable SsManifest manifest,
      @Nullable DataSource.Factory manifestDataSourceFactory,
      @Nullable ParsingLoadable.Parser<? extends SsManifest> manifestParser,
      SsChunkSource.Factory chunkSourceFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      long livePresentationDelayMs) {
    Assertions.checkState(manifest == null || !manifest.isLive);
    this.mediaItem = mediaItem;
    localConfiguration = checkNotNull(mediaItem.localConfiguration);
    this.manifest = manifest;
    this.manifestUri =
        localConfiguration.uri.equals(Uri.EMPTY)
            ? null
            : Util.fixSmoothStreamingIsmManifestUri(localConfiguration.uri);
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.manifestParser = manifestParser;
    this.chunkSourceFactory = chunkSourceFactory;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.livePresentationDelayMs = livePresentationDelayMs;
    this.manifestEventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
    sideloadedManifest = manifest != null;
    mediaPeriods = new ArrayList<>();
  }

  // MediaSource implementation.

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    drmSessionManager.prepare();
    drmSessionManager.setPlayer(/* playbackLooper= */ Looper.myLooper(), getPlayerId());
    if (sideloadedManifest) {
      manifestLoaderErrorThrower = new LoaderErrorThrower.Dummy();
      processManifest();
    } else {
      manifestDataSource = manifestDataSourceFactory.createDataSource();
      manifestLoader = new Loader("SsMediaSource");
      manifestLoaderErrorThrower = manifestLoader;
      manifestRefreshHandler = Util.createHandlerForCurrentLooper();
      startLoadingManifest();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher = createEventDispatcher(id);
    DrmSessionEventListener.EventDispatcher drmEventDispatcher = createDrmEventDispatcher(id);
    SsMediaPeriod period =
        new SsMediaPeriod(
            manifest,
            chunkSourceFactory,
            mediaTransferListener,
            compositeSequenceableLoaderFactory,
            drmSessionManager,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            mediaSourceEventDispatcher,
            manifestLoaderErrorThrower,
            allocator);
    mediaPeriods.add(period);
    return period;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((SsMediaPeriod) mediaPeriod).release();
    mediaPeriods.remove(mediaPeriod);
  }

  @Override
  protected void releaseSourceInternal() {
    manifest = sideloadedManifest ? manifest : null;
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
    drmSessionManager.release();
  }

  // Loader.Callback implementation

  @Override
  public void onLoadCompleted(
      ParsingLoadable<SsManifest> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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
    manifestEventDispatcher.loadCompleted(loadEventInfo, loadable.type);
    manifest = loadable.getResult();
    manifestLoadStartTimestamp = elapsedRealtimeMs - loadDurationMs;
    processManifest();
    scheduleManifestRefresh();
  }

  @Override
  public void onLoadCanceled(
      ParsingLoadable<SsManifest> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      boolean released) {
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
    manifestEventDispatcher.loadCanceled(loadEventInfo, loadable.type);
  }

  @Override
  public LoadErrorAction onLoadError(
      ParsingLoadable<SsManifest> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
    MediaLoadData mediaLoadData = new MediaLoadData(loadable.type);
    long retryDelayMs =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
            new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount));
    LoadErrorAction loadErrorAction =
        retryDelayMs == C.TIME_UNSET
            ? Loader.DONT_RETRY_FATAL
            : Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs);
    boolean wasCanceled = !loadErrorAction.isRetry();
    manifestEventDispatcher.loadError(loadEventInfo, loadable.type, error, wasCanceled);
    if (wasCanceled) {
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }
    return loadErrorAction;
  }

  // Internal methods

  private void processManifest() {
    for (int i = 0; i < mediaPeriods.size(); i++) {
      mediaPeriods.get(i).updateManifest(manifest);
    }

    long startTimeUs = Long.MAX_VALUE;
    long endTimeUs = Long.MIN_VALUE;
    for (StreamElement element : manifest.streamElements) {
      if (element.chunkCount > 0) {
        startTimeUs = min(startTimeUs, element.getStartTimeUs(0));
        endTimeUs =
            max(
                endTimeUs,
                element.getStartTimeUs(element.chunkCount - 1)
                    + element.getChunkDurationUs(element.chunkCount - 1));
      }
    }

    Timeline timeline;
    if (startTimeUs == Long.MAX_VALUE) {
      long periodDurationUs = manifest.isLive ? C.TIME_UNSET : 0;
      timeline =
          new SinglePeriodTimeline(
              periodDurationUs,
              /* windowDurationUs= */ 0,
              /* windowPositionInPeriodUs= */ 0,
              /* windowDefaultStartPositionUs= */ 0,
              /* isSeekable= */ true,
              /* isDynamic= */ manifest.isLive,
              /* useLiveConfiguration= */ manifest.isLive,
              manifest,
              mediaItem);
    } else if (manifest.isLive) {
      if (manifest.dvrWindowLengthUs != C.TIME_UNSET && manifest.dvrWindowLengthUs > 0) {
        startTimeUs = max(startTimeUs, endTimeUs - manifest.dvrWindowLengthUs);
      }
      long durationUs = endTimeUs - startTimeUs;
      long defaultStartPositionUs = durationUs - Util.msToUs(livePresentationDelayMs);
      if (defaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
        // The default start position is too close to the start of the live window. Set it to the
        // minimum default start position provided the window is at least twice as big. Else set
        // it to the middle of the window.
        defaultStartPositionUs = min(MIN_LIVE_DEFAULT_START_POSITION_US, durationUs / 2);
      }
      timeline =
          new SinglePeriodTimeline(
              /* periodDurationUs= */ C.TIME_UNSET,
              durationUs,
              startTimeUs,
              defaultStartPositionUs,
              /* isSeekable= */ true,
              /* isDynamic= */ true,
              /* useLiveConfiguration= */ true,
              manifest,
              mediaItem);
    } else {
      long durationUs =
          manifest.durationUs != C.TIME_UNSET ? manifest.durationUs : endTimeUs - startTimeUs;
      timeline =
          new SinglePeriodTimeline(
              startTimeUs + durationUs,
              durationUs,
              startTimeUs,
              /* windowDefaultStartPositionUs= */ 0,
              /* isSeekable= */ true,
              /* isDynamic= */ false,
              /* useLiveConfiguration= */ false,
              manifest,
              mediaItem);
    }
    refreshSourceInfo(timeline);
  }

  private void scheduleManifestRefresh() {
    if (!manifest.isLive) {
      return;
    }
    long nextLoadTimestamp = manifestLoadStartTimestamp + MINIMUM_MANIFEST_REFRESH_PERIOD_MS;
    long delayUntilNextLoad = max(0, nextLoadTimestamp - SystemClock.elapsedRealtime());
    manifestRefreshHandler.postDelayed(this::startLoadingManifest, delayUntilNextLoad);
  }

  private void startLoadingManifest() {
    if (manifestLoader.hasFatalError()) {
      return;
    }
    ParsingLoadable<SsManifest> loadable =
        new ParsingLoadable<>(
            manifestDataSource, manifestUri, C.DATA_TYPE_MANIFEST, manifestParser);
    long elapsedRealtimeMs =
        manifestLoader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    manifestEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs),
        loadable.type);
  }
}
