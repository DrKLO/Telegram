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
package org.telegram.messenger.exoplayer2.util;

/**
 * Holder for FLAC stream info.
 */
public final class FlacStreamInfo {

  public final int minBlockSize;
  public final int maxBlockSize;
  public final int minFrameSize;
  public final int maxFrameSize;
  public final int sampleRate;
  public final int channels;
  public final int bitsPerSample;
  public final long totalSamples;

  /**
   * Constructs a FlacStreamInfo parsing the given binary FLAC stream info metadata structure.
   *
   * @param data An array holding FLAC stream info metadata structure
   * @param offset Offset of the structure in the array
   * @see <a href="https://xiph.org/flac/format.html#metadata_block_streaminfo">FLAC format
   *     METADATA_BLOCK_STREAMINFO</a>
   */
  public FlacStreamInfo(byte[] data, int offset) {
    ParsableBitArray scratch = new ParsableBitArray(data);
    scratch.setPosition(offset * 8);
    this.minBlockSize = scratch.readBits(16);
    this.maxBlockSize = scratch.readBits(16);
    this.minFrameSize = scratch.readBits(24);
    this.maxFrameSize = scratch.readBits(24);
    this.sampleRate = scratch.readBits(20);
    this.channels = scratch.readBits(3) + 1;
    this.bitsPerSample = scratch.readBits(5) + 1;
    this.totalSamples = ((scratch.readBits(4) & 0xFL) << 32)
        | (scratch.readBits(32) & 0xFFFFFFFFL);
    // Remaining 16 bytes is md5 value
  }

  public FlacStreamInfo(int minBlockSize, int maxBlockSize, int minFrameSize, int maxFrameSize,
      int sampleRate, int channels, int bitsPerSample, long totalSamples) {
    this.minBlockSize = minBlockSize;
    this.maxBlockSize = maxBlockSize;
    this.minFrameSize = minFrameSize;
    this.maxFrameSize = maxFrameSize;
    this.sampleRate = sampleRate;
    this.channels = channels;
    this.bitsPerSample = bitsPerSample;
    this.totalSamples = totalSamples;
  }

  public int maxDecodedFrameSize() {
    return maxBlockSize * channels * 2;
  }

  public int bitRate() {
    return bitsPerSample * sampleRate;
  }

  public long durationUs() {
    return (totalSamples * 1000000L) / sampleRate;
  }

}
