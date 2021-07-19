/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.GlUtil;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLSurfaceView.Renderer implementation that can render YUV Frames returned by a video decoder
 * after decoding. It does the YUV to RGB color conversion in the Fragment Shader.
 */
/* package */ class VideoDecoderRenderer
    implements GLSurfaceView.Renderer, VideoDecoderOutputBufferRenderer {

  private static final float[] kColorConversion601 = {
    1.164f, 1.164f, 1.164f,
    0.0f, -0.392f, 2.017f,
    1.596f, -0.813f, 0.0f,
  };

  private static final float[] kColorConversion709 = {
    1.164f, 1.164f, 1.164f,
    0.0f, -0.213f, 2.112f,
    1.793f, -0.533f, 0.0f,
  };

  private static final float[] kColorConversion2020 = {
    1.168f, 1.168f, 1.168f,
    0.0f, -0.188f, 2.148f,
    1.683f, -0.652f, 0.0f,
  };

  private static final String VERTEX_SHADER =
      "varying vec2 interp_tc_y;\n"
          + "varying vec2 interp_tc_u;\n"
          + "varying vec2 interp_tc_v;\n"
          + "attribute vec4 in_pos;\n"
          + "attribute vec2 in_tc_y;\n"
          + "attribute vec2 in_tc_u;\n"
          + "attribute vec2 in_tc_v;\n"
          + "void main() {\n"
          + "  gl_Position = in_pos;\n"
          + "  interp_tc_y = in_tc_y;\n"
          + "  interp_tc_u = in_tc_u;\n"
          + "  interp_tc_v = in_tc_v;\n"
          + "}\n";
  private static final String[] TEXTURE_UNIFORMS = {"y_tex", "u_tex", "v_tex"};
  private static final String FRAGMENT_SHADER =
      "precision mediump float;\n"
          + "varying vec2 interp_tc_y;\n"
          + "varying vec2 interp_tc_u;\n"
          + "varying vec2 interp_tc_v;\n"
          + "uniform sampler2D y_tex;\n"
          + "uniform sampler2D u_tex;\n"
          + "uniform sampler2D v_tex;\n"
          + "uniform mat3 mColorConversion;\n"
          + "void main() {\n"
          + "  vec3 yuv;\n"
          + "  yuv.x = texture2D(y_tex, interp_tc_y).r - 0.0625;\n"
          + "  yuv.y = texture2D(u_tex, interp_tc_u).r - 0.5;\n"
          + "  yuv.z = texture2D(v_tex, interp_tc_v).r - 0.5;\n"
          + "  gl_FragColor = vec4(mColorConversion * yuv, 1.0);\n"
          + "}\n";

  private static final FloatBuffer TEXTURE_VERTICES =
      GlUtil.createBuffer(new float[] {-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, -1.0f});
  private final GLSurfaceView surfaceView;
  private final int[] yuvTextures = new int[3];
  private final AtomicReference<VideoDecoderOutputBuffer> pendingOutputBufferReference;

  // Kept in field rather than a local variable in order not to get garbage collected before
  // glDrawArrays uses it.
  private FloatBuffer[] textureCoords;

  private int program;
  private int[] texLocations;
  private int colorMatrixLocation;
  private int[] previousWidths;
  private int[] previousStrides;

  @Nullable
  private VideoDecoderOutputBuffer renderedOutputBuffer; // Accessed only from the GL thread.

  public VideoDecoderRenderer(GLSurfaceView surfaceView) {
    this.surfaceView = surfaceView;
    pendingOutputBufferReference = new AtomicReference<>();
    textureCoords = new FloatBuffer[3];
    texLocations = new int[3];
    previousWidths = new int[3];
    previousStrides = new int[3];
    for (int i = 0; i < 3; i++) {
      previousWidths[i] = previousStrides[i] = -1;
    }
  }

  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    program = GlUtil.compileProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    GLES20.glUseProgram(program);
    int posLocation = GLES20.glGetAttribLocation(program, "in_pos");
    GLES20.glEnableVertexAttribArray(posLocation);
    GLES20.glVertexAttribPointer(posLocation, 2, GLES20.GL_FLOAT, false, 0, TEXTURE_VERTICES);
    texLocations[0] = GLES20.glGetAttribLocation(program, "in_tc_y");
    GLES20.glEnableVertexAttribArray(texLocations[0]);
    texLocations[1] = GLES20.glGetAttribLocation(program, "in_tc_u");
    GLES20.glEnableVertexAttribArray(texLocations[1]);
    texLocations[2] = GLES20.glGetAttribLocation(program, "in_tc_v");
    GLES20.glEnableVertexAttribArray(texLocations[2]);
    GlUtil.checkGlError();
    colorMatrixLocation = GLES20.glGetUniformLocation(program, "mColorConversion");
    GlUtil.checkGlError();
    setupTextures();
    GlUtil.checkGlError();
  }

  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 unused) {
    VideoDecoderOutputBuffer pendingOutputBuffer = pendingOutputBufferReference.getAndSet(null);
    if (pendingOutputBuffer == null && renderedOutputBuffer == null) {
      // There is no output buffer to render at the moment.
      return;
    }
    if (pendingOutputBuffer != null) {
      if (renderedOutputBuffer != null) {
        renderedOutputBuffer.release();
      }
      renderedOutputBuffer = pendingOutputBuffer;
    }
    VideoDecoderOutputBuffer outputBuffer = renderedOutputBuffer;
    // Set color matrix. Assume BT709 if the color space is unknown.
    float[] colorConversion = kColorConversion709;
    switch (outputBuffer.colorspace) {
      case VideoDecoderOutputBuffer.COLORSPACE_BT601:
        colorConversion = kColorConversion601;
        break;
      case VideoDecoderOutputBuffer.COLORSPACE_BT2020:
        colorConversion = kColorConversion2020;
        break;
      case VideoDecoderOutputBuffer.COLORSPACE_BT709:
      default:
        break; // Do nothing
    }
    GLES20.glUniformMatrix3fv(colorMatrixLocation, 1, false, colorConversion, 0);

    for (int i = 0; i < 3; i++) {
      int h = (i == 0) ? outputBuffer.height : (outputBuffer.height + 1) / 2;
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
      GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_LUMINANCE,
          outputBuffer.yuvStrides[i],
          h,
          0,
          GLES20.GL_LUMINANCE,
          GLES20.GL_UNSIGNED_BYTE,
          outputBuffer.yuvPlanes[i]);
    }

    int[] widths = new int[3];
    widths[0] = outputBuffer.width;
    // TODO: Handle streams where chroma channels are not stored at half width and height
    // compared to luma channel. See [Internal: b/142097774].
    // U and V planes are being stored at half width compared to Y.
    widths[1] = widths[2] = (widths[0] + 1) / 2;
    for (int i = 0; i < 3; i++) {
      // Set cropping of stride if either width or stride has changed.
      if (previousWidths[i] != widths[i] || previousStrides[i] != outputBuffer.yuvStrides[i]) {
        Assertions.checkState(outputBuffer.yuvStrides[i] != 0);
        float widthRatio = (float) widths[i] / outputBuffer.yuvStrides[i];
        // These buffers are consumed during each call to glDrawArrays. They need to be member
        // variables rather than local variables in order not to get garbage collected.
        textureCoords[i] =
            GlUtil.createBuffer(
                new float[] {0.0f, 0.0f, 0.0f, 1.0f, widthRatio, 0.0f, widthRatio, 1.0f});
        GLES20.glVertexAttribPointer(
            texLocations[i], 2, GLES20.GL_FLOAT, false, 0, textureCoords[i]);
        previousWidths[i] = widths[i];
        previousStrides[i] = outputBuffer.yuvStrides[i];
      }
    }

    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError();
  }

  @Override
  public void setOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    VideoDecoderOutputBuffer oldPendingOutputBuffer =
        pendingOutputBufferReference.getAndSet(outputBuffer);
    if (oldPendingOutputBuffer != null) {
      // The old pending output buffer will never be used for rendering, so release it now.
      oldPendingOutputBuffer.release();
    }
    surfaceView.requestRender();
  }

  private void setupTextures() {
    GLES20.glGenTextures(3, yuvTextures, 0);
    for (int i = 0; i < 3; i++) {
      GLES20.glUniform1i(GLES20.glGetUniformLocation(program, TEXTURE_UNIFORMS[i]), i);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameterf(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }
    GlUtil.checkGlError();
  }
}
