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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheWriter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException;
import com.google.android.exoplayer2.util.RunnableFutureTask;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A downloader for progressive media streams. */
public final class ProgressiveDownloader implements Downloader {

  private final Executor executor;
  private final DataSpec dataSpec;
  private final CacheDataSource dataSource;
  private final CacheWriter cacheWriter;
  @Nullable private final PriorityTaskManager priorityTaskManager;

  @Nullable private ProgressListener progressListener;
  private volatile @MonotonicNonNull RunnableFutureTask<Void, IOException> downloadRunnable;
  private volatile boolean isCanceled;

  /**
   * Creates a new instance.
   *
   * @param mediaItem The media item with a uri to the stream to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   */
  public ProgressiveDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory) {
    this(mediaItem, cacheDataSourceFactory, Runnable::run);
  }

  /**
   * Creates a new instance.
   *
   * @param mediaItem The media item with a uri to the stream to be downloaded.
   * @param cacheDataSourceFactory A {@link CacheDataSource.Factory} for the cache into which the
   *     download will be written.
   * @param executor An {@link Executor} used to make requests for the media being downloaded. In
   *     the future, providing an {@link Executor} that uses multiple threads may speed up the
   *     download by allowing parts of it to be executed in parallel.
   */
  public ProgressiveDownloader(
      MediaItem mediaItem, CacheDataSource.Factory cacheDataSourceFactory, Executor executor) {
    this.executor = Assertions.checkNotNull(executor);
    Assertions.checkNotNull(mediaItem.localConfiguration);
    dataSpec =
        new DataSpec.Builder()
            .setUri(mediaItem.localConfiguration.uri)
            .setKey(mediaItem.localConfiguration.customCacheKey)
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .build();
    dataSource = cacheDataSourceFactory.createDataSourceForDownloading();
    @SuppressWarnings("nullness:methodref.receiver.bound")
    CacheWriter.ProgressListener progressListener = this::onProgress;
    cacheWriter =
        new CacheWriter(dataSource, dataSpec, /* temporaryBuffer= */ null, progressListener);
    priorityTaskManager = cacheDataSourceFactory.getUpstreamPriorityTaskManager();
  }

  @Override
  public void download(@Nullable ProgressListener progressListener)
      throws IOException, InterruptedException {
    this.progressListener = progressListener;
    if (priorityTaskManager != null) {
      priorityTaskManager.add(C.PRIORITY_DOWNLOAD);
    }
    try {
      boolean finished = false;
      while (!finished && !isCanceled) {
        // Recreate downloadRunnable on each loop iteration to avoid rethrowing a previous error.
        downloadRunnable =
            new RunnableFutureTask<Void, IOException>() {
              @Override
              protected Void doWork() throws IOException {
                cacheWriter.cache();
                return null;
              }

              @Override
              protected void cancelWork() {
                cacheWriter.cancel();
              }
            };
        if (priorityTaskManager != null) {
          priorityTaskManager.proceed(C.PRIORITY_DOWNLOAD);
        }
        executor.execute(downloadRunnable);
        try {
          downloadRunnable.get();
          finished = true;
        } catch (ExecutionException e) {
          Throwable cause = Assertions.checkNotNull(e.getCause());
          if (cause instanceof PriorityTooLowException) {
            // The next loop iteration will block until the task is able to proceed.
          } else if (cause instanceof IOException) {
            throw (IOException) cause;
          } else {
            // The cause must be an uncaught Throwable type.
            Util.sneakyThrow(cause);
          }
        }
      }
    } finally {
      // If the main download thread was interrupted as part of cancelation, then it's possible that
      // the runnable is still doing work. We need to wait until it's finished before returning.
      checkNotNull(downloadRunnable).blockUntilFinished();
      if (priorityTaskManager != null) {
        priorityTaskManager.remove(C.PRIORITY_DOWNLOAD);
      }
    }
  }

  @Override
  public void cancel() {
    isCanceled = true;
    RunnableFutureTask<Void, IOException> downloadRunnable = this.downloadRunnable;
    if (downloadRunnable != null) {
      downloadRunnable.cancel(/* interruptIfRunning= */ true);
    }
  }

  @Override
  public void remove() {
    dataSource.getCache().removeResource(dataSource.getCacheKeyFactory().buildCacheKey(dataSpec));
  }

  private void onProgress(long contentLength, long bytesCached, long newBytesCached) {
    if (progressListener == null) {
      return;
    }
    float percentDownloaded =
        contentLength == C.LENGTH_UNSET || contentLength == 0
            ? C.PERCENTAGE_UNSET
            : ((bytesCached * 100f) / contentLength);
    progressListener.onProgress(contentLength, bytesCached, percentDownloaded);
  }
}
