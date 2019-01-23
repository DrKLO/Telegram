/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caching related utility methods.
 */
@SuppressWarnings({"NonAtomicVolatileUpdate", "NonAtomicOperationOnVolatileField"})
public final class CacheUtil {

  /** Counters used during caching. */
  public static class CachingCounters {
    /** The number of bytes already in the cache. */
    public volatile long alreadyCachedBytes;
    /** The number of newly cached bytes. */
    public volatile long newlyCachedBytes;
    /** The length of the content being cached in bytes, or {@link C#LENGTH_UNSET} if unknown. */
    public volatile long contentLength = C.LENGTH_UNSET;

    /**
     * Returns the sum of {@link #alreadyCachedBytes} and {@link #newlyCachedBytes}.
     */
    public long totalCachedBytes() {
      return alreadyCachedBytes + newlyCachedBytes;
    }
  }

  /** Default buffer size to be used while caching. */
  public static final int DEFAULT_BUFFER_SIZE_BYTES = 128 * 1024;

  /** Default {@link CacheKeyFactory}. */
  public static final CacheKeyFactory DEFAULT_CACHE_KEY_FACTORY =
      (dataSpec) -> dataSpec.key != null ? dataSpec.key : generateKey(dataSpec.uri);

  /**
   * Generates a cache key out of the given {@link Uri}.
   *
   * @param uri Uri of a content which the requested key is for.
   */
  public static String generateKey(Uri uri) {
    return uri.toString();
  }

  /**
   * Sets a {@link CachingCounters} to contain the number of bytes already downloaded and the length
   * for the content defined by a {@code dataSpec}. {@link CachingCounters#newlyCachedBytes} is
   * reset to 0.
   *
   * @param dataSpec Defines the data to be checked.
   * @param cache A {@link Cache} which has the data.
   * @param cacheKeyFactory An optional factory for cache keys.
   * @param counters The {@link CachingCounters} to update.
   */
  public static void getCached(
      DataSpec dataSpec,
      Cache cache,
      @Nullable CacheKeyFactory cacheKeyFactory,
      CachingCounters counters) {
    String key = buildCacheKey(dataSpec, cacheKeyFactory);
    long start = dataSpec.absoluteStreamPosition;
    long left =
        dataSpec.length != C.LENGTH_UNSET
            ? dataSpec.length
            : ContentMetadata.getContentLength(cache.getContentMetadata(key));
    counters.contentLength = left;
    counters.alreadyCachedBytes = 0;
    counters.newlyCachedBytes = 0;
    while (left != 0) {
      long blockLength =
          cache.getCachedLength(key, start, left != C.LENGTH_UNSET ? left : Long.MAX_VALUE);
      if (blockLength > 0) {
        counters.alreadyCachedBytes += blockLength;
      } else {
        blockLength = -blockLength;
        if (blockLength == Long.MAX_VALUE) {
          return;
        }
      }
      start += blockLength;
      left -= left == C.LENGTH_UNSET ? 0 : blockLength;
    }
  }

  /**
   * Caches the data defined by {@code dataSpec}, skipping already cached data. Caching stops early
   * if the end of the input is reached.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param cacheKeyFactory An optional factory for cache keys.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   * @param counters If not null, updated during caching.
   * @param isCanceled An optional flag that will interrupt caching if set to true.
   * @throws IOException If an error occurs reading from the source.
   * @throws InterruptedException If the thread was interrupted directly or via {@code isCanceled}.
   */
  public static void cache(
      DataSpec dataSpec,
      Cache cache,
      @Nullable CacheKeyFactory cacheKeyFactory,
      DataSource upstream,
      @Nullable CachingCounters counters,
      @Nullable AtomicBoolean isCanceled)
      throws IOException, InterruptedException {
    cache(
        dataSpec,
        cache,
        cacheKeyFactory,
        new CacheDataSource(cache, upstream),
        new byte[DEFAULT_BUFFER_SIZE_BYTES],
        /* priorityTaskManager= */ null,
        /* priority= */ 0,
        counters,
        isCanceled,
        /* enableEOFException= */ false);
  }

  /**
   * Caches the data defined by {@code dataSpec} while skipping already cached data. Caching stops
   * early if end of input is reached and {@code enableEOFException} is false.
   *
   * <p>If a {@link PriorityTaskManager} is given, it's used to pause and resume caching depending
   * on {@code priority} and the priority of other tasks registered to the PriorityTaskManager.
   * Please note that it's the responsibility of the calling code to call {@link
   * PriorityTaskManager#add} to register with the manager before calling this method, and to call
   * {@link PriorityTaskManager#remove} afterwards to unregister.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param cacheKeyFactory An optional factory for cache keys.
   * @param dataSource A {@link CacheDataSource} that works on the {@code cache}.
   * @param buffer The buffer to be used while caching.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task. Used with {@code priorityTaskManager}.
   * @param counters If not null, updated during caching.
   * @param isCanceled An optional flag that will interrupt caching if set to true.
   * @param enableEOFException Whether to throw an {@link EOFException} if end of input has been
   *     reached unexpectedly.
   * @throws IOException If an error occurs reading from the source.
   * @throws InterruptedException If the thread was interrupted directly or via {@code isCanceled}.
   */
  public static void cache(
      DataSpec dataSpec,
      Cache cache,
      @Nullable CacheKeyFactory cacheKeyFactory,
      CacheDataSource dataSource,
      byte[] buffer,
      PriorityTaskManager priorityTaskManager,
      int priority,
      @Nullable CachingCounters counters,
      @Nullable AtomicBoolean isCanceled,
      boolean enableEOFException)
      throws IOException, InterruptedException {
    Assertions.checkNotNull(dataSource);
    Assertions.checkNotNull(buffer);

    if (counters != null) {
      // Initialize the CachingCounter values.
      getCached(dataSpec, cache, cacheKeyFactory, counters);
    } else {
      // Dummy CachingCounters. No need to initialize as they will not be visible to the caller.
      counters = new CachingCounters();
    }

    String key = buildCacheKey(dataSpec, cacheKeyFactory);
    long start = dataSpec.absoluteStreamPosition;
    long left =
        dataSpec.length != C.LENGTH_UNSET
            ? dataSpec.length
            : ContentMetadata.getContentLength(cache.getContentMetadata(key));
    while (left != 0) {
      throwExceptionIfInterruptedOrCancelled(isCanceled);
      long blockLength =
          cache.getCachedLength(key, start, left != C.LENGTH_UNSET ? left : Long.MAX_VALUE);
      if (blockLength > 0) {
        // Skip already cached data.
      } else {
        // There is a hole in the cache which is at least "-blockLength" long.
        blockLength = -blockLength;
        long read =
            readAndDiscard(
                dataSpec,
                start,
                blockLength,
                dataSource,
                buffer,
                priorityTaskManager,
                priority,
                counters,
                isCanceled);
        if (read < blockLength) {
          // Reached to the end of the data.
          if (enableEOFException && left != C.LENGTH_UNSET) {
            throw new EOFException();
          }
          break;
        }
      }
      start += blockLength;
      left -= left == C.LENGTH_UNSET ? 0 : blockLength;
    }
  }

  /**
   * Reads and discards all data specified by the {@code dataSpec}.
   *
   * @param dataSpec Defines the data to be read. {@code absoluteStreamPosition} and {@code length}
   *     fields are overwritten by the following parameters.
   * @param absoluteStreamPosition The absolute position of the data to be read.
   * @param length Length of the data to be read, or {@link C#LENGTH_UNSET} if it is unknown.
   * @param dataSource The {@link DataSource} to read the data from.
   * @param buffer The buffer to be used while downloading.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task.
   * @param counters Counters to be set during reading.
   * @param isCanceled An optional flag that will interrupt caching if set to true.
   * @return Number of read bytes, or 0 if no data is available because the end of the opened range
   *     has been reached.
   */
  private static long readAndDiscard(
      DataSpec dataSpec,
      long absoluteStreamPosition,
      long length,
      DataSource dataSource,
      byte[] buffer,
      PriorityTaskManager priorityTaskManager,
      int priority,
      CachingCounters counters,
      AtomicBoolean isCanceled)
      throws IOException, InterruptedException {
    while (true) {
      if (priorityTaskManager != null) {
        // Wait for any other thread with higher priority to finish its job.
        priorityTaskManager.proceed(priority);
      }
      try {
        throwExceptionIfInterruptedOrCancelled(isCanceled);
        // Create a new dataSpec setting length to C.LENGTH_UNSET to prevent getting an error in
        // case the given length exceeds the end of input.
        dataSpec =
            new DataSpec(
                dataSpec.uri,
                dataSpec.httpMethod,
                dataSpec.httpBody,
                absoluteStreamPosition,
                dataSpec.position + absoluteStreamPosition - dataSpec.absoluteStreamPosition,
                C.LENGTH_UNSET,
                dataSpec.key,
                dataSpec.flags);
        long resolvedLength = dataSource.open(dataSpec);
        if (counters.contentLength == C.LENGTH_UNSET && resolvedLength != C.LENGTH_UNSET) {
          counters.contentLength = dataSpec.absoluteStreamPosition + resolvedLength;
        }
        long totalRead = 0;
        while (totalRead != length) {
          throwExceptionIfInterruptedOrCancelled(isCanceled);
          int read = dataSource.read(buffer, 0,
              length != C.LENGTH_UNSET ? (int) Math.min(buffer.length, length - totalRead)
                  : buffer.length);
          if (read == C.RESULT_END_OF_INPUT) {
            if (counters.contentLength == C.LENGTH_UNSET) {
              counters.contentLength = dataSpec.absoluteStreamPosition + totalRead;
            }
            break;
          }
          totalRead += read;
          counters.newlyCachedBytes += read;
        }
        return totalRead;
      } catch (PriorityTaskManager.PriorityTooLowException exception) {
        // catch and try again
      } finally {
        Util.closeQuietly(dataSource);
      }
    }
  }

  /**
   * Removes all of the data specified by the {@code dataSpec}.
   *
   * @param dataSpec Defines the data to be removed.
   * @param cache A {@link Cache} to store the data.
   * @param cacheKeyFactory An optional factory for cache keys.
   */
  public static void remove(
      DataSpec dataSpec, Cache cache, @Nullable CacheKeyFactory cacheKeyFactory) {
    remove(cache, buildCacheKey(dataSpec, cacheKeyFactory));
  }

  /**
   * Removes all of the data specified by the {@code key}.
   *
   * @param cache A {@link Cache} to store the data.
   * @param key The key whose data should be removed.
   */
  public static void remove(Cache cache, String key) {
    NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(key);
    for (CacheSpan cachedSpan : cachedSpans) {
      try {
        cache.removeSpan(cachedSpan);
      } catch (Cache.CacheException e) {
        // Do nothing.
      }
    }
  }

  private static String buildCacheKey(
      DataSpec dataSpec, @Nullable CacheKeyFactory cacheKeyFactory) {
    return (cacheKeyFactory != null ? cacheKeyFactory : DEFAULT_CACHE_KEY_FACTORY)
        .buildCacheKey(dataSpec);
  }

  private static void throwExceptionIfInterruptedOrCancelled(AtomicBoolean isCanceled)
      throws InterruptedException {
    if (Thread.interrupted() || (isCanceled != null && isCanceled.get())) {
      throw new InterruptedException();
    }
  }

  private CacheUtil() {}

}
