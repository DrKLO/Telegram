/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer2.source.dash.manifest;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Uniquely identifies a {@link Representation} in a {@link DashManifest}.
 */
public final class RepresentationKey implements Parcelable, Comparable<RepresentationKey> {

  public final int periodIndex;
  public final int adaptationSetIndex;
  public final int representationIndex;

  public RepresentationKey(int periodIndex, int adaptationSetIndex, int representationIndex) {
    this.periodIndex = periodIndex;
    this.adaptationSetIndex = adaptationSetIndex;
    this.representationIndex = representationIndex;
  }

  @Override
  public String toString() {
    return periodIndex + "." + adaptationSetIndex + "." + representationIndex;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(periodIndex);
    dest.writeInt(adaptationSetIndex);
    dest.writeInt(representationIndex);
  }

  public static final Creator<RepresentationKey> CREATOR =
      new Creator<RepresentationKey>() {
        @Override
        public RepresentationKey createFromParcel(Parcel in) {
          return new RepresentationKey(in.readInt(), in.readInt(), in.readInt());
        }

        @Override
        public RepresentationKey[] newArray(int size) {
          return new RepresentationKey[size];
        }
      };

  // Comparable implementation.

  @Override
  public int compareTo(@NonNull RepresentationKey o) {
    int result = periodIndex - o.periodIndex;
    if (result == 0) {
      result = adaptationSetIndex - o.adaptationSetIndex;
      if (result == 0) {
        result = representationIndex - o.representationIndex;
      }
    }
    return result;
  }

}
