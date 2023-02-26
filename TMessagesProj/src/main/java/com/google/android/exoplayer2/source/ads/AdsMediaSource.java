/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.ads;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.CompositeMediaSource;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MaskingMediaPeriod;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaSource} that inserts ads linearly into a provided content media source.
 *
 * <p>The wrapped content media source must contain a single {@link Timeline.Period}.
 */
public final class AdsMediaSource extends CompositeMediaSource<MediaPeriodId> {

  /**
   * Wrapper for exceptions that occur while loading ads, which are notified via {@link
   * MediaSourceEventListener#onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData,
   * IOException, boolean)}.
   */
  public static final class AdLoadException extends IOException {

    /**
     * Types of ad load exceptions. One of {@link #TYPE_AD}, {@link #TYPE_AD_GROUP}, {@link
     * #TYPE_ALL_ADS} or {@link #TYPE_UNEXPECTED}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({TYPE_AD, TYPE_AD_GROUP, TYPE_ALL_ADS, TYPE_UNEXPECTED})
    public @interface Type {}
    /** Type for when an ad failed to load. The ad will be skipped. */
    public static final int TYPE_AD = 0;
    /** Type for when an ad group failed to load. The ad group will be skipped. */
    public static final int TYPE_AD_GROUP = 1;
    /** Type for when all ad groups failed to load. All ads will be skipped. */
    public static final int TYPE_ALL_ADS = 2;
    /** Type for when an unexpected error occurred while loading ads. All ads will be skipped. */
    public static final int TYPE_UNEXPECTED = 3;

    /** Returns a new ad load exception of {@link #TYPE_AD}. */
    public static AdLoadException createForAd(Exception error) {
      return new AdLoadException(TYPE_AD, error);
    }

    /** Returns a new ad load exception of {@link #TYPE_AD_GROUP}. */
    public static AdLoadException createForAdGroup(Exception error, int adGroupIndex) {
      return new AdLoadException(
          TYPE_AD_GROUP, new IOException("Failed to load ad group " + adGroupIndex, error));
    }

    /** Returns a new ad load exception of {@link #TYPE_ALL_ADS}. */
    public static AdLoadException createForAllAds(Exception error) {
      return new AdLoadException(TYPE_ALL_ADS, error);
    }

    /** Returns a new ad load exception of {@link #TYPE_UNEXPECTED}. */
    public static AdLoadException createForUnexpected(RuntimeException error) {
      return new AdLoadException(TYPE_UNEXPECTED, error);
    }

    /** The {@link Type} of the ad load exception. */
    public final @Type int type;

    private AdLoadException(@Type int type, Exception cause) {
      super(cause);
      this.type = type;
    }

    /**
     * Returns the {@link RuntimeException} that caused the exception if its type is {@link
     * #TYPE_UNEXPECTED}.
     */
    public RuntimeException getRuntimeExceptionForUnexpected() {
      Assertions.checkState(type == TYPE_UNEXPECTED);
      return (RuntimeException) checkNotNull(getCause());
    }
  }

  // Used to identify the content "child" source for CompositeMediaSource.
  private static final MediaPeriodId CHILD_SOURCE_MEDIA_PERIOD_ID =
      new MediaPeriodId(/* periodUid= */ new Object());

  private final MediaSource contentMediaSource;
  private final MediaSource.Factory adMediaSourceFactory;
  private final AdsLoader adsLoader;
  private final AdViewProvider adViewProvider;
  private final DataSpec adTagDataSpec;
  private final Object adsId;
  private final Handler mainHandler;
  private final Timeline.Period period;

  // Accessed on the player thread.
  @Nullable private ComponentListener componentListener;
  @Nullable private Timeline contentTimeline;
  @Nullable private AdPlaybackState adPlaybackState;
  private @NullableType AdMediaSourceHolder[][] adMediaSourceHolders;

  /**
   * Constructs a new source that inserts ads linearly with the content specified by {@code
   * contentMediaSource}.
   *
   * @param contentMediaSource The {@link MediaSource} providing the content to play.
   * @param adTagDataSpec The data specification of the ad tag to load.
   * @param adsId An opaque identifier for ad playback state associated with this instance. Ad
   *     loading and playback state is shared among all playlist items that have the same ads id (by
   *     {@link Object#equals(Object) equality}), so it is important to pass the same identifiers
   *     when constructing playlist items each time the player returns to the foreground.
   * @param adMediaSourceFactory Factory for media sources used to load ad media.
   * @param adsLoader The loader for ads.
   * @param adViewProvider Provider of views for the ad UI.
   */
  public AdsMediaSource(
      MediaSource contentMediaSource,
      DataSpec adTagDataSpec,
      Object adsId,
      MediaSource.Factory adMediaSourceFactory,
      AdsLoader adsLoader,
      AdViewProvider adViewProvider) {
    this.contentMediaSource = contentMediaSource;
    this.adMediaSourceFactory = adMediaSourceFactory;
    this.adsLoader = adsLoader;
    this.adViewProvider = adViewProvider;
    this.adTagDataSpec = adTagDataSpec;
    this.adsId = adsId;
    mainHandler = new Handler(Looper.getMainLooper());
    period = new Timeline.Period();
    adMediaSourceHolders = new AdMediaSourceHolder[0][];
    adsLoader.setSupportedContentTypes(adMediaSourceFactory.getSupportedTypes());
  }

  @Override
  public MediaItem getMediaItem() {
    return contentMediaSource.getMediaItem();
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    ComponentListener componentListener = new ComponentListener();
    this.componentListener = componentListener;
    prepareChildSource(CHILD_SOURCE_MEDIA_PERIOD_ID, contentMediaSource);
    mainHandler.post(
        () ->
            adsLoader.start(
                /* adsMediaSource= */ this,
                adTagDataSpec,
                adsId,
                adViewProvider,
                componentListener));
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    AdPlaybackState adPlaybackState = checkNotNull(this.adPlaybackState);
    if (adPlaybackState.adGroupCount > 0 && id.isAd()) {
      int adGroupIndex = id.adGroupIndex;
      int adIndexInAdGroup = id.adIndexInAdGroup;
      if (adMediaSourceHolders[adGroupIndex].length <= adIndexInAdGroup) {
        int adCount = adIndexInAdGroup + 1;
        adMediaSourceHolders[adGroupIndex] =
            Arrays.copyOf(adMediaSourceHolders[adGroupIndex], adCount);
      }
      @Nullable
      AdMediaSourceHolder adMediaSourceHolder =
          adMediaSourceHolders[adGroupIndex][adIndexInAdGroup];
      if (adMediaSourceHolder == null) {
        adMediaSourceHolder = new AdMediaSourceHolder(id);
        adMediaSourceHolders[adGroupIndex][adIndexInAdGroup] = adMediaSourceHolder;
        maybeUpdateAdMediaSources();
      }
      return adMediaSourceHolder.createMediaPeriod(id, allocator, startPositionUs);
    } else {
      MaskingMediaPeriod mediaPeriod = new MaskingMediaPeriod(id, allocator, startPositionUs);
      mediaPeriod.setMediaSource(contentMediaSource);
      mediaPeriod.createPeriod(id);
      return mediaPeriod;
    }
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MaskingMediaPeriod maskingMediaPeriod = (MaskingMediaPeriod) mediaPeriod;
    MediaPeriodId id = maskingMediaPeriod.id;
    if (id.isAd()) {
      AdMediaSourceHolder adMediaSourceHolder =
          checkNotNull(adMediaSourceHolders[id.adGroupIndex][id.adIndexInAdGroup]);
      adMediaSourceHolder.releaseMediaPeriod(maskingMediaPeriod);
      if (adMediaSourceHolder.isInactive()) {
        adMediaSourceHolder.release();
        adMediaSourceHolders[id.adGroupIndex][id.adIndexInAdGroup] = null;
      }
    } else {
      maskingMediaPeriod.releasePeriod();
    }
  }

  @Override
  protected void releaseSourceInternal() {
    super.releaseSourceInternal();
    ComponentListener componentListener = checkNotNull(this.componentListener);
    this.componentListener = null;
    componentListener.stop();
    contentTimeline = null;
    adPlaybackState = null;
    adMediaSourceHolders = new AdMediaSourceHolder[0][];
    mainHandler.post(() -> adsLoader.stop(/* adsMediaSource= */ this, componentListener));
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      MediaPeriodId childSourceId, MediaSource mediaSource, Timeline newTimeline) {
    if (childSourceId.isAd()) {
      int adGroupIndex = childSourceId.adGroupIndex;
      int adIndexInAdGroup = childSourceId.adIndexInAdGroup;
      checkNotNull(adMediaSourceHolders[adGroupIndex][adIndexInAdGroup])
          .handleSourceInfoRefresh(newTimeline);
    } else {
      Assertions.checkArgument(newTimeline.getPeriodCount() == 1);
      contentTimeline = newTimeline;
    }
    maybeUpdateSourceInfo();
  }

  @Override
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      MediaPeriodId childSourceId, MediaPeriodId mediaPeriodId) {
    // The child id for the content period is just CHILD_SOURCE_MEDIA_PERIOD_ID. That's why
    // we need to forward the reported mediaPeriodId in this case.
    return childSourceId.isAd() ? childSourceId : mediaPeriodId;
  }

  // Internal methods.

  private void onAdPlaybackState(AdPlaybackState adPlaybackState) {
    if (this.adPlaybackState == null) {
      adMediaSourceHolders = new AdMediaSourceHolder[adPlaybackState.adGroupCount][];
      Arrays.fill(adMediaSourceHolders, new AdMediaSourceHolder[0]);
    } else {
      checkState(adPlaybackState.adGroupCount == this.adPlaybackState.adGroupCount);
    }
    this.adPlaybackState = adPlaybackState;
    maybeUpdateAdMediaSources();
    maybeUpdateSourceInfo();
  }

  /**
   * Initializes any {@link AdMediaSourceHolder AdMediaSourceHolders} where the ad media URI is
   * newly known.
   */
  private void maybeUpdateAdMediaSources() {
    @Nullable AdPlaybackState adPlaybackState = this.adPlaybackState;
    if (adPlaybackState == null) {
      return;
    }
    for (int adGroupIndex = 0; adGroupIndex < adMediaSourceHolders.length; adGroupIndex++) {
      for (int adIndexInAdGroup = 0;
          adIndexInAdGroup < this.adMediaSourceHolders[adGroupIndex].length;
          adIndexInAdGroup++) {
        @Nullable
        AdMediaSourceHolder adMediaSourceHolder =
            this.adMediaSourceHolders[adGroupIndex][adIndexInAdGroup];
        AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
        if (adMediaSourceHolder != null
            && !adMediaSourceHolder.hasMediaSource()
            && adIndexInAdGroup < adGroup.uris.length) {
          @Nullable Uri adUri = adGroup.uris[adIndexInAdGroup];
          if (adUri != null) {
            MediaItem.Builder adMediaItem = new MediaItem.Builder().setUri(adUri);
            // Propagate the content's DRM config into the ad media source.
            @Nullable
            MediaItem.LocalConfiguration contentLocalConfiguration =
                contentMediaSource.getMediaItem().localConfiguration;
            if (contentLocalConfiguration != null) {
              adMediaItem.setDrmConfiguration(contentLocalConfiguration.drmConfiguration);
            }
            MediaSource adMediaSource = adMediaSourceFactory.createMediaSource(adMediaItem.build());
            adMediaSourceHolder.initializeWithMediaSource(adMediaSource, adUri);
          }
        }
      }
    }
  }

  private void maybeUpdateSourceInfo() {
    @Nullable Timeline contentTimeline = this.contentTimeline;
    if (adPlaybackState != null && contentTimeline != null) {
      if (adPlaybackState.adGroupCount == 0) {
        refreshSourceInfo(contentTimeline);
      } else {
        adPlaybackState = adPlaybackState.withAdDurationsUs(getAdDurationsUs());
        refreshSourceInfo(new SinglePeriodAdTimeline(contentTimeline, adPlaybackState));
      }
    }
  }

  private long[][] getAdDurationsUs() {
    long[][] adDurationsUs = new long[adMediaSourceHolders.length][];
    for (int i = 0; i < adMediaSourceHolders.length; i++) {
      adDurationsUs[i] = new long[adMediaSourceHolders[i].length];
      for (int j = 0; j < adMediaSourceHolders[i].length; j++) {
        @Nullable AdMediaSourceHolder holder = adMediaSourceHolders[i][j];
        adDurationsUs[i][j] = holder == null ? C.TIME_UNSET : holder.getDurationUs();
      }
    }
    return adDurationsUs;
  }

  /** Listener for component events. All methods are called on the main thread. */
  private final class ComponentListener implements AdsLoader.EventListener {

    private final Handler playerHandler;

    private volatile boolean stopped;

    /**
     * Creates new listener which forwards ad playback states on the creating thread and all other
     * events on the external event listener thread.
     */
    public ComponentListener() {
      playerHandler = Util.createHandlerForCurrentLooper();
    }

    /** Stops event delivery from this instance. */
    public void stop() {
      stopped = true;
      playerHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onAdPlaybackState(final AdPlaybackState adPlaybackState) {
      if (stopped) {
        return;
      }
      playerHandler.post(
          () -> {
            if (stopped) {
              return;
            }
            AdsMediaSource.this.onAdPlaybackState(adPlaybackState);
          });
    }

    @Override
    public void onAdLoadError(final AdLoadException error, DataSpec dataSpec) {
      if (stopped) {
        return;
      }
      createEventDispatcher(/* mediaPeriodId= */ null)
          .loadError(
              new LoadEventInfo(
                  LoadEventInfo.getNewId(),
                  dataSpec,
                  /* elapsedRealtimeMs= */ SystemClock.elapsedRealtime()),
              C.DATA_TYPE_AD,
              error,
              /* wasCanceled= */ true);
    }
  }

  private final class AdPrepareListener implements MaskingMediaPeriod.PrepareListener {

    private final Uri adUri;

    public AdPrepareListener(Uri adUri) {
      this.adUri = adUri;
    }

    @Override
    public void onPrepareComplete(MediaPeriodId mediaPeriodId) {
      mainHandler.post(
          () ->
              adsLoader.handlePrepareComplete(
                  /* adsMediaSource= */ AdsMediaSource.this,
                  mediaPeriodId.adGroupIndex,
                  mediaPeriodId.adIndexInAdGroup));
    }

    @Override
    public void onPrepareError(MediaPeriodId mediaPeriodId, IOException exception) {
      createEventDispatcher(mediaPeriodId)
          .loadError(
              new LoadEventInfo(
                  LoadEventInfo.getNewId(),
                  new DataSpec(adUri),
                  /* elapsedRealtimeMs= */ SystemClock.elapsedRealtime()),
              C.DATA_TYPE_AD,
              AdLoadException.createForAd(exception),
              /* wasCanceled= */ true);
      mainHandler.post(
          () ->
              adsLoader.handlePrepareError(
                  /* adsMediaSource= */ AdsMediaSource.this,
                  mediaPeriodId.adGroupIndex,
                  mediaPeriodId.adIndexInAdGroup,
                  exception));
    }
  }

  private final class AdMediaSourceHolder {

    private final MediaPeriodId id;
    private final List<MaskingMediaPeriod> activeMediaPeriods;

    private @MonotonicNonNull Uri adUri;
    private @MonotonicNonNull MediaSource adMediaSource;
    private @MonotonicNonNull Timeline timeline;

    public AdMediaSourceHolder(MediaPeriodId id) {
      this.id = id;
      activeMediaPeriods = new ArrayList<>();
    }

    public void initializeWithMediaSource(MediaSource adMediaSource, Uri adUri) {
      this.adMediaSource = adMediaSource;
      this.adUri = adUri;
      for (int i = 0; i < activeMediaPeriods.size(); i++) {
        MaskingMediaPeriod maskingMediaPeriod = activeMediaPeriods.get(i);
        maskingMediaPeriod.setMediaSource(adMediaSource);
        maskingMediaPeriod.setPrepareListener(new AdPrepareListener(adUri));
      }
      prepareChildSource(id, adMediaSource);
    }

    public MediaPeriod createMediaPeriod(
        MediaPeriodId id, Allocator allocator, long startPositionUs) {
      MaskingMediaPeriod maskingMediaPeriod =
          new MaskingMediaPeriod(id, allocator, startPositionUs);
      activeMediaPeriods.add(maskingMediaPeriod);
      if (adMediaSource != null) {
        maskingMediaPeriod.setMediaSource(adMediaSource);
        maskingMediaPeriod.setPrepareListener(new AdPrepareListener(checkNotNull(adUri)));
      }
      if (timeline != null) {
        Object periodUid = timeline.getUidOfPeriod(/* periodIndex= */ 0);
        MediaPeriodId adSourceMediaPeriodId = new MediaPeriodId(periodUid, id.windowSequenceNumber);
        maskingMediaPeriod.createPeriod(adSourceMediaPeriodId);
      }
      return maskingMediaPeriod;
    }

    public void handleSourceInfoRefresh(Timeline timeline) {
      Assertions.checkArgument(timeline.getPeriodCount() == 1);
      if (this.timeline == null) {
        Object periodUid = timeline.getUidOfPeriod(/* periodIndex= */ 0);
        for (int i = 0; i < activeMediaPeriods.size(); i++) {
          MaskingMediaPeriod mediaPeriod = activeMediaPeriods.get(i);
          MediaPeriodId adSourceMediaPeriodId =
              new MediaPeriodId(periodUid, mediaPeriod.id.windowSequenceNumber);
          mediaPeriod.createPeriod(adSourceMediaPeriodId);
        }
      }
      this.timeline = timeline;
    }

    public long getDurationUs() {
      return timeline == null
          ? C.TIME_UNSET
          : timeline.getPeriod(/* periodIndex= */ 0, period).getDurationUs();
    }

    public void releaseMediaPeriod(MaskingMediaPeriod maskingMediaPeriod) {
      activeMediaPeriods.remove(maskingMediaPeriod);
      maskingMediaPeriod.releasePeriod();
    }

    public void release() {
      if (hasMediaSource()) {
        releaseChildSource(id);
      }
    }

    public boolean hasMediaSource() {
      return adMediaSource != null;
    }

    public boolean isInactive() {
      return activeMediaPeriods.isEmpty();
    }
  }
}
