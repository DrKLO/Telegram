/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.extractor.wav;

import org.telegram.messenger.exoplayer2.C;

/** Header for a WAV file. */
/*package*/ final class WavHeader {

  /** Number of audio chanels. */
  private final int numChannels;
  /** Sample rate in Hertz. */
  private final int sampleRateHz;
  /** Average bytes per second for the sample data. */
  private final int averageBytesPerSecond;
  /** Alignment for frames of audio data; should equal {@code numChannels * bitsPerSample / 8}. */
  private final int blockAlignment;
  /** Bits per sample for the audio data. */
  private final int bitsPerSample;
  /** The PCM encoding */
  @C.PcmEncoding
  private final int encoding;

  /** Offset to the start of sample data. */
  private long dataStartPosition;
  /** Total size in bytes of the sample data. */
  private long dataSize;

  public WavHeader(int numChannels, int sampleRateHz, int averageBytesPerSecond, int blockAlignment,
      int bitsPerSample, @C.PcmEncoding int encoding) {
    this.numChannels = numChannels;
    this.sampleRateHz = sampleRateHz;
    this.averageBytesPerSecond = averageBytesPerSecond;
    this.blockAlignment = blockAlignment;
    this.bitsPerSample = bitsPerSample;
    this.encoding = encoding;
  }

  /** Returns the duration in microseconds of this WAV. */
  public long getDurationUs() {
    long numFrames = dataSize / blockAlignment;
    return (numFrames * C.MICROS_PER_SECOND) / sampleRateHz;
  }

  /** Returns the bytes per frame of this WAV. */
  public int getBytesPerFrame() {
    return blockAlignment;
  }

  /** Returns the bitrate of this WAV. */
  public int getBitrate() {
    return sampleRateHz * bitsPerSample * numChannels;
  }

  /** Returns the sample rate in Hertz of this WAV. */
  public int getSampleRateHz() {
    return sampleRateHz;
  }

  /** Returns the number of audio channels in this WAV. */
  public int getNumChannels() {
    return numChannels;
  }

  /** Returns the position in bytes in this WAV for the given time in microseconds. */
  public long getPosition(long timeUs) {
    long unroundedPosition = (timeUs * averageBytesPerSecond) / C.MICROS_PER_SECOND;
    // Round down to nearest frame.
    long position = (unroundedPosition / blockAlignment) * blockAlignment;
    return Math.min(position, dataSize - blockAlignment) + dataStartPosition;
  }

  /** Returns the time in microseconds for the given position in bytes in this WAV. */
  public long getTimeUs(long position) {
    return position * C.MICROS_PER_SECOND / averageBytesPerSecond;
  }

  /** Returns true if the data start position and size have been set. */
  public boolean hasDataBounds() {
    return dataStartPosition != 0 && dataSize != 0;
  }

  /** Sets the start position and size in bytes of sample data in this WAV. */
  public void setDataBounds(long dataStartPosition, long dataSize) {
    this.dataStartPosition = dataStartPosition;
    this.dataSize = dataSize;
  }

  /** Returns the PCM encoding. **/
  @C.PcmEncoding
  public int getEncoding() {
    return encoding;
  }

}
