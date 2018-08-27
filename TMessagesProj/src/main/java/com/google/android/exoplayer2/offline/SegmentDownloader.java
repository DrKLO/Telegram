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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import android.support.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for multi segment stream downloaders.
 *
 * @param <M> The type of the manifest object.
 */
public abstract class SegmentDownloader<M extends FilterableManifest<M>> implements Downloader {

  /** Smallest unit of content to be downloaded. */
  protected static class Segment implements Comparable<Segment> {
    /** The start time of the segment in microseconds. */
    public final long startTimeUs;

    /** The {@link DataSpec} of the segment. */
    public final DataSpec dataSpec;

    /** Constructs a Segment. */
    public Segment(long startTimeUs, DataSpec dataSpec) {
      this.startTimeUs = startTimeUs;
      this.dataSpec = dataSpec;
    }

    @Override
    public int compareTo(@NonNull Segment other) {
      long startOffsetDiff = startTimeUs - other.startTimeUs;
      return startOffsetDiff == 0 ? 0 : ((startOffsetDiff < 0) ? -1 : 1);
    }
  }

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;

  private final Uri manifestUri;
  private final PriorityTaskManager priorityTaskManager;
  private final Cache cache;
  private final CacheDataSource dataSource;
  private final CacheDataSource offlineDataSource;
  private final ArrayList<StreamKey> streamKeys;
  private final AtomicBoolean isCanceled;

  private volatile int totalSegments;
  private volatile int downloadedSegments;
  private volatile long downloadedBytes;

  /**
   * @param manifestUri The {@link Uri} of the manifest to be downloaded.
   * @param streamKeys Keys defining which streams in the manifest should be selected for download.
   *     If empty, all streams are downloaded.
   * @param constructorHelper A {@link DownloaderConstructorHelper} instance.
   */
  public SegmentDownloader(
      Uri manifestUri, List<StreamKey> streamKeys, DownloaderConstructorHelper constructorHelper) {
    this.manifestUri = manifestUri;
    this.streamKeys = new ArrayList<>(streamKeys);
    this.cache = constructorHelper.getCache();
    this.dataSource = constructorHelper.buildCacheDataSource(false);
    this.offlineDataSource = constructorHelper.buildCacheDataSource(true);
    this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
    totalSegments = C.LENGTH_UNSET;
    isCanceled = new AtomicBoolean();
  }

  /**
   * Downloads the selected streams in the media. If multiple streams are selected, they are
   * downloaded in sync with one another.
   *
   * @throws IOException Thrown when there is an error downloading.
   * @throws InterruptedException If the thread has been interrupted.
   */
  // downloadedSegments and downloadedBytes are only written from this method, and this method
  // should not be called from more than one thread. Hence non-atomic updates are valid.
  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public final void download() throws IOException, InterruptedException {
    priorityTaskManager.add(C.PRIORITY_DOWNLOAD);

    try {
      List<Segment> segments = initDownload();
      Collections.sort(segments);
      byte[] buffer = new byte[BUFFER_SIZE_BYTES];
      CachingCounters cachingCounters = new CachingCounters();
      for (int i = 0; i < segments.size(); i++) {
        try {
          CacheUtil.cache(
              segments.get(i).dataSpec,
              cache,
              dataSource,
              buffer,
              priorityTaskManager,
              C.PRIORITY_DOWNLOAD,
              cachingCounters,
              isCanceled,
              true);
          downloadedSegments++;
        } finally {
          downloadedBytes += cachingCounters.newlyCachedBytes;
        }
      }
    } finally {
      priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
    }
  }

  @Override
  public void cancel() {
    isCanceled.set(true);
  }

  @Override
  public final long getDownloadedBytes() {
    return downloadedBytes;
  }

  @Override
  public final float getDownloadPercentage() {
    // Take local snapshot of the volatile fields
    int totalSegments = this.totalSegments;
    int downloadedSegments = this.downloadedSegments;
    if (totalSegments == C.LENGTH_UNSET || downloadedSegments == C.LENGTH_UNSET) {
      return C.PERCENTAGE_UNSET;
    }
    return totalSegments == 0 ? 100f : (downloadedSegments * 100f) / totalSegments;
  }

  @Override
  public final void remove() throws InterruptedException {
    try {
      M manifest = getManifest(offlineDataSource, manifestUri);
      List<Segment> segments = getSegments(offlineDataSource, manifest, true);
      for (int i = 0; i < segments.size(); i++) {
        removeUri(segments.get(i).dataSpec.uri);
      }
    } catch (IOException e) {
      // Ignore exceptions when removing.
    } finally {
      // Always attempt to remove the manifest.
      removeUri(manifestUri);
    }
  }

  // Internal methods.

  /**
   * Loads and parses the manifest.
   *
   * @param dataSource The {@link DataSource} through which to load.
   * @param uri The manifest uri.
   * @return The manifest.
   * @throws IOException If an error occurs reading data.
   */
  protected abstract M getManifest(DataSource dataSource, Uri uri) throws IOException;

  /**
   * Returns a list of all downloadable {@link Segment}s for a given manifest.
   *
   * @param dataSource The {@link DataSource} through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param allowIncompleteList Whether to continue in the case that a load error prevents all
   *     segments from being listed. If true then a partial segment list will be returned. If false
   *     an {@link IOException} will be thrown.
   * @throws InterruptedException Thrown if the thread was interrupted.
   * @throws IOException Thrown if {@code allowPartialIndex} is false and a load error occurs, or if
   *     the media is not in a form that allows for its segments to be listed.
   * @return The list of downloadable {@link Segment}s.
   */
  protected abstract List<Segment> getSegments(
      DataSource dataSource, M manifest, boolean allowIncompleteList)
      throws InterruptedException, IOException;

  /** Initializes the download, returning a list of {@link Segment}s that need to be downloaded. */
  // Writes to downloadedSegments and downloadedBytes are safe. See the comment on download().
  @SuppressWarnings("NonAtomicVolatileUpdate")
  private List<Segment> initDownload() throws IOException, InterruptedException {
    M manifest = getManifest(dataSource, manifestUri);
    if (!streamKeys.isEmpty()) {
      manifest = manifest.copy(streamKeys);
    }
    List<Segment> segments = getSegments(dataSource, manifest, /* allowIncompleteList= */ false);
    CachingCounters cachingCounters = new CachingCounters();
    totalSegments = segments.size();
    downloadedSegments = 0;
    downloadedBytes = 0;
    for (int i = segments.size() - 1; i >= 0; i--) {
      Segment segment = segments.get(i);
      CacheUtil.getCached(segment.dataSpec, cache, cachingCounters);
      downloadedBytes += cachingCounters.alreadyCachedBytes;
      if (cachingCounters.alreadyCachedBytes == cachingCounters.contentLength) {
        // The segment is fully downloaded.
        downloadedSegments++;
        segments.remove(i);
      }
    }
    return segments;
  }

  private void removeUri(Uri uri) {
    CacheUtil.remove(cache, CacheUtil.generateKey(uri));
  }

}
