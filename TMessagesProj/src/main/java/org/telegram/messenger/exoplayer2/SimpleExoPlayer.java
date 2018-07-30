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
import org.telegram.messenger.exoplayer2.analytics.AnalyticsCollector;
import org.telegram.messenger.exoplayer2.analytics.AnalyticsListener;
import org.telegram.messenger.exoplayer2.audio.AudioAttributes;
import org.telegram.messenger.exoplayer2.audio.AudioRendererEventListener;
import org.telegram.messenger.exoplayer2.decoder.DecoderCounters;
import org.telegram.messenger.exoplayer2.drm.DefaultDrmSessionManager;
import org.telegram.messenger.exoplayer2.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer2.drm.FrameworkMediaCrypto;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.metadata.MetadataOutput;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.text.Cue;
import org.telegram.messenger.exoplayer2.text.TextOutput;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelector;
import org.telegram.messenger.exoplayer2.util.Clock;
import org.telegram.messenger.exoplayer2.util.Util;
import org.telegram.messenger.exoplayer2.video.VideoRendererEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link ExoPlayer} implementation that uses default {@link Renderer} components. Instances can
 * be obtained from {@link ExoPlayerFactory}.
 */
@TargetApi(16)
public class SimpleExoPlayer implements ExoPlayer, Player.VideoComponent, Player.TextComponent {

  /** @deprecated Use {@link org.telegram.messenger.exoplayer2.video.VideoListener}. */
  @Deprecated
  public interface VideoListener extends org.telegram.messenger.exoplayer2.video.VideoListener {}

  private static final String TAG = "SimpleExoPlayer";

  protected final Renderer[] renderers;

  private final ExoPlayer player;
  private final Handler eventHandler;
  private final ComponentListener componentListener;
  private final CopyOnWriteArraySet<org.telegram.messenger.exoplayer2.video.VideoListener>
      videoListeners;
  private final CopyOnWriteArraySet<TextOutput> textOutputs;
  private final CopyOnWriteArraySet<MetadataOutput> metadataOutputs;
  private final CopyOnWriteArraySet<VideoRendererEventListener> videoDebugListeners;
  private final CopyOnWriteArraySet<AudioRendererEventListener> audioDebugListeners;
  private final AnalyticsCollector analyticsCollector;

  private Format videoFormat;
  private Format audioFormat;

  private boolean needSetSurface = true;

  private Surface surface;
  private boolean ownsSurface;
  @C.VideoScalingMode
  private int videoScalingMode;
  private SurfaceHolder surfaceHolder;
  private TextureView textureView;
  private DecoderCounters videoDecoderCounters;
  private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float audioVolume;
  private MediaSource mediaSource;
  private List<Cue> currentCues;

  /**
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  protected SimpleExoPlayer(
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    this(
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        new AnalyticsCollector.Factory());
  }

  /**
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   */
  protected SimpleExoPlayer(
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AnalyticsCollector.Factory analyticsCollectorFactory) {
    this(
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        analyticsCollectorFactory,
        Clock.DEFAULT);
  }

  /**
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   * @param clock The {@link Clock} that will be used by the instance. Should always be {@link
   *     Clock#DEFAULT}, unless the player is being used from a test.
   */
  protected SimpleExoPlayer(
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AnalyticsCollector.Factory analyticsCollectorFactory,
      Clock clock) {
    componentListener = new ComponentListener();
    videoListeners = new CopyOnWriteArraySet<>();
    textOutputs = new CopyOnWriteArraySet<>();
    metadataOutputs = new CopyOnWriteArraySet<>();
    videoDebugListeners = new CopyOnWriteArraySet<>();
    audioDebugListeners = new CopyOnWriteArraySet<>();
    Looper eventLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
    eventHandler = new Handler(eventLooper);
    renderers =
        renderersFactory.createRenderers(
            eventHandler,
            componentListener,
            componentListener,
            componentListener,
            componentListener,
            drmSessionManager);

    // Set initial values.
    audioVolume = 1;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    audioAttributes = AudioAttributes.DEFAULT;
    videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
    currentCues = Collections.emptyList();

    // Build the player and associated objects.
    player = createExoPlayerImpl(renderers, trackSelector, loadControl, clock);
    analyticsCollector = analyticsCollectorFactory.createAnalyticsCollector(player, clock);
    addListener(analyticsCollector);
    videoDebugListeners.add(analyticsCollector);
    audioDebugListeners.add(analyticsCollector);
    addMetadataOutput(analyticsCollector);
    if (drmSessionManager instanceof DefaultDrmSessionManager) {
      ((DefaultDrmSessionManager) drmSessionManager).addListener(eventHandler, analyticsCollector);
    }
  }

  @Override
  public VideoComponent getVideoComponent() {
    return this;
  }

  @Override
  public TextComponent getTextComponent() {
    return this;
  }

  /**
   * Sets the video scaling mode.
   *
   * <p>Note that the scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer}
   * is enabled and if the output surface is owned by a {@link android.view.SurfaceView}.
   *
   * @param videoScalingMode The video scaling mode.
   */
  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    this.videoScalingMode = videoScalingMode;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_SCALING_MODE)
            .setPayload(videoScalingMode)
            .send();
      }
    }
  }

  @Override
  public @C.VideoScalingMode int getVideoScalingMode() {
    return videoScalingMode;
  }

  @Override
  public void clearVideoSurface() {
    setVideoSurface(null);
  }

  @Override
  public void setVideoSurface(Surface surface) {
    removeSurfaceCallbacks();
    setVideoSurfaceInternal(surface, false);
  }

  @Override
  public void clearVideoSurface(Surface surface) {
    if (surface != null && surface == this.surface) {
      setVideoSurface(null);
    }
  }

  @Override
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

  @Override
  public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      setVideoSurfaceHolder(null);
    }
  }

  @Override
  public void setVideoSurfaceView(SurfaceView surfaceView) {
    setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void clearVideoSurfaceView(SurfaceView surfaceView) {
    clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
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

  @Override
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

  /** Returns the {@link AnalyticsCollector} used for collecting analytics events. */
  public AnalyticsCollector getAnalyticsCollector() {
    return analyticsCollector;
  }

  /**
   * Adds an {@link AnalyticsListener} to receive analytics events.
   *
   * @param listener The listener to be added.
   */
  public void addAnalyticsListener(AnalyticsListener listener) {
    analyticsCollector.addListener(listener);
  }

  /**
   * Removes an {@link AnalyticsListener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeAnalyticsListener(AnalyticsListener listener) {
    analyticsCollector.removeListener(listener);
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
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_AUDIO_ATTRIBUTES)
            .setPayload(audioAttributes)
            .send();
      }
    }
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
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player.createMessage(renderer).setType(C.MSG_SET_VOLUME).setPayload(audioVolume).send();
      }
    }
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

  @Override
  public void addVideoListener(org.telegram.messenger.exoplayer2.video.VideoListener listener) {
    videoListeners.add(listener);
  }

  @Override
  public void removeVideoListener(org.telegram.messenger.exoplayer2.video.VideoListener listener) {
    videoListeners.remove(listener);
  }

  /**
   * Sets a listener to receive video events, removing all existing listeners.
   *
   * @param listener The listener.
   * @deprecated Use {@link #addVideoListener(org.telegram.messenger.exoplayer2.video.VideoListener)}.
   */
  @Deprecated
  public void setVideoListener(VideoListener listener) {
    videoListeners.clear();
    if (listener != null) {
      addVideoListener(listener);
    }
  }

  /**
   * Equivalent to {@link #removeVideoListener(org.telegram.messenger.exoplayer2.video.VideoListener)}.
   *
   * @param listener The listener to clear.
   * @deprecated Use {@link
   *     #removeVideoListener(org.telegram.messenger.exoplayer2.video.VideoListener)}.
   */
  @Deprecated
  public void clearVideoListener(VideoListener listener) {
    removeVideoListener(listener);
  }

  @Override
  public void addTextOutput(TextOutput listener) {
    if (!currentCues.isEmpty()) {
      listener.onCues(currentCues);
    }
    textOutputs.add(listener);
  }

  @Override
  public void removeTextOutput(TextOutput listener) {
    textOutputs.remove(listener);
  }

  /**
   * Sets an output to receive text events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addTextOutput(TextOutput)}.
   */
  @Deprecated
  public void setTextOutput(TextOutput output) {
    textOutputs.clear();
    if (output != null) {
      addTextOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeTextOutput(TextOutput)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeTextOutput(TextOutput)}.
   */
  @Deprecated
  public void clearTextOutput(TextOutput output) {
    removeTextOutput(output);
  }

  /**
   * Adds a {@link MetadataOutput} to receive metadata.
   *
   * @param listener The output to register.
   */
  public void addMetadataOutput(MetadataOutput listener) {
    metadataOutputs.add(listener);
  }

  /**
   * Removes a {@link MetadataOutput}.
   *
   * @param listener The output to remove.
   */
  public void removeMetadataOutput(MetadataOutput listener) {
    metadataOutputs.remove(listener);
  }

  /**
   * Sets an output to receive metadata events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addMetadataOutput(MetadataOutput)}.
   */
  @Deprecated
  public void setMetadataOutput(MetadataOutput output) {
    metadataOutputs.retainAll(Collections.singleton(analyticsCollector));
    if (output != null) {
      addMetadataOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeMetadataOutput(MetadataOutput)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeMetadataOutput(MetadataOutput)}.
   */
  @Deprecated
  public void clearMetadataOutput(MetadataOutput output) {
    removeMetadataOutput(output);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void setVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.retainAll(Collections.singleton(analyticsCollector));
    if (listener != null) {
      addVideoDebugListener(listener);
    }
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void addVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.add(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} and {@link
   *     #removeAnalyticsListener(AnalyticsListener)} to get more detailed debug information.
   */
  @Deprecated
  public void removeVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.remove(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void setAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.retainAll(Collections.singleton(analyticsCollector));
    if (listener != null) {
      addAudioDebugListener(listener);
    }
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void addAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.add(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} and {@link
   *     #removeAnalyticsListener(AnalyticsListener)} to get more detailed debug information.
   */
  @Deprecated
  public void removeAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.remove(listener);
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
  public ExoPlaybackException getPlaybackError() {
    return player.getPlaybackError();
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    prepare(mediaSource, /* resetPosition= */ true, /* resetState= */ true);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    if (this.mediaSource != mediaSource) {
      if (this.mediaSource != null) {
        this.mediaSource.removeEventListener(analyticsCollector);
        analyticsCollector.resetForNewMediaSource();
      }
      mediaSource.addEventListener(eventHandler, analyticsCollector);
      this.mediaSource = mediaSource;
    }
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
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    player.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return player.getShuffleModeEnabled();
  }

  @Override
  public boolean isLoading() {
    return player.isLoading();
  }

  @Override
  public void seekToDefaultPosition() {
    analyticsCollector.notifySeekStarted();
    player.seekToDefaultPosition();
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    analyticsCollector.notifySeekStarted();
    player.seekToDefaultPosition(windowIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    analyticsCollector.notifySeekStarted();
    player.seekTo(positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    analyticsCollector.notifySeekStarted();
    player.seekTo(windowIndex, positionMs);
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return player.getPlaybackParameters();
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    player.setSeekParameters(seekParameters);
  }

  @Override
  public @Nullable Object getCurrentTag() {
    return player.getCurrentTag();
  }

  @Override
  public void stop() {
    stop(/* reset= */ false);
  }

  @Override
  public void stop(boolean reset) {
    player.stop(reset);
    if (mediaSource != null) {
      mediaSource.removeEventListener(analyticsCollector);
      mediaSource = null;
      analyticsCollector.resetForNewMediaSource();
    }
    currentCues = Collections.emptyList();
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
    if (mediaSource != null) {
      mediaSource.removeEventListener(analyticsCollector);
    }
    currentCues = Collections.emptyList();
  }

  @Override
  public void sendMessages(ExoPlayerMessage... messages) {
    player.sendMessages(messages);
  }

  @Override
  public PlayerMessage createMessage(PlayerMessage.Target target) {
    return player.createMessage(target);
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
  public int getNextWindowIndex() {
    return player.getNextWindowIndex();
  }

  @Override
  public int getPreviousWindowIndex() {
    return player.getPreviousWindowIndex();
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

  /**
   * Creates the {@link ExoPlayer} implementation used by this instance.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param clock The {@link Clock} that will be used by this instance.
   * @return A new {@link ExoPlayer} instance.
   */
  protected ExoPlayer createExoPlayerImpl(
      Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl, Clock clock) {
    return new ExoPlayerImpl(renderers, trackSelector, loadControl, clock);
  }

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
    List<PlayerMessage> messages = new ArrayList<>();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages.add(
            player.createMessage(renderer).setType(C.MSG_SET_SURFACE).setPayload(surface).send());
      }
    }
    if (this.surface != null && this.surface != surface) {
      // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
      try {
        for (PlayerMessage message : messages) {
          message.blockUntilDelivered();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      // If we created the previous surface, we are responsible for releasing it.
      if (this.ownsSurface) {
        this.surface.release();
      }
    }
    this.surface = surface;
    this.ownsSurface = ownsSurface;
  }

  private final class ComponentListener implements VideoRendererEventListener,
      AudioRendererEventListener, TextOutput, MetadataOutput, SurfaceHolder.Callback,
      TextureView.SurfaceTextureListener {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoEnabled(counters);
      }
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
      videoFormat = format;
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoInputFormatChanged(format);
      }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onDroppedFrames(count, elapsed);
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      for (org.telegram.messenger.exoplayer2.video.VideoListener videoListener : videoListeners) {
        videoListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
      if (SimpleExoPlayer.this.surface == surface) {
        for (org.telegram.messenger.exoplayer2.video.VideoListener videoListener : videoListeners) {
          videoListener.onRenderedFirstFrame();
        }
      }
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onRenderedFirstFrame(surface);
      }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoDecoderCounters = null;
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioEnabled(counters);
      }
    }

    @Override
    public void onAudioSessionId(int sessionId) {
      audioSessionId = sessionId;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioSessionId(sessionId);
      }
    }

    @Override
    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
      audioFormat = format;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioInputFormatChanged(format);
      }
    }

    @Override
    public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs,
        long elapsedSinceLastFeedMs) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioSinkUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioDisabled(counters);
      }
      audioFormat = null;
      audioDecoderCounters = null;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    }

    // TextOutput implementation

    @Override
    public void onCues(List<Cue> cues) {
      currentCues = cues;
      for (TextOutput textOutput : textOutputs) {
        textOutput.onCues(cues);
      }
    }

    // MetadataOutput implementation

    @Override
    public void onMetadata(Metadata metadata) {
      for (MetadataOutput metadataOutput : metadataOutputs) {
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
      for (org.telegram.messenger.exoplayer2.video.VideoListener videoListener : videoListeners) {
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
      for (org.telegram.messenger.exoplayer2.video.VideoListener videoListener : videoListeners) {
        videoListener.onSurfaceTextureUpdated(surfaceTexture);
      }
      // Do nothing.
    }

  }

}
