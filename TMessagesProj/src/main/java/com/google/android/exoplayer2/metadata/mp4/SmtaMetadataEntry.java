/*
 * Copyright 2020 The Android Open Source Project
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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.common.primitives.Floats;

/**
 * Stores metadata from the Samsung smta box.
 *
 * <p>See [Internal: b/150138465#comment76].
 */
public final class SmtaMetadataEntry implements Metadata.Entry {

  /**
   * The capture frame rate, in fps, or {@link C#RATE_UNSET} if it is unknown.
   *
   * <p>If known, the capture frame rate should always be an integer value.
   */
  public final float captureFrameRate;
  /** The number of layers in the SVC extended frames. */
  public final int svcTemporalLayerCount;

  /** Creates an instance. */
  public SmtaMetadataEntry(float captureFrameRate, int svcTemporalLayerCount) {
    this.captureFrameRate = captureFrameRate;
    this.svcTemporalLayerCount = svcTemporalLayerCount;
  }

  private SmtaMetadataEntry(Parcel in) {
    captureFrameRate = in.readFloat();
    svcTemporalLayerCount = in.readInt();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SmtaMetadataEntry other = (SmtaMetadataEntry) obj;
    return captureFrameRate == other.captureFrameRate
        && svcTemporalLayerCount == other.svcTemporalLayerCount;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Floats.hashCode(captureFrameRate);
    result = 31 * result + svcTemporalLayerCount;
    return result;
  }

  @Override
  public String toString() {
    return "smta: captureFrameRate="
        + captureFrameRate
        + ", svcTemporalLayerCount="
        + svcTemporalLayerCount;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(captureFrameRate);
    dest.writeInt(svcTemporalLayerCount);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<SmtaMetadataEntry> CREATOR =
      new Parcelable.Creator<SmtaMetadataEntry>() {

        @Override
        public SmtaMetadataEntry createFromParcel(Parcel in) {
          return new SmtaMetadataEntry(in);
        }

        @Override
        public SmtaMetadataEntry[] newArray(int size) {
          return new SmtaMetadataEntry[size];
        }
      };
}
