/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import android.util.Pair;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.util.List;
import java.util.Map;

/**
 * Factory for HLS media chunk extractors.
 */
public interface HlsExtractorFactory {

  HlsExtractorFactory DEFAULT = new DefaultHlsExtractorFactory();

  /**
   * Creates an {@link Extractor} for extracting HLS media chunks.
   *
   * @param previousExtractor A previously used {@link Extractor} which can be reused if the current
   *     chunk is a continuation of the previously extracted chunk, or null otherwise. It is the
   *     responsibility of implementers to only reuse extractors that are suited for reusage.
   * @param uri The URI of the media chunk.
   * @param format A {@link Format} associated with the chunk to extract.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the master playlist.
   * @param drmInitData {@link DrmInitData} associated with the chunk.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param responseHeaders The HTTP response headers associated with the media segment or
   *     initialization section to extract.
   * @return A pair containing the {@link Extractor} and a boolean that indicates whether it is a
   *     packed audio extractor. The first element may be {@code previousExtractor} if the factory
   *     has determined it can be re-used.
   */
  Pair<Extractor, Boolean> createExtractor(
      Extractor previousExtractor,
      Uri uri,
      Format format,
      List<Format> muxedCaptionFormats,
      DrmInitData drmInitData,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders);
}
