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

import org.telegram.messenger.exoplayer.ExoPlayer.ExoPlayerComponent;
import org.telegram.messenger.exoplayer.util.Assertions;

/**
 * Renders a single component of media.
 *
 * <p>Internally, a renderer's lifecycle is managed by the owning {@link ExoPlayer}. The player
 * will transition its renderers through various states as the overall playback state changes. The
 * valid state transitions are shown below, annotated with the methods that are invoked during each
 * transition.
 * <p align="center"><img src="doc-files/trackrenderer-states.png"
 *     alt="TrackRenderer state transitions"
 *     border="0"/></p>
 */
public abstract class TrackRenderer implements ExoPlayerComponent {

  /**
   * Represents an unknown time or duration. Equal to {@link C#UNKNOWN_TIME_US}.
   */
  public static final long UNKNOWN_TIME_US = C.UNKNOWN_TIME_US; // -1
  /**
   * Represents a time or duration that should match the duration of the longest track whose
   * duration is known. Equal to {@link C#MATCH_LONGEST_US}.
   */
  public static final long MATCH_LONGEST_US = C.MATCH_LONGEST_US; // -2
  /**
   * Represents the time of the end of the track.
   */
  public static final long END_OF_TRACK_US = -3;

  /**
   * The renderer has been released and should not be used.
   */
  protected static final int STATE_RELEASED = -1;
  /**
   * The renderer has not yet been prepared.
   */
  protected static final int STATE_UNPREPARED = 0;
  /**
   * The renderer has completed necessary preparation. Preparation may include, for example,
   * reading the header of a media file to determine the track format and duration.
   * <p>
   * The renderer should not hold scarce or expensive system resources (e.g. media decoders) and
   * should not be actively buffering media data when in this state.
   */
  protected static final int STATE_PREPARED = 1;
  /**
   * The renderer is enabled. It should either be ready to be started, or be actively working
   * towards this state (e.g. a renderer in this state will typically hold any resources that it
   * requires, such as media decoders, and will have buffered or be buffering any media data that
   * is required to start playback).
   */
  protected static final int STATE_ENABLED = 2;
  /**
   * The renderer is started. Calls to {@link #doSomeWork(long, long)} should cause the media to be
   * rendered.
   */
  protected static final int STATE_STARTED = 3;

  private int state;

  /**
   * If the renderer advances its own playback position then this method returns a corresponding
   * {@link MediaClock}. If provided, the player will use the returned {@link MediaClock} as its
   * source of time during playback. A player may have at most one renderer that returns a
   * {@link MediaClock} from this method.
   *
   * @return The {@link MediaClock} tracking the playback position of the renderer, or null.
   */
  protected MediaClock getMediaClock() {
    return null;
  }

  /**
   * Returns the current state of the renderer.
   *
   * @return The current state (one of the STATE_* constants).
   */
  protected final int getState() {
    return state;
  }

  /**
   * Prepares the renderer. This method is non-blocking, and hence it may be necessary to call it
   * more than once in order to transition the renderer into the prepared state.
   *
   * @param positionUs The player's current playback position.
   * @return The current state (one of the STATE_* constants), for convenience.
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final int prepare(long positionUs) throws ExoPlaybackException {
    Assertions.checkState(state == STATE_UNPREPARED);
    state = doPrepare(positionUs) ? STATE_PREPARED : STATE_UNPREPARED;
    return state;
  }

  /**
   * Invoked to make progress when the renderer is in the {@link #STATE_UNPREPARED} state. This
   * method will be called repeatedly until {@code true} is returned.
   * <p>
   * This method should return quickly, and should not block if the renderer is currently unable to
   * make any useful progress.
   *
   * @param positionUs The player's current playback position.
   * @return True if the renderer is now prepared. False otherwise.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract boolean doPrepare(long positionUs) throws ExoPlaybackException;

  /**
   * Returns the number of tracks exposed by the renderer.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_PREPARED}, {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return The number of tracks.
   */
  protected abstract int getTrackCount();

  /**
   * Returns the format of the specified track.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_PREPARED}, {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @param track The track index.
   * @return The format of the specified track.
   */
  protected abstract MediaFormat getFormat(int track);

  /**
   * Enable the renderer for a specified track.
   *
   * @param track The track for which the renderer is being enabled.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void enable(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    Assertions.checkState(state == STATE_PREPARED);
    state = STATE_ENABLED;
    onEnabled(track, positionUs, joining);
  }

  /**
   * Called when the renderer is enabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @param track The track for which the renderer is being enabled.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Starts the renderer, meaning that calls to {@link #doSomeWork(long, long)} will cause the
   * track to be rendered.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void start() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_STARTED;
    onStarted();
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
   * Stops the renderer.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void stop() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_STARTED);
    state = STATE_ENABLED;
    onStopped();
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
   * Disable the renderer.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void disable() throws ExoPlaybackException {
    Assertions.checkState(state == STATE_ENABLED);
    state = STATE_PREPARED;
    onDisabled();
  }

  /**
   * Called when the renderer is disabled.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onDisabled() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Releases the renderer.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  /* package */ final void release() throws ExoPlaybackException {
    Assertions.checkState(state != STATE_ENABLED
        && state != STATE_STARTED
        && state != STATE_RELEASED);
    state = STATE_RELEASED;
    onReleased();
  }

  /**
   * Called when the renderer is released.
   * <p>
   * The default implementation is a no-op.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  protected void onReleased() throws ExoPlaybackException {
    // Do nothing.
  }

  /**
   * Whether the renderer is ready for the {@link ExoPlayer} instance to transition to
   * {@link ExoPlayer#STATE_ENDED}. The player will make this transition as soon as {@code true} is
   * returned by all of its {@link TrackRenderer}s.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return Whether the renderer is ready for the player to transition to the ended state.
   */
  protected abstract boolean isEnded();

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
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return True if the renderer is ready to render media. False otherwise.
   */
  protected abstract boolean isReady();

  /**
   * Invoked to make progress when the renderer is in the {@link #STATE_ENABLED} or
   * {@link #STATE_STARTED} states.
   * <p>
   * If the renderer's state is {@link #STATE_STARTED}, then repeated calls to this method should
   * cause the media track to be rendered. If the state is {@link #STATE_ENABLED}, then repeated
   * calls should make progress towards getting the renderer into a position where it is ready to
   * render the track.
   * <p>
   * This method should return quickly, and should not block if the renderer is currently unable to
   * make any useful progress.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @param positionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void doSomeWork(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException;

  /**
   * Throws an error that's preventing the renderer from making progress or buffering more data at
   * this point in time.
   *
   * @throws ExoPlaybackException An error that's preventing the renderer from making progress or
   *     buffering more data.
   */
  protected abstract void maybeThrowError() throws ExoPlaybackException;

  /**
   * Returns the duration of the media being rendered.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_PREPARED}, {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return The duration of the track in microseconds, or {@link #MATCH_LONGEST_US} if
   *     the track's duration should match that of the longest track whose duration is known, or
   *     or {@link #UNKNOWN_TIME_US} if the duration is not known.
   */
  protected abstract long getDurationUs();

  /**
   * Returns an estimate of the absolute position in microseconds up to which data is buffered.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}, {@link #STATE_STARTED}
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered,
   *     or {@link #END_OF_TRACK_US} if the track is fully buffered, or {@link #UNKNOWN_TIME_US} if
   *     no estimate is available.
   */
  protected abstract long getBufferedPositionUs();

  /**
   * Seeks to a specified time in the track.
   * <p>
   * This method may be called when the renderer is in the following states:
   * {@link #STATE_ENABLED}
   *
   * @param positionUs The desired playback position in microseconds.
   * @throws ExoPlaybackException If an error occurs.
   */
  protected abstract void seekTo(long positionUs) throws ExoPlaybackException;

  @Override
  public void handleMessage(int what, Object object) throws ExoPlaybackException {
    // Do nothing.
  }

}
