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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that converts different PCM audio encodings to 16-bit integer PCM. The
 * following encodings are supported as input:
 *
 * <ul>
 *   <li>{@link C#ENCODING_PCM_8BIT}
 *   <li>{@link C#ENCODING_PCM_16BIT} ({@link #isActive()} will return {@code false})
 *   <li>{@link C#ENCODING_PCM_16BIT_BIG_ENDIAN}
 *   <li>{@link C#ENCODING_PCM_24BIT}
 *   <li>{@link C#ENCODING_PCM_32BIT}
 *   <li>{@link C#ENCODING_PCM_FLOAT}
 * </ul>
 */
/* package */ final class ResamplingAudioProcessor extends BaseAudioProcessor {

  @Override
  @CanIgnoreReturnValue
  public AudioFormat onConfigure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    @C.PcmEncoding int encoding = inputAudioFormat.encoding;
    if (encoding != C.ENCODING_PCM_8BIT
        && encoding != C.ENCODING_PCM_16BIT
        && encoding != C.ENCODING_PCM_16BIT_BIG_ENDIAN
        && encoding != C.ENCODING_PCM_24BIT
        && encoding != C.ENCODING_PCM_32BIT
        && encoding != C.ENCODING_PCM_FLOAT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    return encoding != C.ENCODING_PCM_16BIT
        ? new AudioFormat(
            inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
        : AudioFormat.NOT_SET;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    // Prepare the output buffer.
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int size = limit - position;
    int resampledSize;
    switch (inputAudioFormat.encoding) {
      case C.ENCODING_PCM_8BIT:
        resampledSize = size * 2;
        break;
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        resampledSize = size;
        break;
      case C.ENCODING_PCM_24BIT:
        resampledSize = (size / 3) * 2;
        break;
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_FLOAT:
        resampledSize = size / 2;
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalStateException();
    }

    // Resample the little endian input and update the input/output buffers.
    ByteBuffer buffer = replaceOutputBuffer(resampledSize);
    switch (inputAudioFormat.encoding) {
      case C.ENCODING_PCM_8BIT:
        // 8 -> 16 bit resampling. Shift each byte from [0, 256) to [-128, 128) and scale up.
        for (int i = position; i < limit; i++) {
          buffer.put((byte) 0);
          buffer.put((byte) ((inputBuffer.get(i) & 0xFF) - 128));
        }
        break;
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        // Big endian to little endian resampling. Swap the byte order.
        for (int i = position; i < limit; i += 2) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i));
        }
        break;
      case C.ENCODING_PCM_24BIT:
        // 24 -> 16 bit resampling. Drop the least significant byte.
        for (int i = position; i < limit; i += 3) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i + 2));
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32 -> 16 bit resampling. Drop the two least significant bytes.
        for (int i = position; i < limit; i += 4) {
          buffer.put(inputBuffer.get(i + 2));
          buffer.put(inputBuffer.get(i + 3));
        }
        break;
      case C.ENCODING_PCM_FLOAT:
        // 32 bit floating point -> 16 bit resampling. Floating point values are in the range
        // [-1.0, 1.0], so need to be scaled by Short.MAX_VALUE.
        for (int i = position; i < limit; i += 4) {
          // Clamp to avoid integer overflow if the floating point values exceed their nominal range
          // [Internal ref: b/161204847].
          float floatValue =
              Util.constrainValue(inputBuffer.getFloat(i), /* min= */ -1, /* max= */ 1);
          short shortValue = (short) (floatValue * Short.MAX_VALUE);
          buffer.put((byte) (shortValue & 0xFF));
          buffer.put((byte) ((shortValue >> 8) & 0xFF));
        }
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }
    inputBuffer.position(inputBuffer.limit());
    buffer.flip();
  }
}
