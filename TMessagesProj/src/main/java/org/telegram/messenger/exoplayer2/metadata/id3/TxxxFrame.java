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
package org.telegram.messenger.exoplayer2.metadata.id3;

import android.os.Parcel;
import android.os.Parcelable;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * TXXX (User defined text information) ID3 frame.
 */
public final class TxxxFrame extends Id3Frame {

  public static final String ID = "TXXX";

  public final String description;
  public final String value;

  public TxxxFrame(String description, String value) {
    super(ID);
    this.description = description;
    this.value = value;
  }

  /* package */ TxxxFrame(Parcel in) {
    super(ID);
    description = in.readString();
    value = in.readString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TxxxFrame other = (TxxxFrame) obj;
    return Util.areEqual(description, other.description) && Util.areEqual(value, other.value);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(description);
    dest.writeString(value);
  }

  public static final Parcelable.Creator<TxxxFrame> CREATOR = new Parcelable.Creator<TxxxFrame>() {

    @Override
    public TxxxFrame createFromParcel(Parcel in) {
      return new TxxxFrame(in);
    }

    @Override
    public TxxxFrame[] newArray(int size) {
      return new TxxxFrame[size];
    }

  };

}
