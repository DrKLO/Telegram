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
 * Parses a H263 byte stream carried on RTP packets, and extracts H263 frames as defined in RFC4629.
 */
/* package */ final class RtpH263Reader implements RtpPayloadReader {
  private static final String TAG = "RtpH263Reader";

  private static final int MEDIA_CLOCK_FREQUENCY = 90_000;

  /** I-frame VOP unit type. */
  private static final int I_VOP = 0;

  /** Picture start code, P=1, V=0, PLEN=0. Refer to RFC4629 Section 6.1. */
  private static final int PICTURE_START_CODE = 128;

  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;

  /**
   * First received RTP timestamp. All RTP timestamps are dimension-less, the time base is defined
   * by {@link #MEDIA_CLOCK_FREQUENCY}.
   */
  private long firstReceivedTimestamp;

  /** The combined size of a sample that is fragmented into multiple RTP packets. */
  private int fragmentedSampleSizeBytes;

  private int previousSequenceNumber;

  private int width;
  private int height;
  private boolean isKeyFrame;
  private boolean isOutputFormatSet;
  private long startTimeOffsetUs;
  private long fragmentedSampleTimeUs;
  /**
   * Whether the first packet of a H263 frame is received, it mark the start of a H263 partition. A
   * H263 frame can be split into multiple RTP packets.
   */
  private boolean gotFirstPacketOfH263Frame;

  /** Creates an instance. */
  public RtpH263Reader(RtpPayloadFormat payloadFormat) {
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
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    checkState(firstReceivedTimestamp == C.TIME_UNSET);
    firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    checkStateNotNull(trackOutput);

    // H263 Header Payload Header, RFC4629 Section 5.1.
    //    0                   1
    //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //    |   RR    |P|V|   PLEN    |PEBIT|
    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    int currentPosition = data.getPosition();
    int header = data.readUnsignedShort();
    boolean pBitIsSet = (header & 0x400) > 0;

    // Check if optional V (Video Redundancy Coding), PLEN or PEBIT is present, RFC4629 Section 5.1.
    if ((header & 0x200) != 0 || (header & 0x1F8) != 0 || (header & 0x7) != 0) {
      Log.w(
          TAG,
          "Dropping packet: video reduncancy coding is not supported, packet header VRC, or PLEN or"
              + " PEBIT is non-zero");
      return;
    }

    if (pBitIsSet) {
      if (gotFirstPacketOfH263Frame && fragmentedSampleSizeBytes > 0) {
        // Received new H263 fragment, output data of previous fragment to decoder.
        outputSampleMetadataForFragmentedPackets();
      }
      gotFirstPacketOfH263Frame = true;

      int payloadStartCode = data.peekUnsignedByte() & 0xFC;
      // Packets that begin with a Picture Start Code(100000). Refer RFC4629 Section 6.1.
      if (payloadStartCode < PICTURE_START_CODE) {
        Log.w(TAG, "Picture start Code (PSC) missing, dropping packet.");
        return;
      }
      // Setting first two bytes of the start code. Refer RFC4629 Section 6.1.1.
      data.getData()[currentPosition] = 0;
      data.getData()[currentPosition + 1] = 0;
      data.setPosition(currentPosition);
    } else if (gotFirstPacketOfH263Frame) {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber < expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, sequenceNumber));
        return;
      }
    } else {
      Log.w(
          TAG,
          "First payload octet of the H263 packet is not the beginning of a new H263 partition,"
              + " Dropping current packet.");
      return;
    }

    if (fragmentedSampleSizeBytes == 0) {
      parseVopHeader(data, isOutputFormatSet);
      if (!isOutputFormatSet && isKeyFrame) {
        if (width != payloadFormat.format.width || height != payloadFormat.format.height) {
          trackOutput.format(
              payloadFormat.format.buildUpon().setWidth(width).setHeight(height).build());
        }
        isOutputFormatSet = true;
      }
    }
    int fragmentSize = data.bytesLeft();
    // Write the video sample.
    trackOutput.sampleData(data, fragmentSize);
    fragmentedSampleSizeBytes += fragmentSize;
    fragmentedSampleTimeUs =
        toSampleTimeUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp, MEDIA_CLOCK_FREQUENCY);

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

  /**
   * Parses and set VOP Coding type and resolution. The {@linkplain ParsableByteArray#getPosition()
   * position} is preserved.
   */
  private void parseVopHeader(ParsableByteArray data, boolean gotResolution) {
    // Picture Segment Packets (RFC4629 Section 6.1).
    // Search for SHORT_VIDEO_START_MARKER (0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 0 0 0).
    int currentPosition = data.getPosition();

    /*
     * Parse short video header.
     *
     * These values are taken from <a
     * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codecs/m4v_h263/dec/src/mp4def.h;l=115
     * >Android's software H263 decoder</a>.
     */
    long shortVideoHeader = data.readUnsignedInt();
    if (((shortVideoHeader >> 10) & 0x3F) == 0x20) {
      int header = data.peekUnsignedByte();
      int vopType = ((header >> 1) & 0x1);
      if (!gotResolution && vopType == I_VOP) {
        /*
         * Parse resolution from source format.
         *
         * These values are taken from <a
         * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codecs/m4v_h263/dec/src/vop.cpp;l=1126
         * >Android's software H263 decoder</a>.
         */
        int sourceFormat = ((header >> 2) & 0x07);
        if (sourceFormat == 1) {
          width = 128;
          height = 96;
        } else {
          width = 176 << (sourceFormat - 2);
          height = 144 << (sourceFormat - 2);
        }
      }
      data.setPosition(currentPosition);
      isKeyFrame = vopType == I_VOP;
      return;
    }
    data.setPosition(currentPosition);
    isKeyFrame = false;
  }

  /**
   * Outputs sample metadata of the received fragmented packets.
   *
   * <p>Call this method only after receiving an end of a H263 partition.
   */
  private void outputSampleMetadataForFragmentedPackets() {
    checkNotNull(trackOutput)
        .sampleMetadata(
            fragmentedSampleTimeUs,
            isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0,
            fragmentedSampleSizeBytes,
            /* offset= */ 0,
            /* cryptoData= */ null);
    fragmentedSampleSizeBytes = 0;
    fragmentedSampleTimeUs = C.TIME_UNSET;
    isKeyFrame = false;
    gotFirstPacketOfH263Frame = false;
  }
}
