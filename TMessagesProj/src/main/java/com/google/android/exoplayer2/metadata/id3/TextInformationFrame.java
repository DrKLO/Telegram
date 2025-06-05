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
package com.google.android.exoplayer2.metadata.id3;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.List;

/** Text information ID3 frame. */
public final class TextInformationFrame extends Id3Frame {

  @Nullable public final String description;

  /**
   * @deprecated Use the first element of {@link #values} instead.
   */
  @Deprecated public final String value;

  /** The text values of this frame. Will always have at least one element. */
  public final ImmutableList<String> values;

  public TextInformationFrame(String id, @Nullable String description, List<String> values) {
    super(id);
    checkArgument(!values.isEmpty());

    this.description = description;
    this.values = ImmutableList.copyOf(values);
    this.value = this.values.get(0);
  }

  /**
   * @deprecated Use {@code TextInformationFrame(String id, String description, String[] values}
   *     instead
   */
  @Deprecated
  @InlineMe(
      replacement = "this(id, description, ImmutableList.of(value))",
      imports = "com.google.common.collect.ImmutableList")
  public TextInformationFrame(String id, @Nullable String description, String value) {
    this(id, description, ImmutableList.of(value));
  }

  private TextInformationFrame(Parcel in) {
    this(
        checkNotNull(in.readString()),
        in.readString(),
        ImmutableList.copyOf(checkNotNull(in.createStringArray())));
  }

  /**
   * Uses the first element in {@link #values} to set the relevant field in {@link MediaMetadata}
   * (as determined by {@link #id}).
   */
  @Override
  public void populateMediaMetadata(MediaMetadata.Builder builder) {
    switch (id) {
      case "TT2":
      case "TIT2":
        builder.setTitle(values.get(0));
        break;
      case "TP1":
      case "TPE1":
        builder.setArtist(values.get(0));
        break;
      case "TP2":
      case "TPE2":
        builder.setAlbumArtist(values.get(0));
        break;
      case "TAL":
      case "TALB":
        builder.setAlbumTitle(values.get(0));
        break;
      case "TRK":
      case "TRCK":
        String[] trackNumbers = Util.split(values.get(0), "/");
        try {
          int trackNumber = Integer.parseInt(trackNumbers[0]);
          @Nullable
          Integer totalTrackCount =
              trackNumbers.length > 1 ? Integer.parseInt(trackNumbers[1]) : null;
          builder.setTrackNumber(trackNumber).setTotalTrackCount(totalTrackCount);
        } catch (NumberFormatException e) {
          // Do nothing, invalid input.
        }
        break;
      case "TYE":
      case "TYER":
        try {
          builder.setRecordingYear(Integer.parseInt(values.get(0)));
        } catch (NumberFormatException e) {
          // Do nothing, invalid input.
        }
        break;
      case "TDA":
      case "TDAT":
        try {
          String date = values.get(0);
          int month = Integer.parseInt(date.substring(2, 4));
          int day = Integer.parseInt(date.substring(0, 2));
          builder.setRecordingMonth(month).setRecordingDay(day);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
          // Do nothing, invalid input.
        }
        break;
      case "TDRC":
        List<Integer> recordingDate = parseId3v2point4TimestampFrameForDate(values.get(0));
        switch (recordingDate.size()) {
          case 3:
            builder.setRecordingDay(recordingDate.get(2));
            // fall through
          case 2:
            builder.setRecordingMonth(recordingDate.get(1));
            // fall through
          case 1:
            builder.setRecordingYear(recordingDate.get(0));
            // fall through
            break;
          default:
            // Do nothing.
            break;
        }
        break;
      case "TDRL":
        List<Integer> releaseDate = parseId3v2point4TimestampFrameForDate(values.get(0));
        switch (releaseDate.size()) {
          case 3:
            builder.setReleaseDay(releaseDate.get(2));
            // fall through
          case 2:
            builder.setReleaseMonth(releaseDate.get(1));
            // fall through
          case 1:
            builder.setReleaseYear(releaseDate.get(0));
            // fall through
            break;
          default:
            // Do nothing.
            break;
        }
        break;
      case "TCM":
      case "TCOM":
        builder.setComposer(values.get(0));
        break;
      case "TP3":
      case "TPE3":
        builder.setConductor(values.get(0));
        break;
      case "TXT":
      case "TEXT":
        builder.setWriter(values.get(0));
        break;
      default:
        break;
    }
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TextInformationFrame other = (TextInformationFrame) obj;
    return Util.areEqual(id, other.id)
        && Util.areEqual(description, other.description)
        && values.equals(other.values);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + id.hashCode();
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + values.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return id + ": description=" + description + ": values=" + values;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(description);
    dest.writeStringArray(values.toArray(new String[0]));
  }

  public static final Parcelable.Creator<TextInformationFrame> CREATOR =
      new Parcelable.Creator<TextInformationFrame>() {

        @Override
        public TextInformationFrame createFromParcel(Parcel in) {
          return new TextInformationFrame(in);
        }

        @Override
        public TextInformationFrame[] newArray(int size) {
          return new TextInformationFrame[size];
        }
      };

  // Private methods

  private static List<Integer> parseId3v2point4TimestampFrameForDate(String value) {
    // Timestamp string format is ISO-8601, can be `yyyy-MM-ddTHH:mm:ss`, or reduced precision
    // at each point, for example `yyyy-MM` or `yyyy-MM-ddTHH:mm`.
    List<Integer> dates = new ArrayList<>();
    try {
      if (value.length() >= 10) {
        dates.add(Integer.parseInt(value.substring(0, 4)));
        dates.add(Integer.parseInt(value.substring(5, 7)));
        dates.add(Integer.parseInt(value.substring(8, 10)));
      } else if (value.length() >= 7) {
        dates.add(Integer.parseInt(value.substring(0, 4)));
        dates.add(Integer.parseInt(value.substring(5, 7)));
      } else if (value.length() >= 4) {
        dates.add(Integer.parseInt(value.substring(0, 4)));
      }
    } catch (NumberFormatException e) {
      // Invalid output, return.
      return new ArrayList<>();
    }
    return dates;
  }
}
