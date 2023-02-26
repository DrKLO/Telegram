/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.ads;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Represents ad group times and information on the state and URIs of ads within each ad group.
 *
 * <p>Instances are immutable. Call the {@code with*} methods to get new instances that have the
 * required changes.
 */
public final class AdPlaybackState implements Bundleable {

  /**
   * Represents a group of ads, with information about their states.
   *
   * <p>Instances are immutable. Call the {@code with*} methods to get new instances that have the
   * required changes.
   */
  public static final class AdGroup implements Bundleable {

    /**
     * The time of the ad group in the {@link Timeline.Period}, in microseconds, or {@link
     * C#TIME_END_OF_SOURCE} to indicate a postroll ad.
     */
    public final long timeUs;
    /** The number of ads in the ad group, or {@link C#LENGTH_UNSET} if unknown. */
    public final int count;
    /**
     * The original number of ads in the ad group in case the ad group is only partially available,
     * or {@link C#LENGTH_UNSET} if unknown. An ad can be partially available when a server side
     * inserted ad live stream is joined while an ad is already playing and some ad information is
     * missing.
     */
    public final int originalCount;
    /** The URI of each ad in the ad group. */
    public final @NullableType Uri[] uris;
    /** The state of each ad in the ad group. */
    public final @AdState int[] states;
    /** The durations of each ad in the ad group, in microseconds. */
    public final long[] durationsUs;
    /**
     * The offset in microseconds which should be added to the content stream when resuming playback
     * after the ad group.
     */
    public final long contentResumeOffsetUs;
    /** Whether this ad group is server-side inserted and part of the content stream. */
    public final boolean isServerSideInserted;

    /**
     * Creates a new ad group with an unspecified number of ads.
     *
     * @param timeUs The time of the ad group in the {@link Timeline.Period}, in microseconds, or
     *     {@link C#TIME_END_OF_SOURCE} to indicate a postroll ad.
     */
    public AdGroup(long timeUs) {
      this(
          timeUs,
          /* count= */ C.LENGTH_UNSET,
          /* originalCount= */ C.LENGTH_UNSET,
          /* states= */ new int[0],
          /* uris= */ new Uri[0],
          /* durationsUs= */ new long[0],
          /* contentResumeOffsetUs= */ 0,
          /* isServerSideInserted= */ false);
    }

    private AdGroup(
        long timeUs,
        int count,
        int originalCount,
        @AdState int[] states,
        @NullableType Uri[] uris,
        long[] durationsUs,
        long contentResumeOffsetUs,
        boolean isServerSideInserted) {
      checkArgument(states.length == uris.length);
      this.timeUs = timeUs;
      this.count = count;
      this.originalCount = originalCount;
      this.states = states;
      this.uris = uris;
      this.durationsUs = durationsUs;
      this.contentResumeOffsetUs = contentResumeOffsetUs;
      this.isServerSideInserted = isServerSideInserted;
    }

    /**
     * Returns the index of the first ad in the ad group that should be played, or {@link #count} if
     * no ads should be played.
     */
    public int getFirstAdIndexToPlay() {
      return getNextAdIndexToPlay(-1);
    }

    /**
     * Returns the index of the next ad in the ad group that should be played after playing {@code
     * lastPlayedAdIndex}, or {@link #count} if no later ads should be played. If no ads have been
     * played, pass -1 to get the index of the first ad to play.
     *
     * <p>Note: {@linkplain #isServerSideInserted Server side inserted ads} are always considered
     * playable.
     */
    public int getNextAdIndexToPlay(@IntRange(from = -1) int lastPlayedAdIndex) {
      int nextAdIndexToPlay = lastPlayedAdIndex + 1;
      while (nextAdIndexToPlay < states.length) {
        if (isServerSideInserted
            || states[nextAdIndexToPlay] == AD_STATE_UNAVAILABLE
            || states[nextAdIndexToPlay] == AD_STATE_AVAILABLE) {
          break;
        }
        nextAdIndexToPlay++;
      }
      return nextAdIndexToPlay;
    }

    /** Returns whether the ad group has at least one ad that should be played. */
    public boolean shouldPlayAdGroup() {
      return count == C.LENGTH_UNSET || getFirstAdIndexToPlay() < count;
    }

    /**
     * Returns whether the ad group has at least one ad that is neither played, skipped, nor failed.
     */
    public boolean hasUnplayedAds() {
      if (count == C.LENGTH_UNSET) {
        return true;
      }
      for (int i = 0; i < count; i++) {
        if (states[i] == AD_STATE_UNAVAILABLE || states[i] == AD_STATE_AVAILABLE) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AdGroup adGroup = (AdGroup) o;
      return timeUs == adGroup.timeUs
          && count == adGroup.count
          && originalCount == adGroup.originalCount
          && Arrays.equals(uris, adGroup.uris)
          && Arrays.equals(states, adGroup.states)
          && Arrays.equals(durationsUs, adGroup.durationsUs)
          && contentResumeOffsetUs == adGroup.contentResumeOffsetUs
          && isServerSideInserted == adGroup.isServerSideInserted;
    }

    @Override
    public int hashCode() {
      int result = count;
      result = 31 * result + originalCount;
      result = 31 * result + (int) (timeUs ^ (timeUs >>> 32));
      result = 31 * result + Arrays.hashCode(uris);
      result = 31 * result + Arrays.hashCode(states);
      result = 31 * result + Arrays.hashCode(durationsUs);
      result = 31 * result + (int) (contentResumeOffsetUs ^ (contentResumeOffsetUs >>> 32));
      result = 31 * result + (isServerSideInserted ? 1 : 0);
      return result;
    }

    /** Returns a new instance with the {@link #timeUs} set to the specified value. */
    @CheckResult
    public AdGroup withTimeUs(long timeUs) {
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /** Returns a new instance with the ad count set to {@code count}. */
    @CheckResult
    public AdGroup withAdCount(int count) {
      @AdState int[] states = copyStatesWithSpaceForAdCount(this.states, count);
      long[] durationsUs = copyDurationsUsWithSpaceForAdCount(this.durationsUs, count);
      @NullableType Uri[] uris = Arrays.copyOf(this.uris, count);
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /**
     * Returns a new instance with the specified {@code uri} set for the specified ad, and the ad
     * marked as {@link #AD_STATE_AVAILABLE}.
     */
    @CheckResult
    public AdGroup withAdUri(Uri uri, @IntRange(from = 0) int index) {
      @AdState int[] states = copyStatesWithSpaceForAdCount(this.states, index + 1);
      long[] durationsUs =
          this.durationsUs.length == states.length
              ? this.durationsUs
              : copyDurationsUsWithSpaceForAdCount(this.durationsUs, states.length);
      @NullableType Uri[] uris = Arrays.copyOf(this.uris, states.length);
      uris[index] = uri;
      states[index] = AD_STATE_AVAILABLE;
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /**
     * Returns a new instance with the specified ad set to the specified {@code state}. The ad
     * specified must currently either be in {@link #AD_STATE_UNAVAILABLE} or {@link
     * #AD_STATE_AVAILABLE}.
     *
     * <p>This instance's ad count may be unknown, in which case {@code index} must be less than the
     * ad count specified later. Otherwise, {@code index} must be less than the current ad count.
     */
    @CheckResult
    public AdGroup withAdState(@AdState int state, @IntRange(from = 0) int index) {
      checkArgument(count == C.LENGTH_UNSET || index < count);
      @AdState int[] states = copyStatesWithSpaceForAdCount(this.states, /* count= */ index + 1);
      checkArgument(
          states[index] == AD_STATE_UNAVAILABLE
              || states[index] == AD_STATE_AVAILABLE
              || states[index] == state);
      long[] durationsUs =
          this.durationsUs.length == states.length
              ? this.durationsUs
              : copyDurationsUsWithSpaceForAdCount(this.durationsUs, states.length);
      @NullableType
      Uri[] uris =
          this.uris.length == states.length ? this.uris : Arrays.copyOf(this.uris, states.length);
      states[index] = state;
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /** Returns a new instance with the specified ad durations, in microseconds. */
    @CheckResult
    public AdGroup withAdDurationsUs(long[] durationsUs) {
      if (durationsUs.length < uris.length) {
        durationsUs = copyDurationsUsWithSpaceForAdCount(durationsUs, uris.length);
      } else if (count != C.LENGTH_UNSET && durationsUs.length > uris.length) {
        durationsUs = Arrays.copyOf(durationsUs, uris.length);
      }
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /** Returns an instance with the specified {@link #contentResumeOffsetUs}. */
    @CheckResult
    public AdGroup withContentResumeOffsetUs(long contentResumeOffsetUs) {
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /** Returns an instance with the specified value for {@link #isServerSideInserted}. */
    @CheckResult
    public AdGroup withIsServerSideInserted(boolean isServerSideInserted) {
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /** Returns an instance with the specified value for {@link #originalCount}. */
    public AdGroup withOriginalAdCount(int originalCount) {
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /** Removes the last ad from the ad group. */
    public AdGroup withLastAdRemoved() {
      int newCount = states.length - 1;
      @AdState int[] newStates = Arrays.copyOf(states, newCount);
      @NullableType Uri[] newUris = Arrays.copyOf(uris, newCount);
      long[] newDurationsUs = durationsUs;
      if (durationsUs.length > newCount) {
        newDurationsUs = Arrays.copyOf(durationsUs, newCount);
      }
      return new AdGroup(
          timeUs,
          newCount,
          originalCount,
          newStates,
          newUris,
          newDurationsUs,
          /* contentResumeOffsetUs= */ Util.sum(newDurationsUs),
          isServerSideInserted);
    }

    /**
     * Returns an instance with all unavailable and available ads marked as skipped. If the ad count
     * hasn't been set, it will be set to zero.
     */
    @CheckResult
    public AdGroup withAllAdsSkipped() {
      if (count == C.LENGTH_UNSET) {
        return new AdGroup(
            timeUs,
            /* count= */ 0,
            originalCount,
            /* states= */ new int[0],
            /* uris= */ new Uri[0],
            /* durationsUs= */ new long[0],
            contentResumeOffsetUs,
            isServerSideInserted);
      }
      int count = this.states.length;
      @AdState int[] states = Arrays.copyOf(this.states, count);
      for (int i = 0; i < count; i++) {
        if (states[i] == AD_STATE_AVAILABLE || states[i] == AD_STATE_UNAVAILABLE) {
          states[i] = AD_STATE_SKIPPED;
        }
      }
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    /**
     * Returns an instance with all ads in final states (played, skipped, error) reset to either
     * available or unavailable, which allows to play them again.
     */
    @CheckResult
    public AdGroup withAllAdsReset() {
      if (count == C.LENGTH_UNSET) {
        return this;
      }
      int count = this.states.length;
      @AdState int[] states = Arrays.copyOf(this.states, count);
      for (int i = 0; i < count; i++) {
        if (states[i] == AD_STATE_PLAYED
            || states[i] == AD_STATE_SKIPPED
            || states[i] == AD_STATE_ERROR) {
          states[i] = uris[i] == null ? AD_STATE_UNAVAILABLE : AD_STATE_AVAILABLE;
        }
      }
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states,
          uris,
          durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }

    @CheckResult
    private static @AdState int[] copyStatesWithSpaceForAdCount(@AdState int[] states, int count) {
      int oldStateCount = states.length;
      int newStateCount = max(count, oldStateCount);
      states = Arrays.copyOf(states, newStateCount);
      Arrays.fill(states, oldStateCount, newStateCount, AD_STATE_UNAVAILABLE);
      return states;
    }

    @CheckResult
    private static long[] copyDurationsUsWithSpaceForAdCount(long[] durationsUs, int count) {
      int oldDurationsUsCount = durationsUs.length;
      int newDurationsUsCount = max(count, oldDurationsUsCount);
      durationsUs = Arrays.copyOf(durationsUs, newDurationsUsCount);
      Arrays.fill(durationsUs, oldDurationsUsCount, newDurationsUsCount, C.TIME_UNSET);
      return durationsUs;
    }

    // Bundleable implementation.

    private static final String FIELD_TIME_US = Util.intToStringMaxRadix(0);
    private static final String FIELD_COUNT = Util.intToStringMaxRadix(1);
    private static final String FIELD_URIS = Util.intToStringMaxRadix(2);
    private static final String FIELD_STATES = Util.intToStringMaxRadix(3);
    private static final String FIELD_DURATIONS_US = Util.intToStringMaxRadix(4);
    private static final String FIELD_CONTENT_RESUME_OFFSET_US = Util.intToStringMaxRadix(5);
    private static final String FIELD_IS_SERVER_SIDE_INSERTED = Util.intToStringMaxRadix(6);
    private static final String FIELD_ORIGINAL_COUNT = Util.intToStringMaxRadix(7);

    // putParcelableArrayList actually supports null elements.
    @SuppressWarnings("nullness:argument")
    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putLong(FIELD_TIME_US, timeUs);
      bundle.putInt(FIELD_COUNT, count);
      bundle.putInt(FIELD_ORIGINAL_COUNT, originalCount);
      bundle.putParcelableArrayList(
          FIELD_URIS, new ArrayList<@NullableType Uri>(Arrays.asList(uris)));
      bundle.putIntArray(FIELD_STATES, states);
      bundle.putLongArray(FIELD_DURATIONS_US, durationsUs);
      bundle.putLong(FIELD_CONTENT_RESUME_OFFSET_US, contentResumeOffsetUs);
      bundle.putBoolean(FIELD_IS_SERVER_SIDE_INSERTED, isServerSideInserted);
      return bundle;
    }

    /** Object that can restore {@link AdGroup} from a {@link Bundle}. */
    public static final Creator<AdGroup> CREATOR = AdGroup::fromBundle;

    // getParcelableArrayList may have null elements.
    @SuppressWarnings("nullness:type.argument")
    private static AdGroup fromBundle(Bundle bundle) {
      long timeUs = bundle.getLong(FIELD_TIME_US);
      int count = bundle.getInt(FIELD_COUNT);
      int originalCount = bundle.getInt(FIELD_ORIGINAL_COUNT);
      @Nullable ArrayList<@NullableType Uri> uriList = bundle.getParcelableArrayList(FIELD_URIS);
      @Nullable
      @AdState
      int[] states = bundle.getIntArray(FIELD_STATES);
      @Nullable long[] durationsUs = bundle.getLongArray(FIELD_DURATIONS_US);
      long contentResumeOffsetUs = bundle.getLong(FIELD_CONTENT_RESUME_OFFSET_US);
      boolean isServerSideInserted = bundle.getBoolean(FIELD_IS_SERVER_SIDE_INSERTED);
      return new AdGroup(
          timeUs,
          count,
          originalCount,
          states == null ? new int[0] : states,
          uriList == null ? new Uri[0] : uriList.toArray(new Uri[0]),
          durationsUs == null ? new long[0] : durationsUs,
          contentResumeOffsetUs,
          isServerSideInserted);
    }
  }

  /**
   * Represents the state of an ad in an ad group. One of {@link #AD_STATE_UNAVAILABLE}, {@link
   * #AD_STATE_AVAILABLE}, {@link #AD_STATE_SKIPPED}, {@link #AD_STATE_PLAYED} or {@link
   * #AD_STATE_ERROR}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    AD_STATE_UNAVAILABLE,
    AD_STATE_AVAILABLE,
    AD_STATE_SKIPPED,
    AD_STATE_PLAYED,
    AD_STATE_ERROR,
  })
  public @interface AdState {}
  /** State for an ad that does not yet have a URL. */
  public static final int AD_STATE_UNAVAILABLE = 0;
  /** State for an ad that has a URL but has not yet been played. */
  public static final int AD_STATE_AVAILABLE = 1;
  /** State for an ad that was skipped. */
  public static final int AD_STATE_SKIPPED = 2;
  /** State for an ad that was played in full. */
  public static final int AD_STATE_PLAYED = 3;
  /** State for an ad that could not be loaded. */
  public static final int AD_STATE_ERROR = 4;

  /** Ad playback state with no ads. */
  public static final AdPlaybackState NONE =
      new AdPlaybackState(
          /* adsId= */ null,
          /* adGroups= */ new AdGroup[0],
          /* adResumePositionUs= */ 0L,
          /* contentDurationUs= */ C.TIME_UNSET,
          /* removedAdGroupCount= */ 0);

  private static final AdGroup REMOVED_AD_GROUP = new AdGroup(/* timeUs= */ 0).withAdCount(0);

  /**
   * The opaque identifier for ads with which this instance is associated, or {@code null} if unset.
   */
  @Nullable public final Object adsId;

  /** The number of ad groups. */
  public final int adGroupCount;
  /** The position offset in the first unplayed ad at which to begin playback, in microseconds. */
  public final long adResumePositionUs;
  /**
   * The duration of the content period in microseconds, if known. {@link C#TIME_UNSET} otherwise.
   */
  public final long contentDurationUs;
  /**
   * The number of ad groups that have been removed. Ad groups with indices between {@code 0}
   * (inclusive) and {@code removedAdGroupCount} (exclusive) will be empty and must not be modified
   * by any of the {@code with*} methods.
   */
  public final int removedAdGroupCount;

  private final AdGroup[] adGroups;

  /**
   * Creates a new ad playback state with the specified ad group times.
   *
   * @param adsId The opaque identifier for ads with which this instance is associated.
   * @param adGroupTimesUs The times of ad groups in microseconds, relative to the start of the
   *     {@link Timeline.Period} they belong to. A final element with the value {@link
   *     C#TIME_END_OF_SOURCE} indicates that there is a postroll ad.
   */
  public AdPlaybackState(Object adsId, long... adGroupTimesUs) {
    this(
        adsId,
        createEmptyAdGroups(adGroupTimesUs),
        /* adResumePositionUs= */ 0,
        /* contentDurationUs= */ C.TIME_UNSET,
        /* removedAdGroupCount= */ 0);
  }

  private AdPlaybackState(
      @Nullable Object adsId,
      AdGroup[] adGroups,
      long adResumePositionUs,
      long contentDurationUs,
      int removedAdGroupCount) {
    this.adsId = adsId;
    this.adResumePositionUs = adResumePositionUs;
    this.contentDurationUs = contentDurationUs;
    adGroupCount = adGroups.length + removedAdGroupCount;
    this.adGroups = adGroups;
    this.removedAdGroupCount = removedAdGroupCount;
  }

  /** Returns the specified {@link AdGroup}. */
  public AdGroup getAdGroup(@IntRange(from = 0) int adGroupIndex) {
    return adGroupIndex < removedAdGroupCount
        ? REMOVED_AD_GROUP
        : adGroups[adGroupIndex - removedAdGroupCount];
  }

  /**
   * Returns the index of the ad group at or before {@code positionUs} that should be played before
   * the content at {@code positionUs}. Returns {@link C#INDEX_UNSET} if the ad group at or before
   * {@code positionUs} has no ads remaining to be played, or if there is no such ad group.
   *
   * @param positionUs The period position at or before which to find an ad group, in microseconds,
   *     or {@link C#TIME_END_OF_SOURCE} for the end of the stream (in which case the index of any
   *     unplayed postroll ad group will be returned).
   * @param periodDurationUs The duration of the containing timeline period, in microseconds, or
   *     {@link C#TIME_UNSET} if not known.
   * @return The index of the ad group, or {@link C#INDEX_UNSET}.
   */
  public int getAdGroupIndexForPositionUs(long positionUs, long periodDurationUs) {
    // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
    // In practice we expect there to be few ad groups so the search shouldn't be expensive.
    int index = adGroupCount - 1;
    while (index >= 0 && isPositionBeforeAdGroup(positionUs, periodDurationUs, index)) {
      index--;
    }
    return index >= 0 && getAdGroup(index).hasUnplayedAds() ? index : C.INDEX_UNSET;
  }

  /**
   * Returns the index of the next ad group after {@code positionUs} that should be played. Returns
   * {@link C#INDEX_UNSET} if there is no such ad group.
   *
   * @param positionUs The period position after which to find an ad group, in microseconds, or
   *     {@link C#TIME_END_OF_SOURCE} for the end of the stream (in which case there can be no ad
   *     group after the position).
   * @param periodDurationUs The duration of the containing timeline period, in microseconds, or
   *     {@link C#TIME_UNSET} if not known.
   * @return The index of the ad group, or {@link C#INDEX_UNSET}.
   */
  public int getAdGroupIndexAfterPositionUs(long positionUs, long periodDurationUs) {
    if (positionUs == C.TIME_END_OF_SOURCE
        || (periodDurationUs != C.TIME_UNSET && positionUs >= periodDurationUs)) {
      return C.INDEX_UNSET;
    }
    // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
    // In practice we expect there to be few ad groups so the search shouldn't be expensive.
    int index = removedAdGroupCount;
    while (index < adGroupCount
        && ((getAdGroup(index).timeUs != C.TIME_END_OF_SOURCE
                && getAdGroup(index).timeUs <= positionUs)
            || !getAdGroup(index).shouldPlayAdGroup())) {
      index++;
    }
    return index < adGroupCount ? index : C.INDEX_UNSET;
  }

  /** Returns whether the specified ad has been marked as in {@link #AD_STATE_ERROR}. */
  public boolean isAdInErrorState(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 0) int adIndexInAdGroup) {
    if (adGroupIndex >= adGroupCount) {
      return false;
    }
    AdGroup adGroup = getAdGroup(adGroupIndex);
    if (adGroup.count == C.LENGTH_UNSET || adIndexInAdGroup >= adGroup.count) {
      return false;
    }
    return adGroup.states[adIndexInAdGroup] == AdPlaybackState.AD_STATE_ERROR;
  }

  /**
   * Returns an instance with the specified ad group time.
   *
   * @param adGroupIndex The index of the ad group.
   * @param adGroupTimeUs The new ad group time, in microseconds, or {@link C#TIME_END_OF_SOURCE} to
   *     indicate a postroll ad.
   * @return The updated ad playback state.
   */
  @CheckResult
  public AdPlaybackState withAdGroupTimeUs(
      @IntRange(from = 0) int adGroupIndex, long adGroupTimeUs) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = this.adGroups[adjustedIndex].withTimeUs(adGroupTimeUs);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with a new ad group.
   *
   * @param adGroupIndex The insertion index of the new group.
   * @param adGroupTimeUs The ad group time, in microseconds, or {@link C#TIME_END_OF_SOURCE} to
   *     indicate a postroll ad.
   * @return The updated ad playback state.
   */
  @CheckResult
  public AdPlaybackState withNewAdGroup(@IntRange(from = 0) int adGroupIndex, long adGroupTimeUs) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup newAdGroup = new AdGroup(adGroupTimeUs);
    AdGroup[] adGroups = Util.nullSafeArrayAppend(this.adGroups, newAdGroup);
    System.arraycopy(
        /* src= */ adGroups,
        /* srcPos= */ adjustedIndex,
        /* dest= */ adGroups,
        /* destPos= */ adjustedIndex + 1,
        /* length= */ this.adGroups.length - adjustedIndex);
    adGroups[adjustedIndex] = newAdGroup;
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the number of ads in {@code adGroupIndex} resolved to {@code adCount}.
   * The ad count must be greater than zero.
   */
  @CheckResult
  public AdPlaybackState withAdCount(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 1) int adCount) {
    checkArgument(adCount > 0);
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    if (adGroups[adjustedIndex].count == adCount) {
      return this;
    }
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = this.adGroups[adjustedIndex].withAdCount(adCount);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified ad URI and the ad marked as {@linkplain
   * #AD_STATE_AVAILABLE available}.
   *
   * @throws IllegalStateException If {@link Uri#EMPTY} is passed as argument for a client-side
   *     inserted ad group.
   */
  @CheckResult
  public AdPlaybackState withAvailableAdUri(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 0) int adIndexInAdGroup, Uri uri) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    checkState(!Uri.EMPTY.equals(uri) || adGroups[adjustedIndex].isServerSideInserted);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withAdUri(uri, adIndexInAdGroup);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified ad marked as {@linkplain #AD_STATE_AVAILABLE available}.
   *
   * <p>Must not be called with client side inserted ad groups. Client side inserted ads should use
   * {@link #withAvailableAdUri}.
   *
   * @throws IllegalStateException in case this methods is called on an ad group that {@linkplain
   *     AdGroup#isServerSideInserted is not server side inserted}.
   */
  @CheckResult
  public AdPlaybackState withAvailableAd(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 0) int adIndexInAdGroup) {
    return withAvailableAdUri(adGroupIndex, adIndexInAdGroup, Uri.EMPTY);
  }

  /** Returns an instance with the specified ad marked as {@linkplain #AD_STATE_PLAYED played}. */
  @CheckResult
  public AdPlaybackState withPlayedAd(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 0) int adIndexInAdGroup) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] =
        adGroups[adjustedIndex].withAdState(AD_STATE_PLAYED, adIndexInAdGroup);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /** Returns an instance with the specified ad marked as {@linkplain #AD_STATE_SKIPPED skipped}. */
  @CheckResult
  public AdPlaybackState withSkippedAd(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 0) int adIndexInAdGroup) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] =
        adGroups[adjustedIndex].withAdState(AD_STATE_SKIPPED, adIndexInAdGroup);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /** Returns an instance with the last ad of the given ad group removed. */
  @CheckResult
  public AdPlaybackState withLastAdRemoved(@IntRange(from = 0) int adGroupIndex) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withLastAdRemoved();
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified ad marked {@linkplain #AD_STATE_ERROR as having a load
   * error}.
   */
  @CheckResult
  public AdPlaybackState withAdLoadError(
      @IntRange(from = 0) int adGroupIndex, @IntRange(from = 0) int adIndexInAdGroup) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withAdState(AD_STATE_ERROR, adIndexInAdGroup);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with all ads in the specified ad group skipped (except for those already
   * marked as played or in the error state).
   */
  @CheckResult
  public AdPlaybackState withSkippedAdGroup(@IntRange(from = 0) int adGroupIndex) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withAllAdsSkipped();
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified ad durations, in microseconds.
   *
   * <p>Must only be used if {@link #removedAdGroupCount} is 0.
   */
  @CheckResult
  public AdPlaybackState withAdDurationsUs(long[][] adDurationUs) {
    checkState(removedAdGroupCount == 0);
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    for (int adGroupIndex = 0; adGroupIndex < adGroupCount; adGroupIndex++) {
      adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdDurationsUs(adDurationUs[adGroupIndex]);
    }
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified ad durations, in microseconds, in the specified ad
   * group.
   */
  @CheckResult
  public AdPlaybackState withAdDurationsUs(
      @IntRange(from = 0) int adGroupIndex, long... adDurationsUs) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withAdDurationsUs(adDurationsUs);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified ad resume position, in microseconds, relative to the
   * start of the current ad.
   */
  @CheckResult
  public AdPlaybackState withAdResumePositionUs(long adResumePositionUs) {
    if (this.adResumePositionUs == adResumePositionUs) {
      return this;
    } else {
      return new AdPlaybackState(
          adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
    }
  }

  /** Returns an instance with the specified content duration, in microseconds. */
  @CheckResult
  public AdPlaybackState withContentDurationUs(long contentDurationUs) {
    if (this.contentDurationUs == contentDurationUs) {
      return this;
    } else {
      return new AdPlaybackState(
          adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
    }
  }

  /**
   * Returns an instance with the specified number of {@link #removedAdGroupCount removed ad
   * groups}.
   *
   * <p>Ad groups with indices between {@code 0} (inclusive) and {@code removedAdGroupCount}
   * (exclusive) will be empty and must not be modified by any of the {@code with*} methods.
   */
  @CheckResult
  public AdPlaybackState withRemovedAdGroupCount(@IntRange(from = 0) int removedAdGroupCount) {
    if (this.removedAdGroupCount == removedAdGroupCount) {
      return this;
    } else {
      checkArgument(removedAdGroupCount > this.removedAdGroupCount);
      AdGroup[] adGroups = new AdGroup[adGroupCount - removedAdGroupCount];
      System.arraycopy(
          /* src= */ this.adGroups,
          /* srcPos= */ removedAdGroupCount - this.removedAdGroupCount,
          /* dest= */ adGroups,
          /* destPos= */ 0,
          /* length= */ adGroups.length);
      return new AdPlaybackState(
          adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
    }
  }

  /**
   * Returns an instance with the specified {@link AdGroup#contentResumeOffsetUs}, in microseconds,
   * for the specified ad group.
   */
  @CheckResult
  public AdPlaybackState withContentResumeOffsetUs(
      @IntRange(from = 0) int adGroupIndex, long contentResumeOffsetUs) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    if (adGroups[adjustedIndex].contentResumeOffsetUs == contentResumeOffsetUs) {
      return this;
    }
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] =
        adGroups[adjustedIndex].withContentResumeOffsetUs(contentResumeOffsetUs);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified value for {@link AdGroup#originalCount} in the specified
   * ad group.
   */
  @CheckResult
  public AdPlaybackState withOriginalAdCount(
      @IntRange(from = 0) int adGroupIndex, int originalAdCount) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    if (adGroups[adjustedIndex].originalCount == originalAdCount) {
      return this;
    }
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withOriginalAdCount(originalAdCount);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with the specified value for {@link AdGroup#isServerSideInserted} in the
   * specified ad group.
   */
  @CheckResult
  public AdPlaybackState withIsServerSideInserted(
      @IntRange(from = 0) int adGroupIndex, boolean isServerSideInserted) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    if (adGroups[adjustedIndex].isServerSideInserted == isServerSideInserted) {
      return this;
    }
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] =
        adGroups[adjustedIndex].withIsServerSideInserted(isServerSideInserted);
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns an instance with all ads in the specified ad group reset from final states (played,
   * skipped, error) to either available or unavailable, which allows to play them again.
   */
  @CheckResult
  public AdPlaybackState withResetAdGroup(@IntRange(from = 0) int adGroupIndex) {
    int adjustedIndex = adGroupIndex - removedAdGroupCount;
    AdGroup[] adGroups = Util.nullSafeArrayCopy(this.adGroups, this.adGroups.length);
    adGroups[adjustedIndex] = adGroups[adjustedIndex].withAllAdsReset();
    return new AdPlaybackState(
        adsId, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  /**
   * Returns a copy of the ad playback state with the given ads ID.
   *
   * @param adsId The new ads ID.
   * @param adPlaybackState The ad playback state to copy.
   * @return The new ad playback state.
   */
  public static AdPlaybackState fromAdPlaybackState(Object adsId, AdPlaybackState adPlaybackState) {
    AdGroup[] adGroups =
        new AdGroup[adPlaybackState.adGroupCount - adPlaybackState.removedAdGroupCount];
    for (int i = 0; i < adGroups.length; i++) {
      AdGroup adGroup = adPlaybackState.adGroups[i];
      adGroups[i] =
          new AdGroup(
              adGroup.timeUs,
              adGroup.count,
              adGroup.originalCount,
              Arrays.copyOf(adGroup.states, adGroup.states.length),
              Arrays.copyOf(adGroup.uris, adGroup.uris.length),
              Arrays.copyOf(adGroup.durationsUs, adGroup.durationsUs.length),
              adGroup.contentResumeOffsetUs,
              adGroup.isServerSideInserted);
    }
    return new AdPlaybackState(
        adsId,
        adGroups,
        adPlaybackState.adResumePositionUs,
        adPlaybackState.contentDurationUs,
        adPlaybackState.removedAdGroupCount);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AdPlaybackState that = (AdPlaybackState) o;
    return Util.areEqual(adsId, that.adsId)
        && adGroupCount == that.adGroupCount
        && adResumePositionUs == that.adResumePositionUs
        && contentDurationUs == that.contentDurationUs
        && removedAdGroupCount == that.removedAdGroupCount
        && Arrays.equals(adGroups, that.adGroups);
  }

  @Override
  public int hashCode() {
    int result = adGroupCount;
    result = 31 * result + (adsId == null ? 0 : adsId.hashCode());
    result = 31 * result + (int) adResumePositionUs;
    result = 31 * result + (int) contentDurationUs;
    result = 31 * result + removedAdGroupCount;
    result = 31 * result + Arrays.hashCode(adGroups);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("AdPlaybackState(adsId=");
    sb.append(adsId);
    sb.append(", adResumePositionUs=");
    sb.append(adResumePositionUs);
    sb.append(", adGroups=[");
    for (int i = 0; i < adGroups.length; i++) {
      sb.append("adGroup(timeUs=");
      sb.append(adGroups[i].timeUs);
      sb.append(", ads=[");
      for (int j = 0; j < adGroups[i].states.length; j++) {
        sb.append("ad(state=");
        switch (adGroups[i].states[j]) {
          case AD_STATE_UNAVAILABLE:
            sb.append('_');
            break;
          case AD_STATE_ERROR:
            sb.append('!');
            break;
          case AD_STATE_AVAILABLE:
            sb.append('R');
            break;
          case AD_STATE_PLAYED:
            sb.append('P');
            break;
          case AD_STATE_SKIPPED:
            sb.append('S');
            break;
          default:
            sb.append('?');
            break;
        }
        sb.append(", durationUs=");
        sb.append(adGroups[i].durationsUs[j]);
        sb.append(')');
        if (j < adGroups[i].states.length - 1) {
          sb.append(", ");
        }
      }
      sb.append("])");
      if (i < adGroups.length - 1) {
        sb.append(", ");
      }
    }
    sb.append("])");
    return sb.toString();
  }

  private boolean isPositionBeforeAdGroup(
      long positionUs, long periodDurationUs, int adGroupIndex) {
    if (positionUs == C.TIME_END_OF_SOURCE) {
      // The end of the content is at (but not before) any postroll ad, and after any other ads.
      return false;
    }
    long adGroupPositionUs = getAdGroup(adGroupIndex).timeUs;
    if (adGroupPositionUs == C.TIME_END_OF_SOURCE) {
      return periodDurationUs == C.TIME_UNSET || positionUs < periodDurationUs;
    } else {
      return positionUs < adGroupPositionUs;
    }
  }

  // Bundleable implementation.

  private static final String FIELD_AD_GROUPS = Util.intToStringMaxRadix(1);
  private static final String FIELD_AD_RESUME_POSITION_US = Util.intToStringMaxRadix(2);
  private static final String FIELD_CONTENT_DURATION_US = Util.intToStringMaxRadix(3);
  private static final String FIELD_REMOVED_AD_GROUP_COUNT = Util.intToStringMaxRadix(4);

  /**
   * {@inheritDoc}
   *
   * <p>It omits the {@link #adsId} field so the {@link #adsId} of instances restored by {@link
   * #CREATOR} will always be {@code null}.
   */
  // TODO(b/166765820): See if missing adsId would be okay and add adsId to the Bundle otherwise.
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    ArrayList<Bundle> adGroupBundleList = new ArrayList<>();
    for (AdGroup adGroup : adGroups) {
      adGroupBundleList.add(adGroup.toBundle());
    }
    if (!adGroupBundleList.isEmpty()) {
      bundle.putParcelableArrayList(FIELD_AD_GROUPS, adGroupBundleList);
    }
    if (adResumePositionUs != NONE.adResumePositionUs) {
      bundle.putLong(FIELD_AD_RESUME_POSITION_US, adResumePositionUs);
    }
    if (contentDurationUs != NONE.contentDurationUs) {
      bundle.putLong(FIELD_CONTENT_DURATION_US, contentDurationUs);
    }
    if (removedAdGroupCount != NONE.removedAdGroupCount) {
      bundle.putInt(FIELD_REMOVED_AD_GROUP_COUNT, removedAdGroupCount);
    }
    return bundle;
  }

  /**
   * Object that can restore {@link AdPlaybackState} from a {@link Bundle}.
   *
   * <p>The {@link #adsId} of restored instances will always be {@code null}.
   */
  public static final Bundleable.Creator<AdPlaybackState> CREATOR = AdPlaybackState::fromBundle;

  private static AdPlaybackState fromBundle(Bundle bundle) {
    @Nullable ArrayList<Bundle> adGroupBundleList = bundle.getParcelableArrayList(FIELD_AD_GROUPS);
    @Nullable AdGroup[] adGroups;
    if (adGroupBundleList == null) {
      adGroups = new AdGroup[0];
    } else {
      adGroups = new AdGroup[adGroupBundleList.size()];
      for (int i = 0; i < adGroupBundleList.size(); i++) {
        adGroups[i] = AdGroup.CREATOR.fromBundle(adGroupBundleList.get(i));
      }
    }
    long adResumePositionUs =
        bundle.getLong(FIELD_AD_RESUME_POSITION_US, /* defaultValue= */ NONE.adResumePositionUs);
    long contentDurationUs =
        bundle.getLong(FIELD_CONTENT_DURATION_US, /* defaultValue= */ NONE.contentDurationUs);
    int removedAdGroupCount =
        bundle.getInt(FIELD_REMOVED_AD_GROUP_COUNT, /* defaultValue= */ NONE.removedAdGroupCount);
    return new AdPlaybackState(
        /* adsId= */ null, adGroups, adResumePositionUs, contentDurationUs, removedAdGroupCount);
  }

  private static AdGroup[] createEmptyAdGroups(long[] adGroupTimesUs) {
    AdGroup[] adGroups = new AdGroup[adGroupTimesUs.length];
    for (int i = 0; i < adGroups.length; i++) {
      adGroups[i] = new AdGroup(adGroupTimesUs[i]);
    }
    return adGroups;
  }
}
