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
package com.google.android.exoplayer2.extractor.wav;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Util;

/* package */ final class WavSeekMap implements SeekMap {

  private final WavFormat wavFormat;
  private final int framesPerBlock;
  private final long firstBlockPosition;
  private final long blockCount;
  private final long durationUs;

  public WavSeekMap(
      WavFormat wavFormat, int framesPerBlock, long dataStartPosition, long dataEndPosition) {
    this.wavFormat = wavFormat;
    this.framesPerBlock = framesPerBlock;
    this.firstBlockPosition = dataStartPosition;
    this.blockCount = (dataEndPosition - dataStartPosition) / wavFormat.blockSize;
    durationUs = blockIndexToTimeUs(blockCount);
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    // Calculate the containing block index, constraining to valid indices.
    long blockIndex = (timeUs * wavFormat.frameRateHz) / (C.MICROS_PER_SECOND * framesPerBlock);
    blockIndex = Util.constrainValue(blockIndex, 0, blockCount - 1);

    long seekPosition = firstBlockPosition + (blockIndex * wavFormat.blockSize);
    long seekTimeUs = blockIndexToTimeUs(blockIndex);
    SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
    if (seekTimeUs >= timeUs || blockIndex == blockCount - 1) {
      return new SeekPoints(seekPoint);
    } else {
      long secondBlockIndex = blockIndex + 1;
      long secondSeekPosition = firstBlockPosition + (secondBlockIndex * wavFormat.blockSize);
      long secondSeekTimeUs = blockIndexToTimeUs(secondBlockIndex);
      SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
      return new SeekPoints(seekPoint, secondSeekPoint);
    }
  }

  private long blockIndexToTimeUs(long blockIndex) {
    return Util.scaleLargeTimestamp(
        blockIndex * framesPerBlock, C.MICROS_PER_SECOND, wavFormat.frameRateHz);
  }
}
