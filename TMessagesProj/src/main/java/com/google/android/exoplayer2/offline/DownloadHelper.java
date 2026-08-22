/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSource.MediaSourceCaller;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.trackselection.TrackSelectionUtil;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A helper for initializing and removing downloads.
 *
 * <p>The helper extracts track information from the media, selects tracks for downloading, and
 * creates {@link DownloadRequest download requests} based on the selected tracks.
 *
 * <p>A typical usage of DownloadHelper follows these steps:
 *
 * <ol>
 *   <li>Build the helper using one of the {@code forMediaItem} methods.
 *   <li>Prepare the helper using {@link #prepare(Callback)} and wait for the callback.
 *   <li>Optional: Inspect the selected tracks using {@link #getMappedTrackInfo(int)} and {@link
 *       #getTrackSelections(int, int)}, and make adjustments using {@link
 *       #clearTrackSelections(int)}, {@link #replaceTrackSelections(int, TrackSelectionParameters)}
 *       and {@link #addTrackSelection(int, TrackSelectionParameters)}.
 *   <li>Create a download request for the selected track using {@link #getDownloadRequest(byte[])}.
 *   <li>Release the helper using {@link #release()}.
 * </ol>
 */
public final class DownloadHelper {

  /**
   * Default track selection parameters for downloading, but without any {@link Context}
   * constraints.
   *
   * <p>If possible, use {@link #getDefaultTrackSelectorParameters(Context)} instead.
   *
   * @see DefaultTrackSelector.Parameters#DEFAULT_WITHOUT_CONTEXT
   */
  public static final DefaultTrackSelector.Parameters
      DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT =
          DefaultTrackSelector.Parameters.DEFAULT_WITHOUT_CONTEXT
              .buildUpon()
              .setForceHighestSupportedBitrate(true)
              .setConstrainAudioChannelCountToDeviceCapabilities(false)
              .build();

  /** Returns the default parameters used for track selection for downloading. */
  public static DefaultTrackSelector.Parameters getDefaultTrackSelectorParameters(Context context) {
    return DefaultTrackSelector.Parameters.getDefaults(context)
        .buildUpon()
        .setForceHighestSupportedBitrate(true)
        .setConstrainAudioChannelCountToDeviceCapabilities(false)
        .build();
  }

  /** A callback to be notified when the {@link DownloadHelper} is prepared. */
  public interface Callback {

    /**
     * Called when preparation completes.
     *
     * @param helper The reporting {@link DownloadHelper}.
     */
    void onPrepared(DownloadHelper helper);

    /**
     * Called when preparation fails.
     *
     * @param helper The reporting {@link DownloadHelper}.
     * @param e The error.
     */
    void onPrepareError(DownloadHelper helper, IOException e);
  }

  /** Thrown at an attempt to download live content. */
  public static class LiveContentUnsupportedException extends IOException {}

  /**
   * Extracts renderer capabilities for the renderers created by the provided renderers factory.
   *
   * @param renderersFactory A {@link RenderersFactory}.
   * @return The {@link RendererCapabilities} for each renderer created by the {@code
   *     renderersFactory}.
   */
  public static RendererCapabilities[] getRendererCapabilities(RenderersFactory renderersFactory) {
    Renderer[] renderers =
        renderersFactory.createRenderers(
            Util.createHandlerForCurrentOrMainLooper(),
            new VideoRendererEventListener() {},
            new AudioRendererEventListener() {},
            (cues) -> {},
            (metadata) -> {});
    RendererCapabilities[] capabilities = new RendererCapabilities[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      capabilities[i] = renderers[i].getCapabilities();
    }
    return capabilities;
  }

  /**
   * @deprecated Use {@link #forMediaItem(Context, MediaItem)}
   */
  @Deprecated
  public static DownloadHelper forProgressive(Context context, Uri uri) {
    return forMediaItem(context, new MediaItem.Builder().setUri(uri).build());
  }

  /**
   * @deprecated Use {@link #forMediaItem(Context, MediaItem)}
   */
  @Deprecated
  public static DownloadHelper forProgressive(Context context, Uri uri, @Nullable String cacheKey) {
    return forMediaItem(
        context, new MediaItem.Builder().setUri(uri).setCustomCacheKey(cacheKey).build());
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory)} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static DownloadHelper forDash(
      Context context,
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory) {
    return forDash(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        getDefaultTrackSelectorParameters(context));
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory, DrmSessionManager)} instead.
   */
  @Deprecated
  public static DownloadHelper forDash(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager drmSessionManager,
      TrackSelectionParameters trackSelectionParameters) {
    return forMediaItem(
        new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_MPD).build(),
        trackSelectionParameters,
        renderersFactory,
        dataSourceFactory,
        drmSessionManager);
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory)} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static DownloadHelper forHls(
      Context context,
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory) {
    return forHls(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        getDefaultTrackSelectorParameters(context));
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory, DrmSessionManager)} instead.
   */
  @Deprecated
  public static DownloadHelper forHls(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager drmSessionManager,
      TrackSelectionParameters trackSelectionParameters) {
    return forMediaItem(
        new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
        trackSelectionParameters,
        renderersFactory,
        dataSourceFactory,
        drmSessionManager);
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory)} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static DownloadHelper forSmoothStreaming(
      Uri uri, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
    return forSmoothStreaming(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT);
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory)} instead.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static DownloadHelper forSmoothStreaming(
      Context context,
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory) {
    return forSmoothStreaming(
        uri,
        dataSourceFactory,
        renderersFactory,
        /* drmSessionManager= */ null,
        getDefaultTrackSelectorParameters(context));
  }

  /**
   * @deprecated Use {@link #forMediaItem(MediaItem, TrackSelectionParameters, RenderersFactory,
   *     DataSource.Factory, DrmSessionManager)} instead.
   */
  @Deprecated
  public static DownloadHelper forSmoothStreaming(
      Uri uri,
      DataSource.Factory dataSourceFactory,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager drmSessionManager,
      TrackSelectionParameters trackSelectionParameters) {
    return forMediaItem(
        new MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_SS).build(),
        trackSelectionParameters,
        renderersFactory,
        dataSourceFactory,
        drmSessionManager);
  }

  /**
   * Creates a {@link DownloadHelper} for the given progressive media item.
   *
   * @param context The context.
   * @param mediaItem A {@link MediaItem}.
   * @return A {@link DownloadHelper} for progressive streams.
   * @throws IllegalStateException If the media item is of type DASH, HLS or SmoothStreaming.
   */
  public static DownloadHelper forMediaItem(Context context, MediaItem mediaItem) {
    Assertions.checkArgument(isProgressive(checkNotNull(mediaItem.localConfiguration)));
    return forMediaItem(
        mediaItem,
        getDefaultTrackSelectorParameters(context),
        /* renderersFactory= */ null,
        /* dataSourceFactory= */ null,
        /* drmSessionManager= */ null);
  }

  /**
   * Creates a {@link DownloadHelper} for the given media item.
   *
   * @param context The context.
   * @param mediaItem A {@link MediaItem}.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest for adaptive
   *     streams. This argument is required for adaptive streams and ignored for progressive
   *     streams.
   * @return A {@link DownloadHelper}.
   * @throws IllegalStateException If the corresponding module is missing for DASH, HLS or
   *     SmoothStreaming media items.
   * @throws IllegalArgumentException If the {@code dataSourceFactory} is null for adaptive streams.
   */
  public static DownloadHelper forMediaItem(
      Context context,
      MediaItem mediaItem,
      @Nullable RenderersFactory renderersFactory,
      @Nullable DataSource.Factory dataSourceFactory) {
    return forMediaItem(
        mediaItem,
        getDefaultTrackSelectorParameters(context),
        renderersFactory,
        dataSourceFactory,
        /* drmSessionManager= */ null);
  }

  /**
   * Creates a {@link DownloadHelper} for the given media item.
   *
   * @param mediaItem A {@link MediaItem}.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @param trackSelectionParameters {@link TrackSelectionParameters} for selecting tracks for
   *     downloading.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest for adaptive
   *     streams. This argument is required for adaptive streams and ignored for progressive
   *     streams.
   * @return A {@link DownloadHelper}.
   * @throws IllegalStateException If the corresponding module is missing for DASH, HLS or
   *     SmoothStreaming media items.
   * @throws IllegalArgumentException If the {@code dataSourceFactory} is null for adaptive streams.
   */
  public static DownloadHelper forMediaItem(
      MediaItem mediaItem,
      TrackSelectionParameters trackSelectionParameters,
      @Nullable RenderersFactory renderersFactory,
      @Nullable DataSource.Factory dataSourceFactory) {
    return forMediaItem(
        mediaItem,
        trackSelectionParameters,
        renderersFactory,
        dataSourceFactory,
        /* drmSessionManager= */ null);
  }

  /**
   * Creates a {@link DownloadHelper} for the given media item.
   *
   * @param mediaItem A {@link MediaItem}.
   * @param renderersFactory A {@link RenderersFactory} creating the renderers for which tracks are
   *     selected.
   * @param trackSelectionParameters {@link TrackSelectionParameters} for selecting tracks for
   *     downloading.
   * @param dataSourceFactory A {@link DataSource.Factory} used to load the manifest for adaptive
   *     streams. This argument is required for adaptive streams and ignored for progressive
   *     streams.
   * @param drmSessionManager An optional {@link DrmSessionManager}. Used to help determine which
   *     tracks can be selected.
   * @return A {@link DownloadHelper}.
   * @throws IllegalStateException If the corresponding module is missing for DASH, HLS or
   *     SmoothStreaming media items.
   * @throws IllegalArgumentException If the {@code dataSourceFactory} is null for adaptive streams.
   */
  public static DownloadHelper forMediaItem(
      MediaItem mediaItem,
      TrackSelectionParameters trackSelectionParameters,
      @Nullable RenderersFactory renderersFactory,
      @Nullable DataSource.Factory dataSourceFactory,
      @Nullable DrmSessionManager drmSessionManager) {
    boolean isProgressive = isProgressive(checkNotNull(mediaItem.localConfiguration));
    Assertions.checkArgument(isProgressive || dataSourceFactory != null);
    return new DownloadHelper(
        mediaItem,
        isProgressive
            ? null
            : createMediaSourceInternal(
                mediaItem, castNonNull(dataSourceFactory), drmSessionManager),
        trackSelectionParameters,
        renderersFactory != null
            ? getRendererCapabilities(renderersFactory)
            : new RendererCapabilities[0]);
  }

  /**
   * Equivalent to {@link #createMediaSource(DownloadRequest, DataSource.Factory, DrmSessionManager)
   * createMediaSource(downloadRequest, dataSourceFactory, null)}.
   */
  public static MediaSource createMediaSource(
      DownloadRequest downloadRequest, DataSource.Factory dataSourceFactory) {
    return createMediaSource(downloadRequest, dataSourceFactory, /* drmSessionManager= */ null);
  }

  /**
   * Utility method to create a {@link MediaSource} that only exposes the tracks defined in {@code
   * downloadRequest}.
   *
   * @param downloadRequest A {@link DownloadRequest}.
   * @param dataSourceFactory A factory for {@link DataSource}s to read the media.
   * @param drmSessionManager An optional {@link DrmSessionManager} to be passed to the {@link
   *     MediaSource}.
   * @return A {@link MediaSource} that only exposes the tracks defined in {@code downloadRequest}.
   */
  public static MediaSource createMediaSource(
      DownloadRequest downloadRequest,
      DataSource.Factory dataSourceFactory,
      @Nullable DrmSessionManager drmSessionManager) {
    return createMediaSourceInternal(
        downloadRequest.toMediaItem(), dataSourceFactory, drmSessionManager);
  }

  private final MediaItem.LocalConfiguration localConfiguration;
  @Nullable private final MediaSource mediaSource;
  private final DefaultTrackSelector trackSelector;
  private final RendererCapabilities[] rendererCapabilities;
  private final SparseIntArray scratchSet;
  private final Handler callbackHandler;
  private final Timeline.Window window;

  private boolean isPreparedWithMedia;
  private @MonotonicNonNull Callback callback;
  private @MonotonicNonNull MediaPreparer mediaPreparer;
  private TrackGroupArray @MonotonicNonNull [] trackGroupArrays;
  private MappedTrackInfo @MonotonicNonNull [] mappedTrackInfos;
  private List<ExoTrackSelection> @MonotonicNonNull [][] trackSelectionsByPeriodAndRenderer;
  private List<ExoTrackSelection> @MonotonicNonNull [][]
      immutableTrackSelectionsByPeriodAndRenderer;

  /**
   * Creates download helper.
   *
   * @param mediaItem The media item.
   * @param mediaSource A {@link MediaSource} for which tracks are selected, or null if no track
   *     selection needs to be made.
   * @param trackSelectionParameters {@link TrackSelectionParameters} for selecting tracks for
   *     downloading.
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers for which tracks
   *     are selected.
   */
  public DownloadHelper(
      MediaItem mediaItem,
      @Nullable MediaSource mediaSource,
      TrackSelectionParameters trackSelectionParameters,
      RendererCapabilities[] rendererCapabilities) {
    this.localConfiguration = checkNotNull(mediaItem.localConfiguration);
    this.mediaSource = mediaSource;
    this.trackSelector =
        new DefaultTrackSelector(trackSelectionParameters, new DownloadTrackSelection.Factory());
    this.rendererCapabilities = rendererCapabilities;
    this.scratchSet = new SparseIntArray();
    trackSelector.init(/* listener= */ () -> {}, new FakeBandwidthMeter());
    callbackHandler = Util.createHandlerForCurrentOrMainLooper();
    window = new Timeline.Window();
  }

  /**
   * Initializes the helper for starting a download.
   *
   * @param callback A callback to be notified when preparation completes or fails.
   * @throws IllegalStateException If the download helper has already been prepared.
   */
  public void prepare(Callback callback) {
    Assertions.checkState(this.callback == null);
    this.callback = callback;
    if (mediaSource != null) {
      mediaPreparer = new MediaPreparer(mediaSource, /* downloadHelper= */ this);
    } else {
      callbackHandler.post(() -> callback.onPrepared(this));
    }
  }

  /** Releases the helper and all resources it is holding. */
  public void release() {
    if (mediaPreparer != null) {
      mediaPreparer.release();
    }
    trackSelector.release();
  }

  /**
   * Returns the manifest, or null if no manifest is loaded. Must not be called until after
   * preparation completes.
   */
  @Nullable
  public Object getManifest() {
    if (mediaSource == null) {
      return null;
    }
    assertPreparedWithMedia();
    return mediaPreparer.timeline.getWindowCount() > 0
        ? mediaPreparer.timeline.getWindow(/* windowIndex= */ 0, window).manifest
        : null;
  }

  /**
   * Returns the number of periods for which media is available. Must not be called until after
   * preparation completes.
   */
  public int getPeriodCount() {
    if (mediaSource == null) {
      return 0;
    }
    assertPreparedWithMedia();
    return trackGroupArrays.length;
  }

  /**
   * Returns {@link Tracks} for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The {@link Tracks} for the period. May be {@link Tracks#EMPTY} for single stream
   *     content.
   */
  public Tracks getTracks(int periodIndex) {
    assertPreparedWithMedia();
    return TrackSelectionUtil.buildTracks(
        mappedTrackInfos[periodIndex], immutableTrackSelectionsByPeriodAndRenderer[periodIndex]);
  }

  /**
   * Returns the track groups for the given period. Must not be called until after preparation
   * completes.
   *
   * <p>Use {@link #getMappedTrackInfo(int)} to get the track groups mapped to renderers.
   *
   * @param periodIndex The period index.
   * @return The track groups for the period. May be {@link TrackGroupArray#EMPTY} for single stream
   *     content.
   */
  public TrackGroupArray getTrackGroups(int periodIndex) {
    assertPreparedWithMedia();
    return trackGroupArrays[periodIndex];
  }

  /**
   * Returns the mapped track info for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The {@link MappedTrackInfo} for the period.
   */
  public MappedTrackInfo getMappedTrackInfo(int periodIndex) {
    assertPreparedWithMedia();
    return mappedTrackInfos[periodIndex];
  }

  /**
   * Returns all {@link ExoTrackSelection track selections} for a period and renderer. Must not be
   * called until after preparation completes.
   *
   * @param periodIndex The period index.
   * @param rendererIndex The renderer index.
   * @return A list of selected {@link ExoTrackSelection track selections}.
   */
  public List<ExoTrackSelection> getTrackSelections(int periodIndex, int rendererIndex) {
    assertPreparedWithMedia();
    return immutableTrackSelectionsByPeriodAndRenderer[periodIndex][rendererIndex];
  }

  /**
   * Clears the selection of tracks for a period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which track selections are cleared.
   */
  public void clearTrackSelections(int periodIndex) {
    assertPreparedWithMedia();
    for (int i = 0; i < rendererCapabilities.length; i++) {
      trackSelectionsByPeriodAndRenderer[periodIndex][i].clear();
    }
  }

  /**
   * Replaces a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which the track selection is replaced.
   * @param trackSelectionParameters The {@link TrackSelectionParameters} to obtain the new
   *     selection of tracks.
   */
  public void replaceTrackSelections(
      int periodIndex, TrackSelectionParameters trackSelectionParameters) {
    try {
      assertPreparedWithMedia();
      clearTrackSelections(periodIndex);
      addTrackSelectionInternal(periodIndex, trackSelectionParameters);
    } catch (ExoPlaybackException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Adds a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index this track selection is added for.
   * @param trackSelectionParameters The {@link TrackSelectionParameters} to obtain the new
   *     selection of tracks.
   */
  public void addTrackSelection(
      int periodIndex, TrackSelectionParameters trackSelectionParameters) {
    try {
      assertPreparedWithMedia();
      addTrackSelectionInternal(periodIndex, trackSelectionParameters);
    } catch (ExoPlaybackException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Convenience method to add selections of tracks for all specified audio languages. If an audio
   * track in one of the specified languages is not available, the default fallback audio track is
   * used instead. Must not be called until after preparation completes.
   *
   * @param languages A list of audio languages for which tracks should be added to the download
   *     selection, as IETF BCP 47 conformant tags.
   */
  public void addAudioLanguagesToSelection(String... languages) {
    try {
      assertPreparedWithMedia();

      TrackSelectionParameters.Builder parametersBuilder =
          DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT.buildUpon();
      // Prefer highest supported bitrate for downloads.
      parametersBuilder.setForceHighestSupportedBitrate(true);
      // Disable all non-audio track types supported by the renderers.
      for (RendererCapabilities capabilities : rendererCapabilities) {
        @C.TrackType int trackType = capabilities.getTrackType();
        parametersBuilder.setTrackTypeDisabled(
            trackType, /* disabled= */ trackType != C.TRACK_TYPE_AUDIO);
      }

      // Add a track selection to each period for each of the languages.
      int periodCount = getPeriodCount();
      for (String language : languages) {
        TrackSelectionParameters parameters =
            parametersBuilder.setPreferredAudioLanguage(language).build();
        for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
          addTrackSelectionInternal(periodIndex, parameters);
        }
      }
    } catch (ExoPlaybackException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Convenience method to add selections of tracks for all specified text languages. Must not be
   * called until after preparation completes.
   *
   * @param selectUndeterminedTextLanguage Whether a text track with undetermined language should be
   *     selected for downloading if no track with one of the specified {@code languages} is
   *     available.
   * @param languages A list of text languages for which tracks should be added to the download
   *     selection, as IETF BCP 47 conformant tags.
   */
  public void addTextLanguagesToSelection(
      boolean selectUndeterminedTextLanguage, String... languages) {
    try {
      assertPreparedWithMedia();

      TrackSelectionParameters.Builder parametersBuilder =
          DEFAULT_TRACK_SELECTOR_PARAMETERS_WITHOUT_CONTEXT.buildUpon();
      parametersBuilder.setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage);
      // Prefer highest supported bitrate for downloads.
      parametersBuilder.setForceHighestSupportedBitrate(true);
      // Disable all non-text track types supported by the renderers.
      for (RendererCapabilities capabilities : rendererCapabilities) {
        @C.TrackType int trackType = capabilities.getTrackType();
        parametersBuilder.setTrackTypeDisabled(
            trackType, /* disabled= */ trackType != C.TRACK_TYPE_TEXT);
      }

      // Add a track selection to each period for each of the languages.
      int periodCount = getPeriodCount();
      for (String language : languages) {
        TrackSelectionParameters parameters =
            parametersBuilder.setPreferredTextLanguage(language).build();
        for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
          addTrackSelectionInternal(periodIndex, parameters);
        }
      }
    } catch (ExoPlaybackException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Convenience method to add a selection of tracks to be downloaded for a single renderer. Must
   * not be called until after preparation completes.
   *
   * @param periodIndex The period index the track selection is added for.
   * @param rendererIndex The renderer index the track selection is added for.
   * @param trackSelectorParameters The {@link DefaultTrackSelector.Parameters} to obtain the new
   *     selection of tracks.
   * @param overrides A list of {@link SelectionOverride SelectionOverrides} to apply to the {@code
   *     trackSelectorParameters}. If empty, {@code trackSelectorParameters} are used as they are.
   */
  public void addTrackSelectionForSingleRenderer(
      int periodIndex,
      int rendererIndex,
      DefaultTrackSelector.Parameters trackSelectorParameters,
      List<SelectionOverride> overrides) {
    try {
      assertPreparedWithMedia();
      DefaultTrackSelector.Parameters.Builder builder = trackSelectorParameters.buildUpon();
      for (int i = 0; i < mappedTrackInfos[periodIndex].getRendererCount(); i++) {
        builder.setRendererDisabled(/* rendererIndex= */ i, /* disabled= */ i != rendererIndex);
      }
      if (overrides.isEmpty()) {
        addTrackSelectionInternal(periodIndex, builder.build());
      } else {
        TrackGroupArray trackGroupArray =
            mappedTrackInfos[periodIndex].getTrackGroups(rendererIndex);
        for (int i = 0; i < overrides.size(); i++) {
          builder.setSelectionOverride(rendererIndex, trackGroupArray, overrides.get(i));
          addTrackSelectionInternal(periodIndex, builder.build());
        }
      }
    } catch (ExoPlaybackException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Builds a {@link DownloadRequest} for downloading the selected tracks. Must not be called until
   * after preparation completes. The uri of the {@link DownloadRequest} will be used as content id.
   *
   * @param data Application provided data to store in {@link DownloadRequest#data}.
   * @return The built {@link DownloadRequest}.
   */
  public DownloadRequest getDownloadRequest(@Nullable byte[] data) {
    return getDownloadRequest(localConfiguration.uri.toString(), data);
  }

  /**
   * Builds a {@link DownloadRequest} for downloading the selected tracks. Must not be called until
   * after preparation completes.
   *
   * @param id The unique content id.
   * @param data Application provided data to store in {@link DownloadRequest#data}.
   * @return The built {@link DownloadRequest}.
   */
  public DownloadRequest getDownloadRequest(String id, @Nullable byte[] data) {
    DownloadRequest.Builder requestBuilder =
        new DownloadRequest.Builder(id, localConfiguration.uri)
            .setMimeType(localConfiguration.mimeType)
            .setKeySetId(
                localConfiguration.drmConfiguration != null
                    ? localConfiguration.drmConfiguration.getKeySetId()
                    : null)
            .setCustomCacheKey(localConfiguration.customCacheKey)
            .setData(data);
    if (mediaSource == null) {
      return requestBuilder.build();
    }
    assertPreparedWithMedia();
    List<StreamKey> streamKeys = new ArrayList<>();
    List<ExoTrackSelection> allSelections = new ArrayList<>();
    int periodCount = trackSelectionsByPeriodAndRenderer.length;
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      allSelections.clear();
      int rendererCount = trackSelectionsByPeriodAndRenderer[periodIndex].length;
      for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
        allSelections.addAll(trackSelectionsByPeriodAndRenderer[periodIndex][rendererIndex]);
      }
      streamKeys.addAll(mediaPreparer.mediaPeriods[periodIndex].getStreamKeys(allSelections));
    }
    return requestBuilder.setStreamKeys(streamKeys).build();
  }

  @RequiresNonNull({
    "trackGroupArrays",
    "trackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline"
  })
  private void addTrackSelectionInternal(
      int periodIndex, TrackSelectionParameters trackSelectionParameters)
      throws ExoPlaybackException {
    trackSelector.setParameters(trackSelectionParameters);
    runTrackSelection(periodIndex);
    // TrackSelectionParameters can contain multiple overrides for each track type. The track
    // selector will only use one of them (because it's designed for playback), but for downloads we
    // want to use all of them. Run selection again with each override being the only one of its
    // type, to ensure that all of the desired tracks are included.
    for (TrackSelectionOverride override : trackSelectionParameters.overrides.values()) {
      trackSelector.setParameters(
          trackSelectionParameters.buildUpon().setOverrideForType(override).build());
      runTrackSelection(periodIndex);
    }
  }

  @SuppressWarnings("unchecked") // Initialization of array of Lists.
  private void onMediaPrepared() throws ExoPlaybackException {
    checkNotNull(mediaPreparer);
    checkNotNull(mediaPreparer.mediaPeriods);
    checkNotNull(mediaPreparer.timeline);
    int periodCount = mediaPreparer.mediaPeriods.length;
    int rendererCount = rendererCapabilities.length;
    trackSelectionsByPeriodAndRenderer =
        (List<ExoTrackSelection>[][]) new List<?>[periodCount][rendererCount];
    immutableTrackSelectionsByPeriodAndRenderer =
        (List<ExoTrackSelection>[][]) new List<?>[periodCount][rendererCount];
    for (int i = 0; i < periodCount; i++) {
      for (int j = 0; j < rendererCount; j++) {
        trackSelectionsByPeriodAndRenderer[i][j] = new ArrayList<>();
        immutableTrackSelectionsByPeriodAndRenderer[i][j] =
            Collections.unmodifiableList(trackSelectionsByPeriodAndRenderer[i][j]);
      }
    }
    trackGroupArrays = new TrackGroupArray[periodCount];
    mappedTrackInfos = new MappedTrackInfo[periodCount];
    for (int i = 0; i < periodCount; i++) {
      trackGroupArrays[i] = mediaPreparer.mediaPeriods[i].getTrackGroups();
      TrackSelectorResult trackSelectorResult = runTrackSelection(/* periodIndex= */ i);
      trackSelector.onSelectionActivated(trackSelectorResult.info);
      mappedTrackInfos[i] = checkNotNull(trackSelector.getCurrentMappedTrackInfo());
    }
    setPreparedWithMedia();
    checkNotNull(callbackHandler).post(() -> checkNotNull(callback).onPrepared(this));
  }

  private void onMediaPreparationFailed(IOException error) {
    checkNotNull(callbackHandler).post(() -> checkNotNull(callback).onPrepareError(this, error));
  }

  @RequiresNonNull({
    "trackGroupArrays",
    "mappedTrackInfos",
    "trackSelectionsByPeriodAndRenderer",
    "immutableTrackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline",
    "mediaPreparer.mediaPeriods"
  })
  private void setPreparedWithMedia() {
    isPreparedWithMedia = true;
  }

  @EnsuresNonNull({
    "trackGroupArrays",
    "mappedTrackInfos",
    "trackSelectionsByPeriodAndRenderer",
    "immutableTrackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline",
    "mediaPreparer.mediaPeriods"
  })
  @SuppressWarnings("nullness:contracts.postcondition")
  private void assertPreparedWithMedia() {
    Assertions.checkState(isPreparedWithMedia);
  }

  /**
   * Runs the track selection for a given period index with the current parameters. The selected
   * tracks will be added to {@link #trackSelectionsByPeriodAndRenderer}.
   */
  @RequiresNonNull({
    "trackGroupArrays",
    "trackSelectionsByPeriodAndRenderer",
    "mediaPreparer",
    "mediaPreparer.timeline"
  })
  private TrackSelectorResult runTrackSelection(int periodIndex) throws ExoPlaybackException {
    TrackSelectorResult trackSelectorResult =
        trackSelector.selectTracks(
            rendererCapabilities,
            trackGroupArrays[periodIndex],
            new MediaPeriodId(mediaPreparer.timeline.getUidOfPeriod(periodIndex)),
            mediaPreparer.timeline);
    for (int i = 0; i < trackSelectorResult.length; i++) {
      @Nullable ExoTrackSelection newSelection = trackSelectorResult.selections[i];
      if (newSelection == null) {
        continue;
      }
      List<ExoTrackSelection> existingSelectionList =
          trackSelectionsByPeriodAndRenderer[periodIndex][i];
      boolean mergedWithExistingSelection = false;
      for (int j = 0; j < existingSelectionList.size(); j++) {
        ExoTrackSelection existingSelection = existingSelectionList.get(j);
        if (existingSelection.getTrackGroup().equals(newSelection.getTrackGroup())) {
          // Merge with existing selection.
          scratchSet.clear();
          for (int k = 0; k < existingSelection.length(); k++) {
            scratchSet.put(existingSelection.getIndexInTrackGroup(k), 0);
          }
          for (int k = 0; k < newSelection.length(); k++) {
            scratchSet.put(newSelection.getIndexInTrackGroup(k), 0);
          }
          int[] mergedTracks = new int[scratchSet.size()];
          for (int k = 0; k < scratchSet.size(); k++) {
            mergedTracks[k] = scratchSet.keyAt(k);
          }
          existingSelectionList.set(
              j, new DownloadTrackSelection(existingSelection.getTrackGroup(), mergedTracks));
          mergedWithExistingSelection = true;
          break;
        }
      }
      if (!mergedWithExistingSelection) {
        existingSelectionList.add(newSelection);
      }
    }
    return trackSelectorResult;
  }

  private static MediaSource createMediaSourceInternal(
      MediaItem mediaItem,
      DataSource.Factory dataSourceFactory,
      @Nullable DrmSessionManager drmSessionManager) {
    DefaultMediaSourceFactory mediaSourceFactory =
        new DefaultMediaSourceFactory(dataSourceFactory, ExtractorsFactory.EMPTY);
    if (drmSessionManager != null) {
      mediaSourceFactory.setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager);
    }
    return mediaSourceFactory.createMediaSource(mediaItem);
  }

  private static boolean isProgressive(MediaItem.LocalConfiguration localConfiguration) {
    return Util.inferContentTypeForUriAndMimeType(
            localConfiguration.uri, localConfiguration.mimeType)
        == C.CONTENT_TYPE_OTHER;
  }

  private static final class MediaPreparer
      implements MediaSourceCaller, MediaPeriod.Callback, Handler.Callback {

    private static final int MESSAGE_PREPARE_SOURCE = 0;
    private static final int MESSAGE_CHECK_FOR_FAILURE = 1;
    private static final int MESSAGE_CONTINUE_LOADING = 2;
    private static final int MESSAGE_RELEASE = 3;

    private static final int DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED = 0;
    private static final int DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED = 1;

    private final MediaSource mediaSource;
    private final DownloadHelper downloadHelper;
    private final Allocator allocator;
    private final ArrayList<MediaPeriod> pendingMediaPeriods;
    private final Handler downloadHelperHandler;
    private final HandlerThread mediaSourceThread;
    private final Handler mediaSourceHandler;

    public @MonotonicNonNull Timeline timeline;
    public MediaPeriod @MonotonicNonNull [] mediaPeriods;

    private boolean released;

    public MediaPreparer(MediaSource mediaSource, DownloadHelper downloadHelper) {
      this.mediaSource = mediaSource;
      this.downloadHelper = downloadHelper;
      allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
      pendingMediaPeriods = new ArrayList<>();
      @SuppressWarnings("nullness:methodref.receiver.bound")
      Handler downloadThreadHandler =
          Util.createHandlerForCurrentOrMainLooper(this::handleDownloadHelperCallbackMessage);
      this.downloadHelperHandler = downloadThreadHandler;
      mediaSourceThread = new HandlerThread("ExoPlayer:DownloadHelper");
      mediaSourceThread.start();
      mediaSourceHandler = Util.createHandler(mediaSourceThread.getLooper(), /* callback= */ this);
      mediaSourceHandler.sendEmptyMessage(MESSAGE_PREPARE_SOURCE);
    }

    public void release() {
      if (released) {
        return;
      }
      released = true;
      mediaSourceHandler.sendEmptyMessage(MESSAGE_RELEASE);
    }

    // Handler.Callback

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_PREPARE_SOURCE:
          mediaSource.prepareSource(
              /* caller= */ this, /* mediaTransferListener= */ null, PlayerId.UNSET);
          mediaSourceHandler.sendEmptyMessage(MESSAGE_CHECK_FOR_FAILURE);
          return true;
        case MESSAGE_CHECK_FOR_FAILURE:
          try {
            if (mediaPeriods == null) {
              mediaSource.maybeThrowSourceInfoRefreshError();
            } else {
              for (int i = 0; i < pendingMediaPeriods.size(); i++) {
                pendingMediaPeriods.get(i).maybeThrowPrepareError();
              }
            }
            mediaSourceHandler.sendEmptyMessageDelayed(
                MESSAGE_CHECK_FOR_FAILURE, /* delayMillis= */ 100);
          } catch (IOException e) {
            downloadHelperHandler
                .obtainMessage(DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED, /* obj= */ e)
                .sendToTarget();
          }
          return true;
        case MESSAGE_CONTINUE_LOADING:
          MediaPeriod mediaPeriod = (MediaPeriod) msg.obj;
          if (pendingMediaPeriods.contains(mediaPeriod)) {
            mediaPeriod.continueLoading(/* positionUs= */ 0);
          }
          return true;
        case MESSAGE_RELEASE:
          if (mediaPeriods != null) {
            for (MediaPeriod period : mediaPeriods) {
              mediaSource.releasePeriod(period);
            }
          }
          mediaSource.releaseSource(this);
          mediaSourceHandler.removeCallbacksAndMessages(null);
          mediaSourceThread.quit();
          return true;
        default:
          return false;
      }
    }

    // MediaSource.MediaSourceCaller implementation.

    @Override
    public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
      if (this.timeline != null) {
        // Ignore dynamic updates.
        return;
      }
      if (timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).isLive()) {
        downloadHelperHandler
            .obtainMessage(
                DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED,
                /* obj= */ new LiveContentUnsupportedException())
            .sendToTarget();
        return;
      }
      this.timeline = timeline;
      mediaPeriods = new MediaPeriod[timeline.getPeriodCount()];
      for (int i = 0; i < mediaPeriods.length; i++) {
        MediaPeriod mediaPeriod =
            mediaSource.createPeriod(
                new MediaPeriodId(timeline.getUidOfPeriod(/* periodIndex= */ i)),
                allocator,
                /* startPositionUs= */ 0);
        mediaPeriods[i] = mediaPeriod;
        pendingMediaPeriods.add(mediaPeriod);
      }
      for (MediaPeriod mediaPeriod : mediaPeriods) {
        mediaPeriod.prepare(/* callback= */ this, /* positionUs= */ 0);
      }
    }

    // MediaPeriod.Callback implementation.

    @Override
    public void onPrepared(MediaPeriod mediaPeriod) {
      pendingMediaPeriods.remove(mediaPeriod);
      if (pendingMediaPeriods.isEmpty()) {
        mediaSourceHandler.removeMessages(MESSAGE_CHECK_FOR_FAILURE);
        downloadHelperHandler.sendEmptyMessage(DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED);
      }
    }

    @Override
    public void onContinueLoadingRequested(MediaPeriod mediaPeriod) {
      if (pendingMediaPeriods.contains(mediaPeriod)) {
        mediaSourceHandler.obtainMessage(MESSAGE_CONTINUE_LOADING, mediaPeriod).sendToTarget();
      }
    }

    private boolean handleDownloadHelperCallbackMessage(Message msg) {
      if (released) {
        // Stale message.
        return false;
      }
      switch (msg.what) {
        case DOWNLOAD_HELPER_CALLBACK_MESSAGE_PREPARED:
          try {
            downloadHelper.onMediaPrepared();
          } catch (ExoPlaybackException e) {
            downloadHelperHandler
                .obtainMessage(
                    DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED, /* obj= */ new IOException(e))
                .sendToTarget();
          }
          return true;
        case DOWNLOAD_HELPER_CALLBACK_MESSAGE_FAILED:
          release();
          downloadHelper.onMediaPreparationFailed((IOException) castNonNull(msg.obj));
          return true;
        default:
          return false;
      }
    }
  }

  private static final class DownloadTrackSelection extends BaseTrackSelection {

    private static final class Factory implements ExoTrackSelection.Factory {

      @Override
      public @NullableType ExoTrackSelection[] createTrackSelections(
          @NullableType Definition[] definitions,
          BandwidthMeter bandwidthMeter,
          MediaPeriodId mediaPeriodId,
          Timeline timeline) {
        @NullableType ExoTrackSelection[] selections = new ExoTrackSelection[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
          selections[i] =
              definitions[i] == null
                  ? null
                  : new DownloadTrackSelection(definitions[i].group, definitions[i].tracks);
        }
        return selections;
      }
    }

    public DownloadTrackSelection(TrackGroup trackGroup, int[] tracks) {
      super(trackGroup, tracks);
    }

    @Override
    public int getSelectedIndex() {
      return 0;
    }

    @Override
    public @C.SelectionReason int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Override
    @Nullable
    public Object getSelectionData() {
      return null;
    }

    @Override
    public void updateSelectedTrack(
        long playbackPositionUs,
        long bufferedDurationUs,
        long availableDurationUs,
        List<? extends MediaChunk> queue,
        MediaChunkIterator[] mediaChunkIterators) {
      // Do nothing.
    }
  }

  private static final class FakeBandwidthMeter implements BandwidthMeter {

    @Override
    public long getBitrateEstimate() {
      return 0;
    }

    @Override
    @Nullable
    public TransferListener getTransferListener() {
      return null;
    }

    @Override
    public void addEventListener(Handler eventHandler, EventListener eventListener) {
      // Do nothing.
    }

    @Override
    public void removeEventListener(EventListener eventListener) {
      // Do nothing.
    }
  }
}
