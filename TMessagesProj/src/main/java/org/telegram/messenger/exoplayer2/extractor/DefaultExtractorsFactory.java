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

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 * <li>MP4, including M4A ({@link org.telegram.messenger.exoplayer2.extractor.mp4.Mp4Extractor})</li>
 * <li>fMP4 ({@link org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor})</li>
 * <li>Matroska and WebM ({@link org.telegram.messenger.exoplayer2.extractor.mkv.MatroskaExtractor})
 * </li>
 * <li>Ogg Vorbis/FLAC ({@link org.telegram.messenger.exoplayer2.extractor.ogg.OggExtractor}</li>
 * <li>MP3 ({@link org.telegram.messenger.exoplayer2.extractor.mp3.Mp3Extractor})</li>
 * <li>AAC ({@link org.telegram.messenger.exoplayer2.extractor.ts.AdtsExtractor})</li>
 * <li>MPEG TS ({@link org.telegram.messenger.exoplayer2.extractor.ts.TsExtractor})</li>
 * <li>MPEG PS ({@link org.telegram.messenger.exoplayer2.extractor.ts.PsExtractor})</li>
 * <li>FLV ({@link org.telegram.messenger.exoplayer2.extractor.flv.FlvExtractor})</li>
 * <li>WAV ({@link org.telegram.messenger.exoplayer2.extractor.wav.WavExtractor})</li>
 * <li>FLAC (only available if the FLAC extension is built and included)</li>
 * </ul>
 */
public final class DefaultExtractorsFactory implements ExtractorsFactory {

  // Lazily initialized default extractor classes in priority order.
  private static List<Class<? extends Extractor>> defaultExtractorClasses;

  /**
   * Creates a new factory for the default extractors.
   */
  public DefaultExtractorsFactory() {
    synchronized (DefaultExtractorsFactory.class) {
      if (defaultExtractorClasses == null) {
        // Lazily initialize defaultExtractorClasses.
        List<Class<? extends Extractor>> extractorClasses = new ArrayList<>();
        // We reference extractors using reflection so that they can be deleted cleanly.
        // Class.forName is used so that automated tools like proguard can detect the use of
        // reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.mkv.MatroskaExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.mp4.Mp4Extractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.mp3.Mp3Extractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.ts.AdtsExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.ts.Ac3Extractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.ts.TsExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.flv.FlvExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.ogg.OggExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.ts.PsExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.extractor.wav.WavExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        try {
          extractorClasses.add(
              Class.forName("org.telegram.messenger.exoplayer2.ext.flac.FlacExtractor")
                  .asSubclass(Extractor.class));
        } catch (ClassNotFoundException e) {
          // Extractor not found.
        }
        defaultExtractorClasses = extractorClasses;
      }
    }
  }

  @Override
  public Extractor[] createExtractors() {
    Extractor[] extractors = new Extractor[defaultExtractorClasses.size()];
    for (int i = 0; i < extractors.length; i++) {
      try {
        extractors[i] = defaultExtractorClasses.get(i).getConstructor().newInstance();
      } catch (Exception e) {
        // Should never happen.
        throw new IllegalStateException("Unexpected error creating default extractor", e);
      }
    }
    return extractors;
  }

}
