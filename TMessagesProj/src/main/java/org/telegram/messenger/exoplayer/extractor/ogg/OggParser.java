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
import org.telegram.messenger.exoplayer.extractor.ogg.OggUtil.PacketInfoHolder;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.IOException;

/**
 * Reads OGG packets from an {@link ExtractorInput}.
 */
/* package */ final class OggParser {

  public static final int OGG_MAX_SEGMENT_SIZE = 255;

  private final OggUtil.PageHeader pageHeader = new OggUtil.PageHeader();
  private final ParsableByteArray headerArray = new ParsableByteArray(27 + 255);
  private final PacketInfoHolder holder = new PacketInfoHolder();

  private int currentSegmentIndex = -1;
  private long elapsedSamples;

  /**
   * Resets this reader.
   */
  public void reset() {
    pageHeader.reset();
    headerArray.reset();
    currentSegmentIndex = -1;
  }

  /**
   * Reads the next packet of the ogg stream. In case of an {@code IOException} the caller must make
   * sure to pass the same instance of {@code ParsableByteArray} to this method again so this reader
   * can resume properly from an error while reading a continued packet spanned across multiple
   * pages.
   *
   * @param input the {@link ExtractorInput} to read data from.
   * @param packetArray the {@link ParsableByteArray} to write the packet data into.
   * @return {@code true} if the read was successful. {@code false} if the end of the input was
   *    encountered having read no data.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from input.
   */
  public boolean readPacket(ExtractorInput input, ParsableByteArray packetArray)
      throws IOException, InterruptedException {
    Assertions.checkState(input != null && packetArray != null);

    boolean packetComplete = false;
    while (!packetComplete) {
      if (currentSegmentIndex < 0) {
        // We're at the start of a page.
        if (!OggUtil.populatePageHeader(input, pageHeader, headerArray, true)) {
          return false;
        }
        int segmentIndex = 0;
        int bytesToSkip = pageHeader.headerSize;
        if ((pageHeader.type & 0x01) == 0x01 && packetArray.limit() == 0) {
          // After seeking, the first packet may be the remainder
          // part of a continued packet which has to be discarded.
          OggUtil.calculatePacketSize(pageHeader, segmentIndex, holder);
          segmentIndex += holder.segmentCount;
          bytesToSkip += holder.size;
        }
        input.skipFully(bytesToSkip);
        currentSegmentIndex = segmentIndex;
      }

      OggUtil.calculatePacketSize(pageHeader, currentSegmentIndex, holder);
      int segmentIndex = currentSegmentIndex + holder.segmentCount;
      if (holder.size > 0) {
        input.readFully(packetArray.data, packetArray.limit(), holder.size);
        packetArray.setLimit(packetArray.limit() + holder.size);
        packetComplete = pageHeader.laces[segmentIndex - 1] != 255;
      }
      // advance now since we are sure reading didn't throw an exception
      currentSegmentIndex = segmentIndex == pageHeader.pageSegmentCount ? -1
          : segmentIndex;
    }
    return true;
  }

  /**
   * Skips to the last Ogg page in the stream and reads the header's granule field which is the
   * total number of samples per channel.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @return the total number of samples of this input.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  public long readGranuleOfLastPage(ExtractorInput input)
      throws IOException, InterruptedException {
    Assertions.checkArgument(input.getLength() != C.LENGTH_UNBOUNDED); // never read forever!
    OggUtil.skipToNextPage(input);
    pageHeader.reset();
    while ((pageHeader.type & 0x04) != 0x04 && input.getPosition() < input.getLength()) {
      OggUtil.populatePageHeader(input, pageHeader, headerArray, false);
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
    }
    return pageHeader.granulePosition;
  }

  /**
   * Skips to the position of the start of the page containing the {@code targetGranule} and
   * returns the elapsed samples which is the granule of the page previous to the target page.
   * <p>
   * Note that the position of the {@code input} must be before the start of the page previous to
   * the page containing the targetGranule to get the correct number of elapsed samples.
   * Which is in short like: {@code pos(input) <= pos(targetPage.pageSequence - 1)}.
   *
   * @param input the {@link ExtractorInput} to read from.
   * @param targetGranule the target granule (number of frames per channel).
   * @return the number of elapsed samples at the start of the target page.
   * @throws ParserException thrown if populating the page header fails.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  public long skipToPageOfGranule(ExtractorInput input, long targetGranule)
      throws IOException, InterruptedException {
    OggUtil.skipToNextPage(input);
    OggUtil.populatePageHeader(input, pageHeader, headerArray, false);
    while (pageHeader.granulePosition < targetGranule) {
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
      // Store in a member field to be able to resume after IOExceptions.
      elapsedSamples = pageHeader.granulePosition;
      // Peek next header.
      OggUtil.populatePageHeader(input, pageHeader, headerArray, false);
    }
    if (elapsedSamples == 0) {
      throw new ParserException();
    }
    input.resetPeekPosition();
    long returnValue = elapsedSamples;
    // Reset member state.
    elapsedSamples = 0;
    currentSegmentIndex = -1;
    return returnValue;
  }

  /**
   * Returns the {@link OggUtil.PageHeader} of the current page. The header might not have been
   * populated if the first packet has yet to be read.
   * <p>
   * Note that there is only a single instance of {@code OggParser.PageHeader} which is mutable.
   * The value of the fields might be changed by the reader when reading the stream advances and
   * the next page is read (which implies reading and populating the next header).
   *
   * @return the {@code PageHeader} of the current page or {@code null}.
   */
  public OggUtil.PageHeader getPageHeader() {
    return pageHeader;
  }

}
