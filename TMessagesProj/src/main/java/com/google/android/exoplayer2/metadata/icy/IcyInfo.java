/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.icy;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Arrays;

/** ICY in-stream information. */
public final class IcyInfo implements Metadata.Entry {

  /** The complete metadata bytes used to construct this IcyInfo. */
  public final byte[] rawMetadata;
  /** The stream title if present and decodable, or {@code null}. */
  @Nullable public final String title;
  /** The stream URL if present and decodable, or {@code null}. */
  @Nullable public final String url;

  /**
   * Construct a new IcyInfo from the source metadata, and optionally a StreamTitle and StreamUrl
   * that have been extracted.
   *
   * @param rawMetadata See {@link #rawMetadata}.
   * @param title See {@link #title}.
   * @param url See {@link #url}.
   */
  public IcyInfo(byte[] rawMetadata, @Nullable String title, @Nullable String url) {
    this.rawMetadata = rawMetadata;
    this.title = title;
    this.url = url;
  }

  /* package */ IcyInfo(Parcel in) {
    rawMetadata = Assertions.checkNotNull(in.createByteArray());
    title = in.readString();
    url = in.readString();
  }

  @Override
  public void populateMediaMetadata(MediaMetadata.Builder builder) {
    if (title != null) {
      builder.setTitle(title);
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
    IcyInfo other = (IcyInfo) obj;
    // title & url are derived from rawMetadata, so no need to include them in the comparison.
    return Arrays.equals(rawMetadata, other.rawMetadata);
  }

  @Override
  public int hashCode() {
    // title & url are derived from rawMetadata, so no need to include them in the hash.
    return Arrays.hashCode(rawMetadata);
  }

  @Override
  public String toString() {
    return String.format(
        "ICY: title=\"%s\", url=\"%s\", rawMetadata.length=\"%s\"", title, url, rawMetadata.length);
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeByteArray(rawMetadata);
    dest.writeString(title);
    dest.writeString(url);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<IcyInfo> CREATOR =
      new Parcelable.Creator<IcyInfo>() {

        @Override
        public IcyInfo createFromParcel(Parcel in) {
          return new IcyInfo(in);
        }

        @Override
        public IcyInfo[] newArray(int size) {
          return new IcyInfo[size];
        }
      };
}
