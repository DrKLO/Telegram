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
package com.google.android.exoplayer2.source.chunk;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.chunk.ChunkExtractor.TrackOutputProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link Chunk} that uses an {@link Extractor} to decode initialization data for single track.
 */
public final class InitializationChunk extends Chunk {

  private final ChunkExtractor chunkExtractor;

  private @MonotonicNonNull TrackOutputProvider trackOutputProvider;
  private long nextLoadPosition;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param trackFormat See {@link #trackFormat}.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param chunkExtractor A wrapped extractor to use for parsing the initialization data.
   */
  public InitializationChunk(
      DataSource dataSource,
      DataSpec dataSpec,
      Format trackFormat,
      @C.SelectionReason int trackSelectionReason,
      @Nullable Object trackSelectionData,
      ChunkExtractor chunkExtractor) {
    super(
        dataSource,
        dataSpec,
        C.DATA_TYPE_MEDIA_INITIALIZATION,
        trackFormat,
        trackSelectionReason,
        trackSelectionData,
        C.TIME_UNSET,
        C.TIME_UNSET);
    this.chunkExtractor = chunkExtractor;
  }

  /**
   * Initializes the chunk for loading, setting a {@link TrackOutputProvider} for track outputs to
   * which formats will be written as they are loaded.
   *
   * @param trackOutputProvider The {@link TrackOutputProvider} for track outputs to which formats
   *     will be written as they are loaded.
   */
  public void init(TrackOutputProvider trackOutputProvider) {
    this.trackOutputProvider = trackOutputProvider;
  }

  // Loadable implementation.

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void load() throws IOException {
    if (nextLoadPosition == 0) {
      chunkExtractor.init(
          trackOutputProvider, /* startTimeUs= */ C.TIME_UNSET, /* endTimeUs= */ C.TIME_UNSET);
    }
    try {
      // Create and open the input.
      DataSpec loadDataSpec = dataSpec.subrange(nextLoadPosition);
      ExtractorInput input =
          new DefaultExtractorInput(
              dataSource, loadDataSpec.position, dataSource.open(loadDataSpec));
      // Load and decode the initialization data.
      try {
        while (!loadCanceled && chunkExtractor.read(input)) {}
      } finally {
        nextLoadPosition = input.getPosition() - dataSpec.position;
      }
    } finally {
      DataSourceUtil.closeQuietly(dataSource);
    }
  }
}
