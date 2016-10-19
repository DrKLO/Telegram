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
package org.telegram.messenger.exoplayer;

import org.telegram.messenger.exoplayer.MediaCodecUtil.DecoderQueryException;
import org.telegram.messenger.exoplayer.SampleSource.SampleSourceReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Base class for {@link TrackRenderer} implementations that render samples obtained from a
 * {@link SampleSource}.
 */
public abstract class SampleSourceTrackRenderer extends TrackRenderer {

  private final SampleSourceReader[] sources;

  private int[] handledSourceIndices;
  private int[] handledSourceTrackIndices;

  private SampleSourceReader enabledSource;
  private int enabledSourceTrackIndex;

  private long durationUs;

  /**
   * @param sources One or more upstream sources from which the renderer can obtain samples.
   */
  public SampleSourceTrackRenderer(SampleSource... sources) {
    this.sources = new SampleSourceReader[sources.length];
    for (int i = 0; i < sources.length; i++) {
      this.sources[i] = sources[i].register();
    }
  }

  @Override
  protected final boolean doPrepare(long positionUs) throws ExoPlaybackException {
    boolean allSourcesPrepared = true;
    for (int i = 0; i < sources.length; i++) {
      allSourcesPrepared &= sources[i].prepare(positionUs);
    }
    if (!allSourcesPrepared) {
      return false;
    }
    // The sources are all prepared.
    int totalSourceTrackCount = 0;
    for (int i = 0; i < sources.length; i++) {
      totalSourceTrackCount += sources[i].getTrackCount();
    }
    long durationUs = 0;
    int handledTrackCount = 0;
    int[] handledSourceIndices = new int[totalSourceTrackCount];
    int[] handledTrackIndices = new int[totalSourceTrackCount];
    int sourceCount = sources.length;
    for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
      SampleSourceReader source = sources[sourceIndex];
      int sourceTrackCount = source.getTrackCount();
      for (int trackIndex = 0; trackIndex < sourceTrackCount; trackIndex++) {
        MediaFormat format = source.getFormat(trackIndex);
        boolean handlesTrack;
        try {
          handlesTrack = handlesTrack(format);
        } catch (DecoderQueryException e) {
          throw new ExoPlaybackException(e);
        }
        if (handlesTrack) {
          handledSourceIndices[handledTrackCount] = sourceIndex;
          handledTrackIndices[handledTrackCount] = trackIndex;
          handledTrackCount++;
          if (durationUs == TrackRenderer.UNKNOWN_TIME_US) {
            // We've already encountered a track for which the duration is unknown, so the media
            // duration is unknown regardless of the duration of this track.
          } else {
            long trackDurationUs = format.durationUs;
            if (trackDurationUs == TrackRenderer.UNKNOWN_TIME_US) {
              durationUs = TrackRenderer.UNKNOWN_TIME_US;
            } else if (trackDurationUs == TrackRenderer.MATCH_LONGEST_US) {
              // Do nothing.
            } else {
              durationUs = Math.max(durationUs, trackDurationUs);
            }
          }
        }
      }
    }
    this.durationUs = durationUs;
    this.handledSourceIndices = Arrays.copyOf(handledSourceIndices, handledTrackCount);
    this.handledSourceTrackIndices = Arrays.copyOf(handledTrackIndices, handledTrackCount);
    return true;
  }

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    positionUs = shiftInputPosition(positionUs);
    enabledSource = sources[handledSourceIndices[track]];
    enabledSourceTrackIndex = handledSourceTrackIndices[track];
    enabledSource.enable(enabledSourceTrackIndex, positionUs);
    onDiscontinuity(positionUs);
  }

  @Override
  protected final void seekTo(long positionUs) throws ExoPlaybackException {
    positionUs = shiftInputPosition(positionUs);
    enabledSource.seekToUs(positionUs);
    checkForDiscontinuity(positionUs);
  }

  @Override
  protected final void doSomeWork(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException {
    positionUs = shiftInputPosition(positionUs);
    boolean sourceIsReady = enabledSource.continueBuffering(enabledSourceTrackIndex, positionUs);
    positionUs = checkForDiscontinuity(positionUs);
    doSomeWork(positionUs, elapsedRealtimeUs, sourceIsReady);
  }

  @Override
  protected long getBufferedPositionUs() {
    return enabledSource.getBufferedPositionUs();
  }

  @Override
  protected long getDurationUs() {
    return durationUs;
  }

  @Override
  protected void maybeThrowError() throws ExoPlaybackException {
    if (enabledSource != null) {
      maybeThrowError(enabledSource);
    } else {
      int sourceCount = sources.length;
      for (int i = 0; i < sourceCount; i++) {
        maybeThrowError(sources[i]);
      }
    }
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    enabledSource.disable(enabledSourceTrackIndex);
    enabledSource = null;
  }

  @Override
  protected void onReleased() throws ExoPlaybackException {
    int sourceCount = sources.length;
    for (int i = 0; i < sourceCount; i++) {
      sources[i].release();
    }
  }

  @Override
  protected final int getTrackCount() {
    return handledSourceTrackIndices.length;
  }

  @Override
  protected final MediaFormat getFormat(int track) {
    SampleSourceReader source = sources[handledSourceIndices[track]];
    return source.getFormat(handledSourceTrackIndices[track]);
  }

  /**
   * Shifts positions passed to {@link #onEnabled(int, long, boolean)}, {@link #seekTo(long)} and
   * {@link #doSomeWork(long, long)}.
   * <p>
   * The default implementation does not modify the position. Except in very specific cases,
   * subclasses should not override this method.
   *
   * @param positionUs The position in microseconds.
   * @return The adjusted position in microseconds.
   */
  protected long shiftInputPosition(long positionUs) {
    return positionUs;
  }

  // Methods to be called by subclasses.

  /**
   * Reads from the enabled upstream source.
   *
   * @param positionUs The current playback position.
   * @param formatHolder A {@link MediaFormatHolder} object to populate in the case of a new format.
   * @param sampleHolder A {@link SampleHolder} object to populate in the case of a new sample.
   *     If the caller requires the sample data then it must ensure that {@link SampleHolder#data}
   *     references a valid output buffer.
   * @return The result, which can be {@link SampleSource#SAMPLE_READ},
   *     {@link SampleSource#FORMAT_READ}, {@link SampleSource#NOTHING_READ} or
   *     {@link SampleSource#END_OF_STREAM}.
   */
  protected final int readSource(long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder) {
    return enabledSource.readData(enabledSourceTrackIndex, positionUs, formatHolder, sampleHolder);
  }

  // Abstract methods.

  /**
   * Returns whether this renderer is capable of handling the provided track.
   *
   * @param mediaFormat The format of the track.
   * @return True if the renderer can handle the track, false otherwise.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  protected abstract boolean handlesTrack(MediaFormat mediaFormat) throws DecoderQueryException;

  /**
   * Invoked when a discontinuity is encountered. Also invoked when the renderer is enabled, for
   * convenience.
   *
   * @param positionUs The playback position after the discontinuity, or the position at which
   *     the renderer is being enabled.
   * @throws ExoPlaybackException If an error occurs handling the discontinuity.
   */
  protected abstract void onDiscontinuity(long positionUs) throws ExoPlaybackException;

  /**
   * Called by {@link #doSomeWork(long, long)}.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param sourceIsReady The result of the most recent call to
   *     {@link SampleSourceReader#continueBuffering(int, long)}.
   * @throws ExoPlaybackException If an error occurs.
   * @throws ExoPlaybackException
   */
  protected abstract void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException;

  // Private methods.

  private long checkForDiscontinuity(long positionUs) throws ExoPlaybackException {
    long discontinuityPositionUs = enabledSource.readDiscontinuity(enabledSourceTrackIndex);
    if (discontinuityPositionUs != SampleSource.NO_DISCONTINUITY) {
      onDiscontinuity(discontinuityPositionUs);
      return discontinuityPositionUs;
    }
    return positionUs;
  }

  private void maybeThrowError(SampleSourceReader source) throws ExoPlaybackException {
    try {
      source.maybeThrowError();
    } catch (IOException e) {
      throw new ExoPlaybackException(e);
    }
  }

}
