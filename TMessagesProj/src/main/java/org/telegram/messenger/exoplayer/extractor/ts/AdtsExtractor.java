/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor.ts;

import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer.extractor.PositionHolder;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.util.ParsableBitArray;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;

/**
 * Facilitates the extraction of AAC samples from elementary audio files formatted as AAC with ADTS
 * headers.
 */
public final class AdtsExtractor implements Extractor {

  private static final int MAX_PACKET_SIZE = 200;
  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
  /**
   * The maximum number of bytes to search when sniffing, excluding the header, before giving up.
   * Frame sizes are represented by 13-bit fields, so expect a valid frame in the first 8192 bytes.
   */
  private static final int MAX_SNIFF_BYTES = 8 * 1024;

  private final long firstSampleTimestampUs;
  private final ParsableByteArray packetBuffer;

  // Accessed only by the loading thread.
  private AdtsReader adtsReader;
  private boolean startedPacket;

  public AdtsExtractor() {
    this(0);
  }

  public AdtsExtractor(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    packetBuffer = new ParsableByteArray(MAX_PACKET_SIZE);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Skip any ID3 headers.
    ParsableByteArray scratch = new ParsableByteArray(10);
    ParsableBitArray scratchBits = new ParsableBitArray(scratch.data);
    int startPosition = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 10);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }
      int length = (scratch.data[6] & 0x7F) << 21 | ((scratch.data[7] & 0x7F) << 14)
          | ((scratch.data[8] & 0x7F) << 7) | (scratch.data[9] & 0x7F);
      startPosition += 10 + length;
      input.advancePeekPosition(length);
    }
    input.resetPeekPosition();
    input.advancePeekPosition(startPosition);

    // Try to find four or more consecutive AAC audio frames, exceeding the MPEG TS packet size.
    int headerPosition = startPosition;
    int validFramesSize = 0;
    int validFramesCount = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 2);
      scratch.setPosition(0);
      int syncBytes = scratch.readUnsignedShort();
      if ((syncBytes & 0xFFF6) != 0xFFF0) {
        validFramesCount = 0;
        validFramesSize = 0;
        input.resetPeekPosition();
        if (++headerPosition - startPosition >= MAX_SNIFF_BYTES) {
          return false;
        }
        input.advancePeekPosition(headerPosition);
      } else {
        if (++validFramesCount >= 4 && validFramesSize > 188) {
          return true;
        }

        // Skip the frame.
        input.peekFully(scratch.data, 0, 4);
        scratchBits.setPosition(14);
        int frameSize = scratchBits.readBits(13);
        // Either the stream is malformed OR we're not parsing an ADTS stream.
        if (frameSize <= 6) {
          return false;
        }
        input.advancePeekPosition(frameSize - 6);
        validFramesSize += frameSize;
      }
    }
  }

  @Override
  public void init(ExtractorOutput output) {
    adtsReader = new AdtsReader(output.track(0), output.track(1));
    output.endTracks();
    output.seekMap(SeekMap.UNSEEKABLE);
  }

  @Override
  public void seek() {
    startedPacket = false;
    adtsReader.seek();
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    int bytesRead = input.read(packetBuffer.data, 0, MAX_PACKET_SIZE);
    if (bytesRead == -1) {
      return RESULT_END_OF_INPUT;
    }

    // Feed whatever data we have to the reader, regardless of whether the read finished or not.
    packetBuffer.setPosition(0);
    packetBuffer.setLimit(bytesRead);

    // TODO: Make it possible for adtsReader to consume the dataSource directly, so that it becomes
    // unnecessary to copy the data through packetBuffer.
    if (!startedPacket) {
      // Pass data to the reader as though it's contained within a single infinitely long packet.
      adtsReader.packetStarted(firstSampleTimestampUs, true);
      startedPacket = true;
    }
    adtsReader.consume(packetBuffer);
    return RESULT_CONTINUE;
  }

}
