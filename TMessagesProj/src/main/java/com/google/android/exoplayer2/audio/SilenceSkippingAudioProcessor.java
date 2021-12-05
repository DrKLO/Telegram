/*
 * Copyright (C) 2018 The Android Open Source Project
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

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioProcessor} that skips silence in the input stream. Input and output are 16-bit
 * PCM.
 */
public final class SilenceSkippingAudioProcessor extends BaseAudioProcessor {

  /**
   * The default value for {@link #SilenceSkippingAudioProcessor(long, long, short)
   * minimumSilenceDurationUs}.
   */
  public static final long DEFAULT_MINIMUM_SILENCE_DURATION_US = 150_000;
  /**
   * The default value for {@link #SilenceSkippingAudioProcessor(long, long, short)
   * paddingSilenceUs}.
   */
  public static final long DEFAULT_PADDING_SILENCE_US = 20_000;
  /**
   * The default value for {@link #SilenceSkippingAudioProcessor(long, long, short)
   * silenceThresholdLevel}.
   */
  public static final short DEFAULT_SILENCE_THRESHOLD_LEVEL = 1024;

  /** Trimming states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_NOISY,
    STATE_MAYBE_SILENT,
    STATE_SILENT,
  })
  private @interface State {}
  /** State when the input is not silent. */
  private static final int STATE_NOISY = 0;
  /** State when the input may be silent but we haven't read enough yet to know. */
  private static final int STATE_MAYBE_SILENT = 1;
  /** State when the input is silent. */
  private static final int STATE_SILENT = 2;

  private final long minimumSilenceDurationUs;
  private final long paddingSilenceUs;
  private final short silenceThresholdLevel;
  private int bytesPerFrame;
  private boolean enabled;

  /**
   * Buffers audio data that may be classified as silence while in {@link #STATE_MAYBE_SILENT}. If
   * the input becomes noisy before the buffer has filled, it will be output. Otherwise, the buffer
   * contents will be dropped and the state will transition to {@link #STATE_SILENT}.
   */
  private byte[] maybeSilenceBuffer;

  /**
   * Stores the latest part of the input while silent. It will be output as padding if the next
   * input is noisy.
   */
  private byte[] paddingBuffer;

  @State private int state;
  private int maybeSilenceBufferSize;
  private int paddingSize;
  private boolean hasOutputNoise;
  private long skippedFrames;

  /** Creates a new silence skipping audio processor. */
  public SilenceSkippingAudioProcessor() {
    this(
        DEFAULT_MINIMUM_SILENCE_DURATION_US,
        DEFAULT_PADDING_SILENCE_US,
        DEFAULT_SILENCE_THRESHOLD_LEVEL);
  }

  /**
   * Creates a new silence skipping audio processor.
   *
   * @param minimumSilenceDurationUs The minimum duration of audio that must be below {@code
   *     silenceThresholdLevel} to classify that part of audio as silent, in microseconds.
   * @param paddingSilenceUs The duration of silence by which to extend non-silent sections, in
   *     microseconds. The value must not exceed {@code minimumSilenceDurationUs}.
   * @param silenceThresholdLevel The absolute level below which an individual PCM sample is
   *     classified as silent.
   */
  public SilenceSkippingAudioProcessor(
      long minimumSilenceDurationUs, long paddingSilenceUs, short silenceThresholdLevel) {
    Assertions.checkArgument(paddingSilenceUs <= minimumSilenceDurationUs);
    this.minimumSilenceDurationUs = minimumSilenceDurationUs;
    this.paddingSilenceUs = paddingSilenceUs;
    this.silenceThresholdLevel = silenceThresholdLevel;

    maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY;
    paddingBuffer = Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Sets whether to skip silence in the input. This method may only be called after draining data
   * through the processor. The value returned by {@link #isActive()} may change, and the processor
   * must be {@link #flush() flushed} before queueing more data.
   *
   * @param enabled Whether to skip silence in the input.
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the total number of frames of input audio that were skipped due to being classified as
   * silence since the last call to {@link #flush()}.
   */
  public long getSkippedFrames() {
    return skippedFrames;
  }

  // AudioProcessor implementation.

  @Override
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    return enabled ? inputAudioFormat : AudioFormat.NOT_SET;
  }

  @Override
  public boolean isActive() {
    return enabled;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    while (inputBuffer.hasRemaining() && !hasPendingOutput()) {
      switch (state) {
        case STATE_NOISY:
          processNoisy(inputBuffer);
          break;
        case STATE_MAYBE_SILENT:
          processMaybeSilence(inputBuffer);
          break;
        case STATE_SILENT:
          processSilence(inputBuffer);
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  protected void onQueueEndOfStream() {
    if (maybeSilenceBufferSize > 0) {
      // We haven't received enough silence to transition to the silent state, so output the buffer.
      output(maybeSilenceBuffer, maybeSilenceBufferSize);
    }
    if (!hasOutputNoise) {
      skippedFrames += paddingSize / bytesPerFrame;
    }
  }

  @Override
  protected void onFlush() {
    if (enabled) {
      bytesPerFrame = inputAudioFormat.bytesPerFrame;
      int maybeSilenceBufferSize = durationUsToFrames(minimumSilenceDurationUs) * bytesPerFrame;
      if (maybeSilenceBuffer.length != maybeSilenceBufferSize) {
        maybeSilenceBuffer = new byte[maybeSilenceBufferSize];
      }
      paddingSize = durationUsToFrames(paddingSilenceUs) * bytesPerFrame;
      if (paddingBuffer.length != paddingSize) {
        paddingBuffer = new byte[paddingSize];
      }
    }
    state = STATE_NOISY;
    skippedFrames = 0;
    maybeSilenceBufferSize = 0;
    hasOutputNoise = false;
  }

  @Override
  protected void onReset() {
    enabled = false;
    paddingSize = 0;
    maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY;
    paddingBuffer = Util.EMPTY_BYTE_ARRAY;
  }

  // Internal methods.

  /**
   * Incrementally processes new input from {@code inputBuffer} while in {@link #STATE_NOISY},
   * updating the state if needed.
   */
  private void processNoisy(ByteBuffer inputBuffer) {
    int limit = inputBuffer.limit();

    // Check if there's any noise within the maybe silence buffer duration.
    inputBuffer.limit(Math.min(limit, inputBuffer.position() + maybeSilenceBuffer.length));
    int noiseLimit = findNoiseLimit(inputBuffer);
    if (noiseLimit == inputBuffer.position()) {
      // The buffer contains the start of possible silence.
      state = STATE_MAYBE_SILENT;
    } else {
      inputBuffer.limit(noiseLimit);
      output(inputBuffer);
    }

    // Restore the limit.
    inputBuffer.limit(limit);
  }

  /**
   * Incrementally processes new input from {@code inputBuffer} while in {@link
   * #STATE_MAYBE_SILENT}, updating the state if needed.
   */
  private void processMaybeSilence(ByteBuffer inputBuffer) {
    int limit = inputBuffer.limit();
    int noisePosition = findNoisePosition(inputBuffer);
    int maybeSilenceInputSize = noisePosition - inputBuffer.position();
    int maybeSilenceBufferRemaining = maybeSilenceBuffer.length - maybeSilenceBufferSize;
    if (noisePosition < limit && maybeSilenceInputSize < maybeSilenceBufferRemaining) {
      // The maybe silence buffer isn't full, so output it and switch back to the noisy state.
      output(maybeSilenceBuffer, maybeSilenceBufferSize);
      maybeSilenceBufferSize = 0;
      state = STATE_NOISY;
    } else {
      // Fill as much of the maybe silence buffer as possible.
      int bytesToWrite = Math.min(maybeSilenceInputSize, maybeSilenceBufferRemaining);
      inputBuffer.limit(inputBuffer.position() + bytesToWrite);
      inputBuffer.get(maybeSilenceBuffer, maybeSilenceBufferSize, bytesToWrite);
      maybeSilenceBufferSize += bytesToWrite;
      if (maybeSilenceBufferSize == maybeSilenceBuffer.length) {
        // We've reached a period of silence, so skip it, taking in to account padding for both
        // the noisy to silent transition and any future silent to noisy transition.
        if (hasOutputNoise) {
          output(maybeSilenceBuffer, paddingSize);
          skippedFrames += (maybeSilenceBufferSize - paddingSize * 2) / bytesPerFrame;
        } else {
          skippedFrames += (maybeSilenceBufferSize - paddingSize) / bytesPerFrame;
        }
        updatePaddingBuffer(inputBuffer, maybeSilenceBuffer, maybeSilenceBufferSize);
        maybeSilenceBufferSize = 0;
        state = STATE_SILENT;
      }

      // Restore the limit.
      inputBuffer.limit(limit);
    }
  }

  /**
   * Incrementally processes new input from {@code inputBuffer} while in {@link #STATE_SILENT},
   * updating the state if needed.
   */
  private void processSilence(ByteBuffer inputBuffer) {
    int limit = inputBuffer.limit();
    int noisyPosition = findNoisePosition(inputBuffer);
    inputBuffer.limit(noisyPosition);
    skippedFrames += inputBuffer.remaining() / bytesPerFrame;
    updatePaddingBuffer(inputBuffer, paddingBuffer, paddingSize);
    if (noisyPosition < limit) {
      // Output the padding, which may include previous input as well as new input, then transition
      // back to the noisy state.
      output(paddingBuffer, paddingSize);
      state = STATE_NOISY;

      // Restore the limit.
      inputBuffer.limit(limit);
    }
  }

  /**
   * Copies {@code length} elements from {@code data} to populate a new output buffer from the
   * processor.
   */
  private void output(byte[] data, int length) {
    replaceOutputBuffer(length).put(data, 0, length).flip();
    if (length > 0) {
      hasOutputNoise = true;
    }
  }

  /**
   * Copies remaining bytes from {@code data} to populate a new output buffer from the processor.
   */
  private void output(ByteBuffer data) {
    int length = data.remaining();
    replaceOutputBuffer(length).put(data).flip();
    if (length > 0) {
      hasOutputNoise = true;
    }
  }

  /**
   * Fills {@link #paddingBuffer} using data from {@code input}, plus any additional buffered data
   * at the end of {@code buffer} (up to its {@code size}) required to fill it, advancing the input
   * position.
   */
  private void updatePaddingBuffer(ByteBuffer input, byte[] buffer, int size) {
    int fromInputSize = Math.min(input.remaining(), paddingSize);
    int fromBufferSize = paddingSize - fromInputSize;
    System.arraycopy(
        /* src= */ buffer,
        /* srcPos= */ size - fromBufferSize,
        /* dest= */ paddingBuffer,
        /* destPos= */ 0,
        /* length= */ fromBufferSize);
    input.position(input.limit() - fromInputSize);
    input.get(paddingBuffer, fromBufferSize, fromInputSize);
  }

  /**
   * Returns the number of input frames corresponding to {@code durationUs} microseconds of audio.
   */
  private int durationUsToFrames(long durationUs) {
    return (int) ((durationUs * inputAudioFormat.sampleRate) / C.MICROS_PER_SECOND);
  }

  /**
   * Returns the earliest byte position in [position, limit) of {@code buffer} that contains a frame
   * classified as a noisy frame, or the limit of the buffer if no such frame exists.
   */
  private int findNoisePosition(ByteBuffer buffer) {
    Assertions.checkArgument(buffer.order() == ByteOrder.LITTLE_ENDIAN);
    // The input is in ByteOrder.nativeOrder(), which is little endian on Android.
    for (int i = buffer.position(); i < buffer.limit(); i += 2) {
      if (Math.abs(buffer.getShort(i)) > silenceThresholdLevel) {
        // Round to the start of the frame.
        return bytesPerFrame * (i / bytesPerFrame);
      }
    }
    return buffer.limit();
  }

  /**
   * Returns the earliest byte position in [position, limit) of {@code buffer} such that all frames
   * from the byte position to the limit are classified as silent.
   */
  private int findNoiseLimit(ByteBuffer buffer) {
    Assertions.checkArgument(buffer.order() == ByteOrder.LITTLE_ENDIAN);
    // The input is in ByteOrder.nativeOrder(), which is little endian on Android.
    for (int i = buffer.limit() - 2; i >= buffer.position(); i -= 2) {
      if (Math.abs(buffer.getShort(i)) > silenceThresholdLevel) {
        // Return the start of the next frame.
        return bytesPerFrame * (i / bytesPerFrame) + bytesPerFrame;
      }
    }
    return buffer.position();
  }
}
