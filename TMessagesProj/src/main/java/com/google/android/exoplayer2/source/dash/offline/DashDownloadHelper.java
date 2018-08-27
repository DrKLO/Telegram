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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.offline.TrackKey;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link DownloadHelper} for DASH streams. */
public final class DashDownloadHelper extends DownloadHelper {

  private final Uri uri;
  private final DataSource.Factory manifestDataSourceFactory;

  private @MonotonicNonNull DashManifest manifest;

  public DashDownloadHelper(Uri uri, DataSource.Factory manifestDataSourceFactory) {
    this.uri = uri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected void prepareInternal() throws IOException {
    DataSource dataSource = manifestDataSourceFactory.createDataSource();
    manifest =
        ParsingLoadable.load(dataSource, new DashManifestParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  /** Returns the DASH manifest. Must not be called until after preparation completes. */
  public DashManifest getManifest() {
    Assertions.checkNotNull(manifest);
    return manifest;
  }

  @Override
  public int getPeriodCount() {
    Assertions.checkNotNull(manifest);
    return manifest.getPeriodCount();
  }

  @Override
  public TrackGroupArray getTrackGroups(int periodIndex) {
    Assertions.checkNotNull(manifest);
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
    return new TrackGroupArray(trackGroups);
  }

  @Override
  public DashDownloadAction getDownloadAction(@Nullable byte[] data, List<TrackKey> trackKeys) {
    return DashDownloadAction.createDownloadAction(uri, data, toStreamKeys(trackKeys));
  }

  @Override
  public DashDownloadAction getRemoveAction(@Nullable byte[] data) {
    return DashDownloadAction.createRemoveAction(uri, data);
  }

  private static List<StreamKey> toStreamKeys(List<TrackKey> trackKeys) {
    List<StreamKey> streamKeys = new ArrayList<>(trackKeys.size());
    for (int i = 0; i < trackKeys.size(); i++) {
      TrackKey trackKey = trackKeys.get(i);
      streamKeys.add(new StreamKey(trackKey.periodIndex, trackKey.groupIndex, trackKey.trackIndex));
    }
    return streamKeys;
  }
}
