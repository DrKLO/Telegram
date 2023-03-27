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

import android.opengl.Matrix;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Scales the red, green, and blue color channels of a frame. */
public final class RgbAdjustment implements RgbMatrix {

  /** A builder for {@link RgbAdjustment} instances. */
  public static final class Builder {
    private float redScale;
    private float greenScale;
    private float blueScale;

    /** Creates a new instance with default values. */
    public Builder() {
      redScale = 1;
      greenScale = 1;
      blueScale = 1;
    }

    /**
     * Scales the red channel of the frame by {@code redScale}.
     *
     * @param redScale The scale to apply to the red channel. Needs to be non-negative and the
     *     default value is {@code 1}.
     */
    @CanIgnoreReturnValue
    public Builder setRedScale(float redScale) {
      checkArgument(0 <= redScale, "Red scale needs to be non-negative.");
      this.redScale = redScale;
      return this;
    }

    /**
     * Scales the green channel of the frame by {@code greenScale}.
     *
     * @param greenScale The scale to apply to the green channel. Needs to be non-negative and the
     *     default value is {@code 1}.
     */
    @CanIgnoreReturnValue
    public Builder setGreenScale(float greenScale) {
      checkArgument(0 <= greenScale, "Green scale needs to be non-negative.");
      this.greenScale = greenScale;
      return this;
    }

    /**
     * Scales the blue channel of the frame by {@code blueScale}.
     *
     * @param blueScale The scale to apply to the blue channel. Needs to be non-negative and the
     *     default value is {@code 1}.
     */
    @CanIgnoreReturnValue
    public Builder setBlueScale(float blueScale) {
      checkArgument(0 <= blueScale, "Blue scale needs to be non-negative.");
      this.blueScale = blueScale;
      return this;
    }

    /** Creates a new {@link RgbAdjustment} instance. */
    public RgbAdjustment build() {
      float[] rgbMatrix = GlUtil.create4x4IdentityMatrix();
      Matrix.scaleM(
          rgbMatrix, /* smOffset= */ 0, /* x= */ redScale, /* y= */ greenScale, /* z= */ blueScale);

      return new RgbAdjustment(rgbMatrix);
    }
  }

  private final float[] rgbMatrix;

  private RgbAdjustment(float[] rgbMatrix) {
    this.rgbMatrix = rgbMatrix;
  }

  @Override
  public float[] getMatrix(long presentationTimeUs, boolean useHdr) {
    return rgbMatrix;
  }
}
