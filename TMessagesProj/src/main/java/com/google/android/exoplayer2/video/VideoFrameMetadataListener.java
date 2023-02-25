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
package com.google.android.exoplayer2.video;

import android.media.MediaFormat;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;

/** A listener for metadata corresponding to video frames being rendered. */
public interface VideoFrameMetadataListener {
  /**
   * Called on the playback thread when a video frame is about to be rendered.
   *
   * @param presentationTimeUs The presentation time of the frame, in microseconds.
   * @param releaseTimeNs The wallclock time at which the frame should be displayed, in nanoseconds.
   *     If the platform API version of the device is less than 21, then this is a best effort.
   * @param format The format associated with the frame.
   * @param mediaFormat The framework media format associated with the frame, or {@code null} if not
   *     known or not applicable (e.g., because the frame was not output by a {@link
   *     android.media.MediaCodec MediaCodec}).
   */
  void onVideoFrameAboutToBeRendered(
      long presentationTimeUs,
      long releaseTimeNs,
      Format format,
      @Nullable MediaFormat mediaFormat);
}
