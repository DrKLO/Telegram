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
import org.telegram.messenger.exoplayer.util.ParsableByteArray;

/**
 * Parses ID3 data and extracts individual text information frames.
 */
/* package */ final class Id3Reader extends ElementaryStreamReader {

  private static final int ID3_HEADER_SIZE = 10;

  private final ParsableByteArray id3Header;

  // State that should be reset on seek.
  private boolean writingSample;

  // Per sample state that gets reset at the start of each sample.
  private long sampleTimeUs;
  private int sampleSize;
  private int sampleBytesRead;

  public Id3Reader(TrackOutput output) {
    super(output);
    output.format(MediaFormat.createId3Format());
    id3Header = new ParsableByteArray(ID3_HEADER_SIZE);
  }

  @Override
  public void seek() {
    writingSample = false;
  }

  @Override
  public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    if (!dataAlignmentIndicator) {
      return;
    }
    writingSample = true;
    sampleTimeUs = pesTimeUs;
    sampleSize = 0;
    sampleBytesRead = 0;
  }

  @Override
  public void consume(ParsableByteArray data) {
    if (!writingSample) {
      return;
    }
    int bytesAvailable = data.bytesLeft();
    if (sampleBytesRead < ID3_HEADER_SIZE) {
      // We're still reading the ID3 header.
      int headerBytesAvailable = Math.min(bytesAvailable, ID3_HEADER_SIZE - sampleBytesRead);
      System.arraycopy(data.data, data.getPosition(), id3Header.data, sampleBytesRead,
          headerBytesAvailable);
      if (sampleBytesRead + headerBytesAvailable == ID3_HEADER_SIZE) {
        // We've finished reading the ID3 header. Extract the sample size.
        id3Header.setPosition(6); // 'ID3' (3) + version (2) + flags (1)
        sampleSize = ID3_HEADER_SIZE + id3Header.readSynchSafeInt();
      }
    }
    // Write data to the output.
    int bytesToWrite = Math.min(bytesAvailable, sampleSize - sampleBytesRead);
    output.sampleData(data, bytesToWrite);
    sampleBytesRead += bytesToWrite;
  }

  @Override
  public void packetFinished() {
    if (!writingSample || sampleSize == 0 || sampleBytesRead != sampleSize) {
      return;
    }
    output.sampleMetadata(sampleTimeUs, C.SAMPLE_FLAG_SYNC, sampleSize, 0, null);
    writingSample = false;
  }

}
