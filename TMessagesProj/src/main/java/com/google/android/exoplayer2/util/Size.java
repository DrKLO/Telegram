/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;

/** Immutable class for describing width and height dimensions in pixels. */
public final class Size {

  /** A static instance to represent an unknown size value. */
  public static final Size UNKNOWN =
      new Size(/* width= */ C.LENGTH_UNSET, /* height= */ C.LENGTH_UNSET);

  /* A static instance to represent a size of zero height and width. */
  public static final Size ZERO = new Size(/* width= */ 0, /* height= */ 0);

  private final int width;
  private final int height;

  /**
   * Creates a new immutable Size instance.
   *
   * @param width The width of the size, in pixels, or {@link C#LENGTH_UNSET} if unknown.
   * @param height The height of the size, in pixels, or {@link C#LENGTH_UNSET} if unknown.
   * @throws IllegalArgumentException if an invalid {@code width} or {@code height} is specified.
   */
  public Size(int width, int height) {
    checkArgument(
        (width == C.LENGTH_UNSET || width >= 0) && (height == C.LENGTH_UNSET || height >= 0));

    this.width = width;
    this.height = height;
  }

  /** Returns the width of the size (in pixels), or {@link C#LENGTH_UNSET} if unknown. */
  public int getWidth() {
    return width;
  }

  /** Returns the height of the size (in pixels), or {@link C#LENGTH_UNSET} if unknown. */
  public int getHeight() {
    return height;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == null) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (obj instanceof Size) {
      Size other = (Size) obj;
      return width == other.width && height == other.height;
    }
    return false;
  }

  @Override
  public String toString() {
    return width + "x" + height;
  }

  @Override
  public int hashCode() {
    // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
    return height ^ ((width << (Integer.SIZE / 2)) | (width >>> (Integer.SIZE / 2)));
  }
}
