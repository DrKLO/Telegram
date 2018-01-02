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

import org.telegram.messenger.exoplayer2.ExoPlayer.ExoPlayerComponent;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.util.MediaClock;
import java.io.IOException;

/**
 * Renders media read from a {@link SampleStream}.
 * <p>
 * Internally, a renderer's lifecycle is managed by the owning {@link ExoPlayer}. The renderer is
 * transitioned through various states as the overall playback state changes. The valid state
 * transitions are shown below, annotated with the methods that are called during each transition.
 * <p align="center">
 *   <img src="doc-files/renderer-states.svg" alt="Renderer state transitions">
 * </p>
 */
public interface Renderer extends ExoPlayerComponent {

  /**
   * The renderer is disabled.
   */
  int STATE_DISABLED = 0;
  /**
   * The renderer is enabled but not started. A renderer in this state is not actively rendering
   * media, but will typically hold resources that it requires for rendering (e.g. media decoders).
   */
  int STATE_ENABLED = 1;
  /**
   * The renderer is started. Calls to {@link #render(long, long)} will cause media to be rendered.
   */
  int STATE_STARTED = 2;

  /**
   * Returns the track type that the {@link Renderer} handles. For example, a video renderer will
   * return {@link C#TRACK_TYPE_VIDEO}, an audio renderer will return {@link C#TRACK_TYPE_AUDIO}, a
   * text renderer will return {@link C#TRACK_TYPE_TEXT}, and so on.
   *
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
  int getTrackType();

  /**
   * Returns the capabilities of the renderer.
   *
   * @return The capabilities of the renderer.
   */
  RendererCapabilities getCapabilities();

  /**
   * Sets the index of this renderer within the player.
   *
   * @param index The renderer index.
   */
  void setIndex(int index);

  /**
   * If the renderer advances its own playback position then this method returns a corresponding
   * {@link MediaClock}. If provided, the player will use the returned {@link MediaClock} as its
   * source of time during playback. A player may have at most one renderer that returns a
   * {@link MediaClock} from this method.
   *
   * @return The {@link MediaClock} tracking the playback position of the renderer, or null.
   */
  MediaClock getMediaClock();

  /**
   * Returns the current state of the renderer.
   *
   * @return The current state (one of the {@code STATE_*} constants).
   */
  int getState();

  /**
   * Enables the renderer to consume from the specified {@link SampleStream}.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_DISABLED}.
   *
   * @param configuration The renderer configuration.
   * @param formats The enabled formats.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream}
   *     before they are rendered.
   * @throws ExoPlaybackException If an error occurs.
   */
  void enable(RendererConfiguration configuration, Format[] formats, SampleStream stream,
      long positionUs, boolean joining, long offsetUs) throws ExoPlaybackException;

  /**
   * Starts the renderer, meaning that calls to {@link #render(long, long)} will cause media to be
   * rendered.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  void start() throws ExoPlaybackException;

  /**
   * Replaces the {@link SampleStream} from which samples will be consumed.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param formats The enabled formats.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream} before
   *     they are rendered.
   * @throws ExoPlaybackException If an error occurs.
   */
  void replaceStream(Format[] formats, SampleStream stream, long offsetUs)
      throws ExoPlaybackException;

  /**
   * Returns the {@link SampleStream} being consumed, or null if the renderer is disabled.
   */
  SampleStream getStream();

  /**
   * Returns whether the renderer has read the current {@link SampleStream} to the end.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  boolean hasReadStreamToEnd();

  /**
   * Signals to the renderer that the current {@link SampleStream} will be the final one supplied
   * before it is next disabled or reset.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  void setCurrentStreamFinal();

  /**
   * Returns whether the current {@link SampleStream} will be the final one supplied before the
   * renderer is next disabled or reset.
   */
  boolean isCurrentStreamFinal();

  /**
   * Throws an error that's preventing the renderer from reading from its {@link SampleStream}. Does
   * nothing if no such error exists.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @throws IOException An error that's preventing the renderer from making progress or buffering
   *     more data.
   */
  void maybeThrowStreamError() throws IOException;

  /**
   * Signals to the renderer that a position discontinuity has occurred.
   * <p>
   * After a position discontinuity, the renderer's {@link SampleStream} is guaranteed to provide
   * samples starting from a key frame.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The new playback position in microseconds.
   * @throws ExoPlaybackException If an error occurs handling the reset.
   */
  void resetPosition(long positionUs) throws ExoPlaybackException;

  /**
   * Incrementally renders the {@link SampleStream}.
   * <p>
   * If the renderer is in the {@link #STATE_ENABLED} state then each call to this method will do
   * work toward being ready to render the {@link SampleStream} when the renderer is started. It may
   * also render the very start of the media, for example the first frame of a video stream. If the
   * renderer is in the {@link #STATE_STARTED} state then calls to this method will render the
   * {@link SampleStream} in sync with the specified media positions.
   * <p>
   * This method should return quickly, and should not block if the renderer is unable to make
   * useful progress.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @throws ExoPlaybackException If an error occurs.
   */
  void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException;

  /**
   * Whether the renderer is able to immediately render media from the current position.
   * <p>
   * If the renderer is in the {@link #STATE_STARTED} state then returning true indicates that the
   * renderer has everything that it needs to continue playback. Returning false indicates that
   * the player should pause until the renderer is ready.
   * <p>
   * If the renderer is in the {@link #STATE_ENABLED} state then returning true indicates that the
   * renderer is ready for playback to be started. Returning false indicates that it is not.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return Whether the renderer is ready to render media.
   */
  boolean isReady();

  /**
   * Whether the renderer is ready for the {@link ExoPlayer} instance to transition to
   * {@link ExoPlayer#STATE_ENDED}. The player will make this transition as soon as {@code true} is
   * returned by all of its {@link Renderer}s.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return Whether the renderer is ready for the player to transition to the ended state.
   */
  boolean isEnded();

  /**
   * Stops the renderer, transitioning it to the {@link #STATE_ENABLED} state.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_STARTED}.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  void stop() throws ExoPlaybackException;

  /**
   * Disable the renderer, transitioning it to the {@link #STATE_DISABLED} state.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}.
   */
  void disable();

}
