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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioProcessor} that converts 24-bit and 32-bit integer PCM audio to 32-bit float PCM
 * audio.
 */
/* package */ final class FloatResamplingAudioProcessor implements AudioProcessor {

  private static final int FLOAT_NAN_AS_INT = Float.floatToIntBits(Float.NaN);
  private static final double PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR = 1.0 / 0x7FFFFFFF;

  private int sampleRateHz;
  private int channelCount;
  private @C.PcmEncoding int sourceEncoding;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  /** Creates a new audio processor that converts audio data to {@link C#ENCODING_PCM_FLOAT}. */
  public FloatResamplingAudioProcessor() {
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    sourceEncoding = C.ENCODING_INVALID;
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
      throws UnhandledFormatException {
    if (!Util.isEncodingHighResolutionIntegerPcm(encoding)) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (this.sampleRateHz == sampleRateHz
        && this.channelCount == channelCount
        && sourceEncoding == encoding) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    sourceEncoding = encoding;
    return true;
  }

  @Override
  public boolean isActive() {
    return Util.isEncodingHighResolutionIntegerPcm(sourceEncoding);
  }

  @Override
  public int getOutputChannelCount() {
    return channelCount;
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_FLOAT;
  }

  @Override
  public int getOutputSampleRateHz() {
    return sampleRateHz;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    boolean isInput32Bit = sourceEncoding == C.ENCODING_PCM_32BIT;
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int size = limit - position;

    int resampledSize = isInput32Bit ? size : (size / 3) * 4;
    if (buffer.capacity() < resampledSize) {
      buffer = ByteBuffer.allocateDirect(resampledSize).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }
    if (isInput32Bit) {
      for (int i = position; i < limit; i += 4) {
        int pcm32BitInteger =
            (inputBuffer.get(i) & 0xFF)
                | ((inputBuffer.get(i + 1) & 0xFF) << 8)
                | ((inputBuffer.get(i + 2) & 0xFF) << 16)
                | ((inputBuffer.get(i + 3) & 0xFF) << 24);
        writePcm32BitFloat(pcm32BitInteger, buffer);
      }
    } else {
      for (int i = position; i < limit; i += 3) {
        int pcm32BitInteger =
            ((inputBuffer.get(i) & 0xFF) << 8)
                | ((inputBuffer.get(i + 1) & 0xFF) << 16)
                | ((inputBuffer.get(i + 2) & 0xFF) << 24);
        writePcm32BitFloat(pcm32BitInteger, buffer);
      }
    }

    inputBuffer.position(inputBuffer.limit());
    buffer.flip();
    outputBuffer = buffer;
  }

  @Override
  public void queueEndOfStream() {
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isEnded() {
    return inputEnded && outputBuffer == EMPTY_BUFFER;
  }

  @Override
  public void flush() {
    outputBuffer = EMPTY_BUFFER;
    inputEnded = false;
  }

  @Override
  public void reset() {
    flush();
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    sourceEncoding = C.ENCODING_INVALID;
    buffer = EMPTY_BUFFER;
  }

  /**
   * Converts the provided 32-bit integer to a 32-bit float value and writes it to {@code buffer}.
   *
   * @param pcm32BitInt The 32-bit integer value to convert to 32-bit float in [-1.0, 1.0].
   * @param buffer The output buffer.
   */
  private static void writePcm32BitFloat(int pcm32BitInt, ByteBuffer buffer) {
    float pcm32BitFloat = (float) (PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR * pcm32BitInt);
    int floatBits = Float.floatToIntBits(pcm32BitFloat);
    if (floatBits == FLOAT_NAN_AS_INT) {
      floatBits = Float.floatToIntBits((float) 0.0);
    }
    buffer.putInt(floatBits);
  }
}
