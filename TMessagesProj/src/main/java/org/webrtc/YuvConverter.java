/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.graphics.Matrix;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import org.webrtc.VideoFrame.I420Buffer;
import org.webrtc.VideoFrame.TextureBuffer;

/**
 * Class for converting OES textures to a YUV ByteBuffer. It can be constructed on any thread, but
 * should only be operated from a single thread with an active EGL context.
 */
public class YuvConverter {
  private static final String FRAGMENT_SHADER =
      // Difference in texture coordinate corresponding to one
      // sub-pixel in the x direction.
      "uniform vec2 xUnit;\n"
      // Color conversion coefficients, including constant term
      + "uniform vec4 coeffs;\n"
      + "\n"
      + "void main() {\n"
      // Since the alpha read from the texture is always 1, this could
      // be written as a mat4 x vec4 multiply. However, that seems to
      // give a worse framerate, possibly because the additional
      // multiplies by 1.0 consume resources. TODO(nisse): Could also
      // try to do it as a vec3 x mat3x4, followed by an add in of a
      // constant vector.
      + "  gl_FragColor.r = coeffs.a + dot(coeffs.rgb,\n"
      + "      sample(tc - 1.5 * xUnit).rgb);\n"
      + "  gl_FragColor.g = coeffs.a + dot(coeffs.rgb,\n"
      + "      sample(tc - 0.5 * xUnit).rgb);\n"
      + "  gl_FragColor.b = coeffs.a + dot(coeffs.rgb,\n"
      + "      sample(tc + 0.5 * xUnit).rgb);\n"
      + "  gl_FragColor.a = coeffs.a + dot(coeffs.rgb,\n"
      + "      sample(tc + 1.5 * xUnit).rgb);\n"
      + "}\n";

  private static class ShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
    // Y'UV444 to RGB888, see https://en.wikipedia.org/wiki/YUV#Y%E2%80%B2UV444_to_RGB888_conversion
    // We use the ITU-R BT.601 coefficients for Y, U and V.
    // The values in Wikipedia are inaccurate, the accurate values derived from the spec are:
    // Y = 0.299 * R + 0.587 * G + 0.114 * B
    // U = -0.168736 * R - 0.331264 * G + 0.5 * B + 0.5
    // V = 0.5 * R - 0.418688 * G - 0.0813124 * B + 0.5
    // To map the Y-values to range [16-235] and U- and V-values to range [16-240], the matrix has
    // been multiplied with matrix:
    // {{219 / 255, 0, 0, 16 / 255},
    // {0, 224 / 255, 0, 16 / 255},
    // {0, 0, 224 / 255, 16 / 255},
    // {0, 0, 0, 1}}
    private static final float[] yCoeffs =
        new float[] {0.256788f, 0.504129f, 0.0979059f, 0.0627451f};
    private static final float[] uCoeffs =
        new float[] {-0.148223f, -0.290993f, 0.439216f, 0.501961f};
    private static final float[] vCoeffs =
        new float[] {0.439216f, -0.367788f, -0.0714274f, 0.501961f};

    private int xUnitLoc;
    private int coeffsLoc;

    private float[] coeffs;
    private float stepSize;

    public void setPlaneY() {
      coeffs = yCoeffs;
      stepSize = 1.0f;
    }

    public void setPlaneU() {
      coeffs = uCoeffs;
      stepSize = 2.0f;
    }

    public void setPlaneV() {
      coeffs = vCoeffs;
      stepSize = 2.0f;
    }

    @Override
    public void onNewShader(GlShader shader) {
      xUnitLoc = shader.getUniformLocation("xUnit");
      coeffsLoc = shader.getUniformLocation("coeffs");
    }

    @Override
    public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth, int frameHeight,
        int viewportWidth, int viewportHeight) {
      GLES20.glUniform4fv(coeffsLoc, /* count= */ 1, coeffs, /* offset= */ 0);
      // Matrix * (1;0;0;0) / (width / stepSize). Note that OpenGL uses column major order.
      GLES20.glUniform2f(
          xUnitLoc, stepSize * texMatrix[0] / frameWidth, stepSize * texMatrix[1] / frameWidth);
    }
  }

  private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();
  private final GlTextureFrameBuffer i420TextureFrameBuffer =
      new GlTextureFrameBuffer(GLES20.GL_RGBA);
  private final ShaderCallbacks shaderCallbacks = new ShaderCallbacks();
  private final GlGenericDrawer drawer = new GlGenericDrawer(FRAGMENT_SHADER, shaderCallbacks);
  private final VideoFrameDrawer videoFrameDrawer;

  /**
   * This class should be constructed on a thread that has an active EGL context.
   */
  public YuvConverter() {
    this(new VideoFrameDrawer());
  }

  public YuvConverter(VideoFrameDrawer videoFrameDrawer) {
    this.videoFrameDrawer = videoFrameDrawer;
    threadChecker.detachThread();
  }

  /** Converts the texture buffer to I420. */
  public I420Buffer convert(TextureBuffer inputTextureBuffer) {
    threadChecker.checkIsOnValidThread();

    TextureBuffer preparedBuffer = (TextureBuffer) videoFrameDrawer.prepareBufferForViewportSize(
        inputTextureBuffer, inputTextureBuffer.getWidth(), inputTextureBuffer.getHeight());

    // We draw into a buffer laid out like
    //
    //    +---------+
    //    |         |
    //    |  Y      |
    //    |         |
    //    |         |
    //    +----+----+
    //    | U  | V  |
    //    |    |    |
    //    +----+----+
    //
    // In memory, we use the same stride for all of Y, U and V. The
    // U data starts at offset |height| * |stride| from the Y data,
    // and the V data starts at at offset |stride/2| from the U
    // data, with rows of U and V data alternating.
    //
    // Now, it would have made sense to allocate a pixel buffer with
    // a single byte per pixel (EGL10.EGL_COLOR_BUFFER_TYPE,
    // EGL10.EGL_LUMINANCE_BUFFER,), but that seems to be
    // unsupported by devices. So do the following hack: Allocate an
    // RGBA buffer, of width |stride|/4. To render each of these
    // large pixels, sample the texture at 4 different x coordinates
    // and store the results in the four components.
    //
    // Since the V data needs to start on a boundary of such a
    // larger pixel, it is not sufficient that |stride| is even, it
    // has to be a multiple of 8 pixels.
    final int frameWidth = preparedBuffer.getWidth();
    final int frameHeight = preparedBuffer.getHeight();
    final int stride = ((frameWidth + 7) / 8) * 8;
    final int uvHeight = (frameHeight + 1) / 2;
    // Total height of the combined memory layout.
    final int totalHeight = frameHeight + uvHeight;
    final ByteBuffer i420ByteBuffer = JniCommon.nativeAllocateByteBuffer(stride * totalHeight);
    // Viewport width is divided by four since we are squeezing in four color bytes in each RGBA
    // pixel.
    final int viewportWidth = stride / 4;

    // Produce a frame buffer starting at top-left corner, not bottom-left.
    final Matrix renderMatrix = new Matrix();
    renderMatrix.preTranslate(0.5f, 0.5f);
    renderMatrix.preScale(1f, -1f);
    renderMatrix.preTranslate(-0.5f, -0.5f);

    i420TextureFrameBuffer.setSize(viewportWidth, totalHeight);

    // Bind our framebuffer.
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, i420TextureFrameBuffer.getFrameBufferId());
    GlUtil.checkNoGLES2Error("glBindFramebuffer");

    // Draw Y.
    shaderCallbacks.setPlaneY();
    VideoFrameDrawer.drawTexture(drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight,
        /* viewportX= */ 0, /* viewportY= */ 0, viewportWidth,
        /* viewportHeight= */ frameHeight);

    // Draw U.
    shaderCallbacks.setPlaneU();
    VideoFrameDrawer.drawTexture(drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight,
        /* viewportX= */ 0, /* viewportY= */ frameHeight, viewportWidth / 2,
        /* viewportHeight= */ uvHeight);

    // Draw V.
    shaderCallbacks.setPlaneV();
    VideoFrameDrawer.drawTexture(drawer, preparedBuffer, renderMatrix, frameWidth, frameHeight,
        /* viewportX= */ viewportWidth / 2, /* viewportY= */ frameHeight, viewportWidth / 2,
        /* viewportHeight= */ uvHeight);

    GLES20.glReadPixels(0, 0, i420TextureFrameBuffer.getWidth(), i420TextureFrameBuffer.getHeight(),
        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, i420ByteBuffer);

    GlUtil.checkNoGLES2Error("YuvConverter.convert");

    // Restore normal framebuffer.
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

    // Prepare Y, U, and V ByteBuffer slices.
    final int yPos = 0;
    final int uPos = yPos + stride * frameHeight;
    // Rows of U and V alternate in the buffer, so V data starts after the first row of U.
    final int vPos = uPos + stride / 2;

    i420ByteBuffer.position(yPos);
    i420ByteBuffer.limit(yPos + stride * frameHeight);
    final ByteBuffer dataY = i420ByteBuffer.slice();

    i420ByteBuffer.position(uPos);
    // The last row does not have padding.
    final int uvSize = stride * (uvHeight - 1) + stride / 2;
    i420ByteBuffer.limit(uPos + uvSize);
    final ByteBuffer dataU = i420ByteBuffer.slice();

    i420ByteBuffer.position(vPos);
    i420ByteBuffer.limit(vPos + uvSize);
    final ByteBuffer dataV = i420ByteBuffer.slice();

    preparedBuffer.release();

    return JavaI420Buffer.wrap(frameWidth, frameHeight, dataY, stride, dataU, stride, dataV, stride,
        () -> { JniCommon.nativeFreeByteBuffer(i420ByteBuffer); });
  }

  public void release() {
    threadChecker.checkIsOnValidThread();
    drawer.release();
    i420TextureFrameBuffer.release();
    videoFrameDrawer.release();
    // Allow this class to be reused.
    threadChecker.detachThread();
  }
}
