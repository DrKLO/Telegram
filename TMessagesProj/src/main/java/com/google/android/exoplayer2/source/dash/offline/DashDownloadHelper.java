/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.dash.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import java.io.IOException;
import java.util.List;

/** A {@link DownloadHelper} for DASH streams. */
public final class DashDownloadHelper extends DownloadHelper<DashManifest> {

  private final DataSource.Factory manifestDataSourceFactory;

  /**
   * Creates a DASH download helper.
   *
   * <p>The helper uses {@link DownloadHelper#DEFAULT_TRACK_SELECTOR_PARAMETERS} for track selection
   * and does not support drm protected content.
   *
   * @param uri A manifest {@link Uri}.
   * @param manifestDataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param renderersFactory The {@link RenderersFactory} creating the renderers for which tracks
   *     are selected.
   */
  public DashDownloadHelper(
      Uri uri, DataSource.Factory manifestDataSourceFactory, RenderersFactory renderersFactory) {
    this(
        uri,
        manifestDataSourceFactory,
        DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
        renderersFactory,
        /* drmSessionManager= */ null);
  }

  /**
   * Creates a DASH download helper.
   *
   * @param uri A manifest {@link Uri}.
   * @param manifestDataSourceFactory A {@link DataSource.Factory} used to load the manifest.
   * @param trackSelectorParameters {@link DefaultTrackSelector.Parameters} for selecting tracks for
   *     downloading.
   * @param renderersFactory The {@link RenderersFactory} creating the renderers for which tracks
   *     are selected.
   * @param drmSessionManager An optional {@link DrmSessionManager} used by the renderers created by
   *     {@code renderersFactory}.
   */
  public DashDownloadHelper(
      Uri uri,
      DataSource.Factory manifestDataSourceFactory,
      DefaultTrackSelector.Parameters trackSelectorParameters,
      RenderersFactory renderersFactory,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
    super(
        DownloadAction.TYPE_DASH,
        uri,
        /* cacheKey= */ null,
        trackSelectorParameters,
        renderersFactory,
        drmSessionManager);
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected DashManifest loadManifest(Uri uri) throws IOException {
    DataSource dataSource = manifestDataSourceFactory.createDataSource();
    return ParsingLoadable.load(dataSource, new DashManifestParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  @Override
  public TrackGroupArray[] getTrackGroupArrays(DashManifest manifest) {
    int periodCount = manifest.getPeriodCount();
    TrackGroupArray[] trackGroupArrays = new TrackGroupArray[periodCount];
    for (int periodIndex = 0; periodIndex < periodCount; periodIndex++) {
      List<AdaptationSet> adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
      TrackGroup[] trackGroups = new TrackGroup[adaptationSets.size()];
      for (int i = 0; i < trackGroups.length; i++) {
        List<Representation> representations = adaptationSets.get(i).representations;
        Format[] formats = new Format[representations.size()];
        int representationsCount = representations.size();
        for (int j = 0; j < representationsCount; j++) {
          formats[j] = representations.get(j).format;
        }
        trackGroups[i] = new TrackGroup(formats);
      }
      trackGroupArrays[periodIndex] = new TrackGroupArray(trackGroups);
    }
    return trackGroupArrays;
  }

  @Override
  protected StreamKey toStreamKey(
      int periodIndex, int trackGroupIndex, int trackIndexInTrackGroup) {
    return new StreamKey(periodIndex, trackGroupIndex, trackIndexInTrackGroup);
  }
}
