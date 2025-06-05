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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.upstream.ParsingLoadable.Parser;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.CacheWriter;
import com.google.android.exoplayer2.upstream.cache.ContentMetadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException;
import com.google.android.exoplayer2.util.RunnableFutureTask;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

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
    public int compareTo(Segment other) {
      return Util.compareLong(startTimeUs, other.startTimeUs);
    }
  }

  private static final int BUFFER_SIZE_BYTES = 128 * 1024;
  private static final long MAX_MERGED_SEGMENT_START_TIME_DIFF_US = 20 * C.MICROS_PER_SECOND;

  private final DataSpec manifestDataSpec;
  private final Parser<M> manifestParser;
  private final ArrayList<StreamKey> streamKeys;
  private final CacheDataSource.Factory cacheDataSourceFactory;
  private final Cache cache;
  private final CacheKeyFactory cacheKeyFactory;
  @Nullable private final PriorityTaskManager priorityTaskManager;
  private final Executor executor;

  /**
   * The currently active runnables.
   *
   * <p>Note: Only the {@link #download} thread is permitted to modify this list. Modifications, as
   * well as the iteration on the {@link #cancel} thread, must be synchronized on the instance for
   * thread safety. Iterations on the {@link #download} thread do not need to be synchronized, and
   * should not be synchronized because doing so can erroneously block {@link #cancel}.
   */
  private final ArrayList<RunnableFutureTask<?, ?>> activeRunnables;

  private volatile boolean isCanceled;

  /**
   * @param mediaItem The {@link MediaItem} to be downloaded.
   * @param manifestParser A parser for manifests belonging to the media to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded.
   *     Providing an {@link Executor} that uses multiple threads will speed up the download by
   *     allowing parts of it to be executed in parallel.
   */
  public SegmentDownloader(
      MediaItem mediaItem,
      Parser<M> manifestParser,
      CacheDataSource.Factory cacheDataSourceFactory,
      Executor executor) {
    checkNotNull(mediaItem.localConfiguration);
    this.manifestDataSpec = getCompressibleDataSpec(mediaItem.localConfiguration.uri);
    this.manifestParser = manifestParser;
    this.streamKeys = new ArrayList<>(mediaItem.localConfiguration.streamKeys);
    this.cacheDataSourceFactory = cacheDataSourceFactory;
    this.executor = executor;
    cache = Assertions.checkNotNull(cacheDataSourceFactory.getCache());
    cacheKeyFactory = cacheDataSourceFactory.getCacheKeyFactory();
    priorityTaskManager = cacheDataSourceFactory.getUpstreamPriorityTaskManager();
    activeRunnables = new ArrayList<>();
  }

  @Override
  public final void download(@Nullable ProgressListener progressListener)
      throws IOException, InterruptedException {
    ArrayDeque<Segment> pendingSegments = new ArrayDeque<>();
    ArrayDeque<SegmentDownloadRunnable> recycledRunnables = new ArrayDeque<>();
    if (priorityTaskManager != null) {
      priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    }
    try {
      CacheDataSource dataSource = cacheDataSourceFactory.createDataSourceForDownloading();
      // Get the manifest and all of the segments.
      M manifest = getManifest(dataSource, manifestDataSpec, /* removing= */ false);
      if (!streamKeys.isEmpty()) {
        manifest = manifest.copy(streamKeys);
      }
      List<Segment> segments = getSegments(dataSource, manifest, /* removing= */ false);

      // Sort the segments so that we download media in the right order from the start of the
      // content, and merge segments where possible to minimize the number of server round trips.
      Collections.sort(segments);
      mergeSegments(segments, cacheKeyFactory);

      // Scan the segments, removing any that are fully downloaded.
      int totalSegments = segments.size();
      int segmentsDownloaded = 0;
      long contentLength = 0;
      long bytesDownloaded = 0;
      for (int i = segments.size() - 1; i >= 0; i--) {
        DataSpec dataSpec = segments.get(i).dataSpec;
        String cacheKey = cacheKeyFactory.buildCacheKey(dataSpec);
        long segmentLength = dataSpec.length;
        if (segmentLength == C.LENGTH_UNSET) {
          long resourceLength =
              ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey));
          if (resourceLength != C.LENGTH_UNSET) {
            segmentLength = resourceLength - dataSpec.position;
          }
        }
        long segmentBytesDownloaded =
            cache.getCachedBytes(cacheKey, dataSpec.position, segmentLength);
        bytesDownloaded += segmentBytesDownloaded;
        if (segmentLength != C.LENGTH_UNSET) {
          if (segmentLength == segmentBytesDownloaded) {
            // The segment is fully downloaded.
            segmentsDownloaded++;
            segments.remove(i);
          }
          if (contentLength != C.LENGTH_UNSET) {
            contentLength += segmentLength;
          }
        } else {
          contentLength = C.LENGTH_UNSET;
        }
      }

      // Download the segments.
      @Nullable
      ProgressNotifier progressNotifier =
          progressListener != null
              ? new ProgressNotifier(
                  progressListener,
                  contentLength,
                  totalSegments,
                  bytesDownloaded,
                  segmentsDownloaded)
              : null;
      pendingSegments.addAll(segments);
      while (!isCanceled && !pendingSegments.isEmpty()) {
        // Block until there aren't any higher priority tasks.
        if (priorityTaskManager != null) {
          priorityTaskManager.proceed(C.PRIORITY_DOWNLOAD);
        }

        // Create and execute a runnable to download the next segment.
        CacheDataSource segmentDataSource;
        byte[] temporaryBuffer;
        if (!recycledRunnables.isEmpty()) {
          SegmentDownloadRunnable recycledRunnable = recycledRunnables.removeFirst();
          segmentDataSource = recycledRunnable.dataSource;
          temporaryBuffer = recycledRunnable.temporaryBuffer;
        } else {
          segmentDataSource = cacheDataSourceFactory.createDataSourceForDownloading();
          temporaryBuffer = new byte[BUFFER_SIZE_BYTES];
        }
        Segment segment = pendingSegments.removeFirst();
        SegmentDownloadRunnable downloadRunnable =
            new SegmentDownloadRunnable(
                segment, segmentDataSource, progressNotifier, temporaryBuffer);
        addActiveRunnable(downloadRunnable);
        executor.execute(downloadRunnable);

        // Clean up runnables that have finished.
        for (int j = activeRunnables.size() - 1; j >= 0; j--) {
          SegmentDownloadRunnable activeRunnable = (SegmentDownloadRunnable) activeRunnables.get(j);
          // Only block until the runnable has finished if we don't have any more pending segments
          // to start. If we do have pending segments to start then only process the runnable if
          // it's already finished.
          if (pendingSegments.isEmpty() || activeRunnable.isDone()) {
            try {
              activeRunnable.get();
              removeActiveRunnable(j);
              recycledRunnables.addLast(activeRunnable);
            } catch (ExecutionException e) {
              Throwable cause = Assertions.checkNotNull(e.getCause());
              if (cause instanceof PriorityTooLowException) {
                // We need to schedule this segment again in a future loop iteration.
                pendingSegments.addFirst(activeRunnable.segment);
                removeActiveRunnable(j);
                recycledRunnables.addLast(activeRunnable);
              } else if (cause instanceof IOException) {
                throw (IOException) cause;
              } else {
                // The cause must be an uncaught Throwable type.
                Util.sneakyThrow(cause);
              }
            }
          }
        }

        // Don't move on to the next segment until the runnable for this segment has started. This
        // drip feeds runnables to the executor, rather than providing them all up front.
        downloadRunnable.blockUntilStarted();
      }
    } finally {
      // If one of the runnables has thrown an exception, then it's possible there are other active
      // runnables still doing work. We need to wait until they finish before exiting this method.
      // Cancel them to speed this up.
      for (int i = 0; i < activeRunnables.size(); i++) {
        activeRunnables.get(i).cancel(/* interruptIfRunning= */ true);
      }
      // Wait until the runnables have finished. In addition to the failure case, we also need to
      // do this for the case where the main download thread was interrupted as part of cancelation.
      for (int i = activeRunnables.size() - 1; i >= 0; i--) {
        activeRunnables.get(i).blockUntilFinished();
        removeActiveRunnable(i);
      }
      if (priorityTaskManager != null) {
        priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
      }
    }
  }

  @Override
  public void cancel() {
    synchronized (activeRunnables) {
      isCanceled = true;
      for (int i = 0; i < activeRunnables.size(); i++) {
        activeRunnables.get(i).cancel(/* interruptIfRunning= */ true);
      }
    }
  }

  @Override
  public final void remove() {
    CacheDataSource dataSource = cacheDataSourceFactory.createDataSourceForRemovingDownload();
    try {
      M manifest = getManifest(dataSource, manifestDataSpec, /* removing= */ true);
      List<Segment> segments = getSegments(dataSource, manifest, /* removing= */ true);
      for (int i = 0; i < segments.size(); i++) {
        cache.removeResource(cacheKeyFactory.buildCacheKey(segments.get(i).dataSpec));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      // Ignore exceptions when removing.
    } finally {
      // Always attempt to remove the manifest.
      cache.removeResource(cacheKeyFactory.buildCacheKey(manifestDataSpec));
    }
  }

  // Internal methods.

  /**
   * Loads and parses a manifest.
   *
   * @param dataSpec The manifest {@link DataSpec}.
   * @param removing Whether the manifest is being loaded as part of the download being removed.
   * @return The loaded manifest.
   * @throws InterruptedException If the thread on which the method is called is interrupted.
   * @throws IOException If an error occurs during execution.
   */
  protected final M getManifest(DataSource dataSource, DataSpec dataSpec, boolean removing)
      throws InterruptedException, IOException {
    return execute(
        new RunnableFutureTask<M, IOException>() {
          @Override
          protected M doWork() throws IOException {
            return ParsingLoadable.load(dataSource, manifestParser, dataSpec, C.DATA_TYPE_MANIFEST);
          }
        },
        removing);
  }

  /**
   * Executes the provided {@link RunnableFutureTask}.
   *
   * @param runnable The {@link RunnableFutureTask} to execute.
   * @param removing Whether the execution is part of the download being removed.
   * @return The result.
   * @throws InterruptedException If the thread on which the method is called is interrupted.
   * @throws IOException If an error occurs during execution.
   */
  protected final <T> T execute(RunnableFutureTask<T, ?> runnable, boolean removing)
      throws InterruptedException, IOException {
    if (removing) {
      runnable.run();
      try {
        return runnable.get();
      } catch (ExecutionException e) {
        Throwable cause = Assertions.checkNotNull(e.getCause());
        if (cause instanceof IOException) {
          throw (IOException) cause;
        } else {
          // The cause must be an uncaught Throwable type.
          Util.sneakyThrow(e);
        }
      }
    }
    while (true) {
      if (isCanceled) {
        throw new InterruptedException();
      }
      // Block until there aren't any higher priority tasks.
      if (priorityTaskManager != null) {
        priorityTaskManager.proceed(C.PRIORITY_DOWNLOAD);
      }
      addActiveRunnable(runnable);
      executor.execute(runnable);
      try {
        return runnable.get();
      } catch (ExecutionException e) {
        Throwable cause = Assertions.checkNotNull(e.getCause());
        if (cause instanceof PriorityTooLowException) {
          // The next loop iteration will block until the task is able to proceed.
        } else if (cause instanceof IOException) {
          throw (IOException) cause;
        } else {
          // The cause must be an uncaught Throwable type.
          Util.sneakyThrow(e);
        }
      } finally {
        // We don't want to return for as long as the runnable might still be doing work.
        runnable.blockUntilFinished();
        removeActiveRunnable(runnable);
      }
    }
  }

  /**
   * Returns a list of all downloadable {@link Segment}s for a given manifest. Any required data
   * should be loaded using {@link #getManifest} or {@link #execute}.
   *
   * @param dataSource The {@link DataSource} through which to load any required data.
   * @param manifest The manifest containing the segments.
   * @param removing Whether the segments are being obtained as part of a removal. If true then a
   *     partial segment list is returned in the case that a load error prevents all segments from
   *     being listed. If false then an {@link IOException} will be thrown in this case.
   * @return The list of downloadable {@link Segment}s.
   * @throws IOException Thrown if {@code allowPartialIndex} is false and an execution error occurs,
   *     or if the media is not in a form that allows for its segments to be listed.
   */
  protected abstract List<Segment> getSegments(DataSource dataSource, M manifest, boolean removing)
      throws IOException, InterruptedException;

  protected static DataSpec getCompressibleDataSpec(Uri uri) {
    return new DataSpec.Builder().setUri(uri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build();
  }

  private <T> void addActiveRunnable(RunnableFutureTask<T, ?> runnable)
      throws InterruptedException {
    synchronized (activeRunnables) {
      if (isCanceled) {
        throw new InterruptedException();
      }
      activeRunnables.add(runnable);
    }
  }

  private void removeActiveRunnable(RunnableFutureTask<?, ?> runnable) {
    synchronized (activeRunnables) {
      activeRunnables.remove(runnable);
    }
  }

  private void removeActiveRunnable(int index) {
    synchronized (activeRunnables) {
      activeRunnables.remove(index);
    }
  }

  private static void mergeSegments(List<Segment> segments, CacheKeyFactory keyFactory) {
    HashMap<String, Integer> lastIndexByCacheKey = new HashMap<>();
    int nextOutIndex = 0;
    for (int i = 0; i < segments.size(); i++) {
      Segment segment = segments.get(i);
      String cacheKey = keyFactory.buildCacheKey(segment.dataSpec);
      @Nullable Integer lastIndex = lastIndexByCacheKey.get(cacheKey);
      @Nullable Segment lastSegment = lastIndex == null ? null : segments.get(lastIndex);
      if (lastSegment == null
          || segment.startTimeUs > lastSegment.startTimeUs + MAX_MERGED_SEGMENT_START_TIME_DIFF_US
          || !canMergeSegments(lastSegment.dataSpec, segment.dataSpec)) {
        lastIndexByCacheKey.put(cacheKey, nextOutIndex);
        segments.set(nextOutIndex, segment);
        nextOutIndex++;
      } else {
        long mergedLength =
            segment.dataSpec.length == C.LENGTH_UNSET
                ? C.LENGTH_UNSET
                : lastSegment.dataSpec.length + segment.dataSpec.length;
        DataSpec mergedDataSpec = lastSegment.dataSpec.subrange(/* offset= */ 0, mergedLength);
        segments.set(
            Assertions.checkNotNull(lastIndex),
            new Segment(lastSegment.startTimeUs, mergedDataSpec));
      }
    }
    Util.removeRange(segments, /* fromIndex= */ nextOutIndex, /* toIndex= */ segments.size());
  }

  private static boolean canMergeSegments(DataSpec dataSpec1, DataSpec dataSpec2) {
    return dataSpec1.uri.equals(dataSpec2.uri)
        && dataSpec1.length != C.LENGTH_UNSET
        && (dataSpec1.position + dataSpec1.length == dataSpec2.position)
        && Util.areEqual(dataSpec1.key, dataSpec2.key)
        && dataSpec1.flags == dataSpec2.flags
        && dataSpec1.httpMethod == dataSpec2.httpMethod
        && dataSpec1.httpRequestHeaders.equals(dataSpec2.httpRequestHeaders);
  }

  private static final class SegmentDownloadRunnable extends RunnableFutureTask<Void, IOException> {

    public final Segment segment;
    public final CacheDataSource dataSource;
    @Nullable private final ProgressNotifier progressNotifier;
    public final byte[] temporaryBuffer;
    private final CacheWriter cacheWriter;

    public SegmentDownloadRunnable(
        Segment segment,
        CacheDataSource dataSource,
        @Nullable ProgressNotifier progressNotifier,
        byte[] temporaryBuffer) {
      this.segment = segment;
      this.dataSource = dataSource;
      this.progressNotifier = progressNotifier;
      this.temporaryBuffer = temporaryBuffer;
      this.cacheWriter =
          new CacheWriter(dataSource, segment.dataSpec, temporaryBuffer, progressNotifier);
    }

    @Override
    protected Void doWork() throws IOException {
      cacheWriter.cache();
      if (progressNotifier != null) {
        progressNotifier.onSegmentDownloaded();
      }
      return null;
    }

    @Override
    protected void cancelWork() {
      cacheWriter.cancel();
    }
  }

  private static final class ProgressNotifier implements CacheWriter.ProgressListener {

    private final ProgressListener progressListener;

    private final long contentLength;
    private final int totalSegments;

    private long bytesDownloaded;
    private int segmentsDownloaded;

    public ProgressNotifier(
        ProgressListener progressListener,
        long contentLength,
        int totalSegments,
        long bytesDownloaded,
        int segmentsDownloaded) {
      this.progressListener = progressListener;
      this.contentLength = contentLength;
      this.totalSegments = totalSegments;
      this.bytesDownloaded = bytesDownloaded;
      this.segmentsDownloaded = segmentsDownloaded;
    }

    @Override
    public void onProgress(long requestLength, long bytesCached, long newBytesCached) {
      bytesDownloaded += newBytesCached;
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded());
    }

    public void onSegmentDownloaded() {
      segmentsDownloaded++;
      progressListener.onProgress(contentLength, bytesDownloaded, getPercentDownloaded());
    }

    private float getPercentDownloaded() {
      if (contentLength != C.LENGTH_UNSET && contentLength != 0) {
        return (bytesDownloaded * 100f) / contentLength;
      } else if (totalSegments != 0) {
        return (segmentsDownloaded * 100f) / totalSegments;
      } else {
        return C.PERCENTAGE_UNSET;
      }
    }
  }
}
