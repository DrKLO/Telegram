/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.exoplayer2.source.chunk;

import android.util.Log;
import org.telegram.messenger.exoplayer2.extractor.DefaultTrackOutput;
import org.telegram.messenger.exoplayer2.extractor.DummyTrackOutput;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkExtractorWrapper.TrackOutputProvider;

/**
 * An output for {@link BaseMediaChunk}s.
 */
/* package */ final class BaseMediaChunkOutput implements TrackOutputProvider {

  private static final String TAG = "BaseMediaChunkOutput";

  private final int[] trackTypes;
  private final DefaultTrackOutput[] trackOutputs;

  /**
   * @param trackTypes The track types of the individual track outputs.
   * @param trackOutputs The individual track outputs.
   */
  public BaseMediaChunkOutput(int[] trackTypes, DefaultTrackOutput[] trackOutputs) {
    this.trackTypes = trackTypes;
    this.trackOutputs = trackOutputs;
  }

  @Override
  public TrackOutput track(int id, int type) {
    for (int i = 0; i < trackTypes.length; i++) {
      if (type == trackTypes[i]) {
        return trackOutputs[i];
      }
    }
    Log.e(TAG, "Unmatched track of type: " + type);
    return new DummyTrackOutput();
  }

  /**
   * Returns the current absolute write indices of the individual track outputs.
   */
  public int[] getWriteIndices() {
    int[] writeIndices = new int[trackOutputs.length];
    for (int i = 0; i < trackOutputs.length; i++) {
      if (trackOutputs[i] != null) {
        writeIndices[i] = trackOutputs[i].getWriteIndex();
      }
    }
    return writeIndices;
  }

  /**
   * Sets an offset that will be added to the timestamps (and sub-sample timestamps) of samples
   * subsequently written to the track outputs.
   */
  public void setSampleOffsetUs(long sampleOffsetUs) {
    for (DefaultTrackOutput trackOutput : trackOutputs) {
      if (trackOutput != null) {
        trackOutput.setSampleOffsetUs(sampleOffsetUs);
      }
    }
  }

}
