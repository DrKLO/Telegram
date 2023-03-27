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

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.PriorityTaskManager.PriorityTooLowException;
import java.io.IOException;
import java.io.InterruptedIOException;

/** Caching related utility methods. */
public final class CacheWriter {

  /** Receives progress updates during cache operations. */
  public interface ProgressListener {

    /**
     * Called when progress is made during a cache operation.
     *
     * @param requestLength The length of the content being cached in bytes, or {@link
     *     C#LENGTH_UNSET} if unknown.
     * @param bytesCached The number of bytes that are cached.
     * @param newBytesCached The number of bytes that have been newly cached since the last progress
     *     update.
     */
    void onProgress(long requestLength, long bytesCached, long newBytesCached);
  }

  /** Default buffer size to be used while caching. */
  public static final int DEFAULT_BUFFER_SIZE_BYTES = 128 * 1024;

  private final CacheDataSource dataSource;
  private final Cache cache;
  private final DataSpec dataSpec;
  private final String cacheKey;
  private final byte[] temporaryBuffer;
  @Nullable private final ProgressListener progressListener;

  private long nextPosition;
  private long endPosition;
  private long bytesCached;

  private volatile boolean isCanceled;

  /**
   * @param dataSource A {@link CacheDataSource} that writes to the target cache.
   * @param dataSpec Defines the data to be written.
   * @param temporaryBuffer A temporary buffer to be used during caching, or {@code null} if the
   *     writer should instantiate its own internal temporary buffer.
   * @param progressListener An optional progress listener.
   */
  public CacheWriter(
      CacheDataSource dataSource,
      DataSpec dataSpec,
      @Nullable byte[] temporaryBuffer,
      @Nullable ProgressListener progressListener) {
    this.dataSource = dataSource;
    this.cache = dataSource.getCache();
    this.dataSpec = dataSpec;
    this.temporaryBuffer =
        temporaryBuffer == null ? new byte[DEFAULT_BUFFER_SIZE_BYTES] : temporaryBuffer;
    this.progressListener = progressListener;
    cacheKey = dataSource.getCacheKeyFactory().buildCacheKey(dataSpec);
    nextPosition = dataSpec.position;
  }

  /**
   * Cancels this writer's caching operation. {@link #cache} checks for cancelation frequently
   * during execution, and throws an {@link InterruptedIOException} if it sees that the caching
   * operation has been canceled.
   */
  public void cancel() {
    isCanceled = true;
  }

  /**
   * Caches the requested data, skipping any that's already cached.
   *
   * <p>If the {@link CacheDataSource} used by the writer has a {@link PriorityTaskManager}, then
   * it's the responsibility of the caller to call {@link PriorityTaskManager#add} to register with
   * the manager before calling this method, and to call {@link PriorityTaskManager#remove}
   * afterwards to unregister. {@link PriorityTooLowException} will be thrown if the priority
   * required by the {@link CacheDataSource} is not high enough for progress to be made.
   *
   * <p>This method may be slow and shouldn't normally be called on the main thread.
   *
   * @throws IOException If an error occurs reading the data, or writing the data into the cache, or
   *     if the operation is canceled. If canceled, an {@link InterruptedIOException} is thrown. The
   *     method may be called again to continue the operation from where the error occurred.
   */
  @WorkerThread
  public void cache() throws IOException {
    throwIfCanceled();

    bytesCached = cache.getCachedBytes(cacheKey, dataSpec.position, dataSpec.length);
    if (dataSpec.length != C.LENGTH_UNSET) {
      endPosition = dataSpec.position + dataSpec.length;
    } else {
      long contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey));
      endPosition = contentLength == C.LENGTH_UNSET ? C.POSITION_UNSET : contentLength;
    }
    if (progressListener != null) {
      progressListener.onProgress(getLength(), bytesCached, /* newBytesCached= */ 0);
    }

    while (endPosition == C.POSITION_UNSET || nextPosition < endPosition) {
      throwIfCanceled();
      long maxRemainingLength =
          endPosition == C.POSITION_UNSET ? Long.MAX_VALUE : endPosition - nextPosition;
      long blockLength = cache.getCachedLength(cacheKey, nextPosition, maxRemainingLength);
      if (blockLength > 0) {
        nextPosition += blockLength;
      } else {
        // There's a hole of length -blockLength.
        blockLength = -blockLength;
        long nextRequestLength = blockLength == Long.MAX_VALUE ? C.LENGTH_UNSET : blockLength;
        nextPosition += readBlockToCache(nextPosition, nextRequestLength);
      }
    }
  }

  /**
   * Reads the specified block of data, writing it into the cache.
   *
   * @param position The starting position of the block.
   * @param length The length of the block, or {@link C#LENGTH_UNSET} if unbounded.
   * @return The number of bytes read.
   * @throws IOException If an error occurs reading the data or writing it to the cache.
   */
  private long readBlockToCache(long position, long length) throws IOException {
    boolean isLastBlock = position + length == endPosition || length == C.LENGTH_UNSET;

    long resolvedLength = C.LENGTH_UNSET;
    boolean isDataSourceOpen = false;
    if (length != C.LENGTH_UNSET) {
      // If the length is specified, try to open the data source with a bounded request to avoid
      // the underlying network stack requesting more data than required.
      DataSpec boundedDataSpec =
          dataSpec.buildUpon().setPosition(position).setLength(length).build();
      try {
        resolvedLength = dataSource.open(boundedDataSpec);
        isDataSourceOpen = true;
      } catch (IOException e) {
        DataSourceUtil.closeQuietly(dataSource);
      }
    }

    if (!isDataSourceOpen) {
      // Either the length was unspecified, or we allow short content and our attempt to open the
      // DataSource with the specified length failed.
      throwIfCanceled();
      DataSpec unboundedDataSpec =
          dataSpec.buildUpon().setPosition(position).setLength(C.LENGTH_UNSET).build();
      try {
        resolvedLength = dataSource.open(unboundedDataSpec);
      } catch (IOException e) {
        DataSourceUtil.closeQuietly(dataSource);
        throw e;
      }
    }

    int totalBytesRead = 0;
    try {
      if (isLastBlock && resolvedLength != C.LENGTH_UNSET) {
        onRequestEndPosition(position + resolvedLength);
      }
      int bytesRead = 0;
      while (bytesRead != C.RESULT_END_OF_INPUT) {
        throwIfCanceled();
        bytesRead = dataSource.read(temporaryBuffer, /* offset= */ 0, temporaryBuffer.length);
        if (bytesRead != C.RESULT_END_OF_INPUT) {
          onNewBytesCached(bytesRead);
          totalBytesRead += bytesRead;
        }
      }
      if (isLastBlock) {
        onRequestEndPosition(position + totalBytesRead);
      }
    } catch (IOException e) {
      DataSourceUtil.closeQuietly(dataSource);
      throw e;
    }

    // Util.closeQuietly(dataSource) is not used here because it's important that an exception is
    // thrown if DataSource.close fails. This is because there's no way of knowing whether the block
    // was successfully cached in this case.
    dataSource.close();
    return totalBytesRead;
  }

  private void onRequestEndPosition(long endPosition) {
    if (this.endPosition == endPosition) {
      return;
    }
    this.endPosition = endPosition;
    if (progressListener != null) {
      progressListener.onProgress(getLength(), bytesCached, /* newBytesCached= */ 0);
    }
  }

  private void onNewBytesCached(long newBytesCached) {
    bytesCached += newBytesCached;
    if (progressListener != null) {
      progressListener.onProgress(getLength(), bytesCached, newBytesCached);
    }
  }

  private long getLength() {
    return endPosition == C.POSITION_UNSET ? C.LENGTH_UNSET : endPosition - dataSpec.position;
  }

  private void throwIfCanceled() throws InterruptedIOException {
    if (isCanceled) {
      throw new InterruptedIOException();
    }
  }
}
