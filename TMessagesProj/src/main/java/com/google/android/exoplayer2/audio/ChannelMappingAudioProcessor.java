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
import com.google.android.exoplayer2.C.Encoding;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Assertions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * An {@link AudioProcessor} that applies a mapping from input channels onto specified output
 * channels. This can be used to reorder, duplicate or discard channels.
 */
/* package */ final class ChannelMappingAudioProcessor implements AudioProcessor {

  private int channelCount;
  private int sampleRateHz;
  private @Nullable int[] pendingOutputChannels;

  private boolean active;
  private @Nullable int[] outputChannels;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  /** Creates a new processor that applies a channel mapping. */
  public ChannelMappingAudioProcessor() {
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
  }

  /**
   * Resets the channel mapping. After calling this method, call {@link #configure(int, int, int)}
   * to start using the new channel map.
   *
   * @param outputChannels The mapping from input to output channel indices, or {@code null} to
   *     leave the input unchanged.
   * @see AudioSink#configure(int, int, int, int, int[], int, int)
   */
  public void setChannelMap(@Nullable int[] outputChannels) {
    pendingOutputChannels = outputChannels;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @Encoding int encoding)
      throws UnhandledFormatException {
    boolean outputChannelsChanged = !Arrays.equals(pendingOutputChannels, outputChannels);
    outputChannels = pendingOutputChannels;
    if (outputChannels == null) {
      active = false;
      return outputChannelsChanged;
    }
    if (encoding != C.ENCODING_PCM_16BIT) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (!outputChannelsChanged && this.sampleRateHz == sampleRateHz
        && this.channelCount == channelCount) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;

    active = channelCount != outputChannels.length;
    for (int i = 0; i < outputChannels.length; i++) {
      int channelIndex = outputChannels[i];
      if (channelIndex >= channelCount) {
        throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
      }
      active |= (channelIndex != i);
    }
    return true;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public int getOutputChannelCount() {
    return outputChannels == null ? channelCount : outputChannels.length;
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
    Assertions.checkState(outputChannels != null);
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int frameCount = (limit - position) / (2 * channelCount);
    int outputSize = frameCount * outputChannels.length * 2;
    if (buffer.capacity() < outputSize) {
      buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }
    while (position < limit) {
      for (int channelIndex : outputChannels) {
        buffer.putShort(inputBuffer.getShort(position + 2 * channelIndex));
      }
      position += channelCount * 2;
    }
    inputBuffer.position(limit);
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
    buffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    outputChannels = null;
    pendingOutputChannels = null;
    active = false;
  }

}
