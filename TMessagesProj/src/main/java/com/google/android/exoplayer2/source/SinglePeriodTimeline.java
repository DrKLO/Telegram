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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;

/** A {@link Timeline} consisting of a single period and static window. */
public final class SinglePeriodTimeline extends Timeline {

  private static final Object UID = new Object();
  private static final MediaItem MEDIA_ITEM =
      new MediaItem.Builder().setMediaId("SinglePeriodTimeline").setUri(Uri.EMPTY).build();

  private final long presentationStartTimeMs;
  private final long windowStartTimeMs;
  private final long elapsedRealtimeEpochOffsetMs;
  private final long periodDurationUs;
  private final long windowDurationUs;
  private final long windowPositionInPeriodUs;
  private final long windowDefaultStartPositionUs;
  private final boolean isSeekable;
  private final boolean isDynamic;
  private final boolean suppressPositionProjection;
  @Nullable private final Object manifest;
  @Nullable private final MediaItem mediaItem;
  @Nullable private final MediaItem.LiveConfiguration liveConfiguration;

  /**
   * @deprecated Use {@link #SinglePeriodTimeline(long, boolean, boolean, boolean, Object,
   *     MediaItem)} instead.
   */
  // Provide backwards compatibility.
  @SuppressWarnings("deprecation")
  @Deprecated
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
   * Creates a timeline containing a single period and a window that spans it.
   *
   * @param durationUs The duration of the period, in microseconds.
   * @param isSeekable Whether seeking is supported within the period.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param useLiveConfiguration Whether the window is live and {@link MediaItem#liveConfiguration}
   *     is used to configure live playback behaviour.
   * @param manifest The manifest. May be {@code null}.
   * @param mediaItem A media item used for {@link Window#mediaItem}.
   */
  public SinglePeriodTimeline(
      long durationUs,
      boolean isSeekable,
      boolean isDynamic,
      boolean useLiveConfiguration,
      @Nullable Object manifest,
      MediaItem mediaItem) {
    this(
        durationUs,
        durationUs,
        /* windowPositionInPeriodUs= */ 0,
        /* windowDefaultStartPositionUs= */ 0,
        isSeekable,
        isDynamic,
        useLiveConfiguration,
        manifest,
        mediaItem);
  }

  /**
   * @deprecated Use {@link #SinglePeriodTimeline(long, long, long, long, boolean, boolean, boolean,
   *     Object, MediaItem)} instead.
   */
  // Provide backwards compatibility.
  @SuppressWarnings("deprecation")
  @Deprecated
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
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
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
   * @param periodDurationUs The duration of the period in microseconds.
   * @param windowDurationUs The duration of the window in microseconds.
   * @param windowPositionInPeriodUs The position of the start of the window in the period, in
   *     microseconds.
   * @param windowDefaultStartPositionUs The default position relative to the start of the window at
   *     which to begin playback, in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param useLiveConfiguration Whether the window is live and {@link MediaItem#liveConfiguration}
   *     is used to configure live playback behaviour.
   * @param manifest The manifest. May be {@code null}.
   * @param mediaItem A media item used for {@link Timeline.Window#mediaItem}.
   */
  public SinglePeriodTimeline(
      long periodDurationUs,
      long windowDurationUs,
      long windowPositionInPeriodUs,
      long windowDefaultStartPositionUs,
      boolean isSeekable,
      boolean isDynamic,
      boolean useLiveConfiguration,
      @Nullable Object manifest,
      MediaItem mediaItem) {
    this(
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
        periodDurationUs,
        windowDurationUs,
        windowPositionInPeriodUs,
        windowDefaultStartPositionUs,
        isSeekable,
        isDynamic,
        /* suppressPositionProjection= */ false,
        manifest,
        mediaItem,
        useLiveConfiguration ? mediaItem.liveConfiguration : null);
  }

  /**
   * @deprecated Use {@link #SinglePeriodTimeline(long, long, long, long, long, long, long, boolean,
   *     boolean, boolean, Object, MediaItem, MediaItem.LiveConfiguration)} instead.
   */
  @Deprecated
  public SinglePeriodTimeline(
      long presentationStartTimeMs,
      long windowStartTimeMs,
      long elapsedRealtimeEpochOffsetMs,
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
        presentationStartTimeMs,
        windowStartTimeMs,
        elapsedRealtimeEpochOffsetMs,
        periodDurationUs,
        windowDurationUs,
        windowPositionInPeriodUs,
        windowDefaultStartPositionUs,
        isSeekable,
        isDynamic,
        /* suppressPositionProjection= */ false,
        manifest,
        MEDIA_ITEM.buildUpon().setTag(tag).build(),
        isLive ? MEDIA_ITEM.liveConfiguration : null);
  }

  /**
   * @deprecated Use {@link #SinglePeriodTimeline(long, long, long, long, long, long, long, boolean,
   *     boolean, boolean, Object, MediaItem, MediaItem.LiveConfiguration)} instead.
   */
  @Deprecated
  public SinglePeriodTimeline(
      long presentationStartTimeMs,
      long windowStartTimeMs,
      long elapsedRealtimeEpochOffsetMs,
      long periodDurationUs,
      long windowDurationUs,
      long windowPositionInPeriodUs,
      long windowDefaultStartPositionUs,
      boolean isSeekable,
      boolean isDynamic,
      @Nullable Object manifest,
      MediaItem mediaItem,
      @Nullable MediaItem.LiveConfiguration liveConfiguration) {
    this(
        presentationStartTimeMs,
        windowStartTimeMs,
        elapsedRealtimeEpochOffsetMs,
        periodDurationUs,
        windowDurationUs,
        windowPositionInPeriodUs,
        windowDefaultStartPositionUs,
        isSeekable,
        isDynamic,
        /* suppressPositionProjection= */ false,
        manifest,
        mediaItem,
        liveConfiguration);
  }

  /**
   * Creates a timeline with one period, and a window of known duration starting at a specified
   * position in the period.
   *
   * @param presentationStartTimeMs The start time of the presentation in milliseconds since the
   *     epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
   * @param windowStartTimeMs The window's start time in milliseconds since the epoch, or {@link
   *     C#TIME_UNSET} if unknown or not applicable.
   * @param elapsedRealtimeEpochOffsetMs The offset between {@link
   *     android.os.SystemClock#elapsedRealtime()} and the time since the Unix epoch according to
   *     the clock of the media origin server, or {@link C#TIME_UNSET} if unknown or not applicable.
   * @param periodDurationUs The duration of the period in microseconds.
   * @param windowDurationUs The duration of the window in microseconds.
   * @param windowPositionInPeriodUs The position of the start of the window in the period, in
   *     microseconds.
   * @param windowDefaultStartPositionUs The default position relative to the start of the window at
   *     which to begin playback, in microseconds.
   * @param isSeekable Whether seeking is supported within the window.
   * @param isDynamic Whether the window may change when the timeline is updated.
   * @param suppressPositionProjection Whether {@link #getWindow(int, Window, long) position
   *     projection} in a playlist should be suppressed. This only applies for dynamic timelines and
   *     is ignored otherwise.
   * @param manifest The manifest. May be {@code null}.
   * @param mediaItem A media item used for {@link Timeline.Window#mediaItem}.
   * @param liveConfiguration The configuration for live playback behaviour, or {@code null} if the
   *     window is not live.
   */
  public SinglePeriodTimeline(
      long presentationStartTimeMs,
      long windowStartTimeMs,
      long elapsedRealtimeEpochOffsetMs,
      long periodDurationUs,
      long windowDurationUs,
      long windowPositionInPeriodUs,
      long windowDefaultStartPositionUs,
      boolean isSeekable,
      boolean isDynamic,
      boolean suppressPositionProjection,
      @Nullable Object manifest,
      MediaItem mediaItem,
      @Nullable MediaItem.LiveConfiguration liveConfiguration) {
    this.presentationStartTimeMs = presentationStartTimeMs;
    this.windowStartTimeMs = windowStartTimeMs;
    this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
    this.periodDurationUs = periodDurationUs;
    this.windowDurationUs = windowDurationUs;
    this.windowPositionInPeriodUs = windowPositionInPeriodUs;
    this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
    this.isSeekable = isSeekable;
    this.isDynamic = isDynamic;
    this.suppressPositionProjection = suppressPositionProjection;
    this.manifest = manifest;
    this.mediaItem = checkNotNull(mediaItem);
    this.liveConfiguration = liveConfiguration;
  }

  @Override
  public int getWindowCount() {
    return 1;
  }

  // Provide backwards compatibility.
  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, 1);
    long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
    if (isDynamic && !suppressPositionProjection && defaultPositionProjectionUs != 0) {
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
        mediaItem,
        manifest,
        presentationStartTimeMs,
        windowStartTimeMs,
        elapsedRealtimeEpochOffsetMs,
        isSeekable,
        isDynamic,
        liveConfiguration,
        windowDefaultStartPositionUs,
        windowDurationUs,
        /* firstPeriodIndex= */ 0,
        /* lastPeriodIndex= */ 0,
        windowPositionInPeriodUs);
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    Assertions.checkIndex(periodIndex, 0, 1);
    @Nullable Object uid = setIds ? UID : null;
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
