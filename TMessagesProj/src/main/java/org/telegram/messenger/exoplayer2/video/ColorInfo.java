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
package org.telegram.messenger.exoplayer2.video;

import android.os.Parcel;
import android.os.Parcelable;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import java.util.Arrays;

/**
 * Stores color info.
 */
public final class ColorInfo implements Parcelable {

  /**
   * The color space of the video. Valid values are {@link C#COLOR_SPACE_BT601}, {@link
   * C#COLOR_SPACE_BT709}, {@link C#COLOR_SPACE_BT2020} or {@link Format#NO_VALUE} if unknown.
   */
  @C.ColorSpace
  public final int colorSpace;

  /**
   * The color range of the video. Valid values are {@link C#COLOR_RANGE_LIMITED}, {@link
   * C#COLOR_RANGE_FULL} or {@link Format#NO_VALUE} if unknown.
   */
  @C.ColorRange
  public final int colorRange;

  /**
   * The color transfer characteristicks of the video. Valid values are {@link
   * C#COLOR_TRANSFER_HLG}, {@link C#COLOR_TRANSFER_ST2084}, {@link C#COLOR_TRANSFER_SDR} or {@link
   * Format#NO_VALUE} if unknown.
   */
  @C.ColorTransfer
  public final int colorTransfer;

  /**
   * HdrStaticInfo as defined in CTA-861.3.
   */
  public final byte[] hdrStaticInfo;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * Constructs the ColorInfo.
   *
   * @param colorSpace The color space of the video.
   * @param colorRange The color range of the video.
   * @param colorTransfer The color transfer characteristics of the video.
   * @param hdrStaticInfo HdrStaticInfo as defined in CTA-861.3.
   */
  public ColorInfo(@C.ColorSpace int colorSpace, @C.ColorRange int colorRange,
      @C.ColorTransfer int colorTransfer, byte[] hdrStaticInfo) {
    this.colorSpace = colorSpace;
    this.colorRange = colorRange;
    this.colorTransfer = colorTransfer;
    this.hdrStaticInfo = hdrStaticInfo;
  }

  @SuppressWarnings("ResourceType")
  /* package */ ColorInfo(Parcel in) {
    colorSpace = in.readInt();
    colorRange = in.readInt();
    colorTransfer = in.readInt();
    boolean hasHdrStaticInfo = in.readInt() != 0;
    hdrStaticInfo = hasHdrStaticInfo ? in.createByteArray() : null;
  }

  // Parcelable implementation.
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ColorInfo other = (ColorInfo) obj;
    if (colorSpace != other.colorSpace || colorRange != other.colorRange
        || colorTransfer != other.colorTransfer
        || !Arrays.equals(hdrStaticInfo, other.hdrStaticInfo)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ColorInfo(" + colorSpace + ", " + colorRange + ", " + colorTransfer
        + ", " + (hdrStaticInfo != null) + ")";
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + colorSpace;
      result = 31 * result + colorRange;
      result = 31 * result + colorTransfer;
      result = 31 * result + Arrays.hashCode(hdrStaticInfo);
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(colorSpace);
    dest.writeInt(colorRange);
    dest.writeInt(colorTransfer);
    dest.writeInt(hdrStaticInfo != null ? 1 : 0);
    if (hdrStaticInfo != null) {
      dest.writeByteArray(hdrStaticInfo);
    }
  }

  public static final Parcelable.Creator<ColorInfo> CREATOR = new Parcelable.Creator<ColorInfo>() {
    @Override
    public ColorInfo createFromParcel(Parcel in) {
      return new ColorInfo(in);
    }

    @Override
    public ColorInfo[] newArray(int size) {
      return new ColorInfo[0];
    }
  };

}
