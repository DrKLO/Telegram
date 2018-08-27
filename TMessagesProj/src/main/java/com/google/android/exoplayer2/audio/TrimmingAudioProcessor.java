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
import com.google.android.exoplayer2.C.Encoding;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Audio processor for trimming samples from the start/end of data. */
/* package */ final class TrimmingAudioProcessor implements AudioProcessor {

  private boolean isActive;
  private int trimStartFrames;
  private int trimEndFrames;
  private int channelCount;
  private int sampleRateHz;

  private int pendingTrimStartBytes;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private byte[] endBuffer;
  private int endBufferSize;
  private boolean inputEnded;

  /** Creates a new audio processor for trimming samples from the start/end of data. */
  public TrimmingAudioProcessor() {
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    endBuffer = new byte[0];
  }

  /**
   * Sets the number of audio frames to trim from the start and end of audio passed to this
   * processor. After calling this method, call {@link #configure(int, int, int)} to apply the new
   * trimming frame counts.
   *
   * @param trimStartFrames The number of audio frames to trim from the start of audio.
   * @param trimEndFrames The number of audio frames to trim from the end of audio.
   * @see AudioSink#configure(int, int, int, int, int[], int, int)
   */
  public void setTrimFrameCount(int trimStartFrames, int trimEndFrames) {
    this.trimStartFrames = trimStartFrames;
    this.trimEndFrames = trimEndFrames;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @Encoding int encoding)
      throws UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    this.channelCount = channelCount;
    this.sampleRateHz = sampleRateHz;
    endBuffer = new byte[trimEndFrames * channelCount * 2];
    endBufferSize = 0;
    pendingTrimStartBytes = trimStartFrames * channelCount * 2;
    boolean wasActive = isActive;
    isActive = trimStartFrames != 0 || trimEndFrames != 0;
    return wasActive != isActive;
  }

  @Override
  public boolean isActive() {
    return isActive;
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
  public int getOutputSampleRateHz() {
    return sampleRateHz;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int remaining = limit - position;

    // Trim any pending start bytes from the input buffer.
    int trimBytes = Math.min(remaining, pendingTrimStartBytes);
    pendingTrimStartBytes -= trimBytes;
    inputBuffer.position(position + trimBytes);
    if (pendingTrimStartBytes > 0) {
      // Nothing to output yet.
      return;
    }
    remaining -= trimBytes;

    // endBuffer must be kept as full as possible, so that we trim the right amount of media if we
    // don't receive any more input. After taking into account the number of bytes needed to keep
    // endBuffer as full as possible, the output should be any surplus bytes currently in endBuffer
    // followed by any surplus bytes in the new inputBuffer.
    int remainingBytesToOutput = endBufferSize + remaining - endBuffer.length;
    if (buffer.capacity() < remainingBytesToOutput) {
      buffer = ByteBuffer.allocateDirect(remainingBytesToOutput).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }

    // Output from endBuffer.
    int endBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, endBufferSize);
    buffer.put(endBuffer, 0, endBufferBytesToOutput);
    remainingBytesToOutput -= endBufferBytesToOutput;

    // Output from inputBuffer, restoring its limit afterwards.
    int inputBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, remaining);
    inputBuffer.limit(inputBuffer.position() + inputBufferBytesToOutput);
    buffer.put(inputBuffer);
    inputBuffer.limit(limit);
    remaining -= inputBufferBytesToOutput;

    // Compact endBuffer, then repopulate it using the new input.
    endBufferSize -= endBufferBytesToOutput;
    System.arraycopy(endBuffer, endBufferBytesToOutput, endBuffer, 0, endBufferSize);
    inputBuffer.get(endBuffer, endBufferSize, remaining);
    endBufferSize += remaining;

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
    // It's no longer necessary to trim any media from the start, but it is necessary to clear the
    // end buffer and refill it.
    pendingTrimStartBytes = 0;
    endBufferSize = 0;
  }

  @Override
  public void reset() {
    flush();
    buffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    endBuffer = new byte[0];
  }

}
