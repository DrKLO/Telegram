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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ReusableBufferedOutputStream;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes data into a cache.
 *
 * <p>If the {@link DataSpec} object used with {@link #open(DataSpec)} method call has the {@code
 * length} field set to {@link C#LENGTH_UNSET} but {@link
 * DataSpec#FLAG_ALLOW_CACHING_UNKNOWN_LENGTH} isn't set then {@link #write(byte[], int, int)} calls
 * are ignored.
 */
public final class CacheDataSink implements DataSink {

  /** Default buffer size in bytes. */
  public static final int DEFAULT_BUFFER_SIZE = 20480;

  private final Cache cache;
  private final long maxCacheFileSize;
  private final int bufferSize;
  private final boolean syncFileDescriptor;

  private DataSpec dataSpec;
  private File file;
  private OutputStream outputStream;
  private FileOutputStream underlyingFileOutputStream;
  private long outputStreamBytesWritten;
  private long dataSpecBytesWritten;
  private ReusableBufferedOutputStream bufferedOutputStream;

  /**
   * Thrown when IOException is encountered when writing data into sink.
   */
  public static class CacheDataSinkException extends CacheException {

    public CacheDataSinkException(IOException cause) {
      super(cause);
    }

  }

  /**
   * Constructs a CacheDataSink using the {@link #DEFAULT_BUFFER_SIZE}.
   *
   * @param cache The cache into which data should be written.
   * @param maxCacheFileSize The maximum size of a cache file, in bytes. If the sink is opened for
   *    a {@link DataSpec} whose size exceeds this value, then the data will be fragmented into
   *    multiple cache files.
   */
  public CacheDataSink(Cache cache, long maxCacheFileSize) {
    this(cache, maxCacheFileSize, DEFAULT_BUFFER_SIZE, true);
  }

  /**
   * Constructs a CacheDataSink using the {@link #DEFAULT_BUFFER_SIZE}.
   *
   * @param cache The cache into which data should be written.
   * @param maxCacheFileSize The maximum size of a cache file, in bytes. If the sink is opened for a
   *     {@link DataSpec} whose size exceeds this value, then the data will be fragmented into
   *     multiple cache files.
   * @param syncFileDescriptor Whether file descriptors are sync'd when closing output streams.
   */
  public CacheDataSink(Cache cache, long maxCacheFileSize, boolean syncFileDescriptor) {
    this(cache, maxCacheFileSize, DEFAULT_BUFFER_SIZE, syncFileDescriptor);
  }

  /**
   * @param cache The cache into which data should be written.
   * @param maxCacheFileSize The maximum size of a cache file, in bytes. If the sink is opened for a
   *     {@link DataSpec} whose size exceeds this value, then the data will be fragmented into
   *     multiple cache files.
   * @param bufferSize The buffer size in bytes for writing to a cache file. A zero or negative
   *     value disables buffering.
   */
  public CacheDataSink(Cache cache, long maxCacheFileSize, int bufferSize) {
    this(cache, maxCacheFileSize, bufferSize, true);
  }

  /**
   * @param cache The cache into which data should be written.
   * @param maxCacheFileSize The maximum size of a cache file, in bytes. If the sink is opened for a
   *     {@link DataSpec} whose size exceeds this value, then the data will be fragmented into
   *     multiple cache files.
   * @param bufferSize The buffer size in bytes for writing to a cache file. A zero or negative
   *     value disables buffering.
   * @param syncFileDescriptor Whether file descriptors are sync'd when closing output streams.
   */
  public CacheDataSink(
      Cache cache, long maxCacheFileSize, int bufferSize, boolean syncFileDescriptor) {
    this.cache = Assertions.checkNotNull(cache);
    this.maxCacheFileSize = maxCacheFileSize;
    this.bufferSize = bufferSize;
    this.syncFileDescriptor = syncFileDescriptor;
  }

  @Override
  public void open(DataSpec dataSpec) throws CacheDataSinkException {
    if (dataSpec.length == C.LENGTH_UNSET
        && !dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH)) {
      this.dataSpec = null;
      return;
    }
    this.dataSpec = dataSpec;
    dataSpecBytesWritten = 0;
    try {
      openNextOutputStream();
    } catch (IOException e) {
      throw new CacheDataSinkException(e);
    }
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws CacheDataSinkException {
    if (dataSpec == null) {
      return;
    }
    try {
      int bytesWritten = 0;
      while (bytesWritten < length) {
        if (outputStreamBytesWritten == maxCacheFileSize) {
          closeCurrentOutputStream();
          openNextOutputStream();
        }
        int bytesToWrite = (int) Math.min(length - bytesWritten,
            maxCacheFileSize - outputStreamBytesWritten);
        outputStream.write(buffer, offset + bytesWritten, bytesToWrite);
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

  private void openNextOutputStream() throws IOException {
    long maxLength = dataSpec.length == C.LENGTH_UNSET ? maxCacheFileSize
        : Math.min(dataSpec.length - dataSpecBytesWritten, maxCacheFileSize);
    file = cache.startFile(dataSpec.key, dataSpec.absoluteStreamPosition + dataSpecBytesWritten,
        maxLength);
    underlyingFileOutputStream = new FileOutputStream(file);
    if (bufferSize > 0) {
      if (bufferedOutputStream == null) {
        bufferedOutputStream = new ReusableBufferedOutputStream(underlyingFileOutputStream,
            bufferSize);
      } else {
        bufferedOutputStream.reset(underlyingFileOutputStream);
      }
      outputStream = bufferedOutputStream;
    } else {
      outputStream = underlyingFileOutputStream;
    }
    outputStreamBytesWritten = 0;
  }

  @SuppressWarnings("ThrowFromFinallyBlock")
  private void closeCurrentOutputStream() throws IOException {
    if (outputStream == null) {
      return;
    }

    boolean success = false;
    try {
      outputStream.flush();
      if (syncFileDescriptor) {
        underlyingFileOutputStream.getFD().sync();
      }
      success = true;
    } finally {
      Util.closeQuietly(outputStream);
      outputStream = null;
      File fileToCommit = file;
      file = null;
      if (success) {
        cache.commitFile(fileToCommit);
      } else {
        fileToCommit.delete();
      }
    }
  }

}
