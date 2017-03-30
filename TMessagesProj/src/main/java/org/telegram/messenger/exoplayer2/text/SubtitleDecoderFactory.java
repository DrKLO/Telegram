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
   * <li>TX3G ({@link Tx3gDecoder})</li>
   * <li>Cea608 ({@link Cea608Decoder})</li>
   * </ul>
   */
  SubtitleDecoderFactory DEFAULT = new SubtitleDecoderFactory() {

    @Override
    public boolean supportsFormat(Format format) {
      return getDecoderClass(format.sampleMimeType) != null;
    }

    @Override
    public SubtitleDecoder createDecoder(Format format) {
      try {
        Class<?> clazz = getDecoderClass(format.sampleMimeType);
        if (clazz == null) {
          throw new IllegalArgumentException("Attempted to create decoder for unsupported format");
        }
        if (clazz == Cea608Decoder.class) {
          return clazz.asSubclass(SubtitleDecoder.class)
              .getConstructor(Integer.TYPE).newInstance(format.accessibilityChannel);
        } else {
          return clazz.asSubclass(SubtitleDecoder.class).getConstructor().newInstance();
        }
      } catch (Exception e) {
        throw new IllegalStateException("Unexpected error instantiating decoder", e);
      }
    }

    private Class<?> getDecoderClass(String mimeType) {
      if (mimeType == null) {
        return null;
      }
      try {
        switch (mimeType) {
          case MimeTypes.TEXT_VTT:
            return Class.forName("org.telegram.messenger.exoplayer2.text.webvtt.WebvttDecoder");
          case MimeTypes.APPLICATION_TTML:
            return Class.forName("org.telegram.messenger.exoplayer2.text.ttml.TtmlDecoder");
          case MimeTypes.APPLICATION_MP4VTT:
            return Class.forName("org.telegram.messenger.exoplayer2.text.webvtt.Mp4WebvttDecoder");
          case MimeTypes.APPLICATION_SUBRIP:
            return Class.forName("org.telegram.messenger.exoplayer2.text.subrip.SubripDecoder");
          case MimeTypes.APPLICATION_TX3G:
            return Class.forName("org.telegram.messenger.exoplayer2.text.tx3g.Tx3gDecoder");
          case MimeTypes.APPLICATION_CEA608:
            return Class.forName("org.telegram.messenger.exoplayer2.text.cea.Cea608Decoder");
          default:
            return null;
        }
      } catch (ClassNotFoundException e) {
        return null;
      }
    }

  };

}
