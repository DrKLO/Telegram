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

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.upstream.DataSink;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Writes data into a cache.
 */
public final class CacheDataSink implements DataSink {

  private final Cache cache;
  private final long maxCacheFileSize;

  private DataSpec dataSpec;
  private File file;
  private FileOutputStream outputStream;
  private long outputStreamBytesWritten;
  private long dataSpecBytesWritten;

  /**
   * Thrown when IOException is encountered when writing data into sink.
   */
  public static class CacheDataSinkException extends IOException {

    public CacheDataSinkException(IOException cause) {
      super(cause);
    }

  }


  /**
   * @param cache The cache into which data should be written.
   * @param maxCacheFileSize The maximum size of a cache file, in bytes. If the sink is opened for
   *    a {@link DataSpec} whose size exceeds this value, then the data will be fragmented into
   *    multiple cache files.
   */
  public CacheDataSink(Cache cache, long maxCacheFileSize) {
    this.cache = Assertions.checkNotNull(cache);
    this.maxCacheFileSize = maxCacheFileSize;
  }

  @Override
  public DataSink open(DataSpec dataSpec) throws CacheDataSinkException {
    // TODO: Support caching for unbounded requests. See TODO in {@link CacheDataSource} for
    // more details.
    Assertions.checkState(dataSpec.length != C.LENGTH_UNBOUNDED);
    try {
      this.dataSpec = dataSpec;
      dataSpecBytesWritten = 0;
      openNextOutputStream();
      return this;
    } catch (FileNotFoundException e) {
      throw new CacheDataSinkException(e);
    }
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws CacheDataSinkException {
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
    try {
      closeCurrentOutputStream();
    } catch (IOException e) {
      throw new CacheDataSinkException(e);
    }
  }

  private void openNextOutputStream() throws FileNotFoundException {
    file = cache.startFile(dataSpec.key, dataSpec.absoluteStreamPosition + dataSpecBytesWritten,
        Math.min(dataSpec.length - dataSpecBytesWritten, maxCacheFileSize));
    outputStream = new FileOutputStream(file);
    outputStreamBytesWritten = 0;
  }

  private void closeCurrentOutputStream() throws IOException {
    if (outputStream == null) {
      return;
    }

    boolean success = false;
    try {
      outputStream.flush();
      outputStream.getFD().sync();
      success = true;
    } finally {
      Util.closeQuietly(outputStream);
      if (success) {
        cache.commitFile(file);
      } else {
        file.delete();
      }
      outputStream = null;
      file = null;
    }
  }

}
