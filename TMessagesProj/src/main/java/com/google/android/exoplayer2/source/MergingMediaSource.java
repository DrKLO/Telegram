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
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Merges multiple {@link MediaSource}s.
 *
 * <p>The {@link Timeline}s of the sources being merged must have the same number of periods.
 */
public final class MergingMediaSource extends CompositeMediaSource<Integer> {

  /** Thrown when a {@link MergingMediaSource} cannot merge its sources. */
  public static final class IllegalMergeException extends IOException {

    /** The reason the merge failed. One of {@link #REASON_PERIOD_COUNT_MISMATCH}. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({REASON_PERIOD_COUNT_MISMATCH})
    public @interface Reason {}
    /** The sources have different period counts. */
    public static final int REASON_PERIOD_COUNT_MISMATCH = 0;

    /** The reason the merge failed. */
    public final @Reason int reason;

    /**
     * @param reason The reason the merge failed.
     */
    public IllegalMergeException(@Reason int reason) {
      this.reason = reason;
    }
  }

  private static final int PERIOD_COUNT_UNSET = -1;
  private static final MediaItem PLACEHOLDER_MEDIA_ITEM =
      new MediaItem.Builder().setMediaId("MergingMediaSource").build();

  private final boolean adjustPeriodTimeOffsets;
  private final boolean clipDurations;
  private final MediaSource[] mediaSources;
  private final Timeline[] timelines;
  private final ArrayList<MediaSource> pendingTimelineSources;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final Map<Object, Long> clippedDurationsUs;
  private final Multimap<Object, ClippingMediaPeriod> clippedMediaPeriods;

  private int periodCount;
  private long[][] periodTimeOffsetsUs;

  @Nullable private IllegalMergeException mergeError;

  /**
   * Creates a merging media source.
   *
   * <p>Neither offsets between the timestamps in the media sources nor the durations of the media
   * sources will be adjusted.
   *
   * @param mediaSources The {@link MediaSource MediaSources} to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this(/* adjustPeriodTimeOffsets= */ false, mediaSources);
  }

  /**
   * Creates a merging media source.
   *
   * <p>Durations of the media sources will not be adjusted.
   *
   * @param adjustPeriodTimeOffsets Whether to adjust timestamps of the merged media sources to all
   *     start at the same time.
   * @param mediaSources The {@link MediaSource MediaSources} to merge.
   */
  public MergingMediaSource(boolean adjustPeriodTimeOffsets, MediaSource... mediaSources) {
    this(adjustPeriodTimeOffsets, /* clipDurations= */ false, mediaSources);
  }

  /**
   * Creates a merging media source.
   *
   * @param adjustPeriodTimeOffsets Whether to adjust timestamps of the merged media sources to all
   *     start at the same time.
   * @param clipDurations Whether to clip the durations of the media sources to match the shortest
   *     duration.
   * @param mediaSources The {@link MediaSource MediaSources} to merge.
   */
  public MergingMediaSource(
      boolean adjustPeriodTimeOffsets, boolean clipDurations, MediaSource... mediaSources) {
    this(
        adjustPeriodTimeOffsets,
        clipDurations,
        new DefaultCompositeSequenceableLoaderFactory(),
        mediaSources);
  }

  /**
   * Creates a merging media source.
   *
   * @param adjustPeriodTimeOffsets Whether to adjust timestamps of the merged media sources to all
   *     start at the same time.
   * @param clipDurations Whether to clip the durations of the media sources to match the shortest
   *     duration.
   * @param compositeSequenceableLoaderFactory A factory to create composite {@link
   *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
   *     audio etc...).
   * @param mediaSources The {@link MediaSource MediaSources} to merge.
   */
  public MergingMediaSource(
      boolean adjustPeriodTimeOffsets,
      boolean clipDurations,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      MediaSource... mediaSources) {
    this.adjustPeriodTimeOffsets = adjustPeriodTimeOffsets;
    this.clipDurations = clipDurations;
    this.mediaSources = mediaSources;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    pendingTimelineSources = new ArrayList<>(Arrays.asList(mediaSources));
    periodCount = PERIOD_COUNT_UNSET;
    timelines = new Timeline[mediaSources.length];
    periodTimeOffsetsUs = new long[0][];
    clippedDurationsUs = new HashMap<>();
    clippedMediaPeriods = MultimapBuilder.hashKeys().arrayListValues().build();
  }

  @Override
  public MediaItem getMediaItem() {
    return mediaSources.length > 0 ? mediaSources[0].getMediaItem() : PLACEHOLDER_MEDIA_ITEM;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    for (int i = 0; i < mediaSources.length; i++) {
      prepareChildSource(i, mediaSources[i]);
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (mergeError != null) {
      throw mergeError;
    }
    super.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaPeriod[] periods = new MediaPeriod[mediaSources.length];
    int periodIndex = timelines[0].getIndexOfPeriod(id.periodUid);
    for (int i = 0; i < periods.length; i++) {
      MediaPeriodId childMediaPeriodId =
          id.copyWithPeriodUid(timelines[i].getUidOfPeriod(periodIndex));
      periods[i] =
          mediaSources[i].createPeriod(
              childMediaPeriodId, allocator, startPositionUs - periodTimeOffsetsUs[periodIndex][i]);
    }
    MediaPeriod mediaPeriod =
        new MergingMediaPeriod(
            compositeSequenceableLoaderFactory, periodTimeOffsetsUs[periodIndex], periods);
    if (clipDurations) {
      mediaPeriod =
          new ClippingMediaPeriod(
              mediaPeriod,
              /* enableInitialDiscontinuity= */ true,
              /* startUs= */ 0,
              /* endUs= */ checkNotNull(clippedDurationsUs.get(id.periodUid)));
      clippedMediaPeriods.put(id.periodUid, (ClippingMediaPeriod) mediaPeriod);
    }
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    if (clipDurations) {
      ClippingMediaPeriod clippingMediaPeriod = (ClippingMediaPeriod) mediaPeriod;
      for (Map.Entry<Object, ClippingMediaPeriod> entry : clippedMediaPeriods.entries()) {
        if (entry.getValue().equals(clippingMediaPeriod)) {
          clippedMediaPeriods.remove(entry.getKey(), entry.getValue());
          break;
        }
      }
      mediaPeriod = clippingMediaPeriod.mediaPeriod;
    }
    MergingMediaPeriod mergingPeriod = (MergingMediaPeriod) mediaPeriod;
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i].releasePeriod(mergingPeriod.getChildPeriod(i));
    }
  }

  @Override
  protected void releaseSourceInternal() {
    super.releaseSourceInternal();
    Arrays.fill(timelines, null);
    periodCount = PERIOD_COUNT_UNSET;
    mergeError = null;
    pendingTimelineSources.clear();
    Collections.addAll(pendingTimelineSources, mediaSources);
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Integer childSourceId, MediaSource mediaSource, Timeline newTimeline) {
    if (mergeError != null) {
      return;
    }
    if (periodCount == PERIOD_COUNT_UNSET) {
      periodCount = newTimeline.getPeriodCount();
    } else if (newTimeline.getPeriodCount() != periodCount) {
      mergeError = new IllegalMergeException(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
      return;
    }
    if (periodTimeOffsetsUs.length == 0) {
      periodTimeOffsetsUs = new long[periodCount][timelines.length];
    }
    pendingTimelineSources.remove(mediaSource);
    timelines[childSourceId] = newTimeline;
    if (pendingTimelineSources.isEmpty()) {
      if (adjustPeriodTimeOffsets) {
        computePeriodTimeOffsets();
      }
      Timeline mergedTimeline = timelines[0];
      if (clipDurations) {
        updateClippedDuration();
        mergedTimeline = new ClippedTimeline(mergedTimeline, clippedDurationsUs);
      }
      refreshSourceInfo(mergedTimeline);
    }
  }

  @Override
  @Nullable
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      Integer childSourceId, MediaPeriodId mediaPeriodId) {
    return childSourceId == 0 ? mediaPeriodId : null;
  }

  private void computePeriodTimeOffsets() {
    Timeline.Period period = new Timeline.Period();
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      long primaryWindowOffsetUs =
          -timelines[0].getPeriod(periodIndex, period).getPositionInWindowUs();
      for (int timelineIndex = 1; timelineIndex < timelines.length; timelineIndex++) {
        long secondaryWindowOffsetUs =
            -timelines[timelineIndex].getPeriod(periodIndex, period).getPositionInWindowUs();
        periodTimeOffsetsUs[periodIndex][timelineIndex] =
            primaryWindowOffsetUs - secondaryWindowOffsetUs;
      }
    }
  }

  private void updateClippedDuration() {
    Timeline.Period period = new Timeline.Period();
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      long minDurationUs = C.TIME_END_OF_SOURCE;
      for (int timelineIndex = 0; timelineIndex < timelines.length; timelineIndex++) {
        long durationUs = timelines[timelineIndex].getPeriod(periodIndex, period).getDurationUs();
        if (durationUs == C.TIME_UNSET) {
          continue;
        }
        long adjustedDurationUs = durationUs + periodTimeOffsetsUs[periodIndex][timelineIndex];
        if (minDurationUs == C.TIME_END_OF_SOURCE || adjustedDurationUs < minDurationUs) {
          minDurationUs = adjustedDurationUs;
        }
      }
      Object periodUid = timelines[0].getUidOfPeriod(periodIndex);
      clippedDurationsUs.put(periodUid, minDurationUs);
      for (ClippingMediaPeriod clippingMediaPeriod : clippedMediaPeriods.get(periodUid)) {
        clippingMediaPeriod.updateClipping(/* startUs= */ 0, /* endUs= */ minDurationUs);
      }
    }
  }

  private static final class ClippedTimeline extends ForwardingTimeline {

    private final long[] periodDurationsUs;
    private final long[] windowDurationsUs;

    public ClippedTimeline(Timeline timeline, Map<Object, Long> clippedDurationsUs) {
      super(timeline);
      int windowCount = timeline.getWindowCount();
      windowDurationsUs = new long[timeline.getWindowCount()];
      Window window = new Window();
      for (int i = 0; i < windowCount; i++) {
        windowDurationsUs[i] = timeline.getWindow(i, window).durationUs;
      }
      int periodCount = timeline.getPeriodCount();
      periodDurationsUs = new long[periodCount];
      Period period = new Period();
      for (int i = 0; i < periodCount; i++) {
        timeline.getPeriod(i, period, /* setIds= */ true);
        long clippedDurationUs = checkNotNull(clippedDurationsUs.get(period.uid));
        periodDurationsUs[i] =
            clippedDurationUs != C.TIME_END_OF_SOURCE ? clippedDurationUs : period.durationUs;
        if (period.durationUs != C.TIME_UNSET) {
          windowDurationsUs[period.windowIndex] -= period.durationUs - periodDurationsUs[i];
        }
      }
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      super.getWindow(windowIndex, window, defaultPositionProjectionUs);
      window.durationUs = windowDurationsUs[windowIndex];
      window.defaultPositionUs =
          window.durationUs == C.TIME_UNSET || window.defaultPositionUs == C.TIME_UNSET
              ? window.defaultPositionUs
              : min(window.defaultPositionUs, window.durationUs);
      return window;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      super.getPeriod(periodIndex, period, setIds);
      period.durationUs = periodDurationsUs[periodIndex];
      return period;
    }
  }
}
