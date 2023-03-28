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
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Applies a sequence of {@link MatrixTransformation MatrixTransformations} in the vertex shader and
 * a sequence of {@link RgbMatrix RgbMatrices} in the fragment shader. Copies input pixels into an
 * output frame based on their locations after applying the sequence of transformation matrices.
 *
 * <p>{@link MatrixTransformation} operations are done on normalized device coordinates (-1 to 1 on
 * x, y, and z axes). Transformed vertices that are moved outside of this range after any of the
 * transformation matrices are clipped to the NDC range.
 *
 * <p>After applying all {@link RgbMatrix} instances, color values are clamped to the limits of the
 * color space (e.g. BT.709 for SDR). Intermediate results are not clamped.
 *
 * <p>The background color of the output frame will be (r=0, g=0, b=0, a=0).
 *
 * <p>Can copy frames from an external texture and apply color transformations for HDR if needed.
 */
@SuppressWarnings("FunctionalInterfaceClash") // b/228192298
/* package */ final class MatrixTextureProcessor extends SingleFrameGlTextureProcessor
    implements ExternalTextureProcessor {

  private static final String VERTEX_SHADER_TRANSFORMATION_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String VERTEX_SHADER_TRANSFORMATION_ES3_PATH =
      "shaders/vertex_shader_transformation_es3.glsl";
  private static final String FRAGMENT_SHADER_TRANSFORMATION_PATH =
      "shaders/fragment_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_OETF_ES3_PATH =
      "shaders/fragment_shader_oetf_es3.glsl";
  private static final String FRAGMENT_SHADER_TRANSFORMATION_SDR_OETF_ES2_PATH =
      "shaders/fragment_shader_transformation_sdr_oetf_es2.glsl";
  private static final String FRAGMENT_SHADER_TRANSFORMATION_EXTERNAL_YUV_ES3_PATH =
      "shaders/fragment_shader_transformation_external_yuv_es3.glsl";
  private static final String FRAGMENT_SHADER_TRANSFORMATION_SDR_EXTERNAL_PATH =
      "shaders/fragment_shader_transformation_sdr_external_es2.glsl";
  private static final ImmutableList<float[]> NDC_SQUARE =
      ImmutableList.of(
          new float[] {-1, -1, 0, 1},
          new float[] {-1, 1, 0, 1},
          new float[] {1, 1, 0, 1},
          new float[] {1, -1, 0, 1});

  // YUV to RGB color transform coefficients can be calculated from the BT.2020 specification, by
  // inverting the RGB to YUV equations, and scaling for limited range.
  // https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.2020-2-201510-I!!PDF-E.pdf
  private static final float[] BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX = {
    1.0000f, 1.0000f, 1.0000f,
    0.0000f, -0.1646f, 1.8814f,
    1.4746f, -0.5714f, 0.0000f
  };
  private static final float[] BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX = {
    1.1689f, 1.1689f, 1.1689f,
    0.0000f, -0.1881f, 2.1502f,
    1.6853f, -0.6530f, 0.0000f,
  };

  /** The {@link MatrixTransformation MatrixTransformations} to apply. */
  private final ImmutableList<GlMatrixTransformation> matrixTransformations;
  /** The {@link RgbMatrix RgbMatrices} to apply. */
  private final ImmutableList<RgbMatrix> rgbMatrices;
  /** Whether the frame is in HDR or not. */
  private final boolean useHdr;
  /**
   * The transformation matrices provided by the {@link MatrixTransformation MatrixTransformations}
   * for the most recent frame.
   */
  private final float[][] transformationMatrixCache;
  /** The RGB matrices provided by the {@link RgbMatrix RgbMatrices} for the most recent frame. */
  private final float[][] rgbMatrixCache;
  /**
   * The product of the {@link #transformationMatrixCache} for the most recent frame, to be applied
   * in the vertex shader.
   */
  private final float[] compositeTransformationMatrixArray;
  /**
   * The product of the {@link #rgbMatrixCache} for the most recent frame, to be applied in the
   * fragment shader.
   */
  private final float[] compositeRgbMatrixArray;
  /** Matrix for storing an intermediate calculation result. */
  private final float[] tempResultMatrix;

  /**
   * A polygon in the input space chosen such that no additional clipping is needed to keep vertices
   * inside the NDC range when applying each of the {@link #matrixTransformations}.
   *
   * <p>This means that this polygon and {@link #compositeTransformationMatrixArray} can be used
   * instead of applying each of the {@link #matrixTransformations} to {@link #NDC_SQUARE} in
   * separate shaders.
   */
  private ImmutableList<float[]> visiblePolygon;

  private final GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * <p>Input and output are both intermediate optical/linear colors, and RGB BT.2020 if {@code
   * useHdr} is {@code true} and RGB BT.709 if not.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order. Can be empty to apply no vertex transformations.
   * @param rgbMatrices The {@link RgbMatrix RgbMatrices} to apply to each frame in order. Can be
   *     empty to apply no color transformations.
   * @param useHdr Whether input and output colors are HDR.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTextureProcessor create(
      Context context,
      List<GlMatrixTransformation> matrixTransformations,
      List<RgbMatrix> rgbMatrices,
      boolean useHdr)
      throws FrameProcessingException {
    GlProgram glProgram =
        createGlProgram(
            context, VERTEX_SHADER_TRANSFORMATION_PATH, FRAGMENT_SHADER_TRANSFORMATION_PATH);

    // No transfer functions needed, because input and output are both optical colors.
    // TODO(b/241902517): Add transfer functions since existing color filters may change the colors.
    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        ImmutableList.copyOf(rgbMatrices),
        useHdr);
  }

  /**
   * Creates a new instance.
   *
   * <p>Input will be sampled from an external texture. The caller should use {@link
   * #setTextureTransformMatrix(float[])} to provide the transformation matrix associated with the
   * external texture.
   *
   * <p>Applies the {@code electricalColorInfo} EOTF to convert from electrical color input, to
   * intermediate optical {@link GlTextureProcessor} color output, before {@code
   * matrixTransformations} and {@code rgbMatrices} are applied.
   *
   * <p>Intermediate optical/linear colors are RGB BT.2020 if {@code electricalColorInfo} is
   * {@linkplain ColorInfo#isTransferHdr(ColorInfo) HDR}, and RGB BT.709 if not.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order. Can be empty to apply no vertex transformations.
   * @param rgbMatrices The {@link RgbMatrix RgbMatrices} to apply to each frame in order. Can be
   *     empty to apply no color transformations.
   * @param electricalColorInfo The electrical {@link ColorInfo} describing input colors.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTextureProcessor createWithExternalSamplerApplyingEotf(
      Context context,
      List<GlMatrixTransformation> matrixTransformations,
      List<RgbMatrix> rgbMatrices,
      ColorInfo electricalColorInfo)
      throws FrameProcessingException {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        useHdr
            ? FRAGMENT_SHADER_TRANSFORMATION_EXTERNAL_YUV_ES3_PATH
            : FRAGMENT_SHADER_TRANSFORMATION_SDR_EXTERNAL_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      if (!GlUtil.isYuvTargetExtensionSupported()) {
        throw new FrameProcessingException(
            "The EXT_YUV_target extension is required for HDR editing input.");
      }
      glProgram.setFloatsUniform(
          "uYuvToRgbColorTransform",
          electricalColorInfo.colorRange == C.COLOR_RANGE_FULL
              ? BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX
              : BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX);

      @C.ColorTransfer int colorTransfer = electricalColorInfo.colorTransfer;
      checkArgument(
          colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084);
      glProgram.setIntUniform("uEotfColorTransfer", colorTransfer);
    } else {
      glProgram.setIntUniform("uApplyOetf", 0);
    }

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        ImmutableList.copyOf(rgbMatrices),
        useHdr);
  }

  /**
   * Creates a new instance.
   *
   * <p>Applies the {@code electricalColorInfo} OETF to convert from intermediate optical {@link
   * GlTextureProcessor} color input, to electrical color output, after {@code
   * matrixTransformations} and {@code rgbMatrices} are applied.
   *
   * <p>Intermediate optical/linear colors are RGB BT.2020 if {@code electricalColorInfo} is
   * {@linkplain ColorInfo#isTransferHdr(ColorInfo) HDR}, and RGB BT.709 if not.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order. Can be empty to apply no vertex transformations.
   * @param rgbMatrices The {@link RgbMatrix RgbMatrices} to apply to each frame in order. Can be
   *     empty to apply no color transformations.
   * @param electricalColorInfo The electrical {@link ColorInfo} describing output colors.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTextureProcessor createApplyingOetf(
      Context context,
      List<GlMatrixTransformation> matrixTransformations,
      List<RgbMatrix> rgbMatrices,
      ColorInfo electricalColorInfo)
      throws FrameProcessingException {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        useHdr ? FRAGMENT_SHADER_OETF_ES3_PATH : FRAGMENT_SHADER_TRANSFORMATION_SDR_OETF_ES2_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    if (useHdr) {
      @C.ColorTransfer int colorTransfer = electricalColorInfo.colorTransfer;
      checkArgument(
          colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084);
      glProgram.setIntUniform("uOetfColorTransfer", colorTransfer);
    }

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        ImmutableList.copyOf(rgbMatrices),
        useHdr);
  }

  /**
   * Creates a new instance.
   *
   * <p>Input will be sampled from an external texture. The caller should use {@link
   * #setTextureTransformMatrix(float[])} to provide the transformation matrix associated with the
   * external texture.
   *
   * <p>Applies the EOTF, {@code matrixTransformations}, {@code rgbMatrices}, then the OETF, to
   * convert from and to input and output electrical colors.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order. Can be empty to apply no vertex transformations.
   * @param rgbMatrices The {@link RgbMatrix RgbMatrices} to apply to each frame in order. Can be
   *     empty to apply no color transformations.
   * @param electricalColorInfo The electrical {@link ColorInfo} describing input and output colors.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTextureProcessor createWithExternalSamplerApplyingEotfThenOetf(
      Context context,
      List<GlMatrixTransformation> matrixTransformations,
      List<RgbMatrix> rgbMatrices,
      ColorInfo electricalColorInfo)
      throws FrameProcessingException {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        useHdr
            ? FRAGMENT_SHADER_TRANSFORMATION_EXTERNAL_YUV_ES3_PATH
            : FRAGMENT_SHADER_TRANSFORMATION_SDR_EXTERNAL_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      if (!GlUtil.isYuvTargetExtensionSupported()) {
        throw new FrameProcessingException(
            "The EXT_YUV_target extension is required for HDR editing input.");
      }
      glProgram.setFloatsUniform(
          "uYuvToRgbColorTransform",
          electricalColorInfo.colorRange == C.COLOR_RANGE_FULL
              ? BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX
              : BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX);

      // No transfer functions needed, because the EOTF and OETF cancel out.
      glProgram.setIntUniform("uEotfColorTransfer", Format.NO_VALUE);
    } else {
      glProgram.setIntUniform("uApplyOetf", 1);
    }

    return new MatrixTextureProcessor(
        glProgram,
        ImmutableList.copyOf(matrixTransformations),
        ImmutableList.copyOf(rgbMatrices),
        useHdr);
  }

  /**
   * Creates a new instance.
   *
   * @param glProgram The {@link GlProgram}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order. Can be empty to apply no vertex transformations.
   * @param rgbMatrices The {@link RgbMatrix RgbMatrices} to apply to each frame in order. Can be
   *     empty to apply no color transformations.
   * @param useHdr Whether to process the input as an HDR signal. Using HDR requires the {@code
   *     EXT_YUV_target} OpenGL extension.
   */
  private MatrixTextureProcessor(
      GlProgram glProgram,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      ImmutableList<RgbMatrix> rgbMatrices,
      boolean useHdr) {
    super(useHdr);
    this.glProgram = glProgram;
    this.matrixTransformations = matrixTransformations;
    this.rgbMatrices = rgbMatrices;
    this.useHdr = useHdr;

    transformationMatrixCache = new float[matrixTransformations.size()][16];
    rgbMatrixCache = new float[rgbMatrices.size()][16];
    compositeTransformationMatrixArray = GlUtil.create4x4IdentityMatrix();
    compositeRgbMatrixArray = GlUtil.create4x4IdentityMatrix();
    tempResultMatrix = new float[16];
    visiblePolygon = NDC_SQUARE;
  }

  private static GlProgram createGlProgram(
      Context context, String vertexShaderFilePath, String fragmentShaderFilePath)
      throws FrameProcessingException {

    GlProgram glProgram;
    try {
      glProgram = new GlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    return glProgram;
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    glProgram.setFloatsUniform("uTexTransformationMatrix", textureTransformMatrix);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return MatrixUtils.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    updateCompositeRgbaMatrixArray(presentationTimeUs);
    updateCompositeTransformationMatrixAndVisiblePolygon(presentationTimeUs);
    if (visiblePolygon.size() < 3) {
      return; // Need at least three visible vertices for a triangle.
    }

    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.setFloatsUniform("uTransformationMatrix", compositeTransformationMatrixArray);
      glProgram.setFloatsUniform("uRgbMatrix", compositeRgbMatrixArray);
      glProgram.setBufferAttribute(
          "aFramePosition",
          GlUtil.createVertexBuffer(visiblePolygon),
          GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
      glProgram.bindAttributesAndUniforms();
      GLES20.glDrawArrays(
          GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* count= */ visiblePolygon.size());
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() throws FrameProcessingException {
    super.release();
    try {
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }
  }

  /**
   * Updates {@link #compositeTransformationMatrixArray} and {@link #visiblePolygon} based on the
   * given frame timestamp.
   */
  private void updateCompositeTransformationMatrixAndVisiblePolygon(long presentationTimeUs) {
    float[][] matricesAtPresentationTime = new float[matrixTransformations.size()][16];
    for (int i = 0; i < matrixTransformations.size(); i++) {
      matricesAtPresentationTime[i] =
          matrixTransformations.get(i).getGlMatrixArray(presentationTimeUs);
    }

    if (!updateMatrixCache(transformationMatrixCache, matricesAtPresentationTime)) {
      return;
    }

    // Compute the compositeTransformationMatrix and transform and clip the visiblePolygon for each
    // MatrixTransformation's matrix.
    GlUtil.setToIdentity(compositeTransformationMatrixArray);
    visiblePolygon = NDC_SQUARE;
    for (float[] transformationMatrix : transformationMatrixCache) {
      Matrix.multiplyMM(
          /* result= */ tempResultMatrix,
          /* resultOffset= */ 0,
          /* lhs= */ transformationMatrix,
          /* lhsOffset= */ 0,
          /* rhs= */ compositeTransformationMatrixArray,
          /* rhsOffset= */ 0);
      System.arraycopy(
          /* src= */ tempResultMatrix,
          /* srcPos= */ 0,
          /* dest= */ compositeTransformationMatrixArray,
          /* destPost= */ 0,
          /* length= */ tempResultMatrix.length);
      visiblePolygon =
          MatrixUtils.clipConvexPolygonToNdcRange(
              MatrixUtils.transformPoints(transformationMatrix, visiblePolygon));
      if (visiblePolygon.size() < 3) {
        // Can ignore remaining matrices as there are not enough vertices left to form a polygon.
        return;
      }
    }
    // Calculate the input frame vertices corresponding to the output frame's visible polygon.
    Matrix.invertM(
        tempResultMatrix,
        /* mInvOffset= */ 0,
        compositeTransformationMatrixArray,
        /* mOffset= */ 0);
    visiblePolygon = MatrixUtils.transformPoints(tempResultMatrix, visiblePolygon);
  }

  /** Updates {@link #compositeRgbMatrixArray} based on the given frame timestamp. */
  private void updateCompositeRgbaMatrixArray(long presentationTimeUs) {
    float[][] matricesCurrTimestamp = new float[rgbMatrices.size()][16];
    for (int i = 0; i < rgbMatrices.size(); i++) {
      matricesCurrTimestamp[i] = rgbMatrices.get(i).getMatrix(presentationTimeUs, useHdr);
    }

    if (!updateMatrixCache(rgbMatrixCache, matricesCurrTimestamp)) {
      return;
    }

    for (int i = 0; i < rgbMatrices.size(); i++) {
      Matrix.multiplyMM(
          /* result= */ tempResultMatrix,
          /* resultOffset= */ 0,
          /* lhs= */ rgbMatrices.get(i).getMatrix(presentationTimeUs, useHdr),
          /* lhsOffset= */ 0,
          /* rhs= */ compositeRgbMatrixArray,
          /* rhsOffset= */ 0);
      System.arraycopy(
          /* src= */ tempResultMatrix,
          /* srcPos= */ 0,
          /* dest= */ compositeRgbMatrixArray,
          /* destPost= */ 0,
          /* length= */ tempResultMatrix.length);
    }
  }

  /**
   * Updates the {@code cachedMatrices} with the {@code newMatrices}. Returns whether a matrix has
   * changed inside the cache.
   *
   * @param cachedMatrices The existing cached matrices. Gets updated if it is out of date.
   * @param newMatrices The new matrices to compare the cached matrices against.
   */
  private static boolean updateMatrixCache(float[][] cachedMatrices, float[][] newMatrices) {
    boolean matrixChanged = false;
    for (int i = 0; i < cachedMatrices.length; i++) {
      float[] cachedMatrix = cachedMatrices[i];
      float[] newMatrix = newMatrices[i];
      if (!Arrays.equals(cachedMatrix, newMatrix)) {
        checkState(newMatrix.length == 16, "A 4x4 transformation matrix must have 16 elements");
        System.arraycopy(
            /* src= */ newMatrix,
            /* srcPos= */ 0,
            /* dest= */ cachedMatrix,
            /* destPost= */ 0,
            /* length= */ newMatrix.length);
        matrixChanged = true;
      }
    }
    return matrixChanged;
  }
}
