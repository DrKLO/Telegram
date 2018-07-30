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
package org.telegram.messenger.exoplayer2.ext.flac;

import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.PositionHolder;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.SeekPoint;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.FlacStreamInfo;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link SeekMap} implementation for FLAC stream using binary search.
 *
 * <p>This seeker performs seeking by using binary search within the stream, until it finds the
 * frame that contains the target sample.
 */
/* package */ final class FlacBinarySearchSeeker {

  /**
   * When seeking within the source, if the offset is smaller than or equal to this value, the seek
   * operation will be performed using a skip operation. Otherwise, the source will be reloaded at
   * the new seek position.
   */
  private static final long MAX_SKIP_BYTES = 256 * 1024;

  private final FlacStreamInfo streamInfo;
  private final FlacBinarySearchSeekMap seekMap;
  private final FlacDecoderJni decoderJni;

  private final long firstFramePosition;
  private final long inputLength;
  private final long approxBytesPerFrame;

  private @Nullable SeekOperationParams pendingSeekOperationParams;

  public FlacBinarySearchSeeker(
      FlacStreamInfo streamInfo,
      long firstFramePosition,
      long inputLength,
      FlacDecoderJni decoderJni) {
    this.streamInfo = Assertions.checkNotNull(streamInfo);
    this.decoderJni = Assertions.checkNotNull(decoderJni);
    this.firstFramePosition = firstFramePosition;
    this.inputLength = inputLength;
    this.approxBytesPerFrame = streamInfo.getApproxBytesPerFrame();

    pendingSeekOperationParams = null;
    seekMap =
        new FlacBinarySearchSeekMap(
            streamInfo,
            firstFramePosition,
            inputLength,
            streamInfo.durationUs(),
            approxBytesPerFrame);
  }

  /** Returns the seek map for the wrapped FLAC stream. */
  public SeekMap getSeekMap() {
    return seekMap;
  }

  /** Sets the target time in microseconds within the stream to seek to. */
  public void setSeekTargetUs(long timeUs) {
    if (pendingSeekOperationParams != null && pendingSeekOperationParams.seekTimeUs == timeUs) {
      return;
    }

    pendingSeekOperationParams =
        new SeekOperationParams(
            timeUs,
            streamInfo.getSampleIndex(timeUs),
            /* floorSample= */ 0,
            /* ceilingSample= */ streamInfo.totalSamples,
            /* floorPosition= */ firstFramePosition,
            /* ceilingPosition= */ inputLength,
            approxBytesPerFrame);
  }

  /** Returns whether the last operation set by {@link #setSeekTargetUs(long)} is still pending. */
  public boolean hasPendingSeek() {
    return pendingSeekOperationParams != null;
  }

  /**
   * Continues to handle the pending seek operation. Returns one of the {@code RESULT_} values from
   * {@link Extractor}.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param seekPositionHolder If {@link Extractor#RESULT_SEEK} is returned, this holder is updated
   *     to hold the position of the required seek.
   * @param outputBuffer If {@link Extractor#RESULT_CONTINUE} is returned, this byte buffer maybe
   *     updated to hold the extracted frame that contains the target sample. The caller needs to
   *     check the byte buffer limit to see if an extracted frame is available.
   * @return One of the {@code RESULT_} values defined in {@link Extractor}.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public int handlePendingSeek(
      ExtractorInput input, PositionHolder seekPositionHolder, ByteBuffer outputBuffer)
      throws InterruptedException, IOException {
    outputBuffer.position(0);
    outputBuffer.limit(0);
    while (true) {
      long floorPosition = pendingSeekOperationParams.floorPosition;
      long ceilingPosition = pendingSeekOperationParams.ceilingPosition;
      long searchPosition = pendingSeekOperationParams.nextSearchPosition;

      // streamInfo may not contain minFrameSize, in which case this value will be 0.
      int minFrameSize = Math.max(1, streamInfo.minFrameSize);
      if (floorPosition + minFrameSize >= ceilingPosition) {
        // The seeking range is too small for more than 1 frame, so we can just continue from
        // the floor position.
        pendingSeekOperationParams = null;
        decoderJni.reset(floorPosition);
        return seekToPosition(input, floorPosition, seekPositionHolder);
      }

      if (!skipInputUntilPosition(input, searchPosition)) {
        return seekToPosition(input, searchPosition, seekPositionHolder);
      }

      decoderJni.reset(searchPosition);
      try {
        decoderJni.decodeSampleWithBacktrackPosition(
            outputBuffer, /* retryPosition= */ searchPosition);
      } catch (FlacDecoderJni.FlacFrameDecodeException e) {
        // For some reasons, the extractor can't find a frame mid-stream.
        // Stop the seeking and let it re-try playing at the last search position.
        pendingSeekOperationParams = null;
        throw new IOException("Cannot read frame at position " + searchPosition, e);
      }
      if (outputBuffer.limit() == 0) {
        return Extractor.RESULT_END_OF_INPUT;
      }

      long lastFrameSampleIndex = decoderJni.getLastFrameFirstSampleIndex();
      long nextFrameSampleIndex = decoderJni.getNextFrameFirstSampleIndex();
      long nextFrameSamplePosition = decoderJni.getDecodePosition();

      boolean targetSampleInLastFrame =
          lastFrameSampleIndex <= pendingSeekOperationParams.targetSample
              && nextFrameSampleIndex > pendingSeekOperationParams.targetSample;

      if (targetSampleInLastFrame) {
        pendingSeekOperationParams = null;
        return Extractor.RESULT_CONTINUE;
      }

      if (nextFrameSampleIndex <= pendingSeekOperationParams.targetSample) {
        pendingSeekOperationParams.updateSeekFloor(nextFrameSampleIndex, nextFrameSamplePosition);
      } else {
        pendingSeekOperationParams.updateSeekCeiling(lastFrameSampleIndex, searchPosition);
      }
    }
  }

  private boolean skipInputUntilPosition(ExtractorInput input, long position)
      throws IOException, InterruptedException {
    long bytesToSkip = position - input.getPosition();
    if (bytesToSkip >= 0 && bytesToSkip <= MAX_SKIP_BYTES) {
      input.skipFully((int) bytesToSkip);
      return true;
    }
    return false;
  }

  private int seekToPosition(
      ExtractorInput input, long position, PositionHolder seekPositionHolder) {
    if (position == input.getPosition()) {
      return Extractor.RESULT_CONTINUE;
    } else {
      seekPositionHolder.position = position;
      return Extractor.RESULT_SEEK;
    }
  }

  /**
   * Contains parameters for a pending seek operation by {@link FlacBinarySearchSeeker}.
   *
   * <p>This class holds parameters for a binary-search for the {@code targetSample} in the range
   * [floorPosition, ceilingPosition).
   */
  private static final class SeekOperationParams {
    private final long seekTimeUs;
    private final long targetSample;
    private final long approxBytesPerFrame;
    private long floorSample;
    private long ceilingSample;
    private long floorPosition;
    private long ceilingPosition;
    private long nextSearchPosition;

    private SeekOperationParams(
        long seekTimeUs,
        long targetSample,
        long floorSample,
        long ceilingSample,
        long floorPosition,
        long ceilingPosition,
        long approxBytesPerFrame) {
      this.seekTimeUs = seekTimeUs;
      this.floorSample = floorSample;
      this.ceilingSample = ceilingSample;
      this.floorPosition = floorPosition;
      this.ceilingPosition = ceilingPosition;
      this.targetSample = targetSample;
      this.approxBytesPerFrame = approxBytesPerFrame;
      updateNextSearchPosition();
    }

    /** Updates the floor constraints (inclusive) of the seek operation. */
    private void updateSeekFloor(long floorSample, long floorPosition) {
      this.floorSample = floorSample;
      this.floorPosition = floorPosition;
      updateNextSearchPosition();
    }

    /** Updates the ceiling constraints (exclusive) of the seek operation. */
    private void updateSeekCeiling(long ceilingSample, long ceilingPosition) {
      this.ceilingSample = ceilingSample;
      this.ceilingPosition = ceilingPosition;
      updateNextSearchPosition();
    }

    private void updateNextSearchPosition() {
      this.nextSearchPosition =
          getNextSearchPosition(
              targetSample,
              floorSample,
              ceilingSample,
              floorPosition,
              ceilingPosition,
              approxBytesPerFrame);
    }

    /**
     * Returns the next position in FLAC stream to search for target sample, given [floorPosition,
     * ceilingPosition).
     */
    private static long getNextSearchPosition(
        long targetSample,
        long floorSample,
        long ceilingSample,
        long floorPosition,
        long ceilingPosition,
        long approxBytesPerFrame) {
      if (floorPosition + 1 >= ceilingPosition || floorSample + 1 >= ceilingSample) {
        return floorPosition;
      }
      long samplesToSkip = targetSample - floorSample;
      long estimatedBytesPerSample =
          Math.max(1, (ceilingPosition - floorPosition) / (ceilingSample - floorSample));
      // In the stream, the samples are accessed in a group of frame. Given a stream position, the
      // seeker will be able to find the first frame following that position.
      // Hence, if our target sample is in the middle of a frame, and our estimate position is
      // correct, or very near the actual sample position, the seeker will keep accessing the next
      // frame, rather than the frame that contains the target sample.
      // Moreover, it's better to under-estimate rather than over-estimate, because the extractor
      // input can skip forward easily, but cannot rewind easily (it may require a new connection
      // to be made).
      // Therefore, we should reduce the estimated position by some amount, so it will converge to
      // the correct frame earlier.
      long bytesToSkip = samplesToSkip * estimatedBytesPerSample;
      long confidenceInterval = bytesToSkip / 20;

      long estimatedFramePosition = floorPosition + bytesToSkip - (approxBytesPerFrame - 1);
      long estimatedPosition = estimatedFramePosition - confidenceInterval;

      return Util.constrainValue(estimatedPosition, floorPosition, ceilingPosition - 1);
    }
  }

  /**
   * A {@link SeekMap} implementation that returns the estimated byte location from {@link
   * SeekOperationParams#getNextSearchPosition(long, long, long, long, long, long)} for each {@link
   * #getSeekPoints(long)} query.
   */
  private static final class FlacBinarySearchSeekMap implements SeekMap {
    private final FlacStreamInfo streamInfo;
    private final long firstFramePosition;
    private final long inputLength;
    private final long approxBytesPerFrame;
    private final long durationUs;

    private FlacBinarySearchSeekMap(
        FlacStreamInfo streamInfo,
        long firstFramePosition,
        long inputLength,
        long durationUs,
        long approxBytesPerFrame) {
      this.streamInfo = streamInfo;
      this.firstFramePosition = firstFramePosition;
      this.inputLength = inputLength;
      this.approxBytesPerFrame = approxBytesPerFrame;
      this.durationUs = durationUs;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      long nextSearchPosition =
          SeekOperationParams.getNextSearchPosition(
              streamInfo.getSampleIndex(timeUs),
              /* floorSample= */ 0,
              /* ceilingSample= */ streamInfo.totalSamples,
              /* floorPosition= */ firstFramePosition,
              /* ceilingPosition= */ inputLength,
              approxBytesPerFrame);
      return new SeekPoints(new SeekPoint(timeUs, nextSearchPosition));
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }
  }
}
