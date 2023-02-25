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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * {@link ChunkExtractor} implementation that uses ExoPlayer app-bundled {@link Extractor
 * Extractors}.
 */
public final class BundledChunkExtractor implements ExtractorOutput, ChunkExtractor {

  /** {@link ChunkExtractor.Factory} for instances of this class. */
  public static final ChunkExtractor.Factory FACTORY =
      (primaryTrackType,
          format,
          enableEventMessageTrack,
          closedCaptionFormats,
          playerEmsgTrackOutput,
          playerId) -> {
        @Nullable String containerMimeType = format.containerMimeType;
        Extractor extractor;
        if (MimeTypes.isText(containerMimeType)) {
          // Text types do not need an extractor.
          return null;
        } else if (MimeTypes.isMatroska(containerMimeType)) {
          extractor = new MatroskaExtractor(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES);
        } else {
          int flags = 0;
          if (enableEventMessageTrack) {
            flags |= FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK;
          }
          extractor =
              new FragmentedMp4Extractor(
                  flags,
                  /* timestampAdjuster= */ null,
                  /* sideloadedTrack= */ null,
                  closedCaptionFormats,
                  playerEmsgTrackOutput);
        }
        return new BundledChunkExtractor(extractor, primaryTrackType, format);
      };

  private static final PositionHolder POSITION_HOLDER = new PositionHolder();

  private final Extractor extractor;
  private final @C.TrackType int primaryTrackType;
  private final Format primaryTrackManifestFormat;
  private final SparseArray<BindingTrackOutput> bindingTrackOutputs;

  private boolean extractorInitialized;
  @Nullable private TrackOutputProvider trackOutputProvider;
  private long endTimeUs;
  private @MonotonicNonNull SeekMap seekMap;
  private Format @MonotonicNonNull [] sampleFormats;

  /**
   * Creates an instance.
   *
   * @param extractor The extractor to wrap.
   * @param primaryTrackType The {@link C.TrackType type} of the primary track.
   * @param primaryTrackManifestFormat A manifest defined {@link Format} whose data should be merged
   *     into any sample {@link Format} output from the {@link Extractor} for the primary track.
   */
  public BundledChunkExtractor(
      Extractor extractor, @C.TrackType int primaryTrackType, Format primaryTrackManifestFormat) {
    this.extractor = extractor;
    this.primaryTrackType = primaryTrackType;
    this.primaryTrackManifestFormat = primaryTrackManifestFormat;
    bindingTrackOutputs = new SparseArray<>();
  }

  // ChunkExtractor implementation.

  @Override
  @Nullable
  public ChunkIndex getChunkIndex() {
    return seekMap instanceof ChunkIndex ? (ChunkIndex) seekMap : null;
  }

  @Override
  @Nullable
  public Format[] getSampleFormats() {
    return sampleFormats;
  }

  @Override
  public void init(
      @Nullable TrackOutputProvider trackOutputProvider, long startTimeUs, long endTimeUs) {
    this.trackOutputProvider = trackOutputProvider;
    this.endTimeUs = endTimeUs;
    if (!extractorInitialized) {
      extractor.init(this);
      if (startTimeUs != C.TIME_UNSET) {
        extractor.seek(/* position= */ 0, startTimeUs);
      }
      extractorInitialized = true;
    } else {
      extractor.seek(/* position= */ 0, startTimeUs == C.TIME_UNSET ? 0 : startTimeUs);
      for (int i = 0; i < bindingTrackOutputs.size(); i++) {
        bindingTrackOutputs.valueAt(i).bind(trackOutputProvider, endTimeUs);
      }
    }
  }

  @Override
  public void release() {
    extractor.release();
  }

  @Override
  public boolean read(ExtractorInput input) throws IOException {
    int result = extractor.read(input, POSITION_HOLDER);
    Assertions.checkState(result != Extractor.RESULT_SEEK);
    return result == Extractor.RESULT_CONTINUE;
  }

  // ExtractorOutput implementation.

  @Override
  public TrackOutput track(int id, int type) {
    BindingTrackOutput bindingTrackOutput = bindingTrackOutputs.get(id);
    if (bindingTrackOutput == null) {
      // Assert that if we're seeing a new track we have not seen endTracks.
      Assertions.checkState(sampleFormats == null);
      // TODO: Manifest formats for embedded tracks should also be passed here.
      bindingTrackOutput =
          new BindingTrackOutput(
              id, type, type == primaryTrackType ? primaryTrackManifestFormat : null);
      bindingTrackOutput.bind(trackOutputProvider, endTimeUs);
      bindingTrackOutputs.put(id, bindingTrackOutput);
    }
    return bindingTrackOutput;
  }

  @Override
  public void endTracks() {
    Format[] sampleFormats = new Format[bindingTrackOutputs.size()];
    for (int i = 0; i < bindingTrackOutputs.size(); i++) {
      sampleFormats[i] = Assertions.checkStateNotNull(bindingTrackOutputs.valueAt(i).sampleFormat);
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
    @Nullable private final Format manifestFormat;
    private final DummyTrackOutput fakeTrackOutput;

    public @MonotonicNonNull Format sampleFormat;
    private @MonotonicNonNull TrackOutput trackOutput;
    private long endTimeUs;

    public BindingTrackOutput(int id, int type, @Nullable Format manifestFormat) {
      this.id = id;
      this.type = type;
      this.manifestFormat = manifestFormat;
      fakeTrackOutput = new DummyTrackOutput();
    }

    public void bind(@Nullable TrackOutputProvider trackOutputProvider, long endTimeUs) {
      if (trackOutputProvider == null) {
        trackOutput = fakeTrackOutput;
        return;
      }
      this.endTimeUs = endTimeUs;
      trackOutput = trackOutputProvider.track(id, type);
      if (sampleFormat != null) {
        trackOutput.format(sampleFormat);
      }
    }

    @Override
    public void format(Format format) {
      sampleFormat =
          manifestFormat != null ? format.withManifestFormatInfo(manifestFormat) : format;
      castNonNull(trackOutput).format(sampleFormat);
    }

    @Override
    public int sampleData(
        DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
        throws IOException {
      return castNonNull(trackOutput).sampleData(input, length, allowEndOfInput);
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
      castNonNull(trackOutput).sampleData(data, length);
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      if (endTimeUs != C.TIME_UNSET && timeUs >= endTimeUs) {
        trackOutput = fakeTrackOutput;
      }
      castNonNull(trackOutput).sampleMetadata(timeUs, flags, size, offset, cryptoData);
    }
  }
}
