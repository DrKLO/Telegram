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
package org.telegram.messenger.exoplayer2.text;

import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.text.cea.Cea608Decoder;
import org.telegram.messenger.exoplayer2.text.cea.Cea708Decoder;
import org.telegram.messenger.exoplayer2.text.dvb.DvbDecoder;
import org.telegram.messenger.exoplayer2.text.ssa.SsaDecoder;
import org.telegram.messenger.exoplayer2.text.subrip.SubripDecoder;
import org.telegram.messenger.exoplayer2.text.ttml.TtmlDecoder;
import org.telegram.messenger.exoplayer2.text.tx3g.Tx3gDecoder;
import org.telegram.messenger.exoplayer2.text.webvtt.Mp4WebvttDecoder;
import org.telegram.messenger.exoplayer2.text.webvtt.WebvttDecoder;
import org.telegram.messenger.exoplayer2.util.MimeTypes;

/**
 * A factory for {@link SubtitleDecoder} instances.
 */
public interface SubtitleDecoderFactory {

  /**
   * Returns whether the factory is able to instantiate a {@link SubtitleDecoder} for the given
   * {@link Format}.
   *
   * @param format The {@link Format}.
   * @return Whether the factory can instantiate a suitable {@link SubtitleDecoder}.
   */
  boolean supportsFormat(Format format);

  /**
   * Creates a {@link SubtitleDecoder} for the given {@link Format}.
   *
   * @param format The {@link Format}.
   * @return A new {@link SubtitleDecoder}.
   * @throws IllegalArgumentException If the {@link Format} is not supported.
   */
  SubtitleDecoder createDecoder(Format format);

  /**
   * Default {@link SubtitleDecoderFactory} implementation.
   * <p>
   * The formats supported by this factory are:
   * <ul>
   * <li>WebVTT ({@link WebvttDecoder})</li>
   * <li>WebVTT (MP4) ({@link Mp4WebvttDecoder})</li>
   * <li>TTML ({@link TtmlDecoder})</li>
   * <li>SubRip ({@link SubripDecoder})</li>
   * <li>SSA/ASS ({@link SsaDecoder})</li>
   * <li>TX3G ({@link Tx3gDecoder})</li>
   * <li>Cea608 ({@link Cea608Decoder})</li>
   * <li>Cea708 ({@link Cea708Decoder})</li>
   * <li>DVB ({@link DvbDecoder})</li>
   * </ul>
   */
  SubtitleDecoderFactory DEFAULT = new SubtitleDecoderFactory() {

    @Override
    public boolean supportsFormat(Format format) {
      String mimeType = format.sampleMimeType;
      return MimeTypes.TEXT_VTT.equals(mimeType)
          || MimeTypes.TEXT_SSA.equals(mimeType)
          || MimeTypes.APPLICATION_TTML.equals(mimeType)
          || MimeTypes.APPLICATION_MP4VTT.equals(mimeType)
          || MimeTypes.APPLICATION_SUBRIP.equals(mimeType)
          || MimeTypes.APPLICATION_TX3G.equals(mimeType)
          || MimeTypes.APPLICATION_CEA608.equals(mimeType)
          || MimeTypes.APPLICATION_MP4CEA608.equals(mimeType)
          || MimeTypes.APPLICATION_CEA708.equals(mimeType)
          || MimeTypes.APPLICATION_DVBSUBS.equals(mimeType);
    }

    @Override
    public SubtitleDecoder createDecoder(Format format) {
      switch (format.sampleMimeType) {
        case MimeTypes.TEXT_VTT:
          return new WebvttDecoder();
        case MimeTypes.TEXT_SSA:
          return new SsaDecoder(format.initializationData);
        case MimeTypes.APPLICATION_MP4VTT:
          return new Mp4WebvttDecoder();
        case MimeTypes.APPLICATION_TTML:
          return new TtmlDecoder();
        case MimeTypes.APPLICATION_SUBRIP:
          return new SubripDecoder();
        case MimeTypes.APPLICATION_TX3G:
          return new Tx3gDecoder(format.initializationData);
        case MimeTypes.APPLICATION_CEA608:
        case MimeTypes.APPLICATION_MP4CEA608:
          return new Cea608Decoder(format.sampleMimeType, format.accessibilityChannel);
        case MimeTypes.APPLICATION_CEA708:
          return new Cea708Decoder(format.accessibilityChannel);
        case MimeTypes.APPLICATION_DVBSUBS:
          return new DvbDecoder(format.initializationData);
        default:
          throw new IllegalArgumentException("Attempted to create decoder for unsupported format");
      }
    }

  };

}
