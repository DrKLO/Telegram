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

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.text.eia608.Eia608Parser;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;

/**
 * Consumes SEI buffers, outputting contained EIA608 messages to a {@link TrackOutput}.
 */
// TODO: Technically, we shouldn't allow a sample to be read from the queue until we're sure that
// a sample with an earlier timestamp won't be added to it.
/* package */ final class SeiReader {

  private final TrackOutput output;

  public SeiReader(TrackOutput output) {
    this.output = output;
    output.format(MediaFormat.createTextFormat(null, MimeTypes.APPLICATION_EIA608,
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, null));
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
      if (Eia608Parser.isSeiMessageEia608(payloadType, payloadSize, seiBuffer)) {
        output.sampleData(seiBuffer, payloadSize);
        output.sampleMetadata(pesTimeUs, C.SAMPLE_FLAG_SYNC, payloadSize, 0, null);
      } else {
        seiBuffer.skipBytes(payloadSize);
      }
    }
  }

}
