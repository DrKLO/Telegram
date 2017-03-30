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
package org.telegram.messenger.exoplayer2.extractor.ts;

import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.TimestampAdjuster;
import org.telegram.messenger.exoplayer2.util.ParsableBitArray;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * Reads section data packets and feeds the whole sections to a given {@link SectionPayloadReader}.
 */
public final class SectionReader implements TsPayloadReader {

  private static final int SECTION_HEADER_LENGTH = 3;

  private final ParsableByteArray sectionData;
  private final ParsableBitArray headerScratch;
  private final SectionPayloadReader reader;
  private int sectionLength;
  private int sectionBytesRead;

  public SectionReader(SectionPayloadReader reader) {
    this.reader = reader;
    sectionData = new ParsableByteArray();
    headerScratch = new ParsableBitArray(new byte[SECTION_HEADER_LENGTH]);
  }

  @Override
  public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
      TrackIdGenerator idGenerator) {
    reader.init(timestampAdjuster, extractorOutput, idGenerator);
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator) {
    // Skip pointer.
    if (payloadUnitStartIndicator) {
      int pointerField = data.readUnsignedByte();
      data.skipBytes(pointerField);

      // Note: see ISO/IEC 13818-1, section 2.4.4.3 for detailed information on the format of
      // the header.
      data.readBytes(headerScratch, SECTION_HEADER_LENGTH);
      data.setPosition(data.getPosition() - SECTION_HEADER_LENGTH);
      headerScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), 0 (1), reserved (2)
      sectionLength = headerScratch.readBits(12) + SECTION_HEADER_LENGTH;
      sectionBytesRead = 0;

      sectionData.reset(sectionLength);
    }

    int bytesToRead = Math.min(data.bytesLeft(), sectionLength - sectionBytesRead);
    data.readBytes(sectionData.data, sectionBytesRead, bytesToRead);
    sectionBytesRead += bytesToRead;
    if (sectionBytesRead < sectionLength) {
      // Not yet fully read.
      return;
    }

    if (Util.crc(sectionData.data, 0, sectionLength, 0xFFFFFFFF) != 0) {
      // CRC Invalid. The section gets discarded.
      return;
    }
    sectionData.setLimit(sectionData.limit() - 4); // Exclude the CRC_32 field.
    reader.consume(sectionData);
  }

}
