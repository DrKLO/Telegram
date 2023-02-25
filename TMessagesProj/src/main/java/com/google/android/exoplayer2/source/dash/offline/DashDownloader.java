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
package com.google.android.exoplayer2.source.dash.offline;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.offline.DownloadException;
import com.google.android.exoplayer2.offline.SegmentDownloader;
import com.google.android.exoplayer2.source.dash.BaseUrlExclusionList;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.DashWrappingSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.util.RunnableFutureTask;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A downloader for DASH streams.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SimpleCache cache = new SimpleCache(downloadFolder, new NoOpCacheEvictor(), databaseProvider);
 * CacheDataSource.Factory cacheDataSourceFactory =
 *     new CacheDataSource.Factory()
 *         .setCache(cache)
 *         .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory());
 * // Create a downloader for the first representation of the first adaptation set of the first
 * // period.
 * DashDownloader dashDownloader =
 *     new DashDownloader(
 *         new MediaItem.Builder()
 *             .setUri(manifestUrl)
 *             .setStreamKeys(Collections.singletonList(new StreamKey(0, 0, 0)))
 *             .build(),
 *         cacheDataSourceFactory);
 * // Perform the download.
 * dashDownloader.download(progressListener);
 * // Use the downloaded data for playback.
 * DashMediaSource mediaSource =
 *     new DashMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem);
 * }</pre>
 */
public final class DashDownloader extends SegmentDownloader<DashManifest> {

  private final BaseUrlExclusionList baseUrlExclusionList;

  /**
   * Creates a new instance.
   *
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   */
  public DashDownloader(MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory) {
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
  public DashDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory, Executor executor) {
    this(mediaItem, new DashManifestParser(), cacheDataSourceFactory, executor);
  }

  /**
   * Creates a new instance.
   *
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param manifestParser A parser for DASH manifests.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public DashDownloader(
      MediaItem mediaItem,
      Parser<DashManifest> manifestParser,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    super(mediaItem, manifestParser, cacheDataSourceFactory, executor);
    baseUrlExclusionList = new BaseUrlExclusionList();
  }

  @Override
  protected List<Segment> getSegments(
      DataSource dataSource, DashManifest manifest, boolean removing)
      throws IOException, InterruptedException {
    ArrayList<Segment> segments = new ArrayList<>();
    for (int i = 0; i < manifest.getPeriodCount(); i++) {
      Period period = manifest.getPeriod(i);
      long periodStartUs = Util.msToUs(period.startMs);
      long periodDurationUs = manifest.getPeriodDurationUs(i);
      List<AdaptationSet> adaptationSets = period.adaptationSets;
      for (int j = 0; j < adaptationSets.size(); j++) {
        addSegmentsForAdaptationSet(
            dataSource, adaptationSets.get(j), periodStartUs, periodDurationUs, removing, segments);
      }
    }
    return segments;
  }

  private void addSegmentsForAdaptationSet(
      DataSource dataSource,
      AdaptationSet adaptationSet,
      long periodStartUs,
      long periodDurationUs,
      boolean removing,
      ArrayList<Segment> out)
      throws IOException, InterruptedException {
    for (int i = 0; i < adaptationSet.representations.size(); i++) {
      Representation representation = adaptationSet.representations.get(i);
      DashSegmentIndex index;
      try {
        index = getSegmentIndex(dataSource, adaptationSet.type, representation, removing);
        if (index == null) {
          // Loading succeeded but there was no index.
          throw new DownloadException("Missing segment index");
        }
      } catch (IOException e) {
        if (!removing) {
          throw e;
        }
        // Generating an incomplete segment list is allowed. Advance to the next representation.
        continue;
      }

      long segmentCount = index.getSegmentCount(periodDurationUs);
      if (segmentCount == DashSegmentIndex.INDEX_UNBOUNDED) {
        throw new DownloadException("Unbounded segment index");
      }

      String baseUrl = castNonNull(baseUrlExclusionList.selectBaseUrl(representation.baseUrls)).url;
      @Nullable RangedUri initializationUri = representation.getInitializationUri();
      if (initializationUri != null) {
        out.add(createSegment(representation, baseUrl, periodStartUs, initializationUri));
      }
      @Nullable RangedUri indexUri = representation.getIndexUri();
      if (indexUri != null) {
        out.add(createSegment(representation, baseUrl, periodStartUs, indexUri));
      }
      long firstSegmentNum = index.getFirstSegmentNum();
      long lastSegmentNum = firstSegmentNum + segmentCount - 1;
      for (long j = firstSegmentNum; j <= lastSegmentNum; j++) {
        out.add(
            createSegment(
                representation,
                baseUrl,
                periodStartUs + index.getTimeUs(j),
                index.getSegmentUrl(j)));
      }
    }
  }

  private Segment createSegment(
      Representation representation, String baseUrl, long startTimeUs, RangedUri rangedUri) {
    DataSpec dataSpec = DashUtil.buildDataSpec(representation, baseUrl, rangedUri, /* flags= */ 0);
    return new Segment(startTimeUs, dataSpec);
  }

  @Nullable
  private DashSegmentIndex getSegmentIndex(
      DataSource dataSource, int trackType, Representation representation, boolean removing)
      throws IOException, InterruptedException {
    DashSegmentIndex index = representation.getIndex();
    if (index != null) {
      return index;
    }
    RunnableFutureTask<@NullableType ChunkIndex, IOException> runnable =
        new RunnableFutureTask<@NullableType ChunkIndex, IOException>() {
          @Override
          protected @NullableType ChunkIndex doWork() throws IOException {
            return DashUtil.loadChunkIndex(dataSource, trackType, representation);
          }
        };
    @Nullable ChunkIndex seekMap = execute(runnable, removing);
    return seekMap == null
        ? null
        : new DashWrappingSegmentIndex(seekMap, representation.presentationTimeOffsetUs);
  }
}
