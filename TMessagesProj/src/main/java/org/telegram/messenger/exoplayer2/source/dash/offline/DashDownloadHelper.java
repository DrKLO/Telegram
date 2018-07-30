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
package org.telegram.messenger.exoplayer2.source.dash.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.offline.DownloadHelper;
import org.telegram.messenger.exoplayer2.offline.TrackKey;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.dash.manifest.AdaptationSet;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifest;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifestParser;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Representation;
import org.telegram.messenger.exoplayer2.source.dash.manifest.RepresentationKey;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@link DownloadHelper} for DASH streams. */
public final class DashDownloadHelper extends DownloadHelper {

  private final Uri uri;
  private final DataSource.Factory manifestDataSourceFactory;

  private DashManifest manifest;

  public DashDownloadHelper(Uri uri, DataSource.Factory manifestDataSourceFactory) {
    this.uri = uri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected void prepareInternal() throws IOException {
    manifest =
        ParsingLoadable.load(
            manifestDataSourceFactory.createDataSource(), new DashManifestParser(), uri);
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
    return new DashDownloadAction(
        uri, /* isRemoveAction= */ false, data, toRepresentationKeys(trackKeys));
  }

  @Override
  public DashDownloadAction getRemoveAction(@Nullable byte[] data) {
    return new DashDownloadAction(
        uri, /* isRemoveAction= */ true, data, Collections.<RepresentationKey>emptyList());
  }

  private static List<RepresentationKey> toRepresentationKeys(List<TrackKey> trackKeys) {
    List<RepresentationKey> representationKeys = new ArrayList<>(trackKeys.size());
    for (int i = 0; i < trackKeys.size(); i++) {
      TrackKey trackKey = trackKeys.get(i);
      representationKeys.add(
          new RepresentationKey(trackKey.periodIndex, trackKey.groupIndex, trackKey.trackIndex));
    }
    return representationKeys;
  }
}
