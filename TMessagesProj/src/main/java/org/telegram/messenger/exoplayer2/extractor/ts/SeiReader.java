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

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.text.cea.Cea608Decoder;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;

/**
 * Consumes SEI buffers, outputting contained CEA-608 messages to a {@link TrackOutput}.
 */
/* package */ final class SeiReader {

  private final TrackOutput output;

  public SeiReader(TrackOutput output) {
    this.output = output;
    output.format(Format.createTextSampleFormat(null, MimeTypes.APPLICATION_CEA608, null,
        Format.NO_VALUE, 0, null, null));
  }

  public void consume(long pesTimeUs, ParsableByteArray seiBuffer) {
    int b;
    while (seiBuffer.bytesLeft() > 1 /* last byte will be rbsp_trailing_bits */) {
      // Parse payload type.
      int payloadType = 0;
      do {
        b = seiBuffer.readUnsignedByte();
        payloadType += b;
      } while (b == 0xFF);
      // Parse payload size.
      int payloadSize = 0;
      do {
        b = seiBuffer.readUnsignedByte();
        payloadSize += b;
      } while (b == 0xFF);
      // Process the payload.
      if (Cea608Decoder.isSeiMessageCea608(payloadType, payloadSize, seiBuffer)) {
        // Ignore country_code (1) + provider_code (2) + user_identifier (4)
        // + user_data_type_code (1).
        seiBuffer.skipBytes(8);
        // Ignore first three bits: reserved (1) + process_cc_data_flag (1) + zero_bit (1).
        int ccCount = seiBuffer.readUnsignedByte() & 0x1F;
        seiBuffer.skipBytes(1);
        int sampleBytes = 0;
        for (int i = 0; i < ccCount; i++) {
          int ccValidityAndType = seiBuffer.peekUnsignedByte() & 0x07;
          // Check that validity == 1 and type == 0 (i.e. NTSC_CC_FIELD_1).
          if (ccValidityAndType != 0x04) {
            seiBuffer.skipBytes(3);
          } else {
            sampleBytes += 3;
            output.sampleData(seiBuffer, 3);
          }
        }
        output.sampleMetadata(pesTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytes, 0, null);
        // Ignore trailing information in SEI, if any.
        seiBuffer.skipBytes(payloadSize - (10 + ccCount * 3));
      } else {
        seiBuffer.skipBytes(payloadSize);
      }
    }
  }

}
