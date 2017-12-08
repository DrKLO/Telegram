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
import java.util.ArrayList;
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

    /**
     * The http url from which the media playlist can be obtained.
     */
    public final String url;
    /**
     * Format information associated with the HLS url.
     */
    public final Format format;

    /**
     * Creates an HLS url from a given http url.
     *
     * @param url The url.
     * @return An HLS url.
     */
    public static HlsUrl createMediaPlaylistHlsUrl(String url) {
      Format format = Format.createContainerFormat("0", MimeTypes.APPLICATION_M3U8, null, null,
          Format.NO_VALUE, 0, null);
      return new HlsUrl(url, format);
    }

    /**
     * @param url See {@link #url}.
     * @param format See {@link #format}.
     */
    public HlsUrl(String url, Format format) {
      this.url = url;
      this.format = format;
    }

  }

  /**
   * The list of variants declared by the playlist.
   */
  public final List<HlsUrl> variants;
  /**
   * The list of demuxed audios declared by the playlist.
   */
  public final List<HlsUrl> audios;
  /**
   * The list of subtitles declared by the playlist.
   */
  public final List<HlsUrl> subtitles;

  /**
   * The format of the audio muxed in the variants. May be null if the playlist does not declare any
   * muxed audio.
   */
  public final Format muxedAudioFormat;
  /**
   * The format of the closed captions declared by the playlist. May be empty if the playlist
   * explicitly declares no captions are available, or null if the playlist does not declare any
   * captions information.
   */
  public final List<Format> muxedCaptionFormats;

  /**
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   * @param variants See {@link #variants}.
   * @param audios See {@link #audios}.
   * @param subtitles See {@link #subtitles}.
   * @param muxedAudioFormat See {@link #muxedAudioFormat}.
   * @param muxedCaptionFormats See {@link #muxedCaptionFormats}.
   */
  public HlsMasterPlaylist(String baseUri, List<String> tags, List<HlsUrl> variants,
      List<HlsUrl> audios, List<HlsUrl> subtitles, Format muxedAudioFormat,
      List<Format> muxedCaptionFormats) {
    super(baseUri, tags);
    this.variants = Collections.unmodifiableList(variants);
    this.audios = Collections.unmodifiableList(audios);
    this.subtitles = Collections.unmodifiableList(subtitles);
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormats = muxedCaptionFormats != null
        ? Collections.unmodifiableList(muxedCaptionFormats) : null;
  }

  /**
   * Returns a copy of this playlist which includes only the renditions identified by the given
   * urls.
   *
   * @param renditionUrls List of rendition urls.
   * @return A copy of this playlist which includes only the renditions identified by the given
   *     urls.
   */
  public HlsMasterPlaylist copy(List<String> renditionUrls) {
    return new HlsMasterPlaylist(baseUri, tags, copyRenditionsList(variants, renditionUrls),
        copyRenditionsList(audios, renditionUrls), copyRenditionsList(subtitles, renditionUrls),
        muxedAudioFormat, muxedCaptionFormats);
  }

  /**
   * Creates a playlist with a single variant.
   *
   * @param variantUrl The url of the single variant.
   * @return A master playlist with a single variant for the provided url.
   */
  public static HlsMasterPlaylist createSingleVariantMasterPlaylist(String variantUrl) {
    List<HlsUrl> variant = Collections.singletonList(HlsUrl.createMediaPlaylistHlsUrl(variantUrl));
    List<HlsUrl> emptyList = Collections.emptyList();
    return new HlsMasterPlaylist(null, Collections.<String>emptyList(), variant, emptyList,
        emptyList, null, null);
  }

  private static List<HlsUrl> copyRenditionsList(List<HlsUrl> renditions, List<String> urls) {
    List<HlsUrl> copiedRenditions = new ArrayList<>(urls.size());
    for (int i = 0; i < renditions.size(); i++) {
      HlsUrl rendition = renditions.get(i);
      if (urls.contains(rendition.url)) {
        copiedRenditions.add(rendition);
      }
    }
    return copiedRenditions;
  }

}
