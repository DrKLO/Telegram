/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.IOException;

/**
 * A reader that can extract the approximate duration from a given MPEG transport stream (TS).
 *
 * <p>This reader extracts the duration by reading PCR values of the PCR PID packets at the start
 * and at the end of the stream, calculating the difference, and converting that into stream
 * duration. This reader also handles the case when a single PCR wraparound takes place within the
 * stream, which can make PCR values at the beginning of the stream larger than PCR values at the
 * end. This class can only be used once to read duration from a given stream, and the usage of the
 * class is not thread-safe, so all calls should be made from the same thread.
 */
/* package */ final class TsDurationReader {

  private static final int DURATION_READ_PACKETS = 200;
  private static final int DURATION_READ_BYTES = TsExtractor.TS_PACKET_SIZE * DURATION_READ_PACKETS;

  private final TimestampAdjuster pcrTimestampAdjuster;
  private final ParsableByteArray packetBuffer;

  private boolean isDurationRead;
  private boolean isFirstPcrValueRead;
  private boolean isLastPcrValueRead;

  private long firstPcrValue;
  private long lastPcrValue;
  private long durationUs;

  /* package */ TsDurationReader() {
    pcrTimestampAdjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    firstPcrValue = C.TIME_UNSET;
    lastPcrValue = C.TIME_UNSET;
    durationUs = C.TIME_UNSET;
    packetBuffer = new ParsableByteArray(DURATION_READ_BYTES);
  }

  /** Returns true if a TS duration has been read. */
  public boolean isDurationReadFinished() {
    return isDurationRead;
  }

  /**
   * Reads a TS duration from the input, using the given PCR PID.
   *
   * <p>This reader reads the duration by reading PCR values of the PCR PID packets at the start and
   * at the end of the stream, calculating the difference, and converting that into stream duration.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param seekPositionHolder If {@link Extractor#RESULT_SEEK} is returned, this holder is updated
   *     to hold the position of the required seek.
   * @param pcrPid The PID of the packet stream within this TS stream that contains PCR values.
   * @return One of the {@code RESULT_} values defined in {@link Extractor}.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public @Extractor.ReadResult int readDuration(
      ExtractorInput input, PositionHolder seekPositionHolder, int pcrPid)
      throws IOException, InterruptedException {
    if (pcrPid <= 0) {
      return finishReadDuration(input);
    }
    if (!isLastPcrValueRead) {
      return readLastPcrValue(input, seekPositionHolder, pcrPid);
    }
    if (lastPcrValue == C.TIME_UNSET) {
      return finishReadDuration(input);
    }
    if (!isFirstPcrValueRead) {
      return readFirstPcrValue(input, seekPositionHolder, pcrPid);
    }
    if (firstPcrValue == C.TIME_UNSET) {
      return finishReadDuration(input);
    }

    long minPcrPositionUs = pcrTimestampAdjuster.adjustTsTimestamp(firstPcrValue);
    long maxPcrPositionUs = pcrTimestampAdjuster.adjustTsTimestamp(lastPcrValue);
    durationUs = maxPcrPositionUs - minPcrPositionUs;
    return finishReadDuration(input);
  }

  /**
   * Returns the duration last read from {@link #readDuration(ExtractorInput, PositionHolder, int)}.
   */
  public long getDurationUs() {
    return durationUs;
  }

  private int finishReadDuration(ExtractorInput input) {
    isDurationRead = true;
    input.resetPeekPosition();
    return Extractor.RESULT_CONTINUE;
  }

  private int readFirstPcrValue(ExtractorInput input, PositionHolder seekPositionHolder, int pcrPid)
      throws IOException, InterruptedException {
    if (input.getPosition() != 0) {
      seekPositionHolder.position = 0;
      return Extractor.RESULT_SEEK;
    }

    int bytesToRead = (int) Math.min(DURATION_READ_BYTES, input.getLength());
    input.resetPeekPosition();
    input.peekFully(packetBuffer.data, /* offset= */ 0, bytesToRead);
    packetBuffer.setPosition(0);
    packetBuffer.setLimit(bytesToRead);

    firstPcrValue = readFirstPcrValueFromBuffer(packetBuffer, pcrPid);
    isFirstPcrValueRead = true;
    return Extractor.RESULT_CONTINUE;
  }

  private long readFirstPcrValueFromBuffer(ParsableByteArray packetBuffer, int pcrPid) {
    int searchStartPosition = packetBuffer.getPosition();
    int searchEndPosition = packetBuffer.limit();
    for (int searchPosition = searchStartPosition;
        searchPosition < searchEndPosition;
        searchPosition++) {
      if (packetBuffer.data[searchPosition] != TsExtractor.TS_SYNC_BYTE) {
        continue;
      }
      long pcrValue = readPcrFromPacket(packetBuffer, searchPosition, pcrPid);
      if (pcrValue != C.TIME_UNSET) {
        return pcrValue;
      }
    }
    return C.TIME_UNSET;
  }

  private int readLastPcrValue(ExtractorInput input, PositionHolder seekPositionHolder, int pcrPid)
      throws IOException, InterruptedException {
    int bytesToRead = (int) Math.min(DURATION_READ_BYTES, input.getLength());
    long bufferStartStreamPosition = input.getLength() - bytesToRead;
    if (input.getPosition() != bufferStartStreamPosition) {
      seekPositionHolder.position = bufferStartStreamPosition;
      return Extractor.RESULT_SEEK;
    }

    input.resetPeekPosition();
    input.peekFully(packetBuffer.data, /* offset= */ 0, bytesToRead);
    packetBuffer.setPosition(0);
    packetBuffer.setLimit(bytesToRead);

    lastPcrValue = readLastPcrValueFromBuffer(packetBuffer, pcrPid);
    isLastPcrValueRead = true;
    return Extractor.RESULT_CONTINUE;
  }

  private long readLastPcrValueFromBuffer(ParsableByteArray packetBuffer, int pcrPid) {
    int searchStartPosition = packetBuffer.getPosition();
    int searchEndPosition = packetBuffer.limit();
    for (int searchPosition = searchEndPosition - 1;
        searchPosition >= searchStartPosition;
        searchPosition--) {
      if (packetBuffer.data[searchPosition] != TsExtractor.TS_SYNC_BYTE) {
        continue;
      }
      long pcrValue = readPcrFromPacket(packetBuffer, searchPosition, pcrPid);
      if (pcrValue != C.TIME_UNSET) {
        return pcrValue;
      }
    }
    return C.TIME_UNSET;
  }

  private static long readPcrFromPacket(
      ParsableByteArray packetBuffer, int startOfPacket, int pcrPid) {
    packetBuffer.setPosition(startOfPacket);
    if (packetBuffer.bytesLeft() < 5) {
      // Header = 4 bytes, adaptationFieldLength = 1 byte.
      return C.TIME_UNSET;
    }
    // Note: See ISO/IEC 13818-1, section 2.4.3.2 for details of the header format.
    int tsPacketHeader = packetBuffer.readInt();
    if ((tsPacketHeader & 0x800000) != 0) {
      // transport_error_indicator != 0 means there are uncorrectable errors in this packet.
      return C.TIME_UNSET;
    }
    int pid = (tsPacketHeader & 0x1FFF00) >> 8;
    if (pid != pcrPid) {
      return C.TIME_UNSET;
    }
    boolean adaptationFieldExists = (tsPacketHeader & 0x20) != 0;
    if (!adaptationFieldExists) {
      return C.TIME_UNSET;
    }

    int adaptationFieldLength = packetBuffer.readUnsignedByte();
    if (adaptationFieldLength >= 7 && packetBuffer.bytesLeft() >= 7) {
      int flags = packetBuffer.readUnsignedByte();
      boolean pcrFlagSet = (flags & 0x10) == 0x10;
      if (pcrFlagSet) {
        byte[] pcrBytes = new byte[6];
        packetBuffer.readBytes(pcrBytes, /* offset= */ 0, pcrBytes.length);
        return readPcrValueFromPcrBytes(pcrBytes);
      }
    }
    return C.TIME_UNSET;
  }

  /**
   * Returns the value of PCR base - first 33 bits in big endian order from the PCR bytes.
   *
   * <p>We ignore PCR Ext, because it's too small to have any significance.
   */
  private static long readPcrValueFromPcrBytes(byte[] pcrBytes) {
    return (pcrBytes[0] & 0xFFL) << 25
        | (pcrBytes[1] & 0xFFL) << 17
        | (pcrBytes[2] & 0xFFL) << 9
        | (pcrBytes[3] & 0xFFL) << 1
        | (pcrBytes[4] & 0xFFL) >> 7;
  }
}
