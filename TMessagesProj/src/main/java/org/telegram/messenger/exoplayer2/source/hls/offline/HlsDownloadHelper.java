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
package org.telegram.messenger.exoplayer2.source.hls.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.offline.DownloadHelper;
import org.telegram.messenger.exoplayer2.offline.TrackKey;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import org.telegram.messenger.exoplayer2.source.hls.playlist.RenditionKey;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A {@link DownloadHelper} for HLS streams. */
public final class HlsDownloadHelper extends DownloadHelper {

  private final Uri uri;
  private final DataSource.Factory manifestDataSourceFactory;

  private HlsPlaylist playlist;
  private int[] renditionTypes;

  public HlsDownloadHelper(Uri uri, DataSource.Factory manifestDataSourceFactory) {
    this.uri = uri;
    this.manifestDataSourceFactory = manifestDataSourceFactory;
  }

  @Override
  protected void prepareInternal() throws IOException {
    DataSource dataSource = manifestDataSourceFactory.createDataSource();
    playlist = ParsingLoadable.load(dataSource, new HlsPlaylistParser(), uri);
  }

  /** Returns the HLS playlist. Must not be called until after preparation completes. */
  public HlsPlaylist getPlaylist() {
    Assertions.checkNotNull(playlist);
    return playlist;
  }

  @Override
  public int getPeriodCount() {
    Assertions.checkNotNull(playlist);
    return 1;
  }

  @Override
  public TrackGroupArray getTrackGroups(int periodIndex) {
    Assertions.checkNotNull(playlist);
    if (playlist instanceof HlsMediaPlaylist) {
      return TrackGroupArray.EMPTY;
    }
    // TODO: Generate track groups as in playback. Reverse the mapping in getDownloadAction.
    HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;
    TrackGroup[] trackGroups = new TrackGroup[3];
    renditionTypes = new int[3];
    int trackGroupIndex = 0;
    if (!masterPlaylist.variants.isEmpty()) {
      renditionTypes[trackGroupIndex] = RenditionKey.TYPE_VARIANT;
      trackGroups[trackGroupIndex++] = new TrackGroup(toFormats(masterPlaylist.variants));
    }
    if (!masterPlaylist.audios.isEmpty()) {
      renditionTypes[trackGroupIndex] = RenditionKey.TYPE_AUDIO;
      trackGroups[trackGroupIndex++] = new TrackGroup(toFormats(masterPlaylist.audios));
    }
    if (!masterPlaylist.subtitles.isEmpty()) {
      renditionTypes[trackGroupIndex] = RenditionKey.TYPE_SUBTITLE;
      trackGroups[trackGroupIndex++] = new TrackGroup(toFormats(masterPlaylist.subtitles));
    }
    return new TrackGroupArray(Arrays.copyOf(trackGroups, trackGroupIndex));
  }

  @Override
  public HlsDownloadAction getDownloadAction(@Nullable byte[] data, List<TrackKey> trackKeys) {
    Assertions.checkNotNull(renditionTypes);
    return new HlsDownloadAction(
        uri, /* isRemoveAction= */ false, data, toRenditionKeys(trackKeys, renditionTypes));
  }

  @Override
  public HlsDownloadAction getRemoveAction(@Nullable byte[] data) {
    return new HlsDownloadAction(
        uri, /* isRemoveAction= */ true, data, Collections.<RenditionKey>emptyList());
  }

  private static Format[] toFormats(List<HlsMasterPlaylist.HlsUrl> hlsUrls) {
    Format[] formats = new Format[hlsUrls.size()];
    for (int i = 0; i < hlsUrls.size(); i++) {
      formats[i] = hlsUrls.get(i).format;
    }
    return formats;
  }

  private static List<RenditionKey> toRenditionKeys(List<TrackKey> trackKeys, int[] groups) {
    List<RenditionKey> representationKeys = new ArrayList<>(trackKeys.size());
    for (int i = 0; i < trackKeys.size(); i++) {
      TrackKey trackKey = trackKeys.get(i);
      representationKeys.add(new RenditionKey(groups[trackKey.groupIndex], trackKey.trackIndex));
    }
    return representationKeys;
  }
}
