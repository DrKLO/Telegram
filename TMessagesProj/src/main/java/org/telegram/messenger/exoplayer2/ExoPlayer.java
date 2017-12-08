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

import android.os.Looper;
import org.telegram.messenger.exoplayer2.audio.MediaCodecAudioRenderer;
import org.telegram.messenger.exoplayer2.metadata.MetadataRenderer;
import org.telegram.messenger.exoplayer2.source.ClippingMediaSource;
import org.telegram.messenger.exoplayer2.source.ConcatenatingMediaSource;
import org.telegram.messenger.exoplayer2.source.DynamicConcatenatingMediaSource;
import org.telegram.messenger.exoplayer2.source.ExtractorMediaSource;
import org.telegram.messenger.exoplayer2.source.LoopingMediaSource;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.MergingMediaSource;
import org.telegram.messenger.exoplayer2.source.SingleSampleMediaSource;
import org.telegram.messenger.exoplayer2.text.TextRenderer;
import org.telegram.messenger.exoplayer2.trackselection.DefaultTrackSelector;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelector;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.video.MediaCodecVideoRenderer;

/**
 * An extensible media player that plays {@link MediaSource}s. Instances can be obtained from
 * {@link ExoPlayerFactory}.
 *
 * <h3>Player components</h3>
 * <p>ExoPlayer is designed to make few assumptions about (and hence impose few restrictions on) the
 * type of the media being played, how and where it is stored, and how it is rendered. Rather than
 * implementing the loading and rendering of media directly, ExoPlayer implementations delegate this
 * work to components that are injected when a player is created or when it's prepared for playback.
 * Components common to all ExoPlayer implementations are:
 * <ul>
 *   <li>A <b>{@link MediaSource}</b> that defines the media to be played, loads the media, and from
 *   which the loaded media can be read. A MediaSource is injected via {@link #prepare(MediaSource)}
 *   at the start of playback. The library modules provide default implementations for regular media
 *   files ({@link ExtractorMediaSource}), DASH (DashMediaSource), SmoothStreaming (SsMediaSource)
 *   and HLS (HlsMediaSource), an implementation for loading single media samples
 *   ({@link SingleSampleMediaSource}) that's most often used for side-loaded subtitle files, and
 *   implementations for building more complex MediaSources from simpler ones
 *   ({@link MergingMediaSource}, {@link ConcatenatingMediaSource},
 *   {@link DynamicConcatenatingMediaSource}, {@link LoopingMediaSource} and
 *   {@link ClippingMediaSource}).</li>
 *   <li><b>{@link Renderer}</b>s that render individual components of the media. The library
 *   provides default implementations for common media types ({@link MediaCodecVideoRenderer},
 *   {@link MediaCodecAudioRenderer}, {@link TextRenderer} and {@link MetadataRenderer}). A Renderer
 *   consumes media from the MediaSource being played. Renderers are injected when the player is
 *   created.</li>
 *   <li>A <b>{@link TrackSelector}</b> that selects tracks provided by the MediaSource to be
 *   consumed by each of the available Renderers. The library provides a default implementation
 *   ({@link DefaultTrackSelector}) suitable for most use cases. A TrackSelector is injected when
 *   the player is created.</li>
 *   <li>A <b>{@link LoadControl}</b> that controls when the MediaSource buffers more media, and how
 *   much media is buffered. The library provides a default implementation
 *   ({@link DefaultLoadControl}) suitable for most use cases. A LoadControl is injected when the
 *   player is created.</li>
 * </ul>
 * <p>An ExoPlayer can be built using the default components provided by the library, but may also
 * be built using custom implementations if non-standard behaviors are required. For example a
 * custom LoadControl could be injected to change the player's buffering strategy, or a custom
 * Renderer could be injected to add support for a video codec not supported natively by Android.
 *
 * <p>The concept of injecting components that implement pieces of player functionality is present
 * throughout the library. The default component implementations listed above delegate work to
 * further injected components. This allows many sub-components to be individually replaced with
 * custom implementations. For example the default MediaSource implementations require one or more
 * {@link DataSource} factories to be injected via their constructors. By providing a custom factory
 * it's possible to load data from a non-standard source, or through a different network stack.
 *
 * <h3>Threading model</h3>
 * <p>The figure below shows ExoPlayer's threading model.</p>
 * <p align="center">
 *   <img src="doc-files/exoplayer-threading-model.svg" alt="ExoPlayer's threading model">
 * </p>
 *
 * <ul>
 * <li>It is recommended that ExoPlayer instances are created and accessed from a single application
 * thread. The application's main thread is ideal. Accessing an instance from multiple threads is
 * discouraged, however if an application does wish to do this then it may do so provided that it
 * ensures accesses are synchronized.</li>
 * <li>Registered listeners are called on the thread that created the ExoPlayer instance, unless
 * the thread that created the ExoPlayer instance does not have a {@link Looper}. In that case,
 * registered listeners will be called on the application's main thread.</li>
 * <li>An internal playback thread is responsible for playback. Injected player components such as
 * Renderers, MediaSources, TrackSelectors and LoadControls are called by the player on this
 * thread.</li>
 * <li>When the application performs an operation on the player, for example a seek, a message is
 * delivered to the internal playback thread via a message queue. The internal playback thread
 * consumes messages from the queue and performs the corresponding operations. Similarly, when a
 * playback event occurs on the internal playback thread, a message is delivered to the application
 * thread via a second message queue. The application thread consumes messages from the queue,
 * updating the application visible state and calling corresponding listener methods.</li>
 * <li>Injected player components may use additional background threads. For example a MediaSource
 * may use background threads to load data. These are implementation specific.</li>
 * </ul>
 */
public interface ExoPlayer extends Player {

  /**
   * @deprecated Use {@link Player.EventListener} instead.
   */
  @Deprecated
  interface EventListener extends Player.EventListener {}

  /**
   * A component of an {@link ExoPlayer} that can receive messages on the playback thread.
   * <p>
   * Messages can be delivered to a component via {@link #sendMessages} and
   * {@link #blockingSendMessages}.
   */
  interface ExoPlayerComponent {

    /**
     * Handles a message delivered to the component. Called on the playback thread.
     *
     * @param messageType The message type.
     * @param message The message.
     * @throws ExoPlaybackException If an error occurred whilst handling the message.
     */
    void handleMessage(int messageType, Object message) throws ExoPlaybackException;

  }

  /**
   * Defines a message and a target {@link ExoPlayerComponent} to receive it.
   */
  final class ExoPlayerMessage {

    /**
     * The target to receive the message.
     */
    public final ExoPlayerComponent target;
    /**
     * The type of the message.
     */
    public final int messageType;
    /**
     * The message.
     */
    public final Object message;

    /**
     * @param target The target of the message.
     * @param messageType The message type.
     * @param message The message.
     */
    public ExoPlayerMessage(ExoPlayerComponent target, int messageType, Object message) {
      this.target = target;
      this.messageType = messageType;
      this.message = message;
    }

  }

  /**
   * @deprecated Use {@link Player#STATE_IDLE} instead.
   */
  @Deprecated
  int STATE_IDLE = Player.STATE_IDLE;
  /**
   * @deprecated Use {@link Player#STATE_BUFFERING} instead.
   */
  @Deprecated
  int STATE_BUFFERING = Player.STATE_BUFFERING;
  /**
   * @deprecated Use {@link Player#STATE_READY} instead.
   */
  @Deprecated
  int STATE_READY = Player.STATE_READY;
  /**
   * @deprecated Use {@link Player#STATE_ENDED} instead.
   */
  @Deprecated
  int STATE_ENDED = Player.STATE_ENDED;

  /**
   * @deprecated Use {@link Player#REPEAT_MODE_OFF} instead.
   */
  @Deprecated
  @RepeatMode int REPEAT_MODE_OFF = Player.REPEAT_MODE_OFF;
  /**
   * @deprecated Use {@link Player#REPEAT_MODE_ONE} instead.
   */
  @Deprecated
  @RepeatMode int REPEAT_MODE_ONE = Player.REPEAT_MODE_ONE;
  /**
   * @deprecated Use {@link Player#REPEAT_MODE_ALL} instead.
   */
  @Deprecated
  @RepeatMode int REPEAT_MODE_ALL = Player.REPEAT_MODE_ALL;

  /**
   * Gets the {@link Looper} associated with the playback thread.
   *
   * @return The {@link Looper} associated with the playback thread.
   */
  Looper getPlaybackLooper();

  /**
   * Prepares the player to play the provided {@link MediaSource}. Equivalent to
   * {@code prepare(mediaSource, true, true)}.
   */
  void prepare(MediaSource mediaSource);

  /**
   * Prepares the player to play the provided {@link MediaSource}, optionally resetting the playback
   * position the default position in the first {@link Timeline.Window}.
   *
   * @param mediaSource The {@link MediaSource} to play.
   * @param resetPosition Whether the playback position should be reset to the default position in
   *     the first {@link Timeline.Window}. If false, playback will start from the position defined
   *     by {@link #getCurrentWindowIndex()} and {@link #getCurrentPosition()}.
   * @param resetState Whether the timeline, manifest, tracks and track selections should be reset.
   *     Should be true unless the player is being prepared to play the same media as it was playing
   *     previously (e.g. if playback failed and is being retried).
   */
  void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState);

  /**
   * Sends messages to their target components. The messages are delivered on the playback thread.
   * If a component throws an {@link ExoPlaybackException} then it is propagated out of the player
   * as an error.
   *
   * @param messages The messages to be sent.
   */
  void sendMessages(ExoPlayerMessage... messages);

  /**
   * Variant of {@link #sendMessages(ExoPlayerMessage...)} that blocks until after the messages have
   * been delivered.
   *
   * @param messages The messages to be sent.
   */
  void blockingSendMessages(ExoPlayerMessage... messages);

}
