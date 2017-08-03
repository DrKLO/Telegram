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
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;

/**
 * {@link MediaSource} that wraps a source and clips its timeline based on specified start/end
 * positions. The wrapped source may only have a single period/window and it must not be dynamic
 * (live).
 */
public final class ClippingMediaSource implements MediaSource, MediaSource.Listener {

  private final MediaSource mediaSource;
  private final long startUs;
  private final long endUs;
  private final ArrayList<ClippingMediaPeriod> mediaPeriods;

  private MediaSource.Listener sourceListener;
  private ClippingTimeline clippingTimeline;

  /**
   * Creates a new clipping source that wraps the specified source.
   *
   * @param mediaSource The single-period, non-dynamic source to wrap.
   * @param startPositionUs The start position within {@code mediaSource}'s timeline at which to
   *     start providing samples, in microseconds.
   * @param endPositionUs The end position within {@code mediaSource}'s timeline at which to stop
   *     providing samples, in microseconds. Specify {@link C#TIME_END_OF_SOURCE} to provide samples
   *     from the specified start point up to the end of the source.
   */
  public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs) {
    Assertions.checkArgument(startPositionUs >= 0);
    this.mediaSource = Assertions.checkNotNull(mediaSource);
    startUs = startPositionUs;
    endUs = endPositionUs;
    mediaPeriods = new ArrayList<>();
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener) {
    this.sourceListener = listener;
    mediaSource.prepareSource(player, false, this);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    mediaSource.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    ClippingMediaPeriod mediaPeriod = new ClippingMediaPeriod(
        mediaSource.createPeriod(index, allocator, startUs + positionUs));
    mediaPeriods.add(mediaPeriod);
    mediaPeriod.setClipping(clippingTimeline.startUs, clippingTimeline.endUs);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    Assertions.checkState(mediaPeriods.remove(mediaPeriod));
    mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
  }

  @Override
  public void releaseSource() {
    mediaSource.releaseSource();
  }

  // MediaSource.Listener implementation.

  @Override
  public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
    clippingTimeline = new ClippingTimeline(timeline, startUs, endUs);
    sourceListener.onSourceInfoRefreshed(clippingTimeline, manifest);
    long startUs = clippingTimeline.startUs;
    long endUs = clippingTimeline.endUs == C.TIME_UNSET ? C.TIME_END_OF_SOURCE
        : clippingTimeline.endUs;
    int count = mediaPeriods.size();
    for (int i = 0; i < count; i++) {
      mediaPeriods.get(i).setClipping(startUs, endUs);
    }
  }

  /**
   * Provides a clipped view of a specified timeline.
   */
  private static final class ClippingTimeline extends Timeline {

    private final Timeline timeline;
    private final long startUs;
    private final long endUs;

    /**
     * Creates a new clipping timeline that wraps the specified timeline.
     *
     * @param timeline The timeline to clip.
     * @param startUs The number of microseconds to clip from the start of {@code timeline}.
     * @param endUs The end position in microseconds for the clipped timeline relative to the start
     *     of {@code timeline}, or {@link C#TIME_END_OF_SOURCE} to clip no samples from the end.
     */
    public ClippingTimeline(Timeline timeline, long startUs, long endUs) {
      Assertions.checkArgument(timeline.getWindowCount() == 1);
      Assertions.checkArgument(timeline.getPeriodCount() == 1);
      Window window = timeline.getWindow(0, new Window(), false);
      Assertions.checkArgument(!window.isDynamic);
      long resolvedEndUs = endUs == C.TIME_END_OF_SOURCE ? window.durationUs : endUs;
      if (window.durationUs != C.TIME_UNSET) {
        Assertions.checkArgument(startUs == 0 || window.isSeekable);
        Assertions.checkArgument(resolvedEndUs <= window.durationUs);
        Assertions.checkArgument(startUs <= resolvedEndUs);
      }
      Period period = timeline.getPeriod(0, new Period());
      Assertions.checkArgument(period.getPositionInWindowUs() == 0);
      this.timeline = timeline;
      this.startUs = startUs;
      this.endUs = resolvedEndUs;
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      window = timeline.getWindow(0, window, setIds, defaultPositionProjectionUs);
      window.durationUs = endUs != C.TIME_UNSET ? endUs - startUs : C.TIME_UNSET;
      if (window.defaultPositionUs != C.TIME_UNSET) {
        window.defaultPositionUs = Math.max(window.defaultPositionUs, startUs);
        window.defaultPositionUs = endUs == C.TIME_UNSET ? window.defaultPositionUs
            : Math.min(window.defaultPositionUs, endUs);
        window.defaultPositionUs -= startUs;
      }
      long startMs = C.usToMs(startUs);
      if (window.presentationStartTimeMs != C.TIME_UNSET) {
        window.presentationStartTimeMs += startMs;
      }
      if (window.windowStartTimeMs != C.TIME_UNSET) {
        window.windowStartTimeMs += startMs;
      }
      return window;
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      period = timeline.getPeriod(0, period, setIds);
      period.durationUs = endUs != C.TIME_UNSET ? endUs - startUs : C.TIME_UNSET;
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline.getIndexOfPeriod(uid);
    }

  }

}
