/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor;

import static com.google.android.exoplayer2.util.Assertions.checkState;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.Ac3Util;
import java.io.IOException;

/**
 * Rechunks TrueHD sample data into groups of {@link Ac3Util#TRUEHD_RECHUNK_SAMPLE_COUNT} samples.
 */
public final class TrueHdSampleRechunker {

  private final byte[] syncframePrefix;

  private boolean foundSyncframe;
  private int chunkSampleCount;
  private long chunkTimeUs;
  private @C.BufferFlags int chunkFlags;
  private int chunkSize;
  private int chunkOffset;

  public TrueHdSampleRechunker() {
    syncframePrefix = new byte[Ac3Util.TRUEHD_SYNCFRAME_PREFIX_LENGTH];
  }

  public void reset() {
    foundSyncframe = false;
    chunkSampleCount = 0;
  }

  public void startSample(ExtractorInput input) throws IOException {
    if (foundSyncframe) {
      return;
    }
    input.peekFully(syncframePrefix, 0, Ac3Util.TRUEHD_SYNCFRAME_PREFIX_LENGTH);
    input.resetPeekPosition();
    if (Ac3Util.parseTrueHdSyncframeAudioSampleCount(syncframePrefix) == 0) {
      return;
    }
    foundSyncframe = true;
  }

  public void sampleMetadata(
      TrackOutput trackOutput,
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable TrackOutput.CryptoData cryptoData) {
    checkState(
        chunkOffset <= size + offset,
        "TrueHD chunk samples must be contiguous in the sample queue.");
    if (!foundSyncframe) {
      return;
    }
    if (chunkSampleCount++ == 0) {
      // This is the first sample in the chunk.
      chunkTimeUs = timeUs;
      chunkFlags = flags;
      chunkSize = 0;
    }
    chunkSize += size;
    chunkOffset = offset; // The offset is to the end of the sample.
    if (chunkSampleCount >= Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT) {
      outputPendingSampleMetadata(trackOutput, cryptoData);
    }
  }

  public void outputPendingSampleMetadata(
      TrackOutput trackOutput, @Nullable TrackOutput.CryptoData cryptoData) {
    if (chunkSampleCount > 0) {
      trackOutput.sampleMetadata(chunkTimeUs, chunkFlags, chunkSize, chunkOffset, cryptoData);
      chunkSampleCount = 0;
    }
  }
}
