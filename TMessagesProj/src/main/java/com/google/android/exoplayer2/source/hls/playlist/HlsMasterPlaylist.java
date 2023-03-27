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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link HlsMultivariantPlaylist} instead.
 */
@Deprecated
public final class HlsMasterPlaylist extends HlsMultivariantPlaylist {

  /**
   * Creates an HLS multivariant playlist.
   *
   * @deprecated Use {@link HlsMultivariantPlaylist#HlsMultivariantPlaylist} instead.
   */
  @Deprecated
  public HlsMasterPlaylist(
      String baseUri,
      List<String> tags,
      List<Variant> variants,
      List<Rendition> videos,
      List<Rendition> audios,
      List<Rendition> subtitles,
      List<Rendition> closedCaptions,
      @Nullable Format muxedAudioFormat,
      @Nullable List<Format> muxedCaptionFormats,
      boolean hasIndependentSegments,
      Map<String, String> variableDefinitions,
      List<DrmInitData> sessionKeyDrmInitData) {
    super(
        baseUri,
        tags,
        variants,
        videos,
        audios,
        subtitles,
        closedCaptions,
        muxedAudioFormat,
        muxedCaptionFormats,
        hasIndependentSegments,
        variableDefinitions,
        sessionKeyDrmInitData);
  }
}
