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
package org.telegram.messenger.exoplayer2.upstream.cache;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.PriorityTaskManager;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.NavigableSet;

/**
 * Caching related utility methods.
 */
public final class CacheUtil {

  /** Holds the counters used during caching. */
  public static class CachingCounters {
    /** Total number of already cached bytes. */
    public long alreadyCachedBytes;
    /**
     * Total number of downloaded bytes.
     *
     * <p>{@link #getCached(DataSpec, Cache, CachingCounters)} sets it to the count of the missing
     * bytes or to {@link C#LENGTH_UNSET} if {@code dataSpec} is unbounded and content length isn't
     * available in the {@code cache}.
     */
    public long downloadedBytes;
  }

  /**
   * Generates a cache key out of the given {@link Uri}.
   *
   * @param uri Uri of a content which the requested key is for.
   */
  public static String generateKey(Uri uri) {
    return uri.toString();
  }

  /**
   * Returns the {@code dataSpec.key} if not null, otherwise generates a cache key out of {@code
   * dataSpec.uri}
   *
   * @param dataSpec Defines a content which the requested key is for.
   */
  public static String getKey(DataSpec dataSpec) {
    return dataSpec.key != null ? dataSpec.key : generateKey(dataSpec.uri);
  }

  /**
   * Returns already cached and missing bytes in the {@cache} for the data defined by {@code
   * dataSpec}.
   *
   * @param dataSpec Defines the data to be checked.
   * @param cache A {@link Cache} which has the data.
   * @param counters The counters to be set. If null a new {@link CachingCounters} is created and
   *     used.
   * @return The used {@link CachingCounters} instance.
   */
  public static CachingCounters getCached(DataSpec dataSpec, Cache cache,
      CachingCounters counters) {
    try {
      return internalCache(dataSpec, cache, null, null, null, 0, counters);
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Caches the data defined by {@code dataSpec} while skipping already cached data.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param dataSource A {@link CacheDataSource} that works on the {@code cache}.
   * @param buffer The buffer to be used while caching.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task. Used with {@code priorityTaskManager}.
   * @param counters The counters to be set during caching. If not null its values reset to
   *     zero before using. If null a new {@link CachingCounters} is created and used.
   * @return The used {@link CachingCounters} instance.
   * @throws IOException If an error occurs reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public static CachingCounters cache(DataSpec dataSpec, Cache cache, CacheDataSource dataSource,
      byte[] buffer, PriorityTaskManager priorityTaskManager, int priority,
      CachingCounters counters) throws IOException, InterruptedException {
    Assertions.checkNotNull(dataSource);
    Assertions.checkNotNull(buffer);
    return internalCache(dataSpec, cache, dataSource, buffer, priorityTaskManager, priority,
        counters);
  }

  /**
   * Caches the data defined by {@code dataSpec} while skipping already cached data. If {@code
   * dataSource} or {@code buffer} is null performs a dry run.
   *
   * @param dataSpec Defines the data to be cached.
   * @param cache A {@link Cache} to store the data.
   * @param dataSource A {@link CacheDataSource} that works on the {@code cache}. If null a dry run
   *     is performed.
   * @param buffer The buffer to be used while caching. If null a dry run is performed.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task. Used with {@code priorityTaskManager}.
   * @param counters The counters to be set during caching. If not null its values reset to
   *     zero before using. If null a new {@link CachingCounters} is created and used.
   * @return The used {@link CachingCounters} instance.
   * @throws IOException If not dry run and an error occurs reading from the source.
   * @throws InterruptedException If not dry run and the thread was interrupted.
   */
  private static CachingCounters internalCache(DataSpec dataSpec, Cache cache,
      CacheDataSource dataSource, byte[] buffer, PriorityTaskManager priorityTaskManager,
      int priority, CachingCounters counters) throws IOException, InterruptedException {
    long start = dataSpec.position;
    long left = dataSpec.length;
    String key = getKey(dataSpec);
    if (left == C.LENGTH_UNSET) {
      left = cache.getContentLength(key);
      if (left == C.LENGTH_UNSET) {
        left = Long.MAX_VALUE;
      }
    }
    if (counters == null) {
      counters = new CachingCounters();
    } else {
      counters.alreadyCachedBytes = 0;
      counters.downloadedBytes = 0;
    }
    while (left > 0) {
      long blockLength = cache.getCachedBytes(key, start, left);
      // Skip already cached data
      if (blockLength > 0) {
        counters.alreadyCachedBytes += blockLength;
      } else {
        // There is a hole in the cache which is at least "-blockLength" long.
        blockLength = -blockLength;
        if (dataSource != null && buffer != null) {
          DataSpec subDataSpec = new DataSpec(dataSpec.uri, start,
              blockLength == Long.MAX_VALUE ? C.LENGTH_UNSET : blockLength, key);
          long read = readAndDiscard(subDataSpec, dataSource, buffer, priorityTaskManager,
              priority);
          counters.downloadedBytes += read;
          if (read < blockLength) {
            // Reached end of data.
            break;
          }
        } else if (blockLength == Long.MAX_VALUE) {
          counters.downloadedBytes = C.LENGTH_UNSET;
          break;
        } else {
          counters.downloadedBytes += blockLength;
        }
      }
      start += blockLength;
      if (left != Long.MAX_VALUE) {
        left -= blockLength;
      }
    }
    return counters;
  }

  /**
   * Reads and discards all data specified by the {@code dataSpec}.
   *
   * @param dataSpec Defines the data to be read.
   * @param dataSource The {@link DataSource} to read the data from.
   * @param buffer The buffer to be used while downloading.
   * @param priorityTaskManager If not null it's used to check whether it is allowed to proceed with
   *     caching.
   * @param priority The priority of this task.
   * @return Number of read bytes, or 0 if no data is available because the end of the opened range
   * has been reached.
   */
  private static long readAndDiscard(DataSpec dataSpec, DataSource dataSource, byte[] buffer,
      PriorityTaskManager priorityTaskManager, int priority)
      throws IOException, InterruptedException {
    while (true) {
      if (priorityTaskManager != null) {
        // Wait for any other thread with higher priority to finish its job.
        priorityTaskManager.proceed(priority);
      }
      try {
        dataSource.open(dataSpec);
        long totalRead = 0;
        while (true) {
          if (Thread.interrupted()) {
            throw new InterruptedException();
          }
          int read = dataSource.read(buffer, 0, buffer.length);
          if (read == C.RESULT_END_OF_INPUT) {
            return totalRead;
          }
          totalRead += read;
        }
      } catch (PriorityTaskManager.PriorityTooLowException exception) {
        // catch and try again
      } finally {
        Util.closeQuietly(dataSource);
      }
    }
  }

  /** Removes all of the data in the {@code cache} pointed by the {@code key}. */
  public static void remove(Cache cache, String key) {
    NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(key);
    if (cachedSpans == null) {
      return;
    }
    for (CacheSpan cachedSpan : cachedSpans) {
      try {
        cache.removeSpan(cachedSpan);
      } catch (Cache.CacheException e) {
        // do nothing
      }
    }
  }

  private CacheUtil() {}

}
