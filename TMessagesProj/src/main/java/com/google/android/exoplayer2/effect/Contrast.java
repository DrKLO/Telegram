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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.content.Context;
import com.google.android.exoplayer2.util.FrameProcessingException;

/** A {@link GlEffect} to control the contrast of video frames. */
public class Contrast implements GlEffect {

  /** Adjusts the contrast of video frames in the interval [-1, 1]. */
  public final float contrast;

  /**
   * Creates a new instance for the given contrast value.
   *
   * <p>Contrast values range from -1 (all gray pixels) to 1 (maximum difference of colors). 0 means
   * to add no contrast and leaves the frames unchanged.
   */
  public Contrast(float contrast) {
    checkArgument(-1 <= contrast && contrast <= 1, "Contrast needs to be in the interval [-1, 1].");
    this.contrast = contrast;
  }

  @Override
  public SingleFrameGlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr)
      throws FrameProcessingException {
    return new ContrastProcessor(context, this, useHdr);
  }
}
