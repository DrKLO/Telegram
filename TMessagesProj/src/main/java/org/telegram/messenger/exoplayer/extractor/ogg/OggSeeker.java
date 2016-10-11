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
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.IOException;

/**
 * Used to seek in an Ogg stream.
 */
/* package */ final class OggSeeker {

  private static final int MATCH_RANGE = 72000;

  private final OggUtil.PageHeader pageHeader = new OggUtil.PageHeader();
  private final ParsableByteArray headerArray = new ParsableByteArray(27 + 255);
  private long audioDataLength = C.LENGTH_UNBOUNDED;
  private long totalSamples;

  /**
   * Setup the seeker with the data it needs to to an educated guess of seeking positions.
   *
   * @param audioDataLength the length of the audio data (total bytes - header bytes).
   * @param totalSamples the total number of samples of audio data.
   */
  public void setup(long audioDataLength, long totalSamples) {
    Assertions.checkArgument(audioDataLength > 0 && totalSamples > 0);
    this.audioDataLength = audioDataLength;
    this.totalSamples = totalSamples;
  }

  /**
   * Returns a position converging to the {@code targetGranule} to which the {@link ExtractorInput}
   * has to seek and then be passed for another call until -1 is return. If -1 is returned the
   * input is at a position which is before the start of the page before the target page and at
   * which it is sensible to just skip pages to the target granule and pre-roll instead of doing
   * another seek request.
   *
   * @param targetGranule the target granule position to seek to.
   * @param input the {@link ExtractorInput} to read from.
   * @return the position to seek the {@link ExtractorInput} to for a next call or -1 if it's close
   *    enough to skip to the target page.
   * @throws IOException thrown if reading from the input fails.
   * @throws InterruptedException thrown if interrupted while reading from the input.
   */
  public long getNextSeekPosition(long targetGranule, ExtractorInput input)
      throws IOException, InterruptedException {
    Assertions.checkState(audioDataLength != C.LENGTH_UNBOUNDED && totalSamples != 0);
    OggUtil.populatePageHeader(input, pageHeader, headerArray, false);
    long granuleDistance = targetGranule - pageHeader.granulePosition;
    if (granuleDistance <= 0 || granuleDistance > MATCH_RANGE) {
      // estimated position too high or too low
      long offset = (pageHeader.bodySize + pageHeader.headerSize)
          * (granuleDistance <= 0 ? 2 : 1);
      return input.getPosition() - offset + (granuleDistance * audioDataLength / totalSamples);
    }
    // position accepted (below target granule and within MATCH_RANGE)
    input.resetPeekPosition();
    return -1;
  }

}
