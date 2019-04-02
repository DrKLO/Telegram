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
package com.google.android.exoplayer2.source.hls.playlist;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Represents an HLS master playlist. */
public final class HlsMasterPlaylist extends HlsPlaylist {

  /** Represents an empty master playlist, from which no attributes can be inherited. */
  public static final HlsMasterPlaylist EMPTY =
      new HlsMasterPlaylist(
          /* baseUri= */ "",
          /* tags= */ Collections.emptyList(),
          /* variants= */ Collections.emptyList(),
          /* audios= */ Collections.emptyList(),
          /* subtitles= */ Collections.emptyList(),
          /* muxedAudioFormat= */ null,
          /* muxedCaptionFormats= */ Collections.emptyList(),
          /* hasIndependentSegments= */ false,
          /* variableDefinitions= */ Collections.emptyMap());

  public static final int GROUP_INDEX_VARIANT = 0;
  public static final int GROUP_INDEX_AUDIO = 1;
  public static final int GROUP_INDEX_SUBTITLE = 2;

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
      Format format =
          Format.createContainerFormat(
              "0",
              /* label= */ null,
              MimeTypes.APPLICATION_M3U8,
              /* sampleMimeType= */ null,
              /* codecs= */ null,
              /* bitrate= */ Format.NO_VALUE,
              /* selectionFlags= */ 0,
              /* language= */ null);
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
  /** Contains variable definitions, as defined by the #EXT-X-DEFINE tag. */
  public final Map<String, String> variableDefinitions;

  /**
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   * @param variants See {@link #variants}.
   * @param audios See {@link #audios}.
   * @param subtitles See {@link #subtitles}.
   * @param muxedAudioFormat See {@link #muxedAudioFormat}.
   * @param muxedCaptionFormats See {@link #muxedCaptionFormats}.
   * @param hasIndependentSegments See {@link #hasIndependentSegments}.
   * @param variableDefinitions See {@link #variableDefinitions}.
   */
  public HlsMasterPlaylist(
      String baseUri,
      List<String> tags,
      List<HlsUrl> variants,
      List<HlsUrl> audios,
      List<HlsUrl> subtitles,
      Format muxedAudioFormat,
      List<Format> muxedCaptionFormats,
      boolean hasIndependentSegments,
      Map<String, String> variableDefinitions) {
    super(baseUri, tags, hasIndependentSegments);
    this.variants = Collections.unmodifiableList(variants);
    this.audios = Collections.unmodifiableList(audios);
    this.subtitles = Collections.unmodifiableList(subtitles);
    this.muxedAudioFormat = muxedAudioFormat;
    this.muxedCaptionFormats = muxedCaptionFormats != null
        ? Collections.unmodifiableList(muxedCaptionFormats) : null;
    this.variableDefinitions = Collections.unmodifiableMap(variableDefinitions);
  }

  @Override
  public HlsMasterPlaylist copy(List<StreamKey> streamKeys) {
    return new HlsMasterPlaylist(
        baseUri,
        tags,
        copyRenditionsList(variants, GROUP_INDEX_VARIANT, streamKeys),
        copyRenditionsList(audios, GROUP_INDEX_AUDIO, streamKeys),
        copyRenditionsList(subtitles, GROUP_INDEX_SUBTITLE, streamKeys),
        muxedAudioFormat,
        muxedCaptionFormats,
        hasIndependentSegments,
        variableDefinitions);
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
    return new HlsMasterPlaylist(
        null,
        Collections.emptyList(),
        variant,
        emptyList,
        emptyList,
        /* muxedAudioFormat= */ null,
        /* muxedCaptionFormats= */ null,
        /* hasIndependentSegments= */ false,
        /* variableDefinitions= */ Collections.emptyMap());
  }

  private static List<HlsUrl> copyRenditionsList(
      List<HlsUrl> renditions, int groupIndex, List<StreamKey> streamKeys) {
    List<HlsUrl> copiedRenditions = new ArrayList<>(streamKeys.size());
    for (int i = 0; i < renditions.size(); i++) {
      HlsUrl rendition = renditions.get(i);
      for (int j = 0; j < streamKeys.size(); j++) {
        StreamKey streamKey = streamKeys.get(j);
        if (streamKey.groupIndex == groupIndex && streamKey.trackIndex == i) {
          copiedRenditions.add(rendition);
          break;
        }
      }
    }
    return copiedRenditions;
  }

}
