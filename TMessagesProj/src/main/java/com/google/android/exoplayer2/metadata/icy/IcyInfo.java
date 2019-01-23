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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;

/** ICY in-stream information. */
public final class IcyInfo implements Metadata.Entry {

  /** The stream title if present, or {@code null}. */
  @Nullable public final String title;
  /** The stream title if present, or {@code null}. */
  @Nullable public final String url;

  /**
   * @param title See {@link #title}.
   * @param url See {@link #url}.
   */
  public IcyInfo(@Nullable String title, @Nullable String url) {
    this.title = title;
    this.url = url;
  }

  /* package */ IcyInfo(Parcel in) {
    title = in.readString();
    url = in.readString();
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
    return Util.areEqual(title, other.title) && Util.areEqual(url, other.url);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (title != null ? title.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ICY: title=\"" + title + "\", url=\"" + url + "\"";
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
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
