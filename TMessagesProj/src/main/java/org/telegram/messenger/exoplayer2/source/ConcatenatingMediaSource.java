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

import android.util.Pair;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Concatenates multiple {@link MediaSource}s. It is valid for the same {@link MediaSource} instance
 * to be present more than once in the concatenation.
 */
public final class ConcatenatingMediaSource implements MediaSource {

  private final MediaSource[] mediaSources;
  private final Timeline[] timelines;
  private final Object[] manifests;
  private final Map<MediaPeriod, Integer> sourceIndexByMediaPeriod;
  private final boolean[] duplicateFlags;

  private Listener listener;
  private ConcatenatedTimeline timeline;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this.mediaSources = mediaSources;
    timelines = new Timeline[mediaSources.length];
    manifests = new Object[mediaSources.length];
    sourceIndexByMediaPeriod = new HashMap<>();
    duplicateFlags = buildDuplicateFlags(mediaSources);
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    this.listener = listener;
    for (int i = 0; i < mediaSources.length; i++) {
      if (!duplicateFlags[i]) {
        final int index = i;
        mediaSources[i].prepareSource(player, false, new Listener() {
          @Override
          public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
            handleSourceInfoRefreshed(index, timeline, manifest);
          }
        });
      }
    }
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    for (int i = 0; i < mediaSources.length; i++) {
      if (!duplicateFlags[i]) {
        mediaSources[i].maybeThrowSourceInfoRefreshError();
      }
    }
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    int sourceIndex = timeline.getSourceIndexForPeriod(index);
    int periodIndexInSource = index - timeline.getFirstPeriodIndexInSource(sourceIndex);
    MediaPeriod mediaPeriod = mediaSources[sourceIndex].createPeriod(periodIndexInSource, allocator,
        positionUs);
    sourceIndexByMediaPeriod.put(mediaPeriod, sourceIndex);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    int sourceIndex = sourceIndexByMediaPeriod.get(mediaPeriod);
    sourceIndexByMediaPeriod.remove(mediaPeriod);
    mediaSources[sourceIndex].releasePeriod(mediaPeriod);
  }

  @Override
  public void releaseSource() {
    for (int i = 0; i < mediaSources.length; i++) {
      if (!duplicateFlags[i]) {
        mediaSources[i].releaseSource();
      }
    }
  }

  private void handleSourceInfoRefreshed(int sourceFirstIndex, Timeline sourceTimeline,
      Object sourceManifest) {
    // Set the timeline and manifest.
    timelines[sourceFirstIndex] = sourceTimeline;
    manifests[sourceFirstIndex] = sourceManifest;
    // Also set the timeline and manifest for any duplicate entries of the same source.
    for (int i = sourceFirstIndex + 1; i < mediaSources.length; i++) {
      if (mediaSources[i] == mediaSources[sourceFirstIndex]) {
        timelines[i] = sourceTimeline;
        manifests[i] = sourceManifest;
      }
    }
    for (Timeline timeline : timelines) {
      if (timeline == null) {
        // Don't invoke the listener until all sources have timelines.
        return;
      }
    }
    timeline = new ConcatenatedTimeline(timelines.clone());
    listener.onSourceInfoRefreshed(timeline, manifests.clone());
  }

  private static boolean[] buildDuplicateFlags(MediaSource[] mediaSources) {
    boolean[] duplicateFlags = new boolean[mediaSources.length];
    IdentityHashMap<MediaSource, Void> sources = new IdentityHashMap<>(mediaSources.length);
    for (int i = 0; i < mediaSources.length; i++) {
      MediaSource source = mediaSources[i];
      if (!sources.containsKey(source)) {
        sources.put(source, null);
      } else {
        duplicateFlags[i] = true;
      }
    }
    return duplicateFlags;
  }

  /**
   * A {@link Timeline} that is the concatenation of one or more {@link Timeline}s.
   */
  private static final class ConcatenatedTimeline extends Timeline {

    private final Timeline[] timelines;
    private final int[] sourcePeriodOffsets;
    private final int[] sourceWindowOffsets;

    public ConcatenatedTimeline(Timeline[] timelines) {
      int[] sourcePeriodOffsets = new int[timelines.length];
      int[] sourceWindowOffsets = new int[timelines.length];
      long periodCount = 0;
      int windowCount = 0;
      for (int i = 0; i < timelines.length; i++) {
        Timeline timeline = timelines[i];
        periodCount += timeline.getPeriodCount();
        Assertions.checkState(periodCount <= Integer.MAX_VALUE,
            "ConcatenatingMediaSource children contain too many periods");
        sourcePeriodOffsets[i] = (int) periodCount;
        windowCount += timeline.getWindowCount();
        sourceWindowOffsets[i] = windowCount;
      }
      this.timelines = timelines;
      this.sourcePeriodOffsets = sourcePeriodOffsets;
      this.sourceWindowOffsets = sourceWindowOffsets;
    }

    @Override
    public int getWindowCount() {
      return sourceWindowOffsets[sourceWindowOffsets.length - 1];
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      int sourceIndex = getSourceIndexForWindow(windowIndex);
      int firstWindowIndexInSource = getFirstWindowIndexInSource(sourceIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
      timelines[sourceIndex].getWindow(windowIndex - firstWindowIndexInSource, window, setIds,
          defaultPositionProjectionUs);
      window.firstPeriodIndex += firstPeriodIndexInSource;
      window.lastPeriodIndex += firstPeriodIndexInSource;
      return window;
    }

    @Override
    public int getPeriodCount() {
      return sourcePeriodOffsets[sourcePeriodOffsets.length - 1];
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      int sourceIndex = getSourceIndexForPeriod(periodIndex);
      int firstWindowIndexInSource = getFirstWindowIndexInSource(sourceIndex);
      int firstPeriodIndexInSource = getFirstPeriodIndexInSource(sourceIndex);
      timelines[sourceIndex].getPeriod(periodIndex - firstPeriodIndexInSource, period, setIds);
      period.windowIndex += firstWindowIndexInSource;
      if (setIds) {
        period.uid = Pair.create(sourceIndex, period.uid);
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      if (!(uid instanceof Pair)) {
        return C.INDEX_UNSET;
      }
      Pair<?, ?> sourceIndexAndPeriodId = (Pair<?, ?>) uid;
      if (!(sourceIndexAndPeriodId.first instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int sourceIndex = (Integer) sourceIndexAndPeriodId.first;
      Object periodId = sourceIndexAndPeriodId.second;
      if (sourceIndex < 0 || sourceIndex >= timelines.length) {
        return C.INDEX_UNSET;
      }
      int periodIndexInSource = timelines[sourceIndex].getIndexOfPeriod(periodId);
      return periodIndexInSource == C.INDEX_UNSET ? C.INDEX_UNSET
          : getFirstPeriodIndexInSource(sourceIndex) + periodIndexInSource;
    }

    private int getSourceIndexForPeriod(int periodIndex) {
      return Util.binarySearchFloor(sourcePeriodOffsets, periodIndex, true, false) + 1;
    }

    private int getFirstPeriodIndexInSource(int sourceIndex) {
      return sourceIndex == 0 ? 0 : sourcePeriodOffsets[sourceIndex - 1];
    }

    private int getSourceIndexForWindow(int windowIndex) {
      return Util.binarySearchFloor(sourceWindowOffsets, windowIndex, true, false) + 1;
    }

    private int getFirstWindowIndexInSource(int sourceIndex) {
      return sourceIndex == 0 ? 0 : sourceWindowOffsets[sourceIndex - 1];
    }

  }

}
