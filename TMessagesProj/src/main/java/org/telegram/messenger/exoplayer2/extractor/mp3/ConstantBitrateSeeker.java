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
package org.telegram.messenger.exoplayer2.extractor.mp3;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.extractor.MpegAudioHeader;
import org.telegram.messenger.exoplayer2.extractor.SeekPoint;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * MP3 seeker that doesn't rely on metadata and seeks assuming the source has a constant bitrate.
 */
/* package */ final class ConstantBitrateSeeker implements Mp3Extractor.Seeker {

  private static final int BITS_PER_BYTE = 8;

  private final long firstFramePosition;
  private final int frameSize;
  private final long dataSize;
  private final int bitrate;
  private final long durationUs;

  /**
   * @param inputLength The length of the stream in bytes, or {@link C#LENGTH_UNSET} if unknown.
   * @param firstFramePosition The position of the first frame in the stream.
   * @param mpegAudioHeader The MPEG audio header associated with the first frame.
   */
  public ConstantBitrateSeeker(long inputLength, long firstFramePosition,
      MpegAudioHeader mpegAudioHeader) {
    this.firstFramePosition = firstFramePosition;
    this.frameSize = mpegAudioHeader.frameSize;
    this.bitrate = mpegAudioHeader.bitrate;
    if (inputLength == C.LENGTH_UNSET) {
      dataSize = C.LENGTH_UNSET;
      durationUs = C.TIME_UNSET;
    } else {
      dataSize = inputLength - firstFramePosition;
      durationUs = getTimeUs(inputLength);
    }
  }

  @Override
  public boolean isSeekable() {
    return dataSize != C.LENGTH_UNSET;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    if (dataSize == C.LENGTH_UNSET) {
      return new SeekPoints(new SeekPoint(0, firstFramePosition));
    }
    long positionOffset = (timeUs * bitrate) / (C.MICROS_PER_SECOND * BITS_PER_BYTE);
    // Constrain to nearest preceding frame offset.
    positionOffset = (positionOffset / frameSize) * frameSize;
    positionOffset = Util.constrainValue(positionOffset, 0, dataSize - frameSize);
    long seekPosition = firstFramePosition + positionOffset;
    long seekTimeUs = getTimeUs(seekPosition);
    SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
    if (seekTimeUs >= timeUs || positionOffset == dataSize - frameSize) {
      return new SeekPoints(seekPoint);
    } else {
      long secondSeekPosition = seekPosition + frameSize;
      long secondSeekTimeUs = getTimeUs(secondSeekPosition);
      SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
      return new SeekPoints(seekPoint, secondSeekPoint);
    }
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
