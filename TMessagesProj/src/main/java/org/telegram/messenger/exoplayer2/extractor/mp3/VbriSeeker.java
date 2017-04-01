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
package org.telegram.messenger.exoplayer2.extractor.mp3;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.extractor.MpegAudioHeader;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * MP3 seeker that uses metadata from a VBRI header.
 */
/* package */ final class VbriSeeker implements Mp3Extractor.Seeker {

  /**
   * Returns a {@link VbriSeeker} for seeking in the stream, if required information is present.
   * Returns {@code null} if not. On returning, {@code frame}'s position is not specified so the
   * caller should reset it.
   *
   * @param mpegAudioHeader The MPEG audio header associated with the frame.
   * @param frame The data in this audio frame, with its position set to immediately after the
   *     'VBRI' tag.
   * @param position The position (byte offset) of the start of this frame in the stream.
   * @param inputLength The length of the stream in bytes.
   * @return A {@link VbriSeeker} for seeking in the stream, or {@code null} if the required
   *     information is not present.
   */
  public static VbriSeeker create(MpegAudioHeader mpegAudioHeader, ParsableByteArray frame,
      long position, long inputLength) {
    frame.skipBytes(10);
    int numFrames = frame.readInt();
    if (numFrames <= 0) {
      return null;
    }
    int sampleRate = mpegAudioHeader.sampleRate;
    long durationUs = Util.scaleLargeTimestamp(numFrames,
        C.MICROS_PER_SECOND * (sampleRate >= 32000 ? 1152 : 576), sampleRate);
    int entryCount = frame.readUnsignedShort();
    int scale = frame.readUnsignedShort();
    int entrySize = frame.readUnsignedShort();
    frame.skipBytes(2);

    // Skip the frame containing the VBRI header.
    position += mpegAudioHeader.frameSize;

    // Read table of contents entries.
    long[] timesUs = new long[entryCount + 1];
    long[] positions = new long[entryCount + 1];
    timesUs[0] = 0L;
    positions[0] = position;
    for (int index = 1; index < timesUs.length; index++) {
      int segmentSize;
      switch (entrySize) {
        case 1:
          segmentSize = frame.readUnsignedByte();
          break;
        case 2:
          segmentSize = frame.readUnsignedShort();
          break;
        case 3:
          segmentSize = frame.readUnsignedInt24();
          break;
        case 4:
          segmentSize = frame.readUnsignedIntToInt();
          break;
        default:
          return null;
      }
      position += segmentSize * scale;
      timesUs[index] = index * durationUs / entryCount;
      positions[index] =
          inputLength == C.LENGTH_UNSET ? position : Math.min(inputLength, position);
    }
    return new VbriSeeker(timesUs, positions, durationUs);
  }

  private final long[] timesUs;
  private final long[] positions;
  private final long durationUs;

  private VbriSeeker(long[] timesUs, long[] positions, long durationUs) {
    this.timesUs = timesUs;
    this.positions = positions;
    this.durationUs = durationUs;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getPosition(long timeUs) {
    return positions[Util.binarySearchFloor(timesUs, timeUs, true, true)];
  }

  @Override
  public long getTimeUs(long position) {
    return timesUs[Util.binarySearchFloor(positions, position, true, true)];
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

}
