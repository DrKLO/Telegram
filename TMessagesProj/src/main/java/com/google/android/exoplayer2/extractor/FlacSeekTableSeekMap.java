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
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.Util;

/**
 * A {@link SeekMap} implementation for FLAC streams that contain a <a
 * href="https://xiph.org/flac/format.html#metadata_block_seektable">seek table</a>.
 */
public final class FlacSeekTableSeekMap implements SeekMap {

  private final FlacStreamMetadata flacStreamMetadata;
  private final long firstFrameOffset;

  /**
   * Creates a seek map from the FLAC stream seek table.
   *
   * @param flacStreamMetadata The stream metadata.
   * @param firstFrameOffset The byte offset of the first frame in the stream.
   */
  public FlacSeekTableSeekMap(FlacStreamMetadata flacStreamMetadata, long firstFrameOffset) {
    this.flacStreamMetadata = flacStreamMetadata;
    this.firstFrameOffset = firstFrameOffset;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return flacStreamMetadata.getDurationUs();
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    Assertions.checkNotNull(flacStreamMetadata.seekTable);
    long[] pointSampleNumbers = flacStreamMetadata.seekTable.pointSampleNumbers;
    long[] pointOffsets = flacStreamMetadata.seekTable.pointOffsets;

    long targetSampleNumber = flacStreamMetadata.getSampleNumber(timeUs);
    int index =
        Util.binarySearchFloor(
            pointSampleNumbers,
            targetSampleNumber,
            /* inclusive= */ true,
            /* stayInBounds= */ false);

    long seekPointSampleNumber = index == -1 ? 0 : pointSampleNumbers[index];
    long seekPointOffsetFromFirstFrame = index == -1 ? 0 : pointOffsets[index];
    SeekPoint seekPoint = getSeekPoint(seekPointSampleNumber, seekPointOffsetFromFirstFrame);
    if (seekPoint.timeUs == timeUs || index == pointSampleNumbers.length - 1) {
      return new SeekPoints(seekPoint);
    } else {
      SeekPoint secondSeekPoint =
          getSeekPoint(pointSampleNumbers[index + 1], pointOffsets[index + 1]);
      return new SeekPoints(seekPoint, secondSeekPoint);
    }
  }

  private SeekPoint getSeekPoint(long sampleNumber, long offsetFromFirstFrame) {
    long seekTimeUs = sampleNumber * C.MICROS_PER_SECOND / flacStreamMetadata.sampleRate;
    long seekPosition = firstFrameOffset + offsetFromFirstFrame;
    return new SeekPoint(seekTimeUs, seekPosition);
  }
}
