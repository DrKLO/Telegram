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
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
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
import org.telegram.messenger.exoplayer2.video.VideoRendererEventListener;
import java.util.List;

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
  private TextRenderer.Output textOutput;
  private MetadataRenderer.Output metadataOutput;
  private VideoListener videoListener;
  private AudioRendererEventListener audioDebugListener;
  private VideoRendererEventListener videoDebugListener;
  private DecoderCounters videoDecoderCounters;
  private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  @C.StreamType
  private int audioStreamType;
  private float audioVolume;

  protected SimpleExoPlayer(RenderersFactory renderersFactory, TrackSelector trackSelector,
      LoadControl loadControl) {
    componentListener = new ComponentListener();
    renderers = renderersFactory.createRenderers(new Handler(), componentListener,
        componentListener, componentListener, componentListener);

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
    audioStreamType = C.STREAM_TYPE_DEFAULT;
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
      setVideoSurfaceInternal(surfaceHolder.getSurface(), false);
      surfaceHolder.addCallback(componentListener);
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
      SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
      setVideoSurfaceInternal(surfaceTexture == null ? null : new Surface(surfaceTexture), true);
      textureView.setSurfaceTextureListener(componentListener);
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
   * Sets the stream type for audio playback (see {@link C.StreamType} and
   * {@link android.media.AudioTrack#AudioTrack(int, int, int, int, int, int)}). If the stream type
   * is not set, audio renderers use {@link C#STREAM_TYPE_DEFAULT}.
   * <p>
   * Note that when the stream type changes, the AudioTrack must be reinitialized, which can
   * introduce a brief gap in audio output. Note also that tracks in the same audio session must
   * share the same routing, so a new audio session id will be generated.
   *
   * @param audioStreamType The stream type for audio playback.
   */
  public void setAudioStreamType(@C.StreamType int audioStreamType) {
    this.audioStreamType = audioStreamType;
    ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
    int count = 0;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_STREAM_TYPE, audioStreamType);
      }
    }
    player.sendMessages(messages);
  }

  /**
   * Returns the stream type for audio playback.
   */
  public @C.StreamType int getAudioStreamType() {
    return audioStreamType;
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
   * Sets a listener to receive video events.
   *
   * @param listener The listener.
   */
  public void setVideoListener(VideoListener listener) {
    videoListener = listener;
  }

  /**
   * Clears the listener receiving video events if it matches the one passed. Else does nothing.
   *
   * @param listener The listener to clear.
   */
  public void clearVideoListener(VideoListener listener) {
    if (videoListener == listener) {
      videoListener = null;
    }
  }

  /**
   * Sets an output to receive text events.
   *
   * @param output The output.
   */
  public void setTextOutput(TextRenderer.Output output) {
    textOutput = output;
  }

  /**
   * Clears the output receiving text events if it matches the one passed. Else does nothing.
   *
   * @param output The output to clear.
   */
  public void clearTextOutput(TextRenderer.Output output) {
    if (textOutput == output) {
      textOutput = null;
    }
  }

  /**
   * Sets a listener to receive metadata events.
   *
   * @param output The output.
   */
  public void setMetadataOutput(MetadataRenderer.Output output) {
    metadataOutput = output;
  }

  /**
   * Clears the output receiving metadata events if it matches the one passed. Else does nothing.
   *
   * @param output The output to clear.
   */
  public void clearMetadataOutput(MetadataRenderer.Output output) {
    if (metadataOutput == output) {
      metadataOutput = null;
    }
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
  public void addListener(EventListener listener) {
    player.addListener(listener);
  }

  @Override
  public void removeListener(EventListener listener) {
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
      // If we created this surface, we are responsible for releasing it.
      if (this.ownsSurface) {
        this.surface.release();
      }
      // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
      player.blockingSendMessages(messages);
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
      if (videoListener != null) {
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
      if (videoListener != null && SimpleExoPlayer.this.surface == surface) {
        videoListener.onRenderedFirstFrame();
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
      if (textOutput != null) {
        textOutput.onCues(cues);
      }
    }

    // MetadataRenderer.Output implementation

    @Override
    public void onMetadata(Metadata metadata) {
      if (metadataOutput != null) {
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
      if (videoListener.onSurfaceDestroyed(surfaceTexture)) {
        return false;
      }
      setVideoSurfaceInternal(null, true);
      needSetSurface = true;
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      videoListener.onSurfaceTextureUpdated(surfaceTexture);
    }

  }

}
