/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2010 Bill Cox, Sonic Library
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

import org.telegram.messenger.exoplayer2.util.Assertions;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * Sonic audio stream processor for time/pitch stretching.
 * <p>
 * Based on https://github.com/waywardgeek/sonic.
 */
/* package */ final class Sonic {

  private static final int MINIMUM_PITCH = 65;
  private static final int MAXIMUM_PITCH = 400;
  private static final int AMDF_FREQUENCY = 4000;

  private static final int SINC_FILTER_POINTS = 12;
  private static final int SINC_TABLE_SIZE = 601;

  private static final short sincTable[] = {
          0, 0, 0, 0, 0, 0, 0, -1, -1, -2, -2, -3, -4, -6, -7, -9, -10, -12, -14,
          -17, -19, -21, -24, -26, -29, -32, -34, -37, -40, -42, -44, -47, -48, -50,
          -51, -52, -53, -53, -53, -52, -50, -48, -46, -43, -39, -34, -29, -22, -16,
          -8, 0, 9, 19, 29, 41, 53, 65, 79, 92, 107, 121, 137, 152, 168, 184, 200,
          215, 231, 247, 262, 276, 291, 304, 317, 328, 339, 348, 357, 363, 369, 372,
          374, 375, 373, 369, 363, 355, 345, 332, 318, 300, 281, 259, 234, 208, 178,
          147, 113, 77, 39, 0, -41, -85, -130, -177, -225, -274, -324, -375, -426,
          -478, -530, -581, -632, -682, -731, -779, -825, -870, -912, -951, -989,
          -1023, -1053, -1080, -1104, -1123, -1138, -1149, -1154, -1155, -1151,
          -1141, -1125, -1105, -1078, -1046, -1007, -963, -913, -857, -796, -728,
          -655, -576, -492, -403, -309, -210, -107, 0, 111, 225, 342, 462, 584, 708,
          833, 958, 1084, 1209, 1333, 1455, 1575, 1693, 1807, 1916, 2022, 2122, 2216,
          2304, 2384, 2457, 2522, 2579, 2625, 2663, 2689, 2706, 2711, 2705, 2687,
          2657, 2614, 2559, 2491, 2411, 2317, 2211, 2092, 1960, 1815, 1658, 1489,
          1308, 1115, 912, 698, 474, 241, 0, -249, -506, -769, -1037, -1310, -1586,
          -1864, -2144, -2424, -2703, -2980, -3254, -3523, -3787, -4043, -4291,
          -4529, -4757, -4972, -5174, -5360, -5531, -5685, -5819, -5935, -6029,
          -6101, -6150, -6175, -6175, -6149, -6096, -6015, -5905, -5767, -5599,
          -5401, -5172, -4912, -4621, -4298, -3944, -3558, -3141, -2693, -2214,
          -1705, -1166, -597, 0, 625, 1277, 1955, 2658, 3386, 4135, 4906, 5697, 6506,
          7332, 8173, 9027, 9893, 10769, 11654, 12544, 13439, 14335, 15232, 16128,
          17019, 17904, 18782, 19649, 20504, 21345, 22170, 22977, 23763, 24527,
          25268, 25982, 26669, 27327, 27953, 28547, 29107, 29632, 30119, 30569,
          30979, 31349, 31678, 31964, 32208, 32408, 32565, 32677, 32744, 32767,
          32744, 32677, 32565, 32408, 32208, 31964, 31678, 31349, 30979, 30569,
          30119, 29632, 29107, 28547, 27953, 27327, 26669, 25982, 25268, 24527,
          23763, 22977, 22170, 21345, 20504, 19649, 18782, 17904, 17019, 16128,
          15232, 14335, 13439, 12544, 11654, 10769, 9893, 9027, 8173, 7332, 6506,
          5697, 4906, 4135, 3386, 2658, 1955, 1277, 625, 0, -597, -1166, -1705,
          -2214, -2693, -3141, -3558, -3944, -4298, -4621, -4912, -5172, -5401,
          -5599, -5767, -5905, -6015, -6096, -6149, -6175, -6175, -6150, -6101,
          -6029, -5935, -5819, -5685, -5531, -5360, -5174, -4972, -4757, -4529,
          -4291, -4043, -3787, -3523, -3254, -2980, -2703, -2424, -2144, -1864,
          -1586, -1310, -1037, -769, -506, -249, 0, 241, 474, 698, 912, 1115, 1308,
          1489, 1658, 1815, 1960, 2092, 2211, 2317, 2411, 2491, 2559, 2614, 2657,
          2687, 2705, 2711, 2706, 2689, 2663, 2625, 2579, 2522, 2457, 2384, 2304,
          2216, 2122, 2022, 1916, 1807, 1693, 1575, 1455, 1333, 1209, 1084, 958, 833,
          708, 584, 462, 342, 225, 111, 0, -107, -210, -309, -403, -492, -576, -655,
          -728, -796, -857, -913, -963, -1007, -1046, -1078, -1105, -1125, -1141,
          -1151, -1155, -1154, -1149, -1138, -1123, -1104, -1080, -1053, -1023, -989,
          -951, -912, -870, -825, -779, -731, -682, -632, -581, -530, -478, -426,
          -375, -324, -274, -225, -177, -130, -85, -41, 0, 39, 77, 113, 147, 178,
          208, 234, 259, 281, 300, 318, 332, 345, 355, 363, 369, 373, 375, 374, 372,
          369, 363, 357, 348, 339, 328, 317, 304, 291, 276, 262, 247, 231, 215, 200,
          184, 168, 152, 137, 121, 107, 92, 79, 65, 53, 41, 29, 19, 9, 0, -8, -16,
          -22, -29, -34, -39, -43, -46, -48, -50, -52, -53, -53, -53, -52, -51, -50,
          -48, -47, -44, -42, -40, -37, -34, -32, -29, -26, -24, -21, -19, -17, -14,
          -12, -10, -9, -7, -6, -4, -3, -2, -2, -1, -1, 0, 0, 0, 0, 0, 0, 0
  };

  private final int inputSampleRateHz;
  private final int channelCount;
  private final float speed;
  private final float pitch;
  private final float rate;
  private final int minPeriod;
  private final int maxPeriod;
  private final int maxRequiredFrameCount;
  private final short[] downSampleBuffer;

  private short[] inputBuffer;
  private int inputFrameCount;
  private short[] outputBuffer;
  private int outputFrameCount;
  private short[] pitchBuffer;
  private int pitchFrameCount;
  private int oldRatePosition;
  private int newRatePosition;
  private int remainingInputToCopyFrameCount;
  private int prevPeriod;
  private int prevMinDiff;
  private int minDiff;
  private int maxDiff;

  /**
   * Creates a new Sonic audio stream processor.
   *
   * @param inputSampleRateHz The sample rate of input audio, in hertz.
   * @param channelCount The number of channels in the input audio.
   * @param speed The speedup factor for output audio.
   * @param pitch The pitch factor for output audio.
   * @param outputSampleRateHz The sample rate for output audio, in hertz.
   */
  public Sonic(
      int inputSampleRateHz, int channelCount, float speed, float pitch, int outputSampleRateHz) {
    this.inputSampleRateHz = inputSampleRateHz;
    this.channelCount = channelCount;
    this.speed = speed;
    this.pitch = pitch;
    rate = (float) inputSampleRateHz / outputSampleRateHz;
    minPeriod = inputSampleRateHz / MAXIMUM_PITCH;
    maxPeriod = inputSampleRateHz / MINIMUM_PITCH;
    maxRequiredFrameCount = 2 * maxPeriod;
    downSampleBuffer = new short[maxRequiredFrameCount];
    inputBuffer = new short[maxRequiredFrameCount * channelCount];
    outputBuffer = new short[maxRequiredFrameCount * channelCount];
    pitchBuffer = new short[maxRequiredFrameCount * channelCount];
  }

  /**
   * Queues remaining data from {@code buffer}, and advances its position by the number of bytes
   * consumed.
   *
   * @param buffer A {@link ShortBuffer} containing input data between its position and limit.
   */
  public void queueInput(ShortBuffer buffer) {
    int framesToWrite = buffer.remaining() / channelCount;
    int bytesToWrite = framesToWrite * channelCount * 2;
    inputBuffer = ensureSpaceForAdditionalFrames(inputBuffer, inputFrameCount, framesToWrite);
    buffer.get(inputBuffer, inputFrameCount * channelCount, bytesToWrite / 2);
    inputFrameCount += framesToWrite;
    processStreamInput();
  }

  /**
   * Gets available output, outputting to the start of {@code buffer}. The buffer's position will be
   * advanced by the number of bytes written.
   *
   * @param buffer A {@link ShortBuffer} into which output will be written.
   */
  public void getOutput(ShortBuffer buffer) {
    int framesToRead = Math.min(buffer.remaining() / channelCount, outputFrameCount);
    buffer.put(outputBuffer, 0, framesToRead * channelCount);
    outputFrameCount -= framesToRead;
    System.arraycopy(
        outputBuffer,
        framesToRead * channelCount,
        outputBuffer,
        0,
        outputFrameCount * channelCount);
  }

  /**
   * Forces generating output using whatever data has been queued already. No extra delay will be
   * added to the output, but flushing in the middle of words could introduce distortion.
   */
  public void queueEndOfStream() {
    int remainingFrameCount = inputFrameCount;
    float s = speed / pitch;
    float r = rate * pitch;
    int expectedOutputFrames =
        outputFrameCount + (int) ((remainingFrameCount / s + pitchFrameCount) / r + 0.5f);

    // Add enough silence to flush both input and pitch buffers.
    inputBuffer =
        ensureSpaceForAdditionalFrames(
            inputBuffer, inputFrameCount, remainingFrameCount + 2 * maxRequiredFrameCount);
    for (int xSample = 0; xSample < 2 * maxRequiredFrameCount * channelCount; xSample++) {
      inputBuffer[remainingFrameCount * channelCount + xSample] = 0;
    }
    inputFrameCount += 2 * maxRequiredFrameCount;
    processStreamInput();
    // Throw away any extra frames we generated due to the silence we added.
    if (outputFrameCount > expectedOutputFrames) {
      outputFrameCount = expectedOutputFrames;
    }
    // Empty input and pitch buffers.
    inputFrameCount = 0;
    remainingInputToCopyFrameCount = 0;
    pitchFrameCount = 0;
  }

  /** Clears state in preparation for receiving a new stream of input buffers. */
  public void flush() {
    inputFrameCount = 0;
    outputFrameCount = 0;
    pitchFrameCount = 0;
    oldRatePosition = 0;
    newRatePosition = 0;
    remainingInputToCopyFrameCount = 0;
    prevPeriod = 0;
    prevMinDiff = 0;
    minDiff = 0;
    maxDiff = 0;
  }

  /** Returns the number of output frames that can be read with {@link #getOutput(ShortBuffer)}. */
  public int getFramesAvailable() {
    return outputFrameCount;
  }

  // Internal methods.

  /**
   * Returns {@code buffer} or a copy of it, such that there is enough space in the returned buffer
   * to store {@code newFrameCount} additional frames.
   *
   * @param buffer The buffer.
   * @param frameCount The number of frames already in the buffer.
   * @param additionalFrameCount The number of additional frames that need to be stored in the
   *     buffer.
   * @return A buffer with enough space for the additional frames.
   */
  private short[] ensureSpaceForAdditionalFrames(
      short[] buffer, int frameCount, int additionalFrameCount) {
    int currentCapacityFrames = buffer.length / channelCount;
    if (frameCount + additionalFrameCount <= currentCapacityFrames) {
      return buffer;
    } else {
      int newCapacityFrames = 3 * currentCapacityFrames / 2 + additionalFrameCount;
      return Arrays.copyOf(buffer, newCapacityFrames * channelCount);
    }
  }

  private void removeProcessedInputFrames(int positionFrames) {
    int remainingFrames = inputFrameCount - positionFrames;
    System.arraycopy(
        inputBuffer, positionFrames * channelCount, inputBuffer, 0, remainingFrames * channelCount);
    inputFrameCount = remainingFrames;
  }

  private void copyToOutput(short[] samples, int positionFrames, int frameCount) {
    outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, frameCount);
    System.arraycopy(
        samples,
        positionFrames * channelCount,
        outputBuffer,
        outputFrameCount * channelCount,
        frameCount * channelCount);
    outputFrameCount += frameCount;
  }

  private int copyInputToOutput(int positionFrames) {
    int frameCount = Math.min(maxRequiredFrameCount, remainingInputToCopyFrameCount);
    copyToOutput(inputBuffer, positionFrames, frameCount);
    remainingInputToCopyFrameCount -= frameCount;
    return frameCount;
  }

  private void downSampleInput(short samples[], int position, int skip) {
    int numSamples = maxRequiredFrameCount / skip;
    int samplesPerValue = channelCount * skip;
    int value;

    position *= channelCount;
    for (int i = 0; i < numSamples; i++) {
      value = 0;
      for (int j = 0; j < samplesPerValue; j++) {
        value += samples[position + i * samplesPerValue + j];
      }
      value /= samplesPerValue;
      downSampleBuffer[i] = (short) value;
    }
  }

  private int findPitchPeriodInRange(
          short samples[],
          int position,
          int minPeriod,
          int maxPeriod)
  {
    int bestPeriod = 0, worstPeriod = 255;
    int minDiff = 1, maxDiff = 0;

    position *= channelCount;
    for(int period = minPeriod; period <= maxPeriod; period++) {
      int diff = 0;
      for(int i = 0; i < period; i++) {
        short sVal = samples[position + i];
        short pVal = samples[position + period + i];
        diff += sVal >= pVal? sVal - pVal : pVal - sVal;
      }
      if(diff*bestPeriod < minDiff*period) {
        minDiff = diff;
        bestPeriod = period;
      }
      if(diff*worstPeriod > maxDiff*period) {
        maxDiff = diff;
        worstPeriod = period;
      }
    }
    this.minDiff = minDiff/bestPeriod;
    this.maxDiff = maxDiff/worstPeriod;

    return bestPeriod;
  }

  /**
   * Returns whether the previous pitch period estimate is a better approximation, which can occur
   * at the abrupt end of voiced words.
   */
  private boolean previousPeriodBetter(int minDiff, int maxDiff, boolean preferNewPeriod) {
    if (minDiff == 0 || prevPeriod == 0) {
      return false;
    }
    if (preferNewPeriod) {
      if (maxDiff > minDiff * 3) {
        // Got a reasonable match this period
        return false;
      }
      if (minDiff * 2 <= prevMinDiff * 3) {
        // Mismatch is not that much greater this period
        return false;
      }
    } else {
      if (minDiff <= prevMinDiff) {
        return false;
      }
    }
    return true;
  }

  /*private boolean previousPeriodBetter(int minDiff, int maxDiff) {
    if (minDiff == 0 || prevPeriod == 0) {
      return false;
    }
    if (maxDiff > minDiff * 3) {
      // Got a reasonable match this period.
      return false;
    }
    if (minDiff * 2 <= prevMinDiff * 3) {
      // Mismatch is not that much greater this period.
      return false;
    }
    return true;
  }*/

  private int findPitchPeriod(short samples[], int position, boolean preferNewPeriod) {
    int period, retPeriod;
    int skip = 1;

    int quality = 1;
    if (inputSampleRateHz > AMDF_FREQUENCY && quality == 0) {
      skip = inputSampleRateHz / AMDF_FREQUENCY;
    }
    if (channelCount == 1 && skip == 1) {
      period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
    } else {
      downSampleInput(samples, position, skip);
      period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip,
              maxPeriod / skip);
      if (skip != 1) {
        period *= skip;
        int minP = period - (skip << 2);
        int maxP = period + (skip << 2);
        if (minP < minPeriod) {
          minP = minPeriod;
        }
        if (maxP > maxPeriod) {
          maxP = maxPeriod;
        }
        if (channelCount == 1) {
          period = findPitchPeriodInRange(samples, position, minP, maxP);
        } else {
          downSampleInput(samples, position, 1);
          period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
        }
      }
    }
    if (previousPeriodBetter(minDiff, maxDiff, preferNewPeriod)) {
      retPeriod = prevPeriod;
    } else {
      retPeriod = period;
    }
    prevMinDiff = minDiff;
    prevPeriod = period;
    return retPeriod;
  }

  /*private int findPitchPeriod(short[] samples, int position) {
    // Find the pitch period. This is a critical step, and we may have to try multiple ways to get a
    // good answer. This version uses AMDF. To improve speed, we down sample by an integer factor
    // get in the 11 kHz range, and then do it again with a narrower frequency range without down
    // sampling.
    int period;
    int retPeriod;
    int skip = inputSampleRateHz > AMDF_FREQUENCY ? inputSampleRateHz / AMDF_FREQUENCY : 1;
    if (channelCount == 1 && skip == 1) {
      period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
    } else {
      downSampleInput(samples, position, skip);
      period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip, maxPeriod / skip);
      if (skip != 1) {
        period *= skip;
        int minP = period - (skip * 4);
        int maxP = period + (skip * 4);
        if (minP < minPeriod) {
          minP = minPeriod;
        }
        if (maxP > maxPeriod) {
          maxP = maxPeriod;
        }
        if (channelCount == 1) {
          period = findPitchPeriodInRange(samples, position, minP, maxP);
        } else {
          downSampleInput(samples, position, 1);
          period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
        }
      }
    }
    if (previousPeriodBetter(minDiff, maxDiff)) {
      retPeriod = prevPeriod;
    } else {
      retPeriod = period;
    }
    prevMinDiff = minDiff;
    prevPeriod = period;
    return retPeriod;
  }*/

  private void moveNewSamplesToPitchBuffer(int originalOutputFrameCount) {
    int frameCount = outputFrameCount - originalOutputFrameCount;
    pitchBuffer = ensureSpaceForAdditionalFrames(pitchBuffer, pitchFrameCount, frameCount);
    System.arraycopy(
        outputBuffer,
        originalOutputFrameCount * channelCount,
        pitchBuffer,
        pitchFrameCount * channelCount,
        frameCount * channelCount);
    outputFrameCount = originalOutputFrameCount;
    pitchFrameCount += frameCount;
  }

  private void removePitchFrames(int frameCount) {
    if (frameCount == 0) {
      return;
    }
    System.arraycopy(
        pitchBuffer,
        frameCount * channelCount,
        pitchBuffer,
        0,
        (pitchFrameCount - frameCount) * channelCount);
    pitchFrameCount -= frameCount;
  }

  private void adjustPitch(int originalNumOutputSamples) {
    int period, newPeriod, separation;
    int position = 0;

    if (outputFrameCount == originalNumOutputSamples) {
      return;
    }
    moveNewSamplesToPitchBuffer(originalNumOutputSamples);
    while (pitchFrameCount - position >= maxRequiredFrameCount) {
      period = findPitchPeriod(pitchBuffer, position, false);
      newPeriod = (int) (period / pitch);
      outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, newPeriod);
      if (pitch >= 1.0f) {
        overlapAdd(newPeriod, channelCount, outputBuffer, outputFrameCount, pitchBuffer,
                position, pitchBuffer, position + period - newPeriod);
      } else {
        separation = newPeriod - period;
        overlapAddWithSeparation(period, channelCount, separation, outputBuffer, outputFrameCount,
                pitchBuffer, position, pitchBuffer, position);
      }
      outputFrameCount += newPeriod;
      position += period;
    }
    removePitchFrames(position);
  }

  // Aproximate the sinc function times a Hann window from the sinc table.
  private int findSincCoefficient(int i, int ratio, int width) {
    int lobePoints = (SINC_TABLE_SIZE - 1) / SINC_FILTER_POINTS;
    int left = i * lobePoints + (ratio * lobePoints) / width;
    int right = left + 1;
    int position = i * lobePoints * width + ratio * lobePoints - left * width;
    int leftVal = sincTable[left];
    int rightVal = sincTable[right];

    return ((leftVal * (width - position) + rightVal * position) << 1) / width;
  }

  // Return 1 if value >= 0, else -1.  This represents the sign of value.
  private int getSign(int value) {
    return value >= 0 ? 1 : 0;
  }

  /*private short interpolate(short in[], int inPos, int oldSampleRate, int newSampleRate) {
    int i;
    int total = 0;
    int position = newRatePosition * oldSampleRate;
    int leftPosition = oldRatePosition * newSampleRate;
    int rightPosition = (oldRatePosition + 1) * newSampleRate;
    int ratio = rightPosition - position - 1;
    int width = rightPosition - leftPosition;
    int weight, value;
    int oldSign;
    int overflowCount = 0;

    for (i = 0; i < SINC_FILTER_POINTS; i++) {
      weight = findSincCoefficient(i, ratio, width);
      value = in[inPos + i * channelCount] * weight;
      oldSign = getSign(total);
      total += value;
      if (oldSign != getSign(total) && getSign(value) == oldSign) {
        overflowCount += oldSign;
      }
    }
    if (overflowCount > 0) {
      return Short.MAX_VALUE;
    } else if (overflowCount < 0) {
      return Short.MIN_VALUE;
    }
    return (short) (total >> 16);
  }*/

  private short interpolate(short[] in, int inPos, int oldSampleRate, int newSampleRate) {
    short left = in[inPos];
    short right = in[inPos + channelCount];
    int position = newRatePosition * oldSampleRate;
    int leftPosition = oldRatePosition * newSampleRate;
    int rightPosition = (oldRatePosition + 1) * newSampleRate;
    int ratio = rightPosition - position;
    int width = rightPosition - leftPosition;
    return (short) ((ratio * left + (width - ratio) * right) / width);
  }

  private void adjustRate(float rate, int originalNumOutputSamples) {
    int newSampleRate = (int) (inputSampleRateHz / rate);
    int oldSampleRate = inputSampleRateHz;
    int position;

    // Set these values to help with the integer math
    while (newSampleRate > (1 << 14) || oldSampleRate > (1 << 14)) {
      newSampleRate >>= 1;
      oldSampleRate >>= 1;
    }
    moveNewSamplesToPitchBuffer(originalNumOutputSamples);
    // Leave at least one pitch sample in the buffer
    for (position = 0; position < pitchFrameCount - 1; position++) {
      while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
        outputBuffer =
                ensureSpaceForAdditionalFrames(
                        outputBuffer, outputFrameCount, /* additionalFrameCount= */ 1);
        for (int i = 0; i < channelCount; i++) {
          outputBuffer[outputFrameCount * channelCount + i] = interpolate(pitchBuffer,
                  position * channelCount + i, oldSampleRate, newSampleRate);
        }
        newRatePosition++;
        outputFrameCount++;
      }
      oldRatePosition++;
      if (oldRatePosition == oldSampleRate) {
        oldRatePosition = 0;
        Assertions.checkState(newRatePosition == newSampleRate);
        newRatePosition = 0;
      }
    }
    removePitchFrames(position);
  }

  private int skipPitchPeriod(short[] samples, int position, float speed, int period) {
    // Skip over a pitch period, and copy period/speed samples to the output.
    int newFrameCount;
    if (speed >= 2.0f) {
      newFrameCount = (int) (period / (speed - 1.0f));
    } else {
      newFrameCount = period;
      remainingInputToCopyFrameCount = (int) (period * (2.0f - speed) / (speed - 1.0f));
    }
    outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, newFrameCount);
    overlapAdd(
        newFrameCount,
        channelCount,
        outputBuffer,
        outputFrameCount,
        samples,
        position,
        samples,
        position + period);
    outputFrameCount += newFrameCount;
    return newFrameCount;
  }

  private int insertPitchPeriod(short[] samples, int position, float speed, int period) {
    // Insert a pitch period, and determine how much input to copy directly.
    int newFrameCount;
    if (speed < 0.5f) {
      newFrameCount = (int) (period * speed / (1.0f - speed));
    } else {
      newFrameCount = period;
      remainingInputToCopyFrameCount = (int) (period * (2.0f * speed - 1.0f) / (1.0f - speed));
    }
    outputBuffer =
        ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, period + newFrameCount);
    System.arraycopy(
        samples,
        position * channelCount,
        outputBuffer,
        outputFrameCount * channelCount,
        period * channelCount);
    overlapAdd(
        newFrameCount,
        channelCount,
        outputBuffer,
        outputFrameCount + period,
        samples,
        position + period,
        samples,
        position);
    outputFrameCount += period + newFrameCount;
    return newFrameCount;
  }

  private void changeSpeed(float speed) {
    if (inputFrameCount < maxRequiredFrameCount) {
      return;
    }

    int numSamples = inputFrameCount;
    int position = 0, period, newSamples;

    do {
      if (remainingInputToCopyFrameCount > 0) {
        newSamples = copyInputToOutput(position);
        position += newSamples;
      } else {
        period = findPitchPeriod(inputBuffer, position, true);
        if (speed > 1.0) {
          newSamples = skipPitchPeriod(inputBuffer, position, speed, period);
          position += period + newSamples;
        } else {
          newSamples = insertPitchPeriod(inputBuffer, position, speed, period);
          position += newSamples;
        }
      }
    } while (position + maxRequiredFrameCount <= numSamples);
    removeProcessedInputFrames(position);
  }

  private void processStreamInput() {
    int originalNumOutputSamples = outputFrameCount;
    float s = speed / pitch;
    float r = rate;

    boolean useChordPitch = false;
    if (!useChordPitch) {
      r *= pitch;
    }
    if (s > 1.00001 || s < 0.99999) {
      changeSpeed(s);
    } else {
      copyToOutput(inputBuffer, 0, inputFrameCount);
      inputFrameCount = 0;
    }
    if (useChordPitch) {
      if (pitch != 1.0f) {
        adjustPitch(originalNumOutputSamples);
      }
    } else if (r != 1.0f) {
      adjustRate(r, originalNumOutputSamples);
    }
  }

  private static void overlapAdd(
      int frameCount,
      int channelCount,
      short[] out,
      int outPosition,
      short[] rampDown,
      int rampDownPosition,
      short[] rampUp,
      int rampUpPosition) {
    for (int i = 0; i < channelCount; i++) {
      int o = outPosition * channelCount + i;
      int u = rampUpPosition * channelCount + i;
      int d = rampDownPosition * channelCount + i;
      for (int t = 0; t < frameCount; t++) {
        out[o] = (short) ((rampDown[d] * (frameCount - t) + rampUp[u] * t) / frameCount);
        o += channelCount;
        d += channelCount;
        u += channelCount;
      }
    }
  }

  private static void overlapAddWithSeparation(
          int numSamples,
          int numChannels,
          int separation,
          short out[],
          int outPos,
          short rampDown[],
          int rampDownPos,
          short rampUp[],
          int rampUpPos)
  {
    for(int i = 0; i < numChannels; i++) {
      int o = outPos*numChannels + i;
      int u = rampUpPos*numChannels + i;
      int d = rampDownPos*numChannels + i;
      for(int t = 0; t < numSamples + separation; t++) {
        if(t < separation) {
          out[o] = (short)(rampDown[d]*(numSamples - t)/numSamples);
          d += numChannels;
        } else if(t < numSamples) {
          out[o] = (short)((rampDown[d]*(numSamples - t) + rampUp[u]*(t - separation))/numSamples);
          d += numChannels;
          u += numChannels;
        } else {
          out[o] = (short)(rampUp[u]*(t - separation)/numSamples);
          u += numChannels;
        }
        o += numChannels;
      }
    }
  }
}
