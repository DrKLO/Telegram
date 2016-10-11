/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.hls;

/**
 * Represents an HLS playlist.
 */
public abstract class HlsPlaylist {

  public final static int TYPE_MASTER = 0;
  public final static int TYPE_MEDIA = 1;

  public final String baseUri;
  public final int type;

  protected HlsPlaylist(String baseUri, int type) {
    this.baseUri = baseUri;
    this.type = type;
  }

}
