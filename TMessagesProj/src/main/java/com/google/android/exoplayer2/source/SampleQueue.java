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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.source.SampleStream.FLAG_OMIT_SAMPLE_DATA;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_PEEK;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;

import android.os.Looper;
import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer.InsufficientCapacityException;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionEventListener.EventDispatcher;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager.DrmSessionReference;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.SampleStream.ReadFlags;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** A queue of media samples. */
public class SampleQueue implements TrackOutput {

  /** A listener for changes to the upstream format. */
  public interface UpstreamFormatChangedListener {

    /**
     * Called on the loading thread when an upstream format change occurs.
     *
     * @param format The new upstream format.
     */
    void onUpstreamFormatChanged(Format format);
  }

  @VisibleForTesting /* package */ static final int SAMPLE_CAPACITY_INCREMENT = 1000;
  private static final String TAG = "SampleQueue";

  private final SampleDataQueue sampleDataQueue;
  private final SampleExtrasHolder extrasHolder;
  private final SpannedData<SharedSampleMetadata> sharedSampleMetadata;
  @Nullable private final DrmSessionManager drmSessionManager;
  @Nullable private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  @Nullable private UpstreamFormatChangedListener upstreamFormatChangeListener;

  @Nullable private Format downstreamFormat;
  @Nullable private DrmSession currentDrmSession;

  private int capacity;
  private int[] sourceIds;
  private long[] offsets;
  private int[] sizes;
  private int[] flags;
  private long[] timesUs;
  private @NullableType CryptoData[] cryptoDatas;

  private int length;
  private int absoluteFirstIndex;
  private int relativeFirstIndex;
  private int readPosition;

  private long startTimeUs;
  private long largestDiscardedTimestampUs;
  private long largestQueuedTimestampUs;
  private boolean isLastSampleQueued;
  private boolean upstreamKeyframeRequired;
  private boolean upstreamFormatRequired;
  private boolean upstreamFormatAdjustmentRequired;
  @Nullable private Format unadjustedUpstreamFormat;
  @Nullable private Format upstreamFormat;
  private int upstreamSourceId;
  private boolean upstreamAllSamplesAreSyncSamples;
  private boolean loggedUnexpectedNonSyncSample;

  private long sampleOffsetUs;
  private boolean pendingSplice;

  /**
   * Creates a sample queue without DRM resource management.
   *
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public static SampleQueue createWithoutDrm(Allocator allocator) {
    return new SampleQueue(
        allocator, /* drmSessionManager= */ null, /* drmEventDispatcher= */ null);
  }

  /**
   * Creates a sample queue with DRM resource management.
   *
   * <p>For each sample added to the queue, a {@link DrmSession} will be attached containing the
   * keys needed to decrypt it.
   *
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   * @param drmSessionManager The {@link DrmSessionManager} to obtain {@link DrmSession DrmSessions}
   *     from. The created instance does not take ownership of this {@link DrmSessionManager}.
   * @param drmEventDispatcher A {@link DrmSessionEventListener.EventDispatcher} to notify of events
   *     related to this SampleQueue.
   */
  public static SampleQueue createWithDrm(
      Allocator allocator,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher) {
    return new SampleQueue(
        allocator,
        Assertions.checkNotNull(drmSessionManager),
        Assertions.checkNotNull(drmEventDispatcher));
  }

  /**
   * @deprecated Use {@link #createWithDrm(Allocator, DrmSessionManager, EventDispatcher)} instead.
   *     The {@code playbackLooper} should be configured on the {@link DrmSessionManager} with
   *     {@link DrmSessionManager#setPlayer(Looper, PlayerId)}.
   */
  @Deprecated
  public static SampleQueue createWithDrm(
      Allocator allocator,
      Looper playbackLooper,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher) {
    drmSessionManager.setPlayer(playbackLooper, PlayerId.UNSET);
    return new SampleQueue(
        allocator,
        Assertions.checkNotNull(drmSessionManager),
        Assertions.checkNotNull(drmEventDispatcher));
  }

  protected SampleQueue(
      Allocator allocator,
      @Nullable DrmSessionManager drmSessionManager,
      @Nullable DrmSessionEventListener.EventDispatcher drmEventDispatcher) {
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    sampleDataQueue = new SampleDataQueue(allocator);
    extrasHolder = new SampleExtrasHolder();
    capacity = SAMPLE_CAPACITY_INCREMENT;
    sourceIds = new int[capacity];
    offsets = new long[capacity];
    timesUs = new long[capacity];
    flags = new int[capacity];
    sizes = new int[capacity];
    cryptoDatas = new CryptoData[capacity];
    sharedSampleMetadata =
        new SpannedData<>(/* removeCallback= */ metadata -> metadata.drmSessionReference.release());
    startTimeUs = Long.MIN_VALUE;
    largestDiscardedTimestampUs = Long.MIN_VALUE;
    largestQueuedTimestampUs = Long.MIN_VALUE;
    upstreamFormatRequired = true;
    upstreamKeyframeRequired = true;
  }

  // Called by the consuming thread when there is no loading thread.

  /** Calls {@link #reset(boolean) reset(true)} and releases any resources owned by the queue. */
  @CallSuper
  public void release() {
    reset(/* resetUpstreamFormat= */ true);
    releaseDrmSessionReferences();
  }

  /** Convenience method for {@code reset(false)}. */
  public final void reset() {
    reset(/* resetUpstreamFormat= */ false);
  }

  /**
   * Clears all samples from the queue.
   *
   * @param resetUpstreamFormat Whether the upstream format should be cleared. If set to false,
   *     samples queued after the reset (and before a subsequent call to {@link #format(Format)})
   *     are assumed to have the current upstream format. If set to true, {@link #format(Format)}
   *     must be called after the reset before any more samples can be queued.
   */
  @CallSuper
  public void reset(boolean resetUpstreamFormat) {
    sampleDataQueue.reset();
    length = 0;
    absoluteFirstIndex = 0;
    relativeFirstIndex = 0;
    readPosition = 0;
    upstreamKeyframeRequired = true;
    startTimeUs = Long.MIN_VALUE;
    largestDiscardedTimestampUs = Long.MIN_VALUE;
    largestQueuedTimestampUs = Long.MIN_VALUE;
    isLastSampleQueued = false;
    sharedSampleMetadata.clear();
    if (resetUpstreamFormat) {
      unadjustedUpstreamFormat = null;
      upstreamFormat = null;
      upstreamFormatRequired = true;
    }
  }

  /**
   * Sets the start time for the queue. Samples with earlier timestamps will be discarded or have
   * the {@link C#BUFFER_FLAG_DECODE_ONLY} flag set when read.
   *
   * @param startTimeUs The start time, in microseconds.
   */
  public final void setStartTimeUs(long startTimeUs) {
    this.startTimeUs = startTimeUs;
  }

  /**
   * Sets a source identifier for subsequent samples.
   *
   * @param sourceId The source identifier.
   */
  public final void sourceId(int sourceId) {
    upstreamSourceId = sourceId;
  }

  /** Indicates samples that are subsequently queued should be spliced into those already queued. */
  public final void splice() {
    pendingSplice = true;
  }

  /** Returns the current absolute write index. */
  public final int getWriteIndex() {
    return absoluteFirstIndex + length;
  }

  /**
   * Discards samples from the write side of the queue.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded. Must be in the
   *     range [{@link #getReadIndex()}, {@link #getWriteIndex()}].
   */
  public final void discardUpstreamSamples(int discardFromIndex) {
    sampleDataQueue.discardUpstreamSampleBytes(discardUpstreamSampleMetadata(discardFromIndex));
  }

  /**
   * Discards samples from the write side of the queue.
   *
   * @param timeUs Samples will be discarded from the write end of the queue until a sample with a
   *     timestamp smaller than timeUs is encountered (this sample is not discarded). Must be larger
   *     than {@link #getLargestReadTimestampUs()}.
   */
  public final void discardUpstreamFrom(long timeUs) {
    if (length == 0) {
      return;
    }
    checkArgument(timeUs > getLargestReadTimestampUs());
    int retainCount = countUnreadSamplesBefore(timeUs);
    discardUpstreamSamples(absoluteFirstIndex + retainCount);
  }

  // Called by the consuming thread.

  /** Calls {@link #discardToEnd()} and releases any resources owned by the queue. */
  @CallSuper
  public void preRelease() {
    discardToEnd();
    releaseDrmSessionReferences();
  }

  /**
   * Throws an error that's preventing data from being read. Does nothing if no such error exists.
   *
   * @throws IOException The underlying error.
   */
  @CallSuper
  public void maybeThrowError() throws IOException {
    // TODO: Avoid throwing if the DRM error is not preventing a read operation.
    if (currentDrmSession != null && currentDrmSession.getState() == DrmSession.STATE_ERROR) {
      throw Assertions.checkNotNull(currentDrmSession.getError());
    }
  }

  /** Returns the current absolute start index. */
  public final int getFirstIndex() {
    return absoluteFirstIndex;
  }

  /** Returns the current absolute read index. */
  public final int getReadIndex() {
    return absoluteFirstIndex + readPosition;
  }

  /**
   * Peeks the source id of the next sample to be read, or the current upstream source id if the
   * queue is empty or if the read position is at the end of the queue.
   *
   * @return The source id.
   */
  public final synchronized int peekSourceId() {
    int relativeReadIndex = getRelativeIndex(readPosition);
    return hasNextSample() ? sourceIds[relativeReadIndex] : upstreamSourceId;
  }

  /** Returns the upstream {@link Format} in which samples are being queued. */
  @Nullable
  public final synchronized Format getUpstreamFormat() {
    return upstreamFormatRequired ? null : upstreamFormat;
  }

  /**
   * Returns the largest sample timestamp that has been queued since the last {@link #reset}.
   *
   * <p>Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   *
   * @return The largest sample timestamp that has been queued, or {@link Long#MIN_VALUE} if no
   *     samples have been queued.
   */
  public final synchronized long getLargestQueuedTimestampUs() {
    return largestQueuedTimestampUs;
  }

  /**
   * Returns the largest sample timestamp that has been read since the last {@link #reset}.
   *
   * @return The largest sample timestamp that has been read, or {@link Long#MIN_VALUE} if no
   *     samples have been read.
   */
  public final synchronized long getLargestReadTimestampUs() {
    return max(largestDiscardedTimestampUs, getLargestTimestamp(readPosition));
  }

  /**
   * Returns whether the last sample of the stream has knowingly been queued. A return value of
   * {@code false} means that the last sample had not been queued or that it's unknown whether the
   * last sample has been queued.
   *
   * <p>Samples that were discarded by calling {@link #discardUpstreamSamples(int)} are not
   * considered as having been queued. Samples that were dequeued from the front of the queue are
   * considered as having been queued.
   */
  public final synchronized boolean isLastSampleQueued() {
    return isLastSampleQueued;
  }

  /** Returns the timestamp of the first sample, or {@link Long#MIN_VALUE} if the queue is empty. */
  public final synchronized long getFirstTimestampUs() {
    return length == 0 ? Long.MIN_VALUE : timesUs[relativeFirstIndex];
  }

  /**
   * Returns whether there is data available for reading.
   *
   * <p>Note: If the stream has ended then a buffer with the end of stream flag can always be read
   * from {@link #read}. Hence an ended stream is always ready.
   *
   * @param loadingFinished Whether no more samples will be written to the sample queue. When true,
   *     this method returns true if the sample queue is empty, because an empty sample queue means
   *     the end of stream has been reached. When false, this method returns false if the sample
   *     queue is empty.
   */
  @SuppressWarnings("ReferenceEquality") // See comments in setUpstreamFormat
  @CallSuper
  public synchronized boolean isReady(boolean loadingFinished) {
    if (!hasNextSample()) {
      return loadingFinished
          || isLastSampleQueued
          || (upstreamFormat != null && upstreamFormat != downstreamFormat);
    }
    if (sharedSampleMetadata.get(getReadIndex()).format != downstreamFormat) {
      // A format can be read.
      return true;
    }
    return mayReadSample(getRelativeIndex(readPosition));
  }

  /**
   * Attempts to read from the queue.
   *
   * <p>{@link Format Formats} read from this method may be associated to a {@link DrmSession}
   * through {@link FormatHolder#drmSession}.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the {@link
   *     C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
   * @param readFlags Flags controlling the behavior of this read operation.
   * @param loadingFinished True if an empty queue should be considered the end of the stream.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   * @throws InsufficientCapacityException If the {@code buffer} has insufficient capacity to hold
   *     the data of a sample being read. The buffer {@link DecoderInputBuffer#timeUs timestamp} and
   *     flags are populated if this exception is thrown, but the read position is not advanced.
   */
  @CallSuper
  public int read(
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      @ReadFlags int readFlags,
      boolean loadingFinished) {
    int result =
        peekSampleMetadata(
            formatHolder,
            buffer,
            /* formatRequired= */ (readFlags & FLAG_REQUIRE_FORMAT) != 0,
            loadingFinished,
            extrasHolder);
    if (result == C.RESULT_BUFFER_READ && !buffer.isEndOfStream()) {
      boolean peek = (readFlags & FLAG_PEEK) != 0;
      if ((readFlags & FLAG_OMIT_SAMPLE_DATA) == 0) {
        if (peek) {
          sampleDataQueue.peekToBuffer(buffer, extrasHolder);
        } else {
          sampleDataQueue.readToBuffer(buffer, extrasHolder);
        }
      }
      if (!peek) {
        readPosition++;
      }
    }
    return result;
  }

  /**
   * Attempts to seek the read position to the specified sample index.
   *
   * @param sampleIndex The sample index.
   * @return Whether the seek was successful.
   */
  public final synchronized boolean seekTo(int sampleIndex) {
    rewind();
    if (sampleIndex < absoluteFirstIndex || sampleIndex > absoluteFirstIndex + length) {
      return false;
    }
    startTimeUs = Long.MIN_VALUE;
    readPosition = sampleIndex - absoluteFirstIndex;
    return true;
  }

  /**
   * Attempts to seek the read position to the keyframe before or at the specified time.
   *
   * @param timeUs The time to seek to.
   * @param allowTimeBeyondBuffer Whether the operation can succeed if {@code timeUs} is beyond the
   *     end of the queue, by seeking to the last sample (or keyframe).
   * @return Whether the seek was successful.
   */
  public final synchronized boolean seekTo(long timeUs, boolean allowTimeBeyondBuffer) {
    rewind();
    int relativeReadIndex = getRelativeIndex(readPosition);
    if (!hasNextSample()
        || timeUs < timesUs[relativeReadIndex]
        || (timeUs > largestQueuedTimestampUs && !allowTimeBeyondBuffer)) {
      return false;
    }
    int offset =
        findSampleBefore(relativeReadIndex, length - readPosition, timeUs, /* keyframe= */ true);
    if (offset == -1) {
      return false;
    }
    startTimeUs = timeUs;
    readPosition += offset;
    return true;
  }

  /**
   * Returns the number of samples that need to be {@link #skip(int) skipped} to advance the read
   * position to the keyframe before or at the specified time.
   *
   * @param timeUs The time to advance to.
   * @param allowEndOfQueue Whether the end of the queue is considered a keyframe when {@code
   *     timeUs} is larger than the largest queued timestamp.
   * @return The number of samples that need to be skipped, which may be equal to 0.
   */
  public final synchronized int getSkipCount(long timeUs, boolean allowEndOfQueue) {
    int relativeReadIndex = getRelativeIndex(readPosition);
    if (!hasNextSample() || timeUs < timesUs[relativeReadIndex]) {
      return 0;
    }
    if (timeUs > largestQueuedTimestampUs && allowEndOfQueue) {
      return length - readPosition;
    }
    int offset =
        findSampleBefore(relativeReadIndex, length - readPosition, timeUs, /* keyframe= */ true);
    if (offset == -1) {
      return 0;
    }
    return offset;
  }

  /**
   * Advances the read position by the specified number of samples.
   *
   * @param count The number of samples to advance the read position by. Must be at least 0 and at
   *     most {@link #getWriteIndex()} - {@link #getReadIndex()}.
   */
  public final synchronized void skip(int count) {
    checkArgument(count >= 0 && readPosition + count <= length);
    readPosition += count;
  }

  /**
   * Discards up to but not including the sample immediately before or at the specified time.
   *
   * @param timeUs The time to discard up to.
   * @param toKeyframe If true then discards samples up to the keyframe before or at the specified
   *     time, rather than any sample before or at that time.
   * @param stopAtReadPosition If true then samples are only discarded if they're before the read
   *     position. If false then samples at and beyond the read position may be discarded, in which
   *     case the read position is advanced to the first remaining sample.
   */
  public final void discardTo(long timeUs, boolean toKeyframe, boolean stopAtReadPosition) {
    sampleDataQueue.discardDownstreamTo(
        discardSampleMetadataTo(timeUs, toKeyframe, stopAtReadPosition));
  }

  /** Discards up to but not including the read position. */
  public final void discardToRead() {
    sampleDataQueue.discardDownstreamTo(discardSampleMetadataToRead());
  }

  /** Discards all samples in the queue and advances the read position. */
  public final void discardToEnd() {
    sampleDataQueue.discardDownstreamTo(discardSampleMetadataToEnd());
  }

  // Called by the loading thread.

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples that
   * are subsequently queued.
   *
   * @param sampleOffsetUs The timestamp offset in microseconds.
   */
  public final void setSampleOffsetUs(long sampleOffsetUs) {
    if (this.sampleOffsetUs != sampleOffsetUs) {
      this.sampleOffsetUs = sampleOffsetUs;
      invalidateUpstreamFormatAdjustment();
    }
  }

  /**
   * Sets a listener to be notified of changes to the upstream format.
   *
   * @param listener The listener.
   */
  public final void setUpstreamFormatChangeListener(
      @Nullable UpstreamFormatChangedListener listener) {
    upstreamFormatChangeListener = listener;
  }

  // TrackOutput implementation. Called by the loading thread.

  @Override
  public final void format(Format format) {
    Format adjustedUpstreamFormat = getAdjustedUpstreamFormat(format);
    upstreamFormatAdjustmentRequired = false;
    unadjustedUpstreamFormat = format;
    boolean upstreamFormatChanged = setUpstreamFormat(adjustedUpstreamFormat);
    if (upstreamFormatChangeListener != null && upstreamFormatChanged) {
      upstreamFormatChangeListener.onUpstreamFormatChanged(adjustedUpstreamFormat);
    }
  }

  @Override
  public final int sampleData(
      DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
      throws IOException {
    return sampleDataQueue.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public final void sampleData(
      ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
    sampleDataQueue.sampleData(data, length);
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    if (upstreamFormatAdjustmentRequired) {
      format(Assertions.checkStateNotNull(unadjustedUpstreamFormat));
    }

    boolean isKeyframe = (flags & C.BUFFER_FLAG_KEY_FRAME) != 0;
    if (upstreamKeyframeRequired) {
      if (!isKeyframe) {
        return;
      }
      upstreamKeyframeRequired = false;
    }

    timeUs += sampleOffsetUs;
    if (upstreamAllSamplesAreSyncSamples) {
      if (timeUs < startTimeUs) {
        // If we know that all samples are sync samples, we can discard those that come before the
        // start time on the write side of the queue.
        return;
      }
      if ((flags & C.BUFFER_FLAG_KEY_FRAME) == 0) {
        // The flag should always be set unless the source content has incorrect sample metadata.
        // Log a warning (once per format change, to avoid log spam) and override the flag.
        if (!loggedUnexpectedNonSyncSample) {
          Log.w(TAG, "Overriding unexpected non-sync sample for format: " + upstreamFormat);
          loggedUnexpectedNonSyncSample = true;
        }
        flags |= C.BUFFER_FLAG_KEY_FRAME;
      }
    }
    if (pendingSplice) {
      if (!isKeyframe || !attemptSplice(timeUs)) {
        return;
      }
      pendingSplice = false;
    }

    long absoluteOffset = sampleDataQueue.getTotalBytesWritten() - size - offset;
    commitSample(timeUs, flags, absoluteOffset, size, cryptoData);
  }

  /**
   * Invalidates the last upstream format adjustment. {@link #getAdjustedUpstreamFormat(Format)}
   * will be called to adjust the upstream {@link Format} again before the next sample is queued.
   */
  protected final void invalidateUpstreamFormatAdjustment() {
    upstreamFormatAdjustmentRequired = true;
  }

  /**
   * Adjusts the upstream {@link Format} (i.e., the {@link Format} that was most recently passed to
   * {@link #format(Format)}).
   *
   * <p>The default implementation incorporates the sample offset passed to {@link
   * #setSampleOffsetUs(long)} into {@link Format#subsampleOffsetUs}.
   *
   * @param format The {@link Format} to adjust.
   * @return The adjusted {@link Format}.
   */
  @CallSuper
  protected Format getAdjustedUpstreamFormat(Format format) {
    if (sampleOffsetUs != 0 && format.subsampleOffsetUs != Format.OFFSET_SAMPLE_RELATIVE) {
      format =
          format
              .buildUpon()
              .setSubsampleOffsetUs(format.subsampleOffsetUs + sampleOffsetUs)
              .build();
    }
    return format;
  }

  // Internal methods.

  /** Rewinds the read position to the first sample in the queue. */
  private synchronized void rewind() {
    readPosition = 0;
    sampleDataQueue.rewind();
  }

  @SuppressWarnings("ReferenceEquality") // See comments in setUpstreamFormat
  private synchronized int peekSampleMetadata(
      FormatHolder formatHolder,
      DecoderInputBuffer buffer,
      boolean formatRequired,
      boolean loadingFinished,
      SampleExtrasHolder extrasHolder) {
    buffer.waitingForKeys = false;
    if (!hasNextSample()) {
      if (loadingFinished || isLastSampleQueued) {
        buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      } else if (upstreamFormat != null && (formatRequired || upstreamFormat != downstreamFormat)) {
        onFormatResult(Assertions.checkNotNull(upstreamFormat), formatHolder);
        return C.RESULT_FORMAT_READ;
      } else {
        return C.RESULT_NOTHING_READ;
      }
    }

    Format format = sharedSampleMetadata.get(getReadIndex()).format;
    if (formatRequired || format != downstreamFormat) {
      onFormatResult(format, formatHolder);
      return C.RESULT_FORMAT_READ;
    }

    int relativeReadIndex = getRelativeIndex(readPosition);
    if (!mayReadSample(relativeReadIndex)) {
      buffer.waitingForKeys = true;
      return C.RESULT_NOTHING_READ;
    }

    buffer.setFlags(flags[relativeReadIndex]);
    buffer.timeUs = timesUs[relativeReadIndex];
    if (buffer.timeUs < startTimeUs) {
      buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }
    extrasHolder.size = sizes[relativeReadIndex];
    extrasHolder.offset = offsets[relativeReadIndex];
    extrasHolder.cryptoData = cryptoDatas[relativeReadIndex];

    return C.RESULT_BUFFER_READ;
  }

  private synchronized boolean setUpstreamFormat(Format format) {
    upstreamFormatRequired = false;
    if (Util.areEqual(format, upstreamFormat)) {
      // The format is unchanged. If format and upstreamFormat are different objects, we keep the
      // current upstreamFormat so we can detect format changes on the read side using cheap
      // referential quality.
      return false;
    }

    if (!sharedSampleMetadata.isEmpty()
        && sharedSampleMetadata.getEndValue().format.equals(format)) {
      // The format has changed back to the format of the last committed sample. If they are
      // different objects, we revert back to using upstreamCommittedFormat as the upstreamFormat
      // so we can detect format changes on the read side using cheap referential equality.
      upstreamFormat = sharedSampleMetadata.getEndValue().format;
    } else {
      upstreamFormat = format;
    }
    upstreamAllSamplesAreSyncSamples =
        MimeTypes.allSamplesAreSyncSamples(upstreamFormat.sampleMimeType, upstreamFormat.codecs);
    loggedUnexpectedNonSyncSample = false;
    return true;
  }

  private synchronized long discardSampleMetadataTo(
      long timeUs, boolean toKeyframe, boolean stopAtReadPosition) {
    if (length == 0 || timeUs < timesUs[relativeFirstIndex]) {
      return C.POSITION_UNSET;
    }
    int searchLength = stopAtReadPosition && readPosition != length ? readPosition + 1 : length;
    int discardCount = findSampleBefore(relativeFirstIndex, searchLength, timeUs, toKeyframe);
    if (discardCount == -1) {
      return C.POSITION_UNSET;
    }
    return discardSamples(discardCount);
  }

  public synchronized long discardSampleMetadataToRead() {
    if (readPosition == 0) {
      return C.POSITION_UNSET;
    }
    return discardSamples(readPosition);
  }

  private synchronized long discardSampleMetadataToEnd() {
    if (length == 0) {
      return C.POSITION_UNSET;
    }
    return discardSamples(length);
  }

  private void releaseDrmSessionReferences() {
    if (currentDrmSession != null) {
      currentDrmSession.release(drmEventDispatcher);
      currentDrmSession = null;
      // Clear downstream format to avoid violating the assumption that downstreamFormat.drmInitData
      // != null implies currentSession != null
      downstreamFormat = null;
    }
  }

  private synchronized void commitSample(
      long timeUs,
      @C.BufferFlags int sampleFlags,
      long offset,
      int size,
      @Nullable CryptoData cryptoData) {
    if (length > 0) {
      // Ensure sample data doesn't overlap.
      int previousSampleRelativeIndex = getRelativeIndex(length - 1);
      checkArgument(
          offsets[previousSampleRelativeIndex] + sizes[previousSampleRelativeIndex] <= offset);
    }

    isLastSampleQueued = (sampleFlags & C.BUFFER_FLAG_LAST_SAMPLE) != 0;
    largestQueuedTimestampUs = max(largestQueuedTimestampUs, timeUs);

    int relativeEndIndex = getRelativeIndex(length);
    timesUs[relativeEndIndex] = timeUs;
    offsets[relativeEndIndex] = offset;
    sizes[relativeEndIndex] = size;
    flags[relativeEndIndex] = sampleFlags;
    cryptoDatas[relativeEndIndex] = cryptoData;
    sourceIds[relativeEndIndex] = upstreamSourceId;

    if (sharedSampleMetadata.isEmpty()
        || !sharedSampleMetadata.getEndValue().format.equals(upstreamFormat)) {
      DrmSessionReference drmSessionReference =
          drmSessionManager != null
              ? drmSessionManager.preacquireSession(drmEventDispatcher, upstreamFormat)
              : DrmSessionReference.EMPTY;

      sharedSampleMetadata.appendSpan(
          getWriteIndex(),
          new SharedSampleMetadata(checkNotNull(upstreamFormat), drmSessionReference));
    }

    length++;
    if (length == capacity) {
      // Increase the capacity.
      int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
      int[] newSourceIds = new int[newCapacity];
      long[] newOffsets = new long[newCapacity];
      long[] newTimesUs = new long[newCapacity];
      int[] newFlags = new int[newCapacity];
      int[] newSizes = new int[newCapacity];
      CryptoData[] newCryptoDatas = new CryptoData[newCapacity];
      int beforeWrap = capacity - relativeFirstIndex;
      System.arraycopy(offsets, relativeFirstIndex, newOffsets, 0, beforeWrap);
      System.arraycopy(timesUs, relativeFirstIndex, newTimesUs, 0, beforeWrap);
      System.arraycopy(flags, relativeFirstIndex, newFlags, 0, beforeWrap);
      System.arraycopy(sizes, relativeFirstIndex, newSizes, 0, beforeWrap);
      System.arraycopy(cryptoDatas, relativeFirstIndex, newCryptoDatas, 0, beforeWrap);
      System.arraycopy(sourceIds, relativeFirstIndex, newSourceIds, 0, beforeWrap);
      int afterWrap = relativeFirstIndex;
      System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
      System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
      System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
      System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
      System.arraycopy(cryptoDatas, 0, newCryptoDatas, beforeWrap, afterWrap);
      System.arraycopy(sourceIds, 0, newSourceIds, beforeWrap, afterWrap);
      offsets = newOffsets;
      timesUs = newTimesUs;
      flags = newFlags;
      sizes = newSizes;
      cryptoDatas = newCryptoDatas;
      sourceIds = newSourceIds;
      relativeFirstIndex = 0;
      capacity = newCapacity;
    }
  }

  /**
   * Attempts to discard samples from the end of the queue to allow samples starting from the
   * specified timestamp to be spliced in. Samples will not be discarded prior to the read position.
   *
   * @param timeUs The timestamp at which the splice occurs.
   * @return Whether the splice was successful.
   */
  private synchronized boolean attemptSplice(long timeUs) {
    if (length == 0) {
      return timeUs > largestDiscardedTimestampUs;
    }
    if (getLargestReadTimestampUs() >= timeUs) {
      return false;
    }
    int retainCount = countUnreadSamplesBefore(timeUs);
    discardUpstreamSampleMetadata(absoluteFirstIndex + retainCount);
    return true;
  }

  private long discardUpstreamSampleMetadata(int discardFromIndex) {
    int discardCount = getWriteIndex() - discardFromIndex;
    checkArgument(0 <= discardCount && discardCount <= (length - readPosition));
    length -= discardCount;
    largestQueuedTimestampUs = max(largestDiscardedTimestampUs, getLargestTimestamp(length));
    isLastSampleQueued = discardCount == 0 && isLastSampleQueued;
    sharedSampleMetadata.discardFrom(discardFromIndex);
    if (length != 0) {
      int relativeLastWriteIndex = getRelativeIndex(length - 1);
      return offsets[relativeLastWriteIndex] + sizes[relativeLastWriteIndex];
    }
    return 0;
  }

  private boolean hasNextSample() {
    return readPosition != length;
  }

  /**
   * Sets the downstream format, performs DRM resource management, and populates the {@code
   * outputFormatHolder}.
   *
   * @param newFormat The new downstream format.
   * @param outputFormatHolder The output {@link FormatHolder}.
   */
  private void onFormatResult(Format newFormat, FormatHolder outputFormatHolder) {
    boolean isFirstFormat = downstreamFormat == null;
    @Nullable DrmInitData oldDrmInitData = isFirstFormat ? null : downstreamFormat.drmInitData;
    downstreamFormat = newFormat;
    @Nullable DrmInitData newDrmInitData = newFormat.drmInitData;

    outputFormatHolder.format =
        drmSessionManager != null
            ? newFormat.copyWithCryptoType(drmSessionManager.getCryptoType(newFormat))
            : newFormat;
    outputFormatHolder.drmSession = currentDrmSession;
    if (drmSessionManager == null) {
      // This sample queue is not expected to handle DRM. Nothing to do.
      return;
    }
    if (!isFirstFormat && Util.areEqual(oldDrmInitData, newDrmInitData)) {
      // Nothing to do.
      return;
    }
    // Ensure we acquire the new session before releasing the previous one in case the same session
    // is being used for both DrmInitData.
    @Nullable DrmSession previousSession = currentDrmSession;
    currentDrmSession = drmSessionManager.acquireSession(drmEventDispatcher, newFormat);
    outputFormatHolder.drmSession = currentDrmSession;

    if (previousSession != null) {
      previousSession.release(drmEventDispatcher);
    }
  }

  /**
   * Returns whether it's possible to read the next sample.
   *
   * @param relativeReadIndex The relative read index of the next sample.
   * @return Whether it's possible to read the next sample.
   */
  private boolean mayReadSample(int relativeReadIndex) {
    return currentDrmSession == null
        || currentDrmSession.getState() == DrmSession.STATE_OPENED_WITH_KEYS
        || ((flags[relativeReadIndex] & C.BUFFER_FLAG_ENCRYPTED) == 0
            && currentDrmSession.playClearSamplesWithoutKeys());
  }

  /**
   * Finds the sample in the specified range that's before or at the specified time. If {@code
   * keyframe} is {@code true} then the sample is additionally required to be a keyframe.
   *
   * @param relativeStartIndex The relative index from which to start searching.
   * @param length The length of the range being searched.
   * @param timeUs The specified time.
   * @param keyframe Whether only keyframes should be considered.
   * @return The offset from {@code relativeFirstIndex} to the found sample, or -1 if no matching
   *     sample was found.
   */
  private int findSampleBefore(int relativeStartIndex, int length, long timeUs, boolean keyframe) {
    // This could be optimized to use a binary search, however in practice callers to this method
    // normally pass times near to the start of the search region. Hence it's unclear whether
    // switching to a binary search would yield any real benefit.
    int sampleCountToTarget = -1;
    int searchIndex = relativeStartIndex;
    for (int i = 0; i < length && timesUs[searchIndex] <= timeUs; i++) {
      if (!keyframe || (flags[searchIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        // We've found a suitable sample.
        sampleCountToTarget = i;
        if (timesUs[searchIndex] == timeUs) {
          // Stop the search if we found a sample at the specified time to avoid returning a later
          // sample with the same exactly matching timestamp.
          break;
        }
      }
      searchIndex++;
      if (searchIndex == capacity) {
        searchIndex = 0;
      }
    }
    return sampleCountToTarget;
  }

  /**
   * Counts the number of samples that haven't been read that have a timestamp smaller than {@code
   * timeUs}.
   *
   * @param timeUs The specified time.
   * @return The number of unread samples with a timestamp smaller than {@code timeUs}.
   */
  private int countUnreadSamplesBefore(long timeUs) {
    int count = length;
    int relativeSampleIndex = getRelativeIndex(length - 1);
    while (count > readPosition && timesUs[relativeSampleIndex] >= timeUs) {
      count--;
      relativeSampleIndex--;
      if (relativeSampleIndex == -1) {
        relativeSampleIndex = capacity - 1;
      }
    }
    return count;
  }

  /**
   * Discards the specified number of samples.
   *
   * @param discardCount The number of samples to discard.
   * @return The corresponding offset up to which data should be discarded.
   */
  @GuardedBy("this")
  private long discardSamples(int discardCount) {
    largestDiscardedTimestampUs =
        max(largestDiscardedTimestampUs, getLargestTimestamp(discardCount));
    length -= discardCount;
    absoluteFirstIndex += discardCount;
    relativeFirstIndex += discardCount;
    if (relativeFirstIndex >= capacity) {
      relativeFirstIndex -= capacity;
    }
    readPosition -= discardCount;
    if (readPosition < 0) {
      readPosition = 0;
    }
    sharedSampleMetadata.discardTo(absoluteFirstIndex);

    if (length == 0) {
      int relativeLastDiscardIndex = (relativeFirstIndex == 0 ? capacity : relativeFirstIndex) - 1;
      return offsets[relativeLastDiscardIndex] + sizes[relativeLastDiscardIndex];
    } else {
      return offsets[relativeFirstIndex];
    }
  }

  /**
   * Finds the largest timestamp of any sample from the start of the queue up to the specified
   * length, assuming that the timestamps prior to a keyframe are always less than the timestamp of
   * the keyframe itself, and of subsequent frames.
   *
   * @param length The length of the range being searched.
   * @return The largest timestamp, or {@link Long#MIN_VALUE} if {@code length == 0}.
   */
  private long getLargestTimestamp(int length) {
    if (length == 0) {
      return Long.MIN_VALUE;
    }
    long largestTimestampUs = Long.MIN_VALUE;
    int relativeSampleIndex = getRelativeIndex(length - 1);
    for (int i = 0; i < length; i++) {
      largestTimestampUs = max(largestTimestampUs, timesUs[relativeSampleIndex]);
      if ((flags[relativeSampleIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
        break;
      }
      relativeSampleIndex--;
      if (relativeSampleIndex == -1) {
        relativeSampleIndex = capacity - 1;
      }
    }
    return largestTimestampUs;
  }

  /**
   * Returns the relative index for a given offset from the start of the queue.
   *
   * @param offset The offset, which must be in the range [0, length].
   */
  private int getRelativeIndex(int offset) {
    int relativeIndex = relativeFirstIndex + offset;
    return relativeIndex < capacity ? relativeIndex : relativeIndex - capacity;
  }

  /** A holder for sample metadata not held by {@link DecoderInputBuffer}. */
  /* package */ static final class SampleExtrasHolder {

    public int size;
    public long offset;
    @Nullable public CryptoData cryptoData;
  }

  /** A holder for metadata that applies to a span of contiguous samples. */
  private static final class SharedSampleMetadata {
    public final Format format;
    public final DrmSessionReference drmSessionReference;

    private SharedSampleMetadata(Format format, DrmSessionReference drmSessionReference) {
      this.format = format;
      this.drmSessionReference = drmSessionReference;
    }
  }
}
