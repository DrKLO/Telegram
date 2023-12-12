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

import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import com.google.android.exoplayer2.util.FrameProcessingException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Provides common color filters. */
public class RgbFilter implements RgbMatrix {
  private static final int COLOR_FILTER_GRAYSCALE_INDEX = 1;
  private static final int COLOR_FILTER_INVERTED_INDEX = 2;

  // Grayscale transformation matrix using the BT.709 luminance coefficients from
  // https://en.wikipedia.org/wiki/Grayscale#Converting_colour_to_grayscale
  private static final float[] FILTER_MATRIX_GRAYSCALE_SDR = {
    0.2126f, 0.2126f, 0.2126f, 0, 0.7152f, 0.7152f, 0.7152f, 0, 0.0722f, 0.0722f, 0.0722f, 0, 0, 0,
    0, 1
  };
  // Grayscale transformation using the BT.2020 primary colors from
  // https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.2020-2-201510-I!!PDF-E.pdf
  // TODO(b/241240659): Add HDR tests once infrastructure supports it.
  private static final float[] FILTER_MATRIX_GRAYSCALE_HDR = {
    0.2627f, 0.2627f, 0.2627f, 0, 0.6780f, 0.6780f, 0.6780f, 0, 0.0593f, 0.0593f, 0.0593f, 0, 0, 0,
    0, 1
  };
  // Inverted filter uses the transformation R' = -R + 1 = 1 - R.
  private static final float[] FILTER_MATRIX_INVERTED = {
    -1, 0, 0, 0, 0, -1, 0, 0, 0, 0, -1, 0, 1, 1, 1, 1
  };

  private final int colorFilter;
  /**
   * Ensures that the usage of HDR is consistent. {@code null} indicates that HDR has not yet been
   * set.
   */
  private @MonotonicNonNull Boolean useHdr;

  /** Creates a new grayscale {@code RgbFilter} instance. */
  public static RgbFilter createGrayscaleFilter() {
    return new RgbFilter(COLOR_FILTER_GRAYSCALE_INDEX);
  }

  /** Creates a new inverted {@code RgbFilter} instance. */
  public static RgbFilter createInvertedFilter() {
    return new RgbFilter(COLOR_FILTER_INVERTED_INDEX);
  }

  private RgbFilter(int colorFilter) {
    this.colorFilter = colorFilter;
  }

  private void checkForConsistentHdrSetting(boolean useHdr) {
    if (this.useHdr == null) {
      this.useHdr = useHdr;
    } else {
      checkState(this.useHdr == useHdr, "Changing HDR setting is not supported.");
    }
  }

  @Override
  public float[] getMatrix(long presentationTimeUs, boolean useHdr) {
    checkForConsistentHdrSetting(useHdr);
    switch (colorFilter) {
      case COLOR_FILTER_GRAYSCALE_INDEX:
        return useHdr ? FILTER_MATRIX_GRAYSCALE_HDR : FILTER_MATRIX_GRAYSCALE_SDR;
      case COLOR_FILTER_INVERTED_INDEX:
        return FILTER_MATRIX_INVERTED;
      default:
        // Should never happen.
        throw new IllegalStateException("Invalid color filter " + colorFilter);
    }
  }

  @Override
  public SingleFrameGlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr)
      throws FrameProcessingException {
    checkForConsistentHdrSetting(useHdr);
    return RgbMatrix.super.toGlTextureProcessor(context, useHdr);
  }
}
