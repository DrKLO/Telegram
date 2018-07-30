/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.offline;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable.Parser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** A manifest parser that includes only the tracks identified by the given track keys. */
public final class FilteringManifestParser<T extends FilterableManifest<T, K>, K>
    implements Parser<T> {

  private final Parser<T> parser;
  private final List<K> trackKeys;

  /**
   * @param parser A parser for the manifest that will be filtered.
   * @param trackKeys The track keys. If null or empty then filtering will not occur.
   */
  public FilteringManifestParser(Parser<T> parser, List<K> trackKeys) {
    this.parser = parser;
    this.trackKeys = trackKeys;
  }

  @Override
  public T parse(Uri uri, InputStream inputStream) throws IOException {
    T manifest = parser.parse(uri, inputStream);
    return trackKeys == null || trackKeys.isEmpty() ? manifest : manifest.copy(trackKeys);
  }
}
