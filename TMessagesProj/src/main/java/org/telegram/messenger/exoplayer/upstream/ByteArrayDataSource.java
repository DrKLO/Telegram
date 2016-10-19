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
package org.telegram.messenger.exoplayer.upstream;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.util.Assertions;
import java.io.IOException;

/**
 * A {@link DataSource} for reading from a byte array.
 */
public final class ByteArrayDataSource implements DataSource {

  private final byte[] data;
  private int readPosition;
  private int remainingBytes;

  /**
   * @param data The data to be read.
   */
  public ByteArrayDataSource(byte[] data) {
    Assertions.checkNotNull(data);
    Assertions.checkArgument(data.length > 0);
    this.data = data;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    readPosition = (int) dataSpec.position;
    remainingBytes = (int) ((dataSpec.length == C.LENGTH_UNBOUNDED)
        ? (data.length - dataSpec.position) : dataSpec.length);
    if (remainingBytes <= 0 || readPosition + remainingBytes > data.length) {
      throw new IOException("Unsatisfiable range: [" + readPosition + ", " + dataSpec.length
          + "], length: " + data.length);
    }
    return remainingBytes;
  }

  @Override
  public void close() throws IOException {
    // Do nothing.
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (remainingBytes == 0) {
      return -1;
    }
    length = Math.min(length, remainingBytes);
    System.arraycopy(data, readPosition, buffer, offset, length);
    readPosition += length;
    remainingBytes -= length;
    return length;
  }
}

