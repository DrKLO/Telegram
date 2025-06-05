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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses a VP9 byte stream carried on RTP packets, and extracts VP9 Access Units. Refer to <a
 * href=https://datatracker.ietf.org/doc/html/draft-ietf-payload-vp9>this draft RFC</a> for more
 * details.
 */
/* package */ final class RtpVp9Reader implements RtpPayloadReader {

  private static final String TAG = "RtpVp9Reader";

  private static final int MEDIA_CLOCK_FREQUENCY = 90_000;
  private static final int SCALABILITY_STRUCTURE_SIZE = 4;

  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;

  /**
   * First received RTP timestamp. All RTP timestamps are dimension-less, the time base is defined
   * by {@link #MEDIA_CLOCK_FREQUENCY}.
   */
  private long firstReceivedTimestamp;

  private long startTimeOffsetUs;
  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple RTP packets. */
  private int fragmentedSampleSizeBytes;

  private long fragmentedSampleTimeUs;

  private int width;
  private int height;
  /**
   * Whether the first packet of a VP9 frame is received, it mark the start of a VP9 partition. A
   * VP9 frame can be split into multiple RTP packets.
   */
  private boolean gotFirstPacketOfVp9Frame;

  private boolean reportedOutputFormat;
  private boolean isKeyFrame;

  /** Creates an instance. */
  public RtpVp9Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    fragmentedSampleSizeBytes = C.LENGTH_UNSET;
    fragmentedSampleTimeUs = C.TIME_UNSET;
    // The start time offset must be 0 until the first seek.
    startTimeOffsetUs = 0;
    previousSequenceNumber = C.INDEX_UNSET;
    width = C.LENGTH_UNSET;
    height = C.LENGTH_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
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
    checkStateNotNull(trackOutput);

    if (validateVp9Descriptor(data, sequenceNumber)) {
      if (fragmentedSampleSizeBytes == C.LENGTH_UNSET && gotFirstPacketOfVp9Frame) {
        // Parsing the frame_type in VP9 uncompressed header, 0 - key frame, 1 - inter frame.
        // Refer to VP9 Bitstream superframe and uncompressed header, Section 4.1.
        isKeyFrame = (data.peekUnsignedByte() & 0x04) == 0;
      }

      if (!reportedOutputFormat && width != C.LENGTH_UNSET && height != C.LENGTH_UNSET) {
        if (width != payloadFormat.format.width || height != payloadFormat.format.height) {
          trackOutput.format(
              payloadFormat.format.buildUpon().setWidth(width).setHeight(height).build());
        }
        reportedOutputFormat = true;
      }

      int currentFragmentSizeBytes = data.bytesLeft();
      // Write the video sample.
      trackOutput.sampleData(data, currentFragmentSizeBytes);
      if (fragmentedSampleSizeBytes == C.LENGTH_UNSET) {
        fragmentedSampleSizeBytes = currentFragmentSizeBytes;
      } else {
        fragmentedSampleSizeBytes += currentFragmentSizeBytes;
      }
      fragmentedSampleTimeUs =
          toSampleTimeUs(
              startTimeOffsetUs, timestamp, firstReceivedTimestamp, MEDIA_CLOCK_FREQUENCY);

      if (rtpMarker) {
        outputSampleMetadataForFragmentedPackets();
      }
      previousSequenceNumber = sequenceNumber;
    }
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    fragmentedSampleSizeBytes = C.LENGTH_UNSET;
    startTimeOffsetUs = timeUs;
  }

  // Internal methods.
  /**
   * Returns {@code true} and sets the {@link ParsableByteArray#getPosition() payload.position} to
   * the end of the descriptor, if a valid VP9 descriptor is present.
   */
  private boolean validateVp9Descriptor(ParsableByteArray payload, int packetSequenceNumber) {
    // VP9 Payload Descriptor, Section 4.2
    //         0 1 2 3 4 5 6 7
    //        +-+-+-+-+-+-+-+-+
    //        |I|P|L|F|B|E|V|Z| (REQUIRED)
    //        +-+-+-+-+-+-+-+-+
    //   I:   |M| PICTURE ID  | (RECOMMENDED)
    //        +-+-+-+-+-+-+-+-+
    //   M:   | EXTENDED PID  | (RECOMMENDED)
    //        +-+-+-+-+-+-+-+-+
    //   L:   | TID |U| SID |D| (Conditionally RECOMMENDED)
    //        +-+-+-+-+-+-+-+-+
    //        |   TL0PICIDX   | (Conditionally REQUIRED)
    //        +-+-+-+-+-+-+-+-+
    //   V:   | SS            |
    //        | ..            |
    //        +-+-+-+-+-+-+-+-+

    int header = payload.readUnsignedByte();
    if ((header & 0x08) == 0x08) {
      if (gotFirstPacketOfVp9Frame && fragmentedSampleSizeBytes > 0) {
        // Received new VP9 fragment, output data of previous fragment to decoder.
        outputSampleMetadataForFragmentedPackets();
      }
      gotFirstPacketOfVp9Frame = true;
    } else if (gotFirstPacketOfVp9Frame) {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (packetSequenceNumber < expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, packetSequenceNumber));
        return false;
      }
    } else {
      Log.w(
          TAG,
          "First payload octet of the RTP packet is not the beginning of a new VP9 partition,"
              + " Dropping current packet.");
      return false;
    }

    // Check if optional I header is present.
    if ((header & 0x80) != 0) {
      int optionalHeader = payload.readUnsignedByte();
      // Check M for 15 bits PictureID.
      if ((optionalHeader & 0x80) != 0) {
        if (payload.bytesLeft() < 1) {
          return false;
        }
      }
    }

    // Flexible-mode is not implemented.
    checkArgument((header & 0x10) == 0, "VP9 flexible mode is not supported.");

    // Check if the optional L header is present.
    if ((header & 0x20) != 0) {
      payload.skipBytes(1);
      if (payload.bytesLeft() < 1) {
        return false;
      }
      // Check if TL0PICIDX header present (non-flexible mode).
      if ((header & 0x10) == 0) {
        payload.skipBytes(1);
      }
    }

    // Check if the optional V header is present, Refer to RFC Section 4.2.1.
    if ((header & 0x02) != 0) {
      int scalabilityStructure = payload.readUnsignedByte();
      int spatialLayersCount = (scalabilityStructure >> 5) & 0x7;

      // Check Y bit.
      if ((scalabilityStructure & 0x10) != 0) {
        int scalabilityStructureCount = spatialLayersCount + 1;
        if (payload.bytesLeft() < scalabilityStructureCount * SCALABILITY_STRUCTURE_SIZE) {
          return false;
        }
        for (int index = 0; index < scalabilityStructureCount; index++) {
          width = payload.readUnsignedShort();
          height = payload.readUnsignedShort();
        }
      }

      // Checks G bit, skips all additional temporal layers.
      if ((scalabilityStructure & 0x08) != 0) {
        // Reads N_G.
        int numOfPicInPictureGroup = payload.readUnsignedByte();
        if (payload.bytesLeft() < numOfPicInPictureGroup) {
          return false;
        }

        for (int picIndex = 0; picIndex < numOfPicInPictureGroup; picIndex++) {
          int picture = payload.readUnsignedShort();
          int referenceIndices = (picture & 0x0C) >> 2;
          if (payload.bytesLeft() < referenceIndices) {
            return false;
          }
          // Ignore Reference indices.
          payload.skipBytes(referenceIndices);
        }
      }
    }
    return true;
  }

  /**
   * Outputs sample metadata of the received fragmented packets.
   *
   * <p>Call this method only after receiving an end of a VP9 partition.
   */
  private void outputSampleMetadataForFragmentedPackets() {
    checkNotNull(trackOutput)
        .sampleMetadata(
            fragmentedSampleTimeUs,
            isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0,
            fragmentedSampleSizeBytes,
            /* offset= */ 0,
            /* cryptoData= */ null);
    fragmentedSampleSizeBytes = C.LENGTH_UNSET;
    fragmentedSampleTimeUs = C.TIME_UNSET;
    gotFirstPacketOfVp9Frame = false;
  }
}
