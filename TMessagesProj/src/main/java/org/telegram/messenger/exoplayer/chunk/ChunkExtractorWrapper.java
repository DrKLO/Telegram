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
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.IOException;

/**
 * An {@link Extractor} wrapper for loading chunks containing a single track.
 * <p>
 * The wrapper allows switching of the {@link SingleTrackOutput} that receives parsed data.
 */
public class ChunkExtractorWrapper implements ExtractorOutput, TrackOutput {

  /**
   * Receives stream level data extracted by the wrapped {@link Extractor}.
   */
  public interface SingleTrackOutput extends TrackOutput {

    /**
     * @see ExtractorOutput#seekMap(SeekMap)
     */
    void seekMap(SeekMap seekMap);

    /**
     * @see ExtractorOutput#drmInitData(DrmInitData)
     */
    void drmInitData(DrmInitData drmInitData);

  }

  private final Extractor extractor;
  private boolean extractorInitialized;
  private SingleTrackOutput output;

  // Accessed only on the loader thread.
  private boolean seenTrack;

  /**
   * @param extractor The extractor to wrap.
   */
  public ChunkExtractorWrapper(Extractor extractor) {
    this.extractor = extractor;
  }

  /**
   * Initializes the extractor to output to the provided {@link SingleTrackOutput}, and configures
   * it to receive data from a new chunk.
   *
   * @param output The {@link SingleTrackOutput} that will receive the parsed data.
   */
  public void init(SingleTrackOutput output) {
    this.output = output;
    if (!extractorInitialized) {
      extractor.init(this);
      extractorInitialized = true;
    } else {
      extractor.seek();
    }
  }

  /**
   * Reads from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return One of {@link Extractor#RESULT_CONTINUE} and {@link Extractor#RESULT_END_OF_INPUT}.
   * @throws IOException If an error occurred reading from the source.
   * @throws InterruptedException If the thread was interrupted.
   */
  public int read(ExtractorInput input) throws IOException, InterruptedException {
    int result = extractor.read(input, null);
    Assertions.checkState(result != Extractor.RESULT_SEEK);
    return result;
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id) {
    Assertions.checkState(!seenTrack);
    seenTrack = true;
    return this;
  }

  @Override
  public void endTracks() {
    Assertions.checkState(seenTrack);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    output.seekMap(seekMap);
  }

  @Override
  public void drmInitData(DrmInitData drmInitData) {
    output.drmInitData(drmInitData);
  }

  // TrackOutput implementation.

  @Override
  public void format(MediaFormat format) {
    output.format(format);
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return output.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    output.sampleData(data, length);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    output.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
  }

}
