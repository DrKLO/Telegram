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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.math.IntMath;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;

/**
 * Represents the header and the payload of an RTP packet.
 *
 * <p>Not supported parsing at the moment: header extension and CSRC.
 *
 * <p>Structure of an RTP header (RFC3550, Section 5.1).
 *
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                             ....                              |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * | Profile-specific extension ID |   Extension header length     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       Extension header                        |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    3                   2                   1
 *  1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0
 * </pre>
 */
public final class RtpPacket {

  /** Builder class for an {@link RtpPacket} */
  public static final class Builder {
    private boolean padding;
    private boolean marker;
    private byte payloadType;
    private int sequenceNumber;
    private long timestamp;
    private int ssrc;
    private byte[] csrc = EMPTY;
    private byte[] payloadData = EMPTY;

    /** Sets the {@link RtpPacket#padding}. The default is false. */
    @CanIgnoreReturnValue
    public Builder setPadding(boolean padding) {
      this.padding = padding;
      return this;
    }

    /** Sets {@link RtpPacket#marker}. The default is false. */
    @CanIgnoreReturnValue
    public Builder setMarker(boolean marker) {
      this.marker = marker;
      return this;
    }

    /** Sets {@link RtpPacket#payloadType}. The default is 0. */
    @CanIgnoreReturnValue
    public Builder setPayloadType(byte payloadType) {
      this.payloadType = payloadType;
      return this;
    }

    /** Sets {@link RtpPacket#sequenceNumber}. The default is 0. */
    @CanIgnoreReturnValue
    public Builder setSequenceNumber(int sequenceNumber) {
      checkArgument(sequenceNumber >= MIN_SEQUENCE_NUMBER && sequenceNumber <= MAX_SEQUENCE_NUMBER);
      this.sequenceNumber = sequenceNumber & 0xFFFF;
      return this;
    }

    /** Sets {@link RtpPacket#timestamp}. The default is 0. */
    @CanIgnoreReturnValue
    public Builder setTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /** Sets {@link RtpPacket#ssrc}. The default is 0. */
    @CanIgnoreReturnValue
    public Builder setSsrc(int ssrc) {
      this.ssrc = ssrc;
      return this;
    }

    /** Sets {@link RtpPacket#csrc}. The default is an empty byte array. */
    @CanIgnoreReturnValue
    public Builder setCsrc(byte[] csrc) {
      checkNotNull(csrc);
      this.csrc = csrc;
      return this;
    }

    /** Sets {@link RtpPacket#payloadData}. The default is an empty byte array. */
    @CanIgnoreReturnValue
    public Builder setPayloadData(byte[] payloadData) {
      checkNotNull(payloadData);
      this.payloadData = payloadData;
      return this;
    }

    /** Builds the {@link RtpPacket}. */
    public RtpPacket build() {
      return new RtpPacket(this);
    }
  }

  public static final int RTP_VERSION = 2;

  public static final int MAX_SIZE = 65507;
  public static final int MIN_HEADER_SIZE = 12;
  public static final int MIN_SEQUENCE_NUMBER = 0;
  public static final int MAX_SEQUENCE_NUMBER = 0xFFFF;
  public static final int CSRC_SIZE = 4;

  /** Returns the next sequence number of the {@code sequenceNumber}. */
  public static int getNextSequenceNumber(int sequenceNumber) {
    return IntMath.mod(sequenceNumber + 1, MAX_SEQUENCE_NUMBER + 1);
  }

  /** Returns the previous sequence number from the {@code sequenceNumber}. */
  public static int getPreviousSequenceNumber(int sequenceNumber) {
    return IntMath.mod(sequenceNumber - 1, MAX_SEQUENCE_NUMBER + 1);
  }

  private static final byte[] EMPTY = new byte[0];

  /** The RTP version field (Word 0, bits 0-1), should always be 2. */
  public final byte version = RTP_VERSION;
  /** The RTP padding bit (Word 0, bit 2). */
  public final boolean padding;
  /** The RTP extension bit (Word 0, bit 3). */
  public final boolean extension;
  /** The RTP CSRC count field (Word 0, bits 4-7). */
  public final byte csrcCount;

  /** The RTP marker bit (Word 0, bit 8). */
  public final boolean marker;
  /** The RTP CSRC count field (Word 0, bits 9-15). */
  public final byte payloadType;

  /** The RTP sequence number field (Word 0, bits 16-31). */
  public final int sequenceNumber;

  /** The RTP timestamp field (Word 1). */
  public final long timestamp;

  /** The RTP SSRC field (Word 2). */
  public final int ssrc;

  /** The RTP CSRC fields (Optional, up to 15 items). */
  public final byte[] csrc;

  public final byte[] payloadData;

  /**
   * Creates an {@link RtpPacket} from a {@link ParsableByteArray}.
   *
   * @param packetBuffer The buffer that contains the RTP packet data.
   * @return The built {@link RtpPacket}.
   */
  @Nullable
  public static RtpPacket parse(ParsableByteArray packetBuffer) {
    if (packetBuffer.bytesLeft() < MIN_HEADER_SIZE) {
      return null;
    }

    // Word 0.
    int firstByte = packetBuffer.readUnsignedByte();
    byte version = (byte) (firstByte >> 6);
    boolean padding = ((firstByte >> 5) & 0x1) == 1;
    byte csrcCount = (byte) (firstByte & 0xF);

    if (version != RTP_VERSION) {
      return null;
    }

    int secondByte = packetBuffer.readUnsignedByte();
    boolean marker = ((secondByte >> 7) & 0x1) == 1;
    byte payloadType = (byte) (secondByte & 0x7F);

    int sequenceNumber = packetBuffer.readUnsignedShort();

    // Word 1.
    long timestamp = packetBuffer.readUnsignedInt();

    // Word 2.
    int ssrc = packetBuffer.readInt();

    // CSRC.
    byte[] csrc;
    if (csrcCount > 0) {
      csrc = new byte[csrcCount * CSRC_SIZE];
      for (int i = 0; i < csrcCount; i++) {
        packetBuffer.readBytes(csrc, i * CSRC_SIZE, CSRC_SIZE);
      }
    } else {
      csrc = EMPTY;
    }

    // Everything else will be RTP payload.
    byte[] payloadData = new byte[packetBuffer.bytesLeft()];
    packetBuffer.readBytes(payloadData, 0, packetBuffer.bytesLeft());

    Builder builder = new Builder();
    return builder
        .setPadding(padding)
        .setMarker(marker)
        .setPayloadType(payloadType)
        .setSequenceNumber(sequenceNumber)
        .setTimestamp(timestamp)
        .setSsrc(ssrc)
        .setCsrc(csrc)
        .setPayloadData(payloadData)
        .build();
  }

  /**
   * Creates an {@link RtpPacket} from a byte array.
   *
   * @param buffer The buffer that contains the RTP packet data.
   * @param length The length of the RTP packet.
   * @return The built {@link RtpPacket}.
   */
  @Nullable
  public static RtpPacket parse(byte[] buffer, int length) {
    return parse(new ParsableByteArray(buffer, length));
  }

  private RtpPacket(Builder builder) {
    this.padding = builder.padding;
    this.extension = false;
    this.marker = builder.marker;
    this.payloadType = builder.payloadType;
    this.sequenceNumber = builder.sequenceNumber;
    this.timestamp = builder.timestamp;
    this.ssrc = builder.ssrc;
    this.csrc = builder.csrc;
    this.csrcCount = (byte) (this.csrc.length / CSRC_SIZE);
    this.payloadData = builder.payloadData;
  }

  /**
   * Writes the data in an RTP packet to a target buffer.
   *
   * <p>The size of the target buffer and the length argument should be big enough so that the
   * entire RTP packet could fit. That is, if there is not enough space to store the entire RTP
   * packet, no bytes will be written. The maximum size of an RTP packet is defined as {@link
   * RtpPacket#MAX_SIZE}.
   *
   * @param target A target byte buffer to which the packet data is copied.
   * @param offset The offset into the target array at which to write.
   * @param length The maximum number of bytes that can be written.
   * @return The number of bytes written, or {@link C#LENGTH_UNSET} if there is not enough space to
   *     write the packet.
   */
  public int writeToBuffer(byte[] target, int offset, int length) {
    int packetLength = MIN_HEADER_SIZE + (CSRC_SIZE * csrcCount) + payloadData.length;
    if (length < packetLength || target.length - offset < packetLength) {
      return C.LENGTH_UNSET;
    }

    ByteBuffer buffer = ByteBuffer.wrap(target, offset, length);
    byte firstByte =
        (byte)
            ((version << 6)
                | ((padding ? 1 : 0) << 5)
                | ((extension ? 1 : 0) << 4)
                | (csrcCount & 0xF));
    byte secondByte = (byte) (((marker ? 1 : 0) << 7) | (payloadType & 0x7F));
    buffer
        .put(firstByte)
        .put(secondByte)
        .putShort((short) sequenceNumber)
        .putInt((int) timestamp)
        .putInt(ssrc)
        .put(csrc)
        .put(payloadData);
    return packetLength;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RtpPacket rtpPacket = (RtpPacket) o;
    return payloadType == rtpPacket.payloadType
        && sequenceNumber == rtpPacket.sequenceNumber
        && marker == rtpPacket.marker
        && timestamp == rtpPacket.timestamp
        && ssrc == rtpPacket.ssrc;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + payloadType;
    result = 31 * result + sequenceNumber;
    result = 31 * result + (marker ? 1 : 0);
    result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    result = 31 * result + ssrc;
    return result;
  }

  @Override
  public String toString() {
    return Util.formatInvariant(
        "RtpPacket(payloadType=%d, seq=%d, timestamp=%d, ssrc=%x, marker=%b)",
        payloadType, sequenceNumber, timestamp, ssrc, marker);
  }
}
