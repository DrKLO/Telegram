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
package org.telegram.messenger.exoplayer2;

import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MediaClock;
import java.io.IOException;

/**
 * An abstract base class suitable for most {@link Renderer} implementations.
 */
public abstract class BaseRenderer implements Renderer, RendererCapabilities {

  private final int trackType;

  private int index;
  private int state;
  private SampleStream stream;
  private long streamOffsetUs;
  private boolean readEndOfStream;
  private boolean streamIsFinal;

  /**
   * @param trackType The track type that the renderer handles. One of the {@link C}
   * {@code TRACK_TYPE_*} constants.
   */
  public BaseRenderer(int trackType) {
    this.trackType = trackType;
    readEndOfStream = true;
  }

  @Override
  public final int getTrackType() {
    return trackType;
  }

  @Override
  public final RendererCapabilities getCapabilities() {
    return this;
  }

  @Override
  public final void setIndex(int index) {
    this.index = index;
  }

  @Override
  public MediaClock getMediaClock() {
    return null;
  }

  @Override
  public final int getState() {
    return state;
  }

  @Override
  public final void enable(Format[] formats, SampleStream stream, long positionUs,
      boolean joining, long offsetUs) throws ExoPlaybackException {
    Assertions.checkState(state == STATE_DISABLED);
    state = STATE_ENABLED;
    onEnabled(joining);
    replaceStream(formats, stream, offsetUs);
    onPositionReset(positionUs, joining);
  }

  @Override
  public final void start() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_STARTED;
    onStarted();
  }

  @Override
  public final void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
      throws ExoPlaybackException {
    Assertions.checkState(!streamIsFinal);
    this.stream = stream;
    readEndOfStream = false;
    streamOffsetUs = offsetUs;
    onStreamChanged(formats);
  }

  @Override
  public final SampleStream getStream() {
    return stream;
  }

  @Override
  public final boolean hasReadStreamToEnd() {
    return readEndOfStream;
  }

  @Override
  public final void setCurrentStreamIsFinal() {
    streamIsFinal = true;
  }

  @Override
  public final void maybeThrowStreamError() throws IOException {
    stream.maybeThrowError();
  }

  @Override
  public final void resetPosition(long positionUs) throws ExoPlaybackException {
    streamIsFinal = false;
    onPositionReset(positionUs, false);
  }

  @Override
  public final void stop() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_STARTED);
    state = STATE_ENABLED;
    onStopped();
  }

  @Override
  public final void disable() {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_DISABLED;
    onDisabled();
    stream = null;
    streamIsFinal = false;
  }

  // RendererCapabilities implementation.

  @Override
  public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SUPPORTED;
  }

  // ExoPlayerComponent implementation.

  @Override
  public void handleMessage(int what, Object object) throws ExoPlaybackException {
    // Do nothing.
  }

  // Methods to be overridden by subclasses.

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer's stream has changed. This occurs when the renderer is enabled after
   * {@link #onEnabled(boolean)} has been called, and also when the stream has been replaced whilst
   * the renderer is enabled or started.
   * <p>
   * The default implementation is a no-op.
   *
   * @param formats The enabled formats.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the position is reset. This occurs when the renderer is enabled after
   * {@link #onStreamChanged(Format[])} has been called, and also when a position discontinuity
   * is encountered.
   * <p>
   * After a position reset, the renderer's {@link SampleStream} is guaranteed to provide samples
   * starting from a key frame.
   * <p>
   * The default implementation is a no-op.
   *
   * @param positionUs The new playback position in microseconds.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onPositionReset(long positionUs, boolean joining)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is started.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStarted() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is stopped.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onStopped() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   */
  protected void onDisabled() {
    // Do nothing.
  }

  // Methods to be called by subclasses.

  /**
   * Returns the index of the renderer within the player.
   *
   * @return The index of the renderer within the player.
   */
  protected final int getIndex() {
    return index;
  }

  /**
   * Reads from the enabled upstream source. If the upstream source has been read to the end then
   * {@link C#RESULT_BUFFER_READ} is only returned if {@link #setCurrentStreamIsFinal()} has been
   * called. {@link C#RESULT_NOTHING_READ} is returned otherwise.
   *
   * @see SampleStream#readData(FormatHolder, DecoderInputBuffer)
   * @param formatHolder A {@link FormatHolder} to populate in the case of reading a format.
   * @param buffer A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
   *     end of the stream. If the end of the stream has been reached, the
   *     {@link C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
   * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
   *     {@link C#RESULT_BUFFER_READ}.
   */
  protected final int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer) {
    int result = stream.readData(formatHolder, buffer);
    if (result == C.RESULT_BUFFER_READ) {
      if (buffer.isEndOfStream()) {
        readEndOfStream = true;
        return streamIsFinal ? C.RESULT_BUFFER_READ : C.RESULT_NOTHING_READ;
      }
      buffer.timeUs += streamOffsetUs;
    }
    return result;
  }

  /**
   * Returns whether the upstream source is ready.
   *
   * @return Whether the source is ready.
   */
  protected final boolean isSourceReady() {
    return readEndOfStream ? streamIsFinal : stream.isReady();
  }

  /**
   * Attempts to skip to the keyframe before the specified time.
   *
   * @param timeUs The specified time.
   */
  protected void skipToKeyframeBefore(long timeUs) {
    stream.skipToKeyframeBefore(timeUs);
  }

}
