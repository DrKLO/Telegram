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
package com.google.android.exoplayer2.audio;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Interface for audio processors, which take audio data as input and transform it, potentially
 * modifying its channel count, encoding and/or sample rate.
 *
 * <p>In addition to being able to modify the format of audio, implementations may allow parameters
 * to be set that affect the output audio and whether the processor is active/inactive.
 */
public interface AudioProcessor {

  /** PCM audio format that may be handled by an audio processor. */
  final class AudioFormat {
    public static final AudioFormat NOT_SET =
        new AudioFormat(
            /* sampleRate= */ Format.NO_VALUE,
            /* channelCount= */ Format.NO_VALUE,
            /* encoding= */ Format.NO_VALUE);

    /** The sample rate in Hertz. */
    public final int sampleRate;
    /** The number of interleaved channels. */
    public final int channelCount;
    /** The type of linear PCM encoding. */
    public final @C.PcmEncoding int encoding;
    /** The number of bytes used to represent one audio frame. */
    public final int bytesPerFrame;

    public AudioFormat(int sampleRate, int channelCount, @C.PcmEncoding int encoding) {
      this.sampleRate = sampleRate;
      this.channelCount = channelCount;
      this.encoding = encoding;
      bytesPerFrame =
          Util.isEncodingLinearPcm(encoding)
              ? Util.getPcmFrameSize(encoding, channelCount)
              : Format.NO_VALUE;
    }

    @Override
    public String toString() {
      return "AudioFormat["
          + "sampleRate="
          + sampleRate
          + ", channelCount="
          + channelCount
          + ", encoding="
          + encoding
          + ']';
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AudioFormat)) {
        return false;
      }
      AudioFormat that = (AudioFormat) o;
      return sampleRate == that.sampleRate
          && channelCount == that.channelCount
          && encoding == that.encoding;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(sampleRate, channelCount, encoding);
    }
  }

  /** Exception thrown when a processor can't be configured for a given input audio format. */
  final class UnhandledAudioFormatException extends Exception {

    public UnhandledAudioFormatException(AudioFormat inputAudioFormat) {
      super("Unhandled format: " + inputAudioFormat);
    }
  }

  /** An empty, direct {@link ByteBuffer}. */
  ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  /**
   * Configures the processor to process input audio with the specified format. After calling this
   * method, call {@link #isActive()} to determine whether the audio processor is active. Returns
   * the configured output audio format if this instance is active.
   *
   * <p>After calling this method, it is necessary to {@link #flush()} the processor to apply the
   * new configuration. Before applying the new configuration, it is safe to queue input and get
   * output in the old input/output formats. Call {@link #queueEndOfStream()} when no more input
   * will be supplied in the old input format.
   *
   * @param inputAudioFormat The format of audio that will be queued after the next call to {@link
   *     #flush()}.
   * @return The configured output audio format if this instance is {@link #isActive() active}.
   * @throws UnhandledAudioFormatException Thrown if the specified format can't be handled as input.
   */
  @CanIgnoreReturnValue
  AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException;

  /** Returns whether the processor is configured and will process input buffers. */
  boolean isActive();

  /**
   * Queues audio data between the position and limit of the {@code inputBuffer} for processing.
   * After calling this method, processed output may be available via {@link #getOutput()}. Calling
   * {@code queueInput(ByteBuffer)} again invalidates any pending output.
   *
   * @param inputBuffer The input buffer to process. It must be a direct byte buffer with native
   *     byte order. Its contents are treated as read-only. Its position will be advanced by the
   *     number of bytes consumed (which may be zero). The caller retains ownership of the provided
   *     buffer.
   */
  void queueInput(ByteBuffer inputBuffer);

  /**
   * Queues an end of stream signal. After this method has been called, {@link
   * #queueInput(ByteBuffer)} may not be called until after the next call to {@link #flush()}.
   * Calling {@link #getOutput()} will return any remaining output data. Multiple calls may be
   * required to read all of the remaining output data. {@link #isEnded()} will return {@code true}
   * once all remaining output data has been read.
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
   * Returns whether this processor will return no more output from {@link #getOutput()} until
   * {@link #flush()} has been called and more input has been queued.
   */
  boolean isEnded();

  /**
   * Clears any buffered data and pending output. If the audio processor is active, also prepares
   * the audio processor to receive a new stream of input in the last configured (pending) format.
   */
  void flush();

  /** Resets the processor to its unconfigured state, releasing any resources. */
  void reset();
}
