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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

/**
 * @deprecated Use {@link MediaSource.Factory}.
 */
@Deprecated
public interface MediaSourceFactory extends MediaSource.Factory {

  /**
   * An instance that throws {@link UnsupportedOperationException} from {@link #createMediaSource}
   * and {@link #getSupportedTypes()}.
   */
  MediaSourceFactory UNSUPPORTED =
      new MediaSourceFactory() {
        @Override
        public MediaSourceFactory setDrmSessionManagerProvider(
            @Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
          return this;
        }

        @Override
        public MediaSourceFactory setLoadErrorHandlingPolicy(
            @Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
          return this;
        }

        @Override
        public @C.ContentType int[] getSupportedTypes() {
          throw new UnsupportedOperationException();
        }

        @Override
        public MediaSource createMediaSource(MediaItem mediaItem) {
          throw new UnsupportedOperationException();
        }
      };
}
