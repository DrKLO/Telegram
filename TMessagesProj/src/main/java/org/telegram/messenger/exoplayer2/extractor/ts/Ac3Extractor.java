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
import org.telegram.messenger.exoplayer2.audio.Ac3Util;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorsFactory;
import org.telegram.messenger.exoplayer2.extractor.PositionHolder;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Facilitates the extraction of AC-3 samples from elementary audio files formatted as AC-3
 * bitstreams.
 */
public final class Ac3Extractor implements Extractor {

  /**
   * Factory for {@link Ac3Extractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new Ac3Extractor()};
    }

  };

  /**
   * The maximum number of bytes to search when sniffing, excluding ID3 information, before giving
   * up.
   */
  private static final int MAX_SNIFF_BYTES = 8 * 1024;
  private static final int AC3_SYNC_WORD = 0x0B77;
  private static final int MAX_SYNC_FRAME_SIZE = 2786;
  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");

  private final long firstSampleTimestampUs;
  private final Ac3Reader reader;
  private final ParsableByteArray sampleData;

  private boolean startedPacket;

  public Ac3Extractor() {
    this(0);
  }

  public Ac3Extractor(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    reader = new Ac3Reader();
    sampleData = new ParsableByteArray(MAX_SYNC_FRAME_SIZE);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Skip any ID3 headers.
    ParsableByteArray scratch = new ParsableByteArray(10);
    int startPosition = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 10);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }
      scratch.skipBytes(3);
      int length = scratch.readSynchSafeInt();
      startPosition += 10 + length;
      input.advancePeekPosition(length);
    }
    input.resetPeekPosition();
    input.advancePeekPosition(startPosition);

    int headerPosition = startPosition;
    int validFramesCount = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 5);
      scratch.setPosition(0);
      int syncBytes = scratch.readUnsignedShort();
      if (syncBytes != AC3_SYNC_WORD) {
        validFramesCount = 0;
        input.resetPeekPosition();
        if (++headerPosition - startPosition >= MAX_SNIFF_BYTES) {
          return false;
        }
        input.advancePeekPosition(headerPosition);
      } else {
        if (++validFramesCount >= 4) {
          return true;
        }
        int frameSize = Ac3Util.parseAc3SyncframeSize(scratch.data);
        if (frameSize == C.LENGTH_UNSET) {
          return false;
        }
        input.advancePeekPosition(frameSize - 5);
      }
    }
  }

  @Override
  public void init(ExtractorOutput output) {
    reader.createTracks(output, new TrackIdGenerator(0, 1));
    output.endTracks();
    output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
  }

  @Override
  public void seek(long position, long timeUs) {
    startedPacket = false;
    reader.seek();
  }

  @Override
  public void release() {
    // Do nothing.
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException,
      InterruptedException {
    int bytesRead = input.read(sampleData.data, 0, MAX_SYNC_FRAME_SIZE);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }

    // Feed whatever data we have to the reader, regardless of whether the read finished or not.
    sampleData.setPosition(0);
    sampleData.setLimit(bytesRead);

    if (!startedPacket) {
      // Pass data to the reader as though it's contained within a single infinitely long packet.
      reader.packetStarted(firstSampleTimestampUs, true);
      startedPacket = true;
    }
    // TODO: Make it possible for the reader to consume the dataSource directly, so that it becomes
    // unnecessary to copy the data through packetBuffer.
    reader.consume(sampleData);
    return RESULT_CONTINUE;
  }

}
