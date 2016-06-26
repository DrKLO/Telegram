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
package org.telegram.messenger.exoplayer.util;

import org.telegram.messenger.exoplayer.extractor.SeekMap;

/**
 * FLAC seek table class
 */
public final class FlacSeekTable {

  private static final int METADATA_LENGTH_OFFSET = 1;
  private static final int SEEK_POINT_SIZE = 18;

  private final long[] sampleNumbers;
  private final long[] offsets;

  /**
   * Parses a FLAC file seek table metadata structure and creates a FlacSeekTable instance.
   *
   * @param data A ParsableByteArray including whole seek table metadata block. Its position should
   *     be set to the beginning of the block.
   * @return A FlacSeekTable instance keeping seek table data
   * @see <a href="https://xiph.org/flac/format.html#metadata_block_seektable">FLAC format
   *     METADATA_BLOCK_SEEKTABLE</a>
   */
  public static FlacSeekTable parseSeekTable(ParsableByteArray data) {
    data.skipBytes(METADATA_LENGTH_OFFSET);
    int length = data.readUnsignedInt24();
    int numberOfSeekPoints = length / SEEK_POINT_SIZE;

    long[] sampleNumbers = new long[numberOfSeekPoints];
    long[] offsets = new long[numberOfSeekPoints];

    for (int i = 0; i < numberOfSeekPoints; i++) {
      sampleNumbers[i] = data.readLong();
      offsets[i] = data.readLong();
      data.skipBytes(2); // Skip "Number of samples in the target frame."
    }

    return new FlacSeekTable(sampleNumbers, offsets);
  }

  private FlacSeekTable(long[] sampleNumbers, long[] offsets) {
    this.sampleNumbers = sampleNumbers;
    this.offsets = offsets;
  }

  /**
   * Creates a {@link SeekMap} wrapper for this FlacSeekTable.
   *
   * @param firstFrameOffset Offset of the first FLAC frame
   * @param sampleRate Sample rate of the FLAC file.
   * @return A SeekMap wrapper for this FlacSeekTable.
   */
  public SeekMap createSeekMap(final long firstFrameOffset, final long sampleRate) {
    return new SeekMap() {
      @Override
      public boolean isSeekable() {
        return true;
      }

      @Override
      public long getPosition(long timeUs) {
        long sample = (timeUs * sampleRate) / 1000000L;

        int index = Util.binarySearchFloor(sampleNumbers, sample, true, true);
        return firstFrameOffset + offsets[index];
      }
    };
  }
}
