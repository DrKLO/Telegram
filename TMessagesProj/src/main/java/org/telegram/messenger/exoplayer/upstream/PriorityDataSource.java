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

import org.telegram.messenger.exoplayer.util.Assertions;
import java.io.IOException;

/**
 * Allows {@link #open(DataSpec)} and {@link #read(byte[], int, int)} calls only if the specified
 * priority is the highest priority of any task. {@link NetworkLock.PriorityTooLowException} is
 * thrown when this condition does not hold.
 */
public final class PriorityDataSource implements DataSource {

  private final DataSource upstream;
  private final int priority;

  /**
   * @param priority The priority of the source.
   * @param upstream The upstream {@link DataSource}.
   */
  public PriorityDataSource(int priority, DataSource upstream) {
    this.priority = priority;
    this.upstream = Assertions.checkNotNull(upstream);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    NetworkLock.instance.proceedOrThrow(priority);
    return upstream.open(dataSpec);
  }

  @Override
  public int read(byte[] buffer, int offset, int max) throws IOException {
    NetworkLock.instance.proceedOrThrow(priority);
    return upstream.read(buffer, offset, max);
  }

  @Override
  public void close() throws IOException {
    upstream.close();
  }

}
