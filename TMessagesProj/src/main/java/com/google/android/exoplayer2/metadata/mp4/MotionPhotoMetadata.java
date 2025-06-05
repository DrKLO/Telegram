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
import com.google.common.primitives.Longs;

/** Metadata of a motion photo file. */
public final class MotionPhotoMetadata implements Metadata.Entry {

  /** The start offset of the photo data, in bytes. */
  public final long photoStartPosition;
  /** The size of the photo data, in bytes. */
  public final long photoSize;
  /**
   * The presentation timestamp of the photo, in microseconds, or {@link C#TIME_UNSET} if unknown.
   */
  public final long photoPresentationTimestampUs;
  /** The start offset of the video data, in bytes. */
  public final long videoStartPosition;
  /** The size of the video data, in bytes. */
  public final long videoSize;

  /** Creates an instance. */
  public MotionPhotoMetadata(
      long photoStartPosition,
      long photoSize,
      long photoPresentationTimestampUs,
      long videoStartPosition,
      long videoSize) {
    this.photoStartPosition = photoStartPosition;
    this.photoSize = photoSize;
    this.photoPresentationTimestampUs = photoPresentationTimestampUs;
    this.videoStartPosition = videoStartPosition;
    this.videoSize = videoSize;
  }

  private MotionPhotoMetadata(Parcel in) {
    photoStartPosition = in.readLong();
    photoSize = in.readLong();
    photoPresentationTimestampUs = in.readLong();
    videoStartPosition = in.readLong();
    videoSize = in.readLong();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MotionPhotoMetadata other = (MotionPhotoMetadata) obj;
    return photoStartPosition == other.photoStartPosition
        && photoSize == other.photoSize
        && photoPresentationTimestampUs == other.photoPresentationTimestampUs
        && videoStartPosition == other.videoStartPosition
        && videoSize == other.videoSize;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Longs.hashCode(photoStartPosition);
    result = 31 * result + Longs.hashCode(photoSize);
    result = 31 * result + Longs.hashCode(photoPresentationTimestampUs);
    result = 31 * result + Longs.hashCode(videoStartPosition);
    result = 31 * result + Longs.hashCode(videoSize);
    return result;
  }

  @Override
  public String toString() {
    return "Motion photo metadata: photoStartPosition="
        + photoStartPosition
        + ", photoSize="
        + photoSize
        + ", photoPresentationTimestampUs="
        + photoPresentationTimestampUs
        + ", videoStartPosition="
        + videoStartPosition
        + ", videoSize="
        + videoSize;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(photoStartPosition);
    dest.writeLong(photoSize);
    dest.writeLong(photoPresentationTimestampUs);
    dest.writeLong(videoStartPosition);
    dest.writeLong(videoSize);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<MotionPhotoMetadata> CREATOR =
      new Parcelable.Creator<MotionPhotoMetadata>() {

        @Override
        public MotionPhotoMetadata createFromParcel(Parcel in) {
          return new MotionPhotoMetadata(in);
        }

        @Override
        public MotionPhotoMetadata[] newArray(int size) {
          return new MotionPhotoMetadata[size];
        }
      };
}
