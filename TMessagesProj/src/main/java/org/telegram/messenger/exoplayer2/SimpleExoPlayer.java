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

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import org.telegram.messenger.exoplayer2.audio.AudioAttributes;
import org.telegram.messenger.exoplayer2.audio.AudioRendererEventListener;
import org.telegram.messenger.exoplayer2.decoder.DecoderCounters;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.metadata.MetadataRenderer;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.text.Cue;
import org.telegram.messenger.exoplayer2.text.TextRenderer;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelector;
import org.telegram.messenger.exoplayer2.util.Util;
import org.telegram.messenger.exoplayer2.video.VideoRendererEventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link ExoPlayer} implementation that uses default {@link Renderer} components. Instances can
 * be obtained from {@link ExoPlayerFactory}.
 */
@TargetApi(16)
public class SimpleExoPlayer implements ExoPlayer {

  /**
   * A listener for video rendering information from a {@link SimpleExoPlayer}.
   */
  public interface VideoListener {

    /**
     * Called each time there's a change in the size of the video being rendered.
     *
     * @param width The video width in pixels.
     * @param height The video height in pixels.
     * @param unappliedRotationDegrees For videos that require a rotation, this is the clockwise
     *     rotation in degrees that the application should apply for the video for it to be rendered
     *     in the correct orientation. This value will always be zero on API levels 21 and above,
     *     since the renderer will apply all necessary rotations internally. On earlier API levels
     *     this is not possible. Applications that use {@link android.view.TextureView} can apply
     *     the rotation by calling {@link android.view.TextureView#setTransform}. Applications that
     *     do not expect to encounter rotated videos can safely ignore this parameter.
     * @param pixelWidthHeightRatio The width to height ratio of each pixel. For the normal case
     *     of square pixels this will be equal to 1.0. Different values are indicative of anamorphic
     *     content.
     */
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio);

    /**
     * Called when a frame is rendered for the first time since setting the surface, and when a
     * frame is rendered for the first time since a video track was selected.
     */
    void onRenderedFirstFrame();

    boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture);
    void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
  }

  private static final String TAG = "SimpleExoPlayer";

  protected final Renderer[] renderers;

  private final ExoPlayer player;
  private final ComponentListener componentListener;
  private final CopyOnWriteArraySet<VideoListener> videoListeners;
  private final CopyOnWriteArraySet<TextRenderer.Output> textOutputs;
  private final CopyOnWriteArraySet<MetadataRenderer.Output> metadataOutputs;
  private final int videoRendererCount;
  private final int audioRendererCount;

  private boolean needSetSurface = true;

  private Format videoFormat;
  private Format audioFormat;

  private Surface surface;
  private boolean ownsSurface;
  @C.VideoScalingMode
  private int videoScalingMode;
  private SurfaceHolder surfaceHolder;
  private TextureView textureView;
  private AudioRendererEventListener audioDebugListener;
  private VideoRendererEventListener videoDebugListener;
  private DecoderCounters videoDecoderCounters;
  private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float audioVolume;

  protected SimpleExoPlayer(RenderersFactory renderersFactory, TrackSelector trackSelector,
      LoadControl loadControl) {
    componentListener = new ComponentListener();
    videoListeners = new CopyOnWriteArraySet<>();
    textOutputs = new CopyOnWriteArraySet<>();
    metadataOutputs = new CopyOnWriteArraySet<>();
    Looper eventLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
    Handler eventHandler = new Handler(eventLooper);
    renderers = renderersFactory.createRenderers(eventHandler, componentListener, componentListener,
        componentListener, componentListener);

    // Obtain counts of video and audio renderers.
    int videoRendererCount = 0;
    int audioRendererCount = 0;
    for (Renderer renderer : renderers) {
      switch (renderer.getTrackType()) {
        case C.TRACK_TYPE_VIDEO:
          videoRendererCount++;
          break;
        case C.TRACK_TYPE_AUDIO:
          audioRendererCount++;
          break;
      }
    }
    this.videoRendererCount = videoRendererCount;
    this.audioRendererCount = audioRendererCount;

    // Set initial values.
    audioVolume = 1;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    audioAttributes = AudioAttributes.DEFAULT;
    videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;

    // Build the player and associated objects.
    player = new ExoPlayerImpl(renderers, trackSelector, loadControl);
  }

  /**
   * Sets the video scaling mode.
   * <p>
   * Note that the scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer} is
   * enabled and if the output surface is owned by a {@link android.view.SurfaceView}.
   *
   * @param videoScalingMode The video scaling mode.
   */
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    this.videoScalingMode = videoScalingMode;
    ExoPlayerMessage[] messages = new ExoPlayerMessage[videoRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_SCALING_MODE,
            videoScalingMode);
      }
    }
    player.sendMessages(messages);
  }

  /**
   * Returns the video scaling mode.
   */
  public @C.VideoScalingMode int getVideoScalingMode() {
    return videoScalingMode;
  }

  /**
   * Clears any {@link Surface}, {@link SurfaceHolder}, {@link SurfaceView} or {@link TextureView}
   * currently set on the player.
   */
  public void clearVideoSurface() {
    setVideoSurface(null);
  }

  /**
   * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
   * tracking the lifecycle of the surface, and must clear the surface by calling
   * {@code setVideoSurface(null)} if the surface is destroyed.
   * <p>
   * If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link SurfaceHolder}
   * then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)},
   * {@link #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)}
   * rather than this method, since passing the holder allows the player to track the lifecycle of
   * the surface automatically.
   *
   * @param surface The {@link Surface}.
   */
  public void setVideoSurface(Surface surface) {
    removeSurfaceCallbacks();
    setVideoSurfaceInternal(surface, false);
  }

  /**
   * Clears the {@link Surface} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * @param surface The surface to clear.
   */
  public void clearVideoSurface(Surface surface) {
    if (surface != null && surface == this.surface) {
      setVideoSurface(null);
    }
  }

  /**
   * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
   * rendered. The player will track the lifecycle of the surface automatically.
   *
   * @param surfaceHolder The surface holder.
   */
  public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    removeSurfaceCallbacks();
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null) {
      setVideoSurfaceInternal(null, false);
    } else {
      surfaceHolder.addCallback(componentListener);
      Surface surface = surfaceHolder.getSurface();
      setVideoSurfaceInternal(surface != null && surface.isValid() ? surface : null, false);
    }
  }

  /**
   * Clears the {@link SurfaceHolder} that holds the {@link Surface} onto which video is being
   * rendered if it matches the one passed. Else does nothing.
   *
   * @param surfaceHolder The surface holder to clear.
   */
  public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      setVideoSurfaceHolder(null);
    }
  }

  /**
   * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * @param surfaceView The surface view.
   */
  public void setVideoSurfaceView(SurfaceView surfaceView) {
    setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  /**
   * Clears the {@link SurfaceView} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * @param surfaceView The texture view to clear.
   */
  public void clearVideoSurfaceView(SurfaceView surfaceView) {
    clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  /**
   * Sets the {@link TextureView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * @param textureView The texture view.
   */
  public void setVideoTextureView(TextureView textureView) {
    if (this.textureView == textureView) {
      return;
    }
    removeSurfaceCallbacks();
    this.textureView = textureView;
    needSetSurface = true;
    if (textureView == null) {
      setVideoSurfaceInternal(null, true);
    } else {
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w(TAG, "Replacing existing SurfaceTextureListener.");
      }
      textureView.setSurfaceTextureListener(componentListener);
      SurfaceTexture surfaceTexture = textureView.isAvailable() ? textureView.getSurfaceTexture()
          : null;
      setVideoSurfaceInternal(surfaceTexture == null ? null : new Surface(surfaceTexture), true);
    }
  }

  /**
   * Clears the {@link TextureView} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * @param textureView The texture view to clear.
   */
  public void clearVideoTextureView(TextureView textureView) {
    if (textureView != null && textureView == this.textureView) {
      setVideoTextureView(null);
    }
  }

  /**
   * Sets the stream type for audio playback, used by the underlying audio track.
   * <p>
   * Setting the stream type during playback may introduce a short gap in audio output as the audio
   * track is recreated. A new audio session id will also be generated.
   * <p>
   * Calling this method overwrites any attributes set previously by calling
   * {@link #setAudioAttributes(AudioAttributes)}.
   *
   * @deprecated Use {@link #setAudioAttributes(AudioAttributes)}.
   * @param streamType The stream type for audio playback.
   */
  @Deprecated
  public void setAudioStreamType(@C.StreamType int streamType) {
    @C.AudioUsage int usage = Util.getAudioUsageForStreamType(streamType);
    @C.AudioContentType int contentType = Util.getAudioContentTypeForStreamType(streamType);
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build();
    setAudioAttributes(audioAttributes);
  }

  /**
   * Returns the stream type for audio playback.
   *
   * @deprecated Use {@link #getAudioAttributes()}.
   */
  @Deprecated
  public @C.StreamType int getAudioStreamType() {
    return Util.getStreamTypeForAudioUsage(audioAttributes.usage);
  }

  /**
   * Sets the attributes for audio playback, used by the underlying audio track. If not set, the
   * default audio attributes will be used. They are suitable for general media playback.
   * <p>
   * Setting the audio attributes during playback may introduce a short gap in audio output as the
   * audio track is recreated. A new audio session id will also be generated.
   * <p>
   * If tunneling is enabled by the track selector, the specified audio attributes will be ignored,
   * but they will take effect if audio is later played without tunneling.
   * <p>
   * If the device is running a build before platform API version 21, audio attributes cannot be set
   * directly on the underlying audio track. In this case, the usage will be mapped onto an
   * equivalent stream type using {@link Util#getStreamTypeForAudioUsage(int)}.
   *
   * @param audioAttributes The attributes to use for audio playback.
   */
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    this.audioAttributes = audioAttributes;
    ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_AUDIO_ATTRIBUTES,
            audioAttributes);
      }
    }
    player.sendMessages(messages);
  }

  /**
   * Returns the attributes for audio playback.
   */
  public AudioAttributes getAudioAttributes() {
    return audioAttributes;
  }

  /**
   * Sets the audio volume, with 0 being silence and 1 being unity gain.
   *
   * @param audioVolume The audio volume.
   */
  public void setVolume(float audioVolume) {
    this.audioVolume = audioVolume;
    ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_VOLUME, audioVolume);
      }
    }
    player.sendMessages(messages);
  }

  /**
   * Returns the audio volume, with 0 being silence and 1 being unity gain.
   */
  public float getVolume() {
    return audioVolume;
  }

  /**
   * Sets the {@link PlaybackParams} governing audio playback.
   *
   * @deprecated Use {@link #setPlaybackParameters(PlaybackParameters)}.
   * @param params The {@link PlaybackParams}, or null to clear any previously set parameters.
   */
  @Deprecated
  @TargetApi(23)
  public void setPlaybackParams(@Nullable PlaybackParams params) {
    PlaybackParameters playbackParameters;
    if (params != null) {
      params.allowDefaults();
      playbackParameters = new PlaybackParameters(params.getSpeed(), params.getPitch());
    } else {
      playbackParameters = null;
    }
    setPlaybackParameters(playbackParameters);
  }

  /**
   * Returns the video format currently being played, or null if no video is being played.
   */
  public Format getVideoFormat() {
    return videoFormat;
  }

  /**
   * Returns the audio format currently being played, or null if no audio is being played.
   */
  public Format getAudioFormat() {
    return audioFormat;
  }

  /**
   * Returns the audio session identifier, or {@link C#AUDIO_SESSION_ID_UNSET} if not set.
   */
  public int getAudioSessionId() {
    return audioSessionId;
  }

  /**
   * Returns {@link DecoderCounters} for video, or null if no video is being played.
   */
  public DecoderCounters getVideoDecoderCounters() {
    return videoDecoderCounters;
  }

  /**
   * Returns {@link DecoderCounters} for audio, or null if no audio is being played.
   */
  public DecoderCounters getAudioDecoderCounters() {
    return audioDecoderCounters;
  }

  /**
   * Adds a listener to receive video events.
   *
   * @param listener The listener to register.
   */
  public void addVideoListener(VideoListener listener) {
    videoListeners.add(listener);
  }

  /**
   * Removes a listener of video events.
   *
   * @param listener The listener to unregister.
   */
  public void removeVideoListener(VideoListener listener) {
    videoListeners.remove(listener);
  }

  /**
   * Sets a listener to receive video events, removing all existing listeners.
   *
   * @param listener The listener.
   * @deprecated Use {@link #addVideoListener(VideoListener)}.
   */
  @Deprecated
  public void setVideoListener(VideoListener listener) {
    videoListeners.clear();
    if (listener != null) {
      addVideoListener(listener);
    }
  }

  /**
   * Equivalent to {@link #removeVideoListener(VideoListener)}.
   *
   * @param listener The listener to clear.
   * @deprecated Use {@link #removeVideoListener(VideoListener)}.
   */
  @Deprecated
  public void clearVideoListener(VideoListener listener) {
    removeVideoListener(listener);
  }

  /**
   * Registers an output to receive text events.
   *
   * @param listener The output to register.
   */
  public void addTextOutput(TextRenderer.Output listener) {
    textOutputs.add(listener);
  }

  /**
   * Removes a text output.
   *
   * @param listener The output to remove.
   */
  public void removeTextOutput(TextRenderer.Output listener) {
    textOutputs.remove(listener);
  }

  /**
   * Sets an output to receive text events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addTextOutput(TextRenderer.Output)}.
   */
  @Deprecated
  public void setTextOutput(TextRenderer.Output output) {
    textOutputs.clear();
    if (output != null) {
      addTextOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeTextOutput(TextRenderer.Output)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeTextOutput(TextRenderer.Output)}.
   */
  @Deprecated
  public void clearTextOutput(TextRenderer.Output output) {
    removeTextOutput(output);
  }

  /**
   * Registers an output to receive metadata events.
   *
   * @param listener The output to register.
   */
  public void addMetadataOutput(MetadataRenderer.Output listener) {
    metadataOutputs.add(listener);
  }

  /**
   * Removes a metadata output.
   *
   * @param listener The output to remove.
   */
  public void removeMetadataOutput(MetadataRenderer.Output listener) {
    metadataOutputs.remove(listener);
  }

  /**
   * Sets an output to receive metadata events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addMetadataOutput(MetadataRenderer.Output)}.
   */
  @Deprecated
  public void setMetadataOutput(MetadataRenderer.Output output) {
    metadataOutputs.clear();
    if (output != null) {
      addMetadataOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeMetadataOutput(MetadataRenderer.Output)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeMetadataOutput(MetadataRenderer.Output)}.
   */
  @Deprecated
  public void clearMetadataOutput(MetadataRenderer.Output output) {
    removeMetadataOutput(output);
  }

  /**
   * Sets a listener to receive debug events from the video renderer.
   *
   * @param listener The listener.
   */
  public void setVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListener = listener;
  }

  /**
   * Sets a listener to receive debug events from the audio renderer.
   *
   * @param listener The listener.
   */
  public void setAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListener = listener;
  }

  // ExoPlayer implementation

  @Override
  public Looper getPlaybackLooper() {
    return player.getPlaybackLooper();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    player.addListener(listener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    player.removeListener(listener);
  }

  @Override
  public int getPlaybackState() {
    return player.getPlaybackState();
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    player.prepare(mediaSource);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    player.prepare(mediaSource, resetPosition, resetState);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
  }

  @Override
  public boolean getPlayWhenReady() {
    return player.getPlayWhenReady();
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    return player.getRepeatMode();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    player.setRepeatMode(repeatMode);
  }

  @Override
  public boolean isLoading() {
    return player.isLoading();
  }

  @Override
  public void seekToDefaultPosition() {
    player.seekToDefaultPosition();
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    player.seekToDefaultPosition(windowIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    player.seekTo(windowIndex, positionMs);
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return player.getPlaybackParameters();
  }

  @Override
  public void stop() {
    player.stop();
  }

  @Override
  public void release() {
    player.release();
    removeSurfaceCallbacks();
    if (surface != null) {
      if (ownsSurface) {
        surface.release();
      }
      surface = null;
    }
  }

  @Override
  public void sendMessages(ExoPlayerMessage... messages) {
    player.sendMessages(messages);
  }

  @Override
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    player.blockingSendMessages(messages);
  }

  @Override
  public int getRendererCount() {
    return player.getRendererCount();
  }

  @Override
  public int getRendererType(int index) {
    return player.getRendererType(index);
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return player.getCurrentTrackGroups();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return player.getCurrentTrackSelections();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return player.getCurrentTimeline();
  }

  @Override
  public Object getCurrentManifest() {
    return player.getCurrentManifest();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return player.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    return player.getCurrentWindowIndex();
  }

  @Override
  public long getDuration() {
    return player.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    return player.getBufferedPosition();
  }

  @Override
  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  @Override
  public boolean isCurrentWindowDynamic() {
    return player.isCurrentWindowDynamic();
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    return player.isCurrentWindowSeekable();
  }

  @Override
  public boolean isPlayingAd() {
    return player.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return player.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return player.getCurrentAdIndexInAdGroup();
  }

  @Override
  public long getContentPosition() {
    return player.getContentPosition();
  }

  // Internal methods.

  private void removeSurfaceCallbacks() {
    if (textureView != null) {
      if (textureView.getSurfaceTextureListener() != componentListener) {
        Log.w(TAG, "SurfaceTextureListener already unset or replaced.");
      } else {
        textureView.setSurfaceTextureListener(null);
      }
      textureView = null;
    }
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(componentListener);
      surfaceHolder = null;
    }
  }

  private void setVideoSurfaceInternal(Surface surface, boolean ownsSurface) {
    // Note: We don't turn this method into a no-op if the surface is being replaced with itself
    // so as to ensure onRenderedFirstFrame callbacks are still called in this case.
    ExoPlayerMessage[] messages = new ExoPlayerMessage[videoRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_SURFACE, surface);
      }
    }
    if (this.surface != null && this.surface != surface) {
      // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
      player.blockingSendMessages(messages);
      // If we created the previous surface, we are responsible for releasing it.
      if (this.ownsSurface) {
        this.surface.release();
      }
    } else {
      player.sendMessages(messages);
    }
    this.surface = surface;
    this.ownsSurface = ownsSurface;
  }

  private final class ComponentListener implements VideoRendererEventListener,
      AudioRendererEventListener, TextRenderer.Output, MetadataRenderer.Output,
      SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      if (videoDebugListener != null) {
        videoDebugListener.onVideoEnabled(counters);
      }
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      if (videoDebugListener != null) {
        videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
      videoFormat = format;
      if (videoDebugListener != null) {
        videoDebugListener.onVideoInputFormatChanged(format);
      }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      if (videoDebugListener != null) {
        videoDebugListener.onDroppedFrames(count, elapsed);
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      for (VideoListener videoListener : videoListeners) {
        videoListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
      if (videoDebugListener != null) {
        videoDebugListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
      if (SimpleExoPlayer.this.surface == surface) {
        for (VideoListener videoListener : videoListeners) {
          videoListener.onRenderedFirstFrame();
        }
      }
      if (videoDebugListener != null) {
        videoDebugListener.onRenderedFirstFrame(surface);
      }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      if (videoDebugListener != null) {
        videoDebugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoDecoderCounters = null;
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      if (audioDebugListener != null) {
        audioDebugListener.onAudioEnabled(counters);
      }
    }

    @Override
    public void onAudioSessionId(int sessionId) {
      audioSessionId = sessionId;
      if (audioDebugListener != null) {
        audioDebugListener.onAudioSessionId(sessionId);
      }
    }

    @Override
    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      if (audioDebugListener != null) {
        audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
      audioFormat = format;
      if (audioDebugListener != null) {
        audioDebugListener.onAudioInputFormatChanged(format);
      }
    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
        long elapsedSinceLastFeedMs) {
      if (audioDebugListener != null) {
        audioDebugListener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      if (audioDebugListener != null) {
        audioDebugListener.onAudioDisabled(counters);
      }
      audioFormat = null;
      audioDecoderCounters = null;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    }

    // TextRenderer.Output implementation

    @Override
    public void onCues(List<Cue> cues) {
      for (TextRenderer.Output textOutput : textOutputs) {
        textOutput.onCues(cues);
      }
    }

    // MetadataRenderer.Output implementation

    @Override
    public void onMetadata(Metadata metadata) {
      for (MetadataRenderer.Output metadataOutput : metadataOutputs) {
        metadataOutput.onMetadata(metadata);
      }
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      setVideoSurfaceInternal(holder.getSurface(), false);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      setVideoSurfaceInternal(null, false);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      if (needSetSurface) {
        setVideoSurfaceInternal(new Surface(surfaceTexture), true);
        needSetSurface = false;
      }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      // Do nothing.
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      for (VideoListener videoListener : videoListeners) {
        if (videoListener.onSurfaceDestroyed(surfaceTexture)) {
          return false;
        }
      }
      setVideoSurfaceInternal(null, true);
      needSetSurface = true;

      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      for (VideoListener videoListener : videoListeners) {
        videoListener.onSurfaceTextureUpdated(surfaceTexture);
      }
    }

  }

}
