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

import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource.EventListener;

/**
 * A {@link DataSource.Factory} that produces {@link CacheDataSource}.
 */
public final class CacheDataSourceFactory implements DataSource.Factory {

  private final Cache cache;
  private final DataSource.Factory upstreamFactory;
  private final DataSource.Factory cacheReadDataSourceFactory;
  private final DataSink.Factory cacheWriteDataSinkFactory;
  private final int flags;
  private final EventListener eventListener;

  /**
   * @see CacheDataSource#CacheDataSource(Cache, DataSource)
   */
  public CacheDataSourceFactory(Cache cache, DataSource.Factory upstreamFactory) {
    this(cache, upstreamFactory, 0);
  }

  /**
   * @see CacheDataSource#CacheDataSource(Cache, DataSource, int)
   */
  public CacheDataSourceFactory(Cache cache, DataSource.Factory upstreamFactory,
      @CacheDataSource.Flags int flags) {
    this(cache, upstreamFactory, flags, CacheDataSource.DEFAULT_MAX_CACHE_FILE_SIZE);
  }

  /**
   * @see CacheDataSource#CacheDataSource(Cache, DataSource, int, long)
   */
  public CacheDataSourceFactory(Cache cache, DataSource.Factory upstreamFactory,
      @CacheDataSource.Flags int flags, long maxCacheFileSize) {
    this(cache, upstreamFactory, new FileDataSourceFactory(),
        new CacheDataSinkFactory(cache, maxCacheFileSize), flags, null);
  }

  /**
   * @see CacheDataSource#CacheDataSource(Cache, DataSource, DataSource, DataSink, int,
   *     EventListener)
   */
  public CacheDataSourceFactory(Cache cache, Factory upstreamFactory,
      Factory cacheReadDataSourceFactory, DataSink.Factory cacheWriteDataSinkFactory,
      @CacheDataSource.Flags int flags, EventListener eventListener) {
    this.cache = cache;
    this.upstreamFactory = upstreamFactory;
    this.cacheReadDataSourceFactory = cacheReadDataSourceFactory;
    this.cacheWriteDataSinkFactory = cacheWriteDataSinkFactory;
    this.flags = flags;
    this.eventListener = eventListener;
  }

  @Override
  public CacheDataSource createDataSource() {
    return new CacheDataSource(cache, upstreamFactory.createDataSource(),
        cacheReadDataSourceFactory.createDataSource(),
        cacheWriteDataSinkFactory != null ? cacheWriteDataSinkFactory.createDataSink() : null,
        flags, eventListener);
  }

}
