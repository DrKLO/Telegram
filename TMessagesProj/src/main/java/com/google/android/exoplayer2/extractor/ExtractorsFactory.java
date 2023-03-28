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
package com.google.android.exoplayer2.extractor;

import android.net.Uri;
import java.util.List;
import java.util.Map;

/** Factory for arrays of {@link Extractor} instances. */
public interface ExtractorsFactory {

  /**
   * Extractor factory that returns an empty list of extractors. Can be used whenever {@link
   * Extractor Extractors} are not required.
   */
  ExtractorsFactory EMPTY = () -> new Extractor[] {};

  /** Returns an array of new {@link Extractor} instances. */
  Extractor[] createExtractors();

  /**
   * Returns an array of new {@link Extractor} instances.
   *
   * @param uri The {@link Uri} of the media to extract.
   * @param responseHeaders The response headers of the media to extract, or an empty map if there
   *     are none. The map lookup should be case-insensitive.
   * @return The {@link Extractor} instances.
   */
  default Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
    return createExtractors();
  }
}
