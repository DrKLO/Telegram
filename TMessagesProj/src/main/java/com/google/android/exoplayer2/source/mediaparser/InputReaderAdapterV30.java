/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.mediaparser;

import android.annotation.SuppressLint;
import android.media.MediaParser;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/** {@link MediaParser.SeekableInputReader} implementation wrapping a {@link DataReader}. */
@RequiresApi(30)
@SuppressLint("Override") // TODO: Remove once the SDK becomes stable.
public final class InputReaderAdapterV30 implements MediaParser.SeekableInputReader {

  @Nullable private DataReader dataReader;
  private long resourceLength;
  private long currentPosition;
  private long lastSeekPosition;

  /**
   * Sets the wrapped {@link DataReader}.
   *
   * @param dataReader The {@link DataReader} to wrap.
   * @param length The length of the resource from which {@code dataReader} reads.
   */
  public void setDataReader(DataReader dataReader, long length) {
    this.dataReader = dataReader;
    resourceLength = length;
    lastSeekPosition = C.POSITION_UNSET;
  }

  /** Sets the absolute position in the resource from which the wrapped {@link DataReader} reads. */
  public void setCurrentPosition(long position) {
    currentPosition = position;
  }

  /**
   * Returns the last value passed to {@link #seekToPosition(long)} and sets the stored value to
   * {@link C#POSITION_UNSET}.
   */
  public long getAndResetSeekPosition() {
    long lastSeekPosition = this.lastSeekPosition;
    this.lastSeekPosition = C.POSITION_UNSET;
    return lastSeekPosition;
  }

  // SeekableInputReader implementation.

  @Override
  public void seekToPosition(long position) {
    lastSeekPosition = position;
  }

  @Override
  public int read(byte[] bytes, int offset, int readLength) throws IOException {
    int bytesRead = Util.castNonNull(dataReader).read(bytes, offset, readLength);
    currentPosition += bytesRead;
    return bytesRead;
  }

  @Override
  public long getPosition() {
    return currentPosition;
  }

  @Override
  public long getLength() {
    return resourceLength;
  }
}
