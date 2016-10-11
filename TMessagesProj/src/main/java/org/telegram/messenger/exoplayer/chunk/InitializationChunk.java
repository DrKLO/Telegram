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
package org.telegram.messenger.exoplayer.chunk;

import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.chunk.ChunkExtractorWrapper.SingleTrackOutput;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.extractor.DefaultExtractorInput;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;

/**
 * A {@link Chunk} that uses an {@link Extractor} to parse initialization data for single track.
 */
public final class InitializationChunk extends Chunk implements SingleTrackOutput {

  private final ChunkExtractorWrapper extractorWrapper;

  // Initialization results. Set by the loader thread and read by any thread that knows loading
  // has completed. These variables do not need to be volatile, since a memory barrier must occur
  // for the reading thread to know that loading has completed.
  private MediaFormat mediaFormat;
  private DrmInitData drmInitData;
  private SeekMap seekMap;

  private volatile int bytesLoaded;
  private volatile boolean loadCanceled;

  public InitializationChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      ChunkExtractorWrapper extractorWrapper) {
    this(dataSource, dataSpec, trigger, format, extractorWrapper, Chunk.NO_PARENT_ID);
  }

  /**
   * Constructor for a chunk of media samples.
   *
   * @param dataSource A {@link DataSource} for loading the initialization data.
   * @param dataSpec Defines the initialization data to be loaded.
   * @param trigger The reason for this chunk being selected.
   * @param format The format of the stream to which this chunk belongs.
   * @param extractorWrapper A wrapped extractor to use for parsing the initialization data.
   * @param parentId Identifier for a parent from which this chunk originates.
   */
  public InitializationChunk(DataSource dataSource, DataSpec dataSpec, int trigger, Format format,
      ChunkExtractorWrapper extractorWrapper, int parentId) {
    super(dataSource, dataSpec, Chunk.TYPE_MEDIA_INITIALIZATION, trigger, format, parentId);
    this.extractorWrapper = extractorWrapper;
  }

  @Override
  public long bytesLoaded() {
    return bytesLoaded;
  }

  /**
   * True if a {@link MediaFormat} was parsed from the chunk. False otherwise.
   * <p>
   * Should be called after loading has completed.
   */
  public boolean hasFormat() {
    return mediaFormat != null;
  }

  /**
   * Returns a {@link MediaFormat} parsed from the chunk, or null.
   * <p>
   * Should be called after loading has completed.
   */
  public MediaFormat getFormat() {
    return mediaFormat;
  }

  /**
   * True if a {@link DrmInitData} was parsed from the chunk. False otherwise.
   * <p>
   * Should be called after loading has completed.
   */
  public boolean hasDrmInitData() {
    return drmInitData != null;
  }

  /**
   * Returns a {@link DrmInitData} parsed from the chunk, or null.
   * <p>
   * Should be called after loading has completed.
   */
  public DrmInitData getDrmInitData() {
    return drmInitData;
  }

  /**
   * True if a {@link SeekMap} was parsed from the chunk. False otherwise.
   * <p>
   * Should be called after loading has completed.
   */
  public boolean hasSeekMap() {
    return seekMap != null;
  }

  /**
   * Returns a {@link SeekMap} parsed from the chunk, or null.
   * <p>
   * Should be called after loading has completed.
   */
  public SeekMap getSeekMap() {
    return seekMap;
  }

  // SingleTrackOutput implementation.

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  @Override
  public void drmInitData(DrmInitData drmInitData) {
    this.drmInitData = drmInitData;
  }

  @Override
  public void format(MediaFormat mediaFormat) {
    this.mediaFormat = mediaFormat;
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    throw new IllegalStateException("Unexpected sample data in initialization chunk");
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    throw new IllegalStateException("Unexpected sample data in initialization chunk");
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    throw new IllegalStateException("Unexpected sample data in initialization chunk");
  }

  // Loadable implementation.

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void load() throws IOException, InterruptedException {
    DataSpec loadDataSpec = Util.getRemainderDataSpec(dataSpec, bytesLoaded);
    try {
      // Create and open the input.
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (bytesLoaded == 0) {
        // Set the target to ourselves.
        extractorWrapper.init(this);
      }
      // Load and parse the initialization data.
      try {
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractorWrapper.read(input);
        }
      } finally {
        bytesLoaded = (int) (input.getPosition() - dataSpec.absoluteStreamPosition);
      }
    } finally {
      dataSource.close();
    }
  }

}
