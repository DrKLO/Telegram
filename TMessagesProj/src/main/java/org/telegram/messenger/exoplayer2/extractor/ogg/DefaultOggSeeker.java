/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.extractor.ogg;

import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.EOFException;
import java.io.IOException;

/**
 * Used to seek in an Ogg stream.
 */
/* package */ final class DefaultOggSeeker implements OggSeeker {

  //@VisibleForTesting
  public static final int MATCH_RANGE = 72000;
  //@VisibleForTesting
  public static final int MATCH_BYTE_RANGE = 100000;
  private static final int DEFAULT_OFFSET = 30000;

  private static final int STATE_SEEK_TO_END = 0;
  private static final int STATE_READ_LAST_PAGE = 1;
  private static final int STATE_SEEK = 2;
  private static final int STATE_IDLE = 3;

  private final OggPageHeader pageHeader = new OggPageHeader();
  private final long startPosition;
  private final long endPosition;
  private final StreamReader streamReader;

  private int state;
  private long totalGranules;
  private long positionBeforeSeekToEnd;
  private long targetGranule;

  private long start;
  private long end;
  private long startGranule;
  private long endGranule;

  /**
   * Constructs an OggSeeker.
   * @param startPosition Start position of the payload (inclusive).
   * @param endPosition End position of the payload (exclusive).
   * @param streamReader StreamReader instance which owns this OggSeeker
   * @param firstPayloadPageSize The total size of the first payload page, in bytes.
   * @param firstPayloadPageGranulePosition The granule position of the first payload page.
   */
  public DefaultOggSeeker(long startPosition, long endPosition, StreamReader streamReader,
      int firstPayloadPageSize, long firstPayloadPageGranulePosition) {
    Assertions.checkArgument(startPosition >= 0 && endPosition > startPosition);
    this.streamReader = streamReader;
    this.startPosition = startPosition;
    this.endPosition = endPosition;
    if (firstPayloadPageSize == endPosition - startPosition) {
      totalGranules = firstPayloadPageGranulePosition;
      state = STATE_IDLE;
    } else {
      state = STATE_SEEK_TO_END;
    }
  }

  @Override
  public long read(ExtractorInput input) throws IOException, InterruptedException {
    switch (state) {
      case STATE_IDLE:
        return -1;
      case STATE_SEEK_TO_END:
        positionBeforeSeekToEnd = input.getPosition();
        state = STATE_READ_LAST_PAGE;
        // Seek to the end just before the last page of stream to get the duration.
        long lastPageSearchPosition = endPosition - OggPageHeader.MAX_PAGE_SIZE;
        if (lastPageSearchPosition > positionBeforeSeekToEnd) {
          return lastPageSearchPosition;
        }
        // Fall through.
      case STATE_READ_LAST_PAGE:
        totalGranules = readGranuleOfLastPage(input);
        state = STATE_IDLE;
        return positionBeforeSeekToEnd;
      case STATE_SEEK:
        long currentGranule;
        if (targetGranule == 0) {
          currentGranule = 0;
        } else {
          long position = getNextSeekPosition(targetGranule, input);
          if (position >= 0) {
            return position;
          }
          currentGranule = skipToPageOfGranule(input, targetGranule, -(position + 2));
        }
        state = STATE_IDLE;
        return -(currentGranule + 2);
      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  @Override
  public long startSeek(long timeUs) {
    Assertions.checkArgument(state == STATE_IDLE || state == STATE_SEEK);
    targetGranule = timeUs == 0 ? 0 : streamReader.convertTimeToGranule(timeUs);
    state = STATE_SEEK;
    resetSeeking();
    return targetGranule;
  }

  @Override
  public OggSeekMap createSeekMap() {
    return totalGranules != 0 ? new OggSeekMap() : null;
  }

  //@VisibleForTesting
  public void resetSeeking() {
    start = startPosition;
    end = endPosition;
    startGranule = 0;
    endGranule = totalGranules;
  }

  /**
   * Returns a position converging to the {@code targetGranule} to which the {@link ExtractorInput}
   * has to seek and then be passed for another call until a negative number is returned. If a
   * negative number is returned the input is at a position which is before the target page and at
   * which it is sensible to just skip pages to the target granule and pre-roll instead of doing
   * another seek request.
   *
   * @param targetGranule the target granule position to seek to.
   * @param input the {@link ExtractorInput} to read from.
   * @return the position to seek the {@link ExtractorInput} to for a next call or
   *     -(currentGranule + 2) if it's close enough to skip to the target page.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  //@VisibleForTesting
  public long getNextSeekPosition(long targetGranule, ExtractorInput input)
      throws IOException, InterruptedException {
    if (start == end) {
      return -(startGranule + 2);
    }

    long initialPosition = input.getPosition();
    if (!skipToNextPage(input, end)) {
      if (start == initialPosition) {
        throw new IOException("No ogg page can be found.");
      }
      return start;
    }

    pageHeader.populate(input, false);
    input.resetPeekPosition();

    long granuleDistance = targetGranule - pageHeader.granulePosition;
    int pageSize = pageHeader.headerSize + pageHeader.bodySize;
    if (granuleDistance < 0 || granuleDistance > MATCH_RANGE) {
      if (granuleDistance < 0) {
        end = initialPosition;
        endGranule = pageHeader.granulePosition;
      } else {
        start = input.getPosition() + pageSize;
        startGranule = pageHeader.granulePosition;
        if (end - start + pageSize < MATCH_BYTE_RANGE) {
          input.skipFully(pageSize);
          return -(startGranule + 2);
        }
      }

      if (end - start < MATCH_BYTE_RANGE) {
        end = start;
        return start;
      }

      long offset = pageSize * (granuleDistance <= 0 ? 2 : 1);
      long nextPosition = input.getPosition() - offset
          + (granuleDistance * (end - start) / (endGranule - startGranule));

      nextPosition = Math.max(nextPosition, start);
      nextPosition = Math.min(nextPosition, end - 1);
      return nextPosition;
    }

    // position accepted (before target granule and within MATCH_RANGE)
    input.skipFully(pageSize);
    return -(pageHeader.granulePosition + 2);
  }

  private long getEstimatedPosition(long position, long granuleDistance, long offset) {
    position += (granuleDistance * (endPosition - startPosition) / totalGranules) - offset;
    if (position < startPosition) {
      position = startPosition;
    }
    if (position >= endPosition) {
      position = endPosition - 1;
    }
    return position;
  }

  private class OggSeekMap implements SeekMap {

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public long getPosition(long timeUs) {
      if (timeUs == 0) {
        return startPosition;
      }
      long granule = streamReader.convertTimeToGranule(timeUs);
      return getEstimatedPosition(startPosition, granule, DEFAULT_OFFSET);
    }

    @Override
    public long getDurationUs() {
      return streamReader.convertGranuleToTime(totalGranules);
    }

  }

  /**
   * Skips to the next page.
   *
   * @param input The {@code ExtractorInput} to skip to the next page.
   * @throws IOException thrown if peeking/reading from the input fails.
   * @throws InterruptedException thrown if interrupted while peeking/reading from the input.
   * @throws EOFException if the next page can't be found before the end of the input.
   */
  //@VisibleForTesting
  void skipToNextPage(ExtractorInput input) throws IOException, InterruptedException {
    if (!skipToNextPage(input, endPosition)) {
      // Not found until eof.
      throw new EOFException();
    }
  }

  /**
   * Skips to the next page. Searches for the next page header.
   *
   * @param input The {@code ExtractorInput} to skip to the next page.
   * @param until Searches until this position.
   * @return true if the next page is found.
   * @throws IOException thrown if peeking/reading from the input fails.
   * @throws InterruptedException thrown if interrupted while peeking/reading from the input.
   */
  //@VisibleForTesting
  boolean skipToNextPage(ExtractorInput input, long until)
      throws IOException, InterruptedException {
    until = Math.min(until + 3, endPosition);
    byte[] buffer = new byte[2048];
    int peekLength = buffer.length;
    while (true) {
      if (input.getPosition() + peekLength > until) {
        // Make sure to not peek beyond the end of the input.
        peekLength = (int) (until - input.getPosition());
        if (peekLength < 4) {
          // Not found until end.
          return false;
        }
      }
      input.peekFully(buffer, 0, peekLength, false);
      for (int i = 0; i < peekLength - 3; i++) {
        if (buffer[i] == 'O' && buffer[i + 1] == 'g' && buffer[i + 2] == 'g'
            && buffer[i + 3] == 'S') {
          // Match! Skip to the start of the pattern.
          input.skipFully(i);
          return true;
        }
      }
      // Overlap by not skipping the entire peekLength.
      input.skipFully(peekLength - 3);
    }
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
  //@VisibleForTesting
  long readGranuleOfLastPage(ExtractorInput input)
      throws IOException, InterruptedException {
    skipToNextPage(input);
    pageHeader.reset();
    while ((pageHeader.type & 0x04) != 0x04 && input.getPosition() < endPosition) {
      pageHeader.populate(input, false);
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
    }
    return pageHeader.granulePosition;
  }

  /**
   * Skips to the position of the start of the page containing the {@code targetGranule} and
   * returns the granule of the page previous to the target page.
   *
   * @param input the {@link ExtractorInput} to read from.
   * @param targetGranule the target granule.
   * @param currentGranule the current granule or -1 if it's unknown.
   * @return the granule of the prior page or the {@code currentGranule} if there isn't a prior
   *     page.
   * @throws ParserException thrown if populating the page header fails.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  //@VisibleForTesting
  long skipToPageOfGranule(ExtractorInput input, long targetGranule, long currentGranule)
      throws IOException, InterruptedException {
    pageHeader.populate(input, false);
    while (pageHeader.granulePosition < targetGranule) {
      input.skipFully(pageHeader.headerSize + pageHeader.bodySize);
      // Store in a member field to be able to resume after IOExceptions.
      currentGranule = pageHeader.granulePosition;
      // Peek next header.
      pageHeader.populate(input, false);
    }
    input.resetPeekPosition();
    return currentGranule;
  }

}
