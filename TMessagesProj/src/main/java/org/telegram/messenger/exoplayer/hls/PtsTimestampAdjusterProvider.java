/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.hls;

import android.util.SparseArray;
import org.telegram.messenger.exoplayer.extractor.ts.PtsTimestampAdjuster;

/**
 * Provides {@link PtsTimestampAdjuster} instances for use during HLS playbacks.
 */
public final class PtsTimestampAdjusterProvider {

  // TODO: Prevent this array from growing indefinitely large by removing adjusters that are no
  // longer required.
  private final SparseArray<PtsTimestampAdjuster> ptsTimestampAdjusters;

  public PtsTimestampAdjusterProvider() {
    ptsTimestampAdjusters = new SparseArray<>();
  }

  /**
   * Gets a {@link PtsTimestampAdjuster} suitable for adjusting the pts timestamps contained in
   * a chunk with a given discontinuity sequence.
   * <p>
   * This method may return null if the master source has yet to initialize a suitable adjuster.
   *
   * @param isMasterSource True if the calling chunk source is the master.
   * @param discontinuitySequence The chunk's discontinuity sequence.
   * @param startTimeUs The chunk's start time.
   * @return A {@link PtsTimestampAdjuster}.
   */
  public PtsTimestampAdjuster getAdjuster(boolean isMasterSource, int discontinuitySequence,
      long startTimeUs) {
    PtsTimestampAdjuster adjuster = ptsTimestampAdjusters.get(discontinuitySequence);
    if (isMasterSource && adjuster == null) {
      adjuster = new PtsTimestampAdjuster(startTimeUs);
      ptsTimestampAdjusters.put(discontinuitySequence, adjuster);
    }
    return isMasterSource || (adjuster != null && adjuster.isInitialized()) ? adjuster : null;
  }

  /**
   * Resets the provider.
   */
  public void reset() {
    ptsTimestampAdjusters.clear();
  }

}
