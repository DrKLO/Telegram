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
package org.telegram.messenger.exoplayer2.extractor;

import org.telegram.messenger.exoplayer2.extractor.flv.FlvExtractor;
import org.telegram.messenger.exoplayer2.extractor.mkv.MatroskaExtractor;
import org.telegram.messenger.exoplayer2.extractor.mp3.Mp3Extractor;
import org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import org.telegram.messenger.exoplayer2.extractor.mp4.Mp4Extractor;
import org.telegram.messenger.exoplayer2.extractor.ogg.OggExtractor;
import org.telegram.messenger.exoplayer2.extractor.ts.Ac3Extractor;
import org.telegram.messenger.exoplayer2.extractor.ts.AdtsExtractor;
import org.telegram.messenger.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import org.telegram.messenger.exoplayer2.extractor.ts.PsExtractor;
import org.telegram.messenger.exoplayer2.extractor.ts.TsExtractor;
import org.telegram.messenger.exoplayer2.extractor.wav.WavExtractor;
import java.lang.reflect.Constructor;

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 * <li>MP4, including M4A ({@link Mp4Extractor})</li>
 * <li>fMP4 ({@link FragmentedMp4Extractor})</li>
 * <li>Matroska and WebM ({@link MatroskaExtractor})</li>
 * <li>Ogg Vorbis/FLAC ({@link OggExtractor}</li>
 * <li>MP3 ({@link Mp3Extractor})</li>
 * <li>AAC ({@link AdtsExtractor})</li>
 * <li>MPEG TS ({@link TsExtractor})</li>
 * <li>MPEG PS ({@link PsExtractor})</li>
 * <li>FLV ({@link FlvExtractor})</li>
 * <li>WAV ({@link WavExtractor})</li>
 * <li>AC3 ({@link Ac3Extractor})</li>
 * <li>FLAC (only available if the FLAC extension is built and included)</li>
 * </ul>
 */
public final class DefaultExtractorsFactory implements ExtractorsFactory {

  private static final Constructor<? extends Extractor> FLAC_EXTRACTOR_CONSTRUCTOR;
  static {
    Constructor<? extends Extractor> flacExtractorConstructor = null;
    try {
      flacExtractorConstructor =
          Class.forName("org.telegram.messenger.exoplayer2.ext.flac.FlacExtractor")
              .asSubclass(Extractor.class).getConstructor();
    } catch (ClassNotFoundException e) {
      // Extractor not found.
    } catch (NoSuchMethodException e) {
      // Constructor not found.
    }
    FLAC_EXTRACTOR_CONSTRUCTOR = flacExtractorConstructor;
  }

  private @MatroskaExtractor.Flags int matroskaFlags;
  private @FragmentedMp4Extractor.Flags int fragmentedMp4Flags;
  private @Mp3Extractor.Flags int mp3Flags;
  private @DefaultTsPayloadReaderFactory.Flags int tsFlags;

  /**
   * Sets flags for {@link MatroskaExtractor} instances created by the factory.
   *
   * @see MatroskaExtractor#MatroskaExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setMatroskaExtractorFlags(
      @MatroskaExtractor.Flags int flags) {
    this.matroskaFlags = flags;
    return this;
  }

  /**
   * Sets flags for {@link FragmentedMp4Extractor} instances created by the factory.
   *
   * @see FragmentedMp4Extractor#FragmentedMp4Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setFragmentedMp4ExtractorFlags(
      @FragmentedMp4Extractor.Flags int flags) {
    this.fragmentedMp4Flags = flags;
    return this;
  }

  /**
   * Sets flags for {@link Mp3Extractor} instances created by the factory.
   *
   * @see Mp3Extractor#Mp3Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setMp3ExtractorFlags(@Mp3Extractor.Flags int flags) {
    mp3Flags = flags;
    return this;
  }

  /**
   * Sets flags for {@link DefaultTsPayloadReaderFactory}s used by {@link TsExtractor} instances
   * created by the factory.
   *
   * @see TsExtractor#TsExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setTsExtractorFlags(
      @DefaultTsPayloadReaderFactory.Flags int flags) {
    tsFlags = flags;
    return this;
  }

  @Override
  public synchronized Extractor[] createExtractors() {
    Extractor[] extractors = new Extractor[FLAC_EXTRACTOR_CONSTRUCTOR == null ? 11 : 12];
    extractors[0] = new MatroskaExtractor(matroskaFlags);
    extractors[1] = new FragmentedMp4Extractor(fragmentedMp4Flags);
    extractors[2] = new Mp4Extractor();
    extractors[3] = new Mp3Extractor(mp3Flags);
    extractors[4] = new AdtsExtractor();
    extractors[5] = new Ac3Extractor();
    extractors[6] = new TsExtractor(tsFlags);
    extractors[7] = new FlvExtractor();
    extractors[8] = new OggExtractor();
    extractors[9] = new PsExtractor();
    extractors[10] = new WavExtractor();
    if (FLAC_EXTRACTOR_CONSTRUCTOR != null) {
      try {
        extractors[11] = FLAC_EXTRACTOR_CONSTRUCTOR.newInstance();
      } catch (Exception e) {
        // Should never happen.
        throw new IllegalStateException("Unexpected error creating FLAC extractor", e);
      }
    }
    return extractors;
  }

}
