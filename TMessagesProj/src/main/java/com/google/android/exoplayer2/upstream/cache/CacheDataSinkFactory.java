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

/**
 * A {@link DataSink.Factory} that produces {@link CacheDataSink}.
 */
public final class CacheDataSinkFactory implements DataSink.Factory {

  private final Cache cache;
  private final long fragmentSize;
  private final int bufferSize;

  private boolean syncFileDescriptor;
  private boolean respectCacheFragmentationFlag;

  /** @see CacheDataSink#CacheDataSink(Cache, long) */
  public CacheDataSinkFactory(Cache cache, long fragmentSize) {
    this(cache, fragmentSize, CacheDataSink.DEFAULT_BUFFER_SIZE);
  }

  /** @see CacheDataSink#CacheDataSink(Cache, long, int) */
  public CacheDataSinkFactory(Cache cache, long fragmentSize, int bufferSize) {
    this.cache = cache;
    this.fragmentSize = fragmentSize;
    this.bufferSize = bufferSize;
  }

  /**
   * See {@link CacheDataSink#experimental_setSyncFileDescriptor(boolean)}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   */
  public CacheDataSinkFactory experimental_setSyncFileDescriptor(boolean syncFileDescriptor) {
    this.syncFileDescriptor = syncFileDescriptor;
    return this;
  }

  /**
   * See {@link CacheDataSink#experimental_setRespectCacheFragmentationFlag(boolean)}.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   */
  public CacheDataSinkFactory experimental_setRespectCacheFragmentationFlag(
      boolean respectCacheFragmentationFlag) {
    this.respectCacheFragmentationFlag = respectCacheFragmentationFlag;
    return this;
  }

  @Override
  public DataSink createDataSink() {
    CacheDataSink dataSink = new CacheDataSink(cache, fragmentSize, bufferSize);
    dataSink.experimental_setSyncFileDescriptor(syncFileDescriptor);
    dataSink.experimental_setRespectCacheFragmentationFlag(respectCacheFragmentationFlag);
    return dataSink;
  }
}
