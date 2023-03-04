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
package com.google.android.exoplayer2.source.hls.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMultivariantPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.util.UriUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A downloader for HLS streams.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor(), databaseProvider);
 * CacheDataSource.Factory cacheDataSourceFactory =
 *     new CacheDataSource.Factory()
 *         .setCache(cache)
 *         .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory());
 * // Create a downloader for the first variant in a multivariant playlist.
 * HlsDownloader hlsDownloader =
 *     new HlsDownloader(
 *         new MediaItem.Builder()
 *             .setUri(playlistUri)
 *             .setStreamKeys(
 *                 Collections.singletonList(
 *                     new StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, 0)))
 *             .build(),
 *         Collections.singletonList();
 * // Perform the download.
 * hlsDownloader.download(progressListener);
 * // Use the downloaded data for playback.
 * HlsMediaSource mediaSource =
 *     new HlsMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem);
 * }</pre>
 */
public final class HlsDownloader extends SegmentDownloader<HlsPlaylist> {

  /**
   * Creates a new instance.
   *
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   */
  public HlsDownloader(MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory) {
    this(mediaItem, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * Creates a new instance.
   *
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public HlsDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory, Executor executor) {
    this(mediaItem, new HlsPlaylistParser(), cacheDataSourceFactory, executor);
  }

  /**
   * Creates a new instance.
   *
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param manifestParser A parser for HLS playlists.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public HlsDownloader(
      MediaItem mediaItem,
      Parser<HlsPlaylist> manifestParser,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    super(mediaItem, manifestParser, cacheDataSourceFactory, executor);
  }

  @Override
  protected List<Segment> getSegments(DataSource dataSource, HlsPlaylist manifest, boolean removing)
      throws IOException, InterruptedException {
    ArrayList<DataSpec> mediaPlaylistDataSpecs = new ArrayList<>();
    if (manifest instanceof HlsMultivariantPlaylist) {
      HlsMultivariantPlaylist multivariantPlaylist = (HlsMultivariantPlaylist) manifest;
      addMediaPlaylistDataSpecs(multivariantPlaylist.mediaPlaylistUrls, mediaPlaylistDataSpecs);
    } else {
      mediaPlaylistDataSpecs.add(
          SegmentDownloader.getCompressibleDataSpec(Uri.parse(manifest.baseUri)));
    }

    ArrayList<Segment> segments = new ArrayList<>();
    HashSet<Uri> seenEncryptionKeyUris = new HashSet<>();
    for (DataSpec mediaPlaylistDataSpec : mediaPlaylistDataSpecs) {
      segments.add(new Segment(/* startTimeUs= */ 0, mediaPlaylistDataSpec));
      HlsMediaPlaylist mediaPlaylist;
      try {
        mediaPlaylist = (HlsMediaPlaylist) getManifest(dataSource, mediaPlaylistDataSpec, removing);
      } catch (IOException e) {
        if (!removing) {
          throw e;
        }
        // Generating an incomplete segment list is allowed. Advance to the next media playlist.
        continue;
      }
      @Nullable HlsMediaPlaylist.Segment lastInitSegment = null;
      List<HlsMediaPlaylist.Segment> hlsSegments = mediaPlaylist.segments;
      for (int i = 0; i < hlsSegments.size(); i++) {
        HlsMediaPlaylist.Segment segment = hlsSegments.get(i);
        HlsMediaPlaylist.Segment initSegment = segment.initializationSegment;
        if (initSegment != null && initSegment != lastInitSegment) {
          lastInitSegment = initSegment;
          addSegment(mediaPlaylist, initSegment, seenEncryptionKeyUris, segments);
        }
        addSegment(mediaPlaylist, segment, seenEncryptionKeyUris, segments);
      }
    }
    return segments;
  }

  private void addMediaPlaylistDataSpecs(List<Uri> mediaPlaylistUrls, List<DataSpec> out) {
    for (int i = 0; i < mediaPlaylistUrls.size(); i++) {
      out.add(SegmentDownloader.getCompressibleDataSpec(mediaPlaylistUrls.get(i)));
    }
  }

  private void addSegment(
      HlsMediaPlaylist mediaPlaylist,
      HlsMediaPlaylist.Segment segment,
      HashSet<Uri> seenEncryptionKeyUris,
      ArrayList<Segment> out) {
    String baseUri = mediaPlaylist.baseUri;
    long startTimeUs = mediaPlaylist.startTimeUs + segment.relativeStartTimeUs;
    if (segment.fullSegmentEncryptionKeyUri != null) {
      Uri keyUri = UriUtil.resolveToUri(baseUri, segment.fullSegmentEncryptionKeyUri);
      if (seenEncryptionKeyUris.add(keyUri)) {
        out.add(new Segment(startTimeUs, SegmentDownloader.getCompressibleDataSpec(keyUri)));
      }
    }
    Uri segmentUri = UriUtil.resolveToUri(baseUri, segment.url);
    DataSpec dataSpec = new DataSpec(segmentUri, segment.byteRangeOffset, segment.byteRangeLength);
    out.add(new Segment(startTimeUs, dataSpec));
  }
}
