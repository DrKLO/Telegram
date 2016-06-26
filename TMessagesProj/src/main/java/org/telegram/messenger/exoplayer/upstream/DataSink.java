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

import java.io.IOException;

/**
 * A component that consumes media data.
 */
public interface DataSink {

  /**
   * Opens the {@link DataSink} to consume the specified data. Calls to {@link #open(DataSpec)} and
   * {@link #close()} must be balanced.
   *
   * @param dataSpec Defines the data to be consumed.
   * @return This {@link DataSink}, for convenience.
   * @throws IOException
   */
  public DataSink open(DataSpec dataSpec) throws IOException;

  /**
   * Closes the {@link DataSink}.
   *
   * @throws IOException
   */
  public void close() throws IOException;

  /**
   * Consumes the provided data.
   *
   * @param buffer The buffer from which data should be consumed.
   * @param offset The offset of the data to consume in {@code buffer}.
   * @param length The length of the data to consume, in bytes.
   * @throws IOException
   */
  public void write(byte[] buffer, int offset, int length) throws IOException;

}
