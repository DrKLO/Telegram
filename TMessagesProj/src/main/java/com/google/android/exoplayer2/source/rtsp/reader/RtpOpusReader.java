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
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.OpusUtil;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an OPUS byte stream carried on RTP packets and extracts individual samples. Refer to
 * RFC7845 for more details.
 */
/* package */ final class RtpOpusReader implements RtpPayloadReader {
  private static final String TAG = "RtpOpusReader";
  /* Opus uses a fixed 48KHz media clock RFC7845 Section 4. */
  private static final int MEDIA_CLOCK_FREQUENCY = 48_000;

  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;

  /**
   * First received RTP timestamp. All RTP timestamps are dimension-less, the time base is defined
   * by {@link #MEDIA_CLOCK_FREQUENCY}.
   */
  private long firstReceivedTimestamp;

  private long startTimeOffsetUs;
  private int previousSequenceNumber;
  private boolean foundOpusIDHeader;
  private boolean foundOpusCommentHeader;

  /** Creates an instance. */
  public RtpOpusReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    this.firstReceivedTimestamp = C.INDEX_UNSET;
    this.previousSequenceNumber = C.INDEX_UNSET;
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

    /* RFC7845 Section 3.
     * +---------+ +----------------+ +--------------------+ +-----
     * |ID Header| | Comment Header | |Audio Data Packet 1 | | ...
     * +---------+ +----------------+ +--------------------+ +-----
     */
    if (!foundOpusIDHeader) {
      validateOpusIdHeader(data);
      List<byte[]> initializationData = OpusUtil.buildInitializationData(data.getData());
      Format.Builder formatBuilder = payloadFormat.format.buildUpon();
      formatBuilder.setInitializationData(initializationData);
      trackOutput.format(formatBuilder.build());
      foundOpusIDHeader = true;
    } else if (!foundOpusCommentHeader) {
      // Comment Header RFC7845 Section 5.2.
      int sampleSize = data.limit();
      checkArgument(sampleSize >= 8, "Comment Header has insufficient data");
      String header = data.readString(8);
      checkArgument(header.equals("OpusTags"), "Comment Header should follow ID Header");
      foundOpusCommentHeader = true;
    } else {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d.",
                expectedSequenceNumber, sequenceNumber));
      }

      // sending opus data.
      int size = data.bytesLeft();
      trackOutput.sampleData(data, size);
      long timeUs =
          toSampleTimeUs(
              startTimeOffsetUs, timestamp, firstReceivedTimestamp, MEDIA_CLOCK_FREQUENCY);
      trackOutput.sampleMetadata(
          timeUs, C.BUFFER_FLAG_KEY_FRAME, size, /* offset*/ 0, /* cryptoData*/ null);
    }
    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.

  /**
   * Validates the OPUS ID Header at {@code data}'s current position, throws {@link
   * IllegalArgumentException} if the header is invalid.
   *
   * <p>{@code data}'s position does not change after returning.
   */
  private static void validateOpusIdHeader(ParsableByteArray data) {
    int currPosition = data.getPosition();
    int sampleSize = data.limit();
    checkArgument(sampleSize > 18, "ID Header has insufficient data");
    String header = data.readString(8);
    // Identification header RFC7845 Section 5.1.
    checkArgument(header.equals("OpusHead"), "ID Header missing");
    checkArgument(data.readUnsignedByte() == 1, "version number must always be 1");
    data.setPosition(currPosition);
  }
}
