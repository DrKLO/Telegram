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
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an MP4A-LATM byte stream carried on RTP packets, and extracts MP4A-LATM Access Units.
 *
 * <p>Refer to RFC3016 for more details. The LATM byte stream format is defined in ISO/IEC14496-3.
 */
/* package */ final class RtpMp4aReader implements RtpPayloadReader {
  private static final String TAG = "RtpMp4aReader";

  private static final String PARAMETER_MP4A_CONFIG = "config";

  private final RtpPayloadFormat payloadFormat;
  private final int numberOfSubframes;
  private @MonotonicNonNull TrackOutput trackOutput;
  private long firstReceivedTimestamp;
  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple subFrames. */
  private int fragmentedSampleSizeBytes;

  private long startTimeOffsetUs;
  private long fragmentedSampleTimeUs;

  /**
   * Creates an instance.
   *
   * @throws IllegalArgumentException If {@link RtpPayloadFormat payloadFormat} is malformed.
   */
  public RtpMp4aReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    try {
      numberOfSubframes = getNumOfSubframesFromMpeg4AudioConfig(payloadFormat.fmtpParameters);
    } catch (ParserException e) {
      throw new IllegalArgumentException(e);
    }
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
    fragmentedSampleSizeBytes = 0;
    // The start time offset must be 0 until the first seek.
    startTimeOffsetUs = 0;
    fragmentedSampleTimeUs = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
    castNonNull(trackOutput).format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    checkState(firstReceivedTimestamp == C.TIME_UNSET);
    firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    checkStateNotNull(trackOutput);

    int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
    if (fragmentedSampleSizeBytes > 0 && expectedSequenceNumber < sequenceNumber) {
      outputSampleMetadataForFragmentedPackets();
    }

    for (int subFrameIndex = 0; subFrameIndex < numberOfSubframes; subFrameIndex++) {
      int sampleLength = 0;
      // Implements PayloadLengthInfo() in ISO/IEC14496-3 Chapter 1.7.3.1, it only supports one
      // program and one layer. Each subframe starts with a variable length encoding.
      while (data.getPosition() < data.limit()) {
        int payloadMuxLength = data.readUnsignedByte();
        sampleLength += payloadMuxLength;
        if (payloadMuxLength != 0xff) {
          break;
        }
      }

      trackOutput.sampleData(data, sampleLength);
      fragmentedSampleSizeBytes += sampleLength;
    }
    fragmentedSampleTimeUs =
        toSampleTimeUs(
            startTimeOffsetUs, timestamp, firstReceivedTimestamp, payloadFormat.clockRate);
    if (rtpMarker) {
      outputSampleMetadataForFragmentedPackets();
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
   * Parses an MPEG-4 Audio Stream Mux configuration, as defined in ISO/IEC14496-3.
   *
   * <p>FMTP attribute {@code config} contains the MPEG-4 Audio Stream Mux configuration.
   *
   * @param fmtpAttributes The format parameters, mapped from the SDP FMTP attribute.
   * @return The number of subframes that is carried in each RTP packet.
   */
  private static int getNumOfSubframesFromMpeg4AudioConfig(
      ImmutableMap<String, String> fmtpAttributes) throws ParserException {
    @Nullable String configInput = fmtpAttributes.get(PARAMETER_MP4A_CONFIG);
    int numberOfSubframes = 0;
    if (configInput != null && configInput.length() % 2 == 0) {
      byte[] configBuffer = Util.getBytesFromHexString(configInput);
      ParsableBitArray scratchBits = new ParsableBitArray(configBuffer);
      int audioMuxVersion = scratchBits.readBits(1);
      if (audioMuxVersion == 0) {
        checkArgument(scratchBits.readBits(1) == 1, "Only supports allStreamsSameTimeFraming.");
        numberOfSubframes = scratchBits.readBits(6);
        checkArgument(scratchBits.readBits(4) == 0, "Only suppors one program.");
        checkArgument(scratchBits.readBits(3) == 0, "Only suppors one layer.");
      } else {
        throw ParserException.createForMalformedDataOfUnknownType(
            "unsupported audio mux version: " + audioMuxVersion, null);
      }
    }
    // ISO/IEC14496-3 Chapter 1.7.3.2.3: The minimum value is 0 indicating 1 subframe.
    return numberOfSubframes + 1;
  }

  /**
   * Outputs sample metadata.
   *
   * <p>Call this method only after receiving the end of an MPEG4 partition.
   */
  private void outputSampleMetadataForFragmentedPackets() {
    checkNotNull(trackOutput)
        .sampleMetadata(
            fragmentedSampleTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            fragmentedSampleSizeBytes,
            /* offset= */ 0,
            /* cryptoData= */ null);
    fragmentedSampleSizeBytes = 0;
    fragmentedSampleTimeUs = C.TIME_UNSET;
  }
}
