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
package com.google.android.exoplayer2.metadata.mp4;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/**
 * Stores extensible metadata with handler type 'mdta'. See also the QuickTime File Format
 * Specification.
 */
public final class MdtaMetadataEntry implements Metadata.Entry {

  /** Key for the capture frame rate (in frames per second). */
  public static final String KEY_ANDROID_CAPTURE_FPS = "com.android.capture.fps";

  /** The metadata key name. */
  public final String key;
  /** The payload. The interpretation of the value depends on {@link #typeIndicator}. */
  public final byte[] value;
  /** The four byte locale indicator. */
  public final int localeIndicator;
  /** The four byte type indicator. */
  public final int typeIndicator;

  /** Creates a new metadata entry for the specified metadata key/value. */
  public MdtaMetadataEntry(String key, byte[] value, int localeIndicator, int typeIndicator) {
    this.key = key;
    this.value = value;
    this.localeIndicator = localeIndicator;
    this.typeIndicator = typeIndicator;
  }

  private MdtaMetadataEntry(Parcel in) {
    key = Util.castNonNull(in.readString());
    value = Util.castNonNull(in.createByteArray());
    localeIndicator = in.readInt();
    typeIndicator = in.readInt();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MdtaMetadataEntry other = (MdtaMetadataEntry) obj;
    return key.equals(other.key)
        && Arrays.equals(value, other.value)
        && localeIndicator == other.localeIndicator
        && typeIndicator == other.typeIndicator;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + key.hashCode();
    result = 31 * result + Arrays.hashCode(value);
    result = 31 * result + localeIndicator;
    result = 31 * result + typeIndicator;
    return result;
  }

  @Override
  public String toString() {
    return "mdta: key=" + key;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(key);
    dest.writeByteArray(value);
    dest.writeInt(localeIndicator);
    dest.writeInt(typeIndicator);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<MdtaMetadataEntry> CREATOR =
      new Parcelable.Creator<MdtaMetadataEntry>() {

        @Override
        public MdtaMetadataEntry createFromParcel(Parcel in) {
          return new MdtaMetadataEntry(in);
        }

        @Override
        public MdtaMetadataEntry[] newArray(int size) {
          return new MdtaMetadataEntry[size];
        }
      };
}
