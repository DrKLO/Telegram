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
package com.google.android.exoplayer2;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.mediacodec.DefaultMediaCodecAdapterFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionRenderer;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

/** Default {@link RenderersFactory} implementation. */
public class DefaultRenderersFactory implements RenderersFactory {

  /**
   * The default maximum duration for which a video renderer can attempt to seamlessly join an
   * ongoing playback.
   */
  public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

  /**
   * Modes for using extension renderers. One of {@link #EXTENSION_RENDERER_MODE_OFF}, {@link
   * #EXTENSION_RENDERER_MODE_ON} or {@link #EXTENSION_RENDERER_MODE_PREFER}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({EXTENSION_RENDERER_MODE_OFF, EXTENSION_RENDERER_MODE_ON, EXTENSION_RENDERER_MODE_PREFER})
  public @interface ExtensionRendererMode {}
  /** Do not allow use of extension renderers. */
  public static final int EXTENSION_RENDERER_MODE_OFF = 0;
  /**
   * Allow use of extension renderers. Extension renderers are indexed after core renderers of the
   * same type. A {@link TrackSelector} that prefers the first suitable renderer will therefore
   * prefer to use a core renderer to an extension renderer in the case that both are able to play a
   * given track.
   */
  public static final int EXTENSION_RENDERER_MODE_ON = 1;
  /**
   * Allow use of extension renderers. Extension renderers are indexed before core renderers of the
   * same type. A {@link TrackSelector} that prefers the first suitable renderer will therefore
   * prefer to use an extension renderer to a core renderer in the case that both are able to play a
   * given track.
   */
  public static final int EXTENSION_RENDERER_MODE_PREFER = 2;

  /**
   * The maximum number of frames that can be dropped between invocations of {@link
   * VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

  private static final String TAG = "DefaultRenderersFactory";

  private final Context context;
  private final DefaultMediaCodecAdapterFactory codecAdapterFactory;
  private @ExtensionRendererMode int extensionRendererMode;
  private long allowedVideoJoiningTimeMs;
  private boolean enableDecoderFallback;
  private MediaCodecSelector mediaCodecSelector;
  private boolean enableFloatOutput;
  private boolean enableAudioTrackPlaybackParams;
  private boolean enableOffload;

  /**
   * @param context A {@link Context}.
   */
  public DefaultRenderersFactory(Context context) {
    this.context = context;
    codecAdapterFactory = new DefaultMediaCodecAdapterFactory();
    extensionRendererMode = EXTENSION_RENDERER_MODE_OFF;
    allowedVideoJoiningTimeMs = DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS;
    mediaCodecSelector = MediaCodecSelector.DEFAULT;
  }

  /**
   * Sets the extension renderer mode, which determines if and how available extension renderers are
   * used. Note that extensions must be included in the application build for them to be considered
   * available.
   *
   * <p>The default value is {@link #EXTENSION_RENDERER_MODE_OFF}.
   *
   * @param extensionRendererMode The extension renderer mode.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setExtensionRendererMode(
      @ExtensionRendererMode int extensionRendererMode) {
    this.extensionRendererMode = extensionRendererMode;
    return this;
  }

  /**
   * Enables {@link com.google.android.exoplayer2.mediacodec.MediaCodecRenderer} instances to
   * operate their {@link MediaCodec} in asynchronous mode and perform asynchronous queueing.
   *
   * <p>This feature can be enabled only on devices with API versions &gt;= 23. For devices with
   * older API versions, this method is a no-op.
   *
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory forceEnableMediaCodecAsynchronousQueueing() {
    codecAdapterFactory.forceEnableAsynchronous();
    return this;
  }

  /**
   * Disables {@link com.google.android.exoplayer2.mediacodec.MediaCodecRenderer} instances from
   * operating their {@link MediaCodec} in asynchronous mode and perform asynchronous queueing.
   * {@link MediaCodec} instances will be operated synchronous mode.
   *
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory forceDisableMediaCodecAsynchronousQueueing() {
    codecAdapterFactory.forceDisableAsynchronous();
    return this;
  }

  /**
   * Enable synchronizing codec interactions with asynchronous buffer queueing.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   *
   * @param enabled Whether codec interactions will be synchronized with asynchronous buffer
   *     queueing.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory experimentalSetSynchronizeCodecInteractionsWithQueueingEnabled(
      boolean enabled) {
    codecAdapterFactory.experimentalSetSynchronizeCodecInteractionsWithQueueingEnabled(enabled);
    return this;
  }

  /**
   * Sets whether to enable fallback to lower-priority decoders if decoder initialization fails.
   * This may result in using a decoder that is less efficient or slower than the primary decoder.
   *
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setEnableDecoderFallback(boolean enableDecoderFallback) {
    this.enableDecoderFallback = enableDecoderFallback;
    return this;
  }

  /**
   * Sets a {@link MediaCodecSelector} for use by {@link MediaCodec} based renderers.
   *
   * <p>The default value is {@link MediaCodecSelector#DEFAULT}.
   *
   * @param mediaCodecSelector The {@link MediaCodecSelector}.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setMediaCodecSelector(MediaCodecSelector mediaCodecSelector) {
    this.mediaCodecSelector = mediaCodecSelector;
    return this;
  }

  /**
   * Sets whether floating point audio should be output when possible.
   *
   * <p>Enabling floating point output disables audio processing, but may allow for higher quality
   * audio output.
   *
   * <p>The default value is {@code false}.
   *
   * @param enableFloatOutput Whether to enable use of floating point audio output, if available.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setEnableAudioFloatOutput(boolean enableFloatOutput) {
    this.enableFloatOutput = enableFloatOutput;
    return this;
  }

  /**
   * Sets whether audio should be played using the offload path.
   *
   * <p>Audio offload disables ExoPlayer audio processing, but significantly reduces the energy
   * consumption of the playback when {@link
   * ExoPlayer#experimentalSetOffloadSchedulingEnabled(boolean) offload scheduling} is enabled.
   *
   * <p>Most Android devices can only support one offload {@link android.media.AudioTrack} at a time
   * and can invalidate it at any time. Thus an app can never be guaranteed that it will be able to
   * play in offload.
   *
   * <p>The default value is {@code false}.
   *
   * @param enableOffload Whether to enable use of audio offload for supported formats, if
   *     available.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setEnableAudioOffload(boolean enableOffload) {
    this.enableOffload = enableOffload;
    return this;
  }

  /**
   * Sets whether to enable setting playback speed using {@link
   * android.media.AudioTrack#setPlaybackParams(PlaybackParams)}, which is supported from API level
   * 23, rather than using application-level audio speed adjustment. This setting has no effect on
   * builds before API level 23 (application-level speed adjustment will be used in all cases).
   *
   * <p>If enabled and supported, new playback speed settings will take effect more quickly because
   * they are applied at the audio mixer, rather than at the point of writing data to the track.
   *
   * <p>When using this mode, the maximum supported playback speed is limited by the size of the
   * audio track's buffer. If the requested speed is not supported the player's event listener will
   * be notified twice on setting playback speed, once with the requested speed, then again with the
   * old playback speed reflecting the fact that the requested speed was not supported.
   *
   * @param enableAudioTrackPlaybackParams Whether to enable setting playback speed using {@link
   *     android.media.AudioTrack#setPlaybackParams(PlaybackParams)}.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setEnableAudioTrackPlaybackParams(
      boolean enableAudioTrackPlaybackParams) {
    this.enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams;
    return this;
  }

  /**
   * Sets the maximum duration for which video renderers can attempt to seamlessly join an ongoing
   * playback.
   *
   * <p>The default value is {@link #DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS}.
   *
   * @param allowedVideoJoiningTimeMs The maximum duration for which video renderers can attempt to
   *     seamlessly join an ongoing playback, in milliseconds.
   * @return This factory, for convenience.
   */
  @CanIgnoreReturnValue
  public DefaultRenderersFactory setAllowedVideoJoiningTimeMs(long allowedVideoJoiningTimeMs) {
    this.allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs;
    return this;
  }

  @Override
  public Renderer[] createRenderers(
      Handler eventHandler,
      VideoRendererEventListener videoRendererEventListener,
      AudioRendererEventListener audioRendererEventListener,
      TextOutput textRendererOutput,
      MetadataOutput metadataRendererOutput) {
    ArrayList<Renderer> renderersList = new ArrayList<>();
    buildVideoRenderers(
        context,
        extensionRendererMode,
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        videoRendererEventListener,
        allowedVideoJoiningTimeMs,
        renderersList);
    @Nullable
    AudioSink audioSink =
        buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams, enableOffload);
    if (audioSink != null) {
      buildAudioRenderers(
          context,
          extensionRendererMode,
          mediaCodecSelector,
          enableDecoderFallback,
          audioSink,
          eventHandler,
          audioRendererEventListener,
          renderersList);
    }
    buildTextRenderers(
        context,
        textRendererOutput,
        eventHandler.getLooper(),
        extensionRendererMode,
        renderersList);
    buildMetadataRenderers(
        context,
        metadataRendererOutput,
        eventHandler.getLooper(),
        extensionRendererMode,
        renderersList);
    buildCameraMotionRenderers(context, extensionRendererMode, renderersList);
    buildMiscellaneousRenderers(context, eventHandler, extensionRendererMode, renderersList);
    return renderersList.toArray(new Renderer[0]);
  }

  /**
   * Builds video renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param extensionRendererMode The extension renderer mode.
   * @param mediaCodecSelector A decoder selector.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is slower/less efficient than
   *     the primary decoder.
   * @param eventHandler A handler associated with the main thread's looper.
   * @param eventListener An event listener.
   * @param allowedVideoJoiningTimeMs The maximum duration for which video renderers can attempt to
   *     seamlessly join an ongoing playback, in milliseconds.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildVideoRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      Handler eventHandler,
      VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs,
      ArrayList<Renderer> out) {
    MediaCodecVideoRenderer videoRenderer =
        new MediaCodecVideoRenderer(
            context,
            getCodecAdapterFactory(),
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    out.add(videoRenderer);

    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.vp9.LibvpxVideoRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              long.class,
              android.os.Handler.class,
              com.google.android.exoplayer2.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  allowedVideoJoiningTimeMs,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "Loaded LibvpxVideoRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      // The extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating VP9 extension", e);
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.av1.Libgav1VideoRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              long.class,
              android.os.Handler.class,
              com.google.android.exoplayer2.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  allowedVideoJoiningTimeMs,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "Loaded Libgav1VideoRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      // The extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating AV1 extension", e);
    }
  }

  /**
   * Builds audio renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param extensionRendererMode The extension renderer mode.
   * @param mediaCodecSelector A decoder selector.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is slower/less efficient than
   *     the primary decoder.
   * @param audioSink A sink to which the renderers will output.
   * @param eventHandler A handler to use when invoking event listeners and outputs.
   * @param eventListener An event listener.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildAudioRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      AudioSink audioSink,
      Handler eventHandler,
      AudioRendererEventListener eventListener,
      ArrayList<Renderer> out) {
    MediaCodecAudioRenderer audioRenderer =
        new MediaCodecAudioRenderer(
            context,
            getCodecAdapterFactory(),
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            audioSink);
    out.add(audioRenderer);

    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.decoder.midi.MidiRenderer");
      Constructor<?> constructor = clazz.getConstructor();
      Renderer renderer = (Renderer) constructor.newInstance();
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "Loaded MidiRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      // The extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating MIDI extension", e);
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              com.google.android.exoplayer2.audio.AudioRendererEventListener.class,
              com.google.android.exoplayer2.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "Loaded LibopusAudioRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      // The extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating Opus extension", e);
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              com.google.android.exoplayer2.audio.AudioRendererEventListener.class,
              com.google.android.exoplayer2.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "Loaded LibflacAudioRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      // The extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating FLAC extension", e);
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              com.google.android.exoplayer2.audio.AudioRendererEventListener.class,
              com.google.android.exoplayer2.audio.AudioSink.class);
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioSink);
      out.add(extensionRendererIndex++, renderer);
      Log.i(TAG, "Loaded FfmpegAudioRenderer.");
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the extension.
    } catch (Exception e) {
      // The extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating FFmpeg extension", e);
    }
  }

  /**
   * Builds text renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param output An output for the renderers.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildTextRenderers(
      Context context,
      TextOutput output,
      Looper outputLooper,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(new TextRenderer(output, outputLooper));
  }

  /**
   * Builds metadata renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param output An output for the renderers.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildMetadataRenderers(
      Context context,
      MetadataOutput output,
      Looper outputLooper,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(new MetadataRenderer(output, outputLooper));
  }

  /**
   * Builds camera motion renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildCameraMotionRenderers(
      Context context, @ExtensionRendererMode int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new CameraMotionRenderer());
  }

  /**
   * Builds any miscellaneous renderers used by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param eventHandler A handler to use when invoking event listeners and outputs.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildMiscellaneousRenderers(
      Context context,
      Handler eventHandler,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    // Do nothing.
  }

  /**
   * Builds an {@link AudioSink} to which the audio renderers will output.
   *
   * @param context The {@link Context} associated with the player.
   * @param enableFloatOutput Whether to enable use of floating point audio output, if available.
   * @param enableAudioTrackPlaybackParams Whether to enable setting playback speed using {@link
   *     android.media.AudioTrack#setPlaybackParams(PlaybackParams)}, if supported.
   * @param enableOffload Whether to enable use of audio offload for supported formats, if
   *     available.
   * @return The {@link AudioSink} to which the audio renderers will output. May be {@code null} if
   *     no audio renderers are required. If {@code null} is returned then {@link
   *     #buildAudioRenderers} will not be called.
   */
  @Nullable
  protected AudioSink buildAudioSink(
      Context context,
      boolean enableFloatOutput,
      boolean enableAudioTrackPlaybackParams,
      boolean enableOffload) {
    return new DefaultAudioSink.Builder()
        .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setOffloadMode(
            enableOffload
                ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                : DefaultAudioSink.OFFLOAD_MODE_DISABLED)
        .build();
  }

  /**
   * Returns the {@link MediaCodecAdapter.Factory} that will be used when creating {@link
   * com.google.android.exoplayer2.mediacodec.MediaCodecRenderer} instances.
   */
  protected MediaCodecAdapter.Factory getCodecAdapterFactory() {
    return codecAdapterFactory;
  }
}
