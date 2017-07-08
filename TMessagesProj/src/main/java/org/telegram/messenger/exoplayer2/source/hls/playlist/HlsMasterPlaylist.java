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
package org.telegram.messenger.exoplayer2.source.hls.playlist;

import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import java.util.Collections;
import java.util.List;

/**
 * Represents an HLS master playlist.
 */
public final class HlsMasterPlaylist extends HlsPlaylist {

  /**
   * Represents a url in an HLS master playlist.
   */
  public static final class HlsUrl {

    public final String url;
    public final Format format;

    public static HlsUrl createMediaPlaylistHlsUrl(String baseUri) {
      Format format = Format.createContainerFormat("0", MimeTypes.APPLICATION_M3U8, null, null,
          Format.NO_VALUE, 0, null);
      return new HlsUrl(baseUri, format);
    }

    public HlsUrl(String url, Format format) {
      this.url = url;
      this.format = format;
    }

  }

  public final List<HlsUrl> variants;
  public final List<HlsUrl> audios;
  public final List<HlsUrl> subtitles;

  public final Format muxedAudioFormat;
  public final List<Format> muxedCaptionFormats;

  public HlsMasterPlaylist(String baseUri, List<HlsUrl> variants, List<HlsUrl> audios,
      List<HlsUrl> subtitles, Format muxedAudioFormat, List<Format> muxedCaptionFormats) {
    super(baseUri);
    this.variants = Collections.unmodifiableList(variants);
    this.audios = Collections.unmodifiableList(audios);
    this.subtitles = Collections.unmodifiableList(subtitles);
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormats = Collections.unmodifiableList(muxedCaptionFormats);
  }

  public static HlsMasterPlaylist createSingleVariantMasterPlaylist(String variantUri) {
    List<HlsUrl> variant = Collections.singletonList(HlsUrl.createMediaPlaylistHlsUrl(variantUri));
    List<HlsUrl> emptyList = Collections.emptyList();
    return new HlsMasterPlaylist(null, variant, emptyList, emptyList, null,
        Collections.<Format>emptyList());
  }

}
