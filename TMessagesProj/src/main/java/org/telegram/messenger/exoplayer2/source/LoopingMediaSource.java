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

import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Loops a {@link MediaSource}.
 */
public final class LoopingMediaSource implements MediaSource {

  /**
   * The maximum number of periods that can be exposed by the source. The value of this constant is
   * large enough to cause indefinite looping in practice (the total duration of the looping source
   * will be approximately five years if the duration of each period is one second).
   */
  public static final int MAX_EXPOSED_PERIODS = 157680000;

  private static final String TAG = "LoopingMediaSource";

  private final MediaSource childSource;
  private final int loopCount;

  private int childPeriodCount;

  /**
   * Loops the provided source indefinitely.
   *
   * @param childSource The {@link MediaSource} to loop.
   */
  public LoopingMediaSource(MediaSource childSource) {
    this(childSource, Integer.MAX_VALUE);
  }

  /**
   * Loops the provided source a specified number of times.
   *
   * @param childSource The {@link MediaSource} to loop.
   * @param loopCount The desired number of loops. Must be strictly positive. The actual number of
   *     loops will be capped at the maximum that can achieved without causing the number of
   *     periods exposed by the source to exceed {@link #MAX_EXPOSED_PERIODS}.
   */
  public LoopingMediaSource(MediaSource childSource, int loopCount) {
    Assertions.checkArgument(loopCount > 0);
    this.childSource = childSource;
    this.loopCount = loopCount;
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, final Listener listener) {
    childSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        childPeriodCount = timeline.getPeriodCount();
        listener.onSourceInfoRefreshed(new LoopingTimeline(timeline, loopCount), manifest);
      }
    });
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    childSource.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator, long positionUs) {
    return childSource.createPeriod(index % childPeriodCount, allocator, positionUs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    childSource.releasePeriod(mediaPeriod);
  }

  @Override
  public void releaseSource() {
    childSource.releaseSource();
  }

  private static final class LoopingTimeline extends Timeline {

    private final Timeline childTimeline;
    private final int childPeriodCount;
    private final int childWindowCount;
    private final int loopCount;

    public LoopingTimeline(Timeline childTimeline, int loopCount) {
      this.childTimeline = childTimeline;
      childPeriodCount = childTimeline.getPeriodCount();
      childWindowCount = childTimeline.getWindowCount();
      // This is the maximum number of loops that can be performed without exceeding
      // MAX_EXPOSED_PERIODS periods.
      int maxLoopCount = MAX_EXPOSED_PERIODS / childPeriodCount;
      if (loopCount > maxLoopCount) {
        if (loopCount != Integer.MAX_VALUE) {
          Log.w(TAG, "Capped loops to avoid overflow: " + loopCount + " -> " + maxLoopCount);
        }
        this.loopCount = maxLoopCount;
      } else {
        this.loopCount = loopCount;
      }
    }

    @Override
    public int getWindowCount() {
      return childWindowCount * loopCount;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      childTimeline.getWindow(windowIndex % childWindowCount, window, setIds,
          defaultPositionProjectionUs);
      int periodIndexOffset = (windowIndex / childWindowCount) * childPeriodCount;
      window.firstPeriodIndex += periodIndexOffset;
      window.lastPeriodIndex += periodIndexOffset;
      return window;
    }

    @Override
    public int getPeriodCount() {
      return childPeriodCount * loopCount;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      childTimeline.getPeriod(periodIndex % childPeriodCount, period, setIds);
      int loopCount = (periodIndex / childPeriodCount);
      period.windowIndex += loopCount * childWindowCount;
      if (setIds) {
        period.uid = Pair.create(loopCount, period.uid);
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      if (!(uid instanceof Pair)) {
        return C.INDEX_UNSET;
      }
      Pair<?, ?> loopCountAndChildUid = (Pair<?, ?>) uid;
      if (!(loopCountAndChildUid.first instanceof Integer)) {
        return C.INDEX_UNSET;
      }
      int loopCount = (Integer) loopCountAndChildUid.first;
      int periodIndexOffset = loopCount * childPeriodCount;
      return childTimeline.getIndexOfPeriod(loopCountAndChildUid.second) + periodIndexOffset;
    }

  }

}
