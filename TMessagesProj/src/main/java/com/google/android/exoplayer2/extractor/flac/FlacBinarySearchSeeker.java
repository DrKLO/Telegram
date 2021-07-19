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
package com.google.android.exoplayer2.extractor.flac;

import com.google.android.exoplayer2.extractor.BinarySearchSeeker;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.FlacFrameReader;
import com.google.android.exoplayer2.extractor.FlacFrameReader.SampleNumberHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import java.io.IOException;

/**
 * A {@link SeekMap} implementation for FLAC stream using binary search.
 *
 * <p>This seeker performs seeking by using binary search within the stream, until it finds the
 * frame that contains the target sample.
 */
/* package */ final class FlacBinarySearchSeeker extends BinarySearchSeeker {

  /**
   * Creates a {@link FlacBinarySearchSeeker}.
   *
   * @param flacStreamMetadata The stream metadata.
   * @param frameStartMarker The frame start marker, consisting of the 2 bytes by which every frame
   *     in the stream must start.
   * @param firstFramePosition The byte offset of the first frame in the stream.
   * @param inputLength The length of the stream in bytes.
   */
  public FlacBinarySearchSeeker(
      FlacStreamMetadata flacStreamMetadata,
      int frameStartMarker,
      long firstFramePosition,
      long inputLength) {
    super(
        /* seekTimestampConverter= */ flacStreamMetadata::getSampleNumber,
        new FlacTimestampSeeker(flacStreamMetadata, frameStartMarker),
        flacStreamMetadata.getDurationUs(),
        /* floorTimePosition= */ 0,
        /* ceilingTimePosition= */ flacStreamMetadata.totalSamples,
        /* floorBytePosition= */ firstFramePosition,
        /* ceilingBytePosition= */ inputLength,
        /* approxBytesPerFrame= */ flacStreamMetadata.getApproxBytesPerFrame(),
        /* minimumSearchRange= */ Math.max(
            FlacConstants.MIN_FRAME_HEADER_SIZE, flacStreamMetadata.minFrameSize));
  }

  private static final class FlacTimestampSeeker implements TimestampSeeker {

    private final FlacStreamMetadata flacStreamMetadata;
    private final int frameStartMarker;
    private final SampleNumberHolder sampleNumberHolder;

    private FlacTimestampSeeker(FlacStreamMetadata flacStreamMetadata, int frameStartMarker) {
      this.flacStreamMetadata = flacStreamMetadata;
      this.frameStartMarker = frameStartMarker;
      sampleNumberHolder = new SampleNumberHolder();
    }

    @Override
    public TimestampSearchResult searchForTimestamp(ExtractorInput input, long targetSampleNumber)
        throws IOException, InterruptedException {
      long searchPosition = input.getPosition();

      // Find left frame.
      long leftFrameFirstSampleNumber = findNextFrame(input);
      long leftFramePosition = input.getPeekPosition();

      input.advancePeekPosition(
          Math.max(FlacConstants.MIN_FRAME_HEADER_SIZE, flacStreamMetadata.minFrameSize));

      // Find right frame.
      long rightFrameFirstSampleNumber = findNextFrame(input);
      long rightFramePosition = input.getPeekPosition();

      if (leftFrameFirstSampleNumber <= targetSampleNumber
          && rightFrameFirstSampleNumber > targetSampleNumber) {
        return TimestampSearchResult.targetFoundResult(leftFramePosition);
      } else if (rightFrameFirstSampleNumber <= targetSampleNumber) {
        return TimestampSearchResult.underestimatedResult(
            rightFrameFirstSampleNumber, rightFramePosition);
      } else {
        return TimestampSearchResult.overestimatedResult(
            leftFrameFirstSampleNumber, searchPosition);
      }
    }

    /**
     * Searches for the next frame in {@code input}.
     *
     * <p>The peek position is advanced to the start of the found frame, or at the end of the stream
     * if no frame was found.
     *
     * @param input The input from which to search (starting from the peek position).
     * @return The number of the first sample in the found frame, or the total number of samples in
     *     the stream if no frame was found.
     * @throws IOException If peeking from the input fails. In this case, there is no guarantee on
     *     the peek position.
     * @throws InterruptedException If interrupted while peeking from input. In this case, there is
     *     no guarantee on the peek position.
     */
    private long findNextFrame(ExtractorInput input) throws IOException, InterruptedException {
      while (input.getPeekPosition() < input.getLength() - FlacConstants.MIN_FRAME_HEADER_SIZE
          && !FlacFrameReader.checkFrameHeaderFromPeek(
              input, flacStreamMetadata, frameStartMarker, sampleNumberHolder)) {
        input.advancePeekPosition(1);
      }

      if (input.getPeekPosition() >= input.getLength() - FlacConstants.MIN_FRAME_HEADER_SIZE) {
        input.advancePeekPosition((int) (input.getLength() - input.getPeekPosition()));
        return flacStreamMetadata.totalSamples;
      }

      return sampleNumberHolder.sampleNumber;
    }
  }
}
