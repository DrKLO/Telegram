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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Provides one period that loads data from a {@link Uri} and extracted using an {@link Extractor}.
 *
 * <p>If the possible input stream container formats are known, pass a factory that instantiates
 * extractors for them to the constructor. Otherwise, pass a {@link DefaultExtractorsFactory} to use
 * the default extractors. When reading a new stream, the first {@link Extractor} in the array of
 * extractors created by the factory that returns {@code true} from {@link Extractor#sniff} will be
 * used to extract samples from the input stream.
 *
 * <p>Note that the built-in extractor for FLV streams does not support seeking.
 */
public final class ProgressiveMediaSource extends BaseMediaSource
    implements ProgressiveMediaPeriod.Listener {

  /** Factory for {@link ProgressiveMediaSource}s. */
  @SuppressWarnings("deprecation") // Implement deprecated type for backwards compatibility.
  public static final class Factory implements MediaSourceFactory {

    private final DataSource.Factory dataSourceFactory;

    private ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory;
    private DrmSessionManagerProvider drmSessionManagerProvider;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private int continueLoadingCheckIntervalBytes;
    @Nullable private String customCacheKey;
    @Nullable private Object tag;

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultExtractorsFactory}
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     * </ul>
     *
     * @param dataSourceFactory A factory for {@linkplain DataSource data sources} to read the
     *     media.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(dataSourceFactory, new DefaultExtractorsFactory());
    }

    /**
     * Equivalent to {@link #Factory(DataSource.Factory, ProgressiveMediaExtractor.Factory) new
     * Factory(dataSourceFactory, () -> new BundledExtractorsAdapter(extractorsFactory)}.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     * </ul>
     *
     * @param dataSourceFactory A factory for {@linkplain DataSource data sources} to read the
     *     media.
     * @param extractorsFactory A factory for the {@linkplain Extractor extractors} used to extract
     *     the media from its container.
     */
    public Factory(DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory) {
      this(dataSourceFactory, playerId -> new BundledExtractorsAdapter(extractorsFactory));
    }

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s.
     *
     * <p>The factory will use the following default components:
     *
     * <ul>
     *   <li>{@link DefaultDrmSessionManagerProvider}
     *   <li>{@link DefaultLoadErrorHandlingPolicy}
     * </ul>
     *
     * @param dataSourceFactory A factory for {@linkplain DataSource data sources} to read the
     *     media.
     * @param progressiveMediaExtractorFactory A factory for the {@link ProgressiveMediaExtractor}
     *     to extract the media from its container.
     */
    public Factory(
        DataSource.Factory dataSourceFactory,
        ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory) {
      this(
          dataSourceFactory,
          progressiveMediaExtractorFactory,
          new DefaultDrmSessionManagerProvider(),
          new DefaultLoadErrorHandlingPolicy(),
          DEFAULT_LOADING_CHECK_INTERVAL_BYTES);
    }

    /**
     * Creates a new factory for {@link ProgressiveMediaSource}s.
     *
     * @param dataSourceFactory A factory for {@linkplain DataSource data sources} to read the
     *     media.
     * @param progressiveMediaExtractorFactory A factory for the {@link ProgressiveMediaExtractor}
     *     to extract media from its container.
     * @param drmSessionManagerProvider A provider to obtain a {@link DrmSessionManager} for a
     *     {@link MediaItem}.
     * @param loadErrorHandlingPolicy A policy to handle load error.
     * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between
     *     each invocation of {@link
     *     MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
     */
    public Factory(
        DataSource.Factory dataSourceFactory,
        ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory,
        DrmSessionManagerProvider drmSessionManagerProvider,
        LoadErrorHandlingPolicy loadErrorHandlingPolicy,
        int continueLoadingCheckIntervalBytes) {
      this.dataSourceFactory = dataSourceFactory;
      this.progressiveMediaExtractorFactory = progressiveMediaExtractorFactory;
      this.drmSessionManagerProvider = drmSessionManagerProvider;
      this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
      this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
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
     * Sets the number of bytes that should be loaded between each invocation of {@link
     * MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}. The default value is
     * {@link #DEFAULT_LOADING_CHECK_INTERVAL_BYTES}.
     *
     * @param continueLoadingCheckIntervalBytes The number of bytes that should be loaded between
     *     each invocation of {@link
     *     MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setContinueLoadingCheckIntervalBytes(int continueLoadingCheckIntervalBytes) {
      this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
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
     * Returns a new {@link ProgressiveMediaSource} using the current parameters.
     *
     * @param mediaItem The {@link MediaItem}.
     * @return The new {@link ProgressiveMediaSource}.
     * @throws NullPointerException if {@link MediaItem#localConfiguration} is {@code null}.
     */
    @Override
    public ProgressiveMediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      boolean needsTag = mediaItem.localConfiguration.tag == null && tag != null;
      boolean needsCustomCacheKey =
          mediaItem.localConfiguration.customCacheKey == null && customCacheKey != null;
      if (needsTag && needsCustomCacheKey) {
        mediaItem = mediaItem.buildUpon().setTag(tag).setCustomCacheKey(customCacheKey).build();
      } else if (needsTag) {
        mediaItem = mediaItem.buildUpon().setTag(tag).build();
      } else if (needsCustomCacheKey) {
        mediaItem = mediaItem.buildUpon().setCustomCacheKey(customCacheKey).build();
      }
      return new ProgressiveMediaSource(
          mediaItem,
          dataSourceFactory,
          progressiveMediaExtractorFactory,
          drmSessionManagerProvider.get(mediaItem),
          loadErrorHandlingPolicy,
          continueLoadingCheckIntervalBytes);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_OTHER};
    }
  }

  /**
   * The default number of bytes that should be loaded between each each invocation of {@link
   * MediaPeriod.Callback#onContinueLoadingRequested(SequenceableLoader)}.
   */
  public static final int DEFAULT_LOADING_CHECK_INTERVAL_BYTES = 1024 * 1024;

  private final MediaItem mediaItem;
  private final MediaItem.LocalConfiguration localConfiguration;
  private final DataSource.Factory dataSourceFactory;
  private final ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy;
  private final int continueLoadingCheckIntervalBytes;

  private boolean timelineIsPlaceholder;
  private long timelineDurationUs;
  private boolean timelineIsSeekable;
  private boolean timelineIsLive;
  @Nullable private TransferListener transferListener;

  private ProgressiveMediaSource(
      MediaItem mediaItem,
      DataSource.Factory dataSourceFactory,
      ProgressiveMediaExtractor.Factory progressiveMediaExtractorFactory,
      DrmSessionManager drmSessionManager,
      LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy,
      int continueLoadingCheckIntervalBytes) {
    this.localConfiguration = checkNotNull(mediaItem.localConfiguration);
    this.mediaItem = mediaItem;
    this.dataSourceFactory = dataSourceFactory;
    this.progressiveMediaExtractorFactory = progressiveMediaExtractorFactory;
    this.drmSessionManager = drmSessionManager;
    this.loadableLoadErrorHandlingPolicy = loadableLoadErrorHandlingPolicy;
    this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
    this.timelineIsPlaceholder = true;
    this.timelineDurationUs = C.TIME_UNSET;
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    transferListener = mediaTransferListener;
    drmSessionManager.prepare();
    drmSessionManager.setPlayer(
        /* playbackLooper= */ checkNotNull(Looper.myLooper()), getPlayerId());
    notifySourceInfoRefreshed();
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    DataSource dataSource = dataSourceFactory.createDataSource();
    if (transferListener != null) {
      dataSource.addTransferListener(transferListener);
    }
    return new ProgressiveMediaPeriod(
        localConfiguration.uri,
        dataSource,
        progressiveMediaExtractorFactory.createProgressiveMediaExtractor(getPlayerId()),
        drmSessionManager,
        createDrmEventDispatcher(id),
        loadableLoadErrorHandlingPolicy,
        createEventDispatcher(id),
        this,
        allocator,
        localConfiguration.customCacheKey,
        continueLoadingCheckIntervalBytes);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((ProgressiveMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    drmSessionManager.release();
  }

  // ProgressiveMediaPeriod.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(long durationUs, boolean isSeekable, boolean isLive) {
    // If we already have the duration from a previous source info refresh, use it.
    durationUs = durationUs == C.TIME_UNSET ? timelineDurationUs : durationUs;
    if (!timelineIsPlaceholder
        && timelineDurationUs == durationUs
        && timelineIsSeekable == isSeekable
        && timelineIsLive == isLive) {
      // Suppress no-op source info changes.
      return;
    }
    timelineDurationUs = durationUs;
    timelineIsSeekable = isSeekable;
    timelineIsLive = isLive;
    timelineIsPlaceholder = false;
    notifySourceInfoRefreshed();
  }

  // Internal methods.

  private void notifySourceInfoRefreshed() {
    // TODO: Split up isDynamic into multiple fields to indicate which values may change. Then
    // indicate that the duration may change until it's known. See [internal: b/69703223].
    Timeline timeline =
        new SinglePeriodTimeline(
            timelineDurationUs,
            timelineIsSeekable,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ timelineIsLive,
            /* manifest= */ null,
            mediaItem);
    if (timelineIsPlaceholder) {
      // TODO: Actually prepare the extractors during preparation so that we don't need a
      // placeholder. See https://github.com/google/ExoPlayer/issues/4727.
      timeline =
          new ForwardingTimeline(timeline) {
            @Override
            public Window getWindow(
                int windowIndex, Window window, long defaultPositionProjectionUs) {
              super.getWindow(windowIndex, window, defaultPositionProjectionUs);
              window.isPlaceholder = true;
              return window;
            }

            @Override
            public Period getPeriod(int periodIndex, Period period, boolean setIds) {
              super.getPeriod(periodIndex, period, setIds);
              period.isPlaceholder = true;
              return period;
            }
          };
    }
    refreshSourceInfo(timeline);
  }
}
