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

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents an HLS playlist.
 */
public abstract class HlsPlaylist {

  /**
   * The type of playlist.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_MASTER, TYPE_MEDIA})
  public @interface Type {}
  public static final int TYPE_MASTER = 0;
  public static final int TYPE_MEDIA = 1;

  public final String baseUri;
  @Type
  public final int type;

  protected HlsPlaylist(String baseUri, @Type int type) {
    this.baseUri = baseUri;
    this.type = type;
  }

}
