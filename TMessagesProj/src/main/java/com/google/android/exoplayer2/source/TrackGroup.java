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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.os.Bundle;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.util.BundleableUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable group of tracks available within a media stream. All tracks in a group present the
 * same content, but their formats may differ.
 *
 * <p>As an example of how tracks can be grouped, consider an adaptive playback where a main video
 * feed is provided in five resolutions, and an alternative video feed (e.g., a different camera
 * angle in a sports match) is provided in two resolutions. In this case there will be two video
 * track groups, one corresponding to the main video feed containing five tracks, and a second for
 * the alternative video feed containing two tracks.
 *
 * <p>Note that audio tracks whose languages differ are not grouped, because content in different
 * languages is not considered to be the same. Conversely, audio tracks in the same language that
 * only differ in properties such as bitrate, sampling rate, channel count and so on can be grouped.
 * This also applies to text tracks.
 *
 * <p>Note also that this class only contains information derived from the media itself. Unlike
 * {@link Tracks.Group}, it does not include runtime information such as the extent to which
 * playback of each track is supported by the device, or which tracks are currently selected.
 */
public final class TrackGroup implements Bundleable {

  private static final String TAG = "TrackGroup";

  /** The number of tracks in the group. */
  public final int length;
  /** An identifier for the track group. */
  public final String id;
  /** The type of tracks in the group. */
  public final @C.TrackType int type;

  private final Format[] formats;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * Constructs a track group containing the provided {@code formats}.
   *
   * @param formats The list of {@link Format Formats}. Must not be empty.
   */
  public TrackGroup(Format... formats) {
    this(/* id= */ "", formats);
  }

  /**
   * Constructs a track group with the provided {@code id} and {@code formats}.
   *
   * @param id The identifier of the track group. May be an empty string.
   * @param formats The list of {@link Format Formats}. Must not be empty.
   */
  public TrackGroup(String id, Format... formats) {
    checkArgument(formats.length > 0);
    this.id = id;
    this.formats = formats;
    this.length = formats.length;
    @C.TrackType int type = MimeTypes.getTrackType(formats[0].sampleMimeType);
    if (type == C.TRACK_TYPE_UNKNOWN) {
      type = MimeTypes.getTrackType(formats[0].containerMimeType);
    }
    this.type = type;
    verifyCorrectness();
  }

  /**
   * Returns a copy of this track group with the specified {@code id}.
   *
   * @param id The identifier for the copy of the track group.
   * @return The copied track group.
   */
  @CheckResult
  public TrackGroup copyWithId(String id) {
    return new TrackGroup(id, formats);
  }

  /**
   * Returns the format of the track at a given index.
   *
   * @param index The index of the track.
   * @return The track's format.
   */
  public Format getFormat(int index) {
    return formats[index];
  }

  /**
   * Returns the index of the track with the given format in the group. The format is located by
   * identity so, for example, {@code group.indexOf(group.getFormat(index)) == index} even if
   * multiple tracks have formats that contain the same values.
   *
   * @param format The format.
   * @return The index of the track, or {@link C#INDEX_UNSET} if no such track exists.
   */
  @SuppressWarnings("ReferenceEquality")
  public int indexOf(Format format) {
    for (int i = 0; i < formats.length; i++) {
      if (format == formats[i]) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + id.hashCode();
      result = 31 * result + Arrays.hashCode(formats);
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackGroup other = (TrackGroup) obj;
    return id.equals(other.id) && Arrays.equals(formats, other.formats);
  }

  // Bundleable implementation.
  private static final String FIELD_FORMATS = Util.intToStringMaxRadix(0);
  private static final String FIELD_ID = Util.intToStringMaxRadix(1);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    ArrayList<Bundle> arrayList = new ArrayList<>(formats.length);
    for (Format format : formats) {
      arrayList.add(format.toBundle(/* excludeMetadata= */ true));
    }
    bundle.putParcelableArrayList(FIELD_FORMATS, arrayList);
    bundle.putString(FIELD_ID, id);
    return bundle;
  }

  /** Object that can restore {@code TrackGroup} from a {@link Bundle}. */
  public static final Creator<TrackGroup> CREATOR =
      bundle -> {
        @Nullable List<Bundle> formatBundles = bundle.getParcelableArrayList(FIELD_FORMATS);
        List<Format> formats =
            formatBundles == null
                ? ImmutableList.of()
                : BundleableUtil.fromBundleList(Format.CREATOR, formatBundles);
        String id = bundle.getString(FIELD_ID, /* defaultValue= */ "");
        return new TrackGroup(id, formats.toArray(new Format[0]));
      };

  private void verifyCorrectness() {
    // TrackGroups should only contain tracks with exactly the same content (but in different
    // qualities). We only log an error instead of throwing to not break backwards-compatibility for
    // cases where malformed TrackGroups happen to work by chance (e.g. because adaptive selections
    // are always disabled).
    String language = normalizeLanguage(formats[0].language);
    @C.RoleFlags int roleFlags = normalizeRoleFlags(formats[0].roleFlags);
    for (int i = 1; i < formats.length; i++) {
      if (!language.equals(normalizeLanguage(formats[i].language))) {
        logErrorMessage(
            /* mismatchField= */ "languages",
            /* valueIndex0= */ formats[0].language,
            /* otherValue=* */ formats[i].language,
            /* otherIndex= */ i);
        return;
      }
      if (roleFlags != normalizeRoleFlags(formats[i].roleFlags)) {
        logErrorMessage(
            /* mismatchField= */ "role flags",
            /* valueIndex0= */ Integer.toBinaryString(formats[0].roleFlags),
            /* otherValue=* */ Integer.toBinaryString(formats[i].roleFlags),
            /* otherIndex= */ i);
        return;
      }
    }
  }

  private static String normalizeLanguage(@Nullable String language) {
    // Treat all variants of undetermined or unknown languages as compatible.
    return language == null || language.equals(C.LANGUAGE_UNDETERMINED) ? "" : language;
  }

  private static @C.RoleFlags int normalizeRoleFlags(@C.RoleFlags int roleFlags) {
    // Treat trick-play and non-trick-play formats as compatible.
    return roleFlags | C.ROLE_FLAG_TRICK_PLAY;
  }

  private static void logErrorMessage(
      String mismatchField,
      @Nullable String valueIndex0,
      @Nullable String otherValue,
      int otherIndex) {
    Log.e(
        TAG,
        "",
        new IllegalStateException(
            "Different "
                + mismatchField
                + " combined in one TrackGroup: '"
                + valueIndex0
                + "' (track 0) and '"
                + otherValue
                + "' (track "
                + otherIndex
                + ")"));
  }
}
