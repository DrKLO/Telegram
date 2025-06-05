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
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an AMR byte stream carried on RTP packets and extracts individual samples. Interleaving
 * mode is not supported. Refer to RFC4867 for more details.
 */
/* package */ final class RtpAmrReader implements RtpPayloadReader {
  private static final String TAG = "RtpAmrReader";
  /**
   * The frame size in bytes, including header (1 byte), for each of the 16 frame types for AMR-NB
   * (narrow band). AMR-NB supports eight narrow band speech encoding modes with bit rates between
   * 4.75 and 12.2 kbps defined in RFC4867 Section 3.1. Refer to table 1a in 3GPP TS 26.101 for the
   * mapping definition.
   */
  private static final int[] AMR_NB_FRAME_TYPE_INDEX_TO_FRAME_SIZE = {
    13, // 4.75kbps
    14, // 5.15kbps
    16, // 5.90kbps
    18, // 6.70kbps PDC-EFR
    20, // 7.40kbps TDMA-EFR
    21, // 7.95kbps
    27, // 10.2kbps
    32, // 12.2kbps GSM-EFR
    6, // AMR SID
    7, // GSM-EFR SID
    6, // TDMA-EFR SID
    6, // PDC-EFR SID
    1, // Future use
    1, // Future use
    1, // Future use
    1 // No data
  };

  /**
   * The frame size in bytes, including header (1 byte), for each of the 16 frame types for AMR-WB
   * (wide band). AMR-WB supports nine wide band speech encoding modes with bit rates between 6.6 to
   * 23.85 kbps defined in RFC4867 Section 3.2. Refer to table 1a in 3GPP TS 26.201. for the mapping
   * definition.
   */
  private static final int[] AMR_WB_FRAME_TYPE_INDEX_TO_FRAME_SIZE = {
    18, // 6.60kbps
    24, // 8.85kbps
    33, // 12.65kbps
    37, // 14.25kbps
    41, // 15.85kbps
    47, // 18.25kbps
    51, // 19.85kbps
    59, // 23.05kbps
    61, // 23.85kbps
    6, // AMR-WB SID
    1, // Future use
    1, // Future use
    1, // Future use
    1, // Future use
    1, // speech lost
    1 // No data
  };

  private final RtpPayloadFormat payloadFormat;
  private final boolean isWideBand;
  private final int sampleRate;

  private @MonotonicNonNull TrackOutput trackOutput;
  private long firstReceivedTimestamp;
  private long startTimeOffsetUs;
  private int previousSequenceNumber;

  public RtpAmrReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    this.isWideBand =
        MimeTypes.AUDIO_AMR_WB.equals(checkNotNull(payloadFormat.format.sampleMimeType));
    this.sampleRate = payloadFormat.clockRate;
    this.firstReceivedTimestamp = C.TIME_UNSET;
    this.previousSequenceNumber = C.INDEX_UNSET;
    // Start time offset must be 0 before the first seek.
    this.startTimeOffsetUs = 0;
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
    checkStateNotNull(trackOutput);
    // Check that this packet is in the sequence of the previous packet.
    if (previousSequenceNumber != C.INDEX_UNSET) {
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d.",
                expectedSequenceNumber, sequenceNumber));
      }
    }
    //
    // AMR as RTP payload (RFC4867 Section 4.2).
    //
    // +----------------+-------------------+----------------
    // | payload header | table of contents | speech data ...
    // +----------------+-------------------+----------------
    //
    // Payload header (RFC4867 Section 4.4.1).
    //
    // The header won't contain ILL and ILP, as interleaving is not currently supported.
    // +-+-+-+-+-+-+-+- - - - - - - -
    // | CMR |R|R|R|R| ILL  |  ILP  |
    // +-+-+-+-+-+-+-+- - - - - - - -
    //
    // Skip CMR and reserved bits.
    data.skipBytes(1);
    // Loop over sampleSize to send multiple frames along with appropriate timestamp when compound
    // payload support is added.
    int frameType = (data.peekUnsignedByte() >> 3) & 0x0f;
    int frameSize = getFrameSize(frameType, isWideBand);
    int sampleSize = data.bytesLeft();
    checkArgument(sampleSize == frameSize, "compound payload not supported currently");
    trackOutput.sampleData(data, sampleSize);
    long sampleTimeUs =
        toSampleTimeUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp, sampleRate);
    trackOutput.sampleMetadata(
        sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, /* offset= */ 0, /* cryptoData= */ null);

    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.

  public static int getFrameSize(int frameType, boolean isWideBand) {
    checkArgument(
        // Valid frame types are defined in RFC4867 Section 4.3.1.
        (frameType >= 0 && frameType <= 8) || frameType == 15,
        "Illegal AMR " + (isWideBand ? "WB" : "NB") + " frame type " + frameType);

    return isWideBand
        ? AMR_WB_FRAME_TYPE_INDEX_TO_FRAME_SIZE[frameType]
        : AMR_NB_FRAME_TYPE_INDEX_TO_FRAME_SIZE[frameType];
  }
}
