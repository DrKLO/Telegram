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
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.Matrix;
import android.util.Pair;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Controls how a frame is presented with options to set the output resolution and choose how to map
 * the input pixels onto the output frame geometry (for example, by stretching the input frame to
 * match the specified output frame, or fitting the input frame using letterboxing).
 *
 * <p>The background color of the output frame will be black, with alpha = 0 if applicable.
 */
public final class Presentation implements MatrixTransformation {

  /**
   * Strategies controlling the layout of input pixels in the output frame.
   *
   * <p>One of {@link #LAYOUT_SCALE_TO_FIT}, {@link #LAYOUT_SCALE_TO_FIT_WITH_CROP}, or {@link
   * #LAYOUT_STRETCH_TO_FIT}.
   *
   * <p>May scale either width or height, leaving the other output dimension equal to its input.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({LAYOUT_SCALE_TO_FIT, LAYOUT_SCALE_TO_FIT_WITH_CROP, LAYOUT_STRETCH_TO_FIT})
  public @interface Layout {}
  /**
   * Empty pixels added above and below the input frame (for letterboxing), or to the left and right
   * of the input frame (for pillarboxing), until the desired aspect ratio is achieved. All input
   * frame pixels will be within the output frame.
   *
   * <p>When applying:
   *
   * <ul>
   *   <li>letterboxing, the output width will default to the input width, and the output height
   *       will be scaled appropriately.
   *   <li>pillarboxing, the output height will default to the input height, and the output width
   *       will be scaled appropriately.
   * </ul>
   */
  public static final int LAYOUT_SCALE_TO_FIT = 0;
  /**
   * Pixels cropped from the input frame, until the desired aspect ratio is achieved. Pixels may be
   * cropped either from the bottom and top, or from the left and right sides, of the input frame.
   *
   * <p>When cropping from the:
   *
   * <ul>
   *   <li>bottom and top, the output width will default to the input width, and the output height
   *       will be scaled appropriately.
   *   <li>left and right, the output height will default to the input height, and the output width
   *       will be scaled appropriately.
   * </ul>
   */
  public static final int LAYOUT_SCALE_TO_FIT_WITH_CROP = 1;
  /**
   * Frame stretched larger on the x or y axes to fit the desired aspect ratio.
   *
   * <p>When stretching to a:
   *
   * <ul>
   *   <li>taller aspect ratio, the output width will default to the input width, and the output
   *       height will be scaled appropriately.
   *   <li>narrower aspect ratio, the output height will default to the input height, and the output
   *       width will be scaled appropriately.
   * </ul>
   */
  public static final int LAYOUT_STRETCH_TO_FIT = 2;

  private static final float ASPECT_RATIO_UNSET = -1f;

  private static void checkLayout(@Layout int layout) {
    checkArgument(
        layout == LAYOUT_SCALE_TO_FIT
            || layout == LAYOUT_SCALE_TO_FIT_WITH_CROP
            || layout == LAYOUT_STRETCH_TO_FIT,
        "invalid layout " + layout);
  }

  /**
   * Creates a new {@link Presentation} instance.
   *
   * <p>The output frame will have the given aspect ratio (width/height ratio). Width or height will
   * be resized to conform to this {@code aspectRatio}, given a {@link Layout}.
   *
   * @param aspectRatio The aspect ratio (width/height ratio) of the output frame. Must be positive.
   * @param layout The layout of the output frame.
   */
  public static Presentation createForAspectRatio(float aspectRatio, @Layout int layout) {
    checkArgument(
        aspectRatio == C.LENGTH_UNSET || aspectRatio > 0,
        "aspect ratio " + aspectRatio + " must be positive or unset");
    checkLayout(layout);
    return new Presentation(
        /* width= */ C.LENGTH_UNSET, /* height= */ C.LENGTH_UNSET, aspectRatio, layout);
  }

  /**
   * Creates a new {@link Presentation} instance.
   *
   * <p>The output frame will have the given height. Width will scale to preserve the input aspect
   * ratio.
   *
   * @param height The height of the output frame, in pixels.
   */
  public static Presentation createForHeight(int height) {
    return new Presentation(
        /* width= */ C.LENGTH_UNSET, height, ASPECT_RATIO_UNSET, LAYOUT_SCALE_TO_FIT);
  }

  /**
   * Creates a new {@link Presentation} instance.
   *
   * <p>The output frame will have the given width and height, given a {@link Layout}.
   *
   * <p>Width and height must be positive integers representing the output frame's width and height.
   *
   * @param width The width of the output frame, in pixels.
   * @param height The height of the output frame, in pixels.
   * @param layout The layout of the output frame.
   */
  public static Presentation createForWidthAndHeight(int width, int height, @Layout int layout) {
    checkArgument(width > 0, "width " + width + " must be positive");
    checkArgument(height > 0, "height " + height + " must be positive");
    checkLayout(layout);
    return new Presentation(width, height, ASPECT_RATIO_UNSET, layout);
  }

  private final int requestedWidthPixels;
  private final int requestedHeightPixels;
  private float requestedAspectRatio;
  private final @Layout int layout;

  private float outputWidth;
  private float outputHeight;
  private @MonotonicNonNull Matrix transformationMatrix;

  private Presentation(int width, int height, float aspectRatio, @Layout int layout) {
    checkArgument(
        (aspectRatio == C.LENGTH_UNSET) || (width == C.LENGTH_UNSET),
        "width and aspect ratio should not both be set");

    this.requestedWidthPixels = width;
    this.requestedHeightPixels = height;
    this.requestedAspectRatio = aspectRatio;
    this.layout = layout;

    outputWidth = C.LENGTH_UNSET;
    outputHeight = C.LENGTH_UNSET;
    transformationMatrix = new Matrix();
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    transformationMatrix = new Matrix();
    outputWidth = inputWidth;
    outputHeight = inputHeight;

    if ((requestedWidthPixels != C.LENGTH_UNSET) && (requestedHeightPixels != C.LENGTH_UNSET)) {
      requestedAspectRatio = (float) requestedWidthPixels / requestedHeightPixels;
    }

    if (requestedAspectRatio != C.LENGTH_UNSET) {
      applyAspectRatio();
    }

    // Scale output width and height to requested values.
    if (requestedHeightPixels != C.LENGTH_UNSET) {
      if (requestedWidthPixels != C.LENGTH_UNSET) {
        outputWidth = requestedWidthPixels;
      } else {
        outputWidth = requestedHeightPixels * outputWidth / outputHeight;
      }
      outputHeight = requestedHeightPixels;
    }
    return Pair.create(Math.round(outputWidth), Math.round(outputHeight));
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return checkStateNotNull(transformationMatrix, "configure must be called first");
  }

  @RequiresNonNull("transformationMatrix")
  private void applyAspectRatio() {
    float inputAspectRatio = outputWidth / outputHeight;
    if (layout == LAYOUT_SCALE_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = outputHeight * requestedAspectRatio;
      } else {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = outputWidth / requestedAspectRatio;
      }
    } else if (layout == LAYOUT_SCALE_TO_FIT_WITH_CROP) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = outputWidth / requestedAspectRatio;
      } else {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = outputHeight * requestedAspectRatio;
      }
    } else if (layout == LAYOUT_STRETCH_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        outputWidth = outputHeight * requestedAspectRatio;
      } else {
        outputHeight = outputWidth / requestedAspectRatio;
      }
    }
  }
}
