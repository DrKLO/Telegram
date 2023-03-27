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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.android.exoplayer2.extractor.ExtractorUtil.peekFullyQuietly;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/** Data object to store header information. */
/* package */ final class OggPageHeader {

  public static final int EMPTY_PAGE_HEADER_SIZE = 27;
  public static final int MAX_SEGMENT_COUNT = 255;
  public static final int MAX_PAGE_PAYLOAD = 255 * 255;
  public static final int MAX_PAGE_SIZE =
      EMPTY_PAGE_HEADER_SIZE + MAX_SEGMENT_COUNT + MAX_PAGE_PAYLOAD;

  private static final int CAPTURE_PATTERN = 0x4f676753; // OggS
  private static final int CAPTURE_PATTERN_SIZE = 4;

  public int revision;
  public int type;
  /**
   * The absolute granule position of the page. This is the total number of samples from the start
   * of the file up to the <em>end</em> of the page. Samples partially in the page that continue on
   * the next page do not count.
   */
  public long granulePosition;

  public long streamSerialNumber;
  public long pageSequenceNumber;
  public long pageChecksum;
  public int pageSegmentCount;
  public int headerSize;
  public int bodySize;
  /**
   * Be aware that {@code laces.length} is always {@link #MAX_SEGMENT_COUNT}. Instead use {@link
   * #pageSegmentCount} to iterate.
   */
  public final int[] laces = new int[MAX_SEGMENT_COUNT];

  private final ParsableByteArray scratch = new ParsableByteArray(MAX_SEGMENT_COUNT);

  /** Resets all primitive member fields to zero. */
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

  /**
   * Advances through {@code input} looking for the start of the next Ogg page.
   *
   * <p>Equivalent to {@link #skipToNextPage(ExtractorInput, long) skipToNextPage(input, /* limit=
   * *\/ C.POSITION_UNSET)}.
   */
  public boolean skipToNextPage(ExtractorInput input) throws IOException {
    return skipToNextPage(input, /* limit= */ C.POSITION_UNSET);
  }

  /**
   * Advances through {@code input} looking for the start of the next Ogg page.
   *
   * <p>The start of a page is identified by the 4-byte capture_pattern 'OggS'.
   *
   * <p>Returns {@code true} if a capture pattern was found, with the read and peek positions of
   * {@code input} at the start of the page, just before the capture_pattern. Otherwise returns
   * {@code false}, with the read and peek positions of {@code input} at either {@code limit} (if
   * set) or end-of-input.
   *
   * @param input The {@link ExtractorInput} to read from (must have {@code readPosition ==
   *     peekPosition}).
   * @param limit The max position in {@code input} to peek to, or {@link C#POSITION_UNSET} to allow
   *     peeking to the end.
   * @return True if a capture_pattern was found.
   * @throws IOException If reading data fails.
   */
  public boolean skipToNextPage(ExtractorInput input, long limit) throws IOException {
    Assertions.checkArgument(input.getPosition() == input.getPeekPosition());
    scratch.reset(/* limit= */ CAPTURE_PATTERN_SIZE);
    while ((limit == C.POSITION_UNSET || input.getPosition() + CAPTURE_PATTERN_SIZE < limit)
        && peekFullyQuietly(
            input, scratch.getData(), 0, CAPTURE_PATTERN_SIZE, /* allowEndOfInput= */ true)) {
      scratch.setPosition(0);
      if (scratch.readUnsignedInt() == CAPTURE_PATTERN) {
        input.resetPeekPosition();
        return true;
      }
      // Advance one byte before looking for the capture pattern again.
      input.skipFully(1);
    }
    // Move the read & peek positions to limit or end-of-input, whichever is closer.
    while ((limit == C.POSITION_UNSET || input.getPosition() < limit)
        && input.skip(1) != C.RESULT_END_OF_INPUT) {}
    return false;
  }

  /**
   * Peeks an Ogg page header and updates this {@link OggPageHeader}.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @param quiet Whether to return {@code false} rather than throwing an exception if the header
   *     cannot be populated.
   * @return Whether the header was entirely populated.
   * @throws IOException If reading data fails or the stream is invalid.
   */
  public boolean populate(ExtractorInput input, boolean quiet) throws IOException {
    reset();
    scratch.reset(/* limit= */ EMPTY_PAGE_HEADER_SIZE);
    if (!peekFullyQuietly(input, scratch.getData(), 0, EMPTY_PAGE_HEADER_SIZE, quiet)
        || scratch.readUnsignedInt() != CAPTURE_PATTERN) {
      return false;
    }

    revision = scratch.readUnsignedByte();
    if (revision != 0x00) {
      if (quiet) {
        return false;
      } else {
        throw ParserException.createForUnsupportedContainerFeature(
            "unsupported bit stream revision");
      }
    }
    type = scratch.readUnsignedByte();

    granulePosition = scratch.readLittleEndianLong();
    streamSerialNumber = scratch.readLittleEndianUnsignedInt();
    pageSequenceNumber = scratch.readLittleEndianUnsignedInt();
    pageChecksum = scratch.readLittleEndianUnsignedInt();
    pageSegmentCount = scratch.readUnsignedByte();
    headerSize = EMPTY_PAGE_HEADER_SIZE + pageSegmentCount;

    // calculate total size of header including laces
    scratch.reset(/* limit= */ pageSegmentCount);
    if (!peekFullyQuietly(input, scratch.getData(), 0, pageSegmentCount, quiet)) {
      return false;
    }
    for (int i = 0; i < pageSegmentCount; i++) {
      laces[i] = scratch.readUnsignedByte();
      bodySize += laces[i];
    }

    return true;
  }
}
