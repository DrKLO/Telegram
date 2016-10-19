/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream.cache;

import android.net.Uri;
import android.util.Log;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.upstream.DataSink;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.upstream.FileDataSource;
import org.telegram.messenger.exoplayer.upstream.TeeDataSource;
import org.telegram.messenger.exoplayer.upstream.cache.CacheDataSink.CacheDataSinkException;
import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * A {@link DataSource} that reads and writes a {@link Cache}. Requests are fulfilled from the cache
 * when possible. When data is not cached it is requested from an upstream {@link DataSource} and
 * written into the cache.
 */
public final class CacheDataSource implements DataSource {

  /**
   * Interface definition for a callback to be notified of {@link CacheDataSource} events.
   */
  public interface EventListener {

    /**
     * Invoked when bytes have been read from the cache.
     *
     * @param cacheSizeBytes Current cache size in bytes.
     * @param cachedBytesRead Total bytes read from the cache since this method was last invoked.
     */
    void onCachedBytesRead(long cacheSizeBytes, long cachedBytesRead);

  }

  private static final String TAG = "CacheDataSource";

  private final Cache cache;
  private final DataSource cacheReadDataSource;
  private final DataSource cacheWriteDataSource;
  private final DataSource upstreamDataSource;
  private final EventListener eventListener;

  private final boolean blockOnCache;
  private final boolean ignoreCacheOnError;

  private DataSource currentDataSource;
  private Uri uri;
  private int flags;
  private String key;
  private long readPosition;
  private long bytesRemaining;
  private CacheSpan lockedSpan;
  private boolean ignoreCache;
  private long totalCachedBytesRead;

  /**
   * Constructs an instance with default {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache.
   */
  public CacheDataSource(Cache cache, DataSource upstream, boolean blockOnCache,
      boolean ignoreCacheOnError) {
    this(cache, upstream, blockOnCache, ignoreCacheOnError, Long.MAX_VALUE);
  }

  /**
   * Constructs an instance with default {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache. The sink is configured to fragment data such that no single
   * cache file is greater than maxCacheFileSize bytes.
   */
  public CacheDataSource(Cache cache, DataSource upstream, boolean blockOnCache,
      boolean ignoreCacheOnError, long maxCacheFileSize) {
    this(cache, upstream, new FileDataSource(), new CacheDataSink(cache, maxCacheFileSize),
        blockOnCache, ignoreCacheOnError, null);
  }

  /**
   * Constructs an instance with arbitrary {@link DataSource} and {@link DataSink} instances for
   * reading and writing the cache. One use of this constructor is to allow data to be transformed
   * before it is written to disk.
   *
   * @param cache The cache.
   * @param upstream A {@link DataSource} for reading data not in the cache.
   * @param cacheReadDataSource A {@link DataSource} for reading data from the cache.
   * @param cacheWriteDataSink A {@link DataSink} for writing data to the cache.
   * @param blockOnCache A flag indicating whether we will block reads if the cache key is locked.
   *     If this flag is false, then we will read from upstream if the cache key is locked.
   * @param ignoreCacheOnError Whether the cache is bypassed following any cache related error. If
   *     true, then cache related exceptions may be thrown for one cycle of open, read and close
   *     calls. Subsequent cycles of these calls will then bypass the cache.
   * @param eventListener An optional {@link EventListener} to receive events.
   */
  public CacheDataSource(Cache cache, DataSource upstream, DataSource cacheReadDataSource,
      DataSink cacheWriteDataSink, boolean blockOnCache, boolean ignoreCacheOnError,
      EventListener eventListener) {
    this.cache = cache;
    this.cacheReadDataSource = cacheReadDataSource;
    this.blockOnCache = blockOnCache;
    this.ignoreCacheOnError = ignoreCacheOnError;
    this.upstreamDataSource = upstream;
    if (cacheWriteDataSink != null) {
      this.cacheWriteDataSource = new TeeDataSource(upstream, cacheWriteDataSink);
    } else {
      this.cacheWriteDataSource = null;
    }
    this.eventListener = eventListener;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    try {
      uri = dataSpec.uri;
      flags = dataSpec.flags;
      key = dataSpec.key;
      readPosition = dataSpec.position;
      bytesRemaining = dataSpec.length;
      openNextSource();
      return dataSpec.length;
    } catch (IOException e) {
      handleBeforeThrow(e);
      throw e;
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int max) throws IOException {
    try {
      int bytesRead = currentDataSource.read(buffer, offset, max);
      if (bytesRead >= 0) {
        if (currentDataSource == cacheReadDataSource) {
          totalCachedBytesRead += bytesRead;
        }
        readPosition += bytesRead;
        if (bytesRemaining != C.LENGTH_UNBOUNDED) {
          bytesRemaining -= bytesRead;
        }
      } else {
        closeCurrentSource();
        if (bytesRemaining > 0 && bytesRemaining != C.LENGTH_UNBOUNDED) {
          openNextSource();
          return read(buffer, offset, max);
        }
      }
      return bytesRead;
    } catch (IOException e) {
      handleBeforeThrow(e);
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    notifyBytesRead();
    try {
      closeCurrentSource();
    } catch (IOException e) {
      handleBeforeThrow(e);
      throw e;
    }
  }

  /**
   * Opens the next source. If the cache contains data spanning the current read position then
   * {@link #cacheReadDataSource} is opened to read from it. Else {@link #upstreamDataSource} is
   * opened to read from the upstream source and write into the cache.
   */
  private void openNextSource() throws IOException {
    DataSpec dataSpec;
    CacheSpan span;
    if (ignoreCache) {
      span = null;
    } else if (bytesRemaining == C.LENGTH_UNBOUNDED) {
      // TODO: Support caching for unbounded requests. This requires storing the source length
      // into the cache (the simplest approach is to incorporate it into each cache file's name).
      Log.w(TAG, "Cache bypassed due to unbounded length.");
      span = null;
    } else if (blockOnCache) {
      try {
        span = cache.startReadWrite(key, readPosition);
      } catch (InterruptedException e) {
        throw new InterruptedIOException();
      }
    } else {
      span = cache.startReadWriteNonBlocking(key, readPosition);
    }
    if (span == null) {
      // The data is locked in the cache, or we're ignoring the cache. Bypass the cache and read
      // from upstream.
      currentDataSource = upstreamDataSource;
      dataSpec = new DataSpec(uri, readPosition, bytesRemaining, key, flags);
    } else if (span.isCached) {
      // Data is cached, read from cache.
      Uri fileUri = Uri.fromFile(span.file);
      long filePosition = readPosition - span.position;
      long length = Math.min(span.length - filePosition, bytesRemaining);
      dataSpec = new DataSpec(fileUri, readPosition, filePosition, length, key, flags);
      currentDataSource = cacheReadDataSource;
    } else {
      // Data is not cached, and data is not locked, read from upstream with cache backing.
      lockedSpan = span;
      long length = span.isOpenEnded() ? bytesRemaining : Math.min(span.length, bytesRemaining);
      dataSpec = new DataSpec(uri, readPosition, length, key, flags);
      currentDataSource = cacheWriteDataSource != null ? cacheWriteDataSource
          : upstreamDataSource;
    }
    currentDataSource.open(dataSpec);
  }

  private void closeCurrentSource() throws IOException {
    if (currentDataSource == null) {
      return;
    }
    try {
      currentDataSource.close();
      currentDataSource = null;
    } finally {
      if (lockedSpan != null) {
        cache.releaseHoleSpan(lockedSpan);
        lockedSpan = null;
      }
    }
  }

  private void handleBeforeThrow(IOException exception) {
    if (ignoreCacheOnError && (currentDataSource == cacheReadDataSource
        || exception instanceof CacheDataSinkException)) {
      // Ignore the cache from now on.
      ignoreCache = true;
    }
  }

  private void notifyBytesRead() {
    if (eventListener != null && totalCachedBytesRead > 0) {
      eventListener.onCachedBytesRead(cache.getCacheSpace(), totalCachedBytesRead);
      totalCachedBytesRead = 0;
    }
  }

}
