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
package org.telegram.messenger.exoplayer.hls;

import android.util.SparseArray;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.extractor.DefaultTrackOutput;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.upstream.Allocator;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import java.io.IOException;

/**
 * Wraps a {@link Extractor}, adding functionality to enable reading of the extracted samples.
 */
public final class HlsExtractorWrapper implements ExtractorOutput {

  public final int trigger;
  public final Format format;
  public final long startTimeUs;

  private final Extractor extractor;
  private final SparseArray<DefaultTrackOutput> sampleQueues;
  private final boolean shouldSpliceIn;
  private final int adaptiveMaxWidth;
  private final int adaptiveMaxHeight;

  private MediaFormat[] sampleQueueFormats;
  private Allocator allocator;

  private volatile boolean tracksBuilt;

  // Accessed only by the consuming thread.
  private boolean prepared;
  private boolean spliceConfigured;

  public HlsExtractorWrapper(int trigger, Format format, long startTimeUs, Extractor extractor,
      boolean shouldSpliceIn, int adaptiveMaxWidth, int adaptiveMaxHeight) {
    this.trigger = trigger;
    this.format = format;
    this.startTimeUs = startTimeUs;
    this.extractor = extractor;
    this.shouldSpliceIn = shouldSpliceIn;
    this.adaptiveMaxWidth = adaptiveMaxWidth;
    this.adaptiveMaxHeight = adaptiveMaxHeight;
    sampleQueues = new SparseArray<>();
  }

  /**
   * Initializes the wrapper for use.
   *
   * @param allocator An allocator for obtaining allocations into which extracted data is written.
   */
  public void init(Allocator allocator) {
    this.allocator = allocator;
    extractor.init(this);
  }

  /**
   * Whether the extractor is prepared.
   *
   * @return True if the extractor is prepared. False otherwise.
   */
  public boolean isPrepared() {
    if (!prepared && tracksBuilt) {
      for (int i = 0; i < sampleQueues.size(); i++) {
        if (!sampleQueues.valueAt(i).hasFormat()) {
          return false;
        }
      }
      prepared = true;
      sampleQueueFormats = new MediaFormat[sampleQueues.size()];
      for (int i = 0; i < sampleQueueFormats.length; i++) {
        MediaFormat format = sampleQueues.valueAt(i).getFormat();
        if (MimeTypes.isVideo(format.mimeType) && (adaptiveMaxWidth != MediaFormat.NO_VALUE
            || adaptiveMaxHeight != MediaFormat.NO_VALUE)) {
          format = format.copyWithMaxVideoDimensions(adaptiveMaxWidth, adaptiveMaxHeight);
        }
        sampleQueueFormats[i] = format;
      }
    }
    return prepared;
  }

  /**
   * Clears queues for all tracks, returning all allocations to the allocator.
   */
  public void clear() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).clear();
    }
  }

  /**
   * Gets the largest timestamp of any sample parsed by the extractor.
   *
   * @return The largest timestamp, or {@link Long#MIN_VALUE} if no samples have been parsed.
   */
  public long getLargestParsedTimestampUs() {
    long largestParsedTimestampUs = Long.MIN_VALUE;
    for (int i = 0; i < sampleQueues.size(); i++) {
      largestParsedTimestampUs = Math.max(largestParsedTimestampUs,
          sampleQueues.valueAt(i).getLargestParsedTimestampUs());
    }
    return largestParsedTimestampUs;
  }

  /**
   * Attempts to configure a splice from this extractor to the next.
   * <p>
   * The splice is performed such that for each track the samples read from the next extractor
   * start with a keyframe, and continue from where the samples read from this extractor finish.
   * A successful splice may discard samples from either or both extractors.
   * <p>
   * Splice configuration may fail if the next extractor is not yet in a state that allows the
   * splice to be performed. Calling this method is a noop if the splice has already been
   * configured. Hence this method should be called repeatedly during the window within which a
   * splice can be performed.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param nextExtractor The extractor being spliced to.
   */
  public final void configureSpliceTo(HlsExtractorWrapper nextExtractor) {
    Assertions.checkState(isPrepared());
    if (spliceConfigured || !nextExtractor.shouldSpliceIn || !nextExtractor.isPrepared()) {
      // The splice is already configured, or the next extractor doesn't want to be spliced in, or
      // the next extractor isn't ready to be spliced in.
      return;
    }
    boolean spliceConfigured = true;
    int trackCount = getTrackCount();
    for (int i = 0; i < trackCount; i++) {
      DefaultTrackOutput currentSampleQueue = sampleQueues.valueAt(i);
      DefaultTrackOutput nextSampleQueue = nextExtractor.sampleQueues.valueAt(i);
      spliceConfigured &= currentSampleQueue.configureSpliceTo(nextSampleQueue);
    }
    this.spliceConfigured = spliceConfigured;
    return;
  }

  /**
   * Gets the number of available tracks.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @return The number of available tracks.
   */
  public int getTrackCount() {
    Assertions.checkState(isPrepared());
    return sampleQueues.size();
  }

  /**
   * Gets the {@link MediaFormat} of the specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track index.
   * @return The corresponding format.
   */
  public MediaFormat getMediaFormat(int track) {
    Assertions.checkState(isPrepared());
    return sampleQueueFormats[track];
  }

  /**
   * Gets the next sample for the specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track from which to read.
   * @param holder A {@link SampleHolder} into which the sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public boolean getSample(int track, SampleHolder holder) {
    Assertions.checkState(isPrepared());
    return sampleQueues.valueAt(track).getSample(holder);
  }

  /**
   * Discards samples for the specified track up to the specified time.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track from which samples should be discarded.
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public void discardUntil(int track, long timeUs) {
    Assertions.checkState(isPrepared());
    sampleQueues.valueAt(track).discardUntil(timeUs);
  }

  /**
   * Whether samples are available for reading from {@link #getSample(int, SampleHolder)} for the
   * specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @return True if samples are available for reading from {@link #getSample(int, SampleHolder)}
   *     for the specified track. False otherwise.
   */
  public boolean hasSamples(int track) {
    Assertions.checkState(isPrepared());
    return !sampleQueues.valueAt(track).isEmpty();
  }

  /**
   * Reads from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return One of {@link Extractor#RESULT_CONTINUE} and {@link Extractor#RESULT_END_OF_INPUT}.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public int read(ExtractorInput input) throws IOException, InterruptedException {
    int result = extractor.read(input, null);
    Assertions.checkState(result != Extractor.RESULT_SEEK);
    return result;
  }

  public long getAdjustedEndTimeUs() {
    long largestAdjustedPtsParsed = Long.MIN_VALUE;
    for (int i = 0; i < sampleQueues.size(); i++) {
      largestAdjustedPtsParsed = Math.max(largestAdjustedPtsParsed,
          sampleQueues.valueAt(i).getLargestParsedTimestampUs());
    }
    return largestAdjustedPtsParsed;
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id) {
    DefaultTrackOutput sampleQueue = new DefaultTrackOutput(allocator);
    sampleQueues.put(id, sampleQueue);
    return sampleQueue;
  }

  @Override
  public void endTracks() {
    this.tracksBuilt = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    // Do nothing.
  }

  @Override
  public void drmInitData(DrmInitData drmInit) {
    // Do nothing.
  }

}
