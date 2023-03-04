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
package com.google.android.exoplayer2.source.dash;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.constrainValue;
import static com.google.android.exoplayer2.util.Util.usToMs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.Player;
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
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
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
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.SntpClient;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;
import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A DASH {@link MediaSource}. */
public final class DashMediaSource extends BaseMediaSource {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.dash");
  }

  /** Factory for {@link DashMediaSource}s. */
  @SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
  public static final class Factory implements MediaSourceFactory {

    private final DashChunkSource.Factory chunkSourceFactory;
    @Nullable private final DataSource.Factory manifestDataSourceFactory;

    private DrmSessionManagerProvider drmSessionManagerProvider;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private long fallbackTargetLiveOffsetMs;
    @Nullable private ParsingLoadable.Parser<? extends DashManifest> manifestParser;

    /**
     * Creates a new factory for {@link DashMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultDashChunkSource.Factory}
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param dataSourceFactory A factory for {@link DataSource} instances that will be used to load
     *     manifest and media data.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory);
    }

    /**
     * Creates a new factory for {@link DashMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     *   <li>{@link DefaultCompositeSequenceableLoaderFactory}
     * </ul>
     *
     * @param chunkSourceFactory A factory for {@link DashChunkSource} instances.
     * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
     *     to load (and refresh) the manifest. May be {@code null} if the factory will only ever be
     *     used to create create media sources with sideloaded manifests via {@link
     *     #createMediaSource(DashManifest, MediaItem)}.
     */
    public Factory(
        DashChunkSource.Factory chunkSourceFactory,
        @Nullable DataSource.Factory manifestDataSourceFactory) {
      this.chunkSourceFactory = checkNotNull(chunkSourceFactory);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
      drmSessionManagerProvider = new DefaultDrmSessionManagerProvider();
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      fallbackTargetLiveOffsetMs = DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS;
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
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
     * Sets the target {@link Player#getCurrentLiveOffset() offset for live streams} that is used if
     * no value is defined in the {@link MediaItem} or the manifest.
     *
     * <p>The default value is {@link #DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS}.
     *
     * @param fallbackTargetLiveOffsetMs The fallback live target offset in milliseconds.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setFallbackTargetLiveOffsetMs(long fallbackTargetLiveOffsetMs) {
      this.fallbackTargetLiveOffsetMs = fallbackTargetLiveOffsetMs;
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
        @Nullable ParsingLoadable.Parser<? extends DashManifest> manifestParser) {
      this.manifestParser = manifestParser;
      return this;
    }

    /**
     * Sets the factory to create composite {@link SequenceableLoader}s for when this media source
     * loads data from multiple streams (video, audio etc...). The default is an instance of {@link
     * DefaultCompositeSequenceableLoaderFactory}.
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc...).
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      this.compositeSequenceableLoaderFactory =
          checkNotNull(
              compositeSequenceableLoaderFactory,
              "DashMediaSource.Factory#setCompositeSequenceableLoaderFactory no longer handles null"
                  + " by instantiating a new DefaultCompositeSequenceableLoaderFactory. Explicitly"
                  + " construct and pass an instance in order to retain the old behavior.");
      return this;
    }

    /**
     * Returns a new {@link DashMediaSource} using the current parameters and the specified
     * sideloaded manifest.
     *
     * @param manifest The manifest. {@link DashManifest#dynamic} must be false.
     * @return The new {@link DashMediaSource}.
     * @throws IllegalArgumentException If {@link DashManifest#dynamic} is true.
     */
    public DashMediaSource createMediaSource(DashManifest manifest) {
      return createMediaSource(
          manifest,
          new MediaItem.Builder()
              .setUri(Uri.EMPTY)
              .setMediaId(DEFAULT_MEDIA_ID)
              .setMimeType(MimeTypes.APPLICATION_MPD)
              .build());
    }

    /**
     * Returns a new {@link DashMediaSource} using the current parameters and the specified
     * sideloaded manifest.
     *
     * @param manifest The manifest. {@link DashManifest#dynamic} must be false.
     * @param mediaItem The {@link MediaItem} to be included in the timeline.
     * @return The new {@link DashMediaSource}.
     * @throws IllegalArgumentException If {@link DashManifest#dynamic} is true.
     */
    public DashMediaSource createMediaSource(DashManifest manifest, MediaItem mediaItem) {
      Assertions.checkArgument(!manifest.dynamic);
      MediaItem.Builder mediaItemBuilder =
          mediaItem.buildUpon().setMimeType(MimeTypes.APPLICATION_MPD);
      if (mediaItem.localConfiguration == null) {
        mediaItemBuilder.setUri(Uri.EMPTY);
      }
      mediaItem = mediaItemBuilder.build();
      return new DashMediaSource(
          mediaItem,
          manifest,
          /* manifestDataSourceFactory= */ null,
          /* manifestParser= */ null,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          fallbackTargetLiveOffsetMs);
    }

    /**
     * Returns a new {@link DashMediaSource} using the current parameters.
     *
     * @param mediaItem The media item of the dash stream.
     * @return The new {@link DashMediaSource}.
     * @throws NullPointerException if {@link MediaItem#localConfiguration} is {@code null}.
     */
    @Override
    public DashMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      @Nullable ParsingLoadable.Parser<? extends DashManifest> manifestParser = this.manifestParser;
      if (manifestParser == null) {
        manifestParser = new DashManifestParser();
      }
      List<StreamKey> streamKeys = mediaItem.localConfiguration.streamKeys;
      if (!streamKeys.isEmpty()) {
        manifestParser = new FilteringManifestParser<>(manifestParser, streamKeys);
      }

      return new DashMediaSource(
          mediaItem,
          /* manifest= */ null,
          manifestDataSourceFactory,
          manifestParser,
          chunkSourceFactory,
          compositeSequenceableLoaderFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          fallbackTargetLiveOffsetMs);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_DASH};
    }
  }

  /**
   * The default target {@link Player#getCurrentLiveOffset() offset for live streams} that is used
   * if no value is defined in the {@link MediaItem} or the manifest.
   */
  public static final long DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS = 30_000;
  /**
   * @deprecated Use {@link #DEFAULT_FALLBACK_TARGET_LIVE_OFFSET_MS} instead.
   */
  @Deprecated public static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30_000;
  /** The media id used by media items of dash media sources without a manifest URI. */
  public static final String DEFAULT_MEDIA_ID = "DashMediaSource";

  /**
   * The interval in milliseconds between invocations of {@link
   * MediaSourceCaller#onSourceInfoRefreshed(MediaSource, Timeline)} when the source's {@link
   * Timeline} is changing dynamically (for example, for incomplete live streams).
   */
  private static final long DEFAULT_NOTIFY_MANIFEST_INTERVAL_MS = 5000;
  /**
   * The minimum default start position for live streams, relative to the start of the live window.
   */
  private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5_000_000;

  private static final String TAG = "DashMediaSource";

  private final MediaItem mediaItem;
  private final boolean sideloadedManifest;
  private final DataSource.Factory manifestDataSourceFactory;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final BaseUrlExclusionList baseUrlExclusionList;
  private final long fallbackTargetLiveOffsetMs;
  private final EventDispatcher manifestEventDispatcher;
  private final ParsingLoadable.Parser<? extends DashManifest> manifestParser;
  private final ManifestCallback manifestCallback;
  private final Object manifestUriLock;
  private final SparseArray<DashMediaPeriod> periodsById;
  private final Runnable refreshManifestRunnable;
  private final Runnable simulateManifestRefreshRunnable;
  private final PlayerEmsgCallback playerEmsgCallback;
  private final LoaderErrorThrower manifestLoadErrorThrower;

  private DataSource dataSource;
  private Loader loader;
  @Nullable private TransferListener mediaTransferListener;

  private IOException manifestFatalError;
  private Handler handler;

  private MediaItem.LiveConfiguration liveConfiguration;
  private Uri manifestUri;
  private Uri initialManifestUri;
  private DashManifest manifest;
  private boolean manifestLoadPending;
  private long manifestLoadStartTimestampMs;
  private long manifestLoadEndTimestampMs;
  private long elapsedRealtimeOffsetMs;

  private int staleManifestReloadAttempt;
  private long expiredManifestPublishTimeUs;

  private int firstPeriodId;

  private DashMediaSource(
      MediaItem mediaItem,
      @Nullable DashManifest manifest,
      @Nullable DataSource.Factory manifestDataSourceFactory,
      @Nullable ParsingLoadable.Parser<? extends DashManifest> manifestParser,
      DashChunkSource.Factory chunkSourceFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      long fallbackTargetLiveOffsetMs) {
    this.mediaItem = mediaItem;
    this.liveConfiguration = mediaItem.liveConfiguration;
    this.manifestUri = checkNotNull(mediaItem.localConfiguration).uri;
    this.initialManifestUri = mediaItem.localConfiguration.uri;
    this.manifest = manifest;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
    this.manifestParser = manifestParser;
    this.chunkSourceFactory = chunkSourceFactory;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.fallbackTargetLiveOffsetMs = fallbackTargetLiveOffsetMs;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    baseUrlExclusionList = new BaseUrlExclusionList();
    sideloadedManifest = manifest != null;
    manifestEventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
    manifestUriLock = new Object();
    periodsById = new SparseArray<>();
    playerEmsgCallback = new DefaultPlayerEmsgCallback();
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    elapsedRealtimeOffsetMs = C.TIME_UNSET;
    if (sideloadedManifest) {
      Assertions.checkState(!manifest.dynamic);
      manifestCallback = null;
      refreshManifestRunnable = null;
      simulateManifestRefreshRunnable = null;
      manifestLoadErrorThrower = new LoaderErrorThrower.Dummy();
    } else {
      manifestCallback = new ManifestCallback();
      manifestLoadErrorThrower = new ManifestLoadErrorThrower();
      refreshManifestRunnable = this::startLoadingManifest;
      simulateManifestRefreshRunnable = () -> processManifest(false);
    }
  }

  /**
   * Manually replaces the manifest {@link Uri}.
   *
   * @param manifestUri The replacement manifest {@link Uri}.
   */
  public void replaceManifestUri(Uri manifestUri) {
    synchronized (manifestUriLock) {
      this.manifestUri = manifestUri;
      this.initialManifestUri = manifestUri;
    }
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
      processManifest(false);
    } else {
      dataSource = manifestDataSourceFactory.createDataSource();
      loader = new Loader("DashMediaSource");
      handler = Util.createHandlerForCurrentLooper();
      startLoadingManifest();
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    manifestLoadErrorThrower.maybeThrowError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    int periodIndex = (Integer) id.periodUid - firstPeriodId;
    MediaSourceEventListener.EventDispatcher periodEventDispatcher =
        createEventDispatcher(id, manifest.getPeriod(periodIndex).startMs);
    DrmSessionEventListener.EventDispatcher drmEventDispatcher = createDrmEventDispatcher(id);
    DashMediaPeriod mediaPeriod =
        new DashMediaPeriod(
            firstPeriodId + periodIndex,
            manifest,
            baseUrlExclusionList,
            periodIndex,
            chunkSourceFactory,
            mediaTransferListener,
            drmSessionManager,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            periodEventDispatcher,
            elapsedRealtimeOffsetMs,
            manifestLoadErrorThrower,
            allocator,
            compositeSequenceableLoaderFactory,
            playerEmsgCallback,
            getPlayerId());
    periodsById.put(mediaPeriod.id, mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    DashMediaPeriod dashMediaPeriod = (DashMediaPeriod) mediaPeriod;
    dashMediaPeriod.release();
    periodsById.remove(dashMediaPeriod.id);
  }

  @Override
  protected void releaseSourceInternal() {
    manifestLoadPending = false;
    dataSource = null;
    if (loader != null) {
      loader.release();
      loader = null;
    }
    manifestLoadStartTimestampMs = 0;
    manifestLoadEndTimestampMs = 0;
    manifest = sideloadedManifest ? manifest : null;
    manifestUri = initialManifestUri;
    manifestFatalError = null;
    if (handler != null) {
      handler.removeCallbacksAndMessages(null);
      handler = null;
    }
    elapsedRealtimeOffsetMs = C.TIME_UNSET;
    staleManifestReloadAttempt = 0;
    expiredManifestPublishTimeUs = C.TIME_UNSET;
    firstPeriodId = 0;
    periodsById.clear();
    baseUrlExclusionList.reset();
    drmSessionManager.release();
  }

  // PlayerEmsgCallback callbacks.

  /* package */ void onDashManifestRefreshRequested() {
    handler.removeCallbacks(simulateManifestRefreshRunnable);
    startLoadingManifest();
  }

  /* package */ void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
    if (this.expiredManifestPublishTimeUs == C.TIME_UNSET
        || this.expiredManifestPublishTimeUs < expiredManifestPublishTimeUs) {
      this.expiredManifestPublishTimeUs = expiredManifestPublishTimeUs;
    }
  }

  // Loadable callbacks.

  /* package */ void onManifestLoadCompleted(
      ParsingLoadable<DashManifest> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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
    DashManifest newManifest = loadable.getResult();

    int oldPeriodCount = manifest == null ? 0 : manifest.getPeriodCount();
    int removedPeriodCount = 0;
    long newFirstPeriodStartTimeMs = newManifest.getPeriod(0).startMs;
    while (removedPeriodCount < oldPeriodCount
        && manifest.getPeriod(removedPeriodCount).startMs < newFirstPeriodStartTimeMs) {
      removedPeriodCount++;
    }

    if (newManifest.dynamic) {
      boolean isManifestStale = false;
      if (oldPeriodCount - removedPeriodCount > newManifest.getPeriodCount()) {
        // After discarding old periods, we should never have more periods than listed in the new
        // manifest. That would mean that a previously announced period is no longer advertised. If
        // this condition occurs, assume that we are hitting a manifest server that is out of sync
        // and
        // behind.
        Log.w(TAG, "Loaded out of sync manifest");
        isManifestStale = true;
      } else if (expiredManifestPublishTimeUs != C.TIME_UNSET
          && newManifest.publishTimeMs * 1000 <= expiredManifestPublishTimeUs) {
        // If we receive a dynamic manifest that's older than expected (i.e. its publish time has
        // expired, or it's dynamic and we know the presentation has ended), then this manifest is
        // stale.
        Log.w(
            TAG,
            "Loaded stale dynamic manifest: "
                + newManifest.publishTimeMs
                + ", "
                + expiredManifestPublishTimeUs);
        isManifestStale = true;
      }

      if (isManifestStale) {
        if (staleManifestReloadAttempt++
            < loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type)) {
          scheduleManifestRefresh(getManifestLoadRetryDelayMillis());
        } else {
          manifestFatalError = new DashManifestStaleException();
        }
        return;
      }
      staleManifestReloadAttempt = 0;
    }

    manifest = newManifest;
    manifestLoadPending &= manifest.dynamic;
    manifestLoadStartTimestampMs = elapsedRealtimeMs - loadDurationMs;
    manifestLoadEndTimestampMs = elapsedRealtimeMs;

    synchronized (manifestUriLock) {
      // Checks whether replaceManifestUri(Uri) was called to manually replace the URI between the
      // start and end of this load. If it was then isSameUriInstance evaluates to false, and we
      // prefer the manual replacement to one derived from the previous request.
      @SuppressWarnings("ReferenceEquality")
      boolean isSameUriInstance = loadable.dataSpec.uri == manifestUri;
      if (isSameUriInstance) {
        // Replace the manifest URI with one specified by a manifest Location element (if present),
        // or with the final (possibly redirected) URI. This follows the recommendation in
        // DASH-IF-IOP 4.3, section 3.2.15.3. See: https://dashif.org/docs/DASH-IF-IOP-v4.3.pdf.
        manifestUri = manifest.location != null ? manifest.location : loadable.getUri();
      }
    }

    if (oldPeriodCount == 0) {
      if (manifest.dynamic) {
        if (manifest.utcTiming != null) {
          resolveUtcTimingElement(manifest.utcTiming);
        } else {
          loadNtpTimeOffset();
        }
      } else {
        processManifest(true);
      }
    } else {
      firstPeriodId += removedPeriodCount;
      processManifest(true);
    }
  }

  /* package */ LoadErrorAction onManifestLoadError(
      ParsingLoadable<DashManifest> loadable,
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
    LoadErrorInfo loadErrorInfo =
        new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount);
    long retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(loadErrorInfo);
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

  /* package */ void onUtcTimestampLoadCompleted(
      ParsingLoadable<Long> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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
    onUtcTimestampResolved(loadable.getResult() - elapsedRealtimeMs);
  }

  /* package */ LoadErrorAction onUtcTimestampLoadError(
      ParsingLoadable<Long> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error) {
    manifestEventDispatcher.loadError(
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded()),
        loadable.type,
        error,
        /* wasCanceled= */ true);
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    onUtcTimestampResolutionError(error);
    return Loader.DONT_RETRY;
  }

  /* package */ void onLoadCanceled(
      ParsingLoadable<?> loadable, long elapsedRealtimeMs, long loadDurationMs) {
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

  // Internal methods.

  private void resolveUtcTimingElement(UtcTimingElement timingElement) {
    String scheme = timingElement.schemeIdUri;
    if (Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2012")) {
      resolveUtcTimingElementDirect(timingElement);
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2012")) {
      resolveUtcTimingElementHttp(timingElement, new Iso8601Parser());
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2012")) {
      resolveUtcTimingElementHttp(timingElement, new XsDateTimeParser());
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:ntp:2014")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:ntp:2012")) {
      loadNtpTimeOffset();
    } else {
      // Unsupported scheme.
      onUtcTimestampResolutionError(new IOException("Unsupported UTC timing scheme"));
    }
  }

  private void resolveUtcTimingElementDirect(UtcTimingElement timingElement) {
    try {
      long utcTimestampMs = Util.parseXsDateTime(timingElement.value);
      onUtcTimestampResolved(utcTimestampMs - manifestLoadEndTimestampMs);
    } catch (ParserException e) {
      onUtcTimestampResolutionError(e);
    }
  }

  private void resolveUtcTimingElementHttp(
      UtcTimingElement timingElement, ParsingLoadable.Parser<Long> parser) {
    startLoading(
        new ParsingLoadable<>(
            dataSource, Uri.parse(timingElement.value), C.DATA_TYPE_TIME_SYNCHRONIZATION, parser),
        new UtcTimestampCallback(),
        1);
  }

  private void loadNtpTimeOffset() {
    SntpClient.initialize(
        loader,
        new SntpClient.InitializationCallback() {
          @Override
          public void onInitialized() {
            onUtcTimestampResolved(SntpClient.getElapsedRealtimeOffsetMs());
          }

          @Override
          public void onInitializationFailed(IOException error) {
            onUtcTimestampResolutionError(error);
          }
        });
  }

  private void onUtcTimestampResolved(long elapsedRealtimeOffsetMs) {
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    processManifest(true);
  }

  private void onUtcTimestampResolutionError(IOException error) {
    Log.e(TAG, "Failed to resolve time offset.", error);
    // Be optimistic and continue in the hope that the device clock is correct.
    processManifest(true);
  }

  private void processManifest(boolean scheduleRefresh) {
    // Update any periods.
    for (int i = 0; i < periodsById.size(); i++) {
      int id = periodsById.keyAt(i);
      if (id >= firstPeriodId) {
        periodsById.valueAt(i).updateManifest(manifest, id - firstPeriodId);
      } else {
        // This period has been removed from the manifest so it doesn't need to be updated.
      }
    }
    // Update the window.
    Period firstPeriod = manifest.getPeriod(0);
    int lastPeriodIndex = manifest.getPeriodCount() - 1;
    Period lastPeriod = manifest.getPeriod(lastPeriodIndex);
    long lastPeriodDurationUs = manifest.getPeriodDurationUs(lastPeriodIndex);
    long nowUnixTimeUs = Util.msToUs(Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs));
    long windowStartTimeInManifestUs =
        getAvailableStartTimeInManifestUs(
            firstPeriod, manifest.getPeriodDurationUs(0), nowUnixTimeUs);
    long windowEndTimeInManifestUs =
        getAvailableEndTimeInManifestUs(lastPeriod, lastPeriodDurationUs, nowUnixTimeUs);
    boolean windowChangingImplicitly = manifest.dynamic && !isIndexExplicit(lastPeriod);
    if (windowChangingImplicitly && manifest.timeShiftBufferDepthMs != C.TIME_UNSET) {
      // Update the available start time to reflect the manifest's time shift buffer depth.
      long timeShiftBufferStartTimeInManifestUs =
          windowEndTimeInManifestUs - Util.msToUs(manifest.timeShiftBufferDepthMs);
      windowStartTimeInManifestUs =
          max(windowStartTimeInManifestUs, timeShiftBufferStartTimeInManifestUs);
    }
    long windowDurationUs = windowEndTimeInManifestUs - windowStartTimeInManifestUs;
    long windowStartUnixTimeMs = C.TIME_UNSET;
    long windowDefaultPositionUs = 0;
    if (manifest.dynamic) {
      checkState(manifest.availabilityStartTimeMs != C.TIME_UNSET);
      long nowInWindowUs =
          nowUnixTimeUs
              - Util.msToUs(manifest.availabilityStartTimeMs)
              - windowStartTimeInManifestUs;
      updateLiveConfiguration(nowInWindowUs, windowDurationUs);
      windowStartUnixTimeMs =
          manifest.availabilityStartTimeMs + Util.usToMs(windowStartTimeInManifestUs);
      windowDefaultPositionUs = nowInWindowUs - Util.msToUs(liveConfiguration.targetOffsetMs);
      long minimumWindowDefaultPositionUs =
          min(MIN_LIVE_DEFAULT_START_POSITION_US, windowDurationUs / 2);
      if (windowDefaultPositionUs < minimumWindowDefaultPositionUs) {
        // The default position is too close to the start of the live window. Set it to the minimum
        // default position provided the window is at least twice as big. Else set it to the middle
        // of the window.
        windowDefaultPositionUs = minimumWindowDefaultPositionUs;
      }
    }
    long offsetInFirstPeriodUs = windowStartTimeInManifestUs - Util.msToUs(firstPeriod.startMs);
    DashTimeline timeline =
        new DashTimeline(
            manifest.availabilityStartTimeMs,
            windowStartUnixTimeMs,
            elapsedRealtimeOffsetMs,
            firstPeriodId,
            offsetInFirstPeriodUs,
            windowDurationUs,
            windowDefaultPositionUs,
            manifest,
            mediaItem,
            manifest.dynamic ? liveConfiguration : null);
    refreshSourceInfo(timeline);

    if (!sideloadedManifest) {
      // Remove any pending simulated refresh.
      handler.removeCallbacks(simulateManifestRefreshRunnable);
      // If the window is changing implicitly, post a simulated manifest refresh to update it.
      if (windowChangingImplicitly) {
        handler.postDelayed(
            simulateManifestRefreshRunnable,
            getIntervalUntilNextManifestRefreshMs(
                manifest, Util.getNowUnixTimeMs(elapsedRealtimeOffsetMs)));
      }
      if (manifestLoadPending) {
        startLoadingManifest();
      } else if (scheduleRefresh
          && manifest.dynamic
          && manifest.minUpdatePeriodMs != C.TIME_UNSET) {
        // Schedule an explicit refresh if needed.
        long minUpdatePeriodMs = manifest.minUpdatePeriodMs;
        if (minUpdatePeriodMs == 0) {
          // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
          // minimumUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is
          // explicit signaling in the stream, according to:
          // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service
          minUpdatePeriodMs = 5000;
        }
        long nextLoadTimestampMs = manifestLoadStartTimestampMs + minUpdatePeriodMs;
        long delayUntilNextLoadMs = max(0, nextLoadTimestampMs - SystemClock.elapsedRealtime());
        scheduleManifestRefresh(delayUntilNextLoadMs);
      }
    }
  }

  private void updateLiveConfiguration(long nowInWindowUs, long windowDurationUs) {
    // Default maximum offset: start of window.
    long maxPossibleLiveOffsetMs = usToMs(nowInWindowUs);
    long maxLiveOffsetMs = maxPossibleLiveOffsetMs;
    // Override maximum offset with user or media defined values if they are smaller.
    if (mediaItem.liveConfiguration.maxOffsetMs != C.TIME_UNSET) {
      maxLiveOffsetMs = min(maxLiveOffsetMs, mediaItem.liveConfiguration.maxOffsetMs);
    } else if (manifest.serviceDescription != null
        && manifest.serviceDescription.maxOffsetMs != C.TIME_UNSET) {
      maxLiveOffsetMs = min(maxLiveOffsetMs, manifest.serviceDescription.maxOffsetMs);
    }
    // Default minimum offset: end of window.
    long minLiveOffsetMs = usToMs(nowInWindowUs - windowDurationUs);
    if (minLiveOffsetMs < 0 && maxLiveOffsetMs > 0) {
      // The current time is in the window, so assume all clocks are synchronized and set the
      // minimum to a live offset of zero.
      minLiveOffsetMs = 0;
    }
    if (manifest.minBufferTimeMs != C.TIME_UNSET) {
      // Ensure to leave one GOP as minimum and don't exceed the maximum possible offset.
      minLiveOffsetMs = min(minLiveOffsetMs + manifest.minBufferTimeMs, maxPossibleLiveOffsetMs);
    }
    // Override minimum offset with user and media defined values if they are larger, but don't
    // exceed the maximum possible offset.
    if (mediaItem.liveConfiguration.minOffsetMs != C.TIME_UNSET) {
      minLiveOffsetMs =
          constrainValue(
              mediaItem.liveConfiguration.minOffsetMs, minLiveOffsetMs, maxPossibleLiveOffsetMs);
    } else if (manifest.serviceDescription != null
        && manifest.serviceDescription.minOffsetMs != C.TIME_UNSET) {
      minLiveOffsetMs =
          constrainValue(
              manifest.serviceDescription.minOffsetMs, minLiveOffsetMs, maxPossibleLiveOffsetMs);
    }
    if (minLiveOffsetMs > maxLiveOffsetMs) {
      // The values can be set by different sources and may disagree. Prefer the maximum offset
      // under the assumption that it is safer for playback.
      maxLiveOffsetMs = minLiveOffsetMs;
    }
    long targetOffsetMs;
    if (liveConfiguration.targetOffsetMs != C.TIME_UNSET) {
      // Keep existing target offset even if the media configuration changes.
      targetOffsetMs = liveConfiguration.targetOffsetMs;
    } else if (manifest.serviceDescription != null
        && manifest.serviceDescription.targetOffsetMs != C.TIME_UNSET) {
      targetOffsetMs = manifest.serviceDescription.targetOffsetMs;
    } else if (manifest.suggestedPresentationDelayMs != C.TIME_UNSET) {
      targetOffsetMs = manifest.suggestedPresentationDelayMs;
    } else {
      targetOffsetMs = fallbackTargetLiveOffsetMs;
    }
    if (targetOffsetMs < minLiveOffsetMs) {
      targetOffsetMs = minLiveOffsetMs;
    }
    if (targetOffsetMs > maxLiveOffsetMs) {
      long safeDistanceFromWindowStartUs =
          min(MIN_LIVE_DEFAULT_START_POSITION_US, windowDurationUs / 2);
      long maxTargetOffsetForSafeDistanceToWindowStartMs =
          usToMs(nowInWindowUs - safeDistanceFromWindowStartUs);
      targetOffsetMs =
          constrainValue(
              maxTargetOffsetForSafeDistanceToWindowStartMs, minLiveOffsetMs, maxLiveOffsetMs);
    }
    float minPlaybackSpeed = C.RATE_UNSET;
    if (mediaItem.liveConfiguration.minPlaybackSpeed != C.RATE_UNSET) {
      minPlaybackSpeed = mediaItem.liveConfiguration.minPlaybackSpeed;
    } else if (manifest.serviceDescription != null) {
      minPlaybackSpeed = manifest.serviceDescription.minPlaybackSpeed;
    }
    float maxPlaybackSpeed = C.RATE_UNSET;
    if (mediaItem.liveConfiguration.maxPlaybackSpeed != C.RATE_UNSET) {
      maxPlaybackSpeed = mediaItem.liveConfiguration.maxPlaybackSpeed;
    } else if (manifest.serviceDescription != null) {
      maxPlaybackSpeed = manifest.serviceDescription.maxPlaybackSpeed;
    }
    if (minPlaybackSpeed == C.RATE_UNSET
        && maxPlaybackSpeed == C.RATE_UNSET
        && (manifest.serviceDescription == null
            || manifest.serviceDescription.targetOffsetMs == C.TIME_UNSET)) {
      // Force unit speed (instead of automatic adjustment with fallback speeds) if there are no
      // specific speed limits defined by the media item or the manifest, and the manifest contains
      // no low-latency target offset either.
      minPlaybackSpeed = 1f;
      maxPlaybackSpeed = 1f;
    }
    liveConfiguration =
        new MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(targetOffsetMs)
            .setMinOffsetMs(minLiveOffsetMs)
            .setMaxOffsetMs(maxLiveOffsetMs)
            .setMinPlaybackSpeed(minPlaybackSpeed)
            .setMaxPlaybackSpeed(maxPlaybackSpeed)
            .build();
  }

  private void scheduleManifestRefresh(long delayUntilNextLoadMs) {
    handler.postDelayed(refreshManifestRunnable, delayUntilNextLoadMs);
  }

  private void startLoadingManifest() {
    handler.removeCallbacks(refreshManifestRunnable);
    if (loader.hasFatalError()) {
      return;
    }
    if (loader.isLoading()) {
      manifestLoadPending = true;
      return;
    }
    Uri manifestUri;
    synchronized (manifestUriLock) {
      manifestUri = this.manifestUri;
    }
    manifestLoadPending = false;
    startLoading(
        new ParsingLoadable<>(dataSource, manifestUri, C.DATA_TYPE_MANIFEST, manifestParser),
        manifestCallback,
        loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST));
  }

  private long getManifestLoadRetryDelayMillis() {
    return min((staleManifestReloadAttempt - 1) * 1000, 5000);
  }

  private <T> void startLoading(
      ParsingLoadable<T> loadable,
      Loader.Callback<ParsingLoadable<T>> callback,
      int minRetryCount) {
    long elapsedRealtimeMs = loader.startLoading(loadable, callback, minRetryCount);
    manifestEventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, loadable.dataSpec, elapsedRealtimeMs),
        loadable.type);
  }

  private static long getIntervalUntilNextManifestRefreshMs(
      DashManifest manifest, long nowUnixTimeMs) {
    int periodIndex = manifest.getPeriodCount() - 1;
    Period period = manifest.getPeriod(periodIndex);
    long periodStartUs = Util.msToUs(period.startMs);
    long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
    long nowUnixTimeUs = Util.msToUs(nowUnixTimeMs);
    long availabilityStartTimeUs = Util.msToUs(manifest.availabilityStartTimeMs);
    long intervalUs = Util.msToUs(DEFAULT_NOTIFY_MANIFEST_INTERVAL_MS);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      List<Representation> representations = period.adaptationSets.get(i).representations;
      if (representations.isEmpty()) {
        continue;
      }
      @Nullable DashSegmentIndex index = representations.get(0).getIndex();
      if (index != null) {
        long nextSegmentShiftUnixTimeUs =
            availabilityStartTimeUs
                + periodStartUs
                + index.getNextSegmentAvailableTimeUs(periodDurationUs, nowUnixTimeUs);
        long requiredIntervalUs = nextSegmentShiftUnixTimeUs - nowUnixTimeUs;
        // Avoid multiple refreshes within a very small amount of time.
        if (requiredIntervalUs < intervalUs - 100_000
            || (requiredIntervalUs > intervalUs && requiredIntervalUs < intervalUs + 100_000)) {
          intervalUs = requiredIntervalUs;
        }
      }
    }
    // Round up to compensate for a potential loss in the us to ms conversion.
    return LongMath.divide(intervalUs, 1000, RoundingMode.CEILING);
  }

  private static long getAvailableStartTimeInManifestUs(
      Period period, long periodDurationUs, long nowUnixTimeUs) {
    long periodStartTimeInManifestUs = Util.msToUs(period.startMs);
    long availableStartTimeInManifestUs = periodStartTimeInManifestUs;
    boolean haveAudioVideoAdaptationSets = hasVideoOrAudioAdaptationSets(period);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      List<Representation> representations = adaptationSet.representations;
      // Exclude other adaptation sets from duration calculations, if we have at least one audio or
      // video adaptation set. See: https://github.com/google/ExoPlayer/issues/4029.
      boolean adaptationSetIsNotAudioVideo =
          adaptationSet.type != C.TRACK_TYPE_AUDIO && adaptationSet.type != C.TRACK_TYPE_VIDEO;
      if ((haveAudioVideoAdaptationSets && adaptationSetIsNotAudioVideo)
          || representations.isEmpty()) {
        continue;
      }
      @Nullable DashSegmentIndex index = representations.get(0).getIndex();
      if (index == null) {
        return periodStartTimeInManifestUs;
      }
      long availableSegmentCount = index.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
      if (availableSegmentCount == 0) {
        return periodStartTimeInManifestUs;
      }
      long firstAvailableSegmentNum =
          index.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
      long adaptationSetAvailableStartTimeInManifestUs =
          periodStartTimeInManifestUs + index.getTimeUs(firstAvailableSegmentNum);
      availableStartTimeInManifestUs =
          max(availableStartTimeInManifestUs, adaptationSetAvailableStartTimeInManifestUs);
    }
    return availableStartTimeInManifestUs;
  }

  private static long getAvailableEndTimeInManifestUs(
      Period period, long periodDurationUs, long nowUnixTimeUs) {
    long periodStartTimeInManifestUs = Util.msToUs(period.startMs);
    long availableEndTimeInManifestUs = Long.MAX_VALUE;
    boolean haveAudioVideoAdaptationSets = hasVideoOrAudioAdaptationSets(period);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      List<Representation> representations = adaptationSet.representations;
      // Exclude other adaptation sets from duration calculations, if we have at least one audio or
      // video adaptation set. See: https://github.com/google/ExoPlayer/issues/4029
      boolean adaptationSetIsNotAudioVideo =
          adaptationSet.type != C.TRACK_TYPE_AUDIO && adaptationSet.type != C.TRACK_TYPE_VIDEO;
      if ((haveAudioVideoAdaptationSets && adaptationSetIsNotAudioVideo)
          || representations.isEmpty()) {
        continue;
      }
      @Nullable DashSegmentIndex index = representations.get(0).getIndex();
      if (index == null) {
        return periodStartTimeInManifestUs + periodDurationUs;
      }
      long availableSegmentCount = index.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
      if (availableSegmentCount == 0) {
        return periodStartTimeInManifestUs;
      }
      long firstAvailableSegmentNum =
          index.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
      long lastAvailableSegmentNum = firstAvailableSegmentNum + availableSegmentCount - 1;
      long adaptationSetAvailableEndTimeInManifestUs =
          periodStartTimeInManifestUs
              + index.getTimeUs(lastAvailableSegmentNum)
              + index.getDurationUs(lastAvailableSegmentNum, periodDurationUs);
      availableEndTimeInManifestUs =
          min(availableEndTimeInManifestUs, adaptationSetAvailableEndTimeInManifestUs);
    }
    return availableEndTimeInManifestUs;
  }

  private static boolean isIndexExplicit(Period period) {
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      @Nullable
      DashSegmentIndex index = period.adaptationSets.get(i).representations.get(0).getIndex();
      if (index == null || index.isExplicit()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasVideoOrAudioAdaptationSets(Period period) {
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      int type = period.adaptationSets.get(i).type;
      if (type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO) {
        return true;
      }
    }
    return false;
  }

  private static final class DashTimeline extends Timeline {

    private final long presentationStartTimeMs;
    private final long windowStartTimeMs;
    private final long elapsedRealtimeEpochOffsetMs;

    private final int firstPeriodId;
    private final long offsetInFirstPeriodUs;
    private final long windowDurationUs;
    private final long windowDefaultStartPositionUs;
    private final DashManifest manifest;
    private final MediaItem mediaItem;
    @Nullable private final MediaItem.LiveConfiguration liveConfiguration;

    public DashTimeline(
        long presentationStartTimeMs,
        long windowStartTimeMs,
        long elapsedRealtimeEpochOffsetMs,
        int firstPeriodId,
        long offsetInFirstPeriodUs,
        long windowDurationUs,
        long windowDefaultStartPositionUs,
        DashManifest manifest,
        MediaItem mediaItem,
        @Nullable MediaItem.LiveConfiguration liveConfiguration) {
      checkState(manifest.dynamic == (liveConfiguration != null));
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
      this.firstPeriodId = firstPeriodId;
      this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
      this.windowDurationUs = windowDurationUs;
      this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
      this.manifest = manifest;
      this.mediaItem = mediaItem;
      this.liveConfiguration = liveConfiguration;
    }

    @Override
    public int getPeriodCount() {
      return manifest.getPeriodCount();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      Assertions.checkIndex(periodIndex, 0, getPeriodCount());
      Object id = setIds ? manifest.getPeriod(periodIndex).id : null;
      Object uid = setIds ? (firstPeriodId + periodIndex) : null;
      return period.set(
          id,
          uid,
          0,
          manifest.getPeriodDurationUs(periodIndex),
          Util.msToUs(manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs)
              - offsetInFirstPeriodUs);
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      Assertions.checkIndex(windowIndex, 0, 1);
      long windowDefaultStartPositionUs =
          getAdjustedWindowDefaultStartPositionUs(defaultPositionProjectionUs);
      return window.set(
          Window.SINGLE_WINDOW_UID,
          mediaItem,
          manifest,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          /* isSeekable= */ true,
          /* isDynamic= */ isMovingLiveWindow(manifest),
          liveConfiguration,
          windowDefaultStartPositionUs,
          windowDurationUs,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ getPeriodCount() - 1,
          offsetInFirstPeriodUs);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      if (!(uid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int periodId = (int) uid;
      int periodIndex = periodId - firstPeriodId;
      return periodIndex < 0 || periodIndex >= getPeriodCount() ? C.INDEX_UNSET : periodIndex;
    }

    private long getAdjustedWindowDefaultStartPositionUs(long defaultPositionProjectionUs) {
      long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
      if (!isMovingLiveWindow(manifest)) {
        return windowDefaultStartPositionUs;
      }
      if (defaultPositionProjectionUs > 0) {
        windowDefaultStartPositionUs += defaultPositionProjectionUs;
        if (windowDefaultStartPositionUs > windowDurationUs) {
          // The projection takes us beyond the end of the live window.
          return C.TIME_UNSET;
        }
      }
      // Attempt to snap to the start of the corresponding video segment.
      int periodIndex = 0;
      long defaultStartPositionInPeriodUs = offsetInFirstPeriodUs + windowDefaultStartPositionUs;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      while (periodIndex < manifest.getPeriodCount() - 1
          && defaultStartPositionInPeriodUs >= periodDurationUs) {
        defaultStartPositionInPeriodUs -= periodDurationUs;
        periodIndex++;
        periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      }
      com.google.android.exoplayer2.source.dash.manifest.Period period =
          manifest.getPeriod(periodIndex);
      int videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
      if (videoAdaptationSetIndex == C.INDEX_UNSET) {
        // No video adaptation set for snapping.
        return windowDefaultStartPositionUs;
      }
      // If there are multiple video adaptation sets with unaligned segments, the initial time may
      // not correspond to the start of a segment in both, but this is an edge case.
      @Nullable
      DashSegmentIndex snapIndex =
          period.adaptationSets.get(videoAdaptationSetIndex).representations.get(0).getIndex();
      if (snapIndex == null || snapIndex.getSegmentCount(periodDurationUs) == 0) {
        // Video adaptation set does not include a non-empty index for snapping.
        return windowDefaultStartPositionUs;
      }
      long segmentNum = snapIndex.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs);
      return windowDefaultStartPositionUs
          + snapIndex.getTimeUs(segmentNum)
          - defaultStartPositionInPeriodUs;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Assertions.checkIndex(periodIndex, 0, getPeriodCount());
      return firstPeriodId + periodIndex;
    }

    private static boolean isMovingLiveWindow(DashManifest manifest) {
      return manifest.dynamic
          && manifest.minUpdatePeriodMs != C.TIME_UNSET
          && manifest.durationMs == C.TIME_UNSET;
    }
  }

  private final class DefaultPlayerEmsgCallback implements PlayerEmsgCallback {

    @Override
    public void onDashManifestRefreshRequested() {
      DashMediaSource.this.onDashManifestRefreshRequested();
    }

    @Override
    public void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
      DashMediaSource.this.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
    }
  }

  private final class ManifestCallback implements Loader.Callback<ParsingLoadable<DashManifest>> {

    @Override
    public void onLoadCompleted(
        ParsingLoadable<DashManifest> loadable, long elapsedRealtimeMs, long loadDurationMs) {
      onManifestLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(
        ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<DashManifest> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return onManifestLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error, errorCount);
    }
  }

  private final class UtcTimestampCallback implements Loader.Callback<ParsingLoadable<Long>> {

    @Override
    public void onLoadCompleted(
        ParsingLoadable<Long> loadable, long elapsedRealtimeMs, long loadDurationMs) {
      onUtcTimestampLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public void onLoadCanceled(
        ParsingLoadable<Long> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      DashMediaSource.this.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<Long> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      return onUtcTimestampLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error);
    }
  }

  private static final class XsDateTimeParser implements ParsingLoadable.Parser<Long> {

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
      return Util.parseXsDateTime(firstLine);
    }
  }

  /* package */ static final class Iso8601Parser implements ParsingLoadable.Parser<Long> {

    private static final Pattern TIMESTAMP_WITH_TIMEZONE_PATTERN =
        Pattern.compile("(.+?)(Z|((\\+|-|−)(\\d\\d)(:?(\\d\\d))?))");

    @Override
    public Long parse(Uri uri, InputStream inputStream) throws IOException {
      String firstLine =
          new BufferedReader(new InputStreamReader(inputStream, Charsets.UTF_8)).readLine();
      try {
        Matcher matcher = TIMESTAMP_WITH_TIMEZONE_PATTERN.matcher(firstLine);
        if (!matcher.matches()) {
          throw ParserException.createForMalformedManifest(
              "Couldn't parse timestamp: " + firstLine, /* cause= */ null);
        }
        // Parse the timestamp.
        String timestampWithoutTimezone = matcher.group(1);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long timestampMs = format.parse(timestampWithoutTimezone).getTime();
        // Parse the timezone.
        String timezone = matcher.group(2);
        if ("Z".equals(timezone)) {
          // UTC (no offset).
        } else {
          long sign = "+".equals(matcher.group(4)) ? 1 : -1;
          long hours = Long.parseLong(matcher.group(5));
          String minutesString = matcher.group(7);
          long minutes = TextUtils.isEmpty(minutesString) ? 0 : Long.parseLong(minutesString);
          long timestampOffsetMs = sign * (((hours * 60) + minutes) * 60 * 1000);
          timestampMs -= timestampOffsetMs;
        }
        return timestampMs;
      } catch (ParseException e) {
        throw ParserException.createForMalformedManifest(/* message= */ null, /* cause= */ e);
      }
    }
  }

  /**
   * A {@link LoaderErrorThrower} that throws fatal {@link IOException} that has occurred during
   * manifest loading from the manifest {@code loader}, or exception with the loaded manifest.
   */
  /* package */ final class ManifestLoadErrorThrower implements LoaderErrorThrower {

    @Override
    public void maybeThrowError() throws IOException {
      loader.maybeThrowError();
      maybeThrowManifestError();
    }

    @Override
    public void maybeThrowError(int minRetryCount) throws IOException {
      loader.maybeThrowError(minRetryCount);
      maybeThrowManifestError();
    }

    private void maybeThrowManifestError() throws IOException {
      if (manifestFatalError != null) {
        throw manifestFatalError;
      }
    }
  }
}
