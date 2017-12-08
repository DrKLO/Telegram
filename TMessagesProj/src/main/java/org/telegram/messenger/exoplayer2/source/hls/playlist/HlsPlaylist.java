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

import java.util.Collections;
import java.util.List;

/**
 * Represents an HLS playlist.
 */
public abstract class HlsPlaylist {

  /**
   * The base uri. Used to resolve relative paths.
   */
  public final String baseUri;
  /**
   * The list of tags in the playlist.
   */
  public final List<String> tags;

  /**
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   */
  protected HlsPlaylist(String baseUri, List<String> tags) {
    this.baseUri = baseUri;
    this.tags = Collections.unmodifiableList(tags);
  }

}
