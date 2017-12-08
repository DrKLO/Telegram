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

import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.extractor.DummyTrackOutput;
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
 * The wrapper allows switching of the {@link TrackOutput} that receives parsed data.
 */
public final class ChunkExtractorWrapper implements ExtractorOutput {

  /**
   * Provides {@link TrackOutput} instances to be written to by the wrapper.
   */
  public interface TrackOutputProvider {

    /**
     * Called to get the {@link TrackOutput} for a specific track.
     * <p>
     * The same {@link TrackOutput} is returned if multiple calls are made with the same {@code id}.
     *
     * @param id A track identifier.
     * @param type The type of the track. Typically one of the
     *     {@link org.telegram.messenger.exoplayer2.C} {@code TRACK_TYPE_*} constants.
     * @return The {@link TrackOutput} for the given track identifier.
     */
    TrackOutput track(int id, int type);

  }

  public final Extractor extractor;

  private final Format manifestFormat;
  private final SparseArray<BindingTrackOutput> bindingTrackOutputs;

  private boolean extractorInitialized;
  private TrackOutputProvider trackOutputProvider;
  private SeekMap seekMap;
  private Format[] sampleFormats;

  /**
   * @param extractor The extractor to wrap.
   * @param manifestFormat A manifest defined {@link Format} whose data should be merged into any
   *     sample {@link Format} output from the {@link Extractor}.
   */
  public ChunkExtractorWrapper(Extractor extractor, Format manifestFormat) {
    this.extractor = extractor;
    this.manifestFormat = manifestFormat;
    bindingTrackOutputs = new SparseArray<>();
  }

  /**
   * Returns the {@link SeekMap} most recently output by the extractor, or null.
   */
  public SeekMap getSeekMap() {
    return seekMap;
  }

  /**
   * Returns the sample {@link Format}s most recently output by the extractor, or null.
   */
  public Format[] getSampleFormats() {
    return sampleFormats;
  }

  /**
   * Initializes the extractor to output to the provided {@link TrackOutput}, and configures it to
   * receive data from a new chunk.
   *
   * @param trackOutputProvider The provider of {@link TrackOutput}s that will receive sample data.
   */
  public void init(TrackOutputProvider trackOutputProvider) {
    this.trackOutputProvider = trackOutputProvider;
    if (!extractorInitialized) {
      extractor.init(this);
      extractorInitialized = true;
    } else {
      extractor.seek(0, 0);
      for (int i = 0; i < bindingTrackOutputs.size(); i++) {
        bindingTrackOutputs.valueAt(i).bind(trackOutputProvider);
      }
    }
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id, int type) {
    BindingTrackOutput bindingTrackOutput = bindingTrackOutputs.get(id);
    if (bindingTrackOutput == null) {
      // Assert that if we're seeing a new track we have not seen endTracks.
      Assertions.checkState(sampleFormats == null);
      bindingTrackOutput = new BindingTrackOutput(id, type, manifestFormat);
      bindingTrackOutput.bind(trackOutputProvider);
      bindingTrackOutputs.put(id, bindingTrackOutput);
    }
    return bindingTrackOutput;
  }

  @Override
  public void endTracks() {
    Format[] sampleFormats = new Format[bindingTrackOutputs.size()];
    for (int i = 0; i < bindingTrackOutputs.size(); i++) {
      sampleFormats[i] = bindingTrackOutputs.valueAt(i).sampleFormat;
    }
    this.sampleFormats = sampleFormats;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  // Internal logic.

  private static final class BindingTrackOutput implements TrackOutput {

    private final int id;
    private final int type;
    private final Format manifestFormat;

    public Format sampleFormat;
    private TrackOutput trackOutput;

    public BindingTrackOutput(int id, int type, Format manifestFormat) {
      this.id = id;
      this.type = type;
      this.manifestFormat = manifestFormat;
    }

    public void bind(TrackOutputProvider trackOutputProvider) {
      if (trackOutputProvider == null) {
        trackOutput = new DummyTrackOutput();
        return;
      }
      trackOutput = trackOutputProvider.track(id, type);
      if (trackOutput != null) {
        trackOutput.format(sampleFormat);
      }
    }

    @Override
    public void format(Format format) {
      // TODO: This should only happen for the primary track. Additional metadata/text tracks need
      // to be copied with different manifest derived formats.
      sampleFormat = format.copyWithManifestFormatInfo(manifestFormat);
      trackOutput.format(sampleFormat);
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
        CryptoData cryptoData) {
      trackOutput.sampleMetadata(timeUs, flags, size, offset, cryptoData);
    }

  }

}
