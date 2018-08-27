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

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Merges multiple {@link MediaSource}s.
 *
 * <p>The {@link Timeline}s of the sources being merged must have the same number of periods.
 */
public final class MergingMediaSource extends CompositeMediaSource<Integer> {

  /**
   * Thrown when a {@link MergingMediaSource} cannot merge its sources.
   */
  public static final class IllegalMergeException extends IOException {

    /**
     * The reason the merge failed.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REASON_PERIOD_COUNT_MISMATCH})
    public @interface Reason {}
    /**
     * The sources have different period counts.
     */
    public static final int REASON_PERIOD_COUNT_MISMATCH = 0;

    /**
     * The reason the merge failed.
     */
    @Reason public final int reason;

    /**
     * @param reason The reason the merge failed.
     */
    public IllegalMergeException(@Reason int reason) {
      this.reason = reason;
    }

  }

  private static final int PERIOD_COUNT_UNSET = -1;

  private final MediaSource[] mediaSources;
  private final ArrayList<MediaSource> pendingTimelineSources;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;

  private Timeline primaryTimeline;
  private Object primaryManifest;
  private int periodCount;
  private IllegalMergeException mergeError;

  /**
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this(new DefaultCompositeSequenceableLoaderFactory(), mediaSources);
  }

  /**
   * @param compositeSequenceableLoaderFactory A factory to create composite
   *     {@link SequenceableLoader}s for when this media source loads data from multiple streams
   *     (video, audio etc...).
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    pendingTimelineSources = new ArrayList<>(Arrays.asList(mediaSources));
    periodCount = PERIOD_COUNT_UNSET;
  }

  @Override
  public void prepareSourceInternal(
      ExoPlayer player,
      boolean isTopLevelSource,
      @Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
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
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    MediaPeriod[] periods = new MediaPeriod[mediaSources.length];
    for (int i = 0; i < periods.length; i++) {
      periods[i] = mediaSources[i].createPeriod(id, allocator);
    }
    return new MergingMediaPeriod(compositeSequenceableLoaderFactory, periods);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MergingMediaPeriod mergingPeriod = (MergingMediaPeriod) mediaPeriod;
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i].releasePeriod(mergingPeriod.periods[i]);
    }
  }

  @Override
  public void releaseSourceInternal() {
    super.releaseSourceInternal();
    primaryTimeline = null;
    primaryManifest = null;
    periodCount = PERIOD_COUNT_UNSET;
    mergeError = null;
    pendingTimelineSources.clear();
    Collections.addAll(pendingTimelineSources, mediaSources);
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Integer id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
    if (mergeError == null) {
      mergeError = checkTimelineMerges(timeline);
    }
    if (mergeError != null) {
      return;
    }
    pendingTimelineSources.remove(mediaSource);
    if (mediaSource == mediaSources[0]) {
      primaryTimeline = timeline;
      primaryManifest = manifest;
    }
    if (pendingTimelineSources.isEmpty()) {
      refreshSourceInfo(primaryTimeline, primaryManifest);
    }
  }

  private IllegalMergeException checkTimelineMerges(Timeline timeline) {
    if (periodCount == PERIOD_COUNT_UNSET) {
      periodCount = timeline.getPeriodCount();
    } else if (timeline.getPeriodCount() != periodCount) {
      return new IllegalMergeException(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
    }
    return null;
  }

}
