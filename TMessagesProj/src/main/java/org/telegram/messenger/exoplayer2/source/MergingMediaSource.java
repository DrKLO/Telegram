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

import android.support.annotation.IntDef;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Merges multiple {@link MediaSource}s.
 * <p>
 * The {@link Timeline}s of the sources being merged must have the same number of periods, and must
 * not have any dynamic windows.
 */
public final class MergingMediaSource implements MediaSource {

  /**
   * Thrown when a {@link MergingMediaSource} cannot merge its sources.
   */
  public static final class IllegalMergeException extends IOException {

    /**
     * The reason the merge failed.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REASON_WINDOWS_ARE_DYNAMIC, REASON_PERIOD_COUNT_MISMATCH})
    public @interface Reason {}
    /**
     * The merge failed because one of the sources being merged has a dynamic window.
     */
    public static final int REASON_WINDOWS_ARE_DYNAMIC = 0;
    /**
     * The merge failed because the sources have different period counts.
     */
    public static final int REASON_PERIOD_COUNT_MISMATCH = 1;

    /**
     * The reason the merge failed. One of {@link #REASON_WINDOWS_ARE_DYNAMIC} and
     * {@link #REASON_PERIOD_COUNT_MISMATCH}.
     */
    @Reason public final int reason;

    /**
     * @param reason The reason the merge failed. One of {@link #REASON_WINDOWS_ARE_DYNAMIC} and
     *     {@link #REASON_PERIOD_COUNT_MISMATCH}.
     */
    public IllegalMergeException(@Reason int reason) {
      this.reason = reason;
    }

  }

  private static final int PERIOD_COUNT_UNSET = -1;

  private final MediaSource[] mediaSources;
  private final ArrayList<MediaSource> pendingTimelineSources;
  private final Timeline.Window window;

  private Listener listener;
  private Timeline primaryTimeline;
  private Object primaryManifest;
  private int periodCount;
  private IllegalMergeException mergeError;

  /**
   * @param mediaSources The {@link MediaSource}s to merge.
   */
  public MergingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    pendingTimelineSources = new ArrayList<>(Arrays.asList(mediaSources));
    window = new Timeline.Window();
    periodCount = PERIOD_COUNT_UNSET;
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    this.listener = listener;
    for (int i = 0; i < mediaSources.length; i++) {
      final int sourceIndex = i;
      mediaSources[sourceIndex].prepareSource(player, false, new Listener() {
        @Override
        public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
          handleSourceInfoRefreshed(sourceIndex, timeline, manifest);
        }
      });
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (mergeError != null) {
      throw mergeError;
    }
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.maybeThrowSourceInfoRefreshError();
    }
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    MediaPeriod[] periods = new MediaPeriod[mediaSources.length];
    for (int i = 0; i < periods.length; i++) {
      periods[i] = mediaSources[i].createPeriod(index, allocator, positionUs);
    }
    return new MergingMediaPeriod(periods);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MergingMediaPeriod mergingPeriod = (MergingMediaPeriod) mediaPeriod;
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i].releasePeriod(mergingPeriod.periods[i]);
    }
  }

  @Override
  public void releaseSource() {
    for (MediaSource mediaSource : mediaSources) {
      mediaSource.releaseSource();
    }
  }

  private void handleSourceInfoRefreshed(int sourceIndex, Timeline timeline, Object manifest) {
    if (mergeError == null) {
      mergeError = checkTimelineMerges(timeline);
    }
    if (mergeError != null) {
      return;
    }
    pendingTimelineSources.remove(mediaSources[sourceIndex]);
    if (sourceIndex == 0) {
      primaryTimeline = timeline;
      primaryManifest = manifest;
    }
    if (pendingTimelineSources.isEmpty()) {
      listener.onSourceInfoRefreshed(primaryTimeline, primaryManifest);
    }
  }

  private IllegalMergeException checkTimelineMerges(Timeline timeline) {
    int windowCount = timeline.getWindowCount();
    for (int i = 0; i < windowCount; i++) {
      if (timeline.getWindow(i, window, false).isDynamic) {
        return new IllegalMergeException(IllegalMergeException.REASON_WINDOWS_ARE_DYNAMIC);
      }
    }
    if (periodCount == PERIOD_COUNT_UNSET) {
      periodCount = timeline.getPeriodCount();
    } else if (timeline.getPeriodCount() != periodCount) {
      return new IllegalMergeException(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
    }
    return null;
  }

}
