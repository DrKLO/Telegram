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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.source.ads.AdPlaybackState.AD_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.BundleUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.List;

/**
 * A flexible representation of the structure of media. A timeline is able to represent the
 * structure of a wide variety of media, from simple cases like a single media file through to
 * complex compositions of media such as playlists and streams with inserted ads. Instances are
 * immutable. For cases where media is changing dynamically (e.g. live streams), a timeline provides
 * a snapshot of the current state.
 *
 * <p>A timeline consists of {@link Window Windows} and {@link Period Periods}.
 *
 * <ul>
 *   <li>A {@link Window} usually corresponds to one playlist item. It may span one or more periods
 *       and it defines the region within those periods that's currently available for playback. The
 *       window also provides additional information such as whether seeking is supported within the
 *       window and the default position, which is the position from which playback will start when
 *       the player starts playing the window.
 *   <li>A {@link Period} defines a single logical piece of media, for example a media file. It may
 *       also define groups of ads inserted into the media, along with information about whether
 *       those ads have been loaded and played.
 * </ul>
 *
 * <p>The following examples illustrate timelines for various use cases.
 *
 * <h2 id="single-file">Single media file or on-demand stream</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-single-file.svg" alt="Example timeline for a
 * single file">
 *
 * <p>A timeline for a single media file or on-demand stream consists of a single period and window.
 * The window spans the whole period, indicating that all parts of the media are available for
 * playback. The window's default position is typically at the start of the period (indicated by the
 * black dot in the figure above).
 *
 * <h2>Playlist of media files or on-demand streams</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-playlist.svg" alt="Example timeline for a
 * playlist of files">
 *
 * <p>A timeline for a playlist of media files or on-demand streams consists of multiple periods,
 * each with its own window. Each window spans the whole of the corresponding period, and typically
 * has a default position at the start of the period. The properties of the periods and windows
 * (e.g. their durations and whether the window is seekable) will often only become known when the
 * player starts buffering the corresponding file or stream.
 *
 * <h2 id="live-limited">Live stream with limited availability</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-live-limited.svg" alt="Example timeline for
 * a live stream with limited availability">
 *
 * <p>A timeline for a live stream consists of a period whose duration is unknown, since it's
 * continually extending as more content is broadcast. If content only remains available for a
 * limited period of time then the window may start at a non-zero position, defining the region of
 * content that can still be played. The window will return true from {@link Window#isLive()} to
 * indicate it's a live stream and {@link Window#isDynamic} will be set to true as long as we expect
 * changes to the live window. Its default position is typically near to the live edge (indicated by
 * the black dot in the figure above).
 *
 * <h2>Live stream with indefinite availability</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-live-indefinite.svg" alt="Example timeline
 * for a live stream with indefinite availability">
 *
 * <p>A timeline for a live stream with indefinite availability is similar to the <a
 * href="#live-limited">Live stream with limited availability</a> case, except that the window
 * starts at the beginning of the period to indicate that all of the previously broadcast content
 * can still be played.
 *
 * <h2 id="live-multi-period">Live stream with multiple periods</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-live-multi-period.svg" alt="Example timeline
 * for a live stream with multiple periods">
 *
 * <p>This case arises when a live stream is explicitly divided into separate periods, for example
 * at content boundaries. This case is similar to the <a href="#live-limited">Live stream with
 * limited availability</a> case, except that the window may span more than one period. Multiple
 * periods are also possible in the indefinite availability case.
 *
 * <h2>On-demand stream followed by live stream</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-advanced.svg" alt="Example timeline for an
 * on-demand stream followed by a live stream">
 *
 * <p>This case is the concatenation of the <a href="#single-file">Single media file or on-demand
 * stream</a> and <a href="#multi-period">Live stream with multiple periods</a> cases. When playback
 * of the on-demand stream ends, playback of the live stream will start from its default position
 * near the live edge.
 *
 * <h2 id="single-file-midrolls">On-demand stream with mid-roll ads</h2>
 *
 * <p style="align:center"><img src="doc-files/timeline-single-file-midrolls.svg" alt="Example
 * timeline for an on-demand stream with mid-roll ad groups">
 *
 * <p>This case includes mid-roll ad groups, which are defined as part of the timeline's single
 * period. The period can be queried for information about the ad groups and the ads they contain.
 */
public abstract class Timeline implements Bundleable {

  /**
   * Holds information about a window in a {@link Timeline}. A window usually corresponds to one
   * playlist item and defines a region of media currently available for playback along with
   * additional information such as whether seeking is supported within the window. The figure below
   * shows some of the information defined by a window, as well as how this information relates to
   * corresponding {@link Period Periods} in the timeline.
   *
   * <p style="align:center"><img src="doc-files/timeline-window.svg" alt="Information defined by a
   * timeline window">
   */
  public static final class Window implements Bundleable {

    /**
     * A {@link #uid} for a window that must be used for single-window {@link Timeline Timelines}.
     */
    public static final Object SINGLE_WINDOW_UID = new Object();

    private static final Object FAKE_WINDOW_UID = new Object();

    private static final MediaItem PLACEHOLDER_MEDIA_ITEM =
        new MediaItem.Builder()
            .setMediaId("com.google.android.exoplayer2.Timeline")
            .setUri(Uri.EMPTY)
            .build();

    /**
     * A unique identifier for the window. Single-window {@link Timeline Timelines} must use {@link
     * #SINGLE_WINDOW_UID}.
     */
    public Object uid;

    /**
     * @deprecated Use {@link #mediaItem} instead.
     */
    @Deprecated @Nullable public Object tag;

    /** The {@link MediaItem} associated to the window. Not necessarily unique. */
    public MediaItem mediaItem;

    /** The manifest of the window. May be {@code null}. */
    @Nullable public Object manifest;

    /**
     * The start time of the presentation to which this window belongs in milliseconds since the
     * Unix epoch, or {@link C#TIME_UNSET} if unknown or not applicable. For informational purposes
     * only.
     */
    public long presentationStartTimeMs;

    /**
     * The window's start time in milliseconds since the Unix epoch, or {@link C#TIME_UNSET} if
     * unknown or not applicable.
     */
    public long windowStartTimeMs;

    /**
     * The offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix epoch
     * according to the clock of the media origin server, or {@link C#TIME_UNSET} if unknown or not
     * applicable.
     *
     * <p>Note that the current Unix time can be retrieved using {@link #getCurrentUnixTimeMs()} and
     * is calculated as {@code SystemClock.elapsedRealtime() + elapsedRealtimeEpochOffsetMs}.
     */
    public long elapsedRealtimeEpochOffsetMs;

    /** Whether it's possible to seek within this window. */
    public boolean isSeekable;

    // TODO: Split this to better describe which parts of the window might change. For example it
    // should be possible to individually determine whether the start and end positions of the
    // window may change relative to the underlying periods. For an example of where it's useful to
    // know that the end position is fixed whilst the start position may still change, see:
    // https://github.com/google/ExoPlayer/issues/4780.
    /** Whether this window may change when the timeline is updated. */
    public boolean isDynamic;

    /**
     * @deprecated Use {@link #isLive()} instead.
     */
    @Deprecated public boolean isLive;

    /**
     * The {@link MediaItem.LiveConfiguration} that is used or null if {@link #isLive()} returns
     * false.
     */
    @Nullable public MediaItem.LiveConfiguration liveConfiguration;

    /**
     * Whether this window contains placeholder information because the real information has yet to
     * be loaded.
     */
    public boolean isPlaceholder;

    /**
     * The default position relative to the start of the window at which to begin playback, in
     * microseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long defaultPositionUs;

    /** The duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public long durationUs;

    /** The index of the first period that belongs to this window. */
    public int firstPeriodIndex;

    /** The index of the last period that belongs to this window. */
    public int lastPeriodIndex;

    /**
     * The position of the start of this window relative to the start of the first period belonging
     * to it, in microseconds.
     */
    public long positionInFirstPeriodUs;

    /** Creates window. */
    public Window() {
      uid = SINGLE_WINDOW_UID;
      mediaItem = PLACEHOLDER_MEDIA_ITEM;
    }

    /** Sets the data held by this window. */
    @CanIgnoreReturnValue
    @SuppressWarnings("deprecation")
    public Window set(
        Object uid,
        @Nullable MediaItem mediaItem,
        @Nullable Object manifest,
        long presentationStartTimeMs,
        long windowStartTimeMs,
        long elapsedRealtimeEpochOffsetMs,
        boolean isSeekable,
        boolean isDynamic,
        @Nullable MediaItem.LiveConfiguration liveConfiguration,
        long defaultPositionUs,
        long durationUs,
        int firstPeriodIndex,
        int lastPeriodIndex,
        long positionInFirstPeriodUs) {
      this.uid = uid;
      this.mediaItem = mediaItem != null ? mediaItem : PLACEHOLDER_MEDIA_ITEM;
      this.tag =
          mediaItem != null && mediaItem.localConfiguration != null
              ? mediaItem.localConfiguration.tag
              : null;
      this.manifest = manifest;
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.isLive = liveConfiguration != null;
      this.liveConfiguration = liveConfiguration;
      this.defaultPositionUs = defaultPositionUs;
      this.durationUs = durationUs;
      this.firstPeriodIndex = firstPeriodIndex;
      this.lastPeriodIndex = lastPeriodIndex;
      this.positionInFirstPeriodUs = positionInFirstPeriodUs;
      this.isPlaceholder = false;
      return this;
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in milliseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long getDefaultPositionMs() {
      return Util.usToMs(defaultPositionUs);
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in microseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long getDefaultPositionUs() {
      return defaultPositionUs;
    }

    /** Returns the duration of the window in milliseconds, or {@link C#TIME_UNSET} if unknown. */
    public long getDurationMs() {
      return Util.usToMs(durationUs);
    }

    /** Returns the duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in milliseconds.
     */
    public long getPositionInFirstPeriodMs() {
      return Util.usToMs(positionInFirstPeriodUs);
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public long getPositionInFirstPeriodUs() {
      return positionInFirstPeriodUs;
    }

    /**
     * Returns the current time in milliseconds since the Unix epoch.
     *
     * <p>This method applies {@link #elapsedRealtimeEpochOffsetMs known corrections} made available
     * by the media such that this time corresponds to the clock of the media origin server.
     */
    public long getCurrentUnixTimeMs() {
      return Util.getNowUnixTimeMs(elapsedRealtimeEpochOffsetMs);
    }

    /** Returns whether this is a live stream. */
    // Verifies whether the deprecated isLive member field is in a correct state.
    @SuppressWarnings("deprecation")
    public boolean isLive() {
      checkState(isLive == (liveConfiguration != null));
      return liveConfiguration != null;
    }

    // Provide backward compatibility for tag.
    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || !getClass().equals(obj.getClass())) {
        return false;
      }
      Window that = (Window) obj;
      return Util.areEqual(uid, that.uid)
          && Util.areEqual(mediaItem, that.mediaItem)
          && Util.areEqual(manifest, that.manifest)
          && Util.areEqual(liveConfiguration, that.liveConfiguration)
          && presentationStartTimeMs == that.presentationStartTimeMs
          && windowStartTimeMs == that.windowStartTimeMs
          && elapsedRealtimeEpochOffsetMs == that.elapsedRealtimeEpochOffsetMs
          && isSeekable == that.isSeekable
          && isDynamic == that.isDynamic
          && isPlaceholder == that.isPlaceholder
          && defaultPositionUs == that.defaultPositionUs
          && durationUs == that.durationUs
          && firstPeriodIndex == that.firstPeriodIndex
          && lastPeriodIndex == that.lastPeriodIndex
          && positionInFirstPeriodUs == that.positionInFirstPeriodUs;
    }

    // Provide backward compatibility for tag.
    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + mediaItem.hashCode();
      result = 31 * result + (manifest == null ? 0 : manifest.hashCode());
      result = 31 * result + (liveConfiguration == null ? 0 : liveConfiguration.hashCode());
      result = 31 * result + (int) (presentationStartTimeMs ^ (presentationStartTimeMs >>> 32));
      result = 31 * result + (int) (windowStartTimeMs ^ (windowStartTimeMs >>> 32));
      result =
          31 * result
              + (int) (elapsedRealtimeEpochOffsetMs ^ (elapsedRealtimeEpochOffsetMs >>> 32));
      result = 31 * result + (isSeekable ? 1 : 0);
      result = 31 * result + (isDynamic ? 1 : 0);
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + (int) (defaultPositionUs ^ (defaultPositionUs >>> 32));
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + firstPeriodIndex;
      result = 31 * result + lastPeriodIndex;
      result = 31 * result + (int) (positionInFirstPeriodUs ^ (positionInFirstPeriodUs >>> 32));
      return result;
    }

    // Bundleable implementation.

    private static final String FIELD_MEDIA_ITEM = Util.intToStringMaxRadix(1);
    private static final String FIELD_PRESENTATION_START_TIME_MS = Util.intToStringMaxRadix(2);
    private static final String FIELD_WINDOW_START_TIME_MS = Util.intToStringMaxRadix(3);
    private static final String FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS =
        Util.intToStringMaxRadix(4);
    private static final String FIELD_IS_SEEKABLE = Util.intToStringMaxRadix(5);
    private static final String FIELD_IS_DYNAMIC = Util.intToStringMaxRadix(6);
    private static final String FIELD_LIVE_CONFIGURATION = Util.intToStringMaxRadix(7);
    private static final String FIELD_IS_PLACEHOLDER = Util.intToStringMaxRadix(8);
    private static final String FIELD_DEFAULT_POSITION_US = Util.intToStringMaxRadix(9);
    private static final String FIELD_DURATION_US = Util.intToStringMaxRadix(10);
    private static final String FIELD_FIRST_PERIOD_INDEX = Util.intToStringMaxRadix(11);
    private static final String FIELD_LAST_PERIOD_INDEX = Util.intToStringMaxRadix(12);
    private static final String FIELD_POSITION_IN_FIRST_PERIOD_US = Util.intToStringMaxRadix(13);

    /**
     * {@inheritDoc}
     *
     * <p>It omits the {@link #uid} and {@link #manifest} fields. The {@link #uid} of an instance
     * restored by {@link #CREATOR} will be a fake {@link Object} and the {@link #manifest} of the
     * instance will be {@code null}.
     */
    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (!MediaItem.EMPTY.equals(mediaItem)) {
        bundle.putBundle(FIELD_MEDIA_ITEM, mediaItem.toBundle());
      }
      if (presentationStartTimeMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_PRESENTATION_START_TIME_MS, presentationStartTimeMs);
      }
      if (windowStartTimeMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_WINDOW_START_TIME_MS, windowStartTimeMs);
      }
      if (elapsedRealtimeEpochOffsetMs != C.TIME_UNSET) {
        bundle.putLong(FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS, elapsedRealtimeEpochOffsetMs);
      }
      if (isSeekable) {
        bundle.putBoolean(FIELD_IS_SEEKABLE, isSeekable);
      }
      if (isDynamic) {
        bundle.putBoolean(FIELD_IS_DYNAMIC, isDynamic);
      }

      @Nullable MediaItem.LiveConfiguration liveConfiguration = this.liveConfiguration;
      if (liveConfiguration != null) {
        bundle.putBundle(FIELD_LIVE_CONFIGURATION, liveConfiguration.toBundle());
      }
      if (isPlaceholder) {
        bundle.putBoolean(FIELD_IS_PLACEHOLDER, isPlaceholder);
      }
      if (defaultPositionUs != 0) {
        bundle.putLong(FIELD_DEFAULT_POSITION_US, defaultPositionUs);
      }
      if (durationUs != C.TIME_UNSET) {
        bundle.putLong(FIELD_DURATION_US, durationUs);
      }
      if (firstPeriodIndex != 0) {
        bundle.putInt(FIELD_FIRST_PERIOD_INDEX, firstPeriodIndex);
      }
      if (lastPeriodIndex != 0) {
        bundle.putInt(FIELD_LAST_PERIOD_INDEX, lastPeriodIndex);
      }
      if (positionInFirstPeriodUs != 0) {
        bundle.putLong(FIELD_POSITION_IN_FIRST_PERIOD_US, positionInFirstPeriodUs);
      }
      return bundle;
    }

    /**
     * Object that can restore {@link Period} from a {@link Bundle}.
     *
     * <p>The {@link #uid} of a restored instance will be a fake {@link Object} and the {@link
     * #manifest} of the instance will be {@code null}.
     */
    public static final Creator<Window> CREATOR = Window::fromBundle;

    private static Window fromBundle(Bundle bundle) {
      @Nullable Bundle mediaItemBundle = bundle.getBundle(FIELD_MEDIA_ITEM);
      @Nullable
      MediaItem mediaItem =
          mediaItemBundle != null ? MediaItem.CREATOR.fromBundle(mediaItemBundle) : MediaItem.EMPTY;
      long presentationStartTimeMs =
          bundle.getLong(FIELD_PRESENTATION_START_TIME_MS, /* defaultValue= */ C.TIME_UNSET);
      long windowStartTimeMs =
          bundle.getLong(FIELD_WINDOW_START_TIME_MS, /* defaultValue= */ C.TIME_UNSET);
      long elapsedRealtimeEpochOffsetMs =
          bundle.getLong(FIELD_ELAPSED_REALTIME_EPOCH_OFFSET_MS, /* defaultValue= */ C.TIME_UNSET);
      boolean isSeekable = bundle.getBoolean(FIELD_IS_SEEKABLE, /* defaultValue= */ false);
      boolean isDynamic = bundle.getBoolean(FIELD_IS_DYNAMIC, /* defaultValue= */ false);
      @Nullable Bundle liveConfigurationBundle = bundle.getBundle(FIELD_LIVE_CONFIGURATION);
      @Nullable
      MediaItem.LiveConfiguration liveConfiguration =
          liveConfigurationBundle != null
              ? MediaItem.LiveConfiguration.CREATOR.fromBundle(liveConfigurationBundle)
              : null;
      boolean isPlaceHolder = bundle.getBoolean(FIELD_IS_PLACEHOLDER, /* defaultValue= */ false);
      long defaultPositionUs = bundle.getLong(FIELD_DEFAULT_POSITION_US, /* defaultValue= */ 0);
      long durationUs = bundle.getLong(FIELD_DURATION_US, /* defaultValue= */ C.TIME_UNSET);
      int firstPeriodIndex = bundle.getInt(FIELD_FIRST_PERIOD_INDEX, /* defaultValue= */ 0);
      int lastPeriodIndex = bundle.getInt(FIELD_LAST_PERIOD_INDEX, /* defaultValue= */ 0);
      long positionInFirstPeriodUs =
          bundle.getLong(FIELD_POSITION_IN_FIRST_PERIOD_US, /* defaultValue= */ 0);

      Window window = new Window();
      window.set(
          FAKE_WINDOW_UID,
          mediaItem,
          /* manifest= */ null,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          isSeekable,
          isDynamic,
          liveConfiguration,
          defaultPositionUs,
          durationUs,
          firstPeriodIndex,
          lastPeriodIndex,
          positionInFirstPeriodUs);
      window.isPlaceholder = isPlaceHolder;
      return window;
    }
  }

  /**
   * Holds information about a period in a {@link Timeline}. A period defines a single logical piece
   * of media, for example a media file. It may also define groups of ads inserted into the media,
   * along with information about whether those ads have been loaded and played.
   *
   * <p>The figure below shows some of the information defined by a period, as well as how this
   * information relates to a corresponding {@link Window} in the timeline.
   *
   * <p style="align:center"><img src="doc-files/timeline-period.svg" alt="Information defined by a
   * period">
   */
  public static final class Period implements Bundleable {

    /**
     * An identifier for the period. Not necessarily unique. May be null if the ids of the period
     * are not required.
     */
    @Nullable public Object id;

    /**
     * A unique identifier for the period. May be null if the ids of the period are not required.
     */
    @Nullable public Object uid;

    /** The index of the window to which this period belongs. */
    public int windowIndex;

    /** The duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public long durationUs;

    /**
     * The position of the start of this period relative to the start of the window to which it
     * belongs, in microseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long positionInWindowUs;

    /**
     * Whether this period contains placeholder information because the real information has yet to
     * be loaded.
     */
    public boolean isPlaceholder;

    private AdPlaybackState adPlaybackState;

    /** Creates a new instance with no ad playback state. */
    public Period() {
      adPlaybackState = AdPlaybackState.NONE;
    }

    /**
     * Sets the data held by this period.
     *
     * @param id An identifier for the period. Not necessarily unique. May be null if the ids of the
     *     period are not required.
     * @param uid A unique identifier for the period. May be null if the ids of the period are not
     *     required.
     * @param windowIndex The index of the window to which this period belongs.
     * @param durationUs The duration of this period in microseconds, or {@link C#TIME_UNSET} if
     *     unknown.
     * @param positionInWindowUs The position of the start of this period relative to the start of
     *     the window to which it belongs, in milliseconds. May be negative if the start of the
     *     period is not within the window.
     * @return This period, for convenience.
     */
    @CanIgnoreReturnValue
    public Period set(
        @Nullable Object id,
        @Nullable Object uid,
        int windowIndex,
        long durationUs,
        long positionInWindowUs) {
      return set(
          id,
          uid,
          windowIndex,
          durationUs,
          positionInWindowUs,
          AdPlaybackState.NONE,
          /* isPlaceholder= */ false);
    }

    /**
     * Sets the data held by this period.
     *
     * @param id An identifier for the period. Not necessarily unique. May be null if the ids of the
     *     period are not required.
     * @param uid A unique identifier for the period. May be null if the ids of the period are not
     *     required.
     * @param windowIndex The index of the window to which this period belongs.
     * @param durationUs The duration of this period in microseconds, or {@link C#TIME_UNSET} if
     *     unknown.
     * @param positionInWindowUs The position of the start of this period relative to the start of
     *     the window to which it belongs, in milliseconds. May be negative if the start of the
     *     period is not within the window.
     * @param adPlaybackState The state of the period's ads, or {@link AdPlaybackState#NONE} if
     *     there are no ads.
     * @param isPlaceholder Whether this period contains placeholder information because the real
     *     information has yet to be loaded.
     * @return This period, for convenience.
     */
    @CanIgnoreReturnValue
    public Period set(
        @Nullable Object id,
        @Nullable Object uid,
        int windowIndex,
        long durationUs,
        long positionInWindowUs,
        AdPlaybackState adPlaybackState,
        boolean isPlaceholder) {
      this.id = id;
      this.uid = uid;
      this.windowIndex = windowIndex;
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
      this.adPlaybackState = adPlaybackState;
      this.isPlaceholder = isPlaceholder;
      return this;
    }

    /** Returns the duration of the period in milliseconds, or {@link C#TIME_UNSET} if unknown. */
    public long getDurationMs() {
      return Util.usToMs(durationUs);
    }

    /** Returns the duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in milliseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowMs() {
      return Util.usToMs(positionInWindowUs);
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in microseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowUs() {
      return positionInWindowUs;
    }

    /** Returns the opaque identifier for ads played with this period, or {@code null} if unset. */
    @Nullable
    public Object getAdsId() {
      return adPlaybackState.adsId;
    }

    /** Returns the number of ad groups in the period. */
    public int getAdGroupCount() {
      return adPlaybackState.adGroupCount;
    }

    /**
     * Returns the number of removed ad groups in the period. Ad groups with indices between {@code
     * 0} (inclusive) and {@code removedAdGroupCount} (exclusive) will be empty.
     */
    public int getRemovedAdGroupCount() {
      return adPlaybackState.removedAdGroupCount;
    }

    /**
     * Returns the time of the ad group at index {@code adGroupIndex} in the period, in
     * microseconds.
     *
     * @param adGroupIndex The ad group index.
     * @return The time of the ad group at the index relative to the start of the enclosing {@link
     *     Period}, in microseconds, or {@link C#TIME_END_OF_SOURCE} for a post-roll ad group.
     */
    public long getAdGroupTimeUs(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).timeUs;
    }

    /**
     * Returns the index of the first ad in the specified ad group that should be played, or the
     * number of ads in the ad group if no ads should be played.
     *
     * @param adGroupIndex The ad group index.
     * @return The index of the first ad that should be played, or the number of ads in the ad group
     *     if no ads should be played.
     */
    public int getFirstAdIndexToPlay(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).getFirstAdIndexToPlay();
    }

    /**
     * Returns the index of the next ad in the specified ad group that should be played after
     * playing {@code adIndexInAdGroup}, or the number of ads in the ad group if no later ads should
     * be played.
     *
     * @param adGroupIndex The ad group index.
     * @param lastPlayedAdIndex The last played ad index in the ad group.
     * @return The index of the next ad that should be played, or the number of ads in the ad group
     *     if the ad group does not have any ads remaining to play.
     */
    public int getNextAdIndexToPlay(int adGroupIndex, int lastPlayedAdIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).getNextAdIndexToPlay(lastPlayedAdIndex);
    }

    /**
     * Returns whether all ads in the ad group at index {@code adGroupIndex} have been played,
     * skipped or failed.
     *
     * @param adGroupIndex The ad group index.
     * @return Whether all ads in the ad group at index {@code adGroupIndex} have been played,
     *     skipped or failed.
     */
    public boolean hasPlayedAdGroup(int adGroupIndex) {
      return !adPlaybackState.getAdGroup(adGroupIndex).hasUnplayedAds();
    }

    /**
     * Returns the index of the ad group at or before {@code positionUs} in the period that should
     * be played before the content at {@code positionUs}. Returns {@link C#INDEX_UNSET} if the ad
     * group at or before {@code positionUs} has no ads remaining to be played, or if there is no
     * such ad group.
     *
     * @param positionUs The period position at or before which to find an ad group, in
     *     microseconds.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    public int getAdGroupIndexForPositionUs(long positionUs) {
      return adPlaybackState.getAdGroupIndexForPositionUs(positionUs, durationUs);
    }

    /**
     * Returns the index of the next ad group after {@code positionUs} in the period that has ads
     * that should be played. Returns {@link C#INDEX_UNSET} if there is no such ad group.
     *
     * @param positionUs The period position after which to find an ad group, in microseconds.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    public int getAdGroupIndexAfterPositionUs(long positionUs) {
      return adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, durationUs);
    }

    /**
     * Returns the number of ads in the ad group at index {@code adGroupIndex}, or {@link
     * C#LENGTH_UNSET} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @return The number of ads in the ad group, or {@link C#LENGTH_UNSET} if not yet known.
     */
    public int getAdCountInAdGroup(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).count;
    }

    /**
     * Returns the duration of the ad at index {@code adIndexInAdGroup} in the ad group at {@code
     * adGroupIndex}, in microseconds, or {@link C#TIME_UNSET} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @param adIndexInAdGroup The ad index in the ad group.
     * @return The duration of the ad, or {@link C#TIME_UNSET} if not yet known.
     */
    public long getAdDurationUs(int adGroupIndex, int adIndexInAdGroup) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
      return adGroup.count != C.LENGTH_UNSET ? adGroup.durationsUs[adIndexInAdGroup] : C.TIME_UNSET;
    }

    /**
     * Returns the state of the ad at index {@code adIndexInAdGroup} in the ad group at {@code
     * adGroupIndex}, or {@link AdPlaybackState#AD_STATE_UNAVAILABLE} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @return The state of the ad, or {@link AdPlaybackState#AD_STATE_UNAVAILABLE} if not yet
     *     known.
     */
    public int getAdState(int adGroupIndex, int adIndexInAdGroup) {
      AdPlaybackState.AdGroup adGroup = adPlaybackState.getAdGroup(adGroupIndex);
      return adGroup.count != C.LENGTH_UNSET
          ? adGroup.states[adIndexInAdGroup]
          : AD_STATE_UNAVAILABLE;
    }

    /**
     * Returns the position offset in the first unplayed ad at which to begin playback, in
     * microseconds.
     */
    public long getAdResumePositionUs() {
      return adPlaybackState.adResumePositionUs;
    }

    /**
     * Returns whether the ad group at index {@code adGroupIndex} is server-side inserted and part
     * of the content stream.
     *
     * @param adGroupIndex The ad group index.
     * @return Whether this ad group is server-side inserted and part of the content stream.
     */
    public boolean isServerSideInsertedAdGroup(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).isServerSideInserted;
    }

    /**
     * Returns the offset in microseconds which should be added to the content stream when resuming
     * playback after the specified ad group.
     *
     * @param adGroupIndex The ad group index.
     * @return The offset that should be added to the content stream, in microseconds.
     */
    public long getContentResumeOffsetUs(int adGroupIndex) {
      return adPlaybackState.getAdGroup(adGroupIndex).contentResumeOffsetUs;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || !getClass().equals(obj.getClass())) {
        return false;
      }
      Period that = (Period) obj;
      return Util.areEqual(id, that.id)
          && Util.areEqual(uid, that.uid)
          && windowIndex == that.windowIndex
          && durationUs == that.durationUs
          && positionInWindowUs == that.positionInWindowUs
          && isPlaceholder == that.isPlaceholder
          && Util.areEqual(adPlaybackState, that.adPlaybackState);
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + (uid == null ? 0 : uid.hashCode());
      result = 31 * result + windowIndex;
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + (int) (positionInWindowUs ^ (positionInWindowUs >>> 32));
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + adPlaybackState.hashCode();
      return result;
    }

    // Bundleable implementation.

    private static final String FIELD_WINDOW_INDEX = Util.intToStringMaxRadix(0);
    private static final String FIELD_DURATION_US = Util.intToStringMaxRadix(1);
    private static final String FIELD_POSITION_IN_WINDOW_US = Util.intToStringMaxRadix(2);
    private static final String FIELD_PLACEHOLDER = Util.intToStringMaxRadix(3);
    private static final String FIELD_AD_PLAYBACK_STATE = Util.intToStringMaxRadix(4);

    /**
     * {@inheritDoc}
     *
     * <p>It omits the {@link #id} and {@link #uid} fields so these fields of an instance restored
     * by {@link #CREATOR} will always be {@code null}.
     */
    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      if (windowIndex != 0) {
        bundle.putInt(FIELD_WINDOW_INDEX, windowIndex);
      }
      if (durationUs != C.TIME_UNSET) {
        bundle.putLong(FIELD_DURATION_US, durationUs);
      }
      if (positionInWindowUs != 0) {
        bundle.putLong(FIELD_POSITION_IN_WINDOW_US, positionInWindowUs);
      }
      if (isPlaceholder) {
        bundle.putBoolean(FIELD_PLACEHOLDER, isPlaceholder);
      }
      if (!adPlaybackState.equals(AdPlaybackState.NONE)) {
        bundle.putBundle(FIELD_AD_PLAYBACK_STATE, adPlaybackState.toBundle());
      }
      return bundle;
    }

    /**
     * Object that can restore {@link Period} from a {@link Bundle}.
     *
     * <p>The {@link #id} and {@link #uid} of restored instances will always be {@code null}.
     */
    public static final Creator<Period> CREATOR = Period::fromBundle;

    private static Period fromBundle(Bundle bundle) {
      int windowIndex = bundle.getInt(FIELD_WINDOW_INDEX, /* defaultValue= */ 0);
      long durationUs = bundle.getLong(FIELD_DURATION_US, /* defaultValue= */ C.TIME_UNSET);
      long positionInWindowUs = bundle.getLong(FIELD_POSITION_IN_WINDOW_US, /* defaultValue= */ 0);
      boolean isPlaceholder = bundle.getBoolean(FIELD_PLACEHOLDER, /* defaultValue= */ false);
      @Nullable Bundle adPlaybackStateBundle = bundle.getBundle(FIELD_AD_PLAYBACK_STATE);
      AdPlaybackState adPlaybackState =
          adPlaybackStateBundle != null
              ? AdPlaybackState.CREATOR.fromBundle(adPlaybackStateBundle)
              : AdPlaybackState.NONE;

      Period period = new Period();
      period.set(
          /* id= */ null,
          /* uid= */ null,
          windowIndex,
          durationUs,
          positionInWindowUs,
          adPlaybackState,
          isPlaceholder);
      return period;
    }
  }

  /** An empty timeline. */
  public static final Timeline EMPTY =
      new Timeline() {

        @Override
        public int getWindowCount() {
          return 0;
        }

        @Override
        public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
          throw new IndexOutOfBoundsException();
        }

        @Override
        public int getPeriodCount() {
          return 0;
        }

        @Override
        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
          throw new IndexOutOfBoundsException();
        }

        @Override
        public int getIndexOfPeriod(Object uid) {
          return C.INDEX_UNSET;
        }

        @Override
        public Object getUidOfPeriod(int periodIndex) {
          throw new IndexOutOfBoundsException();
        }
      };

  protected Timeline() {}

  /** Returns whether the timeline is empty. */
  public final boolean isEmpty() {
    return getWindowCount() == 0;
  }

  /** Returns the number of windows in the timeline. */
  public abstract int getWindowCount();

  /**
   * Returns the index of the window after the window at index {@code windowIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param windowIndex Index of a window in the timeline.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the next window, or {@link C#INDEX_UNSET} if this is the last window.
   */
  public int getNextWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getLastWindowIndex(shuffleModeEnabled)
            ? C.INDEX_UNSET
            : windowIndex + 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getLastWindowIndex(shuffleModeEnabled)
            ? getFirstWindowIndex(shuffleModeEnabled)
            : windowIndex + 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the index of the window before the window at index {@code windowIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param windowIndex Index of a window in the timeline.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the previous window, or {@link C#INDEX_UNSET} if this is the first window.
   */
  public int getPreviousWindowIndex(
      int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getFirstWindowIndex(shuffleModeEnabled)
            ? C.INDEX_UNSET
            : windowIndex - 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getFirstWindowIndex(shuffleModeEnabled)
            ? getLastWindowIndex(shuffleModeEnabled)
            : windowIndex - 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the index of the last window in the playback order depending on whether shuffling is
   * enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the last window in the playback order, or {@link C#INDEX_UNSET} if the
   *     timeline is empty.
   */
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    return isEmpty() ? C.INDEX_UNSET : getWindowCount() - 1;
  }

  /**
   * Returns the index of the first window in the playback order depending on whether shuffling is
   * enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the first window in the playback order, or {@link C#INDEX_UNSET} if the
   *     timeline is empty.
   */
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    return isEmpty() ? C.INDEX_UNSET : 0;
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @return The populated {@link Window}, for convenience.
   */
  public final Window getWindow(int windowIndex, Window window) {
    return getWindow(windowIndex, window, /* defaultPositionProjectionUs= */ 0);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param defaultPositionProjectionUs A duration into the future that the populated window's
   *     default start position should be projected.
   * @return The populated {@link Window}, for convenience.
   */
  public abstract Window getWindow(
      int windowIndex, Window window, long defaultPositionProjectionUs);

  /** Returns the number of periods in the timeline. */
  public abstract int getPeriodCount();

  /**
   * Returns the index of the period after the period at index {@code periodIndex} depending on the
   * {@code repeatMode} and whether shuffling is enabled.
   *
   * @param periodIndex Index of a period in the timeline.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return The index of the next period, or {@link C#INDEX_UNSET} if this is the last period.
   */
  public final int getNextPeriodIndex(
      int periodIndex,
      Period period,
      Window window,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    int windowIndex = getPeriod(periodIndex, period).windowIndex;
    if (getWindow(windowIndex, window).lastPeriodIndex == periodIndex) {
      int nextWindowIndex = getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
      if (nextWindowIndex == C.INDEX_UNSET) {
        return C.INDEX_UNSET;
      }
      return getWindow(nextWindowIndex, window).firstPeriodIndex;
    }
    return periodIndex + 1;
  }

  /**
   * Returns whether the given period is the last period of the timeline depending on the {@code
   * repeatMode} and whether shuffling is enabled.
   *
   * @param periodIndex A period index.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @param shuffleModeEnabled Whether shuffling is enabled.
   * @return Whether the period of the given index is the last period of the timeline.
   */
  public final boolean isLastPeriod(
      int periodIndex,
      Period period,
      Window window,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    return getNextPeriodIndex(periodIndex, period, window, repeatMode, shuffleModeEnabled)
        == C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getPeriodPositionUs(Window, Period, int, long)} instead.
   */
  @Deprecated
  @InlineMe(replacement = "this.getPeriodPositionUs(window, period, windowIndex, windowPositionUs)")
  public final Pair<Object, Long> getPeriodPosition(
      Window window, Period period, int windowIndex, long windowPositionUs) {
    return getPeriodPositionUs(window, period, windowIndex, windowPositionUs);
  }
  /**
   * @deprecated Use {@link #getPeriodPositionUs(Window, Period, int, long, long)} instead.
   */
  @Deprecated
  @Nullable
  @InlineMe(
      replacement =
          "this.getPeriodPositionUs("
              + "window, period, windowIndex, windowPositionUs, defaultPositionProjectionUs)")
  public final Pair<Object, Long> getPeriodPosition(
      Window window,
      Period period,
      int windowIndex,
      long windowPositionUs,
      long defaultPositionProjectionUs) {
    return getPeriodPositionUs(
        window, period, windowIndex, windowPositionUs, defaultPositionProjectionUs);
  }

  /**
   * Calls {@link #getPeriodPositionUs(Window, Period, int, long)} with a zero default position
   * projection.
   */
  public final Pair<Object, Long> getPeriodPositionUs(
      Window window, Period period, int windowIndex, long windowPositionUs) {
    return Assertions.checkNotNull(
        getPeriodPositionUs(
            window, period, windowIndex, windowPositionUs, /* defaultPositionProjectionUs= */ 0));
  }

  /**
   * Converts {@code (windowIndex, windowPositionUs)} to the corresponding {@code (periodUid,
   * periodPositionUs)}. The returned {@code periodPositionUs} is constrained to be non-negative,
   * and to be less than the containing period's duration if it is known.
   *
   * @param window A {@link Window} that may be overwritten.
   * @param period A {@link Period} that may be overwritten.
   * @param windowIndex The window index.
   * @param windowPositionUs The window time, or {@link C#TIME_UNSET} to use the window's default
   *     start position.
   * @param defaultPositionProjectionUs If {@code windowPositionUs} is {@link C#TIME_UNSET}, the
   *     duration into the future by which the window's position should be projected.
   * @return The corresponding (periodUid, periodPositionUs), or null if {@code #windowPositionUs}
   *     is {@link C#TIME_UNSET}, {@code defaultPositionProjectionUs} is non-zero, and the window's
   *     position could not be projected by {@code defaultPositionProjectionUs}.
   */
  @Nullable
  public final Pair<Object, Long> getPeriodPositionUs(
      Window window,
      Period period,
      int windowIndex,
      long windowPositionUs,
      long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, getWindowCount());
    getWindow(windowIndex, window, defaultPositionProjectionUs);
    if (windowPositionUs == C.TIME_UNSET) {
      windowPositionUs = window.getDefaultPositionUs();
      if (windowPositionUs == C.TIME_UNSET) {
        return null;
      }
    }
    int periodIndex = window.firstPeriodIndex;
    getPeriod(periodIndex, period);
    while (periodIndex < window.lastPeriodIndex
        && period.positionInWindowUs != windowPositionUs
        && getPeriod(periodIndex + 1, period).positionInWindowUs <= windowPositionUs) {
      periodIndex++;
    }
    getPeriod(periodIndex, period, /* setIds= */ true);
    long periodPositionUs = windowPositionUs - period.positionInWindowUs;
    // The period positions must be less than the period duration, if it is known.
    if (period.durationUs != C.TIME_UNSET) {
      periodPositionUs = min(periodPositionUs, period.durationUs - 1);
    }
    // Period positions cannot be negative.
    periodPositionUs = max(0, periodPositionUs);
    return Pair.create(Assertions.checkNotNull(period.uid), periodPositionUs);
  }

  /**
   * Populates a {@link Period} with data for the period with the specified unique identifier.
   *
   * @param periodUid The unique identifier of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @return The populated {@link Period}, for convenience.
   */
  public Period getPeriodByUid(Object periodUid, Period period) {
    return getPeriod(getIndexOfPeriod(periodUid), period, /* setIds= */ true);
  }

  /**
   * Populates a {@link Period} with data for the period at the specified index. {@link Period#id}
   * and {@link Period#uid} will be set to null.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @return The populated {@link Period}, for convenience.
   */
  public final Period getPeriod(int periodIndex, Period period) {
    return getPeriod(periodIndex, period, false);
  }

  /**
   * Populates a {@link Period} with data for the period at the specified index.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @param setIds Whether {@link Period#id} and {@link Period#uid} should be populated. If false,
   *     the fields will be set to null. The caller should pass false for efficiency reasons unless
   *     the fields are required.
   * @return The populated {@link Period}, for convenience.
   */
  public abstract Period getPeriod(int periodIndex, Period period, boolean setIds);

  /**
   * Returns the index of the period identified by its unique {@link Period#uid}, or {@link
   * C#INDEX_UNSET} if the period is not in the timeline.
   *
   * @param uid A unique identifier for a period.
   * @return The index of the period, or {@link C#INDEX_UNSET} if the period was not found.
   */
  public abstract int getIndexOfPeriod(Object uid);

  /**
   * Returns the unique id of the period identified by its index in the timeline.
   *
   * @param periodIndex The index of the period.
   * @return The unique id of the period.
   */
  public abstract Object getUidOfPeriod(int periodIndex);

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Timeline)) {
      return false;
    }
    Timeline other = (Timeline) obj;
    if (other.getWindowCount() != getWindowCount() || other.getPeriodCount() != getPeriodCount()) {
      return false;
    }
    Timeline.Window window = new Timeline.Window();
    Timeline.Period period = new Timeline.Period();
    Timeline.Window otherWindow = new Timeline.Window();
    Timeline.Period otherPeriod = new Timeline.Period();
    for (int i = 0; i < getWindowCount(); i++) {
      if (!getWindow(i, window).equals(other.getWindow(i, otherWindow))) {
        return false;
      }
    }
    for (int i = 0; i < getPeriodCount(); i++) {
      if (!getPeriod(i, period, /* setIds= */ true)
          .equals(other.getPeriod(i, otherPeriod, /* setIds= */ true))) {
        return false;
      }
    }

    // Check shuffled order
    int windowIndex = getFirstWindowIndex(/* shuffleModeEnabled= */ true);
    if (windowIndex != other.getFirstWindowIndex(/* shuffleModeEnabled= */ true)) {
      return false;
    }
    int lastWindowIndex = getLastWindowIndex(/* shuffleModeEnabled= */ true);
    if (lastWindowIndex != other.getLastWindowIndex(/* shuffleModeEnabled= */ true)) {
      return false;
    }
    while (windowIndex != lastWindowIndex) {
      int nextWindowIndex =
          getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true);
      if (nextWindowIndex
          != other.getNextWindowIndex(
              windowIndex, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true)) {
        return false;
      }
      windowIndex = nextWindowIndex;
    }

    return true;
  }

  @Override
  public int hashCode() {
    Window window = new Window();
    Period period = new Period();
    int result = 7;
    result = 31 * result + getWindowCount();
    for (int i = 0; i < getWindowCount(); i++) {
      result = 31 * result + getWindow(i, window).hashCode();
    }
    result = 31 * result + getPeriodCount();
    for (int i = 0; i < getPeriodCount(); i++) {
      result = 31 * result + getPeriod(i, period, /* setIds= */ true).hashCode();
    }

    for (int windowIndex = getFirstWindowIndex(true);
        windowIndex != C.INDEX_UNSET;
        windowIndex = getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)) {
      result = 31 * result + windowIndex;
    }

    return result;
  }

  // Bundleable implementation.

  private static final String FIELD_WINDOWS = Util.intToStringMaxRadix(0);
  private static final String FIELD_PERIODS = Util.intToStringMaxRadix(1);
  private static final String FIELD_SHUFFLED_WINDOW_INDICES = Util.intToStringMaxRadix(2);

  /**
   * {@inheritDoc}
   *
   * <p>The {@link #getWindow(int, Window)} windows} and {@link #getPeriod(int, Period) periods} of
   * an instance restored by {@link #CREATOR} may have missing fields as described in {@link
   * Window#toBundle()} and {@link Period#toBundle()}.
   */
  @Override
  public final Bundle toBundle() {
    List<Bundle> windowBundles = new ArrayList<>();
    int windowCount = getWindowCount();
    Window window = new Window();
    for (int i = 0; i < windowCount; i++) {
      windowBundles.add(getWindow(i, window, /* defaultPositionProjectionUs= */ 0).toBundle());
    }

    List<Bundle> periodBundles = new ArrayList<>();
    int periodCount = getPeriodCount();
    Period period = new Period();
    for (int i = 0; i < periodCount; i++) {
      periodBundles.add(getPeriod(i, period, /* setIds= */ false).toBundle());
    }

    int[] shuffledWindowIndices = new int[windowCount];
    if (windowCount > 0) {
      shuffledWindowIndices[0] = getFirstWindowIndex(/* shuffleModeEnabled= */ true);
    }
    for (int i = 1; i < windowCount; i++) {
      shuffledWindowIndices[i] =
          getNextWindowIndex(
              shuffledWindowIndices[i - 1], Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true);
    }

    Bundle bundle = new Bundle();
    BundleUtil.putBinder(bundle, FIELD_WINDOWS, new BundleListRetriever(windowBundles));
    BundleUtil.putBinder(bundle, FIELD_PERIODS, new BundleListRetriever(periodBundles));
    bundle.putIntArray(FIELD_SHUFFLED_WINDOW_INDICES, shuffledWindowIndices);
    return bundle;
  }

  /**
   * Returns a {@link Bundle} containing just the specified {@link Window}.
   *
   * <p>The {@link #getWindow(int, Window)} windows} and {@link #getPeriod(int, Period) periods} of
   * an instance restored by {@link #CREATOR} may have missing fields as described in {@link
   * Window#toBundle()} and {@link Period#toBundle()}.
   *
   * @param windowIndex The index of the {@link Window} to include in the {@link Bundle}.
   */
  public final Bundle toBundleWithOneWindowOnly(int windowIndex) {
    Window window = getWindow(windowIndex, new Window(), /* defaultPositionProjectionUs= */ 0);

    List<Bundle> periodBundles = new ArrayList<>();
    Period period = new Period();
    for (int i = window.firstPeriodIndex; i <= window.lastPeriodIndex; i++) {
      getPeriod(i, period, /* setIds= */ false);
      period.windowIndex = 0;
      periodBundles.add(period.toBundle());
    }

    window.lastPeriodIndex = window.lastPeriodIndex - window.firstPeriodIndex;
    window.firstPeriodIndex = 0;
    Bundle windowBundle = window.toBundle();

    Bundle bundle = new Bundle();
    BundleUtil.putBinder(
        bundle, FIELD_WINDOWS, new BundleListRetriever(ImmutableList.of(windowBundle)));
    BundleUtil.putBinder(bundle, FIELD_PERIODS, new BundleListRetriever(periodBundles));
    bundle.putIntArray(FIELD_SHUFFLED_WINDOW_INDICES, new int[] {0});
    return bundle;
  }

  /**
   * Object that can restore a {@link Timeline} from a {@link Bundle}.
   *
   * <p>The {@link #getWindow(int, Window)} windows} and {@link #getPeriod(int, Period) periods} of
   * a restored instance may have missing fields as described in {@link Window#CREATOR} and {@link
   * Period#CREATOR}.
   */
  public static final Creator<Timeline> CREATOR = Timeline::fromBundle;

  private static Timeline fromBundle(Bundle bundle) {
    ImmutableList<Window> windows =
        fromBundleListRetriever(Window.CREATOR, BundleUtil.getBinder(bundle, FIELD_WINDOWS));
    ImmutableList<Period> periods =
        fromBundleListRetriever(Period.CREATOR, BundleUtil.getBinder(bundle, FIELD_PERIODS));
    @Nullable int[] shuffledWindowIndices = bundle.getIntArray(FIELD_SHUFFLED_WINDOW_INDICES);
    return new RemotableTimeline(
        windows,
        periods,
        shuffledWindowIndices == null
            ? generateUnshuffledIndices(windows.size())
            : shuffledWindowIndices);
  }

  private static <T extends Bundleable> ImmutableList<T> fromBundleListRetriever(
      Creator<T> creator, @Nullable IBinder binder) {
    if (binder == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<T> builder = new ImmutableList.Builder<>();
    List<Bundle> bundleList = BundleListRetriever.getList(binder);
    for (int i = 0; i < bundleList.size(); i++) {
      builder.add(creator.fromBundle(bundleList.get(i)));
    }
    return builder.build();
  }

  private static int[] generateUnshuffledIndices(int n) {
    int[] indices = new int[n];
    for (int i = 0; i < n; i++) {
      indices[i] = i;
    }
    return indices;
  }

  /**
   * A concrete class of {@link Timeline} to restore a {@link Timeline} instance from a {@link
   * Bundle} sent by another process via {@link IBinder}.
   */
  public static final class RemotableTimeline extends Timeline {

    private final ImmutableList<Window> windows;
    private final ImmutableList<Period> periods;
    private final int[] shuffledWindowIndices;
    private final int[] windowIndicesInShuffled;

    public RemotableTimeline(
        ImmutableList<Window> windows, ImmutableList<Period> periods, int[] shuffledWindowIndices) {
      checkArgument(windows.size() == shuffledWindowIndices.length);
      this.windows = windows;
      this.periods = periods;
      this.shuffledWindowIndices = shuffledWindowIndices;
      windowIndicesInShuffled = new int[shuffledWindowIndices.length];
      for (int i = 0; i < shuffledWindowIndices.length; i++) {
        windowIndicesInShuffled[shuffledWindowIndices[i]] = i;
      }
    }

    @Override
    public int getWindowCount() {
      return windows.size();
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      Window w = windows.get(windowIndex);
      window.set(
          w.uid,
          w.mediaItem,
          w.manifest,
          w.presentationStartTimeMs,
          w.windowStartTimeMs,
          w.elapsedRealtimeEpochOffsetMs,
          w.isSeekable,
          w.isDynamic,
          w.liveConfiguration,
          w.defaultPositionUs,
          w.durationUs,
          w.firstPeriodIndex,
          w.lastPeriodIndex,
          w.positionInFirstPeriodUs);
      window.isPlaceholder = w.isPlaceholder;
      return window;
    }

    @Override
    public int getNextWindowIndex(
        int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
      if (repeatMode == Player.REPEAT_MODE_ONE) {
        return windowIndex;
      }
      if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
        return repeatMode == Player.REPEAT_MODE_ALL
            ? getFirstWindowIndex(shuffleModeEnabled)
            : C.INDEX_UNSET;
      }
      return shuffleModeEnabled
          ? shuffledWindowIndices[windowIndicesInShuffled[windowIndex] + 1]
          : windowIndex + 1;
    }

    @Override
    public int getPreviousWindowIndex(
        int windowIndex, @Player.RepeatMode int repeatMode, boolean shuffleModeEnabled) {
      if (repeatMode == Player.REPEAT_MODE_ONE) {
        return windowIndex;
      }
      if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
        return repeatMode == Player.REPEAT_MODE_ALL
            ? getLastWindowIndex(shuffleModeEnabled)
            : C.INDEX_UNSET;
      }
      return shuffleModeEnabled
          ? shuffledWindowIndices[windowIndicesInShuffled[windowIndex] - 1]
          : windowIndex - 1;
    }

    @Override
    public int getLastWindowIndex(boolean shuffleModeEnabled) {
      if (isEmpty()) {
        return C.INDEX_UNSET;
      }
      return shuffleModeEnabled
          ? shuffledWindowIndices[getWindowCount() - 1]
          : getWindowCount() - 1;
    }

    @Override
    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
      if (isEmpty()) {
        return C.INDEX_UNSET;
      }
      return shuffleModeEnabled ? shuffledWindowIndices[0] : 0;
    }

    @Override
    public int getPeriodCount() {
      return periods.size();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      Period p = periods.get(periodIndex);
      period.set(
          p.id,
          p.uid,
          p.windowIndex,
          p.durationUs,
          p.positionInWindowUs,
          p.adPlaybackState,
          p.isPlaceholder);
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      throw new UnsupportedOperationException();
    }
  }
}
