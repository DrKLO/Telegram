/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor.ogg;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.EOFException;
import java.io.IOException;

/**
 * Utility methods for reading ogg streams.
 */
/* package */ final class OggUtil {

  public static final int PAGE_HEADER_SIZE = 27;

  private static final int TYPE_OGGS = Util.getIntegerCodeForString("OggS");

  /**
   * Reads an int of {@code length} bits from {@code src} starting at
   * {@code leastSignificantBitIndex}.
   *
   * @param src the {@code byte} to read from.
   * @param length the length in bits of the int to read.
   * @param leastSignificantBitIndex the index of the least significant bit of the int to read.
   * @return the int value read.
   */
  public static int readBits(byte src, int length, int leastSignificantBitIndex) {
    return (src >> leastSignificantBitIndex) & (255 >>> (8 - length));
  }

  /**
   * Skips to the next page.
   *
   * @param input The {@code ExtractorInput} to skip to the next page.
   * @throws IOException thrown if peeking/reading from the input fails.
   * @throws InterruptedException thrown if interrupted while peeking/reading from the input.
   */
  public static void skipToNextPage(ExtractorInput input)
      throws IOException, InterruptedException {

    byte[] buffer = new byte[2048];
    int peekLength = buffer.length;
    while (true) {
      if (input.getLength() != C.LENGTH_UNBOUNDED
          && input.getPosition() + peekLength > input.getLength()) {
        // Make sure to not peek beyond the end of the input.
        peekLength = (int) (input.getLength() - input.getPosition());
        if (peekLength < 4) {
          // Not found until eof.
          throw new EOFException();
        }
      }
      input.peekFully(buffer, 0, peekLength, false);
      for (int i = 0; i < peekLength - 3; i++) {
        if (buffer[i] == 'O' && buffer[i + 1] == 'g' && buffer[i + 2] == 'g'
            && buffer[i + 3] == 'S') {
          // Match! Skip to the start of the pattern.
          input.skipFully(i);
          return;
        }
      }
      // Overlap by not skipping the entire peekLength.
      input.skipFully(peekLength - 3);
    }
  }

  /**
   * Peeks an Ogg page header and stores the data in the {@code header} object passed
   * as argument.
   *
   * @param input the {@link ExtractorInput} to read from.
   * @param header the {@link PageHeader} to read from.
   * @param scratch a scratch array temporary use. Its size should be at least PAGE_HEADER_SIZE
   * @param quite if {@code true} no Exceptions are thrown but {@code false} is return if something
   *     goes wrong.
   * @return {@code true} if the read was successful. {@code false} if the end of the
   *     input was encountered having read no data.
   * @throws IOException thrown if reading data fails or the stream is invalid.
   * @throws InterruptedException thrown if thread is interrupted when reading/peeking.
   */
  public static boolean populatePageHeader(ExtractorInput input, PageHeader header,
      ParsableByteArray scratch, boolean quite) throws IOException, InterruptedException {

    scratch.reset();
    header.reset();
    boolean hasEnoughBytes = input.getLength() == C.LENGTH_UNBOUNDED
        || input.getLength() - input.getPeekPosition() >= PAGE_HEADER_SIZE;
    if (!hasEnoughBytes || !input.peekFully(scratch.data, 0, PAGE_HEADER_SIZE, true)) {
      if (quite) {
        return false;
      } else {
        throw new EOFException();
      }
    }
    if (scratch.readUnsignedInt() != TYPE_OGGS) {
      if (quite) {
        return false;
      } else {
        throw new ParserException("expected OggS capture pattern at begin of page");
      }
    }

    header.revision = scratch.readUnsignedByte();
    if (header.revision != 0x00) {
      if (quite) {
        return false;
      } else {
        throw new ParserException("unsupported bit stream revision");
      }
    }
    header.type = scratch.readUnsignedByte();

    header.granulePosition = scratch.readLittleEndianLong();
    header.streamSerialNumber = scratch.readLittleEndianUnsignedInt();
    header.pageSequenceNumber = scratch.readLittleEndianUnsignedInt();
    header.pageChecksum = scratch.readLittleEndianUnsignedInt();
    header.pageSegmentCount = scratch.readUnsignedByte();

    scratch.reset();
    // calculate total size of header including laces
    header.headerSize = PAGE_HEADER_SIZE + header.pageSegmentCount;
    input.peekFully(scratch.data, 0, header.pageSegmentCount);
    for (int i = 0; i < header.pageSegmentCount; i++) {
      header.laces[i] = scratch.readUnsignedByte();
      header.bodySize += header.laces[i];
    }
    return true;
  }

  /**
   * Calculates the size of the packet starting from {@code startSegmentIndex}.
   *
   * @param header the {@link PageHeader} with laces.
   * @param startSegmentIndex the index of the first segment of the packet.
   * @param holder a position holder to store the resulting size value.
   */
  public static void calculatePacketSize(PageHeader header, int startSegmentIndex,
      PacketInfoHolder holder) {
    holder.segmentCount = 0;
    holder.size = 0;
    while (startSegmentIndex + holder.segmentCount < header.pageSegmentCount) {
      int segmentLength = header.laces[startSegmentIndex + holder.segmentCount++];
      holder.size += segmentLength;
      if (segmentLength != 255) {
        // packets end at first lace < 255
        break;
      }
    }
  }

  /**
   * Data object to store header information. Be aware that {@code laces.length} is always 255.
   * Instead use {@code pageSegmentCount} to iterate.
   */
  public static final class PageHeader {

    public int revision;
    public int type;
    public long granulePosition;
    public long streamSerialNumber;
    public long pageSequenceNumber;
    public long pageChecksum;
    public int pageSegmentCount;
    public int headerSize;
    public int bodySize;
    public final int[] laces = new int[255];

    /**
     * Resets all primitive member fields to zero.
     */
    public void reset() {
      revision = 0;
      type = 0;
      granulePosition = 0;
      streamSerialNumber = 0;
      pageSequenceNumber = 0;
      pageChecksum = 0;
      pageSegmentCount = 0;
      headerSize = 0;
      bodySize = 0;
    }

  }

  /**
   * Holds size and number of segments of a packet.
   */
  public static class PacketInfoHolder {
    public int size;
    public int segmentCount;
  }

}
