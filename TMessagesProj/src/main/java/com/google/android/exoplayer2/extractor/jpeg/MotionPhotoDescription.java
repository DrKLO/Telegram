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
package com.google.android.exoplayer2.extractor.jpeg;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.List;

/** Describes the layout and metadata of a motion photo file. */
/* package */ final class MotionPhotoDescription {

  /** Describes a media item in the motion photo. */
  public static final class ContainerItem {
    /** The MIME type of the media item. */
    public final String mime;
    /** The application-specific meaning of the media item. */
    public final String semantic;
    /**
     * The positive integer length in bytes of the media item, or 0 for primary media items and
     * secondary media items that share their resource with the preceding media item.
     */
    public final long length;
    /**
     * The number of bytes of additional padding between the end of the primary media item and the
     * start of the next media item. 0 for secondary media items.
     */
    public final long padding;

    public ContainerItem(String mime, String semantic, long length, long padding) {
      this.mime = mime;
      this.semantic = semantic;
      this.length = length;
      this.padding = padding;
    }
  }

  /**
   * The presentation timestamp of the primary media item, in microseconds, or {@link C#TIME_UNSET}
   * if unknown.
   */
  public final long photoPresentationTimestampUs;
  /**
   * The media items represented by the motion photo file, in order. The primary media item is
   * listed first, followed by any secondary media items.
   */
  public final List<ContainerItem> items;

  public MotionPhotoDescription(long photoPresentationTimestampUs, List<ContainerItem> items) {
    this.photoPresentationTimestampUs = photoPresentationTimestampUs;
    this.items = items;
  }

  /**
   * Returns the {@link MotionPhotoMetadata} for the motion photo represented by this instance, or
   * {@code null} if there wasn't enough information to derive the metadata.
   *
   * @param motionPhotoLength The length of the motion photo file, in bytes.
   * @return The motion photo metadata, or {@code null}.
   */
  @Nullable
  public MotionPhotoMetadata getMotionPhotoMetadata(long motionPhotoLength) {
    if (items.size() < 2) {
      // We need a primary item (photo) and at least one secondary item (video).
      return null;
    }
    // Iterate backwards through the items to find the earlier video in the list. If we find a video
    // item with length zero, we need to keep scanning backwards to find the preceding item with
    // non-zero length, which is the item that contains the video data.
    long photoStartPosition = C.POSITION_UNSET;
    long photoLength = C.LENGTH_UNSET;
    long mp4StartPosition = C.POSITION_UNSET;
    long mp4Length = C.LENGTH_UNSET;
    boolean itemContainsMp4 = false;
    long itemStartPosition = motionPhotoLength;
    long itemEndPosition = motionPhotoLength;
    for (int i = items.size() - 1; i >= 0; i--) {
      MotionPhotoDescription.ContainerItem item = items.get(i);
      itemContainsMp4 |= MimeTypes.VIDEO_MP4.equals(item.mime);
      itemEndPosition = itemStartPosition;
      if (i == 0) {
        // Padding is only applied for the primary item.
        itemStartPosition = 0;
        itemEndPosition -= item.padding;
      } else {
        itemStartPosition -= item.length;
      }
      if (itemContainsMp4 && itemStartPosition != itemEndPosition) {
        mp4StartPosition = itemStartPosition;
        mp4Length = itemEndPosition - itemStartPosition;
        // Reset in case there's another video earlier in the list.
        itemContainsMp4 = false;
      }
      if (i == 0) {
        photoStartPosition = itemStartPosition;
        photoLength = itemEndPosition;
      }
    }
    if (mp4StartPosition == C.POSITION_UNSET
        || mp4Length == C.LENGTH_UNSET
        || photoStartPosition == C.POSITION_UNSET
        || photoLength == C.LENGTH_UNSET) {
      return null;
    }
    return new MotionPhotoMetadata(
        photoStartPosition, photoLength, photoPresentationTimestampUs, mp4StartPosition, mp4Length);
  }
}
