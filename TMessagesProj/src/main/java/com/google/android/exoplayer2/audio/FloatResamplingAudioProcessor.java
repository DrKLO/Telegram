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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that converts high resolution PCM audio to 32-bit float. The following
 * encodings are supported as input:
 *
 * <ul>
 *   <li>{@link C#ENCODING_PCM_24BIT}
 *   <li>{@link C#ENCODING_PCM_32BIT}
 *   <li>{@link C#ENCODING_PCM_FLOAT} ({@link #isActive()} will return {@code false})
 * </ul>
 */
/* package */ final class FloatResamplingAudioProcessor extends BaseAudioProcessor {

  private static final int FLOAT_NAN_AS_INT = Float.floatToIntBits(Float.NaN);
  private static final double PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR = 1.0 / 0x7FFFFFFF;

  @Override
  @CanIgnoreReturnValue
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    @C.PcmEncoding int encoding = inputAudioFormat.encoding;
    if (!Util.isEncodingHighResolutionPcm(encoding)) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    return encoding != C.ENCODING_PCM_FLOAT
        ? new AudioFormat(
            inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
        : AudioFormat.NOT_SET;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int size = limit - position;

    ByteBuffer buffer;
    switch (inputAudioFormat.encoding) {
      case C.ENCODING_PCM_24BIT:
        buffer = replaceOutputBuffer((size / 3) * 4);
        for (int i = position; i < limit; i += 3) {
          int pcm32BitInteger =
              ((inputBuffer.get(i) & 0xFF) << 8)
                  | ((inputBuffer.get(i + 1) & 0xFF) << 16)
                  | ((inputBuffer.get(i + 2) & 0xFF) << 24);
          writePcm32BitFloat(pcm32BitInteger, buffer);
        }
        break;
      case C.ENCODING_PCM_32BIT:
        buffer = replaceOutputBuffer(size);
        for (int i = position; i < limit; i += 4) {
          int pcm32BitInteger =
              (inputBuffer.get(i) & 0xFF)
                  | ((inputBuffer.get(i + 1) & 0xFF) << 8)
                  | ((inputBuffer.get(i + 2) & 0xFF) << 16)
                  | ((inputBuffer.get(i + 3) & 0xFF) << 24);
          writePcm32BitFloat(pcm32BitInteger, buffer);
        }
        break;
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }

    inputBuffer.position(inputBuffer.limit());
    buffer.flip();
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
