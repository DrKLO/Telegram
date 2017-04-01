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
package org.telegram.messenger.exoplayer2.source.chunk;

import java.io.IOException;
import java.util.List;

/**
 * A provider of {@link Chunk}s for a {@link ChunkSampleStream} to load.
 */
public interface ChunkSource {

  /**
   * If the source is currently having difficulty providing chunks, then this method throws the
   * underlying error. Otherwise does nothing.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @throws IOException The underlying error.
   */
  void maybeThrowError() throws IOException;

  /**
   * Evaluates whether {@link MediaChunk}s should be removed from the back of the queue.
   * <p>
   * Removing {@link MediaChunk}s from the back of the queue can be useful if they could be replaced
   * with chunks of a significantly higher quality (e.g. because the available bandwidth has
   * substantially increased).
   *
   * @param playbackPositionUs The current playback position.
   * @param queue The queue of buffered {@link MediaChunk}s.
   * @return The preferred queue size.
   */
  int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue);

  /**
   * Returns the next chunk to load.
   * <p>
   * If a chunk is available then {@link ChunkHolder#chunk} is set. If the end of the stream has
   * been reached then {@link ChunkHolder#endOfStream} is set. If a chunk is not available but the
   * end of the stream has not been reached, the {@link ChunkHolder} is not modified.
   *
   * @param previous The most recently loaded media chunk.
   * @param playbackPositionUs The current playback position. If {@code previous} is null then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out A holder to populate.
   */
  void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out);

  /**
   * Called when the {@link ChunkSampleStream} has finished loading a chunk obtained from this
   * source.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param chunk The chunk whose load has been completed.
   */
  void onChunkLoadCompleted(Chunk chunk);

  /**
   * Called when the {@link ChunkSampleStream} encounters an error loading a chunk obtained from
   * this source.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param cancelable Whether the load can be canceled.
   * @param e The error.
   * @return Whether the load should be canceled.
   */
  boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e);

}
