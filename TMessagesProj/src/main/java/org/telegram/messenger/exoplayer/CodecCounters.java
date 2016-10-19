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
package org.telegram.messenger.exoplayer;

/**
 * Maintains codec event counts, for debugging purposes only.
 * <p>
 * Counters should be written from the playback thread only. Counters may be read from any thread.
 * To ensure that the counter values are correctly reflected between threads, users of this class
 * should invoke {@link #ensureUpdated()} prior to reading and after writing.
 */
public final class CodecCounters {

  public int codecInitCount;
  public int codecReleaseCount;
  public int inputBufferCount;
  public int outputFormatChangedCount;
  public int outputBuffersChangedCount;
  public int renderedOutputBufferCount;
  public int skippedOutputBufferCount;
  public int droppedOutputBufferCount;
  public int maxConsecutiveDroppedOutputBufferCount;

  /**
   * Should be invoked from the playback thread after the counters have been updated. Should also
   * be invoked from any other thread that wishes to read the counters, before reading. These calls
   * ensure that counter updates are made visible to the reading threads.
   */
  public synchronized void ensureUpdated() {
    // Do nothing. The use of synchronized ensures a memory barrier should another thread also
    // call this method.
  }

  public String getDebugString() {
    ensureUpdated();
    StringBuilder builder = new StringBuilder();
    builder.append("cic:").append(codecInitCount);
    builder.append(" crc:").append(codecReleaseCount);
    builder.append(" ibc:").append(inputBufferCount);
    builder.append(" ofc:").append(outputFormatChangedCount);
    builder.append(" obc:").append(outputBuffersChangedCount);
    builder.append(" ren:").append(renderedOutputBufferCount);
    builder.append(" sob:").append(skippedOutputBufferCount);
    builder.append(" dob:").append(droppedOutputBufferCount);
    builder.append(" mcdob:").append(maxConsecutiveDroppedOutputBufferCount);
    return builder.toString();
  }

}
