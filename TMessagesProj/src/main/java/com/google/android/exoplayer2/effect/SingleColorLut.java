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
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;

/** Transforms the colors of a frame by applying the same color lookup table to each frame. */
public class SingleColorLut implements ColorLut {
  private final Bitmap lut;
  private int lutTextureId;

  /**
   * Creates a new instance.
   *
   * <p>{@code lutCube} needs to be a {@code N x N x N} cube and each element is an integer
   * representing a color using the {@link Bitmap.Config#ARGB_8888} format.
   */
  public static SingleColorLut createFromCube(int[][][] lutCube) {
    checkArgument(
        lutCube.length > 0 && lutCube[0].length > 0 && lutCube[0][0].length > 0,
        "LUT must have three dimensions.");
    checkArgument(
        lutCube.length == lutCube[0].length && lutCube.length == lutCube[0][0].length,
        Util.formatInvariant(
            "All three dimensions of a LUT must match, received %d x %d x %d.",
            lutCube.length, lutCube[0].length, lutCube[0][0].length));

    return new SingleColorLut(transformCubeIntoBitmap(lutCube));
  }

  /**
   * Creates a new instance.
   *
   * <p>LUT needs to be a Bitmap of a flattened HALD image of width {@code N} and height {@code
   * N^2}. Each element must be an integer representing a color using the {@link
   * Bitmap.Config#ARGB_8888} format.
   */
  public static SingleColorLut createFromBitmap(Bitmap lut) {
    checkArgument(
        lut.getWidth() * lut.getWidth() == lut.getHeight(),
        Util.formatInvariant(
            "LUT needs to be in a N x N^2 format, received %d x %d.",
            lut.getWidth(), lut.getHeight()));
    checkArgument(
        lut.getConfig() == Bitmap.Config.ARGB_8888, "Color representation needs to be ARGB_8888.");

    return new SingleColorLut(lut);
  }

  private SingleColorLut(Bitmap lut) {
    this.lut = lut;
    lutTextureId = Format.NO_VALUE;
  }

  /**
   * Transforms the N x N x N {@code cube} into a N x N^2 {@code bitmap}.
   *
   * @param cube The 3D Color Lut which gets indexed using {@code cube[R][G][B]}.
   * @return A {@link Bitmap} of size {@code N x N^2}, where the {@code cube[R][G][B]} color can be
   *     indexed at {@code bitmap.getColor(B, N * R + G)}.
   */
  private static Bitmap transformCubeIntoBitmap(int[][][] cube) {
    // The support for 3D textures starts in OpenGL 3.0 and the Android API 8, Version 2.2
    // uses OpenGL 2.0 which only supports 2D textures. Thus we need to transform the 3D LUT
    // into 2D to support all Android SDKs.

    // The cube consists of N planes on the z-direction in the coordinate system where each plane
    // has a size of N x N. To transform the cube into a 2D bitmap we stack each N x N plane
    // vertically on top of each other. This gives us a bitmap of width N and height N^2.
    //
    // As an example, lets take the following 3D identity LUT of size 2x2x2:
    // cube = [
    //         [[(0, 0, 0), (0, 0, 1)],
    //          [(0, 1, 0), (0, 1, 1)]],
    //         [[(1, 0, 0), (1, 0, 1)],
    //          [(1, 1, 0), (1, 1, 1)]]
    //       ];
    // If we transform this cube now into a 2x2^2 = 2x4 bitmap we yield the following 2D plane:
    // bitmap = [[(0, 0, 0), (0, 0, 1)],
    //           [(0, 1, 0), (0, 1, 1)],
    //           [(1, 0, 0), (1, 0, 1)],
    //           [(1, 1, 0), (1, 1, 1)]];
    // media/bitmap/lut/identity.png is an example of how a 32x32x32 3D LUT looks like as an
    // 32x32^2 bitmap.
    int length = cube.length;
    int[] bitmapColorsArray = new int[length * length * length];

    for (int r = 0; r < length; r++) {
      for (int g = 0; g < length; g++) {
        for (int b = 0; b < length; b++) {
          int color = cube[r][g][b];
          int planePosition = b + length * (g + length * r);
          bitmapColorsArray[planePosition] = color;
        }
      }
    }

    return Bitmap.createBitmap(
        bitmapColorsArray,
        /* width= */ length,
        /* height= */ length * length,
        Bitmap.Config.ARGB_8888);
  }

  /** Must be called after {@link #toGlTextureProcessor(Context, boolean)}. */
  @Override
  public int getLutTextureId(long presentationTimeUs) {
    checkState(
        lutTextureId != Format.NO_VALUE,
        "The LUT has not been stored as a texture in OpenGL yet. You must to call"
            + " #toGlTextureProcessor() first.");
    return lutTextureId;
  }

  @Override
  public int getLength(long presentationTimeUs) {
    return lut.getWidth();
  }

  @Override
  public void release() throws GlUtil.GlException {
    GlUtil.deleteTexture(lutTextureId);
  }

  @Override
  public SingleFrameGlTextureProcessor toGlTextureProcessor(Context context, boolean useHdr)
      throws FrameProcessingException {
    checkState(!useHdr, "HDR is currently not supported.");

    try {
      lutTextureId = storeLutAsTexture(lut);
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException("Could not store the LUT as a texture.", e);
    }

    return new ColorLutProcessor(context, /* colorLut= */ this, useHdr);
  }

  private static int storeLutAsTexture(Bitmap bitmap) throws GlUtil.GlException {
    int lutTextureId =
        GlUtil.createTexture(
            bitmap.getWidth(), bitmap.getHeight(), /* useHighPrecisionColorComponents= */ false);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, bitmap, /* border= */ 0);
    GlUtil.checkGlError();
    return lutTextureId;
  }
}
