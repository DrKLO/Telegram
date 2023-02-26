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
import android.opengl.GLES20;
import android.util.Pair;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;

/** Applies the {@link HslAdjustment} to each frame in the fragment shader. */
/* package */ final class HslProcessor extends SingleFrameGlTextureProcessor {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_hsl_es2.glsl";

  private final GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param hslAdjustment The {@link HslAdjustment} to apply to each frame in order.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @throws FrameProcessingException If a problem occurs while reading shader files.
   */
  public HslProcessor(Context context, HslAdjustment hslAdjustment, boolean useHdr)
      throws FrameProcessingException {
    super(useHdr);
    // TODO(b/241241680): Check if HDR <-> HSL works the same or not.
    checkArgument(!useHdr, "HDR is not yet supported.");

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);

    // OpenGL operates in a [0, 1] unit range and thus we transform the HSL intervals into
    // the unit interval as well. The hue is defined in the [0, 360] interval and saturation
    // and lightness in the [0, 100] interval.
    glProgram.setFloatUniform("uHueAdjustmentDegrees", hslAdjustment.hueAdjustmentDegrees / 360);
    glProgram.setFloatUniform("uSaturationAdjustment", hslAdjustment.saturationAdjustment / 100);
    glProgram.setFloatUniform("uLightnessAdjustment", hslAdjustment.lightnessAdjustment / 100);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return Pair.create(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }
}
