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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Writes data into a cache.
 *
 * <p>If the {@link DataSpec} passed to {@link #open(DataSpec)} has the {@code length} field set to
 * {@link C#LENGTH_UNSET} and {@link DataSpec#FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN} set, then {@link
 * #write(byte[], int, int)} calls are ignored.
 */
public final class CacheDataSink implements DataSink {

  /** {@link DataSink.Factory} for {@link CacheDataSink} instances. */
  public static final class Factory implements DataSink.Factory {

    private @MonotonicNonNull Cache cache;
    private long fragmentSize;
    private int bufferSize;

    /** Creates an instance. */
    public Factory() {
      fragmentSize = CacheDataSink.DEFAULT_FRAGMENT_SIZE;
      bufferSize = CacheDataSink.DEFAULT_BUFFER_SIZE;
    }

    /**
     * Sets the cache to which data will be written.
     *
     * <p>Must be called before the factory is used.
     *
     * @param cache The cache to which data will be written.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    public Factory setCache(Cache cache) {
      this.cache = cache;
      return this;
    }

    /**
     * Sets the cache file fragment size. For requests that should be fragmented into multiple cache
     * files, this is the maximum size of a cache file in bytes. If set to {@link C#LENGTH_UNSET}
     * then no fragmentation will occur. Using a small value allows for finer-grained cache eviction
     * policies, at the cost of increased overhead both on the cache implementation and the file
     * system. Values under {@code (2 * 1024 * 1024)} are not recommended.
     *
     * <p>The default value is {@link CacheDataSink#DEFAULT_FRAGMENT_SIZE}.
     *
     * @param fragmentSize The fragment size in bytes, or {@link C#LENGTH_UNSET} to disable
     *     fragmentation.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    public Factory setFragmentSize(long fragmentSize) {
      this.fragmentSize = fragmentSize;
      return this;
    }

    /**
     * Sets the size of an in-memory buffer used when writing to a cache file. A zero or negative
     * value disables buffering.
     *
     * <p>The default value is {@link CacheDataSink#DEFAULT_BUFFER_SIZE}.
     *
     * @param bufferSize The buffer size in bytes.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    public Factory setBufferSize(int bufferSize) {
      this.bufferSize = bufferSize;
      return this;
    }

    @Override
    public DataSink createDataSink() {
      return new CacheDataSink(checkNotNull(cache), fragmentSize, bufferSize);
    }
  }

  /** Thrown when an {@link IOException} is encountered when writing data to the sink. */
  public static final class CacheDataSinkException extends CacheException {

    public CacheDataSinkException(IOException cause) {
      super(cause);
    }
  }

  /** Default {@code fragmentSize} recommended for caching use cases. */
  public static final long DEFAULT_FRAGMENT_SIZE = 5 * 1024 * 1024;
  /** Default buffer size in bytes. */
  public static final int DEFAULT_BUFFER_SIZE = 20 * 1024;

  private static final long MIN_RECOMMENDED_FRAGMENT_SIZE = 2 * 1024 * 1024;
  private static final String TAG = "CacheDataSink";

  private final Cache cache;
  private final long fragmentSize;
  private final int bufferSize;

  @Nullable private DataSpec dataSpec;
  private long dataSpecFragmentSize;
  @Nullable private File file;
  @Nullable private OutputStream outputStream;
  private long outputStreamBytesWritten;
  private long dataSpecBytesWritten;
  private @MonotonicNonNull ReusableBufferedOutputStream bufferedOutputStream;

  /**
   * Constructs an instance using {@link #DEFAULT_BUFFER_SIZE}.
   *
   * @param cache The cache into which data should be written.
   * @param fragmentSize For requests that should be fragmented into multiple cache files, this is
   *     the maximum size of a cache file in bytes. If set to {@link C#LENGTH_UNSET} then no
   *     fragmentation will occur. Using a small value allows for finer-grained cache eviction
   *     policies, at the cost of increased overhead both on the cache implementation and the file
   *     system. Values under {@code (2 * 1024 * 1024)} are not recommended.
   */
  public CacheDataSink(Cache cache, long fragmentSize) {
    this(cache, fragmentSize, DEFAULT_BUFFER_SIZE);
  }

  /**
   * @param cache The cache into which data should be written.
   * @param fragmentSize For requests that should be fragmented into multiple cache files, this is
   *     the maximum size of a cache file in bytes. If set to {@link C#LENGTH_UNSET} then no
   *     fragmentation will occur. Using a small value allows for finer-grained cache eviction
   *     policies, at the cost of increased overhead both on the cache implementation and the file
   *     system. Values under {@code (2 * 1024 * 1024)} are not recommended.
   * @param bufferSize The buffer size in bytes for writing to a cache file. A zero or negative
   *     value disables buffering.
   */
  public CacheDataSink(Cache cache, long fragmentSize, int bufferSize) {
    Assertions.checkState(
        fragmentSize > 0 || fragmentSize == C.LENGTH_UNSET,
        "fragmentSize must be positive or C.LENGTH_UNSET.");
    if (fragmentSize != C.LENGTH_UNSET && fragmentSize < MIN_RECOMMENDED_FRAGMENT_SIZE) {
      Log.w(
          TAG,
          "fragmentSize is below the minimum recommended value of "
              + MIN_RECOMMENDED_FRAGMENT_SIZE
              + ". This may cause poor cache performance.");
    }
    this.cache = checkNotNull(cache);
    this.fragmentSize = fragmentSize == C.LENGTH_UNSET ? Long.MAX_VALUE : fragmentSize;
    this.bufferSize = bufferSize;
  }

  @Override
  public void open(DataSpec dataSpec) throws CacheDataSinkException {
    checkNotNull(dataSpec.key);
    if (dataSpec.length == C.LENGTH_UNSET
        && dataSpec.isFlagSet(DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN)) {
      this.dataSpec = null;
      return;
    }
    this.dataSpec = dataSpec;
    this.dataSpecFragmentSize =
        dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION) ? fragmentSize : Long.MAX_VALUE;
    dataSpecBytesWritten = 0;
    try {
      openNextOutputStream(dataSpec);
    } catch (IOException e) {
      throw new CacheDataSinkException(e);
    }
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws CacheDataSinkException {
    @Nullable DataSpec dataSpec = this.dataSpec;
    if (dataSpec == null) {
      return;
    }
    try {
      int bytesWritten = 0;
      while (bytesWritten < length) {
        if (outputStreamBytesWritten == dataSpecFragmentSize) {
          closeCurrentOutputStream();
          openNextOutputStream(dataSpec);
        }
        int bytesToWrite =
            (int) min(length - bytesWritten, dataSpecFragmentSize - outputStreamBytesWritten);
        castNonNull(outputStream).write(buffer, offset + bytesWritten, bytesToWrite);
        bytesWritten += bytesToWrite;
        outputStreamBytesWritten += bytesToWrite;
        dataSpecBytesWritten += bytesToWrite;
      }
    } catch (IOException e) {
      throw new CacheDataSinkException(e);
    }
  }

  @Override
  public void close() throws CacheDataSinkException {
    if (dataSpec == null) {
      return;
    }
    try {
      closeCurrentOutputStream();
    } catch (IOException e) {
      throw new CacheDataSinkException(e);
    }
  }

  private void openNextOutputStream(DataSpec dataSpec) throws IOException {
    long length =
        dataSpec.length == C.LENGTH_UNSET
            ? C.LENGTH_UNSET
            : min(dataSpec.length - dataSpecBytesWritten, dataSpecFragmentSize);
    file =
        cache.startFile(
            castNonNull(dataSpec.key), dataSpec.position + dataSpecBytesWritten, length);
    FileOutputStream underlyingFileOutputStream = new FileOutputStream(file);
    if (bufferSize > 0) {
      if (bufferedOutputStream == null) {
        bufferedOutputStream =
            new ReusableBufferedOutputStream(underlyingFileOutputStream, bufferSize);
      } else {
        bufferedOutputStream.reset(underlyingFileOutputStream);
      }
      outputStream = bufferedOutputStream;
    } else {
      outputStream = underlyingFileOutputStream;
    }
    outputStreamBytesWritten = 0;
  }

  private void closeCurrentOutputStream() throws IOException {
    if (outputStream == null) {
      return;
    }

    boolean success = false;
    try {
      outputStream.flush();
      success = true;
    } finally {
      Util.closeQuietly(outputStream);
      outputStream = null;
      File fileToCommit = castNonNull(file);
      file = null;
      if (success) {
        cache.commitFile(fileToCommit, outputStreamBytesWritten);
      } else {
        fileToCommit.delete();
      }
    }
  }
}
