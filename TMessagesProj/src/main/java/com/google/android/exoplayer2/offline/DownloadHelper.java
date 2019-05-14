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

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
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
 * creates {@link DownloadAction download actions} based on the selected tracks.
 *
 * <p>A typical usage of DownloadHelper follows these steps:
 *
 * <ol>
 *   <li>Construct the download helper with information about the {@link RenderersFactory renderers}
 *       and {@link DefaultTrackSelector.Parameters parameters} for track selection.
 *   <li>Prepare the helper using {@link #prepare(Callback)} and wait for the callback.
 *   <li>Optional: Inspect the selected tracks using {@link #getMappedTrackInfo(int)} and {@link
 *       #getTrackSelections(int, int)}, and make adjustments using {@link
 *       #clearTrackSelections(int)}, {@link #replaceTrackSelections(int, Parameters)} and {@link
 *       #addTrackSelection(int, Parameters)}.
 *   <li>Create download actions for the selected track using {@link #getDownloadAction(byte[])}.
 * </ol>
 *
 * @param <T> The manifest type.
 */
public abstract class DownloadHelper<T> {

  /**
   * The default parameters used for track selection for downloading. This default selects the
   * highest bitrate audio and video tracks which are supported by the renderers.
   */
  public static final DefaultTrackSelector.Parameters DEFAULT_TRACK_SELECTOR_PARAMETERS =
      new DefaultTrackSelector.ParametersBuilder().setForceHighestSupportedBitrate(true).build();

  /** A callback to be notified when the {@link DownloadHelper} is prepared. */
  public interface Callback {

    /**
     * Called when preparation completes.
     *
     * @param helper The reporting {@link DownloadHelper}.
     */
    void onPrepared(DownloadHelper<?> helper);

    /**
     * Called when preparation fails.
     *
     * @param helper The reporting {@link DownloadHelper}.
     * @param e The error.
     */
    void onPrepareError(DownloadHelper<?> helper, IOException e);
  }

  private final String downloadType;
  private final Uri uri;
  @Nullable private final String cacheKey;
  private final DefaultTrackSelector trackSelector;
  private final RendererCapabilities[] rendererCapabilities;
  private final SparseIntArray scratchSet;

  private int currentTrackSelectionPeriodIndex;
  @Nullable private T manifest;
  private TrackGroupArray @MonotonicNonNull [] trackGroupArrays;
  private MappedTrackInfo @MonotonicNonNull [] mappedTrackInfos;
  private List<TrackSelection> @MonotonicNonNull [][] trackSelectionsByPeriodAndRenderer;
  private List<TrackSelection> @MonotonicNonNull [][] immutableTrackSelectionsByPeriodAndRenderer;

  /**
   * Creates download helper.
   *
   * @param downloadType A download type. This value will be used as {@link DownloadAction#type}.
   * @param uri A {@link Uri}.
   * @param cacheKey An optional cache key.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @param renderersFactory The {@link RenderersFactory} creating the renderers for which tracks
   *     are selected.
   * @param drmSessionManager An optional {@link DrmSessionManager} used by the renderers created by
   *     {@code renderersFactory}.
   */
  public DownloadHelper(
      String downloadType,
      Uri uri,
      @Nullable String cacheKey,
      DefaultTrackSelector.Parameters trackSelectorParameters,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    this.downloadType = downloadType;
    this.uri = uri;
    this.cacheKey = cacheKey;
    this.trackSelector = new DefaultTrackSelector(new DownloadTrackSelection.Factory());
    this.rendererCapabilities = Util.getRendererCapabilities(renderersFactory, drmSessionManager);
    this.scratchSet = new SparseIntArray();
    trackSelector.setParameters(trackSelectorParameters);
    trackSelector.init(/* listener= */ () -> {}, new DummyBandwidthMeter());
  }

  /**
   * Initializes the helper for starting a download.
   *
   * @param callback A callback to be notified when preparation completes or fails. The callback
   *     will be invoked on the calling thread unless that thread does not have an associated {@link
   *     Looper}, in which case it will be called on the application's main thread.
   */
  public final void prepare(Callback callback) {
    Handler handler =
        new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
    new Thread(
            () -> {
              try {
                manifest = loadManifest(uri);
                trackGroupArrays = getTrackGroupArrays(manifest);
                initializeTrackSelectionLists(trackGroupArrays.length, rendererCapabilities.length);
                mappedTrackInfos = new MappedTrackInfo[trackGroupArrays.length];
                for (int i = 0; i < trackGroupArrays.length; i++) {
                  TrackSelectorResult trackSelectorResult = runTrackSelection(/* periodIndex= */ i);
                  trackSelector.onSelectionActivated(trackSelectorResult.info);
                  mappedTrackInfos[i] =
                      Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
                }
                handler.post(() -> callback.onPrepared(DownloadHelper.this));
              } catch (final IOException e) {
                handler.post(() -> callback.onPrepareError(DownloadHelper.this, e));
              }
            })
        .start();
  }

  /** Returns the manifest. Must not be called until after preparation completes. */
  public final T getManifest() {
    Assertions.checkNotNull(manifest);
    return manifest;
  }

  /**
   * Returns the number of periods for which media is available. Must not be called until after
   * preparation completes.
   */
  public final int getPeriodCount() {
    Assertions.checkNotNull(trackGroupArrays);
    return trackGroupArrays.length;
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
  public final TrackGroupArray getTrackGroups(int periodIndex) {
    Assertions.checkNotNull(trackGroupArrays);
    return trackGroupArrays[periodIndex];
  }

  /**
   * Returns the mapped track info for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The {@link MappedTrackInfo} for the period.
   */
  public final MappedTrackInfo getMappedTrackInfo(int periodIndex) {
    Assertions.checkNotNull(mappedTrackInfos);
    return mappedTrackInfos[periodIndex];
  }

  /**
   * Returns all {@link TrackSelection track selections} for a period and renderer. Must not be
   * called until after preparation completes.
   *
   * @param periodIndex The period index.
   * @param rendererIndex The renderer index.
   * @return A list of selected {@link TrackSelection track selections}.
   */
  public final List<TrackSelection> getTrackSelections(int periodIndex, int rendererIndex) {
    Assertions.checkNotNull(immutableTrackSelectionsByPeriodAndRenderer);
    return immutableTrackSelectionsByPeriodAndRenderer[periodIndex][rendererIndex];
  }

  /**
   * Clears the selection of tracks for a period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which track selections are cleared.
   */
  public final void clearTrackSelections(int periodIndex) {
    Assertions.checkNotNull(trackSelectionsByPeriodAndRenderer);
    for (int i = 0; i < rendererCapabilities.length; i++) {
      trackSelectionsByPeriodAndRenderer[periodIndex][i].clear();
    }
  }

  /**
   * Replaces a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index for which the track selection is replaced.
   * @param trackSelectorParameters The {@link DefaultTrackSelector.Parameters} to obtain the new
   *     selection of tracks.
   */
  public final void replaceTrackSelections(
      int periodIndex, DefaultTrackSelector.Parameters trackSelectorParameters) {
    clearTrackSelections(periodIndex);
    addTrackSelection(periodIndex, trackSelectorParameters);
  }

  /**
   * Adds a selection of tracks to be downloaded. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index this track selection is added for.
   * @param trackSelectorParameters The {@link DefaultTrackSelector.Parameters} to obtain the new
   *     selection of tracks.
   */
  public final void addTrackSelection(
      int periodIndex, DefaultTrackSelector.Parameters trackSelectorParameters) {
    Assertions.checkNotNull(trackGroupArrays);
    Assertions.checkNotNull(trackSelectionsByPeriodAndRenderer);
    trackSelector.setParameters(trackSelectorParameters);
    runTrackSelection(periodIndex);
  }

  /**
   * Builds a {@link DownloadAction} for downloading the selected tracks. Must not be called until
   * after preparation completes.
   *
   * @param data Application provided data to store in {@link DownloadAction#data}.
   * @return The built {@link DownloadAction}.
   */
  public final DownloadAction getDownloadAction(@Nullable byte[] data) {
    Assertions.checkNotNull(trackSelectionsByPeriodAndRenderer);
    Assertions.checkNotNull(trackGroupArrays);
    List<StreamKey> streamKeys = new ArrayList<>();
    int periodCount = trackSelectionsByPeriodAndRenderer.length;
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      int rendererCount = trackSelectionsByPeriodAndRenderer[periodIndex].length;
      for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
        List<TrackSelection> trackSelectionList =
            trackSelectionsByPeriodAndRenderer[periodIndex][rendererIndex];
        for (int selectionIndex = 0; selectionIndex < trackSelectionList.size(); selectionIndex++) {
          TrackSelection trackSelection = trackSelectionList.get(selectionIndex);
          int trackGroupIndex =
              trackGroupArrays[periodIndex].indexOf(trackSelection.getTrackGroup());
          int trackCount = trackSelection.length();
          for (int trackListIndex = 0; trackListIndex < trackCount; trackListIndex++) {
            int trackIndex = trackSelection.getIndexInTrackGroup(trackListIndex);
            streamKeys.add(toStreamKey(periodIndex, trackGroupIndex, trackIndex));
          }
        }
      }
    }
    return DownloadAction.createDownloadAction(downloadType, uri, streamKeys, cacheKey, data);
  }

  /**
   * Builds a {@link DownloadAction} for removing the media. May be called in any state.
   *
   * @return The built {@link DownloadAction}.
   */
  public final DownloadAction getRemoveAction() {
    return DownloadAction.createRemoveAction(downloadType, uri, cacheKey);
  }

  /**
   * Loads the manifest. This method is called on a background thread.
   *
   * @param uri The manifest uri.
   * @throws IOException If loading fails.
   */
  protected abstract T loadManifest(Uri uri) throws IOException;

  /**
   * Returns the track group arrays for each period in the manifest.
   *
   * @param manifest The manifest.
   * @return An array of {@link TrackGroupArray}s. One for each period in the manifest.
   */
  protected abstract TrackGroupArray[] getTrackGroupArrays(T manifest);

  /**
   * Converts a track of a track group of a period to the corresponding {@link StreamKey}.
   *
   * @param periodIndex The index of the containing period.
   * @param trackGroupIndex The index of the containing track group within the period.
   * @param trackIndexInTrackGroup The index of the track within the track group.
   * @return The corresponding {@link StreamKey}.
   */
  protected abstract StreamKey toStreamKey(
      int periodIndex, int trackGroupIndex, int trackIndexInTrackGroup);

  @SuppressWarnings("unchecked")
  @EnsuresNonNull("trackSelectionsByPeriodAndRenderer")
  private void initializeTrackSelectionLists(int periodCount, int rendererCount) {
    trackSelectionsByPeriodAndRenderer =
        (List<TrackSelection>[][]) new List<?>[periodCount][rendererCount];
    immutableTrackSelectionsByPeriodAndRenderer =
        (List<TrackSelection>[][]) new List<?>[periodCount][rendererCount];
    for (int i = 0; i < periodCount; i++) {
      for (int j = 0; j < rendererCount; j++) {
        trackSelectionsByPeriodAndRenderer[i][j] = new ArrayList<>();
        immutableTrackSelectionsByPeriodAndRenderer[i][j] =
            Collections.unmodifiableList(trackSelectionsByPeriodAndRenderer[i][j]);
      }
    }
  }

  /**
   * Runs the track selection for a given period index with the current parameters. The selected
   * tracks will be added to {@link #trackSelectionsByPeriodAndRenderer}.
   */
  // Intentional reference comparison of track group instances.
  @SuppressWarnings("ReferenceEquality")
  @RequiresNonNull({"trackGroupArrays", "trackSelectionsByPeriodAndRenderer"})
  private TrackSelectorResult runTrackSelection(int periodIndex) {
    // TODO: Use actual timeline and media period id.
    MediaPeriodId dummyMediaPeriodId = new MediaPeriodId(new Object());
    Timeline dummyTimeline = Timeline.EMPTY;
    currentTrackSelectionPeriodIndex = periodIndex;
    try {
      TrackSelectorResult trackSelectorResult =
          trackSelector.selectTracks(
              rendererCapabilities,
              trackGroupArrays[periodIndex],
              dummyMediaPeriodId,
              dummyTimeline);
      for (int i = 0; i < trackSelectorResult.length; i++) {
        TrackSelection newSelection = trackSelectorResult.selections.get(i);
        if (newSelection == null) {
          continue;
        }
        List<TrackSelection> existingSelectionList =
            trackSelectionsByPeriodAndRenderer[currentTrackSelectionPeriodIndex][i];
        boolean mergedWithExistingSelection = false;
        for (int j = 0; j < existingSelectionList.size(); j++) {
          TrackSelection existingSelection = existingSelectionList.get(j);
          if (existingSelection.getTrackGroup() == newSelection.getTrackGroup()) {
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
    } catch (ExoPlaybackException e) {
      // DefaultTrackSelector does not throw exceptions during track selection.
      throw new UnsupportedOperationException(e);
    }
  }

  private static final class DownloadTrackSelection extends BaseTrackSelection {

    private static final class Factory implements TrackSelection.Factory {

      @Override
      public @NullableType TrackSelection[] createTrackSelections(
          @NullableType Definition[] definitions, BandwidthMeter bandwidthMeter) {
        @NullableType TrackSelection[] selections = new TrackSelection[definitions.length];
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
    public int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Nullable
    @Override
    public Object getSelectionData() {
      return null;
    }
  }

  private static final class DummyBandwidthMeter implements BandwidthMeter {

    @Override
    public long getBitrateEstimate() {
      return 0;
    }

    @Nullable
    @Override
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
