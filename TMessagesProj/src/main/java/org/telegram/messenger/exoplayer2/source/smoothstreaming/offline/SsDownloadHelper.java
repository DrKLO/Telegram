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
package org.telegram.messenger.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.offline.DownloadHelper;
import org.telegram.messenger.exoplayer2.offline.TrackKey;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.StreamKey;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@link DownloadHelper} for SmoothStreaming streams. */
public final class SsDownloadHelper extends DownloadHelper {

  private final Uri uri;
  private final DataSource.Factory manifestDataSourceFactory;

  private SsManifest manifest;

  public SsDownloadHelper(Uri uri, DataSource.Factory manifestDataSourceFactory) {
    this.uri = uri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected void prepareInternal() throws IOException {
    DataSource dataSource = manifestDataSourceFactory.createDataSource();
    manifest = ParsingLoadable.load(dataSource, new SsManifestParser(), uri);
  }

  /** Returns the SmoothStreaming manifest. Must not be called until after preparation completes. */
  public SsManifest getManifest() {
    Assertions.checkNotNull(manifest);
    return manifest;
  }

  @Override
  public int getPeriodCount() {
    Assertions.checkNotNull(manifest);
    return 1;
  }

  @Override
  public TrackGroupArray getTrackGroups(int periodIndex) {
    Assertions.checkNotNull(manifest);
    SsManifest.StreamElement[] streamElements = manifest.streamElements;
    TrackGroup[] trackGroups = new TrackGroup[streamElements.length];
    for (int i = 0; i < streamElements.length; i++) {
      trackGroups[i] = new TrackGroup(streamElements[i].formats);
    }
    return new TrackGroupArray(trackGroups);
  }

  @Override
  public SsDownloadAction getDownloadAction(@Nullable byte[] data, List<TrackKey> trackKeys) {
    return new SsDownloadAction(uri, /* isRemoveAction= */ false, data, toStreamKeys(trackKeys));
  }

  @Override
  public SsDownloadAction getRemoveAction(@Nullable byte[] data) {
    return new SsDownloadAction(
        uri, /* isRemoveAction= */ true, data, Collections.<StreamKey>emptyList());
  }

  private static List<StreamKey> toStreamKeys(List<TrackKey> trackKeys) {
    List<StreamKey> representationKeys = new ArrayList<>(trackKeys.size());
    for (int i = 0; i < trackKeys.size(); i++) {
      TrackKey trackKey = trackKeys.get(i);
      representationKeys.add(new StreamKey(trackKey.groupIndex, trackKey.trackIndex));
    }
    return representationKeys;
  }
}
