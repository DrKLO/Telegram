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

import android.content.Context;
import android.os.Looper;
import org.telegram.messenger.exoplayer2.drm.DrmSessionManager;
import org.telegram.messenger.exoplayer2.drm.FrameworkMediaCrypto;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelector;

/**
 * A factory for {@link ExoPlayer} instances.
 */
public final class ExoPlayerFactory {

  /**
   * The default maximum duration for which a video renderer can attempt to seamlessly join an
   * ongoing playback.
   */
  public static final long DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS = 5000;

  private ExoPlayerFactory() {}

  /**
   * Creates a {@link SimpleExoPlayer} instance. Must be called from a thread that has an associated
   * {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      LoadControl loadControl) {
    return newSimpleInstance(context, trackSelector, loadControl, null);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance. Must be called from a thread that has an associated
   * {@link Looper}. Available extension renderers are not used.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      LoadControl loadControl, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(context, trackSelector, loadControl,
        drmSessionManager, SimpleExoPlayer.EXTENSION_RENDERER_MODE_OFF);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance. Must be called from a thread that has an associated
   * {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param extensionRendererMode The extension renderer mode, which determines if and how available
   *     extension renderers are used. Note that extensions must be included in the application
   *     build for them to be considered available.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      LoadControl loadControl, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @SimpleExoPlayer.ExtensionRendererMode int extensionRendererMode) {
    return newSimpleInstance(context, trackSelector, loadControl, drmSessionManager,
        extensionRendererMode, DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS);
  }

  /**
   * Creates a {@link SimpleExoPlayer} instance. Must be called from a thread that has an associated
   * {@link Looper}.
   *
   * @param context A {@link Context}.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param extensionRendererMode The extension renderer mode, which determines if and how available
   *     extension renderers are used. Note that extensions must be included in the application
   *     build for them to be considered available.
   * @param allowedVideoJoiningTimeMs The maximum duration for which a video renderer can attempt to
   *     seamlessly join an ongoing playback.
   */
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector,
      LoadControl loadControl, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @SimpleExoPlayer.ExtensionRendererMode int extensionRendererMode,
      long allowedVideoJoiningTimeMs) {
    return new SimpleExoPlayer(context, trackSelector, loadControl, drmSessionManager,
        extensionRendererMode, allowedVideoJoiningTimeMs);
  }

  /**
   * Creates an {@link ExoPlayer} instance. Must be called from a thread that has an associated
   * {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   */
  public static ExoPlayer newInstance(Renderer[] renderers, TrackSelector trackSelector) {
    return newInstance(renderers, trackSelector, new DefaultLoadControl());
  }

  /**
   * Creates an {@link ExoPlayer} instance. Must be called from a thread that has an associated
   * {@link Looper}.
   *
   * @param renderers The {@link Renderer}s that will be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   */
  public static ExoPlayer newInstance(Renderer[] renderers, TrackSelector trackSelector,
      LoadControl loadControl) {
    return new ExoPlayerImpl(renderers, trackSelector, loadControl);
  }

}
