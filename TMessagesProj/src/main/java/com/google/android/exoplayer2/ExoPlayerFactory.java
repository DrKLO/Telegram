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

import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;

/** @deprecated Use {@link SimpleExoPlayer.Builder} or {@link ExoPlayer.Builder} instead. */
@Deprecated
public final class ExoPlayerFactory {

  private ExoPlayerFactory() {}

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode) {
    RenderersFactory renderersFactory =
        new DefaultRenderersFactory(context).setExtensionRendererMode(extensionRendererMode);
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode,
      long allowedVideoJoiningTimeMs) {
    RenderersFactory renderersFactory =
        new DefaultRenderersFactory(context)
            .setExtensionRendererMode(extensionRendererMode)
            .setAllowedVideoJoiningTimeMs(allowedVideoJoiningTimeMs);
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(Context context) {
    return newSimpleInstance(context, new DefaultTrackSelector(context));
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(Context context, TrackSelector trackSelector) {
    return newSimpleInstance(context, new DefaultRenderersFactory(context), trackSelector);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context, RenderersFactory renderersFactory, TrackSelector trackSelector) {
    return newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl());
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context, TrackSelector trackSelector, LoadControl loadControl) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager);
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(
        context, renderersFactory, trackSelector, new DefaultLoadControl(), drmSessionManager);
  }

  /** @deprecated Use {@link SimpleExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        /* drmSessionManager= */ null,
        Util.getLooper());
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    return newSimpleInstance(
        context, renderersFactory, trackSelector, loadControl, drmSessionManager, Util.getLooper());
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      BandwidthMeter bandwidthMeter) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        bandwidthMeter,
        new AnalyticsCollector(Clock.DEFAULT),
        Util.getLooper());
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AnalyticsCollector analyticsCollector) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        analyticsCollector,
        Util.getLooper());
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      Looper looper) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        new AnalyticsCollector(Clock.DEFAULT),
        looper);
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      AnalyticsCollector analyticsCollector,
      Looper looper) {
    return newSimpleInstance(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        DefaultBandwidthMeter.getSingletonInstance(context),
        analyticsCollector,
        looper);
  }

  /**
   * @deprecated Use {@link SimpleExoPlayer.Builder} instead. The {@link DrmSessionManager} cannot
   *     be passed to {@link SimpleExoPlayer.Builder} and should instead be injected into the {@link
   *     MediaSource} factories.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static SimpleExoPlayer newSimpleInstance(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      BandwidthMeter bandwidthMeter,
      AnalyticsCollector analyticsCollector,
      Looper looper) {
    return new SimpleExoPlayer(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        bandwidthMeter,
        analyticsCollector,
        Clock.DEFAULT,
        looper);
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector) {
    return newInstance(context, renderers, trackSelector, new DefaultLoadControl());
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ExoPlayer newInstance(
      Context context, Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
    return newInstance(context, renderers, trackSelector, loadControl, Util.getLooper());
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  public static ExoPlayer newInstance(
      Context context,
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      Looper looper) {
    return newInstance(
        context,
        renderers,
        trackSelector,
        loadControl,
        DefaultBandwidthMeter.getSingletonInstance(context),
        looper);
  }

  /** @deprecated Use {@link ExoPlayer.Builder} instead. */
  @Deprecated
  public static ExoPlayer newInstance(
      Context context,
      Renderer[] renderers,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      Looper looper) {
    return new ExoPlayerImpl(
        renderers, trackSelector, loadControl, bandwidthMeter, Clock.DEFAULT, looper);
  }
}
