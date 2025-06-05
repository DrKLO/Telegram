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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;

/**
 * {@link MediaSource} that wraps a source and clips its timeline based on specified start/end
 * positions. The wrapped source must consist of a single period.
 */
public final class ClippingMediaSource extends WrappingMediaSource {

  /** Thrown when a {@link ClippingMediaSource} cannot clip its wrapped source. */
  public static final class IllegalClippingException extends IOException {

    /**
     * The reason clipping failed. One of {@link #REASON_INVALID_PERIOD_COUNT}, {@link
     * #REASON_NOT_SEEKABLE_TO_START} or {@link #REASON_START_EXCEEDS_END}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({REASON_INVALID_PERIOD_COUNT, REASON_NOT_SEEKABLE_TO_START, REASON_START_EXCEEDS_END})
    public @interface Reason {}
    /** The wrapped source doesn't consist of a single period. */
    public static final int REASON_INVALID_PERIOD_COUNT = 0;
    /** The wrapped source is not seekable and a non-zero clipping start position was specified. */
    public static final int REASON_NOT_SEEKABLE_TO_START = 1;
    /** The wrapped source ends before the specified clipping start position. */
    public static final int REASON_START_EXCEEDS_END = 2;

    /** The reason clipping failed. */
    public final @Reason int reason;

    /**
     * @param reason The reason clipping failed.
     */
    public IllegalClippingException(@Reason int reason) {
      super("Illegal clipping: " + getReasonDescription(reason));
      this.reason = reason;
    }

    private static String getReasonDescription(@Reason int reason) {
      switch (reason) {
        case REASON_INVALID_PERIOD_COUNT:
          return "invalid period count";
        case REASON_NOT_SEEKABLE_TO_START:
          return "not seekable to start";
        case REASON_START_EXCEEDS_END:
          return "start exceeds end";
        default:
          return "unknown";
      }
    }
  }

  private final long startUs;
  private final long endUs;
  private final boolean enableInitialDiscontinuity;
  private final boolean allowDynamicClippingUpdates;
  private final boolean relativeToDefaultPosition;
  private final ArrayList<ClippingMediaPeriod> mediaPeriods;
  private final Timeline.Window window;

  @Nullable private ClippingTimeline clippingTimeline;
  @Nullable private IllegalClippingException clippingError;
  private long periodStartUs;
  private long periodEndUs;

  /**
   * Creates a new clipping source that wraps the specified source and provides samples between the
   * specified start and end position.
   *
   * @param mediaSource The single-period source to wrap.
   * @param startPositionUs The start position within {@code mediaSource}'s window at which to start
   *     providing samples, in microseconds.
   * @param endPositionUs The end position within {@code mediaSource}'s window at which to stop
   *     providing samples, in microseconds. Specify {@link C#TIME_END_OF_SOURCE} to provide samples
   *     from the specified start point up to the end of the source. Specifying a position that
   *     exceeds the {@code mediaSource}'s duration will also result in the end of the source not
   *     being clipped.
   */
  public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs) {
    this(
        mediaSource,
        startPositionUs,
        endPositionUs,
        /* enableInitialDiscontinuity= */ true,
        /* allowDynamicClippingUpdates= */ false,
        /* relativeToDefaultPosition= */ false);
  }

  /**
   * Creates a new clipping source that wraps the specified source and provides samples from the
   * default position for the specified duration.
   *
   * @param mediaSource The single-period source to wrap.
   * @param durationUs The duration from the default position in the window in {@code mediaSource}'s
   *     timeline at which to stop providing samples. Specifying a duration that exceeds the {@code
   *     mediaSource}'s duration will result in the end of the source not being clipped.
   */
  public ClippingMediaSource(MediaSource mediaSource, long durationUs) {
    this(
        mediaSource,
        /* startPositionUs= */ 0,
        /* endPositionUs= */ durationUs,
        /* enableInitialDiscontinuity= */ true,
        /* allowDynamicClippingUpdates= */ false,
        /* relativeToDefaultPosition= */ true);
  }

  /**
   * Creates a new clipping source that wraps the specified source.
   *
   * <p>If the start point is guaranteed to be a key frame, pass {@code false} to {@code
   * enableInitialPositionDiscontinuity} to suppress an initial discontinuity when a period is first
   * read from.
   *
   * <p>For live streams, if the clipping positions should move with the live window, pass {@code
   * true} to {@code allowDynamicClippingUpdates}. Otherwise, the live stream ends when the playback
   * reaches {@code endPositionUs} in the last reported live window at the time a media period was
   * created.
   *
   * @param mediaSource The single-period source to wrap.
   * @param startPositionUs The start position at which to start providing samples, in microseconds.
   *     If {@code relativeToDefaultPosition} is {@code false}, this position is relative to the
   *     start of the window in {@code mediaSource}'s timeline. If {@code relativeToDefaultPosition}
   *     is {@code true}, this position is relative to the default position in the window in {@code
   *     mediaSource}'s timeline.
   * @param endPositionUs The end position at which to stop providing samples, in microseconds.
   *     Specify {@link C#TIME_END_OF_SOURCE} to provide samples from the specified start point up
   *     to the end of the source. Specifying a position that exceeds the {@code mediaSource}'s
   *     duration will also result in the end of the source not being clipped. If {@code
   *     relativeToDefaultPosition} is {@code false}, the specified position is relative to the
   *     start of the window in {@code mediaSource}'s timeline. If {@code relativeToDefaultPosition}
   *     is {@code true}, this position is relative to the default position in the window in {@code
   *     mediaSource}'s timeline.
   * @param enableInitialDiscontinuity Whether the initial discontinuity should be enabled.
   * @param allowDynamicClippingUpdates Whether the clipping of active media periods moves with a
   *     live window. If {@code false}, playback ends when it reaches {@code endPositionUs} in the
   *     last reported live window at the time a media period was created.
   * @param relativeToDefaultPosition Whether {@code startPositionUs} and {@code endPositionUs} are
   *     relative to the default position in the window in {@code mediaSource}'s timeline.
   */
  public ClippingMediaSource(
      MediaSource mediaSource,
      long startPositionUs,
      long endPositionUs,
      boolean enableInitialDiscontinuity,
      boolean allowDynamicClippingUpdates,
      boolean relativeToDefaultPosition) {
    super(Assertions.checkNotNull(mediaSource));
    Assertions.checkArgument(startPositionUs >= 0);
    startUs = startPositionUs;
    endUs = endPositionUs;
    this.enableInitialDiscontinuity = enableInitialDiscontinuity;
    this.allowDynamicClippingUpdates = allowDynamicClippingUpdates;
    this.relativeToDefaultPosition = relativeToDefaultPosition;
    mediaPeriods = new ArrayList<>();
    window = new Timeline.Window();
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (clippingError != null) {
      throw clippingError;
    }
    super.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    ClippingMediaPeriod mediaPeriod =
        new ClippingMediaPeriod(
            mediaSource.createPeriod(id, allocator, startPositionUs),
            enableInitialDiscontinuity,
            periodStartUs,
            periodEndUs);
    mediaPeriods.add(mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    Assertions.checkState(mediaPeriods.remove(mediaPeriod));
    mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
    if (mediaPeriods.isEmpty() && !allowDynamicClippingUpdates) {
      refreshClippedTimeline(Assertions.checkNotNull(clippingTimeline).timeline);
    }
  }

  @Override
  protected void releaseSourceInternal() {
    super.releaseSourceInternal();
    clippingError = null;
    clippingTimeline = null;
  }

  @Override
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    if (clippingError != null) {
      return;
    }
    refreshClippedTimeline(newTimeline);
  }

  private void refreshClippedTimeline(Timeline timeline) {
    long windowStartUs;
    long windowEndUs;
    timeline.getWindow(/* windowIndex= */ 0, window);
    long windowPositionInPeriodUs = window.getPositionInFirstPeriodUs();
    if (clippingTimeline == null || mediaPeriods.isEmpty() || allowDynamicClippingUpdates) {
      windowStartUs = startUs;
      windowEndUs = endUs;
      if (relativeToDefaultPosition) {
        long windowDefaultPositionUs = window.getDefaultPositionUs();
        windowStartUs += windowDefaultPositionUs;
        windowEndUs += windowDefaultPositionUs;
      }
      periodStartUs = windowPositionInPeriodUs + windowStartUs;
      periodEndUs =
          endUs == C.TIME_END_OF_SOURCE
              ? C.TIME_END_OF_SOURCE
              : windowPositionInPeriodUs + windowEndUs;
      int count = mediaPeriods.size();
      for (int i = 0; i < count; i++) {
        mediaPeriods.get(i).updateClipping(periodStartUs, periodEndUs);
      }
    } else {
      // Keep window fixed at previous period position.
      windowStartUs = periodStartUs - windowPositionInPeriodUs;
      windowEndUs =
          endUs == C.TIME_END_OF_SOURCE
              ? C.TIME_END_OF_SOURCE
              : periodEndUs - windowPositionInPeriodUs;
    }
    try {
      clippingTimeline = new ClippingTimeline(timeline, windowStartUs, windowEndUs);
    } catch (IllegalClippingException e) {
      clippingError = e;
      // The clipping error won't be propagated while we have existing MediaPeriods. Setting the
      // error at the MediaPeriods ensures it will be thrown as soon as possible.
      for (int i = 0; i < mediaPeriods.size(); i++) {
        mediaPeriods.get(i).setClippingError(clippingError);
      }
      return;
    }
    refreshSourceInfo(clippingTimeline);
  }

  /** Provides a clipped view of a specified timeline. */
  private static final class ClippingTimeline extends ForwardingTimeline {

    private final long startUs;
    private final long endUs;
    private final long durationUs;
    private final boolean isDynamic;

    /**
     * Creates a new clipping timeline that wraps the specified timeline.
     *
     * @param timeline The timeline to clip.
     * @param startUs The number of microseconds to clip from the start of {@code timeline}.
     * @param endUs The end position in microseconds for the clipped timeline relative to the start
     *     of {@code timeline}, or {@link C#TIME_END_OF_SOURCE} to clip no samples from the end.
     * @throws IllegalClippingException If the timeline could not be clipped.
     */
    public ClippingTimeline(Timeline timeline, long startUs, long endUs)
        throws IllegalClippingException {
      super(timeline);
      if (timeline.getPeriodCount() != 1) {
        throw new IllegalClippingException(IllegalClippingException.REASON_INVALID_PERIOD_COUNT);
      }
      Window window = timeline.getWindow(0, new Window());
      startUs = max(0, startUs);
      if (!window.isPlaceholder && startUs != 0 && !window.isSeekable) {
        throw new IllegalClippingException(IllegalClippingException.REASON_NOT_SEEKABLE_TO_START);
      }
      long resolvedEndUs = endUs == C.TIME_END_OF_SOURCE ? window.durationUs : max(0, endUs);
      if (window.durationUs != C.TIME_UNSET) {
        if (resolvedEndUs > window.durationUs) {
          resolvedEndUs = window.durationUs;
        }
        if (startUs > resolvedEndUs) {
          throw new IllegalClippingException(IllegalClippingException.REASON_START_EXCEEDS_END);
        }
      }
      this.startUs = startUs;
      this.endUs = resolvedEndUs;
      durationUs = resolvedEndUs == C.TIME_UNSET ? C.TIME_UNSET : (resolvedEndUs - startUs);
      isDynamic =
          window.isDynamic
              && (resolvedEndUs == C.TIME_UNSET
                  || (window.durationUs != C.TIME_UNSET && resolvedEndUs == window.durationUs));
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      timeline.getWindow(/* windowIndex= */ 0, window, /* defaultPositionProjectionUs= */ 0);
      window.positionInFirstPeriodUs += startUs;
      window.durationUs = durationUs;
      window.isDynamic = isDynamic;
      if (window.defaultPositionUs != C.TIME_UNSET) {
        window.defaultPositionUs = max(window.defaultPositionUs, startUs);
        window.defaultPositionUs =
            endUs == C.TIME_UNSET ? window.defaultPositionUs : min(window.defaultPositionUs, endUs);
        window.defaultPositionUs -= startUs;
      }
      long startMs = Util.usToMs(startUs);
      if (window.presentationStartTimeMs != C.TIME_UNSET) {
        window.presentationStartTimeMs += startMs;
      }
      if (window.windowStartTimeMs != C.TIME_UNSET) {
        window.windowStartTimeMs += startMs;
      }
      return window;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(/* periodIndex= */ 0, period, setIds);
      long positionInClippedWindowUs = period.getPositionInWindowUs() - startUs;
      long periodDurationUs =
          durationUs == C.TIME_UNSET ? C.TIME_UNSET : durationUs - positionInClippedWindowUs;
      return period.set(
          period.id, period.uid, /* windowIndex= */ 0, periodDurationUs, positionInClippedWindowUs);
    }
  }
}
