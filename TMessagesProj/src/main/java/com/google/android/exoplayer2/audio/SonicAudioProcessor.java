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
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * An {@link AudioProcessor} that uses the Sonic library to modify audio speed/pitch/sample rate.
 */
public final class SonicAudioProcessor implements AudioProcessor {

  /**
   * The maximum allowed playback speed in {@link #setSpeed(float)}.
   */
  public static final float MAXIMUM_SPEED = 8.0f;
  /**
   * The minimum allowed playback speed in {@link #setSpeed(float)}.
   */
  public static final float MINIMUM_SPEED = 0.1f;
  /**
   * The maximum allowed pitch in {@link #setPitch(float)}.
   */
  public static final float MAXIMUM_PITCH = 8.0f;
  /**
   * The minimum allowed pitch in {@link #setPitch(float)}.
   */
  public static final float MINIMUM_PITCH = 0.1f;
  /**
   * Indicates that the output sample rate should be the same as the input.
   */
  public static final int SAMPLE_RATE_NO_CHANGE = -1;

  /**
   * The threshold below which the difference between two pitch/speed factors is negligible.
   */
  private static final float CLOSE_THRESHOLD = 0.01f;

  /**
   * The minimum number of output bytes at which the speedup is calculated using the input/output
   * byte counts, rather than using the current playback parameters speed.
   */
  private static final int MIN_BYTES_FOR_SPEEDUP_CALCULATION = 1024;

  private int pendingOutputSampleRate;
  private float speed;
  private float pitch;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private AudioFormat inputAudioFormat;
  private AudioFormat outputAudioFormat;

  private boolean pendingSonicRecreation;
  @Nullable private Sonic sonic;
  private ByteBuffer buffer;
  private ShortBuffer shortBuffer;
  private ByteBuffer outputBuffer;
  private long inputBytes;
  private long outputBytes;
  private boolean inputEnded;

  /**
   * Creates a new Sonic audio processor.
   */
  public SonicAudioProcessor() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
  }

  /**
   * Sets the playback speed. This method may only be called after draining data through the
   * processor. The value returned by {@link #isActive()} may change, and the processor must be
   * {@link #flush() flushed} before queueing more data.
   *
   * @param speed The requested new playback speed.
   * @return The actual new playback speed.
   */
  public float setSpeed(float speed) {
    speed = Util.constrainValue(speed, MINIMUM_SPEED, MAXIMUM_SPEED);
    if (this.speed != speed) {
      this.speed = speed;
      pendingSonicRecreation = true;
    }
    return speed;
  }

  /**
   * Sets the playback pitch. This method may only be called after draining data through the
   * processor. The value returned by {@link #isActive()} may change, and the processor must be
   * {@link #flush() flushed} before queueing more data.
   *
   * @param pitch The requested new pitch.
   * @return The actual new pitch.
   */
  public float setPitch(float pitch) {
    pitch = Util.constrainValue(pitch, MINIMUM_PITCH, MAXIMUM_PITCH);
    if (this.pitch != pitch) {
      this.pitch = pitch;
      pendingSonicRecreation = true;
    }
    return pitch;
  }

  /**
   * Sets the sample rate for output audio, in Hertz. Pass {@link #SAMPLE_RATE_NO_CHANGE} to output
   * audio at the same sample rate as the input. After calling this method, call {@link
   * #configure(AudioFormat)} to configure the processor with the new sample rate.
   *
   * @param sampleRateHz The sample rate for output audio, in Hertz.
   * @see #configure(AudioFormat)
   */
  public void setOutputSampleRateHz(int sampleRateHz) {
    pendingOutputSampleRate = sampleRateHz;
  }

  /**
   * Returns the specified duration scaled to take into account the speedup factor of this instance,
   * in the same units as {@code duration}.
   *
   * @param duration The duration to scale taking into account speedup.
   * @return The specified duration scaled to take into account speedup, in the same units as
   *     {@code duration}.
   */
  public long scaleDurationForSpeedup(long duration) {
    if (outputBytes >= MIN_BYTES_FOR_SPEEDUP_CALCULATION) {
      return outputAudioFormat.sampleRate == inputAudioFormat.sampleRate
          ? Util.scaleLargeTimestamp(duration, inputBytes, outputBytes)
          : Util.scaleLargeTimestamp(
              duration,
              inputBytes * outputAudioFormat.sampleRate,
              outputBytes * inputAudioFormat.sampleRate);
    } else {
      return (long) ((double) speed * duration);
    }
  }

  @Override
  public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
    int outputSampleRateHz =
        pendingOutputSampleRate == SAMPLE_RATE_NO_CHANGE
            ? inputAudioFormat.sampleRate
            : pendingOutputSampleRate;
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat =
        new AudioFormat(outputSampleRateHz, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT);
    pendingSonicRecreation = true;
    return pendingOutputAudioFormat;
  }

  @Override
  public boolean isActive() {
    return pendingOutputAudioFormat.sampleRate != Format.NO_VALUE
        && (Math.abs(speed - 1f) >= CLOSE_THRESHOLD
            || Math.abs(pitch - 1f) >= CLOSE_THRESHOLD
            || pendingOutputAudioFormat.sampleRate != pendingInputAudioFormat.sampleRate);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    Sonic sonic = Assertions.checkNotNull(this.sonic);
    if (inputBuffer.hasRemaining()) {
      ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
      int inputSize = inputBuffer.remaining();
      inputBytes += inputSize;
      sonic.queueInput(shortBuffer);
      inputBuffer.position(inputBuffer.position() + inputSize);
    }
    int outputSize = sonic.getOutputSize();
    if (outputSize > 0) {
      if (buffer.capacity() < outputSize) {
        buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
        shortBuffer = buffer.asShortBuffer();
      } else {
        buffer.clear();
        shortBuffer.clear();
      }
      sonic.getOutput(shortBuffer);
      outputBytes += outputSize;
      buffer.limit(outputSize);
      outputBuffer = buffer;
    }
  }

  @Override
  public void queueEndOfStream() {
    if (sonic != null) {
      sonic.queueEndOfStream();
    }
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @Override
  public boolean isEnded() {
    return inputEnded && (sonic == null || sonic.getOutputSize() == 0);
  }

  @Override
  public void flush() {
    if (isActive()) {
      inputAudioFormat = pendingInputAudioFormat;
      outputAudioFormat = pendingOutputAudioFormat;
      if (pendingSonicRecreation) {
        sonic =
            new Sonic(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                speed,
                pitch,
                outputAudioFormat.sampleRate);
      } else if (sonic != null) {
        sonic.flush();
      }
    }
    outputBuffer = EMPTY_BUFFER;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

  @Override
  public void reset() {
    speed = 1f;
    pitch = 1f;
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    pendingOutputSampleRate = SAMPLE_RATE_NO_CHANGE;
    pendingSonicRecreation = false;
    sonic = null;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

}
