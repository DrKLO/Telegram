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
package org.telegram.messenger.exoplayer2;

import android.util.Pair;
import org.telegram.messenger.exoplayer2.util.Assertions;

/**
 * A flexible representation of the structure of media. A timeline is able to represent the
 * structure of a wide variety of media, from simple cases like a single media file through to
 * complex compositions of media such as playlists and streams with inserted ads. Instances are
 * immutable. For cases where media is changing dynamically (e.g. live streams), a timeline provides
 * a snapshot of the current state.
 * <p>
 * A timeline consists of related {@link Period}s and {@link Window}s. A period defines a single
 * logical piece of media, for example a media file. It may also define groups of ads inserted into
 * the media, along with information about whether those ads have been loaded and played. A window
 * spans one or more periods, defining the region within those periods that's currently available
 * for playback along with additional information such as whether seeking is supported within the
 * window. Each window defines a default position, which is the position from which playback will
 * start when the player starts playing the window. The following examples illustrate timelines for
 * various use cases.
 *
 * <h3 id="single-file">Single media file or on-demand stream</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-single-file.svg" alt="Example timeline for a single file">
 * </p>
 * A timeline for a single media file or on-demand stream consists of a single period and window.
 * The window spans the whole period, indicating that all parts of the media are available for
 * playback. The window's default position is typically at the start of the period (indicated by the
 * black dot in the figure above).
 *
 * <h3>Playlist of media files or on-demand streams</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-playlist.svg" alt="Example timeline for a playlist of files">
 * </p>
 * A timeline for a playlist of media files or on-demand streams consists of multiple periods, each
 * with its own window. Each window spans the whole of the corresponding period, and typically has a
 * default position at the start of the period. The properties of the periods and windows (e.g.
 * their durations and whether the window is seekable) will often only become known when the player
 * starts buffering the corresponding file or stream.
 *
 * <h3 id="live-limited">Live stream with limited availability</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-live-limited.svg" alt="Example timeline for a live stream with
 *       limited availability">
 * </p>
 * A timeline for a live stream consists of a period whose duration is unknown, since it's
 * continually extending as more content is broadcast. If content only remains available for a
 * limited period of time then the window may start at a non-zero position, defining the region of
 * content that can still be played. The window will have {@link Window#isDynamic} set to true if
 * the stream is still live. Its default position is typically near to the live edge (indicated by
 * the black dot in the figure above).
 *
 * <h3>Live stream with indefinite availability</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-live-indefinite.svg" alt="Example timeline for a live stream with
 *       indefinite availability">
 * </p>
 * A timeline for a live stream with indefinite availability is similar to the
 * <a href="#live-limited">Live stream with limited availability</a> case, except that the window
 * starts at the beginning of the period to indicate that all of the previously broadcast content
 * can still be played.
 *
 * <h3 id="live-multi-period">Live stream with multiple periods</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-live-multi-period.svg" alt="Example timeline for a live stream
 *       with multiple periods">
 * </p>
 * This case arises when a live stream is explicitly divided into separate periods, for example at
 * content boundaries. This case is similar to the <a href="#live-limited">Live stream with limited
 * availability</a> case, except that the window may span more than one period. Multiple periods are
 * also possible in the indefinite availability case.
 *
 * <h3>On-demand stream followed by live stream</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-advanced.svg" alt="Example timeline for an on-demand stream
 *       followed by a live stream">
 * </p>
 * This case is the concatenation of the <a href="#single-file">Single media file or on-demand
 * stream</a> and <a href="#multi-period">Live stream with multiple periods</a> cases. When playback
 * of the on-demand stream ends, playback of the live stream will start from its default position
 * near the live edge.
 *
 * <h3 id="single-file-midrolls">On-demand stream with mid-roll ads</h3>
 * <p align="center">
 *   <img src="doc-files/timeline-single-file-midrolls.svg" alt="Example timeline for an on-demand
 *       stream with mid-roll ad groups">
 * </p>
 * This case includes mid-roll ad groups, which are defined as part of the timeline's single period.
 * The period can be queried for information about the ad groups and the ads they contain.
 */
public abstract class Timeline {

  /**
   * Holds information about a window in a {@link Timeline}. A window defines a region of media
   * currently available for playback along with additional information such as whether seeking is
   * supported within the window. The figure below shows some of the information defined by a
   * window, as well as how this information relates to corresponding {@link Period}s in the
   * timeline.
   * <p align="center">
   *   <img src="doc-files/timeline-window.svg" alt="Information defined by a timeline window">
   * </p>
   */
  public static final class Window {

    /**
     * An identifier for the window. Not necessarily unique.
     */
    public Object id;

    /**
     * The start time of the presentation to which this window belongs in milliseconds since the
     * epoch, or {@link C#TIME_UNSET} if unknown or not applicable. For informational purposes only.
     */
    public long presentationStartTimeMs;

    /**
     * The window's start time in milliseconds since the epoch, or {@link C#TIME_UNSET} if unknown
     * or not applicable. For informational purposes only.
     */
    public long windowStartTimeMs;

    /**
     * Whether it's possible to seek within this window.
     */
    public boolean isSeekable;

    /**
     * Whether this window may change when the timeline is updated.
     */
    public boolean isDynamic;

    /**
     * The index of the first period that belongs to this window.
     */
    public int firstPeriodIndex;

    /**
     * The index of the last period that belongs to this window.
     */
    public int lastPeriodIndex;

    /**
     * The default position relative to the start of the window at which to begin playback, in
     * microseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long defaultPositionUs;

    /**
     * The duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long durationUs;

    /**
     * The position of the start of this window relative to the start of the first period belonging
     * to it, in microseconds.
     */
    public long positionInFirstPeriodUs;

    /**
     * Sets the data held by this window.
     */
    public Window set(Object id, long presentationStartTimeMs, long windowStartTimeMs,
        boolean isSeekable, boolean isDynamic, long defaultPositionUs, long durationUs,
        int firstPeriodIndex, int lastPeriodIndex, long positionInFirstPeriodUs) {
      this.id = id;
      this.presentationStartTimeMs = presentationStartTimeMs;
      this.windowStartTimeMs = windowStartTimeMs;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.defaultPositionUs = defaultPositionUs;
      this.durationUs = durationUs;
      this.firstPeriodIndex = firstPeriodIndex;
      this.lastPeriodIndex = lastPeriodIndex;
      this.positionInFirstPeriodUs = positionInFirstPeriodUs;
      return this;
    }

    /**
     * Returns the default position relative to the start of the window at which to begin playback,
     * in milliseconds. May be {@link C#TIME_UNSET} if and only if the window was populated with a
     * non-zero default position projection, and if the specified projection cannot be performed
     * whilst remaining within the bounds of the window.
     */
    public long getDefaultPositionMs() {
      return C.usToMs(defaultPositionUs);
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

    /**
     * Returns the duration of the window in milliseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationMs() {
      return C.usToMs(durationUs);
    }

    /**
     * Returns the duration of this window in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in milliseconds.
     */
    public long getPositionInFirstPeriodMs() {
      return C.usToMs(positionInFirstPeriodUs);
    }

    /**
     * Returns the position of the start of this window relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public long getPositionInFirstPeriodUs() {
      return positionInFirstPeriodUs;
    }

  }

  /**
   * Holds information about a period in a {@link Timeline}. A period defines a single logical piece
   * of media, for example a media file. It may also define groups of ads inserted into the media,
   * along with information about whether those ads have been loaded and played.
   * <p>
   * The figure below shows some of the information defined by a period, as well as how this
   * information relates to a corresponding {@link Window} in the timeline.
   * <p align="center">
   *   <img src="doc-files/timeline-period.svg" alt="Information defined by a period">
   * </p>
   */
  public static final class Period {

    /**
     * An identifier for the period. Not necessarily unique.
     */
    public Object id;

    /**
     * A unique identifier for the period.
     */
    public Object uid;

    /**
     * The index of the window to which this period belongs.
     */
    public int windowIndex;

    /**
     * The duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long durationUs;

    private long positionInWindowUs;
    private long[] adGroupTimesUs;
    private int[] adCounts;
    private int[] adsLoadedCounts;
    private int[] adsPlayedCounts;
    private long[][] adDurationsUs;
    private long adResumePositionUs;

    /**
     * Sets the data held by this period.
     *
     * @param id An identifier for the period. Not necessarily unique.
     * @param uid A unique identifier for the period.
     * @param windowIndex The index of the window to which this period belongs.
     * @param durationUs The duration of this period in microseconds, or {@link C#TIME_UNSET} if
     *     unknown.
     * @param positionInWindowUs The position of the start of this period relative to the start of
     *     the window to which it belongs, in milliseconds. May be negative if the start of the
     *     period is not within the window.
     * @return This period, for convenience.
     */
    public Period set(Object id, Object uid, int windowIndex, long durationUs,
        long positionInWindowUs) {
      return set(id, uid, windowIndex, durationUs, positionInWindowUs, null, null, null, null,
          null, C.TIME_UNSET);
    }

    /**
     * Sets the data held by this period.
     *
     * @param id An identifier for the period. Not necessarily unique.
     * @param uid A unique identifier for the period.
     * @param windowIndex The index of the window to which this period belongs.
     * @param durationUs The duration of this period in microseconds, or {@link C#TIME_UNSET} if
     *     unknown.
     * @param positionInWindowUs The position of the start of this period relative to the start of
     *     the window to which it belongs, in milliseconds. May be negative if the start of the
     *     period is not within the window.
     * @param adGroupTimesUs The times of ad groups relative to the start of the period, in
     *     microseconds. A final element with the value {@link C#TIME_END_OF_SOURCE} indicates that
     *     the period has a postroll ad.
     * @param adCounts The number of ads in each ad group. An element may be {@link C#LENGTH_UNSET}
     *     if the number of ads is not yet known.
     * @param adsLoadedCounts The number of ads loaded so far in each ad group.
     * @param adsPlayedCounts The number of ads played so far in each ad group.
     * @param adDurationsUs The duration of each ad in each ad group, in microseconds. An element
     *     may be {@link C#TIME_UNSET} if the duration is not yet known.
     * @param adResumePositionUs The position offset in the first unplayed ad at which to begin
     *     playback, in microseconds.
     * @return This period, for convenience.
     */
    public Period set(Object id, Object uid, int windowIndex, long durationUs,
        long positionInWindowUs, long[] adGroupTimesUs, int[] adCounts, int[] adsLoadedCounts,
        int[] adsPlayedCounts, long[][] adDurationsUs, long adResumePositionUs) {
      this.id = id;
      this.uid = uid;
      this.windowIndex = windowIndex;
      this.durationUs = durationUs;
      this.positionInWindowUs = positionInWindowUs;
      this.adGroupTimesUs = adGroupTimesUs;
      this.adCounts = adCounts;
      this.adsLoadedCounts = adsLoadedCounts;
      this.adsPlayedCounts = adsPlayedCounts;
      this.adDurationsUs = adDurationsUs;
      this.adResumePositionUs = adResumePositionUs;
      return this;
    }

    /**
     * Returns the duration of the period in milliseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationMs() {
      return C.usToMs(durationUs);
    }

    /**
     * Returns the duration of this period in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public long getDurationUs() {
      return durationUs;
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in milliseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowMs() {
      return C.usToMs(positionInWindowUs);
    }

    /**
     * Returns the position of the start of this period relative to the start of the window to which
     * it belongs, in microseconds. May be negative if the start of the period is not within the
     * window.
     */
    public long getPositionInWindowUs() {
      return positionInWindowUs;
    }

    /**
     * Returns the number of ad groups in the period.
     */
    public int getAdGroupCount() {
      return adGroupTimesUs == null ? 0 : adGroupTimesUs.length;
    }

    /**
     * Returns the time of the ad group at index {@code adGroupIndex} in the period, in
     * microseconds.
     *
     * @param adGroupIndex The ad group index.
     * @return The time of the ad group at the index, in microseconds.
     */
    public long getAdGroupTimeUs(int adGroupIndex) {
      return adGroupTimesUs[adGroupIndex];
    }

    /**
     * Returns the number of ads that have been played in the specified ad group in the period.
     *
     * @param adGroupIndex The ad group index.
     * @return The number of ads that have been played.
     */
    public int getPlayedAdCount(int adGroupIndex) {
      return adsPlayedCounts[adGroupIndex];
    }

    /**
     * Returns whether the ad group at index {@code adGroupIndex} has been played.
     *
     * @param adGroupIndex The ad group index.
     * @return Whether the ad group at index {@code adGroupIndex} has been played.
     */
    public boolean hasPlayedAdGroup(int adGroupIndex) {
      return adCounts[adGroupIndex] != C.INDEX_UNSET
          && adsPlayedCounts[adGroupIndex] == adCounts[adGroupIndex];
    }

    /**
     * Returns the index of the ad group at or before {@code positionUs}, if that ad group is
     * unplayed. Returns {@link C#INDEX_UNSET} if the ad group before {@code positionUs} has been
     * played, or if there is no such ad group.
     *
     * @param positionUs The position at or before which to find an ad group, in microseconds.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    public int getAdGroupIndexForPositionUs(long positionUs) {
      if (adGroupTimesUs == null) {
        return C.INDEX_UNSET;
      }
      // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
      // In practice we expect there to be few ad groups so the search shouldn't be expensive.
      int index = adGroupTimesUs.length - 1;
      while (index >= 0 && (adGroupTimesUs[index] == C.TIME_END_OF_SOURCE
          || adGroupTimesUs[index] > positionUs)) {
        index--;
      }
      return index >= 0 && !hasPlayedAdGroup(index) ? index : C.INDEX_UNSET;
    }

    /**
     * Returns the index of the next unplayed ad group after {@code positionUs}. Returns
     * {@link C#INDEX_UNSET} if there is no such ad group.
     *
     * @param positionUs The position after which to find an ad group, in microseconds.
     * @return The index of the ad group, or {@link C#INDEX_UNSET}.
     */
    public int getAdGroupIndexAfterPositionUs(long positionUs) {
      if (adGroupTimesUs == null) {
        return C.INDEX_UNSET;
      }
      // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
      // In practice we expect there to be few ad groups so the search shouldn't be expensive.
      int index = 0;
      while (index < adGroupTimesUs.length && adGroupTimesUs[index] != C.TIME_END_OF_SOURCE
          && (positionUs >= adGroupTimesUs[index] || hasPlayedAdGroup(index))) {
        index++;
      }
      return index < adGroupTimesUs.length ? index : C.INDEX_UNSET;
    }

    /**
     * Returns the number of ads in the ad group at index {@code adGroupIndex}, or
     * {@link C#LENGTH_UNSET} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @return The number of ads in the ad group, or {@link C#LENGTH_UNSET} if not yet known.
     */
    public int getAdCountInAdGroup(int adGroupIndex) {
      return adCounts[adGroupIndex];
    }

    /**
     * Returns whether the URL for the specified ad is known.
     *
     * @param adGroupIndex The ad group index.
     * @param adIndexInAdGroup The ad index in the ad group.
     * @return Whether the URL for the specified ad is known.
     */
    public boolean isAdAvailable(int adGroupIndex, int adIndexInAdGroup) {
      return adIndexInAdGroup < adsLoadedCounts[adGroupIndex];
    }

    /**
     * Returns the duration of the ad at index {@code adIndexInAdGroup} in the ad group at
     * {@code adGroupIndex}, in microseconds, or {@link C#TIME_UNSET} if not yet known.
     *
     * @param adGroupIndex The ad group index.
     * @param adIndexInAdGroup The ad index in the ad group.
     * @return The duration of the ad, or {@link C#TIME_UNSET} if not yet known.
     */
    public long getAdDurationUs(int adGroupIndex, int adIndexInAdGroup) {
      if (adIndexInAdGroup >= adDurationsUs[adGroupIndex].length) {
        return C.TIME_UNSET;
      }
      return adDurationsUs[adGroupIndex][adIndexInAdGroup];
    }

    /**
     * Returns the position offset in the first unplayed ad at which to begin playback, in
     * microseconds.
     */
    public long getAdResumePositionUs() {
      return adResumePositionUs;
    }

  }

  /**
   * An empty timeline.
   */
  public static final Timeline EMPTY = new Timeline() {

    @Override
    public int getWindowCount() {
      return 0;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
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

  };

  /**
   * Returns whether the timeline is empty.
   */
  public final boolean isEmpty() {
    return getWindowCount() == 0;
  }

  /**
   * Returns the number of windows in the timeline.
   */
  public abstract int getWindowCount();

  /**
   * Returns the index of the window after the window at index {@code windowIndex} depending on the
   * {@code repeatMode}.
   *
   * @param windowIndex Index of a window in the timeline.
   * @param repeatMode A repeat mode.
   * @return The index of the next window, or {@link C#INDEX_UNSET} if this is the last window.
   */
  public int getNextWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == getWindowCount() - 1 ? C.INDEX_UNSET : windowIndex + 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == getWindowCount() - 1 ? 0 : windowIndex + 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the index of the window before the window at index {@code windowIndex} depending on the
   * {@code repeatMode}.
   *
   * @param windowIndex Index of a window in the timeline.
   * @param repeatMode A repeat mode.
   * @return The index of the previous window, or {@link C#INDEX_UNSET} if this is the first window.
   */
  public int getPreviousWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return windowIndex == 0 ? C.INDEX_UNSET : windowIndex - 1;
      case Player.REPEAT_MODE_ONE:
        return windowIndex;
      case Player.REPEAT_MODE_ALL:
        return windowIndex == 0 ? getWindowCount() - 1 : windowIndex - 1;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index. Does not populate
   * {@link Window#id}.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @return The populated {@link Window}, for convenience.
   */
  public final Window getWindow(int windowIndex, Window window) {
    return getWindow(windowIndex, window, false);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param setIds Whether {@link Window#id} should be populated. If false, the field will be set to
   *     null. The caller should pass false for efficiency reasons unless the field is required.
   * @return The populated {@link Window}, for convenience.
   */
  public Window getWindow(int windowIndex, Window window, boolean setIds) {
    return getWindow(windowIndex, window, setIds, 0);
  }

  /**
   * Populates a {@link Window} with data for the window at the specified index.
   *
   * @param windowIndex The index of the window.
   * @param window The {@link Window} to populate. Must not be null.
   * @param setIds Whether {@link Window#id} should be populated. If false, the field will be set to
   *     null. The caller should pass false for efficiency reasons unless the field is required.
   * @param defaultPositionProjectionUs A duration into the future that the populated window's
   *     default start position should be projected.
   * @return The populated {@link Window}, for convenience.
   */
  public abstract Window getWindow(int windowIndex, Window window, boolean setIds,
      long defaultPositionProjectionUs);

  /**
   * Returns the number of periods in the timeline.
   */
  public abstract int getPeriodCount();

  /**
   * Returns the index of the period after the period at index {@code periodIndex} depending on the
   * {@code repeatMode}.
   *
   * @param periodIndex Index of a period in the timeline.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @return The index of the next period, or {@link C#INDEX_UNSET} if this is the last period.
   */
  public final int getNextPeriodIndex(int periodIndex, Period period, Window window,
      @Player.RepeatMode int repeatMode) {
    int windowIndex = getPeriod(periodIndex, period).windowIndex;
    if (getWindow(windowIndex, window).lastPeriodIndex == periodIndex) {
      int nextWindowIndex = getNextWindowIndex(windowIndex, repeatMode);
      if (nextWindowIndex == C.INDEX_UNSET) {
        return C.INDEX_UNSET;
      }
      return getWindow(nextWindowIndex, window).firstPeriodIndex;
    }
    return periodIndex + 1;
  }

  /**
   * Returns whether the given period is the last period of the timeline depending on the
   * {@code repeatMode}.
   *
   * @param periodIndex A period index.
   * @param period A {@link Period} to be used internally. Must not be null.
   * @param window A {@link Window} to be used internally. Must not be null.
   * @param repeatMode A repeat mode.
   * @return Whether the period of the given index is the last period of the timeline.
   */
  public final boolean isLastPeriod(int periodIndex, Period period, Window window,
      @Player.RepeatMode int repeatMode) {
    return getNextPeriodIndex(periodIndex, period, window, repeatMode) == C.INDEX_UNSET;
  }

  /**
   * Populates a {@link Period} with data for the period at the specified index. Does not populate
   * {@link Period#id} and {@link Period#uid}.
   *
   * @param periodIndex The index of the period.
   * @param period The {@link Period} to populate. Must not be null.
   * @return The populated {@link Period}, for convenience.
   */
  public final Period getPeriod(int periodIndex, Period period) {
    return getPeriod(periodIndex, period, false);
  }

  /**
   * Calls {@link #getPeriodPosition(Window, Period, int, long, long)} with a zero default position
   * projection.
   */
  public final Pair<Integer, Long> getPeriodPosition(Window window, Period period, int windowIndex,
      long windowPositionUs) {
    return getPeriodPosition(window, period, windowIndex, windowPositionUs, 0);
  }

  /**
   * Converts (windowIndex, windowPositionUs) to the corresponding (periodIndex, periodPositionUs).
   *
   * @param window A {@link Window} that may be overwritten.
   * @param period A {@link Period} that may be overwritten.
   * @param windowIndex The window index.
   * @param windowPositionUs The window time, or {@link C#TIME_UNSET} to use the window's default
   *     start position.
   * @param defaultPositionProjectionUs If {@code windowPositionUs} is {@link C#TIME_UNSET}, the
   *     duration into the future by which the window's position should be projected.
   * @return The corresponding (periodIndex, periodPositionUs), or null if {@code #windowPositionUs}
   *     is {@link C#TIME_UNSET}, {@code defaultPositionProjectionUs} is non-zero, and the window's
   *     position could not be projected by {@code defaultPositionProjectionUs}.
   */
  public final Pair<Integer, Long> getPeriodPosition(Window window, Period period, int windowIndex,
      long windowPositionUs, long defaultPositionProjectionUs) {
    Assertions.checkIndex(windowIndex, 0, getWindowCount());
    getWindow(windowIndex, window, false, defaultPositionProjectionUs);
    if (windowPositionUs == C.TIME_UNSET) {
      windowPositionUs = window.getDefaultPositionUs();
      if (windowPositionUs == C.TIME_UNSET) {
        return null;
      }
    }
    int periodIndex = window.firstPeriodIndex;
    long periodPositionUs = window.getPositionInFirstPeriodUs() + windowPositionUs;
    long periodDurationUs = getPeriod(periodIndex, period).getDurationUs();
    while (periodDurationUs != C.TIME_UNSET && periodPositionUs >= periodDurationUs
        && periodIndex < window.lastPeriodIndex) {
      periodPositionUs -= periodDurationUs;
      periodDurationUs = getPeriod(++periodIndex, period).getDurationUs();
    }
    return Pair.create(periodIndex, periodPositionUs);
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
   * Returns the index of the period identified by its unique {@code id}, or {@link C#INDEX_UNSET}
   * if the period is not in the timeline.
   *
   * @param uid A unique identifier for a period.
   * @return The index of the period, or {@link C#INDEX_UNSET} if the period was not found.
   */
  public abstract int getIndexOfPeriod(Object uid);

}
