/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.graphics.Matrix;
import android.graphics.Point;
import android.opengl.GLES20;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Helper class to draw VideoFrames. Calls either drawer.drawOes, drawer.drawRgb, or
 * drawer.drawYuv depending on the type of the buffer. The frame will be rendered with rotation
 * taken into account. You can supply an additional render matrix for custom transformations.
 */
public class VideoFrameDrawer {
  public static final String TAG = "VideoFrameDrawer";
  /**
   * Draws a VideoFrame.TextureBuffer. Calls either drawer.drawOes or drawer.drawRgb
   * depending on the type of the buffer. You can supply an additional render matrix. This is
   * used multiplied together with the transformation matrix of the frame. (M = renderMatrix *
   * transformationMatrix)
   */
  public static void drawTexture(RendererCommon.GlDrawer drawer, VideoFrame.TextureBuffer buffer,
      Matrix renderMatrix, int rotatedWidth, int rotatedHeight, int frameWidth, int frameHeight, int viewportX, int viewportY,
      int viewportWidth, int viewportHeight, boolean blur) {
    Matrix finalMatrix = new Matrix(buffer.getTransformMatrix());
    finalMatrix.preConcat(renderMatrix);
    float[] finalGlMatrix = RendererCommon.convertMatrixFromAndroidGraphicsMatrix(finalMatrix);
    switch (buffer.getType()) {
      case OES:
        drawer.drawOes(buffer.getTextureId(), buffer.getWidth(), buffer.getHeight(), rotatedWidth, rotatedHeight, finalGlMatrix, frameWidth, frameHeight, viewportX,
            viewportY, viewportWidth, viewportHeight, blur);
        break;
      case RGB:
        drawer.drawRgb(buffer.getTextureId(), buffer.getWidth(), buffer.getHeight(), rotatedWidth, rotatedHeight, finalGlMatrix, frameWidth, frameHeight, viewportX,
            viewportY, viewportWidth, viewportHeight, blur);
        break;
      default:
        throw new RuntimeException("Unknown texture type.");
    }
  }

  public void getRenderBufferBitmap(RendererCommon.GlDrawer drawer, int rotation, GlGenericDrawer.TextureCallback callback) {
    if (drawer instanceof GlGenericDrawer) {
      ((GlGenericDrawer) drawer).getRenderBufferBitmap(rotation, callback);
    }
  }

  /**
   * Helper class for uploading YUV bytebuffer frames to textures that handles stride > width. This
   * class keeps an internal ByteBuffer to avoid unnecessary allocations for intermediate copies.
   */
  private static class YuvUploader {
    // Intermediate copy buffer for uploading yuv frames that are not packed, i.e. stride > width.
    // TODO(magjed): Investigate when GL_UNPACK_ROW_LENGTH is available, or make a custom shader
    // that handles stride and compare performance with intermediate copy.
    @Nullable private ByteBuffer copyBuffer;
    @Nullable private int[] yuvTextures;

    /**
     * Upload `planes` into OpenGL textures, taking stride into consideration.
     *
     * @return Array of three texture indices corresponding to Y-, U-, and V-plane respectively.
     */
    @Nullable
    public int[] uploadYuvData(int width, int height, int[] strides, ByteBuffer[] planes) {
      final int[] planeWidths = new int[] {width, width / 2, width / 2};
      final int[] planeHeights = new int[] {height, height / 2, height / 2};
      // Make a first pass to see if we need a temporary copy buffer.
      int copyCapacityNeeded = 0;
      for (int i = 0; i < 3; ++i) {
        if (strides[i] > planeWidths[i]) {
          copyCapacityNeeded = Math.max(copyCapacityNeeded, planeWidths[i] * planeHeights[i]);
        }
      }
      // Allocate copy buffer if necessary.
      if (copyCapacityNeeded > 0
          && (copyBuffer == null || copyBuffer.capacity() < copyCapacityNeeded)) {
        copyBuffer = ByteBuffer.allocateDirect(copyCapacityNeeded);
      }
      // Make sure YUV textures are allocated.
      if (yuvTextures == null) {
        yuvTextures = new int[3];
        for (int i = 0; i < 3; i++) {
          yuvTextures[i] = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
        }
      }
      // Upload each plane.
      for (int i = 0; i < 3; ++i) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
        // GLES only accepts packed data, i.e. stride == planeWidth.
        final ByteBuffer packedByteBuffer;
        if (strides[i] == planeWidths[i]) {
          // Input is packed already.
          packedByteBuffer = planes[i];
        } else {
          YuvHelper.copyPlane(
              planes[i], strides[i], copyBuffer, planeWidths[i], planeWidths[i], planeHeights[i]);
          packedByteBuffer = copyBuffer;
        }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, planeWidths[i],
            planeHeights[i], 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, packedByteBuffer);
      }
      return yuvTextures;
    }

    @Nullable
    public int[] uploadFromBuffer(VideoFrame.I420Buffer buffer) {
      int[] strides = {buffer.getStrideY(), buffer.getStrideU(), buffer.getStrideV()};
      ByteBuffer[] planes = {buffer.getDataY(), buffer.getDataU(), buffer.getDataV()};
      return uploadYuvData(buffer.getWidth(), buffer.getHeight(), strides, planes);
    }

    @Nullable
    public int[] getYuvTextures() {
      return yuvTextures;
    }

    /**
     * Releases cached resources. Uploader can still be used and the resources will be reallocated
     * on first use.
     */
    public void release() {
      copyBuffer = null;
      if (yuvTextures != null) {
        GLES20.glDeleteTextures(3, yuvTextures, 0);
        yuvTextures = null;
      }
    }
  }

  private static int distance(float x0, float y0, float x1, float y1) {
    return (int) Math.round(Math.hypot(x1 - x0, y1 - y0));
  }

  // These points are used to calculate the size of the part of the frame we are rendering.
  final static float[] srcPoints =
      new float[] {0f /* x0 */, 0f /* y0 */, 1f /* x1 */, 0f /* y1 */, 0f /* x2 */, 1f /* y2 */};
  private final float[] dstPoints = new float[6];
  private final Point renderSize = new Point();
  private int renderWidth;
  private int renderHeight;

  // Calculate the frame size after `renderMatrix` is applied. Stores the output in member variables
  // `renderWidth` and `renderHeight` to avoid allocations since this function is called for every
  // frame.
  private void calculateTransformedRenderSize(
      int frameWidth, int frameHeight, @Nullable Matrix renderMatrix) {
    if (renderMatrix == null) {
      renderWidth = frameWidth;
      renderHeight = frameHeight;
      return;
    }
    // Transform the texture coordinates (in the range [0, 1]) according to `renderMatrix`.
    renderMatrix.mapPoints(dstPoints, srcPoints);

    // Multiply with the width and height to get the positions in terms of pixels.
    for (int i = 0; i < 3; ++i) {
      dstPoints[i * 2] *= frameWidth;
      dstPoints[i * 2 + 1] *= frameHeight;
    }

    // Get the length of the sides of the transformed rectangle in terms of pixels.
    renderWidth = distance(dstPoints[0], dstPoints[1], dstPoints[2], dstPoints[3]);
    renderHeight = distance(dstPoints[0], dstPoints[1], dstPoints[4], dstPoints[5]);
  }

  private final YuvUploader yuvUploader = new YuvUploader();
  // This variable will only be used for checking reference equality and is used for caching I420
  // textures.
  @Nullable private VideoFrame lastI420Frame;
  private final Matrix renderMatrix = new Matrix();
  private final Matrix renderRotateMatrix = new Matrix();

  public void drawFrame(VideoFrame frame, RendererCommon.GlDrawer drawer) {
    drawFrame(frame, drawer, null /* additionalRenderMatrix */);
  }

  public void drawFrame(
      VideoFrame frame, RendererCommon.GlDrawer drawer, Matrix additionalRenderMatrix) {
    drawFrame(frame, drawer, additionalRenderMatrix, 0 /* viewportX */, 0 /* viewportY */,
        frame.getRotatedWidth(), frame.getRotatedHeight(), false, false);
  }

  public void drawFrame(VideoFrame frame, RendererCommon.GlDrawer drawer,
      @Nullable Matrix additionalRenderMatrix, int viewportX, int viewportY, int viewportWidth,
      int viewportHeight, boolean rotate, boolean blur) {
    final int width = rotate ? frame.getRotatedHeight() : frame.getRotatedWidth();
    final int height = rotate ? frame.getRotatedWidth() : frame.getRotatedHeight();
    calculateTransformedRenderSize(width, height, additionalRenderMatrix);
    if (renderWidth <= 0 || renderHeight <= 0) {
      Logging.w(TAG, "Illegal frame size: " + renderWidth + "x" + renderHeight);
      return;
    }

    final boolean isTextureFrame = frame.getBuffer() instanceof VideoFrame.TextureBuffer;
    renderMatrix.reset();
    renderMatrix.preTranslate(0.5f, 0.5f);
    if (!isTextureFrame) {
      renderMatrix.preScale(1f, -1f); // I420-frames are upside down
    }
    renderMatrix.preRotate(frame.getRotation());
    renderMatrix.preTranslate(-0.5f, -0.5f);
    if (additionalRenderMatrix != null) {
      renderMatrix.preConcat(additionalRenderMatrix);
    }

    if (isTextureFrame) {
      lastI420Frame = null;
      drawTexture(drawer, (VideoFrame.TextureBuffer) frame.getBuffer(), renderMatrix, frame.getRotatedWidth(), frame.getRotatedHeight(), renderWidth,
          renderHeight, viewportX, viewportY, viewportWidth, viewportHeight, blur);
    } else {
      // Only upload the I420 data to textures once per frame, if we are called multiple times
      // with the same frame.
      if (frame != lastI420Frame) {
        lastI420Frame = frame;
        final VideoFrame.I420Buffer i420Buffer = frame.getBuffer().toI420();
        yuvUploader.uploadFromBuffer(i420Buffer);
        i420Buffer.release();
      }

      drawer.drawYuv(yuvUploader.getYuvTextures(), frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotatedWidth(), frame.getRotatedHeight(),
          RendererCommon.convertMatrixFromAndroidGraphicsMatrix(renderMatrix), renderWidth,
          renderHeight, viewportX, viewportY, viewportWidth, viewportHeight, blur);
    }
  }

  public VideoFrame.Buffer prepareBufferForViewportSize(
      VideoFrame.Buffer buffer, int width, int height) {
    buffer.retain();
    return buffer;
  }

  public void release() {
    yuvUploader.release();
    lastI420Frame = null;
  }
}
