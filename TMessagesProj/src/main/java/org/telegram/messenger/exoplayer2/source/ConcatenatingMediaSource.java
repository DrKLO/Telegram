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

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Player;
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
  private final boolean isRepeatOneAtomic;

  private Listener listener;
  private ConcatenatedTimeline timeline;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this(false, mediaSources);
  }

  /**
   * @param isRepeatOneAtomic Whether the concatenated media source shall be treated as atomic
   *     (i.e., repeated in its entirety) when repeat mode is set to {@code Player.REPEAT_MODE_ONE}.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(boolean isRepeatOneAtomic, MediaSource... mediaSources) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    this.mediaSources = mediaSources;
    this.isRepeatOneAtomic = isRepeatOneAtomic;
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
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
    int sourceIndex = timeline.getChildIndexByPeriodIndex(id.periodIndex);
    MediaPeriodId periodIdInSource =
        new MediaPeriodId(id.periodIndex - timeline.getFirstPeriodIndexByChildIndex(sourceIndex));
    MediaPeriod mediaPeriod = mediaSources[sourceIndex].createPeriod(periodIdInSource, allocator);
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
    timeline = new ConcatenatedTimeline(timelines.clone(), isRepeatOneAtomic);
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
  private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {

    private final Timeline[] timelines;
    private final int[] sourcePeriodOffsets;
    private final int[] sourceWindowOffsets;
    private final boolean isRepeatOneAtomic;

    public ConcatenatedTimeline(Timeline[] timelines, boolean isRepeatOneAtomic) {
      super(timelines.length);
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
      this.isRepeatOneAtomic = isRepeatOneAtomic;
    }

    @Override
    public int getWindowCount() {
      return sourceWindowOffsets[sourceWindowOffsets.length - 1];
    }

    @Override
    public int getPeriodCount() {
      return sourcePeriodOffsets[sourcePeriodOffsets.length - 1];
    }

    @Override
    public int getNextWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode) {
      if (isRepeatOneAtomic && repeatMode == Player.REPEAT_MODE_ONE) {
        repeatMode = Player.REPEAT_MODE_ALL;
      }
      return super.getNextWindowIndex(windowIndex, repeatMode);
    }

    @Override
    public int getPreviousWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode) {
      if (isRepeatOneAtomic && repeatMode == Player.REPEAT_MODE_ONE) {
        repeatMode = Player.REPEAT_MODE_ALL;
      }
      return super.getPreviousWindowIndex(windowIndex, repeatMode);
    }

    @Override
    protected int getChildIndexByPeriodIndex(int periodIndex) {
      return Util.binarySearchFloor(sourcePeriodOffsets, periodIndex, true, false) + 1;
    }

    @Override
    protected int getChildIndexByWindowIndex(int windowIndex) {
      return Util.binarySearchFloor(sourceWindowOffsets, windowIndex, true, false) + 1;
    }

    @Override
    protected int getChildIndexByChildUid(Object childUid) {
      if (!(childUid instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      return (Integer) childUid;
    }

    @Override
    protected Timeline getTimelineByChildIndex(int childIndex) {
      return timelines[childIndex];
    }

    @Override
    protected int getFirstPeriodIndexByChildIndex(int childIndex) {
      return childIndex == 0 ? 0 : sourcePeriodOffsets[childIndex - 1];
    }

    @Override
    protected int getFirstWindowIndexByChildIndex(int childIndex) {
      return childIndex == 0 ? 0 : sourceWindowOffsets[childIndex - 1];
    }

    @Override
    protected Object getChildUidByChildIndex(int childIndex) {
      return childIndex;
    }

  }

}
