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
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.graphics.Matrix;
import android.util.Pair;
import com.google.android.exoplayer2.util.GlUtil;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Specifies a crop to apply in the vertex shader.
 *
 * <p>The background color of the output frame will be black, with alpha = 0 if applicable.
 */
public final class Crop implements MatrixTransformation {

  private final float left;
  private final float right;
  private final float bottom;
  private final float top;

  private @MonotonicNonNull Matrix transformationMatrix;

  /**
   * Crops a smaller (or larger) frame, per normalized device coordinates (NDC), where the input
   * frame corresponds to the square ranging from -1 to 1 on the x and y axes.
   *
   * <p>{@code left} and {@code bottom} default to -1, and {@code right} and {@code top} default to
   * 1, which corresponds to not applying any crop. To crop to a smaller subset of the input frame,
   * use values between -1 and 1. To crop to a larger frame, use values below -1 and above 1.
   *
   * @param left The left edge of the output frame, in NDC. Must be less than {@code right}.
   * @param right The right edge of the output frame, in NDC. Must be greater than {@code left}.
   * @param bottom The bottom edge of the output frame, in NDC. Must be less than {@code top}.
   * @param top The top edge of the output frame, in NDC. Must be greater than {@code bottom}.
   */
  public Crop(float left, float right, float bottom, float top) {
    checkArgument(
        right > left, "right value " + right + " should be greater than left value " + left);
    checkArgument(
        top > bottom, "top value " + top + " should be greater than bottom value " + bottom);
    this.left = left;
    this.right = right;
    this.bottom = bottom;
    this.top = top;

    transformationMatrix = new Matrix();
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    transformationMatrix = new Matrix();
    if (left == -1f && right == 1f && bottom == -1f && top == 1f) {
      // No crop needed.
      return Pair.create(inputWidth, inputHeight);
    }

    float scaleX = (right - left) / GlUtil.LENGTH_NDC;
    float scaleY = (top - bottom) / GlUtil.LENGTH_NDC;
    float centerX = (left + right) / 2;
    float centerY = (bottom + top) / 2;

    transformationMatrix.postTranslate(-centerX, -centerY);
    transformationMatrix.postScale(1f / scaleX, 1f / scaleY);

    int outputWidth = Math.round(inputWidth * scaleX);
    int outputHeight = Math.round(inputHeight * scaleY);
    return Pair.create(outputWidth, outputHeight);
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return checkStateNotNull(transformationMatrix, "configure must be called first");
  }
}
