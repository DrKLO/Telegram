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
package com.google.android.exoplayer2.ext.flac;

import com.google.android.exoplayer2.extractor.BinarySearchSeeker;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link SeekMap} implementation for FLAC stream using binary search.
 *
 * <p>This seeker performs seeking by using binary search within the stream, until it finds the
 * frame that contains the target sample.
 */
/* package */ final class FlacBinarySearchSeeker extends BinarySearchSeeker {

  /**
   * Holds a frame extracted from a stream, together with the time stamp of the frame in
   * microseconds.
   */
  public static final class OutputFrameHolder {

    public final ByteBuffer byteBuffer;
    public long timeUs;

    /** Constructs an instance, wrapping the given byte buffer. */
    public OutputFrameHolder(ByteBuffer outputByteBuffer) {
      this.timeUs = 0;
      this.byteBuffer = outputByteBuffer;
    }
  }

  private final FlacDecoderJni decoderJni;

  /**
   * Creates a {@link FlacBinarySearchSeeker}.
   *
   * @param streamMetadata The stream metadata.
   * @param firstFramePosition The byte offset of the first frame in the stream.
   * @param inputLength The length of the stream in bytes.
   * @param decoderJni The FLAC JNI decoder.
   * @param outputFrameHolder A holder used to retrieve the frame found by a seeking operation.
   */
  public FlacBinarySearchSeeker(
      FlacStreamMetadata streamMetadata,
      long firstFramePosition,
      long inputLength,
      FlacDecoderJni decoderJni,
      OutputFrameHolder outputFrameHolder) {
    super(
        /* seekTimestampConverter= */ streamMetadata::getSampleNumber,
        new FlacTimestampSeeker(decoderJni, outputFrameHolder),
        streamMetadata.getDurationUs(),
        /* floorTimePosition= */ 0,
        /* ceilingTimePosition= */ streamMetadata.totalSamples,
        /* floorBytePosition= */ firstFramePosition,
        /* ceilingBytePosition= */ inputLength,
        /* approxBytesPerFrame= */ streamMetadata.getApproxBytesPerFrame(),
        /* minimumSearchRange= */ Math.max(
            FlacConstants.MIN_FRAME_HEADER_SIZE, streamMetadata.minFrameSize));
    this.decoderJni = Assertions.checkNotNull(decoderJni);
  }

  @Override
  protected void onSeekOperationFinished(boolean foundTargetFrame, long resultPosition) {
    if (!foundTargetFrame) {
      // If we can't find the target frame (sample), we need to reset the decoder jni so that
      // it can continue from the result position.
      decoderJni.reset(resultPosition);
    }
  }

  private static final class FlacTimestampSeeker implements TimestampSeeker {

    private final FlacDecoderJni decoderJni;
    private final OutputFrameHolder outputFrameHolder;

    private FlacTimestampSeeker(FlacDecoderJni decoderJni, OutputFrameHolder outputFrameHolder) {
      this.decoderJni = decoderJni;
      this.outputFrameHolder = outputFrameHolder;
    }

    @Override
    public TimestampSearchResult searchForTimestamp(ExtractorInput input, long targetSampleIndex)
        throws IOException, InterruptedException {
      ByteBuffer outputBuffer = outputFrameHolder.byteBuffer;
      long searchPosition = input.getPosition();
      decoderJni.reset(searchPosition);
      try {
        decoderJni.decodeSampleWithBacktrackPosition(
            outputBuffer, /* retryPosition= */ searchPosition);
      } catch (FlacDecoderJni.FlacFrameDecodeException e) {
        // For some reasons, the extractor can't find a frame mid-stream.
        // Stop the seeking and let it re-try playing at the last search position.
        return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
      }
      if (outputBuffer.limit() == 0) {
        return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
      }

      long lastFrameSampleIndex = decoderJni.getLastFrameFirstSampleIndex();
      long nextFrameSampleIndex = decoderJni.getNextFrameFirstSampleIndex();
      long nextFrameSamplePosition = decoderJni.getDecodePosition();

      boolean targetSampleInLastFrame =
          lastFrameSampleIndex <= targetSampleIndex && nextFrameSampleIndex > targetSampleIndex;

      if (targetSampleInLastFrame) {
        // We are holding the target frame in outputFrameHolder. Set its presentation time now.
        outputFrameHolder.timeUs = decoderJni.getLastFrameTimestamp();
        // The input position is passed even though it does not indicate the frame containing the
        // target sample because the extractor must continue to read from this position.
        return TimestampSearchResult.targetFoundResult(input.getPosition());
      } else if (nextFrameSampleIndex <= targetSampleIndex) {
        return TimestampSearchResult.underestimatedResult(
            nextFrameSampleIndex, nextFrameSamplePosition);
      } else {
        return TimestampSearchResult.overestimatedResult(lastFrameSampleIndex, searchPosition);
      }
    }
  }
}
