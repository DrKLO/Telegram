/*
 * Copyright 2022 The Android Open Source Project
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
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Parses an H265 byte stream carried on RTP packets, and extracts H265 Access Units. Refer to
 * RFC7798 for more details.
 */
/* package */ final class RtpH265Reader implements RtpPayloadReader {

  private static final String TAG = "RtpH265Reader";
  private static final int MEDIA_CLOCK_FREQUENCY = 90_000;
  /** Offset of payload data within a FU payload. */
  private static final int FU_PAYLOAD_OFFSET = 3;
  /** Aggregation Packet. RFC7798 Section 4.4.2. */
  private static final int RTP_PACKET_TYPE_AP = 48;
  /** Fragmentation Unit. RFC7798 Section 4.4.3. */
  private static final int RTP_PACKET_TYPE_FU = 49;
  /** IDR NAL unit types. */
  private static final int NAL_IDR_W_RADL = 19;

  private static final int NAL_IDR_N_LP = 20;

  /** Scratch for Fragmentation Unit RTP packets. */
  private final ParsableByteArray fuScratchBuffer;

  private final ParsableByteArray nalStartCodeArray;
  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;
  private @C.BufferFlags int bufferFlags;
  private long firstReceivedTimestamp;
  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple RTP packets. */
  private int fragmentedSampleSizeBytes;

  private long startTimeOffsetUs;

  /** Creates an instance. */
  public RtpH265Reader(RtpPayloadFormat payloadFormat) {
    this.fuScratchBuffer = new ParsableByteArray();
    this.nalStartCodeArray = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {}

  @Override
  public void consume(ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker)
      throws ParserException {
    if (data.getData().length == 0) {
      throw ParserException.createForMalformedManifest("Empty RTP data packet.", /* cause= */ null);
    }
    // NAL Unit Header.type (RFC7798 Section 1.1.4).
    int payloadType = (data.getData()[0] >> 1) & 0x3F;

    checkStateNotNull(trackOutput);
    if (payloadType >= 0 && payloadType < RTP_PACKET_TYPE_AP) {
      processSingleNalUnitPacket(data);
    } else if (payloadType == RTP_PACKET_TYPE_AP) {
      // TODO: Support AggregationPacket mode.
      throw new UnsupportedOperationException("need to implement processAggregationPacket");
    } else if (payloadType == RTP_PACKET_TYPE_FU) {
      processFragmentationUnitPacket(data, sequenceNumber);
    } else {
      throw ParserException.createForMalformedManifest(
          String.format("RTP H265 payload type [%d] not supported.", payloadType),
          /* cause= */ null);
    }

    if (rtpMarker) {
      if (firstReceivedTimestamp == C.TIME_UNSET) {
        firstReceivedTimestamp = timestamp;
      }

      long timeUs =
          toSampleTimeUs(
              startTimeOffsetUs, timestamp, firstReceivedTimestamp, MEDIA_CLOCK_FREQUENCY);
      trackOutput.sampleMetadata(
          timeUs, bufferFlags, fragmentedSampleSizeBytes, /* offset= */ 0, /* cryptoData= */ null);
      fragmentedSampleSizeBytes = 0;
    }

    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    fragmentedSampleSizeBytes = 0;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.

  /**
   * Processes Single NAL Unit packet (RFC7798 Section 4.4.1).
   *
   * <p>Outputs the single NAL Unit (with start code prepended) to {@link #trackOutput}. Sets {@link
   * #bufferFlags} and {@link #fragmentedSampleSizeBytes} accordingly.
   */
  @RequiresNonNull("trackOutput")
  private void processSingleNalUnitPacket(ParsableByteArray data) {
    //  The structure a single NAL unit packet.
    //     0                   1                   2                   3
    //     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //    |           PayloadHdr          |      DONL (conditional)       |
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //    |                                                               |
    //    |                  NAL unit payload data                        |
    //    |                                                               |
    //    |                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //    |                               :...OPTIONAL RTP padding        |
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    int numBytesInData = data.bytesLeft();
    fragmentedSampleSizeBytes += writeStartCode();
    trackOutput.sampleData(data, numBytesInData);
    fragmentedSampleSizeBytes += numBytesInData;

    int nalHeaderType = (data.getData()[0] >> 1) & 0x3F;
    bufferFlags = getBufferFlagsFromNalType(nalHeaderType);
  }

  /**
   * Processes Fragmentation Unit packet (RFC7798 Section 4.4.3).
   *
   * <p>This method will be invoked multiple times to receive a single frame that is broken down
   * into a series of fragmentation units in multiple RTP packets.
   *
   * <p>Outputs the received fragmentation units (with start code prepended) to {@link
   * #trackOutput}. Sets {@link #bufferFlags} and {@link #fragmentedSampleSizeBytes} accordingly.
   */
  @RequiresNonNull("trackOutput")
  private void processFragmentationUnitPacket(ParsableByteArray data, int packetSequenceNumber)
      throws ParserException {
    //  The structure of an FU packet.
    //    0                   1                   2                   3
    //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //   |    PayloadHdr (Type=49)       |   FU header   | DONL (cond)   |
    //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-|
    //   | DONL (cond)   |                                               |
    //   |-+-+-+-+-+-+-+-+                                               |
    //   |                         FU payload                            |
    //   |                                                               |
    //   |                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //   |                               :...OPTIONAL RTP padding        |
    //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //
    //   FU header.
    //   +---------------+
    //   |0|1|2|3|4|5|6|7|
    //   +-+-+-+-+-+-+-+-+
    //   |S|E|  FuType   |
    //   +---------------+
    //
    //   Structure of the PayloadHdr and HEVC NAL unit header, RFC7798 Section 1.1.4.
    //   +---------------+---------------+
    //   |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
    //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //   |F|   Type    |  LayerId  | TID |
    //   +-------------+-----------------+
    if (data.getData().length < 3) {
      throw ParserException.createForMalformedManifest("Malformed FU header.", /* cause= */ null);
    }
    int tid = (data.getData()[1] & 0x7);
    int fuHeader = data.getData()[2];
    int nalUnitType = fuHeader & 0x3F;
    boolean isFirstFuPacket = (fuHeader & 0x80) > 0;
    boolean isLastFuPacket = (fuHeader & 0x40) > 0;

    if (isFirstFuPacket) {
      // Prepends starter code.
      fragmentedSampleSizeBytes += writeStartCode();

      // Convert RTP header into HEVC NAL Unit header accoding to RFC7798 Section 1.1.4.
      // RTP byte 0: ignored.
      // RTP byte 1: repurposed as HEVC HALU byte 0, copy NALU type.
      // RTP Byte 2: repurposed as HEVC HALU byte 1, layerId required to be zero, copying only tid.
      // Set data position from byte 1 as byte 0 is ignored.
      data.getData()[1] = (byte) ((nalUnitType << 1) & 0x7F);
      data.getData()[2] = (byte) tid;
      fuScratchBuffer.reset(data.getData());
      fuScratchBuffer.setPosition(1);
    } else {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = (previousSequenceNumber + 1) % RtpPacket.MAX_SEQUENCE_NUMBER;
      if (packetSequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, packetSequenceNumber));
        return;
      }

      // Setting position to ignore payload and FU header.
      fuScratchBuffer.reset(data.getData());
      fuScratchBuffer.setPosition(FU_PAYLOAD_OFFSET);
    }

    int fragmentSize = fuScratchBuffer.bytesLeft();
    trackOutput.sampleData(fuScratchBuffer, fragmentSize);
    fragmentedSampleSizeBytes += fragmentSize;

    if (isLastFuPacket) {
      bufferFlags = getBufferFlagsFromNalType(nalUnitType);
    }
  }

  private int writeStartCode() {
    nalStartCodeArray.setPosition(/* position= */ 0);
    int bytesWritten = nalStartCodeArray.bytesLeft();
    checkNotNull(trackOutput).sampleData(nalStartCodeArray, bytesWritten);
    return bytesWritten;
  }

  private static @C.BufferFlags int getBufferFlagsFromNalType(int nalType) {
    return (nalType == NAL_IDR_W_RADL || nalType == NAL_IDR_N_LP) ? C.BUFFER_FLAG_KEY_FRAME : 0;
  }
}
