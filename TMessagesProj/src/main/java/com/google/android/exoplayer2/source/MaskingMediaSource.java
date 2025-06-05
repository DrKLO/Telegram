/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A {@link MediaSource} that masks the {@link Timeline} with a placeholder until the actual media
 * structure is known.
 */
public final class MaskingMediaSource extends WrappingMediaSource {

  private final boolean useLazyPreparation;
  private final Timeline.Window window;
  private final Timeline.Period period;

  private MaskingTimeline timeline;
  @Nullable private MaskingMediaPeriod unpreparedMaskingMediaPeriod;
  private boolean hasStartedPreparing;
  private boolean isPrepared;
  private boolean hasRealTimeline;

  /**
   * Creates the masking media source.
   *
   * @param mediaSource A {@link MediaSource}.
   * @param useLazyPreparation Whether the {@code mediaSource} is prepared lazily. If false, all
   *     manifest loads and other initial preparation steps happen immediately. If true, these
   *     initial preparations are triggered only when the player starts buffering the media.
   */
  public MaskingMediaSource(MediaSource mediaSource, boolean useLazyPreparation) {
    super(mediaSource);
    this.useLazyPreparation = useLazyPreparation && mediaSource.isSingleWindow();
    window = new Timeline.Window();
    period = new Timeline.Period();
    @Nullable Timeline initialTimeline = mediaSource.getInitialTimeline();
    if (initialTimeline != null) {
      timeline =
          MaskingTimeline.createWithRealTimeline(
              initialTimeline, /* firstWindowUid= */ null, /* firstPeriodUid= */ null);
      hasRealTimeline = true;
    } else {
      timeline = MaskingTimeline.createWithPlaceholderTimeline(mediaSource.getMediaItem());
    }
  }

  /** Returns the {@link Timeline}. */
  public Timeline getTimeline() {
    return timeline;
  }

  @Override
  public void prepareSourceInternal() {
    if (!useLazyPreparation) {
      hasStartedPreparing = true;
      prepareChildSource();
    }
  }

  @Override
  @SuppressWarnings("MissingSuperCall")
  public void maybeThrowSourceInfoRefreshError() {
    // Do nothing. Source info refresh errors will be thrown when calling
    // MaskingMediaPeriod.maybeThrowPrepareError.
  }

  @Override
  public MaskingMediaPeriod createPeriod(
      MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MaskingMediaPeriod mediaPeriod = new MaskingMediaPeriod(id, allocator, startPositionUs);
    mediaPeriod.setMediaSource(mediaSource);
    if (isPrepared) {
      MediaPeriodId idInSource = id.copyWithPeriodUid(getInternalPeriodUid(id.periodUid));
      mediaPeriod.createPeriod(idInSource);
    } else {
      // We should have at most one media period while source is unprepared because the duration is
      // unset and we don't load beyond periods with unset duration. We need to figure out how to
      // handle the prepare positions of multiple deferred media periods, should that ever change.
      unpreparedMaskingMediaPeriod = mediaPeriod;
      if (!hasStartedPreparing) {
        hasStartedPreparing = true;
        prepareChildSource();
      }
    }
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((MaskingMediaPeriod) mediaPeriod).releasePeriod();
    if (mediaPeriod == unpreparedMaskingMediaPeriod) {
      unpreparedMaskingMediaPeriod = null;
    }
  }

  @Override
  public void releaseSourceInternal() {
    isPrepared = false;
    hasStartedPreparing = false;
    super.releaseSourceInternal();
  }

  @Override
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    @Nullable MediaPeriodId idForMaskingPeriodPreparation = null;
    if (isPrepared) {
      timeline = timeline.cloneWithUpdatedTimeline(newTimeline);
      if (unpreparedMaskingMediaPeriod != null) {
        // Reset override in case the duration changed and we need to update our override.
        setPreparePositionOverrideToUnpreparedMaskingPeriod(
            unpreparedMaskingMediaPeriod.getPreparePositionOverrideUs());
      }
    } else if (newTimeline.isEmpty()) {
      timeline =
          hasRealTimeline
              ? timeline.cloneWithUpdatedTimeline(newTimeline)
              : MaskingTimeline.createWithRealTimeline(
                  newTimeline,
                  Window.SINGLE_WINDOW_UID,
                  MaskingTimeline.MASKING_EXTERNAL_PERIOD_UID);
    } else {
      // Determine first period and the start position.
      // This will be:
      //  1. The default window start position if no deferred period has been created yet.
      //  2. The non-zero prepare position of the deferred period under the assumption that this is
      //     a non-zero initial seek position in the window.
      //  3. The default window start position if the deferred period has a prepare position of zero
      //     under the assumption that the prepare position of zero was used because it's the
      //     default position of the PlaceholderTimeline window. Note that this will override an
      //     intentional seek to zero for a window with a non-zero default position. This is
      //     unlikely to be a problem as a non-zero default position usually only occurs for live
      //     playbacks and seeking to zero in a live window would cause BehindLiveWindowExceptions
      //     anyway.
      newTimeline.getWindow(/* windowIndex= */ 0, window);
      long windowStartPositionUs = window.getDefaultPositionUs();
      Object windowUid = window.uid;
      if (unpreparedMaskingMediaPeriod != null) {
        long periodPreparePositionUs = unpreparedMaskingMediaPeriod.getPreparePositionUs();
        timeline.getPeriodByUid(unpreparedMaskingMediaPeriod.id.periodUid, period);
        long windowPreparePositionUs = period.getPositionInWindowUs() + periodPreparePositionUs;
        long oldWindowDefaultPositionUs =
            timeline.getWindow(/* windowIndex= */ 0, window).getDefaultPositionUs();
        if (windowPreparePositionUs != oldWindowDefaultPositionUs) {
          windowStartPositionUs = windowPreparePositionUs;
        }
      }
      Pair<Object, Long> periodUidAndPositionUs =
          newTimeline.getPeriodPositionUs(
              window, period, /* windowIndex= */ 0, windowStartPositionUs);
      Object periodUid = periodUidAndPositionUs.first;
      long periodPositionUs = periodUidAndPositionUs.second;
      timeline =
          hasRealTimeline
              ? timeline.cloneWithUpdatedTimeline(newTimeline)
              : MaskingTimeline.createWithRealTimeline(newTimeline, windowUid, periodUid);
      if (unpreparedMaskingMediaPeriod != null) {
        MaskingMediaPeriod maskingPeriod = unpreparedMaskingMediaPeriod;
        setPreparePositionOverrideToUnpreparedMaskingPeriod(periodPositionUs);
        idForMaskingPeriodPreparation =
            maskingPeriod.id.copyWithPeriodUid(getInternalPeriodUid(maskingPeriod.id.periodUid));
      }
    }
    hasRealTimeline = true;
    isPrepared = true;
    refreshSourceInfo(this.timeline);
    if (idForMaskingPeriodPreparation != null) {
      Assertions.checkNotNull(unpreparedMaskingMediaPeriod)
          .createPeriod(idForMaskingPeriodPreparation);
    }
  }

  @Override
  @Nullable
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(MediaPeriodId mediaPeriodId) {
    return mediaPeriodId.copyWithPeriodUid(getExternalPeriodUid(mediaPeriodId.periodUid));
  }

  private Object getInternalPeriodUid(Object externalPeriodUid) {
    return timeline.replacedInternalPeriodUid != null
            && externalPeriodUid.equals(MaskingTimeline.MASKING_EXTERNAL_PERIOD_UID)
        ? timeline.replacedInternalPeriodUid
        : externalPeriodUid;
  }

  private Object getExternalPeriodUid(Object internalPeriodUid) {
    return timeline.replacedInternalPeriodUid != null
            && timeline.replacedInternalPeriodUid.equals(internalPeriodUid)
        ? MaskingTimeline.MASKING_EXTERNAL_PERIOD_UID
        : internalPeriodUid;
  }

  @RequiresNonNull("unpreparedMaskingMediaPeriod")
  private void setPreparePositionOverrideToUnpreparedMaskingPeriod(long preparePositionOverrideUs) {
    MaskingMediaPeriod maskingPeriod = unpreparedMaskingMediaPeriod;
    int maskingPeriodIndex = timeline.getIndexOfPeriod(maskingPeriod.id.periodUid);
    if (maskingPeriodIndex == C.INDEX_UNSET) {
      // The new timeline doesn't contain this period anymore. This can happen if the media source
      // has multiple periods and removed the first period with a timeline update. Ignore the
      // update, as the non-existing period will be released anyway as soon as the player receives
      // this new timeline.
      return;
    }
    long periodDurationUs = timeline.getPeriod(maskingPeriodIndex, period).durationUs;
    if (periodDurationUs != C.TIME_UNSET) {
      // Ensure the overridden position doesn't exceed the period duration.
      if (preparePositionOverrideUs >= periodDurationUs) {
        preparePositionOverrideUs = max(0, periodDurationUs - 1);
      }
    }
    maskingPeriod.overridePreparePositionUs(preparePositionOverrideUs);
  }

  /**
   * Timeline used as placeholder for an unprepared media source. After preparation, a
   * MaskingTimeline is used to keep the originally assigned masking period ID.
   */
  private static final class MaskingTimeline extends ForwardingTimeline {

    public static final Object MASKING_EXTERNAL_PERIOD_UID = new Object();

    @Nullable private final Object replacedInternalWindowUid;
    @Nullable private final Object replacedInternalPeriodUid;

    /**
     * Returns an instance with a placeholder timeline using the provided {@link MediaItem}.
     *
     * @param mediaItem A {@link MediaItem}.
     */
    public static MaskingTimeline createWithPlaceholderTimeline(MediaItem mediaItem) {
      return new MaskingTimeline(
          new PlaceholderTimeline(mediaItem),
          Window.SINGLE_WINDOW_UID,
          MASKING_EXTERNAL_PERIOD_UID);
    }

    /**
     * Returns an instance with a real timeline, replacing the provided period ID with the already
     * assigned masking period ID.
     *
     * @param timeline The real timeline.
     * @param firstWindowUid The window UID in the timeline which will be replaced by the already
     *     assigned {@link Window#SINGLE_WINDOW_UID}.
     * @param firstPeriodUid The period UID in the timeline which will be replaced by the already
     *     assigned {@link #MASKING_EXTERNAL_PERIOD_UID}.
     */
    public static MaskingTimeline createWithRealTimeline(
        Timeline timeline, @Nullable Object firstWindowUid, @Nullable Object firstPeriodUid) {
      return new MaskingTimeline(timeline, firstWindowUid, firstPeriodUid);
    }

    private MaskingTimeline(
        Timeline timeline,
        @Nullable Object replacedInternalWindowUid,
        @Nullable Object replacedInternalPeriodUid) {
      super(timeline);
      this.replacedInternalWindowUid = replacedInternalWindowUid;
      this.replacedInternalPeriodUid = replacedInternalPeriodUid;
    }

    /**
     * Returns a copy with an updated timeline. This keeps the existing period replacement.
     *
     * @param timeline The new timeline.
     */
    public MaskingTimeline cloneWithUpdatedTimeline(Timeline timeline) {
      return new MaskingTimeline(timeline, replacedInternalWindowUid, replacedInternalPeriodUid);
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      timeline.getWindow(windowIndex, window, defaultPositionProjectionUs);
      if (Util.areEqual(window.uid, replacedInternalWindowUid)) {
        window.uid = Window.SINGLE_WINDOW_UID;
      }
      return window;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(periodIndex, period, setIds);
      if (Util.areEqual(period.uid, replacedInternalPeriodUid) && setIds) {
        period.uid = MASKING_EXTERNAL_PERIOD_UID;
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline.getIndexOfPeriod(
          MASKING_EXTERNAL_PERIOD_UID.equals(uid) && replacedInternalPeriodUid != null
              ? replacedInternalPeriodUid
              : uid);
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Object uid = timeline.getUidOfPeriod(periodIndex);
      return Util.areEqual(uid, replacedInternalPeriodUid) ? MASKING_EXTERNAL_PERIOD_UID : uid;
    }
  }

  /** A timeline with one dynamic window with a period of indeterminate duration. */
  @VisibleForTesting
  public static final class PlaceholderTimeline extends Timeline {

    private final MediaItem mediaItem;

    /** Creates a new instance with the given media item. */
    public PlaceholderTimeline(MediaItem mediaItem) {
      this.mediaItem = mediaItem;
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      window.set(
          Window.SINGLE_WINDOW_UID,
          mediaItem,
          /* manifest= */ null,
          /* presentationStartTimeMs= */ C.TIME_UNSET,
          /* windowStartTimeMs= */ C.TIME_UNSET,
          /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
          /* isSeekable= */ false,
          // Dynamic window to indicate pending timeline updates.
          /* isDynamic= */ true,
          /* liveConfiguration= */ null,
          /* defaultPositionUs= */ 0,
          /* durationUs= */ C.TIME_UNSET,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ 0,
          /* positionInFirstPeriodUs= */ 0);
      window.isPlaceholder = true;
      return window;
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      period.set(
          /* id= */ setIds ? 0 : null,
          /* uid= */ setIds ? MaskingTimeline.MASKING_EXTERNAL_PERIOD_UID : null,
          /* windowIndex= */ 0,
          /* durationUs = */ C.TIME_UNSET,
          /* positionInWindowUs= */ 0,
          /* adPlaybackState= */ AdPlaybackState.NONE,
          /* isPlaceholder= */ true);
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return uid == MaskingTimeline.MASKING_EXTERNAL_PERIOD_UID ? 0 : C.INDEX_UNSET;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      return MaskingTimeline.MASKING_EXTERNAL_PERIOD_UID;
    }
  }
}
