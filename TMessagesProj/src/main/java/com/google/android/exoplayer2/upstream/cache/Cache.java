/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import java.io.File;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.Set;

/**
 * A cache that supports partial caching of resources.
 *
 * <h2>Terminology</h2>
 *
 * <ul>
 *   <li>A <em>resource</em> is a complete piece of logical data, for example a complete media file.
 *   <li>A <em>cache key</em> uniquely identifies a resource. URIs are often suitable for use as
 *       cache keys, however this is not always the case. URIs are not suitable when caching
 *       resources obtained from a service that generates multiple URIs for the same underlying
 *       resource, for example because the service uses expiring URIs as a form of access control.
 *   <li>A <em>cache span</em> is a byte range within a resource, which may or may not be cached. A
 *       cache span that's not cached is called a <em>hole span</em>. A cache span that is cached
 *       corresponds to a single underlying file in the cache.
 * </ul>
 */
public interface Cache {

  /** Listener of {@link Cache} events. */
  interface Listener {

    /**
     * Called when a {@link CacheSpan} is added to the cache.
     *
     * @param cache The source of the event.
     * @param span The added {@link CacheSpan}.
     */
    void onSpanAdded(Cache cache, CacheSpan span);

    /**
     * Called when a {@link CacheSpan} is removed from the cache.
     *
     * @param cache The source of the event.
     * @param span The removed {@link CacheSpan}.
     */
    void onSpanRemoved(Cache cache, CacheSpan span);

    /**
     * Called when an existing {@link CacheSpan} is touched, causing it to be replaced. The new
     * {@link CacheSpan} is guaranteed to represent the same data as the one it replaces, however
     * {@link CacheSpan#file} and {@link CacheSpan#lastTouchTimestamp} may have changed.
     *
     * <p>Note that for span replacement, {@link #onSpanAdded(Cache, CacheSpan)} and {@link
     * #onSpanRemoved(Cache, CacheSpan)} are not called in addition to this method.
     *
     * @param cache The source of the event.
     * @param oldSpan The old {@link CacheSpan}, which has been removed from the cache.
     * @param newSpan The new {@link CacheSpan}, which has been added to the cache.
     */
    void onSpanTouched(Cache cache, CacheSpan oldSpan, CacheSpan newSpan);
  }

  /** Thrown when an error is encountered when writing data. */
  class CacheException extends IOException {

    public CacheException(String message) {
      super(message);
    }

    public CacheException(Throwable cause) {
      super(cause);
    }

    public CacheException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Returned by {@link #getUid()} if initialization failed before the unique identifier was read or
   * generated.
   */
  long UID_UNSET = -1;

  /**
   * Returns a non-negative unique identifier for the cache, or {@link #UID_UNSET} if initialization
   * failed before the unique identifier was determined.
   *
   * <p>Implementations are expected to generate and store the unique identifier alongside the
   * cached content. If the location of the cache is deleted or swapped, it is expected that a new
   * unique identifier will be generated when the cache is recreated.
   */
  long getUid();

  /**
   * Releases the cache. This method must be called when the cache is no longer required. The cache
   * must not be used after calling this method.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   */
  @WorkerThread
  void release();

  /**
   * Registers a listener to listen for changes to a given resource.
   *
   * <p>No guarantees are made about the thread or threads on which the listener is called, but it
   * is guaranteed that listener methods will be called in a serial fashion (i.e. one at a time) and
   * in the same order as events occurred.
   *
   * @param key The cache key of the resource.
   * @param listener The listener to add.
   * @return The current spans for the resource.
   */
  NavigableSet<CacheSpan> addListener(String key, Listener listener);

  /**
   * Unregisters a listener.
   *
   * @param key The cache key of the resource.
   * @param listener The listener to remove.
   */
  void removeListener(String key, Listener listener);

  /**
   * Returns the cached spans for a given resource.
   *
   * @param key The cache key of the resource.
   * @return The spans for the key.
   */
  NavigableSet<CacheSpan> getCachedSpans(String key);

  /** Returns the cache keys of all of the resources that are at least partially cached. */
  Set<String> getKeys();

  /** Returns the total disk space in bytes used by the cache. */
  long getCacheSpace();

  /**
   * A caller should invoke this method when they require data starting from a given position in a
   * given resource.
   *
   * <p>If there is a cache entry that overlaps the position, then the returned {@link CacheSpan}
   * defines the file in which the data is stored. {@link CacheSpan#isCached} is true. The caller
   * may read from the cache file, but does not acquire any locks.
   *
   * <p>If there is no cache entry overlapping {@code position}, then the returned {@link CacheSpan}
   * defines a hole in the cache starting at {@code position} into which the caller may write as it
   * obtains the data from some other source. The returned {@link CacheSpan} serves as a lock.
   * Whilst the caller holds the lock it may write data into the hole. It may split data into
   * multiple files. When the caller has finished writing a file it should commit it to the cache by
   * calling {@link #commitFile(File, long)}. When the caller has finished writing, it must release
   * the lock by calling {@link #releaseHoleSpan}.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param key The cache key of the resource.
   * @param position The starting position in the resource from which data is required.
   * @param length The length of the data being requested, or {@link C#LENGTH_UNSET} if unbounded.
   *     The length is ignored if there is a cache entry that overlaps the position. Else, it
   *     defines the maximum length of the hole {@link CacheSpan} that's returned. Cache
   *     implementations may support parallel writes into non-overlapping holes, and so passing the
   *     actual required length should be preferred to passing {@link C#LENGTH_UNSET} when possible.
   * @return The {@link CacheSpan}.
   * @throws InterruptedException If the thread was interrupted.
   * @throws CacheException If an error is encountered.
   */
  @WorkerThread
  CacheSpan startReadWrite(String key, long position, long length)
      throws InterruptedException, CacheException;

  /**
   * Same as {@link #startReadWrite(String, long, long)}. However, if the cache entry is locked,
   * then instead of blocking, this method will return null as the {@link CacheSpan}.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param key The cache key of the resource.
   * @param position The starting position in the resource from which data is required.
   * @param length The length of the data being requested, or {@link C#LENGTH_UNSET} if unbounded.
   *     The length is ignored if there is a cache entry that overlaps the position. Else, it
   *     defines the range of data locked by the returned {@link CacheSpan}.
   * @return The {@link CacheSpan}. Or null if the cache entry is locked.
   * @throws CacheException If an error is encountered.
   */
  @WorkerThread
  @Nullable
  CacheSpan startReadWriteNonBlocking(String key, long position, long length) throws CacheException;

  /**
   * Obtains a cache file into which data can be written. Must only be called when holding a
   * corresponding hole {@link CacheSpan} obtained from {@link #startReadWrite(String, long, long)}.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param key The cache key of the resource being written.
   * @param position The starting position in the resource from which data will be written.
   * @param length The length of the data being written, or {@link C#LENGTH_UNSET} if unknown. Used
   *     only to ensure that there is enough space in the cache.
   * @return The file into which data should be written.
   * @throws CacheException If an error is encountered.
   */
  @WorkerThread
  File startFile(String key, long position, long length) throws CacheException;

  /**
   * Commits a file into the cache. Must only be called when holding a corresponding hole {@link
   * CacheSpan} obtained from {@link #startReadWrite(String, long, long)}.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param file A newly written cache file.
   * @param length The length of the newly written cache file in bytes.
   * @throws CacheException If an error is encountered.
   */
  @WorkerThread
  void commitFile(File file, long length) throws CacheException;

  /**
   * Releases a {@link CacheSpan} obtained from {@link #startReadWrite(String, long, long)} which
   * corresponded to a hole in the cache.
   *
   * @param holeSpan The {@link CacheSpan} being released.
   */
  void releaseHoleSpan(CacheSpan holeSpan);

  /**
   * Removes all {@link CacheSpan CacheSpans} for a resource, deleting the underlying files.
   *
   * @param key The cache key of the resource being removed.
   */
  @WorkerThread
  void removeResource(String key);

  /**
   * Removes a cached {@link CacheSpan} from the cache, deleting the underlying file.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param span The {@link CacheSpan} to remove.
   */
  @WorkerThread
  void removeSpan(CacheSpan span);

  /**
   * Returns whether the specified range of data in a resource is fully cached.
   *
   * @param key The cache key of the resource.
   * @param position The starting position of the data in the resource.
   * @param length The length of the data.
   * @return true if the data is available in the Cache otherwise false;
   */
  boolean isCached(String key, long position, long length);

  /**
   * Returns the length of continuously cached data starting from {@code position}, up to a maximum
   * of {@code maxLength}, of a resource. If {@code position} isn't cached then {@code -holeLength}
   * is returned, where {@code holeLength} is the length of continuously uncached data starting from
   * {@code position}, up to a maximum of {@code maxLength}.
   *
   * @param key The cache key of the resource.
   * @param position The starting position of the data in the resource.
   * @param length The maximum length of the data or hole to be returned. {@link C#LENGTH_UNSET} is
   *     permitted, and is equivalent to passing {@link Long#MAX_VALUE}.
   * @return The length of the continuously cached data, or {@code -holeLength} if {@code position}
   *     isn't cached.
   */
  long getCachedLength(String key, long position, long length);

  /**
   * Returns the total number of cached bytes between {@code position} (inclusive) and {@code
   * (position + length)} (exclusive) of a resource.
   *
   * @param key The cache key of the resource.
   * @param position The starting position of the data in the resource.
   * @param length The length of the data to check. {@link C#LENGTH_UNSET} is permitted, and is
   *     equivalent to passing {@link Long#MAX_VALUE}.
   * @return The total number of cached bytes.
   */
  long getCachedBytes(String key, long position, long length);

  /**
   * Applies {@code mutations} to the {@link ContentMetadata} for the given resource. A new {@link
   * CachedContent} is added if there isn't one already for the resource.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @param key The cache key of the resource.
   * @param mutations Contains mutations to be applied to the metadata.
   * @throws CacheException If an error is encountered.
   */
  @WorkerThread
  void applyContentMetadataMutations(String key, ContentMetadataMutations mutations)
      throws CacheException;

  /**
   * Returns a {@link ContentMetadata} for the given resource.
   *
   * @param key The cache key of the resource.
   * @return The {@link ContentMetadata} for the resource.
   */
  ContentMetadata getContentMetadata(String key);
}
