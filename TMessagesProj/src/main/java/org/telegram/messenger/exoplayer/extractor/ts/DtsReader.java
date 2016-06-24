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
import org.telegram.messenger.exoplayer.util.DtsUtil;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;

/**
 * Parses a continuous DTS byte stream and extracts individual samples.
 */
/* package */ final class DtsReader extends ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 15;
  private static final int SYNC_VALUE = 0x7FFE8001;
  private static final int SYNC_VALUE_SIZE = 4;

  private final ParsableByteArray headerScratchBytes;

  private int state;
  private int bytesRead;

  // Used to find the header.
  private int syncBytes;

  // Used when parsing the header.
  private long sampleDurationUs;
  private MediaFormat mediaFormat;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  /**
   * Constructs a new reader for DTS elementary streams.
   *
   * @param output Track output for extracted samples.
   */
  public DtsReader(TrackOutput output) {
    super(output);
    headerScratchBytes = new ParsableByteArray(new byte[HEADER_SIZE]);
    headerScratchBytes.data[0] = (byte) ((SYNC_VALUE >> 24) & 0xFF);
    headerScratchBytes.data[1] = (byte) ((SYNC_VALUE >> 16) & 0xFF);
    headerScratchBytes.data[2] = (byte) ((SYNC_VALUE >> 8) & 0xFF);
    headerScratchBytes.data[3] = (byte) (SYNC_VALUE & 0xFF);
    state = STATE_FINDING_SYNC;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    syncBytes = 0;
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
            bytesRead = SYNC_VALUE_SIZE;
            state = STATE_READING_HEADER;
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
   * Locates the next SYNC value in the buffer, advancing the position to the byte that immediately
   * follows it. If SYNC was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return True if SYNC was found. False otherwise.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      syncBytes <<= 8;
      syncBytes |= pesBuffer.readUnsignedByte();
      if (syncBytes == SYNC_VALUE) {
        syncBytes = 0;
        return true;
      }
    }
    return false;
  }

  /**
   * Parses the sample header.
   */
  private void parseHeader() {
    byte[] frameData = headerScratchBytes.data;
    if (mediaFormat == null) {
      mediaFormat = DtsUtil.parseDtsFormat(frameData, null, C.UNKNOWN_TIME_US, null);
      output.format(mediaFormat);
    }
    sampleSize = DtsUtil.getDtsFrameSize(frameData);
    // In this class a sample is an access unit (frame in DTS), but the MediaFormat sample rate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs = (int) (C.MICROS_PER_SECOND
        * DtsUtil.parseDtsAudioSampleCount(frameData) / mediaFormat.sampleRate);
  }

}
