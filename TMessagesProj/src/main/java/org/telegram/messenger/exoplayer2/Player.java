/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A media player interface defining traditional high-level functionality, such as the ability to
 * play, pause, seek and query properties of the currently playing media.
 * <p>
 * Some important properties of media players that implement this interface are:
 * <ul>
 *     <li>They can provide a {@link Timeline} representing the structure of the media being played,
 *     which can be obtained by calling {@link #getCurrentTimeline()}.</li>
 *     <li>They can provide a {@link TrackGroupArray} defining the currently available tracks,
 *     which can be obtained by calling {@link #getCurrentTrackGroups()}.</li>
 *     <li>They contain a number of renderers, each of which is able to render tracks of a single
 *     type (e.g. audio, video or text). The number of renderers and their respective track types
 *     can be obtained by calling {@link #getRendererCount()} and {@link #getRendererType(int)}.
 *     </li>
 *     <li>They can provide a {@link TrackSelectionArray} defining which of the currently available
 *     tracks are selected to be rendered by each renderer. This can be obtained by calling
 *     {@link #getCurrentTrackSelections()}}.</li>
 * </ul>
 */
public interface Player {

  /**
   * Listener of changes in player state.
   */
  interface EventListener {

    /**
     * Called when the timeline and/or manifest has been refreshed.
     * <p>
     * Note that if the timeline has changed then a position discontinuity may also have occurred.
     * For example, the current period index may have changed as a result of periods being added or
     * removed from the timeline. This will <em>not</em> be reported via a separate call to
     * {@link #onPositionDiscontinuity()}.
     *
     * @param timeline The latest timeline. Never null, but may be empty.
     * @param manifest The latest manifest. May be null.
     */
    void onTimelineChanged(Timeline timeline, Object manifest);

    /**
     * Called when the available or selected tracks change.
     *
     * @param trackGroups The available tracks. Never null, but may be of length zero.
     * @param trackSelections The track selections for each renderer. Never null and always of
     *     length {@link #getRendererCount()}, but may contain null elements.
     */
    void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections);

    /**
     * Called when the player starts or stops loading the source.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    void onLoadingChanged(boolean isLoading);

    /**
     * Called when the value returned from either {@link #getPlayWhenReady()} or
     * {@link #getPlaybackState()} changes.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param playbackState One of the {@code STATE} constants.
     */
    void onPlayerStateChanged(boolean playWhenReady, int playbackState);

    /**
     * Called when the value of {@link #getRepeatMode()} changes.
     *
     * @param repeatMode The {@link RepeatMode} used for playback.
     */
    void onRepeatModeChanged(@RepeatMode int repeatMode);

    /**
     * Called when an error occurs. The playback state will transition to {@link #STATE_IDLE}
     * immediately after this method is called. The player instance can still be used, and
     * {@link #release()} must still be called on the player should it no longer be required.
     *
     * @param error The error.
     */
    void onPlayerError(ExoPlaybackException error);

    /**
     * Called when a position discontinuity occurs without a change to the timeline. A position
     * discontinuity occurs when the current window or period index changes (as a result of playback
     * transitioning from one period in the timeline to the next), or when the playback position
     * jumps within the period currently being played (as a result of a seek being performed, or
     * when the source introduces a discontinuity internally).
     * <p>
     * When a position discontinuity occurs as a result of a change to the timeline this method is
     * <em>not</em> called. {@link #onTimelineChanged(Timeline, Object)} is called in this case.
     */
    void onPositionDiscontinuity();

    /**
     * Called when the current playback parameters change. The playback parameters may change due to
     * a call to {@link #setPlaybackParameters(PlaybackParameters)}, or the player itself may change
     * them (for example, if audio playback switches to passthrough mode, where speed adjustment is
     * no longer possible).
     *
     * @param playbackParameters The playback parameters.
     */
    void onPlaybackParametersChanged(PlaybackParameters playbackParameters);

  }

  /**
   * The player does not have any media to play.
   */
  int STATE_IDLE = 1;
  /**
   * The player is not able to immediately play from its current position. This state typically
   * occurs when more data needs to be loaded.
   */
  int STATE_BUFFERING = 2;
  /**
   * The player is able to immediately play from its current position. The player will be playing if
   * {@link #getPlayWhenReady()} is true, and paused otherwise.
   */
  int STATE_READY = 3;
  /**
   * The player has finished playing the media.
   */
  int STATE_ENDED = 4;

  /**
   * Repeat modes for playback.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL})
  public @interface RepeatMode {}
  /**
   * Normal playback without repetition.
   */
  int REPEAT_MODE_OFF = 0;
  /**
   * "Repeat One" mode to repeat the currently playing window infinitely.
   */
  int REPEAT_MODE_ONE = 1;
  /**
   * "Repeat All" mode to repeat the entire timeline infinitely.
   */
  int REPEAT_MODE_ALL = 2;

  /**
   * Register a listener to receive events from the player. The listener's methods will be called on
   * the thread that was used to construct the player. However, if the thread used to construct the
   * player does not have a {@link Looper}, then the listener will be called on the main thread.
   *
   * @param listener The listener to register.
   */
  void addListener(EventListener listener);

  /**
   * Unregister a listener. The listener will no longer receive events from the player.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(EventListener listener);

  /**
   * Returns the current state of the player.
   *
   * @return One of the {@code STATE} constants defined in this interface.
   */
  int getPlaybackState();

  /**
   * Sets whether playback should proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   * <p>
   * If the player is already in the ready state then this method can be used to pause and resume
   * playback.
   *
   * @param playWhenReady Whether playback should proceed when ready.
   */
  void setPlayWhenReady(boolean playWhenReady);

  /**
   * Whether playback will proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * @return Whether playback will proceed when ready.
   */
  boolean getPlayWhenReady();

  /**
   * Sets the {@link RepeatMode} to be used for playback.
   *
   * @param repeatMode A repeat mode.
   */
  void setRepeatMode(@RepeatMode int repeatMode);

  /**
   * Returns the current {@link RepeatMode} used for playback.
   *
   * @return The current repeat mode.
   */
  @RepeatMode int getRepeatMode();

  /**
   * Whether the player is currently loading the source.
   *
   * @return Whether the player is currently loading the source.
   */
  boolean isLoading();

  /**
   * Seeks to the default position associated with the current window. The position can depend on
   * the type of media being played. For live streams it will typically be the live edge of the
   * window. For other streams it will typically be the start of the window.
   */
  void seekToDefaultPosition();

  /**
   * Seeks to the default position associated with the specified window. The position can depend on
   * the type of media being played. For live streams it will typically be the live edge of the
   * window. For other streams it will typically be the start of the window.
   *
   * @param windowIndex The index of the window whose associated default position should be seeked
   *     to.
   */
  void seekToDefaultPosition(int windowIndex);

  /**
   * Seeks to a position specified in milliseconds in the current window.
   *
   * @param positionMs The seek position in the current window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   */
  void seekTo(long positionMs);

  /**
   * Seeks to a position specified in milliseconds in the specified window.
   *
   * @param windowIndex The index of the window.
   * @param positionMs The seek position in the specified window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   */
  void seekTo(int windowIndex, long positionMs);

  /**
   * Attempts to set the playback parameters. Passing {@code null} sets the parameters to the
   * default, {@link PlaybackParameters#DEFAULT}, which means there is no speed or pitch adjustment.
   * <p>
   * Playback parameters changes may cause the player to buffer.
   * {@link EventListener#onPlaybackParametersChanged(PlaybackParameters)} will be called whenever
   * the currently active playback parameters change. When that listener is called, the parameters
   * passed to it may not match {@code playbackParameters}. For example, the chosen speed or pitch
   * may be out of range, in which case they are constrained to a set of permitted values. If it is
   * not possible to change the playback parameters, the listener will not be invoked.
   *
   * @param playbackParameters The playback parameters, or {@code null} to use the defaults.
   */
  void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters);

  /**
   * Returns the currently active playback parameters.
   *
   * @see EventListener#onPlaybackParametersChanged(PlaybackParameters)
   */
  PlaybackParameters getPlaybackParameters();

  /**
   * Stops playback. Use {@code setPlayWhenReady(false)} rather than this method if the intention
   * is to pause playback.
   * <p>
   * Calling this method will cause the playback state to transition to {@link #STATE_IDLE}. The
   * player instance can still be used, and {@link #release()} must still be called on the player if
   * it's no longer required.
   * <p>
   * Calling this method does not reset the playback position.
   */
  void stop();

  /**
   * Releases the player. This method must be called when the player is no longer required. The
   * player must not be used after calling this method.
   */
  void release();

  /**
   * Returns the number of renderers.
   */
  int getRendererCount();

  /**
   * Returns the track type that the renderer at a given index handles.
   *
   * @see Renderer#getTrackType()
   * @param index The index of the renderer.
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
  int getRendererType(int index);

  /**
   * Returns the available track groups.
   */
  TrackGroupArray getCurrentTrackGroups();

  /**
   * Returns the current track selections for each renderer.
   */
  TrackSelectionArray getCurrentTrackSelections();

  /**
   * Returns the current manifest. The type depends on the type of media being played. May be null.
   */
  @Nullable Object getCurrentManifest();

  /**
   * Returns the current {@link Timeline}. Never null, but may be empty.
   */
  Timeline getCurrentTimeline();

  /**
   * Returns the index of the period currently being played.
   */
  int getCurrentPeriodIndex();

  /**
   * Returns the index of the window currently being played.
   */
  int getCurrentWindowIndex();

  /**
   * Returns the duration of the current window in milliseconds, or {@link C#TIME_UNSET} if the
   * duration is not known.
   */
  long getDuration();

  /**
   * Returns the playback position in the current window, in milliseconds.
   */
  long getCurrentPosition();

  /**
   * Returns an estimate of the position in the current window up to which data is buffered, in
   * milliseconds.
   */
  long getBufferedPosition();

  /**
   * Returns an estimate of the percentage in the current window up to which data is buffered, or 0
   * if no estimate is available.
   */
  int getBufferedPercentage();

  /**
   * Returns whether the current window is dynamic, or {@code false} if the {@link Timeline} is
   * empty.
   *
   * @see Timeline.Window#isDynamic
   */
  boolean isCurrentWindowDynamic();

  /**
   * Returns whether the current window is seekable, or {@code false} if the {@link Timeline} is
   * empty.
   *
   * @see Timeline.Window#isSeekable
   */
  boolean isCurrentWindowSeekable();

  /**
   * Returns whether the player is currently playing an ad.
   */
  boolean isPlayingAd();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad group in the period
   * currently being played. Returns {@link C#INDEX_UNSET} otherwise.
   */
  int getCurrentAdGroupIndex();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad in its ad group. Returns
   * {@link C#INDEX_UNSET} otherwise.
   */
  int getCurrentAdIndexInAdGroup();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the content position that will be
   * played once all ads in the ad group have finished playing, in milliseconds. If there is no ad
   * playing, the returned position is the same as that returned by {@link #getCurrentPosition()}.
   */
  long getContentPosition();

}
