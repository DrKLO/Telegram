/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp.reader;

import static com.google.android.exoplayer2.source.rtsp.reader.RtpReaderUtils.toSampleTimeUs;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses a AAC byte stream carried on RTP packets and extracts individual samples. Interleaving
 * mode is not supported.
 */
/* package */ final class RtpAacReader implements RtpPayloadReader {

  /** AAC low bit rate mode, RFC3640 Section 3.3.5. */
  private static final String AAC_LOW_BITRATE_MODE = "AAC-lbr";
  /** AAC high bit rate mode, RFC3640 Section 3.3.6. */
  private static final String AAC_HIGH_BITRATE_MODE = "AAC-hbr";

  private static final String TAG = "RtpAacReader";

  private final RtpPayloadFormat payloadFormat;
  private final ParsableBitArray auHeaderScratchBit;
  private final int sampleRate;
  private final int auSizeFieldBitSize;
  private final int auIndexFieldBitSize;
  private final int numBitsInAuHeader;

  private long firstReceivedTimestamp;
  private @MonotonicNonNull TrackOutput trackOutput;
  private long startTimeOffsetUs;

  public RtpAacReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    this.auHeaderScratchBit = new ParsableBitArray();
    this.sampleRate = this.payloadFormat.clockRate;

    // mode attribute is mandatory. See RFC3640 Section 4.1.
    String mode = checkNotNull(payloadFormat.fmtpParameters.get("mode"));
    if (Ascii.equalsIgnoreCase(mode, AAC_HIGH_BITRATE_MODE)) {
      auSizeFieldBitSize = 13;
      auIndexFieldBitSize = 3;
    } else if (Ascii.equalsIgnoreCase(mode, AAC_LOW_BITRATE_MODE)) {
      auSizeFieldBitSize = 6;
      auIndexFieldBitSize = 2;
    } else {
      throw new UnsupportedOperationException("AAC mode not supported");
    }
    // TODO(b/172331505) Add support for other AU-Header fields, like CTS-flag, CTS-delta, etc.
    numBitsInAuHeader = auIndexFieldBitSize + auSizeFieldBitSize;
  }

  // RtpPayloadReader implementation.

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_AUDIO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    this.firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    /*
    AAC as RTP payload (RFC3640):
      +---------+-----------+-----------+---------------+
      | RTP     | AU Header | Auxiliary | Access Unit   |
      | Header  | Section   | Section   | Data Section  |
      +---------+-----------+-----------+---------------+
                <----------RTP Packet Payload----------->

    Access Unit(AU) Header section
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- .. -+-+-+-+-+-+-+-+-+-+
      |AU-headers-length|AU-header|AU-header|      |AU-header|padding|
      |in bits          |   (1)   |   (2)   |      |   (n)   | bits  |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+- .. -+-+-+-+-+-+-+-+-+-+

    The 16-bit AU-headers-length is mandatory in the AAC-lbr and AAC-hbr modes that we support.
    */
    checkNotNull(trackOutput);
    // Reads AU-header-length that specifies the length in bits of the immediately following
    // AU-headers, excluding the padding.
    int auHeadersBitLength = data.readShort();
    int auHeaderCount = auHeadersBitLength / numBitsInAuHeader;
    long sampleTimeUs =
        toSampleTimeUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp, sampleRate);

    // Points to the start of the AU-headers (right past the AU-headers-length).
    auHeaderScratchBit.reset(data);
    if (auHeaderCount == 1) {
      // Reads the first AU-Header that contains AU-Size and AU-Index/AU-Index-delta.
      int auSize = auHeaderScratchBit.readBits(auSizeFieldBitSize);
      auHeaderScratchBit.skipBits(auIndexFieldBitSize);

      // Outputs all the received data, whether fragmented or not.
      trackOutput.sampleData(data, data.bytesLeft());
      if (rtpMarker) {
        outputSampleMetadata(trackOutput, sampleTimeUs, auSize);
      }
    } else {
      // Skips the AU-headers section to the data section, accounts for the possible padding bits.
      data.skipBytes((auHeadersBitLength + 7) / 8);
      for (int i = 0; i < auHeaderCount; i++) {
        int auSize = auHeaderScratchBit.readBits(auSizeFieldBitSize);
        auHeaderScratchBit.skipBits(auIndexFieldBitSize);

        trackOutput.sampleData(data, auSize);
        outputSampleMetadata(trackOutput, sampleTimeUs, auSize);
        // The sample time of the  of the i-th AU (RFC3640 Page 17):
        // (timestamp-of-the-first-AU) + i * (access-unit-duration)
        sampleTimeUs +=
            Util.scaleLargeTimestamp(
                auHeaderCount, /* multiplier= */ C.MICROS_PER_SECOND, /* divisor= */ sampleRate);
      }
    }
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.

  private static void outputSampleMetadata(TrackOutput trackOutput, long sampleTimeUs, int size) {
    trackOutput.sampleMetadata(
        sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, size, /* offset= */ 0, /* cryptoData= */ null);
  }
}
