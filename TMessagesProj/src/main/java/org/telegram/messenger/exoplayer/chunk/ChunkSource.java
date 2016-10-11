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

import org.telegram.messenger.exoplayer.MediaFormat;
import java.io.IOException;
import java.util.List;

/**
 * A provider of {@link Chunk}s for a {@link ChunkSampleSource} to load.
 */
/*
 * TODO: Share more state between this interface and {@link ChunkSampleSource}. In particular
 * implementations of this class needs to know about errors, and should be more tightly integrated
 * into the process of resuming loading of a chunk after an error occurs.
 */
public interface ChunkSource {

  /**
   * If the source is currently having difficulty preparing or providing chunks, then this method
   * throws the underlying error. Otherwise does nothing.
   *
   * @throws IOException The underlying error.
   */
  void maybeThrowError() throws IOException;

  /**
   * Prepares the source.
   * <p>
   * The method can be called repeatedly until the return value indicates success.
   *
   * @return True if the source was prepared, false otherwise.
   */
  boolean prepare();

  /**
   * Returns the number of tracks exposed by the source.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The number of tracks.
   */
  int getTrackCount();

  /**
   * Gets the format of the specified track.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param track The track index.
   * @return The format of the track.
   */
  MediaFormat getFormat(int track);

  /**
   * Enable the source for the specified track.
   * <p>
   * This method should only be called after the source has been prepared, and when the source is
   * disabled.
   *
   * @param track The track index.
   */
  void enable(int track);

  /**
   * Indicates to the source that it should still be checking for updates to the stream.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param playbackPositionUs The current playback position.
   */
  void continueBuffering(long playbackPositionUs);

  /**
   * Updates the provided {@link ChunkOperationHolder} to contain the next operation that should
   * be performed by the calling {@link ChunkSampleSource}.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param queue A representation of the currently buffered {@link MediaChunk}s.
   * @param playbackPositionUs The current playback position. If the queue is empty then this
   *     parameter is the position from which playback is expected to start (or restart) and hence
   *     should be interpreted as a seek position.
   * @param out A holder for the next operation, whose {@link ChunkOperationHolder#endOfStream} is
   *     initially set to false, whose {@link ChunkOperationHolder#queueSize} is initially equal to
   *     the length of the queue, and whose {@link ChunkOperationHolder#chunk} is initially equal to
   *     null or a {@link Chunk} previously supplied by the {@link ChunkSource} that the caller has
   *     not yet finished loading. In the latter case the chunk can either be replaced or left
   *     unchanged. Note that leaving the chunk unchanged is both preferred and more efficient than
   *     replacing it with a new but identical chunk.
   */
  void getChunkOperation(List<? extends MediaChunk> queue, long playbackPositionUs,
      ChunkOperationHolder out);

  /**
   * Invoked when the {@link ChunkSampleSource} has finished loading a chunk obtained from this
   * source.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param chunk The chunk whose load has been completed.
   */
  void onChunkLoadCompleted(Chunk chunk);

  /**
   * Invoked when the {@link ChunkSampleSource} encounters an error loading a chunk obtained from
   * this source.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param chunk The chunk whose load encountered the error.
   * @param e The error.
   */
  void onChunkLoadError(Chunk chunk, Exception e);

  /**
   * Disables the source.
   * <p>
   * This method should only be called when the source is enabled.
   *
   * @param queue A representation of the currently buffered {@link MediaChunk}s.
   */
  void disable(List<? extends MediaChunk> queue);

}
