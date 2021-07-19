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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link Timeline} consisting of a single period and static window.
 */
public final class SinglePeriodTimeline extends Timeline {

  private static final Object UID = new Object();

  private final long presentationStartTimeMs;
  private final long windowStartTimeMs;
  private final long periodDurationUs;
  private final long windowDurationUs;
  private final long windowPositionInPeriodUs;
  private final long windowDefaultStartPositionUs;
  private final boolean isSeekable;
  private final boolean isDynamic;
  private final boolean isLive;
  @Nullable private final Object tag;
  @Nullable private final Object manifest;

  /**
   * Creates a timeline containing a single period and a window that spans it.
   *
   * @param durationUs The duration of the period, in microseconds.
   * @param isSeekable Whether seeking is supported within the period.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param isLive Whether the window is live.
   */
  public SinglePeriodTimeline(
      long durationUs, boolean isSeekable, boolean isDynamic, boolean isLive) {
    this(durationUs, isSeekable, isDynamic, isLive, /* manifest= */ null, /* tag= */ null);
  }

  /**
   * Creates a timeline containing a single period and a window that spans it.
   *
   * @param durationUs The duration of the period, in microseconds.
   * @param isSeekable Whether seeking is supported within the period.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param isLive Whether the window is live.
   * @param manifest The manifest. May be {@code null}.
   * @param tag A tag used for {@link Window#tag}.
   */
  public SinglePeriodTimeline(
      long durationUs,
      boolean isSeekable,
      boolean isDynamic,
      boolean isLive,
      @Nullable Object manifest,
      @Nullable Object tag) {
    this(
        durationUs,
        durationUs,
        /* windowPositionInPeriodUs= */ 0,
        /* windowDefaultStartPositionUs= */ 0,
        isSeekable,
        isDynamic,
        isLive,
        manifest,
        tag);
  }

  /**
   * Creates a timeline with one period, and a window of known duration starting at a specified
   * position in the period.
   *
   * @param periodDurationUs The duration of the period in microseconds.
   * @param windowDurationUs The duration of the window in microseconds.
   * @param windowPositionInPeriodUs The position of the start of the window in the period, in
   *     microseconds.
   * @param windowDefaultStartPositionUs The default position relative to the start of the window at
   *     which to begin playback, in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param isLive Whether the window is live.
   * @param manifest The manifest. May be (@code null}.
   * @param tag A tag used for {@link Timeline.Window#tag}.
   */
  public SinglePeriodTimeline(
      long periodDurationUs,
      long windowDurationUs,
      long windowPositionInPeriodUs,
      long windowDefaultStartPositionUs,
      boolean isSeekable,
      boolean isDynamic,
      boolean isLive,
      @Nullable Object manifest,
      @Nullable Object tag) {
    this(
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        periodDurationUs,
        windowDurationUs,
        windowPositionInPeriodUs,
        windowDefaultStartPositionUs,
        isSeekable,
        isDynamic,
        isLive,
        manifest,
        tag);
  }

  /**
   * Creates a timeline with one period, and a window of known duration starting at a specified
   * position in the period.
   *
   * @param presentationStartTimeMs The start time of the presentation in milliseconds since the
   *     epoch.
   * @param windowStartTimeMs The window's start time in milliseconds since the epoch.
   * @param periodDurationUs The duration of the period in microseconds.
   * @param windowDurationUs The duration of the window in microseconds.
   * @param windowPositionInPeriodUs The position of the start of the window in the period, in
   *     microseconds.
   * @param windowDefaultStartPositionUs The default position relative to the start of the window at
   *     which to begin playback, in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param isLive Whether the window is live.
   * @param manifest The manifest. May be {@code null}.
   * @param tag A tag used for {@link Timeline.Window#tag}.
   */
  public SinglePeriodTimeline(
      long presentationStartTimeMs,
      long windowStartTimeMs,
      long periodDurationUs,
      long windowDurationUs,
      long windowPositionInPeriodUs,
      long windowDefaultStartPositionUs,
      boolean isSeekable,
      boolean isDynamic,
      boolean isLive,
      @Nullable Object manifest,
      @Nullable Object tag) {
    this.presentationStartTimeMs = presentationStartTimeMs;
    this.windowStartTimeMs = windowStartTimeMs;
    this.periodDurationUs = periodDurationUs;
    this.windowDurationUs = windowDurationUs;
    this.windowPositionInPeriodUs = windowPositionInPeriodUs;
    this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
    this.isSeekable = isSeekable;
    this.isDynamic = isDynamic;
    this.isLive = isLive;
    this.manifest = manifest;
    this.tag = tag;
  }

  @Override
  public int getWindowCount() {
    return 1;
  }

  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, 1);
    long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
    if (isDynamic && defaultPositionProjectionUs != 0) {
      if (windowDurationUs == C.TIME_UNSET) {
        // Don't allow projection into a window that has an unknown duration.
        windowDefaultStartPositionUs = C.TIME_UNSET;
      } else {
        windowDefaultStartPositionUs += defaultPositionProjectionUs;
        if (windowDefaultStartPositionUs > windowDurationUs) {
          // The projection takes us beyond the end of the window.
          windowDefaultStartPositionUs = C.TIME_UNSET;
        }
      }
    }
    return window.set(
        Window.SINGLE_WINDOW_UID,
        tag,
        manifest,
        presentationStartTimeMs,
        windowStartTimeMs,
        isSeekable,
        isDynamic,
        isLive,
        windowDefaultStartPositionUs,
        windowDurationUs,
        0,
        0,
        windowPositionInPeriodUs);
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    Assertions.checkIndex(periodIndex, 0, 1);
    Object uid = setIds ? UID : null;
    return period.set(/* id= */ null, uid, 0, periodDurationUs, -windowPositionInPeriodUs);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    return UID.equals(uid) ? 0 : C.INDEX_UNSET;
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, 1);
    return UID;
  }
}
