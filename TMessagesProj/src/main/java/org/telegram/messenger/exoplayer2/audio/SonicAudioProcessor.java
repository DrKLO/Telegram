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
import org.telegram.messenger.exoplayer2.C.Encoding;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * An {@link AudioProcessor} that uses the Sonic library to modify the speed/pitch of audio.
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
   * The threshold below which the difference between two pitch/speed factors is negligible.
   */
  private static final float CLOSE_THRESHOLD = 0.01f;

  private int channelCount;
  private int sampleRateHz;

  private Sonic sonic;
  private float speed;
  private float pitch;

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
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
  }

  /**
   * Sets the playback speed. The new speed will take effect after a call to {@link #flush()}.
   *
   * @param speed The requested new playback speed.
   * @return The actual new playback speed.
   */
  public float setSpeed(float speed) {
    this.speed = Util.constrainValue(speed, MINIMUM_SPEED, MAXIMUM_SPEED);
    return this.speed;
  }

  /**
   * Sets the playback pitch. The new pitch will take effect after a call to {@link #flush()}.
   *
   * @param pitch The requested new pitch.
   * @return The actual new pitch.
   */
  public float setPitch(float pitch) {
    this.pitch = Util.constrainValue(pitch, MINIMUM_PITCH, MAXIMUM_PITCH);
    return pitch;
  }

  /**
   * Returns the number of bytes of input queued since the last call to {@link #flush()}.
   */
  public long getInputByteCount() {
    return inputBytes;
  }

  /**
   * Returns the number of bytes of output dequeued since the last call to {@link #flush()}.
   */
  public long getOutputByteCount() {
    return outputBytes;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @Encoding int encoding)
      throws UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    return true;
  }

  @Override
  public boolean isActive() {
    return Math.abs(speed - 1f) >= CLOSE_THRESHOLD || Math.abs(pitch - 1f) >= CLOSE_THRESHOLD;
  }

  @Override
  public int getOutputChannelCount() {
    return channelCount;
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_16BIT;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    if (inputBuffer.hasRemaining()) {
      ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
      int inputSize = inputBuffer.remaining();
      inputBytes += inputSize;
      sonic.queueInput(shortBuffer);
      inputBuffer.position(inputBuffer.position() + inputSize);
    }
    int outputSize = sonic.getSamplesAvailable() * channelCount * 2;
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
    sonic.queueEndOfStream();
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
    return inputEnded && (sonic == null || sonic.getSamplesAvailable() == 0);
  }

  @Override
  public void flush() {
    sonic = new Sonic(sampleRateHz, channelCount);
    sonic.setSpeed(speed);
    sonic.setPitch(pitch);
    outputBuffer = EMPTY_BUFFER;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

  @Override
  public void reset() {
    sonic = null;
    buffer = EMPTY_BUFFER;
    shortBuffer = buffer.asShortBuffer();
    outputBuffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    inputBytes = 0;
    outputBytes = 0;
    inputEnded = false;
  }

}
