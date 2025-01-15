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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.opengl.EGLContext;
import android.os.Looper;
import android.os.Process;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.DefaultAnalyticsCollector;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.telegram.messenger.DispatchQueue;

import java.util.List;

/**
 * An extensible media player that plays {@link MediaSource}s. Instances can be obtained from {@link
 * Builder}.
 *
 * <h2>Player components</h2>
 *
 * <p>ExoPlayer is designed to make few assumptions about (and hence impose few restrictions on) the
 * type of the media being played, how and where it is stored, and how it is rendered. Rather than
 * implementing the loading and rendering of media directly, ExoPlayer implementations delegate this
 * work to components that are injected when a player is created or when it's prepared for playback.
 * Components common to all ExoPlayer implementations are:
 *
 * <ul>
 *   <li><b>{@link MediaSource MediaSources}</b> that define the media to be played, load the media,
 *       and from which the loaded media can be read. MediaSources are created from {@link MediaItem
 *       MediaItems} by the {@link MediaSource.Factory} injected into the player {@link
 *       Builder#setMediaSourceFactory Builder}, or can be added directly by methods like {@link
 *       #setMediaSource(MediaSource)}. The library provides a {@link DefaultMediaSourceFactory} for
 *       progressive media files, DASH, SmoothStreaming and HLS, which also includes functionality
 *       for side-loading subtitle files and clipping media.
 *   <li><b>{@link Renderer}</b>s that render individual components of the media. The library
 *       provides default implementations for common media types ({@link MediaCodecVideoRenderer},
 *       {@link MediaCodecAudioRenderer}, {@link TextRenderer} and {@link MetadataRenderer}). A
 *       Renderer consumes media from the MediaSource being played. Renderers are injected when the
 *       player is created. The number of renderers and their respective track types can be obtained
 *       by calling {@link #getRendererCount()} and {@link #getRendererType(int)}.
 *   <li>A <b>{@link TrackSelector}</b> that selects tracks provided by the MediaSource to be
 *       consumed by each of the available Renderers. The library provides a default implementation
 *       ({@link DefaultTrackSelector}) suitable for most use cases. A TrackSelector is injected
 *       when the player is created.
 *   <li>A <b>{@link LoadControl}</b> that controls when the MediaSource buffers more media, and how
 *       much media is buffered. The library provides a default implementation ({@link
 *       DefaultLoadControl}) suitable for most use cases. A LoadControl is injected when the player
 *       is created.
 * </ul>
 *
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
 * <h2>Threading model</h2>
 *
 * <p>The figure below shows ExoPlayer's threading model.
 *
 * <p style="align:center"><img src="doc-files/exoplayer-threading-model.svg" alt="ExoPlayer's
 * threading model">
 *
 * <ul>
 *   <li>ExoPlayer instances must be accessed from a single application thread unless indicated
 *       otherwise. For the vast majority of cases this should be the application's main thread.
 *       Using the application's main thread is also a requirement when using ExoPlayer's UI
 *       components or the IMA extension. The thread on which an ExoPlayer instance must be accessed
 *       can be explicitly specified by passing a `Looper` when creating the player. If no `Looper`
 *       is specified, then the `Looper` of the thread that the player is created on is used, or if
 *       that thread does not have a `Looper`, the `Looper` of the application's main thread is
 *       used. In all cases the `Looper` of the thread from which the player must be accessed can be
 *       queried using {@link #getApplicationLooper()}.
 *   <li>Registered listeners are called on the thread associated with {@link
 *       #getApplicationLooper()}. Note that this means registered listeners are called on the same
 *       thread which must be used to access the player.
 *   <li>An internal playback thread is responsible for playback. Injected player components such as
 *       Renderers, MediaSources, TrackSelectors and LoadControls are called by the player on this
 *       thread.
 *   <li>When the application performs an operation on the player, for example a seek, a message is
 *       delivered to the internal playback thread via a message queue. The internal playback thread
 *       consumes messages from the queue and performs the corresponding operations. Similarly, when
 *       a playback event occurs on the internal playback thread, a message is delivered to the
 *       application thread via a second message queue. The application thread consumes messages
 *       from the queue, updating the application visible state and calling corresponding listener
 *       methods.
 *   <li>Injected player components may use additional background threads. For example a MediaSource
 *       may use background threads to load data. These are implementation specific.
 * </ul>
 */
public interface ExoPlayer extends Player {

    void setWorkerQueue(DispatchQueue dispatchQueue);

    /**
   * @deprecated Use {@link ExoPlayer}, as the {@link AudioComponent} methods are defined by that
   *     interface.
   */
  @Deprecated
  interface AudioComponent {

    /**
     * @deprecated Use {@link ExoPlayer#setAudioAttributes(AudioAttributes, boolean)} instead.
     */
    @Deprecated
    void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);

    /**
     * @deprecated Use {@link Player#getAudioAttributes()} instead.
     */
    @Deprecated
    AudioAttributes getAudioAttributes();

    /**
     * @deprecated Use {@link ExoPlayer#setAudioSessionId(int)} instead.
     */
    @Deprecated
    void setAudioSessionId(int audioSessionId);

    /**
     * @deprecated Use {@link ExoPlayer#getAudioSessionId()} instead.
     */
    @Deprecated
    int getAudioSessionId();

    /**
     * @deprecated Use {@link ExoPlayer#setAuxEffectInfo(AuxEffectInfo)} instead.
     */
    @Deprecated
    void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

    /**
     * @deprecated Use {@link ExoPlayer#clearAuxEffectInfo()} instead.
     */
    @Deprecated
    void clearAuxEffectInfo();

    /**
     * @deprecated Use {@link Player#setVolume(float)} instead.
     */
    @Deprecated
    void setVolume(float audioVolume);

    /**
     * @deprecated Use {@link Player#getVolume()} instead.
     */
    @Deprecated
    float getVolume();

    /**
     * @deprecated Use {@link ExoPlayer#setSkipSilenceEnabled(boolean)} instead.
     */
    @Deprecated
    void setSkipSilenceEnabled(boolean skipSilenceEnabled);

    /**
     * @deprecated Use {@link ExoPlayer#getSkipSilenceEnabled()} instead.
     */
    @Deprecated
    boolean getSkipSilenceEnabled();
  }

  /**
   * @deprecated Use {@link ExoPlayer}, as the {@link VideoComponent} methods are defined by that
   *     interface.
   */
  @Deprecated
  interface VideoComponent {

    /**
     * @deprecated Use {@link ExoPlayer#setVideoScalingMode(int)} instead.
     */
    @Deprecated
    void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode);

    /**
     * @deprecated Use {@link ExoPlayer#getVideoScalingMode()} instead.
     */
    @Deprecated
    @C.VideoScalingMode
    int getVideoScalingMode();

    /**
     * @deprecated Use {@link ExoPlayer#setVideoChangeFrameRateStrategy(int)} instead.
     */
    @Deprecated
    void setVideoChangeFrameRateStrategy(
        @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy);

    /**
     * @deprecated Use {@link ExoPlayer#getVideoChangeFrameRateStrategy()} instead.
     */
    @Deprecated
    @C.VideoChangeFrameRateStrategy
    int getVideoChangeFrameRateStrategy();

    /**
     * @deprecated Use {@link ExoPlayer#setVideoFrameMetadataListener(VideoFrameMetadataListener)}
     *     instead.
     */
    @Deprecated
    void setVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * @deprecated Use {@link ExoPlayer#clearVideoFrameMetadataListener(VideoFrameMetadataListener)}
     *     instead.
     */
    @Deprecated
    void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * @deprecated Use {@link ExoPlayer#setCameraMotionListener(CameraMotionListener)} instead.
     */
    @Deprecated
    void setCameraMotionListener(CameraMotionListener listener);

    /**
     * @deprecated Use {@link ExoPlayer#clearCameraMotionListener(CameraMotionListener)} instead.
     */
    @Deprecated
    void clearCameraMotionListener(CameraMotionListener listener);

    /**
     * @deprecated Use {@link Player#clearVideoSurface()} instead.
     */
    @Deprecated
    void clearVideoSurface();

    /**
     * @deprecated Use {@link Player#clearVideoSurface(Surface)} instead.
     */
    @Deprecated
    void clearVideoSurface(@Nullable Surface surface);

    /**
     * @deprecated Use {@link Player#setVideoSurface(Surface)} instead.
     */
    @Deprecated
    void setVideoSurface(@Nullable Surface surface);

    /**
     * @deprecated Use {@link Player#setVideoSurfaceHolder(SurfaceHolder)} instead.
     */
    @Deprecated
    void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    /**
     * @deprecated Use {@link Player#clearVideoSurfaceHolder(SurfaceHolder)} instead.
     */
    @Deprecated
    void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    /**
     * @deprecated Use {@link Player#setVideoSurfaceView(SurfaceView)} instead.
     */
    @Deprecated
    void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

    /**
     * @deprecated Use {@link Player#clearVideoSurfaceView(SurfaceView)} instead.
     */
    @Deprecated
    void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

    /**
     * @deprecated Use {@link Player#setVideoTextureView(TextureView)} instead.
     */
    @Deprecated
    void setVideoTextureView(@Nullable TextureView textureView);

    /**
     * @deprecated Use {@link Player#clearVideoTextureView(TextureView)} instead.
     */
    @Deprecated
    void clearVideoTextureView(@Nullable TextureView textureView);

    /**
     * @deprecated Use {@link Player#getVideoSize()} instead.
     */
    @Deprecated
    VideoSize getVideoSize();
  }

  /**
   * @deprecated Use {@link Player}, as the {@link TextComponent} methods are defined by that
   *     interface.
   */
  @Deprecated
  interface TextComponent {

    /**
     * @deprecated Use {@link Player#getCurrentCues()} instead.
     */
    @Deprecated
    CueGroup getCurrentCues();
  }

  /**
   * @deprecated Use {@link Player}, as the {@link DeviceComponent} methods are defined by that
   *     interface.
   */
  @Deprecated
  interface DeviceComponent {

    /**
     * @deprecated Use {@link Player#getDeviceInfo()} instead.
     */
    @Deprecated
    DeviceInfo getDeviceInfo();

    /**
     * @deprecated Use {@link Player#getDeviceVolume()} instead.
     */
    @Deprecated
    int getDeviceVolume();

    /**
     * @deprecated Use {@link Player#isDeviceMuted()} instead.
     */
    @Deprecated
    boolean isDeviceMuted();

    /**
     * @deprecated Use {@link Player#setDeviceVolume(int)} instead.
     */
    @Deprecated
    void setDeviceVolume(int volume);

    /**
     * @deprecated Use {@link Player#increaseDeviceVolume()} instead.
     */
    @Deprecated
    void increaseDeviceVolume();

    /**
     * @deprecated Use {@link Player#decreaseDeviceVolume()} instead.
     */
    @Deprecated
    void decreaseDeviceVolume();

    /**
     * @deprecated Use {@link Player#setDeviceMuted(boolean)} instead.
     */
    @Deprecated
    void setDeviceMuted(boolean muted);
  }

  /**
   * A listener for audio offload events.
   *
   * <p>This class is experimental, and might be renamed, moved or removed in a future release.
   */
  interface AudioOffloadListener {
    /**
     * Called when the player has started or stopped offload scheduling using {@link
     * #experimentalSetOffloadSchedulingEnabled(boolean)}.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     */
    default void onExperimentalOffloadSchedulingEnabledChanged(boolean offloadSchedulingEnabled) {}

    /**
     * Called when the player has started or finished sleeping for offload.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     */
    default void onExperimentalSleepingForOffloadChanged(boolean sleepingForOffload) {}

    /**
     * Called when the value of {@link AudioTrack#isOffloadedPlayback} changes.
     *
     * <p>This should not be generally required to be acted upon. But when offload is critical for
     * efficiency, or audio features (gapless, playback speed), this will let the app know.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     */
    default void onExperimentalOffloadedPlayback(boolean offloadedPlayback) {}
  }

  /**
   * A builder for {@link ExoPlayer} instances.
   *
   * <p>See {@link #Builder(Context)} for the list of default values.
   */
  @SuppressWarnings("deprecation")
  final class Builder {

    /* package */ final Context context;
    public EGLContext eglContext;

    /* package */ Clock clock;
    /* package */ long foregroundModeTimeoutMs;
    /* package */ Supplier<RenderersFactory> renderersFactorySupplier;
    /* package */ Supplier<MediaSource.Factory> mediaSourceFactorySupplier;
    /* package */ Supplier<TrackSelector> trackSelectorSupplier;
    /* package */ Supplier<LoadControl> loadControlSupplier;
    /* package */ Supplier<BandwidthMeter> bandwidthMeterSupplier;
    /* package */ Function<Clock, AnalyticsCollector> analyticsCollectorFunction;
    /* package */ Looper looper;
    @Nullable /* package */ PriorityTaskManager priorityTaskManager;
    /* package */ AudioAttributes audioAttributes;
    /* package */ boolean handleAudioFocus;
    @C.WakeMode /* package */ int wakeMode;
    /* package */ boolean handleAudioBecomingNoisy;
    /* package */ boolean skipSilenceEnabled;
    @C.VideoScalingMode /* package */ int videoScalingMode;
    @C.VideoChangeFrameRateStrategy /* package */ int videoChangeFrameRateStrategy;
    /* package */ boolean useLazyPreparation;
    /* package */ SeekParameters seekParameters;
    /* package */ long seekBackIncrementMs;
    /* package */ long seekForwardIncrementMs;
    /* package */ LivePlaybackSpeedControl livePlaybackSpeedControl;
    /* package */ long releaseTimeoutMs;
    /* package */ long detachSurfaceTimeoutMs;
    /* package */ boolean pauseAtEndOfMediaItems;
    /* package */ boolean usePlatformDiagnostics;
    @Nullable /* package */ Looper playbackLooper;
    /* package */ boolean buildCalled;

    /**
     * Creates a builder.
     *
     * <p>Use {@link #Builder(Context, RenderersFactory)}, {@link #Builder(Context,
     * MediaSource.Factory)} or {@link #Builder(Context, RenderersFactory, MediaSource.Factory)}
     * instead, if you intend to provide a custom {@link RenderersFactory}, {@link
     * ExtractorsFactory} or {@link DefaultMediaSourceFactory}. This is to ensure that ProGuard or
     * R8 can remove ExoPlayer's {@link DefaultRenderersFactory}, {@link DefaultExtractorsFactory}
     * and {@link DefaultMediaSourceFactory} from the APK.
     *
     * <p>The builder uses the following default values:
     *
     * <ul>
     *   <li>{@link RenderersFactory}: {@link DefaultRenderersFactory}
     *   <li>{@link TrackSelector}: {@link DefaultTrackSelector}
     *   <li>{@link MediaSource.Factory}: {@link DefaultMediaSourceFactory}
     *   <li>{@link LoadControl}: {@link DefaultLoadControl}
     *   <li>{@link BandwidthMeter}: {@link DefaultBandwidthMeter#getSingletonInstance(Context)}
     *   <li>{@link LivePlaybackSpeedControl}: {@link DefaultLivePlaybackSpeedControl}
     *   <li>{@link Looper}: The {@link Looper} associated with the current thread, or the {@link
     *       Looper} of the application's main thread if the current thread doesn't have a {@link
     *       Looper}
     *   <li>{@link AnalyticsCollector}: {@link AnalyticsCollector} with {@link Clock#DEFAULT}
     *   <li>{@link PriorityTaskManager}: {@code null} (not used)
     *   <li>{@link AudioAttributes}: {@link AudioAttributes#DEFAULT}, not handling audio focus
     *   <li>{@link C.WakeMode}: {@link C#WAKE_MODE_NONE}
     *   <li>{@code handleAudioBecomingNoisy}: {@code false}
     *   <li>{@code skipSilenceEnabled}: {@code false}
     *   <li>{@link C.VideoScalingMode}: {@link C#VIDEO_SCALING_MODE_DEFAULT}
     *   <li>{@link C.VideoChangeFrameRateStrategy}: {@link
     *       C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS}
     *   <li>{@code useLazyPreparation}: {@code true}
     *   <li>{@link SeekParameters}: {@link SeekParameters#DEFAULT}
     *   <li>{@code seekBackIncrementMs}: {@link C#DEFAULT_SEEK_BACK_INCREMENT_MS}
     *   <li>{@code seekForwardIncrementMs}: {@link C#DEFAULT_SEEK_FORWARD_INCREMENT_MS}
     *   <li>{@code releaseTimeoutMs}: {@link #DEFAULT_RELEASE_TIMEOUT_MS}
     *   <li>{@code detachSurfaceTimeoutMs}: {@link #DEFAULT_DETACH_SURFACE_TIMEOUT_MS}
     *   <li>{@code pauseAtEndOfMediaItems}: {@code false}
     *   <li>{@code usePlatformDiagnostics}: {@code true}
     *   <li>{@link Clock}: {@link Clock#DEFAULT}
     *   <li>{@code playbackLooper}: {@code null} (create new thread)
     * </ul>
     *
     * @param context A {@link Context}.
     */
    public Builder(Context context) {
      this(
          context,
          () -> new DefaultRenderersFactory(context),
          () -> new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory()));
    }

    /**
     * Creates a builder with a custom {@link RenderersFactory}.
     *
     * <p>See {@link #Builder(Context)} for a list of default values.
     *
     * <p>Note that this constructor is only useful to try and ensure that ExoPlayer's {@link
     * DefaultRenderersFactory} can be removed by ProGuard or R8.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     */
    public Builder(Context context, RenderersFactory renderersFactory) {
      this(
          context,
          () -> renderersFactory,
          () -> new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory()));
      checkNotNull(renderersFactory);
    }

    /**
     * Creates a builder with a custom {@link MediaSource.Factory}.
     *
     * <p>See {@link #Builder(Context)} for a list of default values.
     *
     * <p>Note that this constructor is only useful to try and ensure that ExoPlayer's {@link
     * DefaultMediaSourceFactory} (and therefore {@link DefaultExtractorsFactory}) can be removed by
     * ProGuard or R8.
     *
     * @param context A {@link Context}.
     * @param mediaSourceFactory A factory for creating a {@link MediaSource} from a {@link
     *     MediaItem}.
     */
    public Builder(Context context, MediaSource.Factory mediaSourceFactory) {
      this(context, () -> new DefaultRenderersFactory(context), () -> mediaSourceFactory);
      checkNotNull(mediaSourceFactory);
    }

    /**
     * Creates a builder with a custom {@link RenderersFactory} and {@link MediaSource.Factory}.
     *
     * <p>See {@link #Builder(Context)} for a list of default values.
     *
     * <p>Note that this constructor is only useful to try and ensure that ExoPlayer's {@link
     * DefaultRenderersFactory}, {@link DefaultMediaSourceFactory} (and therefore {@link
     * DefaultExtractorsFactory}) can be removed by ProGuard or R8.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     * @param mediaSourceFactory A factory for creating a {@link MediaSource} from a {@link
     *     MediaItem}.
     */
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        MediaSource.Factory mediaSourceFactory) {
      this(context, () -> renderersFactory, () -> mediaSourceFactory);
      checkNotNull(renderersFactory);
      checkNotNull(mediaSourceFactory);
    }

    /**
     * Creates a builder with the specified custom components.
     *
     * <p>Note that this constructor is only useful to try and ensure that ExoPlayer's default
     * components can be removed by ProGuard or R8.
     *
     * @param context A {@link Context}.
     * @param renderersFactory A factory for creating {@link Renderer Renderers} to be used by the
     *     player.
     * @param mediaSourceFactory A {@link MediaSource.Factory}.
     * @param trackSelector A {@link TrackSelector}.
     * @param loadControl A {@link LoadControl}.
     * @param bandwidthMeter A {@link BandwidthMeter}.
     * @param analyticsCollector An {@link AnalyticsCollector}.
     */
    public Builder(
        Context context,
        RenderersFactory renderersFactory,
        MediaSource.Factory mediaSourceFactory,
        TrackSelector trackSelector,
        LoadControl loadControl,
        BandwidthMeter bandwidthMeter,
        AnalyticsCollector analyticsCollector) {
      this(
          context,
          () -> renderersFactory,
          () -> mediaSourceFactory,
          () -> trackSelector,
          () -> loadControl,
          () -> bandwidthMeter,
          (clock) -> analyticsCollector);
      checkNotNull(renderersFactory);
      checkNotNull(mediaSourceFactory);
      checkNotNull(trackSelector);
      checkNotNull(bandwidthMeter);
      checkNotNull(analyticsCollector);
    }

    private Builder(
        Context context,
        Supplier<RenderersFactory> renderersFactorySupplier,
        Supplier<MediaSource.Factory> mediaSourceFactorySupplier) {
      this(
          context,
          renderersFactorySupplier,
          mediaSourceFactorySupplier,
          () -> new DefaultTrackSelector(context),
          DefaultLoadControl::new,
          () -> DefaultBandwidthMeter.getSingletonInstance(context),
          DefaultAnalyticsCollector::new);
    }

    private Builder(
        Context context,
        Supplier<RenderersFactory> renderersFactorySupplier,
        Supplier<MediaSource.Factory> mediaSourceFactorySupplier,
        Supplier<TrackSelector> trackSelectorSupplier,
        Supplier<LoadControl> loadControlSupplier,
        Supplier<BandwidthMeter> bandwidthMeterSupplier,
        Function<Clock, AnalyticsCollector> analyticsCollectorFunction) {
      this.context = checkNotNull(context);
      this.renderersFactorySupplier = renderersFactorySupplier;
      this.mediaSourceFactorySupplier = mediaSourceFactorySupplier;
      this.trackSelectorSupplier = trackSelectorSupplier;
      this.loadControlSupplier = loadControlSupplier;
      this.bandwidthMeterSupplier = bandwidthMeterSupplier;
      this.analyticsCollectorFunction = analyticsCollectorFunction;
      looper = Util.getCurrentOrMainLooper();
      audioAttributes = AudioAttributes.DEFAULT;
      wakeMode = C.WAKE_MODE_NONE;
      videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
      videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
      useLazyPreparation = true;
      seekParameters = SeekParameters.DEFAULT;
      seekBackIncrementMs = C.DEFAULT_SEEK_BACK_INCREMENT_MS;
      seekForwardIncrementMs = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
      livePlaybackSpeedControl = new DefaultLivePlaybackSpeedControl.Builder().build();
      clock = Clock.DEFAULT;
      releaseTimeoutMs = DEFAULT_RELEASE_TIMEOUT_MS;
      detachSurfaceTimeoutMs = DEFAULT_DETACH_SURFACE_TIMEOUT_MS;
      usePlatformDiagnostics = true;
    }

    /**
     * Sets a limit on the time a call to {@link #setForegroundMode} can spend. If a call to {@link
     * #setForegroundMode} takes more than {@code timeoutMs} milliseconds to complete, the player
     * will raise an error via {@link Player.Listener#onPlayerError}.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release.
     *
     * @param timeoutMs The time limit in milliseconds.
     */
    @CanIgnoreReturnValue
    public Builder experimentalSetForegroundModeTimeoutMs(long timeoutMs) {
      checkState(!buildCalled);
      foregroundModeTimeoutMs = timeoutMs;
      return this;
    }

    /**
     * Sets the {@link RenderersFactory} that will be used by the player.
     *
     * @param renderersFactory A {@link RenderersFactory}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      checkState(!buildCalled);
      checkNotNull(renderersFactory);
      this.renderersFactorySupplier = () -> renderersFactory;
      return this;
    }

    /**
     * Sets the {@link MediaSource.Factory} that will be used by the player.
     *
     * @param mediaSourceFactory A {@link MediaSource.Factory}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      checkState(!buildCalled);
      checkNotNull(mediaSourceFactory);
      this.mediaSourceFactorySupplier = () -> mediaSourceFactory;
      return this;
    }

    /**
     * Sets the {@link TrackSelector} that will be used by the player.
     *
     * @param trackSelector A {@link TrackSelector}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setTrackSelector(TrackSelector trackSelector) {
      checkState(!buildCalled);
      checkNotNull(trackSelector);
      this.trackSelectorSupplier = () -> trackSelector;
      return this;
    }

    /**
     * Sets the {@link LoadControl} that will be used by the player.
     *
     * @param loadControl A {@link LoadControl}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setLoadControl(LoadControl loadControl) {
      checkState(!buildCalled);
      checkNotNull(loadControl);
      this.loadControlSupplier = () -> loadControl;
      return this;
    }

    /**
     * Sets the {@link BandwidthMeter} that will be used by the player.
     *
     * @param bandwidthMeter A {@link BandwidthMeter}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      checkState(!buildCalled);
      checkNotNull(bandwidthMeter);
      this.bandwidthMeterSupplier = () -> bandwidthMeter;
      return this;
    }

    /**
     * Sets the {@link Looper} that must be used for all calls to the player and that is used to
     * call listeners on.
     *
     * @param looper A {@link Looper}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setLooper(Looper looper) {
      checkState(!buildCalled);
      checkNotNull(looper);
      this.looper = looper;
      return this;
    }

    /**
     * Sets the {@link AnalyticsCollector} that will collect and forward all player events.
     *
     * @param analyticsCollector An {@link AnalyticsCollector}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setAnalyticsCollector(AnalyticsCollector analyticsCollector) {
      checkState(!buildCalled);
      checkNotNull(analyticsCollector);
      this.analyticsCollectorFunction = (clock) -> analyticsCollector;
      return this;
    }

    /**
     * Sets an {@link PriorityTaskManager} that will be used by the player.
     *
     * <p>The priority {@link C#PRIORITY_PLAYBACK} will be set while the player is loading.
     *
     * @param priorityTaskManager A {@link PriorityTaskManager}, or null to not use one.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
      checkState(!buildCalled);
      this.priorityTaskManager = priorityTaskManager;
      return this;
    }

    /**
     * Sets {@link AudioAttributes} that will be used by the player and whether to handle audio
     * focus.
     *
     * <p>If audio focus should be handled, the {@link AudioAttributes#usage} must be {@link
     * C#USAGE_MEDIA} or {@link C#USAGE_GAME}. Other usages will throw an {@link
     * IllegalArgumentException}.
     *
     * @param audioAttributes {@link AudioAttributes}.
     * @param handleAudioFocus Whether the player should handle audio focus.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
      checkState(!buildCalled);
      this.audioAttributes = checkNotNull(audioAttributes);
      this.handleAudioFocus = handleAudioFocus;
      return this;
    }

    /**
     * Sets the {@link C.WakeMode} that will be used by the player.
     *
     * <p>Enabling this feature requires the {@link android.Manifest.permission#WAKE_LOCK}
     * permission. It should be used together with a foreground {@link android.app.Service} for use
     * cases where playback occurs and the screen is off (e.g. background audio playback). It is not
     * useful when the screen will be kept on during playback (e.g. foreground video playback).
     *
     * <p>When enabled, the locks ({@link android.os.PowerManager.WakeLock} / {@link
     * android.net.wifi.WifiManager.WifiLock}) will be held whenever the player is in the {@link
     * #STATE_READY} or {@link #STATE_BUFFERING} states with {@code playWhenReady = true}. The locks
     * held depend on the specified {@link C.WakeMode}.
     *
     * @param wakeMode A {@link C.WakeMode}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setWakeMode(@C.WakeMode int wakeMode) {
      checkState(!buildCalled);
      this.wakeMode = wakeMode;
      return this;
    }

    /**
     * Sets whether the player should pause automatically when audio is rerouted from a headset to
     * device speakers. See the <a
     * href="https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy">
     * audio becoming noisy</a> documentation for more information.
     *
     * @param handleAudioBecomingNoisy Whether the player should pause automatically when audio is
     *     rerouted from a headset to device speakers.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
      checkState(!buildCalled);
      this.handleAudioBecomingNoisy = handleAudioBecomingNoisy;
      return this;
    }

    /**
     * Sets whether silences silences in the audio stream is enabled.
     *
     * @param skipSilenceEnabled Whether skipping silences is enabled.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setSkipSilenceEnabled(boolean skipSilenceEnabled) {
      checkState(!buildCalled);
      this.skipSilenceEnabled = skipSilenceEnabled;
      return this;
    }

    /**
     * Sets the {@link C.VideoScalingMode} that will be used by the player.
     *
     * <p>The scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer} is
     * enabled and if the output surface is owned by a {@link SurfaceView}.
     *
     * @param videoScalingMode A {@link C.VideoScalingMode}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
      checkState(!buildCalled);
      this.videoScalingMode = videoScalingMode;
      return this;
    }

    /**
     * Sets a {@link C.VideoChangeFrameRateStrategy} that will be used by the player when provided
     * with a video output {@link Surface}.
     *
     * <p>The strategy only applies if a {@link MediaCodec}-based video {@link Renderer} is enabled.
     * Applications wishing to use {@link Surface#CHANGE_FRAME_RATE_ALWAYS} should set the mode to
     * {@link C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF} to disable calls to {@link
     * Surface#setFrameRate} from ExoPlayer, and should then call {@link Surface#setFrameRate}
     * directly from application code.
     *
     * @param videoChangeFrameRateStrategy A {@link C.VideoChangeFrameRateStrategy}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setVideoChangeFrameRateStrategy(
        @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy) {
      checkState(!buildCalled);
      this.videoChangeFrameRateStrategy = videoChangeFrameRateStrategy;
      return this;
    }

    /**
     * Sets whether media sources should be initialized lazily.
     *
     * <p>If false, all initial preparation steps (e.g., manifest loads) happen immediately. If
     * true, these initial preparations are triggered only when the player starts buffering the
     * media.
     *
     * @param useLazyPreparation Whether to use lazy preparation.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setUseLazyPreparation(boolean useLazyPreparation) {
      checkState(!buildCalled);
      this.useLazyPreparation = useLazyPreparation;
      return this;
    }

    /**
     * Sets the parameters that control how seek operations are performed.
     *
     * @param seekParameters The {@link SeekParameters}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setSeekParameters(SeekParameters seekParameters) {
      checkState(!buildCalled);
      this.seekParameters = checkNotNull(seekParameters);
      return this;
    }

    /**
     * Sets the {@link #seekBack()} increment.
     *
     * @param seekBackIncrementMs The seek back increment, in milliseconds.
     * @return This builder.
     * @throws IllegalArgumentException If {@code seekBackIncrementMs} is non-positive.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setSeekBackIncrementMs(@IntRange(from = 1) long seekBackIncrementMs) {
      checkArgument(seekBackIncrementMs > 0);
      checkState(!buildCalled);
      this.seekBackIncrementMs = seekBackIncrementMs;
      return this;
    }

    /**
     * Sets the {@link #seekForward()} increment.
     *
     * @param seekForwardIncrementMs The seek forward increment, in milliseconds.
     * @return This builder.
     * @throws IllegalArgumentException If {@code seekForwardIncrementMs} is non-positive.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setSeekForwardIncrementMs(@IntRange(from = 1) long seekForwardIncrementMs) {
      checkArgument(seekForwardIncrementMs > 0);
      checkState(!buildCalled);
      this.seekForwardIncrementMs = seekForwardIncrementMs;
      return this;
    }

    /**
     * Sets a timeout for calls to {@link #release} and {@link #setForegroundMode}.
     *
     * <p>If a call to {@link #release} or {@link #setForegroundMode} takes more than {@code
     * timeoutMs} to complete, the player will report an error via {@link
     * Player.Listener#onPlayerError}.
     *
     * @param releaseTimeoutMs The release timeout, in milliseconds.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setReleaseTimeoutMs(long releaseTimeoutMs) {
      checkState(!buildCalled);
      this.releaseTimeoutMs = releaseTimeoutMs;
      return this;
    }

    /**
     * Sets a timeout for detaching a surface from the player.
     *
     * <p>If detaching a surface or replacing a surface takes more than {@code
     * detachSurfaceTimeoutMs} to complete, the player will report an error via {@link
     * Player.Listener#onPlayerError}.
     *
     * @param detachSurfaceTimeoutMs The timeout for detaching a surface, in milliseconds.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setDetachSurfaceTimeoutMs(long detachSurfaceTimeoutMs) {
      checkState(!buildCalled);
      this.detachSurfaceTimeoutMs = detachSurfaceTimeoutMs;
      return this;
    }

    /**
     * Sets whether to pause playback at the end of each media item.
     *
     * <p>This means the player will pause at the end of each window in the current {@link
     * #getCurrentTimeline() timeline}. Listeners will be informed by a call to {@link
     * Player.Listener#onPlayWhenReadyChanged(boolean, int)} with the reason {@link
     * Player#PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM} when this happens.
     *
     * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
      checkState(!buildCalled);
      this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems;
      return this;
    }

    /**
     * Sets the {@link LivePlaybackSpeedControl} that will control the playback speed when playing
     * live streams, in order to maintain a steady target offset from the live stream edge.
     *
     * @param livePlaybackSpeedControl The {@link LivePlaybackSpeedControl}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setLivePlaybackSpeedControl(LivePlaybackSpeedControl livePlaybackSpeedControl) {
      checkState(!buildCalled);
      this.livePlaybackSpeedControl = checkNotNull(livePlaybackSpeedControl);
      return this;
    }

    /**
     * Sets whether the player reports diagnostics data to the Android platform.
     *
     * <p>If enabled, the player will use the {@link android.media.metrics.MediaMetricsManager} to
     * create a {@link android.media.metrics.PlaybackSession} and forward playback events and
     * performance data to this session. This helps to provide system performance and debugging
     * information for media playback on the device. This data may also be collected by Google <a
     * href="https://support.google.com/accounts/answer/6078260">if sharing usage and diagnostics
     * data is enabled</a> by the user of the device.
     *
     * @param usePlatformDiagnostics Whether the player reports diagnostics data to the Android
     *     platform.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setUsePlatformDiagnostics(boolean usePlatformDiagnostics) {
      checkState(!buildCalled);
      this.usePlatformDiagnostics = usePlatformDiagnostics;
      return this;
    }

    /**
     * Sets the {@link Clock} that will be used by the player. Should only be set for testing
     * purposes.
     *
     * @param clock A {@link Clock}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    @VisibleForTesting
    public Builder setClock(Clock clock) {
      checkState(!buildCalled);
      this.clock = clock;
      return this;
    }

    /**
     * Sets the {@link Looper} that will be used for playback.
     *
     * <p>The backing thread should run with priority {@link Process#THREAD_PRIORITY_AUDIO} and
     * should handle messages within 10ms.
     *
     * @param playbackLooper A {@link looper}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setPlaybackLooper(Looper playbackLooper) {
      checkState(!buildCalled);
      this.playbackLooper = playbackLooper;
      return this;
    }

    /**
     * Builds an {@link ExoPlayer} instance.
     *
     * @throws IllegalStateException If this method has already been called.
     */
    public ExoPlayer build() {
      checkState(!buildCalled);
      buildCalled = true;
      return new ExoPlayerImpl(/* builder= */ this, /* wrappingPlayer= */ null);
    }

    public SimpleExoPlayer buildSimpleExoPlayer() {
      checkState(!buildCalled);
      buildCalled = true;
      return new SimpleExoPlayer(/* builder= */ this);
    }
  }

  /**
   * The default timeout for calls to {@link #release} and {@link #setForegroundMode}, in
   * milliseconds.
   */
  long DEFAULT_RELEASE_TIMEOUT_MS = 500;

  /** The default timeout for detaching a surface from the player, in milliseconds. */
  long DEFAULT_DETACH_SURFACE_TIMEOUT_MS = 2_000;

  /**
   * Equivalent to {@link Player#getPlayerError()}, except the exception is guaranteed to be an
   * {@link ExoPlaybackException}.
   */
  @Override
  @Nullable
  ExoPlaybackException getPlayerError();

  /**
   * @deprecated Use {@link ExoPlayer}, as the {@link AudioComponent} methods are defined by that
   *     interface.
   */
  @Nullable
  @Deprecated
  AudioComponent getAudioComponent();

  /**
   * @deprecated Use {@link ExoPlayer}, as the {@link VideoComponent} methods are defined by that
   *     interface.
   */
  @Nullable
  @Deprecated
  VideoComponent getVideoComponent();

  /**
   * @deprecated Use {@link Player}, as the {@link TextComponent} methods are defined by that
   *     interface.
   */
  @Nullable
  @Deprecated
  TextComponent getTextComponent();

  /**
   * @deprecated Use {@link Player}, as the {@link DeviceComponent} methods are defined by that
   *     interface.
   */
  @Nullable
  @Deprecated
  DeviceComponent getDeviceComponent();

  /**
   * Adds a listener to receive audio offload events.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to register.
   */
  void addAudioOffloadListener(AudioOffloadListener listener);

  /**
   * Removes a listener of audio offload events.
   *
   * @param listener The listener to unregister.
   */
  void removeAudioOffloadListener(AudioOffloadListener listener);

  /** Returns the {@link AnalyticsCollector} used for collecting analytics events. */
  AnalyticsCollector getAnalyticsCollector();

  /**
   * Adds an {@link AnalyticsListener} to receive analytics events.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to be added.
   */
  void addAnalyticsListener(AnalyticsListener listener);

  /**
   * Removes an {@link AnalyticsListener}.
   *
   * @param listener The listener to be removed.
   */
  void removeAnalyticsListener(AnalyticsListener listener);

  /** Returns the number of renderers. */
  int getRendererCount();

  /**
   * Returns the track type that the renderer at a given index handles.
   *
   * <p>For example, a video renderer will return {@link C#TRACK_TYPE_VIDEO}, an audio renderer will
   * return {@link C#TRACK_TYPE_AUDIO} and a text renderer will return {@link C#TRACK_TYPE_TEXT}.
   *
   * @param index The index of the renderer.
   * @return The {@link C.TrackType track type} that the renderer handles.
   */
  @C.TrackType
  int getRendererType(int index);

  /**
   * Returns the renderer at the given index.
   *
   * @param index The index of the renderer.
   * @return The renderer at this index.
   */
  Renderer getRenderer(int index);

  /**
   * Returns the track selector that this player uses, or null if track selection is not supported.
   */
  @Nullable
  TrackSelector getTrackSelector();

  /**
   * Returns the available track groups.
   *
   * @see Listener#onTracksChanged(Tracks)
   * @deprecated Use {@link #getCurrentTracks()}.
   */
  @Deprecated
  TrackGroupArray getCurrentTrackGroups();

  /**
   * Returns the current track selections for each renderer, which may include {@code null} elements
   * if some renderers do not have any selected tracks.
   *
   * @see Listener#onTracksChanged(Tracks)
   * @deprecated Use {@link #getCurrentTracks()}.
   */
  @Deprecated
  TrackSelectionArray getCurrentTrackSelections();

  /**
   * Returns the {@link Looper} associated with the playback thread.
   *
   * <p>This method may be called from any thread.
   */
  Looper getPlaybackLooper();

  /**
   * Returns the {@link Clock} used for playback.
   *
   * <p>This method can be called from any thread.
   */
  Clock getClock();

  /**
   * @deprecated Use {@link #prepare()} instead.
   */
  @Deprecated
  void retry();

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource)} and {@link #prepare()} instead.
   */
  @Deprecated
  void prepare(MediaSource mediaSource);

  /**
   * @deprecated Use {@link #setMediaSource(MediaSource, boolean)} and {@link #prepare()} instead.
   */
  @Deprecated
  void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState);

  /**
   * Clears the playlist, adds the specified {@link MediaSource MediaSources} and resets the
   * position to the default position.
   *
   * @param mediaSources The new {@link MediaSource MediaSources}.
   */
  void setMediaSources(List<MediaSource> mediaSources);

  /**
   * Clears the playlist and adds the specified {@link MediaSource MediaSources}.
   *
   * @param mediaSources The new {@link MediaSource MediaSources}.
   * @param resetPosition Whether the playback position should be reset to the default position in
   *     the first {@link Timeline.Window}. If false, playback will start from the position defined
   *     by {@link #getCurrentMediaItemIndex()} and {@link #getCurrentPosition()}.
   */
  void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition);

  /**
   * Clears the playlist and adds the specified {@link MediaSource MediaSources}.
   *
   * @param mediaSources The new {@link MediaSource MediaSources}.
   * @param startMediaItemIndex The media item index to start playback from. If {@link
   *     C#INDEX_UNSET} is passed, the current position is not reset.
   * @param startPositionMs The position in milliseconds to start playback from. If {@link
   *     C#TIME_UNSET} is passed, the default position of the given media item is used. In any case,
   *     if {@code startMediaItemIndex} is set to {@link C#INDEX_UNSET}, this parameter is ignored
   *     and the position is not reset at all.
   */
  void setMediaSources(
      List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs);

  /**
   * Clears the playlist, adds the specified {@link MediaSource} and resets the position to the
   * default position.
   *
   * @param mediaSource The new {@link MediaSource}.
   */
  void setMediaSource(MediaSource mediaSource);

  /**
   * Clears the playlist and adds the specified {@link MediaSource}.
   *
   * @param mediaSource The new {@link MediaSource}.
   * @param startPositionMs The position in milliseconds to start playback from.
   */
  void setMediaSource(MediaSource mediaSource, long startPositionMs);

  /**
   * Clears the playlist and adds the specified {@link MediaSource}.
   *
   * @param mediaSource The new {@link MediaSource}.
   * @param resetPosition Whether the playback position should be reset to the default position. If
   *     false, playback will start from the position defined by {@link #getCurrentMediaItemIndex()}
   *     and {@link #getCurrentPosition()}.
   */
  void setMediaSource(MediaSource mediaSource, boolean resetPosition);

  /**
   * Adds a media source to the end of the playlist.
   *
   * @param mediaSource The {@link MediaSource} to add.
   */
  void addMediaSource(MediaSource mediaSource);

  /**
   * Adds a media source at the given index of the playlist.
   *
   * @param index The index at which to add the source.
   * @param mediaSource The {@link MediaSource} to add.
   */
  void addMediaSource(int index, MediaSource mediaSource);

  /**
   * Adds a list of media sources to the end of the playlist.
   *
   * @param mediaSources The {@link MediaSource MediaSources} to add.
   */
  void addMediaSources(List<MediaSource> mediaSources);

  /**
   * Adds a list of media sources at the given index of the playlist.
   *
   * @param index The index at which to add the media sources.
   * @param mediaSources The {@link MediaSource MediaSources} to add.
   */
  void addMediaSources(int index, List<MediaSource> mediaSources);

  /**
   * Sets the shuffle order.
   *
   * @param shuffleOrder The shuffle order.
   */
  void setShuffleOrder(ShuffleOrder shuffleOrder);

  /**
   * Sets the attributes for audio playback, used by the underlying audio track. If not set, the
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
   * <p>If audio focus should be handled, the {@link AudioAttributes#usage} must be {@link
   * C#USAGE_MEDIA} or {@link C#USAGE_GAME}. Other usages will throw an {@link
   * IllegalArgumentException}.
   *
   * @param audioAttributes The attributes to use for audio playback.
   * @param handleAudioFocus True if the player should handle audio focus, false otherwise.
   */
  void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);

  /**
   * Sets the ID of the audio session to attach to the underlying {@link android.media.AudioTrack}.
   *
   * <p>The audio session ID can be generated using {@link Util#generateAudioSessionIdV21(Context)}
   * for API 21+.
   *
   * @param audioSessionId The audio session ID, or {@link C#AUDIO_SESSION_ID_UNSET} if it should be
   *     generated by the framework.
   */
  void setAudioSessionId(int audioSessionId);

  /** Returns the audio session identifier, or {@link C#AUDIO_SESSION_ID_UNSET} if not set. */
  int getAudioSessionId();

  /** Sets information on an auxiliary audio effect to attach to the underlying audio track. */
  void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

  /** Detaches any previously attached auxiliary audio effect from the underlying audio track. */
  void clearAuxEffectInfo();

  /**
   * Sets the preferred audio device.
   *
   * @param audioDeviceInfo The preferred {@linkplain AudioDeviceInfo audio device}, or null to
   *     restore the default.
   */
  @RequiresApi(23)
  void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo);

  /**
   * Sets whether skipping silences in the audio stream is enabled.
   *
   * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
   */
  void setSkipSilenceEnabled(boolean skipSilenceEnabled);

  /** Returns whether skipping silences in the audio stream is enabled. */
  boolean getSkipSilenceEnabled();

  /**
   * Sets the {@link C.VideoScalingMode}.
   *
   * <p>The scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer} is
   * enabled and if the output surface is owned by a {@link SurfaceView}.
   *
   * @param videoScalingMode The {@link C.VideoScalingMode}.
   */
  void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode);

  /** Returns the {@link C.VideoScalingMode}. */
  @C.VideoScalingMode
  int getVideoScalingMode();

  /**
   * Sets a {@link C.VideoChangeFrameRateStrategy} that will be used by the player when provided
   * with a video output {@link Surface}.
   *
   * <p>The strategy only applies if a {@link MediaCodec}-based video {@link Renderer} is enabled.
   * Applications wishing to use {@link Surface#CHANGE_FRAME_RATE_ALWAYS} should set the mode to
   * {@link C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF} to disable calls to {@link Surface#setFrameRate}
   * from ExoPlayer, and should then call {@link Surface#setFrameRate} directly from application
   * code.
   *
   * @param videoChangeFrameRateStrategy A {@link C.VideoChangeFrameRateStrategy}.
   */
  void setVideoChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int videoChangeFrameRateStrategy);

  /** Returns the {@link C.VideoChangeFrameRateStrategy}. */
  @C.VideoChangeFrameRateStrategy
  int getVideoChangeFrameRateStrategy();

  /**
   * Sets a listener to receive video frame metadata events.
   *
   * <p>This method is intended to be called by the same component that sets the {@link Surface}
   * onto which video will be rendered. If using ExoPlayer's standard UI components, this method
   * should not be called directly from application code.
   *
   * @param listener The listener.
   */
  void setVideoFrameMetadataListener(VideoFrameMetadataListener listener);

  /**
   * Clears the listener which receives video frame metadata events if it matches the one passed.
   * Else does nothing.
   *
   * @param listener The listener to clear.
   */
  void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener);

  /**
   * Sets a listener of camera motion events.
   *
   * @param listener The listener.
   */
  void setCameraMotionListener(CameraMotionListener listener);

  /**
   * Clears the listener which receives camera motion events if it matches the one passed. Else does
   * nothing.
   *
   * @param listener The listener to clear.
   */
  void clearCameraMotionListener(CameraMotionListener listener);

  /**
   * Creates a message that can be sent to a {@link PlayerMessage.Target}. By default, the message
   * will be delivered immediately without blocking on the playback thread. The default {@link
   * PlayerMessage#getType()} is 0 and the default {@link PlayerMessage#getPayload()} is null. If a
   * position is specified with {@link PlayerMessage#setPosition(long)}, the message will be
   * delivered at this position in the current media item defined by {@link
   * #getCurrentMediaItemIndex()}. Alternatively, the message can be sent at a specific mediaItem
   * using {@link PlayerMessage#setPosition(int, long)}.
   */
  PlayerMessage createMessage(PlayerMessage.Target target);

  /**
   * Sets the parameters that control how seek operations are performed.
   *
   * @param seekParameters The seek parameters, or {@code null} to use the defaults.
   */
  void setSeekParameters(@Nullable SeekParameters seekParameters);

  /** Returns the currently active {@link SeekParameters} of the player. */
  SeekParameters getSeekParameters();

  /**
   * Sets whether the player is allowed to keep holding limited resources such as video decoders,
   * even when in the idle state. By doing so, the player may be able to reduce latency when
   * starting to play another piece of content for which the same resources are required.
   *
   * <p>This mode should be used with caution, since holding limited resources may prevent other
   * players of media components from acquiring them. It should only be enabled when <em>both</em>
   * of the following conditions are true:
   *
   * <ul>
   *   <li>The application that owns the player is in the foreground.
   *   <li>The player is used in a way that may benefit from foreground mode. For this to be true,
   *       the same player instance must be used to play multiple pieces of content, and there must
   *       be gaps between the playbacks (i.e. {@link #stop} is called to halt one playback, and
   *       {@link #prepare} is called some time later to start a new one).
   * </ul>
   *
   * <p>Note that foreground mode is <em>not</em> useful for switching between content without gaps
   * between the playbacks. For this use case {@link #stop} does not need to be called, and simply
   * calling {@link #prepare} for the new media will cause limited resources to be retained even if
   * foreground mode is not enabled.
   *
   * <p>If foreground mode is enabled, it's the application's responsibility to disable it when the
   * conditions described above no longer hold.
   *
   * @param foregroundMode Whether the player is allowed to keep limited resources even when in the
   *     idle state.
   */
  void setForegroundMode(boolean foregroundMode);

  /**
   * Sets whether to pause playback at the end of each media item.
   *
   * <p>This means the player will pause at the end of each window in the current {@link
   * #getCurrentTimeline() timeline}. Listeners will be informed by a call to {@link
   * Player.Listener#onPlayWhenReadyChanged(boolean, int)} with the reason {@link
   * Player#PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM} when this happens.
   *
   * @param pauseAtEndOfMediaItems Whether to pause playback at the end of each media item.
   */
  void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems);

  /**
   * Returns whether the player pauses playback at the end of each media item.
   *
   * @see #setPauseAtEndOfMediaItems(boolean)
   */
  boolean getPauseAtEndOfMediaItems();

  /** Returns the audio format currently being played, or null if no audio is being played. */
  @Nullable
  Format getAudioFormat();

  /** Returns the video format currently being played, or null if no video is being played. */
  @Nullable
  Format getVideoFormat();

  /** Returns {@link DecoderCounters} for audio, or null if no audio is being played. */
  @Nullable
  DecoderCounters getAudioDecoderCounters();

  /** Returns {@link DecoderCounters} for video, or null if no video is being played. */
  @Nullable
  DecoderCounters getVideoDecoderCounters();

  /**
   * Sets whether the player should pause automatically when audio is rerouted from a headset to
   * device speakers. See the <a
   * href="https://developer.android.com/guide/topics/media-apps/volume-and-earphones#becoming-noisy">audio
   * becoming noisy</a> documentation for more information.
   *
   * @param handleAudioBecomingNoisy Whether the player should pause automatically when audio is
   *     rerouted from a headset to device speakers.
   */
  void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy);

  /**
   * @deprecated Use {@link #setWakeMode(int)} instead.
   */
  @Deprecated
  void setHandleWakeLock(boolean handleWakeLock);

  /**
   * Sets how the player should keep the device awake for playback when the screen is off.
   *
   * <p>Enabling this feature requires the {@link android.Manifest.permission#WAKE_LOCK} permission.
   * It should be used together with a foreground {@link android.app.Service} for use cases where
   * playback occurs and the screen is off (e.g. background audio playback). It is not useful when
   * the screen will be kept on during playback (e.g. foreground video playback).
   *
   * <p>When enabled, the locks ({@link android.os.PowerManager.WakeLock} / {@link
   * android.net.wifi.WifiManager.WifiLock}) will be held whenever the player is in the {@link
   * #STATE_READY} or {@link #STATE_BUFFERING} states with {@code playWhenReady = true}. The locks
   * held depends on the specified {@link C.WakeMode}.
   *
   * @param wakeMode The {@link C.WakeMode} option to keep the device awake during playback.
   */
  void setWakeMode(@C.WakeMode int wakeMode);

  /**
   * Sets a {@link PriorityTaskManager}, or null to clear a previously set priority task manager.
   *
   * <p>The priority {@link C#PRIORITY_PLAYBACK} will be set while the player is loading.
   *
   * @param priorityTaskManager The {@link PriorityTaskManager}, or null to clear a previously set
   *     priority task manager.
   */
  void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager);

  /**
   * Sets whether audio offload scheduling is enabled. If enabled, ExoPlayer's main loop will run as
   * rarely as possible when playing an audio stream using audio offload.
   *
   * <p>Only use this scheduling mode if the player is not displaying anything to the user. For
   * example when the application is in the background, or the screen is off. The player state
   * (including position) is rarely updated (roughly between every 10 seconds and 1 minute).
   *
   * <p>While offload scheduling is enabled, player events may be delivered severely delayed and
   * apps should not interact with the player. When returning to the foreground, disable offload
   * scheduling and wait for {@link
   * AudioOffloadListener#onExperimentalOffloadSchedulingEnabledChanged(boolean)} to be called with
   * {@code offloadSchedulingEnabled = false} before interacting with the player.
   *
   * <p>This mode should save significant power when the phone is playing offload audio with the
   * screen off.
   *
   * <p>This mode only has an effect when playing an audio track in offload mode, which requires all
   * the following:
   *
   * <ul>
   *   <li>Audio offload rendering is enabled in {@link
   *       DefaultRenderersFactory#setEnableAudioOffload} or the equivalent option passed to {@link
   *       DefaultAudioSink.Builder#setOffloadMode}.
   *   <li>An audio track is playing in a format that the device supports offloading (for example,
   *       MP3 or AAC).
   *   <li>The {@link AudioSink} is playing with an offload {@link AudioTrack}.
   * </ul>
   *
   * <p>The state where ExoPlayer main loop has been paused to save power during offload playback
   * can be queried with {@link #experimentalIsSleepingForOffload()}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param offloadSchedulingEnabled Whether to enable offload scheduling.
   */
  void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled);

  /**
   * Returns whether the player has paused its main loop to save power in offload scheduling mode.
   *
   * @see #experimentalSetOffloadSchedulingEnabled(boolean)
   * @see AudioOffloadListener#onExperimentalSleepingForOffloadChanged(boolean)
   */
  boolean experimentalIsSleepingForOffload();

  /**
   * Returns whether <a
   * href="https://source.android.com/devices/tv/multimedia-tunneling">tunneling</a> is enabled for
   * the currently selected tracks.
   *
   * @see Player.Listener#onTracksChanged(Tracks)
   */
  boolean isTunnelingEnabled();
}
