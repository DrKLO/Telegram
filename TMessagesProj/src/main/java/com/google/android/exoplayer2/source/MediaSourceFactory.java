/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.offline.StreamKey;
import java.util.List;

/** Factory for creating {@link MediaSource}s from URIs. */
public interface MediaSourceFactory {

  /**
   * Sets a list of {@link StreamKey StreamKeys} by which the manifest is filtered.
   *
   * @param streamKeys A list of {@link StreamKey StreamKeys}.
   * @return This factory, for convenience.
   * @throws IllegalStateException If {@link #createMediaSource(Uri)} has already been called.
   */
  default MediaSourceFactory setStreamKeys(List<StreamKey> streamKeys) {
    return this;
  }

  /**
   * Sets the {@link DrmSessionManager} to use for acquiring {@link DrmSession DrmSessions}.
   *
   * @param drmSessionManager The {@link DrmSessionManager}.
   * @return This factory, for convenience.
   * @throws IllegalStateException If one of the {@code create} methods has already been called.
   */
  MediaSourceFactory setDrmSessionManager(DrmSessionManager<?> drmSessionManager);

  /**
   * Creates a new {@link MediaSource} with the specified {@code uri}.
   *
   * @param uri The URI to play.
   * @return The new {@link MediaSource media source}.
   */
  MediaSource createMediaSource(Uri uri);

  /**
   * Returns the {@link C.ContentType content types} supported by media sources created by this
   * factory.
   */
  @C.ContentType
  int[] getSupportedTypes();
}
