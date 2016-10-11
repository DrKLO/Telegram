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
package org.telegram.messenger.exoplayer.extractor;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.upstream.Allocator;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;

/**
 * A {@link TrackOutput} that buffers extracted samples in a queue, and allows for consumption from
 * that queue.
 */
public class DefaultTrackOutput implements TrackOutput {

  private final RollingSampleBuffer rollingBuffer;
  private final SampleHolder sampleInfoHolder;

  // Accessed only by the consuming thread.
  private boolean needKeyframe;
  private long lastReadTimeUs;
  private long spliceOutTimeUs;

  // Accessed by both the loading and consuming threads.
  private volatile long largestParsedTimestampUs;
  private volatile MediaFormat format;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public DefaultTrackOutput(Allocator allocator) {
    rollingBuffer = new RollingSampleBuffer(allocator);
    sampleInfoHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Clears the queue, returning all allocations to the allocator.
   */
  public void clear() {
    rollingBuffer.clear();
    needKeyframe = true;
    lastReadTimeUs = Long.MIN_VALUE;
    spliceOutTimeUs = Long.MIN_VALUE;
    largestParsedTimestampUs = Long.MIN_VALUE;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return rollingBuffer.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the queue.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    rollingBuffer.discardUpstreamSamples(discardFromIndex);
    largestParsedTimestampUs = rollingBuffer.peekSample(sampleInfoHolder) ? sampleInfoHolder.timeUs
        : Long.MIN_VALUE;
  }

  // Called by the consuming thread.

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return rollingBuffer.getReadIndex();
  }

  /**
   * True if the output has received a format. False otherwise.
   */
  public boolean hasFormat() {
    return format != null;
  }

  /**
   * The format most recently received by the output, or null if a format has yet to be received.
   */
  public MediaFormat getFormat() {
    return format;
  }

  /**
   * The largest timestamp of any sample received by the output, or {@link Long#MIN_VALUE} if a
   * sample has yet to be received.
   */
  public long getLargestParsedTimestampUs() {
    return largestParsedTimestampUs;
  }

  /**
   * True if at least one sample can be read from the queue. False otherwise.
   */
  public boolean isEmpty() {
    return !advanceToEligibleSample();
  }

  /**
   * Removes the next sample from the head of the queue, writing it into the provided holder.
   * <p>
   * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
   * queued prior to the first keyframe are discarded.
   *
   * @param holder A {@link SampleHolder} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public boolean getSample(SampleHolder holder) {
    boolean foundEligibleSample = advanceToEligibleSample();
    if (!foundEligibleSample) {
      return false;
    }
    // Write the sample into the holder.
    rollingBuffer.readSample(holder);
    needKeyframe = false;
    lastReadTimeUs = holder.timeUs;
    return true;
  }

  /**
   * Discards samples from the queue up to the specified time.
   *
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public void discardUntil(long timeUs) {
    while (rollingBuffer.peekSample(sampleInfoHolder) && sampleInfoHolder.timeUs < timeUs) {
      rollingBuffer.skipSample();
      // We're discarding one or more samples. A subsequent read will need to start at a keyframe.
      needKeyframe = true;
    }
    lastReadTimeUs = Long.MIN_VALUE;
  }

  /**
   * Attempts to skip to the keyframe before the specified time, if it's present in the buffer.
   *
   * @param timeUs The seek time.
   * @return True if the skip was successful. False otherwise.
   */
  public boolean skipToKeyframeBefore(long timeUs) {
    return rollingBuffer.skipToKeyframeBefore(timeUs);
  }

  /**
   * Attempts to configure a splice from this queue to the next.
   *
   * @param nextQueue The queue being spliced to.
   * @return Whether the splice was configured successfully.
   */
  public boolean configureSpliceTo(DefaultTrackOutput nextQueue) {
    if (spliceOutTimeUs != Long.MIN_VALUE) {
      // We've already configured the splice.
      return true;
    }
    long firstPossibleSpliceTime;
    if (rollingBuffer.peekSample(sampleInfoHolder)) {
      firstPossibleSpliceTime = sampleInfoHolder.timeUs;
    } else {
      firstPossibleSpliceTime = lastReadTimeUs + 1;
    }
    RollingSampleBuffer nextRollingBuffer = nextQueue.rollingBuffer;
    while (nextRollingBuffer.peekSample(sampleInfoHolder)
        && (sampleInfoHolder.timeUs < firstPossibleSpliceTime || !sampleInfoHolder.isSyncFrame())) {
      // Discard samples from the next queue for as long as they are before the earliest possible
      // splice time, or not keyframes.
      nextRollingBuffer.skipSample();
    }
    if (nextRollingBuffer.peekSample(sampleInfoHolder)) {
      // We've found a keyframe in the next queue that can serve as the splice point. Set the
      // splice point now.
      spliceOutTimeUs = sampleInfoHolder.timeUs;
      return true;
    }
    return false;
  }

  /**
   * Advances the underlying buffer to the next sample that is eligible to be returned.
   *
   * @return True if an eligible sample was found. False otherwise, in which case the underlying
   *     buffer has been emptied.
   */
  private boolean advanceToEligibleSample() {
    boolean haveNext = rollingBuffer.peekSample(sampleInfoHolder);
    if (needKeyframe) {
      while (haveNext && !sampleInfoHolder.isSyncFrame()) {
        rollingBuffer.skipSample();
        haveNext = rollingBuffer.peekSample(sampleInfoHolder);
      }
    }
    if (!haveNext) {
      return false;
    }
    if (spliceOutTimeUs != Long.MIN_VALUE && sampleInfoHolder.timeUs >= spliceOutTimeUs) {
      return false;
    }
    return true;
  }

  // Called by the loading thread.

  /**
   * Invoked to write sample data to the output.
   *
   * @param dataSource A {@link DataSource} from which to read the sample data.
   * @param length The maximum length to read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @return The number of bytes appended.
   * @throws IOException If an error occurred reading from the input.
   */
  public int sampleData(DataSource dataSource, int length, boolean allowEndOfInput)
      throws IOException {
    return rollingBuffer.appendData(dataSource, length, allowEndOfInput);
  }

  // TrackOutput implementation. Called by the loading thread.

  @Override
  public void format(MediaFormat format) {
    this.format = format;
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return rollingBuffer.appendData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray buffer, int length) {
    rollingBuffer.appendData(buffer, length);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    largestParsedTimestampUs = Math.max(largestParsedTimestampUs, timeUs);
    rollingBuffer.commitSample(timeUs, flags, rollingBuffer.getWritePosition() - size - offset,
        size, encryptionKey);
  }

}
