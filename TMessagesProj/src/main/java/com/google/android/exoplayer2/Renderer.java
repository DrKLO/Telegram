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
package com.google.android.exoplayer2;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodec;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Renders media read from a {@link SampleStream}.
 *
 * <p>Internally, a renderer's lifecycle is managed by the owning {@link ExoPlayer}. The renderer is
 * transitioned through various states as the overall playback state and enabled tracks change. The
 * valid state transitions are shown below, annotated with the methods that are called during each
 * transition.
 *
 * <p style="align:center"><img src="doc-files/renderer-states.svg" alt="Renderer state
 * transitions">
 */
public interface Renderer extends PlayerMessage.Target {

  /**
   * Some renderers can signal when {@link #render(long, long)} should be called.
   *
   * <p>That allows the player to sleep until the next wakeup, instead of calling {@link
   * #render(long, long)} in a tight loop. The aim of this interrupt based scheduling is to save
   * power.
   */
  interface WakeupListener {

    /**
     * The renderer no longer needs to render until the next wakeup.
     *
     * <p>Must be called from the thread ExoPlayer invokes the renderer from.
     */
    void onSleep();

    /**
     * The renderer needs to render some frames. The client should call {@link #render(long, long)}
     * at its earliest convenience.
     *
     * <p>Can be called from any thread.
     */
    void onWakeup();
  }

  /**
   * Represents a type of message that can be passed to a renderer. May be one of {@link
   * #MSG_SET_VIDEO_OUTPUT}, {@link #MSG_SET_VOLUME}, {@link #MSG_SET_AUDIO_ATTRIBUTES}, {@link
   * #MSG_SET_SCALING_MODE}, {@link #MSG_SET_CHANGE_FRAME_RATE_STRATEGY}, {@link
   * #MSG_SET_AUX_EFFECT_INFO}, {@link #MSG_SET_VIDEO_FRAME_METADATA_LISTENER}, {@link
   * #MSG_SET_CAMERA_MOTION_LISTENER}, {@link #MSG_SET_SKIP_SILENCE_ENABLED}, {@link
   * #MSG_SET_AUDIO_SESSION_ID} or {@link #MSG_SET_WAKEUP_LISTENER}. May also be an app-defined
   * value (see {@link #MSG_CUSTOM_BASE}).
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      open = true,
      value = {
        MSG_SET_VIDEO_OUTPUT,
        MSG_SET_VOLUME,
        MSG_SET_AUDIO_ATTRIBUTES,
        MSG_SET_SCALING_MODE,
        MSG_SET_CHANGE_FRAME_RATE_STRATEGY,
        MSG_SET_AUX_EFFECT_INFO,
        MSG_SET_VIDEO_FRAME_METADATA_LISTENER,
        MSG_SET_CAMERA_MOTION_LISTENER,
        MSG_SET_SKIP_SILENCE_ENABLED,
        MSG_SET_AUDIO_SESSION_ID,
        MSG_SET_WAKEUP_LISTENER
      })
  public @interface MessageType {}
  /**
   * The type of a message that can be passed to a video renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload is normally a {@link
   * Surface}, however some video renderers may accept other outputs (e.g., {@link
   * VideoDecoderOutputBufferRenderer}).
   *
   * <p>If the receiving renderer does not support the payload type as an output, then it will clear
   * any existing output that it has.
   */
  int MSG_SET_VIDEO_OUTPUT = 1;
  /**
   * A type of a message that can be passed to an audio renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be a {@link Float}
   * with 0 being silence and 1 being unity gain.
   */
  int MSG_SET_VOLUME = 2;
  /**
   * A type of a message that can be passed to an audio renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be an {@link
   * AudioAttributes} instance that will configure the underlying audio track. If not set, the
   * default audio attributes will be used. They are suitable for general media playback.
   *
   * <p>Setting the audio attributes during playback may introduce a short gap in audio output as
   * the audio track is recreated. A new audio session id will also be generated.
   *
   * <p>If tunneling is enabled by the track selector, the specified audio attributes will be
   * ignored, but they will take effect if audio is later played without tunneling.
   *
   * <p>If the device is running a build before platform API version 21, audio attributes cannot be
   * set directly on the underlying audio track. In this case, the usage will be mapped onto an
   * equivalent stream type using {@link Util#getStreamTypeForAudioUsage(int)}.
   *
   * <p>To get audio attributes that are equivalent to a legacy stream type, pass the stream type to
   * {@link Util#getAudioUsageForStreamType(int)} and use the returned {@link C.AudioUsage} to build
   * an audio attributes instance.
   */
  int MSG_SET_AUDIO_ATTRIBUTES = 3;
  /**
   * The type of a message that can be passed to a {@link MediaCodec}-based video renderer via
   * {@link ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be one of the
   * integer scaling modes in {@link C.VideoScalingMode}.
   *
   * <p>Note that the scaling mode only applies if the {@link Surface} targeted by the renderer is
   * owned by a {@link android.view.SurfaceView}.
   */
  int MSG_SET_SCALING_MODE = 4;
  /**
   * The type of a message that can be passed to a video renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be one of the
   * integer strategy constants in {@link C.VideoChangeFrameRateStrategy}.
   */
  int MSG_SET_CHANGE_FRAME_RATE_STRATEGY = 5;
  /**
   * A type of a message that can be passed to an audio renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be an {@link
   * AuxEffectInfo} instance representing an auxiliary audio effect for the underlying audio track.
   */
  int MSG_SET_AUX_EFFECT_INFO = 6;
  /**
   * The type of a message that can be passed to a video renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be a {@link
   * VideoFrameMetadataListener} instance, or null.
   */
  int MSG_SET_VIDEO_FRAME_METADATA_LISTENER = 7;
  /**
   * The type of a message that can be passed to a camera motion renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be a {@link
   * CameraMotionListener} instance, or null.
   */
  int MSG_SET_CAMERA_MOTION_LISTENER = 8;
  /**
   * The type of a message that can be passed to an audio renderer via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be a {@link Boolean}
   * instance telling whether to enable or disable skipping silences in the audio stream.
   */
  int MSG_SET_SKIP_SILENCE_ENABLED = 9;
  /**
   * The type of a message that can be passed to audio and video renderers via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be an {@link
   * Integer} instance representing the audio session ID that will be attached to the underlying
   * audio track. Video renderers that support tunneling will use the audio session ID when
   * tunneling is enabled.
   */
  int MSG_SET_AUDIO_SESSION_ID = 10;
  /**
   * The type of a message that can be passed to a {@link Renderer} via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}, to inform the renderer that it can schedule
   * waking up another component.
   *
   * <p>The message payload must be a {@link WakeupListener} instance.
   */
  int MSG_SET_WAKEUP_LISTENER = 11;
  /**
   * The type of a message that can be passed to audio renderers via {@link
   * ExoPlayer#createMessage(PlayerMessage.Target)}. The message payload should be an {@link
   * android.media.AudioDeviceInfo} instance representing the preferred audio device, or null to
   * restore the default.
   */
  int MSG_SET_PREFERRED_AUDIO_DEVICE = 12;
  /**
   * Applications or extensions may define custom {@code MSG_*} constants that can be passed to
   * renderers. These custom constants must be greater than or equal to this value.
   */
  int MSG_CUSTOM_BASE = 10000;

  /**
   * The renderer states. One of {@link #STATE_DISABLED}, {@link #STATE_ENABLED} or {@link
   * #STATE_STARTED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_DISABLED, STATE_ENABLED, STATE_STARTED})
  @interface State {}
  /**
   * The renderer is disabled. A renderer in this state will not proactively acquire resources that
   * it requires for rendering (e.g., media decoders), but may continue to hold any that it already
   * has. {@link #reset()} can be called to force the renderer to release such resources.
   */
  int STATE_DISABLED = 0;
  /**
   * The renderer is enabled but not started. A renderer in this state may render media at the
   * current position (e.g. an initial video frame), but the position will not advance. A renderer
   * in this state will typically hold resources that it requires for rendering (e.g. media
   * decoders).
   */
  int STATE_ENABLED = 1;
  /**
   * The renderer is started. Calls to {@link #render(long, long)} will cause media to be rendered.
   */
  int STATE_STARTED = 2;

  /**
   * Returns the name of this renderer, for logging and debugging purposes. Should typically be the
   * renderer's (un-obfuscated) class name.
   *
   * @return The name of this renderer.
   */
  String getName();

  /**
   * Returns the track type that the renderer handles.
   *
   * @see ExoPlayer#getRendererType(int)
   * @return The {@link C.TrackType track type}.
   */
  @C.TrackType
  int getTrackType();

  /**
   * Returns the capabilities of the renderer.
   *
   * @return The capabilities of the renderer.
   */
  RendererCapabilities getCapabilities();

  /**
   * Initializes the renderer for playback with a player.
   *
   * @param index The renderer index within the player.
   * @param playerId The {@link PlayerId} of the player.
   */
  void init(int index, PlayerId playerId);

  /**
   * If the renderer advances its own playback position then this method returns a corresponding
   * {@link MediaClock}. If provided, the player will use the returned {@link MediaClock} as its
   * source of time during playback. A player may have at most one renderer that returns a {@link
   * MediaClock} from this method.
   *
   * @return The {@link MediaClock} tracking the playback position of the renderer, or null.
   */
  @Nullable
  MediaClock getMediaClock();

  /**
   * Returns the current state of the renderer.
   *
   * @return The current state. One of {@link #STATE_DISABLED}, {@link #STATE_ENABLED} and {@link
   *     #STATE_STARTED}.
   */
  @State
  int getState();

  /**
   * Enables the renderer to consume from the specified {@link SampleStream}.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_DISABLED}.
   *
   * @param configuration The renderer configuration.
   * @param formats The enabled formats.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @param mayRenderStartOfStream Whether this renderer is allowed to render the start of the
   *     stream even if the state is not {@link #STATE_STARTED} yet.
   * @param startPositionUs The start position of the stream in renderer time (microseconds).
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream} before
   *     they are rendered.
   * @throws ExoPlaybackException If an error occurs.
   */
  void enable(
      RendererConfiguration configuration,
      Format[] formats,
      SampleStream stream,
      long positionUs,
      boolean joining,
      boolean mayRenderStartOfStream,
      long startPositionUs,
      long offsetUs)
      throws ExoPlaybackException;

  /**
   * Starts the renderer, meaning that calls to {@link #render(long, long)} will cause media to be
   * rendered.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  void start() throws ExoPlaybackException;

  /**
   * Replaces the {@link SampleStream} from which samples will be consumed.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param formats The enabled formats.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param startPositionUs The start position of the new stream in renderer time (microseconds).
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream} before
   *     they are rendered.
   * @throws ExoPlaybackException If an error occurs.
   */
  void replaceStream(Format[] formats, SampleStream stream, long startPositionUs, long offsetUs)
      throws ExoPlaybackException;

  /** Returns the {@link SampleStream} being consumed, or null if the renderer is disabled. */
  @Nullable
  SampleStream getStream();

  /**
   * Returns whether the renderer has read the current {@link SampleStream} to the end.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  boolean hasReadStreamToEnd();

  /**
   * Returns the renderer time up to which the renderer has read samples, in microseconds, or {@link
   * C#TIME_END_OF_SOURCE} if the renderer has read the current {@link SampleStream} to the end.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   */
  long getReadingPositionUs();

  /**
   * Signals to the renderer that the current {@link SampleStream} will be the final one supplied
   * before it is next disabled or reset.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
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
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @throws IOException An error that's preventing the renderer from making progress or buffering
   *     more data.
   */
  void maybeThrowStreamError() throws IOException;

  /**
   * Signals to the renderer that a position discontinuity has occurred.
   *
   * <p>After a position discontinuity, the renderer's {@link SampleStream} is guaranteed to provide
   * samples starting from a key frame.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The new playback position in microseconds.
   * @throws ExoPlaybackException If an error occurs handling the reset.
   */
  void resetPosition(long positionUs) throws ExoPlaybackException;

  /**
   * Indicates the playback speed to this renderer.
   *
   * <p>The default implementation is a no-op.
   *
   * @param currentPlaybackSpeed The factor by which playback is currently sped up.
   * @param targetPlaybackSpeed The target factor by which playback should be sped up. This may be
   *     different from {@code currentPlaybackSpeed}, for example, if the speed is temporarily
   *     adjusted for live playback.
   * @throws ExoPlaybackException If an error occurs handling the playback speed.
   */
  default void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
      throws ExoPlaybackException {}

  /**
   * Incrementally renders the {@link SampleStream}.
   *
   * <p>If the renderer is in the {@link #STATE_ENABLED} state then each call to this method will do
   * work toward being ready to render the {@link SampleStream} when the renderer is started. If the
   * renderer is in the {@link #STATE_STARTED} state then calls to this method will render the
   * {@link SampleStream} in sync with the specified media positions.
   *
   * <p>The renderer may also render the very start of the media at the current position (e.g. the
   * first frame of a video stream) while still in the {@link #STATE_ENABLED} state, unless it's the
   * initial start of the media after calling {@link #enable(RendererConfiguration, Format[],
   * SampleStream, long, boolean, boolean, long, long)} with {@code mayRenderStartOfStream} set to
   * {@code false}.
   *
   * <p>This method should return quickly, and should not block if the renderer is unable to make
   * useful progress.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @param positionUs The current media time in microseconds, measured at the start of the current
   *     iteration of the rendering loop.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @throws ExoPlaybackException If an error occurs.
   */
  void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException;

  /**
   * Whether the renderer is able to immediately render media from the current position.
   *
   * <p>If the renderer is in the {@link #STATE_STARTED} state then returning true indicates that
   * the renderer has everything that it needs to continue playback. Returning false indicates that
   * the player should pause until the renderer is ready.
   *
   * <p>If the renderer is in the {@link #STATE_ENABLED} state then returning true indicates that
   * the renderer is ready for playback to be started. Returning false indicates that it is not.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return Whether the renderer is ready to render media.
   */
  boolean isReady();

  /**
   * Whether the renderer is ready for the {@link ExoPlayer} instance to transition to {@link
   * Player#STATE_ENDED}. The player will make this transition as soon as {@code true} is returned
   * by all of its renderers.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}, {@link #STATE_STARTED}.
   *
   * @return Whether the renderer is ready for the player to transition to the ended state.
   */
  boolean isEnded();

  /**
   * Stops the renderer, transitioning it to the {@link #STATE_ENABLED} state.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_STARTED}.
   */
  void stop();

  /**
   * Disable the renderer, transitioning it to the {@link #STATE_DISABLED} state.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_ENABLED}.
   */
  void disable();

  /**
   * Forces the renderer to give up any resources (e.g. media decoders) that it may be holding. If
   * the renderer is not holding any resources, the call is a no-op.
   *
   * <p>This method may be called when the renderer is in the following states: {@link
   * #STATE_DISABLED}.
   */
  void reset();
}
