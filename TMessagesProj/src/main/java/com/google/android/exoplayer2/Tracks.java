/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.BundleableUtil.toBundleArrayList;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.util.BundleableUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;
import java.util.Arrays;
import java.util.List;

/** Information about groups of tracks. */
public final class Tracks implements Bundleable {

  /**
   * Information about a single group of tracks, including the underlying {@link TrackGroup}, the
   * level to which each track is supported by the player, and whether any of the tracks are
   * selected.
   */
  public static final class Group implements Bundleable {

    /** The number of tracks in the group. */
    public final int length;

    private final TrackGroup mediaTrackGroup;
    private final boolean adaptiveSupported;
    private final @C.FormatSupport int[] trackSupport;
    private final boolean[] trackSelected;

    /**
     * Constructs an instance.
     *
     * @param mediaTrackGroup The underlying {@link TrackGroup} defined by the media.
     * @param adaptiveSupported Whether the player supports adaptive selections containing more than
     *     one track in the group.
     * @param trackSupport The {@link C.FormatSupport} of each track in the group.
     * @param trackSelected Whether each track in the {@code trackGroup} is selected.
     */
    public Group(
        TrackGroup mediaTrackGroup,
        boolean adaptiveSupported,
        @C.FormatSupport int[] trackSupport,
        boolean[] trackSelected) {
      length = mediaTrackGroup.length;
      checkArgument(length == trackSupport.length && length == trackSelected.length);
      this.mediaTrackGroup = mediaTrackGroup;
      this.adaptiveSupported = adaptiveSupported && length > 1;
      this.trackSupport = trackSupport.clone();
      this.trackSelected = trackSelected.clone();
    }

    /**
     * Returns the underlying {@link TrackGroup} defined by the media.
     *
     * <p>Unlike this class, {@link TrackGroup} only contains information defined by the media
     * itself, and does not contain runtime information such as which tracks are supported and
     * currently selected. This makes it suitable for use as a {@code key} in certain {@code (key,
     * value)} data structures.
     */
    public TrackGroup getMediaTrackGroup() {
      return mediaTrackGroup;
    }

    /**
     * Returns the {@link Format} for a specified track.
     *
     * @param trackIndex The index of the track in the group.
     * @return The {@link Format} of the track.
     */
    public Format getTrackFormat(int trackIndex) {
      return mediaTrackGroup.getFormat(trackIndex);
    }

    /**
     * Returns the level of support for a specified track.
     *
     * @param trackIndex The index of the track in the group.
     * @return The {@link C.FormatSupport} of the track.
     */
    public @C.FormatSupport int getTrackSupport(int trackIndex) {
      return trackSupport[trackIndex];
    }

    /**
     * Returns whether a specified track is supported for playback, without exceeding the advertised
     * capabilities of the device. Equivalent to {@code isTrackSupported(trackIndex, false)}.
     *
     * @param trackIndex The index of the track in the group.
     * @return True if the track's format can be played, false otherwise.
     */
    public boolean isTrackSupported(int trackIndex) {
      return isTrackSupported(trackIndex, /* allowExceedsCapabilities= */ false);
    }

    /**
     * Returns whether a specified track is supported for playback.
     *
     * @param trackIndex The index of the track in the group.
     * @param allowExceedsCapabilities Whether to consider the track as supported if it has a
     *     supported {@link Format#sampleMimeType MIME type}, but otherwise exceeds the advertised
     *     capabilities of the device. For example, a video track for which there's a corresponding
     *     decoder whose maximum advertised resolution is exceeded by the resolution of the track.
     *     Such tracks may be playable in some cases.
     * @return True if the track's format can be played, false otherwise.
     */
    public boolean isTrackSupported(int trackIndex, boolean allowExceedsCapabilities) {
      return trackSupport[trackIndex] == C.FORMAT_HANDLED
          || (allowExceedsCapabilities
              && trackSupport[trackIndex] == C.FORMAT_EXCEEDS_CAPABILITIES);
    }

    /** Returns whether at least one track in the group is selected for playback. */
    public boolean isSelected() {
      return Booleans.contains(trackSelected, true);
    }

    /** Returns whether adaptive selections containing more than one track are supported. */
    public boolean isAdaptiveSupported() {
      return adaptiveSupported;
    }

    /**
     * Returns whether at least one track in the group is supported for playback, without exceeding
     * the advertised capabilities of the device. Equivalent to {@code isSupported(false)}.
     */
    public boolean isSupported() {
      return isSupported(/* allowExceedsCapabilities= */ false);
    }

    /**
     * Returns whether at least one track in the group is supported for playback.
     *
     * @param allowExceedsCapabilities Whether to consider a track as supported if it has a
     *     supported {@link Format#sampleMimeType MIME type}, but otherwise exceeds the advertised
     *     capabilities of the device. For example, a video track for which there's a corresponding
     *     decoder whose maximum advertised resolution is exceeded by the resolution of the track.
     *     Such tracks may be playable in some cases.
     */
    public boolean isSupported(boolean allowExceedsCapabilities) {
      for (int i = 0; i < trackSupport.length; i++) {
        if (isTrackSupported(i, allowExceedsCapabilities)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns whether a specified track is selected for playback.
     *
     * <p>Note that multiple tracks in the group may be selected. This is common in adaptive
     * streaming, where tracks of different qualities are selected and the player switches between
     * them during playback (e.g., based on the available network bandwidth).
     *
     * <p>This class doesn't provide a way to determine which of the selected tracks is currently
     * playing, however some player implementations have ways of getting such information. For
     * example, ExoPlayer provides this information via {@code ExoTrackSelection.getSelectedFormat}.
     *
     * @param trackIndex The index of the track in the group.
     * @return True if the track is selected, false otherwise.
     */
    public boolean isTrackSelected(int trackIndex) {
      return trackSelected[trackIndex];
    }

    /** Returns the {@link C.TrackType} of the group. */
    public @C.TrackType int getType() {
      return mediaTrackGroup.type;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      Group that = (Group) other;
      return adaptiveSupported == that.adaptiveSupported
          && mediaTrackGroup.equals(that.mediaTrackGroup)
          && Arrays.equals(trackSupport, that.trackSupport)
          && Arrays.equals(trackSelected, that.trackSelected);
    }

    @Override
    public int hashCode() {
      int result = mediaTrackGroup.hashCode();
      result = 31 * result + (adaptiveSupported ? 1 : 0);
      result = 31 * result + Arrays.hashCode(trackSupport);
      result = 31 * result + Arrays.hashCode(trackSelected);
      return result;
    }

    // Bundleable implementation.

    private static final String FIELD_TRACK_GROUP = Util.intToStringMaxRadix(0);
    private static final String FIELD_TRACK_SUPPORT = Util.intToStringMaxRadix(1);
    private static final String FIELD_TRACK_SELECTED = Util.intToStringMaxRadix(3);
    private static final String FIELD_ADAPTIVE_SUPPORTED = Util.intToStringMaxRadix(4);

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putBundle(FIELD_TRACK_GROUP, mediaTrackGroup.toBundle());
      bundle.putIntArray(FIELD_TRACK_SUPPORT, trackSupport);
      bundle.putBooleanArray(FIELD_TRACK_SELECTED, trackSelected);
      bundle.putBoolean(FIELD_ADAPTIVE_SUPPORTED, adaptiveSupported);
      return bundle;
    }

    /** Object that can restore a group of tracks from a {@link Bundle}. */
    public static final Creator<Group> CREATOR =
        bundle -> {
          // Can't create a Tracks.Group without a TrackGroup
          TrackGroup trackGroup =
              TrackGroup.CREATOR.fromBundle(checkNotNull(bundle.getBundle(FIELD_TRACK_GROUP)));
          final @C.FormatSupport int[] trackSupport =
              MoreObjects.firstNonNull(
                  bundle.getIntArray(FIELD_TRACK_SUPPORT), new int[trackGroup.length]);
          boolean[] selected =
              MoreObjects.firstNonNull(
                  bundle.getBooleanArray(FIELD_TRACK_SELECTED), new boolean[trackGroup.length]);
          boolean adaptiveSupported = bundle.getBoolean(FIELD_ADAPTIVE_SUPPORTED, false);
          return new Group(trackGroup, adaptiveSupported, trackSupport, selected);
        };
  }

  /** Empty tracks. */
  public static final Tracks EMPTY = new Tracks(ImmutableList.of());

  private final ImmutableList<Group> groups;

  /**
   * Constructs an instance.
   *
   * @param groups The {@link Group groups} of tracks.
   */
  public Tracks(List<Group> groups) {
    this.groups = ImmutableList.copyOf(groups);
  }

  /** Returns the {@link Group groups} of tracks. */
  public ImmutableList<Group> getGroups() {
    return groups;
  }

  /** Returns {@code true} if there are no tracks, and {@code false} otherwise. */
  public boolean isEmpty() {
    return groups.isEmpty();
  }

  /** Returns true if there are tracks of type {@code trackType}, and false otherwise. */
  public boolean containsType(@C.TrackType int trackType) {
    for (int i = 0; i < groups.size(); i++) {
      if (groups.get(i).getType() == trackType) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if at least one track of type {@code trackType} is {@link
   * Group#isTrackSupported(int) supported}.
   */
  public boolean isTypeSupported(@C.TrackType int trackType) {
    return isTypeSupported(trackType, /* allowExceedsCapabilities= */ false);
  }

  /**
   * Returns true if at least one track of type {@code trackType} is {@link
   * Group#isTrackSupported(int, boolean) supported}.
   *
   * @param allowExceedsCapabilities Whether to consider the track as supported if it has a
   *     supported {@link Format#sampleMimeType MIME type}, but otherwise exceeds the advertised
   *     capabilities of the device. For example, a video track for which there's a corresponding
   *     decoder whose maximum advertised resolution is exceeded by the resolution of the track.
   *     Such tracks may be playable in some cases.
   */
  public boolean isTypeSupported(@C.TrackType int trackType, boolean allowExceedsCapabilities) {
    for (int i = 0; i < groups.size(); i++) {
      if (groups.get(i).getType() == trackType) {
        if (groups.get(i).isSupported(allowExceedsCapabilities)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @deprecated Use {@link #containsType(int)} and {@link #isTypeSupported(int)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public boolean isTypeSupportedOrEmpty(@C.TrackType int trackType) {
    return isTypeSupportedOrEmpty(trackType, /* allowExceedsCapabilities= */ false);
  }

  /**
   * @deprecated Use {@link #containsType(int)} and {@link #isTypeSupported(int, boolean)}.
   */
  @Deprecated
  public boolean isTypeSupportedOrEmpty(
      @C.TrackType int trackType, boolean allowExceedsCapabilities) {
    return !containsType(trackType) || isTypeSupported(trackType, allowExceedsCapabilities);
  }

  /** Returns true if at least one track of the type {@code trackType} is selected for playback. */
  public boolean isTypeSelected(@C.TrackType int trackType) {
    for (int i = 0; i < groups.size(); i++) {
      Group group = groups.get(i);
      if (group.isSelected() && group.getType() == trackType) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Tracks that = (Tracks) other;
    return groups.equals(that.groups);
  }

  @Override
  public int hashCode() {
    return groups.hashCode();
  }
  // Bundleable implementation.

  private static final String FIELD_TRACK_GROUPS = Util.intToStringMaxRadix(0);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(FIELD_TRACK_GROUPS, toBundleArrayList(groups));
    return bundle;
  }

  /** Object that can restore tracks from a {@link Bundle}. */
  public static final Creator<Tracks> CREATOR =
      bundle -> {
        @Nullable List<Bundle> groupBundles = bundle.getParcelableArrayList(FIELD_TRACK_GROUPS);
        List<Group> groups =
            groupBundles == null
                ? ImmutableList.of()
                : BundleableUtil.fromBundleList(Group.CREATOR, groupBundles);
        return new Tracks(groups);
      };
}
