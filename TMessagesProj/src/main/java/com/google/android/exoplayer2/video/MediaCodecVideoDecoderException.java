/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import android.media.MediaCodec;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.mediacodec.MediaCodecDecoderException;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;

/** Thrown when a failure occurs in a {@link MediaCodec} video decoder. */
public class MediaCodecVideoDecoderException extends MediaCodecDecoderException {

  /** The {@link System#identityHashCode(Object)} of the surface when the exception occurred. */
  public final int surfaceIdentityHashCode;

  /** Whether the surface was valid when the exception occurred. */
  public final boolean isSurfaceValid;

  public MediaCodecVideoDecoderException(
      Throwable cause, @Nullable MediaCodecInfo codecInfo, @Nullable Surface surface) {
    super(cause, codecInfo);
    surfaceIdentityHashCode = System.identityHashCode(surface);
    isSurfaceValid = surface == null || surface.isValid();
  }
}
