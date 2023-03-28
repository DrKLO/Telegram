/*
 * Copyright 2021 The Android Open Source Project
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
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Parses an AC3 byte stream carried on RTP packets, and extracts AC3 frames. */
/* package */ public final class RtpAc3Reader implements RtpPayloadReader {

  /** AC3 frame types defined in RFC4184 Section 4.1.1. */
  private static final int AC3_FRAME_TYPE_COMPLETE_FRAME = 0;
  /** Initial fragment of frame which includes the first 5/8ths of the frame. */
  private static final int AC3_FRAME_TYPE_INITIAL_FRAGMENT_A = 1;
  /** Initial fragment of frame which does not include the first 5/8ths of the frame. */
  private static final int AC3_FRAME_TYPE_INITIAL_FRAGMENT_B = 2;

  private static final int AC3_FRAME_TYPE_NON_INITIAL_FRAGMENT = 3;

  /** AC3 payload header size in bytes. */
  private static final int AC3_PAYLOAD_HEADER_SIZE = 2;

  private final RtpPayloadFormat payloadFormat;
  private final ParsableBitArray scratchBitBuffer;

  private @MonotonicNonNull TrackOutput trackOutput;
  private int numBytesPendingMetadataOutput;
  private long firstReceivedTimestamp;
  private long sampleTimeUsOfFramePendingMetadataOutput;
  private long startTimeOffsetUs;

  public RtpAc3Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    scratchBitBuffer = new ParsableBitArray();
    firstReceivedTimestamp = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_AUDIO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    checkState(firstReceivedTimestamp == C.TIME_UNSET);
    firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    /*
    AC-3 payload as an RTP payload (RFC4184).
      +-+-+-+-+-+-+-+-+-+-+-+-+-+- .. +-+-+-+-+-+-+-+
      | Payload | Frame | Frame |     | Frame |
      | Header  |  (1)  |  (2)  |     |  (n)  |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+- .. +-+-+-+-+-+-+-+

    The payload header:
       0                   1
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |    MBZ    | FT|       NF      |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      FT: frame type.
      NF: number of frames/fragments.
     */
    int frameType = data.readUnsignedByte() & 0x3;
    int numOfFrames = data.readUnsignedByte() & 0xFF;

    long sampleTimeUs =
        toSampleTimeUs(
            startTimeOffsetUs, timestamp, firstReceivedTimestamp, payloadFormat.clockRate);

    switch (frameType) {
      case AC3_FRAME_TYPE_COMPLETE_FRAME:
        maybeOutputSampleMetadata();
        if (numOfFrames == 1) {
          // Single AC3 frame in one RTP packet.
          processSingleFramePacket(data, sampleTimeUs);
        } else {
          // Multiple AC3 frames in one RTP packet.
          processMultiFramePacket(data, numOfFrames, sampleTimeUs);
        }
        break;

      case AC3_FRAME_TYPE_INITIAL_FRAGMENT_A:
      case AC3_FRAME_TYPE_INITIAL_FRAGMENT_B:
        maybeOutputSampleMetadata();
        // Falls through.
      case AC3_FRAME_TYPE_NON_INITIAL_FRAGMENT:
        // The content of an AC3 frame is split into multiple RTP packets.
        processFragmentedPacket(data, rtpMarker, frameType, sampleTimeUs);
        break;

      default:
        throw new IllegalArgumentException(String.valueOf(frameType));
    }
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  private void processSingleFramePacket(ParsableByteArray data, long sampleTimeUs) {
    int frameSize = data.bytesLeft();
    checkNotNull(trackOutput).sampleData(data, frameSize);
    castNonNull(trackOutput)
        .sampleMetadata(
            /* timeUs= */ sampleTimeUs,
            /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
            /* size= */ frameSize,
            /* offset= */ 0,
            /* cryptoData= */ null);
  }

  private void processMultiFramePacket(ParsableByteArray data, int numOfFrames, long sampleTimeUs) {
    // The size of each frame must be obtained by reading AC3 sync frame.
    scratchBitBuffer.reset(data.getData());
    // Move the read location after the AC3 payload header.
    scratchBitBuffer.skipBytes(AC3_PAYLOAD_HEADER_SIZE);

    for (int i = 0; i < numOfFrames; i++) {
      Ac3Util.SyncFrameInfo frameInfo = Ac3Util.parseAc3SyncframeInfo(scratchBitBuffer);

      checkNotNull(trackOutput).sampleData(data, frameInfo.frameSize);
      castNonNull(trackOutput)
          .sampleMetadata(
              /* timeUs= */ sampleTimeUs,
              /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
              /* size= */ frameInfo.frameSize,
              /* offset= */ 0,
              /* cryptoData= */ null);

      sampleTimeUs += (frameInfo.sampleCount / frameInfo.sampleRate) * C.MICROS_PER_SECOND;
      // Advance the position by the number of bytes read.
      scratchBitBuffer.skipBytes(frameInfo.frameSize);
    }
  }

  private void processFragmentedPacket(
      ParsableByteArray data, boolean isFrameBoundary, int frameType, long sampleTimeUs) {
    int bytesToWrite = data.bytesLeft();
    checkNotNull(trackOutput).sampleData(data, bytesToWrite);
    numBytesPendingMetadataOutput += bytesToWrite;
    sampleTimeUsOfFramePendingMetadataOutput = sampleTimeUs;

    if (isFrameBoundary && frameType == AC3_FRAME_TYPE_NON_INITIAL_FRAGMENT) {
      // Last RTP packet in the series of fragmentation packets.
      outputSampleMetadataForFragmentedPackets();
    }
  }

  /**
   * Checks and outputs sample metadata, if the last packet of a series of fragmented packets is
   * lost.
   *
   * <p>Call this method only when receiving an initial packet, i.e. on packets with type
   *
   * <ul>
   *   <li>{@link #AC3_FRAME_TYPE_COMPLETE_FRAME},
   *   <li>{@link #AC3_FRAME_TYPE_INITIAL_FRAGMENT_A}, or
   *   <li>{@link #AC3_FRAME_TYPE_INITIAL_FRAGMENT_B}.
   * </ul>
   */
  private void maybeOutputSampleMetadata() {
    if (numBytesPendingMetadataOutput > 0) {
      outputSampleMetadataForFragmentedPackets();
    }
  }

  private void outputSampleMetadataForFragmentedPackets() {
    castNonNull(trackOutput)
        .sampleMetadata(
            /* timeUs= */ sampleTimeUsOfFramePendingMetadataOutput,
            /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
            /* size= */ numBytesPendingMetadataOutput,
            /* offset= */ 0,
            /* cryptoData= */ null);
    numBytesPendingMetadataOutput = 0;
  }
}
