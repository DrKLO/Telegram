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
package org.telegram.messenger.exoplayer.chunk;

/**
 * Holds a chunk operation, which consists of a either:
 * <ul>
 * <li>The number of {@link MediaChunk}s that should be retained on the queue ({@link #queueSize})
 * together with the next {@link Chunk} to load ({@link #chunk}). {@link #chunk} may be null if the
 * next chunk cannot be provided yet.</li>
 * <li>A flag indicating that the end of the stream has been reached ({@link #endOfStream}).</li>
 * </ul>
 */
public final class ChunkOperationHolder {

  /**
   * The number of {@link MediaChunk}s to retain in a queue.
   */
  public int queueSize;

  /**
   * The chunk.
   */
  public Chunk chunk;

  /**
   * Indicates that the end of the stream has been reached.
   */
  public boolean endOfStream;

  /**
   * Clears the holder.
   */
  public void clear() {
    queueSize = 0;
    chunk = null;
    endOfStream = false;
  }

}
