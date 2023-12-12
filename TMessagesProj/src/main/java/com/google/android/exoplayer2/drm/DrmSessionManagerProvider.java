/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.drm;

import com.google.android.exoplayer2.MediaItem;

/**
 * A provider to obtain a {@link DrmSessionManager} suitable for playing the content described by a
 * {@link MediaItem}.
 */
public interface DrmSessionManagerProvider {

  /**
   * Returns a {@link DrmSessionManager} for the given media item.
   *
   * <p>The caller is responsible for {@link DrmSessionManager#prepare() preparing} the {@link
   * DrmSessionManager} before use, and subsequently {@link DrmSessionManager#release() releasing}
   * it.
   */
  DrmSessionManager get(MediaItem mediaItem);
}
