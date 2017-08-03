/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.audio;

import org.telegram.messenger.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Interface for audio processors.
 */
public interface AudioProcessor {

  /**
   * Exception thrown when a processor can't be configured for a given input audio format.
   */
  final class UnhandledFormatException extends Exception {

    public UnhandledFormatException(int sampleRateHz, int channelCount, @C.Encoding int encoding) {
      super("Unhandled format: " + sampleRateHz + " Hz, " + channelCount + " channels in encoding "
          + encoding);
    }

  }

  /**
   * An empty, direct {@link ByteBuffer}.
   */
  ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  /**
   * Configures the processor to process input audio with the specified format. After calling this
   * method, {@link #isActive()} returns whether the processor needs to handle buffers; if not, the
   * processor will not accept any buffers until it is reconfigured. Returns {@code true} if the
   * processor must be flushed, or if the value returned by {@link #isActive()} has changed as a
   * result of the call. If it's active, {@link #getOutputChannelCount()} and
   * {@link #getOutputEncoding()} return the processor's output format.
   *
   * @param sampleRateHz The sample rate of input audio in Hz.
   * @param channelCount The number of interleaved channels in input audio.
   * @param encoding The encoding of input audio.
   * @return {@code true} if the processor must be flushed or the value returned by
   *     {@link #isActive()} has changed as a result of the call.
   * @throws UnhandledFormatException Thrown if the specified format can't be handled as input.
   */
  boolean configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
      throws UnhandledFormatException;

  /**
   * Returns whether the processor is configured and active.
   */
  boolean isActive();

  /**
   * Returns the number of audio channels in the data output by the processor.
   */
  int getOutputChannelCount();

  /**
   * Returns the audio encoding used in the data output by the processor.
   */
  @C.Encoding
  int getOutputEncoding();

  /**
   * Queues audio data between the position and limit of the input {@code buffer} for processing.
   * {@code buffer} must be a direct byte buffer with native byte order. Its contents are treated as
   * read-only. Its position will be advanced by the number of bytes consumed (which may be zero).
   * The caller retains ownership of the provided buffer. Calling this method invalidates any
   * previous buffer returned by {@link #getOutput()}.
   *
   * @param buffer The input buffer to process.
   */
  void queueInput(ByteBuffer buffer);

  /**
   * Queues an end of stream signal. After this method has been called,
   * {@link #queueInput(ByteBuffer)} may not be called until after the next call to
   * {@link #flush()}. Calling {@link #getOutput()} will return any remaining output data. Multiple
   * calls may be required to read all of the remaining output data. {@link #isEnded()} will return
   * {@code true} once all remaining output data has been read.
   */
  void queueEndOfStream();

  /**
   * Returns a buffer containing processed output data between its position and limit. The buffer
   * will always be a direct byte buffer with native byte order. Calling this method invalidates any
   * previously returned buffer. The buffer will be empty if no output is available.
   *
   * @return A buffer containing processed output data between its position and limit.
   */
  ByteBuffer getOutput();

  /**
   * Returns whether this processor will return no more output from {@link #getOutput()} until it
   * has been {@link #flush()}ed and more input has been queued.
   */
  boolean isEnded();

  /**
   * Clears any state in preparation for receiving a new stream of input buffers.
   */
  void flush();

  /**
   * Resets the processor to its initial state.
   */
  void reset();

}
