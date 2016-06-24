/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor.mp3;

import org.telegram.messenger.exoplayer.C;

/**
 * MP3 seeker that doesn't rely on metadata and seeks assuming the source has a constant bitrate.
 */
/* package */ final class ConstantBitrateSeeker implements Mp3Extractor.Seeker {

  private static final int BITS_PER_BYTE = 8;

  private final long firstFramePosition;
  private final int bitrate;
  private final long durationUs;

  public ConstantBitrateSeeker(long firstFramePosition, int bitrate, long inputLength) {
    this.firstFramePosition = firstFramePosition;
    this.bitrate = bitrate;
    durationUs = inputLength == C.LENGTH_UNBOUNDED ? C.UNKNOWN_TIME_US : getTimeUs(inputLength);
  }

  @Override
  public boolean isSeekable() {
    return durationUs != C.UNKNOWN_TIME_US;
  }

  @Override
  public long getPosition(long timeUs) {
    return durationUs == C.UNKNOWN_TIME_US ? 0
        : firstFramePosition + (timeUs * bitrate) / (C.MICROS_PER_SECOND * BITS_PER_BYTE);
  }

  @Override
  public long getTimeUs(long position) {
    return (Math.max(0, position - firstFramePosition) * C.MICROS_PER_SECOND * BITS_PER_BYTE)
        / bitrate;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

}
