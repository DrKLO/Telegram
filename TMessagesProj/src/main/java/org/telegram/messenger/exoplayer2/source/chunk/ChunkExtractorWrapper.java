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
package org.telegram.messenger.exoplayer2.source.chunk;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * An {@link Extractor} wrapper for loading chunks containing a single track.
 * <p>
 * The wrapper allows switching of the {@link SingleTrackMetadataOutput} and {@link TrackOutput}
 * which receive parsed data.
 */
public final class ChunkExtractorWrapper implements ExtractorOutput, TrackOutput {

  /**
   * Receives metadata associated with the track as extracted by the wrapped {@link Extractor}.
   */
  public interface SingleTrackMetadataOutput {

    /**
     * @see ExtractorOutput#seekMap(SeekMap)
     */
    void seekMap(SeekMap seekMap);

  }

  private final Extractor extractor;
  private final Format manifestFormat;
  private final boolean preferManifestDrmInitData;
  private final boolean resendFormatOnInit;

  private boolean extractorInitialized;
  private SingleTrackMetadataOutput metadataOutput;
  private TrackOutput trackOutput;
  private Format sentFormat;

  // Accessed only on the loader thread.
  private boolean seenTrack;
  private int seenTrackId;

  /**
   * @param extractor The extractor to wrap.
   * @param manifestFormat A manifest defined {@link Format} whose data should be merged into any
   *     sample {@link Format} output from the {@link Extractor}.
   * @param preferManifestDrmInitData Whether {@link DrmInitData} defined in {@code manifestFormat}
   *     should be preferred when the sample and manifest {@link Format}s are merged.
   * @param resendFormatOnInit Whether the extractor should resend the previous {@link Format} when
   *     it is initialized via {@link #init(SingleTrackMetadataOutput, TrackOutput)}.
   */
  public ChunkExtractorWrapper(Extractor extractor, Format manifestFormat,
      boolean preferManifestDrmInitData, boolean resendFormatOnInit) {
    this.extractor = extractor;
    this.manifestFormat = manifestFormat;
    this.preferManifestDrmInitData = preferManifestDrmInitData;
    this.resendFormatOnInit = resendFormatOnInit;
  }

  /**
   * Initializes the extractor to output to the provided {@link SingleTrackMetadataOutput} and
   * {@link TrackOutput} instances, and configures it to receive data from a new chunk.
   *
   * @param metadataOutput The {@link SingleTrackMetadataOutput} that will receive metadata.
   * @param trackOutput The {@link TrackOutput} that will receive sample data.
   */
  public void init(SingleTrackMetadataOutput metadataOutput, TrackOutput trackOutput) {
    this.metadataOutput = metadataOutput;
    this.trackOutput = trackOutput;
    if (!extractorInitialized) {
      extractor.init(this);
      extractorInitialized = true;
    } else {
      extractor.seek(0);
      if (resendFormatOnInit && sentFormat != null) {
        trackOutput.format(sentFormat);
      }
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
    Assertions.checkState(!seenTrack || seenTrackId == id);
    seenTrack = true;
    seenTrackId = id;
    return this;
  }

  @Override
  public void endTracks() {
    Assertions.checkState(seenTrack);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    metadataOutput.seekMap(seekMap);
  }

  // TrackOutput implementation.

  @Override
  public void format(Format format) {
    sentFormat = format.copyWithManifestFormatInfo(manifestFormat, preferManifestDrmInitData);
    trackOutput.format(sentFormat);
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return trackOutput.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    trackOutput.sampleData(data, length);
  }

  @Override
  public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset,
      byte[] encryptionKey) {
    trackOutput.sampleMetadata(timeUs, flags, size, offset, encryptionKey);
  }

}
