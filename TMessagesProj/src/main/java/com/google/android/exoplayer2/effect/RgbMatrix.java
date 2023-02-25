/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.effect;

import android.content.Context;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.common.collect.ImmutableList;

/**
 * Specifies a 4x4 RGB color transformation matrix to apply to each frame in the fragment shader.
 */
public interface RgbMatrix extends GlEffect {

  /**
   * Returns the 4x4 RGB transformation {@linkplain android.opengl.Matrix matrix} to apply to the
   * color values of each pixel in the frame with the given timestamp.
   *
   * @param presentationTimeUs The timestamp of the frame to apply the matrix on.
   * @param useHdr If {@code true}, colors will be in linear RGB BT.2020. If {@code false}, colors
   *     will be in linear RGB BT.709. Must be consistent with {@code useHdr} in {@link
   *     #toGlTextureProcessor(Context, boolean)}.
   * @return The {@code RgbMatrix} to apply to the frame.
   */
  float[] getMatrix(long presentationTimeUs, boolean useHdr);

  @Override
  default SingleFrameGlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr)
      throws FrameProcessingException {
    return MatrixTextureProcessor.create(
        context,
        /* matrixTransformations= */ ImmutableList.of(),
        /* rgbMatrices= */ ImmutableList.of(this),
        useHdr);
  }
}
