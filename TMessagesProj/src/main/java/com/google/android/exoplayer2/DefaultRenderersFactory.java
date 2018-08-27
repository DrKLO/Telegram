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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

/**
 * Default {@link RenderersFactory} implementation.
 */
public class DefaultRenderersFactory implements RenderersFactory {

  /**
   * The default maximum duration for which a video renderer can attempt to seamlessly join an
   * ongoing playback.
   */
  public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

  /**
   * Modes for using extension renderers.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({EXTENSION_RENDERER_MODE_OFF, EXTENSION_RENDERER_MODE_ON,
      EXTENSION_RENDERER_MODE_PREFER})
  public @interface ExtensionRendererMode {}
  /**
   * Do not allow use of extension renderers.
   */
  public static final int EXTENSION_RENDERER_MODE_OFF = 0;
  /**
   * Allow use of extension renderers. Extension renderers are indexed after core renderers of the
   * same type. A {@link TrackSelector} that prefers the first suitable renderer will therefore
   * prefer to use a core renderer to an extension renderer in the case that both are able to play
   * a given track.
   */
  public static final int EXTENSION_RENDERER_MODE_ON = 1;
  /**
   * Allow use of extension renderers. Extension renderers are indexed before core renderers of the
   * same type. A {@link TrackSelector} that prefers the first suitable renderer will therefore
   * prefer to use an extension renderer to a core renderer in the case that both are able to play
   * a given track.
   */
  public static final int EXTENSION_RENDERER_MODE_PREFER = 2;

  private static final String TAG = "DefaultRenderersFactory";

  protected static final int MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50;

  private final Context context;
  @Nullable private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
  private final @ExtensionRendererMode int extensionRendererMode;
  private final long allowedVideoJoiningTimeMs;

  /**
   * @param context A {@link Context}.
   */
  public DefaultRenderersFactory(Context context) {
    this(context, EXTENSION_RENDERER_MODE_OFF);
  }

  /**
   * @deprecated Use {@link #DefaultRenderersFactory(Context)} and pass {@link DrmSessionManager}
   *     directly to {@link SimpleExoPlayer} or {@link ExoPlayerFactory}.
   */
  @Deprecated
  public DefaultRenderersFactory(
      Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    this(context, drmSessionManager, EXTENSION_RENDERER_MODE_OFF);
  }

  /**
   * @param context A {@link Context}.
   * @param extensionRendererMode The extension renderer mode, which determines if and how available
   *     extension renderers are used. Note that extensions must be included in the application
   *     build for them to be considered available.
   */
  public DefaultRenderersFactory(
      Context context, @ExtensionRendererMode int extensionRendererMode) {
    this(context, null, extensionRendererMode, DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);
  }

  /**
   * @deprecated Use {@link #DefaultRenderersFactory(Context, int)} and pass {@link
   *     DrmSessionManager} directly to {@link SimpleExoPlayer} or {@link ExoPlayerFactory}.
   */
  @Deprecated
  public DefaultRenderersFactory(
      Context context,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @ExtensionRendererMode int extensionRendererMode) {
    this(context, drmSessionManager, extensionRendererMode, DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);
  }

  /**
   * @param context A {@link Context}.
   * @param extensionRendererMode The extension renderer mode, which determines if and how available
   *     extension renderers are used. Note that extensions must be included in the application
   *     build for them to be considered available.
   * @param allowedVideoJoiningTimeMs The maximum duration for which video renderers can attempt to
   *     seamlessly join an ongoing playback.
   */
  public DefaultRenderersFactory(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      long allowedVideoJoiningTimeMs) {
    this(context, null, extensionRendererMode, allowedVideoJoiningTimeMs);
  }

  /**
   * @deprecated Use {@link #DefaultRenderersFactory(Context, int, long)} and pass {@link
   *     DrmSessionManager} directly to {@link SimpleExoPlayer} or {@link ExoPlayerFactory}.
   */
  @Deprecated
  public DefaultRenderersFactory(
      Context context,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @ExtensionRendererMode int extensionRendererMode,
      long allowedVideoJoiningTimeMs) {
    this.context = context;
    this.extensionRendererMode = extensionRendererMode;
    this.allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs;
    this.drmSessionManager = drmSessionManager;
  }

  @Override
  public Renderer[] createRenderers(
      Handler eventHandler,
      VideoRendererEventListener videoRendererEventListener,
      AudioRendererEventListener audioRendererEventListener,
      TextOutput textRendererOutput,
      MetadataOutput metadataRendererOutput,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    if (drmSessionManager == null) {
      drmSessionManager = this.drmSessionManager;
    }
    ArrayList<Renderer> renderersList = new ArrayList<>();
    buildVideoRenderers(context, drmSessionManager, allowedVideoJoiningTimeMs,
        eventHandler, videoRendererEventListener, extensionRendererMode, renderersList);
    buildAudioRenderers(context, drmSessionManager, buildAudioProcessors(),
        eventHandler, audioRendererEventListener, extensionRendererMode, renderersList);
    buildTextRenderers(context, textRendererOutput, eventHandler.getLooper(),
        extensionRendererMode, renderersList);
    buildMetadataRenderers(context, metadataRendererOutput, eventHandler.getLooper(),
        extensionRendererMode, renderersList);
    buildMiscellaneousRenderers(context, eventHandler, extensionRendererMode, renderersList);
    return renderersList.toArray(new Renderer[renderersList.size()]);
  }

  /**
   * Builds video renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the player
   *     will not be used for DRM protected playbacks.
   * @param allowedVideoJoiningTimeMs The maximum duration in milliseconds for which video
   *     renderers can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler associated with the main thread's looper.
   * @param eventListener An event listener.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildVideoRenderers(Context context,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      long allowedVideoJoiningTimeMs, Handler eventHandler,
      VideoRendererEventListener eventListener, @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(
        new MediaCodecVideoRenderer(
            context,
            MediaCodecSelector.DEFAULT,
            allowedVideoJoiningTimeMs,
            drmSessionManager,
            /* playClearSamplesWithoutKeys= */ false,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));

    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      // LINT.IfChange
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.vp9.LibvpxVideoRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              boolean.class,
              long.class,
              android.os.Handler.class,
              com.google.android.exoplayer2.video.VideoRendererEventListener.class,
              int.class);
      // LINT.ThenChange(../../../../../../../proguard-rules.txt)
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  true,
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
  }

  /**
   * Builds audio renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the player
   *     will not be used for DRM protected playbacks.
   * @param audioProcessors An array of {@link AudioProcessor}s that will process PCM audio
   *     buffers before output. May be empty.
   * @param eventHandler A handler to use when invoking event listeners and outputs.
   * @param eventListener An event listener.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildAudioRenderers(Context context,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AudioProcessor[] audioProcessors, Handler eventHandler,
      AudioRendererEventListener eventListener, @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(
        new MediaCodecAudioRenderer(
            context,
            MediaCodecSelector.DEFAULT,
            drmSessionManager,
            /* playClearSamplesWithoutKeys= */ false,
            eventHandler,
            eventListener,
            AudioCapabilities.getCapabilities(context),
            audioProcessors));

    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      // Full class names used for constructor args so the LINT rule triggers if any of them move.
      // LINT.IfChange
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.opus.LibopusAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              com.google.android.exoplayer2.audio.AudioRendererEventListener.class,
              com.google.android.exoplayer2.audio.AudioProcessor[].class);
      // LINT.ThenChange(../../../../../../../proguard-rules.txt)
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioProcessors);
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
      // LINT.IfChange
      Class<?> clazz = Class.forName("com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              com.google.android.exoplayer2.audio.AudioRendererEventListener.class,
              com.google.android.exoplayer2.audio.AudioProcessor[].class);
      // LINT.ThenChange(../../../../../../../proguard-rules.txt)
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioProcessors);
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
      // LINT.IfChange
      Class<?> clazz =
          Class.forName("com.google.android.exoplayer2.ext.ffmpeg.FfmpegAudioRenderer");
      Constructor<?> constructor =
          clazz.getConstructor(
              android.os.Handler.class,
              com.google.android.exoplayer2.audio.AudioRendererEventListener.class,
              com.google.android.exoplayer2.audio.AudioProcessor[].class);
      // LINT.ThenChange(../../../../../../../proguard-rules.txt)
      Renderer renderer =
          (Renderer) constructor.newInstance(eventHandler, eventListener, audioProcessors);
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
   * @param outputLooper The looper associated with the thread on which the output should be
   *     called.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper,
      @ExtensionRendererMode int extensionRendererMode,
      ArrayList<Renderer> out) {
    out.add(new TextRenderer(output, outputLooper));
  }

  /**
   * Builds metadata renderers for use by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param output An output for the renderers.
   * @param outputLooper The looper associated with the thread on which the output should be
   *     called.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildMetadataRenderers(Context context, MetadataOutput output, Looper outputLooper,
      @ExtensionRendererMode int extensionRendererMode, ArrayList<Renderer> out) {
    out.add(new MetadataRenderer(output, outputLooper));
  }

  /**
   * Builds any miscellaneous renderers used by the player.
   *
   * @param context The {@link Context} associated with the player.
   * @param eventHandler A handler to use when invoking event listeners and outputs.
   * @param extensionRendererMode The extension renderer mode.
   * @param out An array to which the built renderers should be appended.
   */
  protected void buildMiscellaneousRenderers(Context context, Handler eventHandler,
      @ExtensionRendererMode int extensionRendererMode, ArrayList<Renderer> out) {
    // Do nothing.
  }

  /**
   * Builds an array of {@link AudioProcessor}s that will process PCM audio before output.
   */
  protected AudioProcessor[] buildAudioProcessors() {
    return new AudioProcessor[0];
  }

}
