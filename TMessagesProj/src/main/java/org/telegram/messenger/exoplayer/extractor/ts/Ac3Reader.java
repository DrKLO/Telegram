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
import org.telegram.messenger.exoplayer.util.Ac3Util;
import org.telegram.messenger.exoplayer.util.ParsableBitArray;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;

/**
 * Parses a continuous (E-)AC-3 byte stream and extracts individual samples.
 */
/* package */ final class Ac3Reader extends ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 8;

  private final boolean isEac3;
  private final ParsableBitArray headerScratchBits;
  private final ParsableByteArray headerScratchBytes;

  private int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWas0B;

  // Used when parsing the header.
  private long sampleDurationUs;
  private MediaFormat mediaFormat;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  /**
   * Constructs a new reader for (E-)AC-3 elementary streams.
   *
   * @param output Track output for extracted samples.
   * @param isEac3 Whether the stream is E-AC-3 (ETSI TS 102 366 Annex E). Specify {@code false} to
   *     parse sample headers as AC-3.
   */
  public Ac3Reader(TrackOutput output, boolean isEac3) {
    super(output);
    this.isEac3 = isEac3;
    headerScratchBits = new ParsableBitArray(new byte[HEADER_SIZE]);
    headerScratchBytes = new ParsableByteArray(headerScratchBits.data);
    state = STATE_FINDING_SYNC;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWas0B = false;
  }

  @Override
  public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    timeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) {
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            state = STATE_READING_HEADER;
            headerScratchBytes.data[0] = 0x0B;
            headerScratchBytes.data[1] = 0x77;
            bytesRead = 2;
          }
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, headerScratchBytes.data, HEADER_SIZE)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, HEADER_SIZE);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            output.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, sampleSize, 0, null);
            timeUs += sampleDurationUs;
            state = STATE_FINDING_SYNC;
          }
          break;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read.
   * @param targetLength The target length of the read.
   * @return Whether the target length was reached.
   */
  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
    source.readBytes(target, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  /**
   * Locates the next syncword, advancing the position to the byte that immediately follows it. If a
   * syncword was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return True if a syncword position was found. False otherwise.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      if (!lastByteWas0B) {
        lastByteWas0B = pesBuffer.readUnsignedByte() == 0x0B;
        continue;
      }
      int secondByte = pesBuffer.readUnsignedByte();
      if (secondByte == 0x77) {
        lastByteWas0B = false;
        return true;
      } else {
        lastByteWas0B = secondByte == 0x0B;
      }
    }
    return false;
  }

  /**
   * Parses the sample header.
   */
  private void parseHeader() {
    if (mediaFormat == null) {
      mediaFormat = isEac3
          ? Ac3Util.parseEac3SyncframeFormat(headerScratchBits, null, C.UNKNOWN_TIME_US, null)
          : Ac3Util.parseAc3SyncframeFormat(headerScratchBits, null, C.UNKNOWN_TIME_US, null);
      output.format(mediaFormat);
    }
    sampleSize = isEac3 ? Ac3Util.parseEAc3SyncframeSize(headerScratchBits.data)
        : Ac3Util.parseAc3SyncframeSize(headerScratchBits.data);
    int audioSamplesPerSyncframe = isEac3
        ? Ac3Util.parseEAc3SyncframeAudioSampleCount(headerScratchBits.data)
        : Ac3Util.getAc3SyncframeAudioSampleCount();
    // In this class a sample is an access unit (syncframe in AC-3), but the MediaFormat sample rate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs =
        (int) (C.MICROS_PER_SECOND * audioSamplesPerSyncframe / mediaFormat.sampleRate);
  }

}
