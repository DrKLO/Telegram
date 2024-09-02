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
import android.os.Handler;

import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;

/**
 * Android texture buffer that glues together the necessary information together with a generic
 * release callback. ToI420() is implemented by providing a Handler and a YuvConverter.
 */
public class TextureBufferImpl implements VideoFrame.TextureBuffer {
  interface RefCountMonitor {
    void onRetain(TextureBufferImpl textureBuffer);
    void onRelease(TextureBufferImpl textureBuffer);
    void onDestroy(TextureBufferImpl textureBuffer);
  }

  // This is the full resolution the texture has in memory after applying the transformation matrix
  // that might include cropping. This resolution is useful to know when sampling the texture to
  // avoid downscaling artifacts.
  private final int unscaledWidth;
  private final int unscaledHeight;
  // This is the resolution that has been applied after cropAndScale().
  private final int width;
  private final int height;
  private final Type type;
  private final int id;
  private final Matrix transformMatrix;
  private final Handler toI420Handler;
  private final YuvConverter yuvConverter;
  private final RefCountDelegate refCountDelegate;
  private final RefCountMonitor refCountMonitor;

  public TextureBufferImpl(int width, int height, Type type, int id, Matrix transformMatrix,
      Handler toI420Handler, YuvConverter yuvConverter, @Nullable Runnable releaseCallback) {
    this(width, height, width, height, type, id, transformMatrix, toI420Handler, yuvConverter,
        new RefCountMonitor() {
          @Override
          public void onRetain(TextureBufferImpl textureBuffer) {}

          @Override
          public void onRelease(TextureBufferImpl textureBuffer) {}

          @Override
          public void onDestroy(TextureBufferImpl textureBuffer) {
            if (releaseCallback != null) {
              releaseCallback.run();
            }
          }
        });
  }

  TextureBufferImpl(int width, int height, Type type, int id, Matrix transformMatrix,
      Handler toI420Handler, YuvConverter yuvConverter, RefCountMonitor refCountMonitor) {
    this(width, height, width, height, type, id, transformMatrix, toI420Handler, yuvConverter,
        refCountMonitor);
  }

  private TextureBufferImpl(int unscaledWidth, int unscaledHeight, int width, int height, Type type,
      int id, Matrix transformMatrix, Handler toI420Handler, YuvConverter yuvConverter,
      RefCountMonitor refCountMonitor) {
    this.unscaledWidth = unscaledWidth;
    this.unscaledHeight = unscaledHeight;
    this.width = width;
    this.height = height;
    this.type = type;
    this.id = id;
    this.transformMatrix = transformMatrix;
    this.toI420Handler = toI420Handler;
    this.yuvConverter = yuvConverter;
    this.refCountDelegate = new RefCountDelegate(() -> refCountMonitor.onDestroy(this));
    this.refCountMonitor = refCountMonitor;
  }

  @Override
  public VideoFrame.TextureBuffer.Type getType() {
    return type;
  }

  @Override
  public int getTextureId() {
    return id;
  }

  @Override
  public Matrix getTransformMatrix() {
    return transformMatrix;
  }

  @Override
  public int getWidth() {
    return width;
  }

  @Override
  public int getHeight() {
    return height;
  }

  @Override
  public VideoFrame.I420Buffer toI420() {
    try {
      return ThreadUtils.invokeAtFrontUninterruptibly(
              toI420Handler, () -> yuvConverter.convert(this));
    } catch (Throwable e) {
      FileLog.e(e);
      //don't crash if something fails
      final int frameWidth = getWidth();
      final int frameHeight = getHeight();
      final int stride = ((frameWidth + 7) / 8) * 8;
      final int uvHeight = (frameHeight + 1) / 2;

      final int totalHeight = frameHeight + uvHeight;
      final ByteBuffer i420ByteBuffer = JniCommon.nativeAllocateByteBuffer(stride * totalHeight);

      while (i420ByteBuffer.hasRemaining()) {
        i420ByteBuffer.put((byte) 0);
      }

      final int viewportWidth = stride / 4;

      final int yPos = 0;
      final int uPos = yPos + stride * frameHeight;
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


      return JavaI420Buffer.wrap(frameWidth, frameHeight, dataY, stride, dataU, stride, dataV, stride,
              () -> JniCommon.nativeFreeByteBuffer(i420ByteBuffer));
    }
  }

  @Override
  public void retain() {
    refCountMonitor.onRetain(this);
    refCountDelegate.retain();
  }

  @Override
  public void release() {
    refCountMonitor.onRelease(this);
    refCountDelegate.release();
  }

  @Override
  public VideoFrame.Buffer cropAndScale(
      int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
    final Matrix cropAndScaleMatrix = new Matrix();
    // In WebRTC, Y=0 is the top row, while in OpenGL Y=0 is the bottom row. This means that the Y
    // direction is effectively reversed.
    final int cropYFromBottom = height - (cropY + cropHeight);
    cropAndScaleMatrix.preTranslate(cropX / (float) width, cropYFromBottom / (float) height);
    cropAndScaleMatrix.preScale(cropWidth / (float) width, cropHeight / (float) height);

    return applyTransformMatrix(cropAndScaleMatrix,
        (int) Math.round(unscaledWidth * cropWidth / (float) width),
        (int) Math.round(unscaledHeight * cropHeight / (float) height), scaleWidth, scaleHeight);
  }

  /**
   * Returns the width of the texture in memory. This should only be used for downscaling, and you
   * should still respect the width from getWidth().
   */
  public int getUnscaledWidth() {
    return unscaledWidth;
  }

  /**
   * Returns the height of the texture in memory. This should only be used for downscaling, and you
   * should still respect the height from getHeight().
   */
  public int getUnscaledHeight() {
    return unscaledHeight;
  }

  public Handler getToI420Handler() {
    return toI420Handler;
  }

  public YuvConverter getYuvConverter() {
    return yuvConverter;
  }

  /**
   * Create a new TextureBufferImpl with an applied transform matrix and a new size. The
   * existing buffer is unchanged. The given transform matrix is applied first when texture
   * coordinates are still in the unmodified [0, 1] range.
   */
  public TextureBufferImpl applyTransformMatrix(
      Matrix transformMatrix, int newWidth, int newHeight) {
    return applyTransformMatrix(transformMatrix, /* unscaledWidth= */ newWidth,
        /* unscaledHeight= */ newHeight, /* scaledWidth= */ newWidth,
        /* scaledHeight= */ newHeight);
  }

  private TextureBufferImpl applyTransformMatrix(Matrix transformMatrix, int unscaledWidth,
      int unscaledHeight, int scaledWidth, int scaledHeight) {
    final Matrix newMatrix = new Matrix(this.transformMatrix);
    newMatrix.preConcat(transformMatrix);
    retain();
    return new TextureBufferImpl(unscaledWidth, unscaledHeight, scaledWidth, scaledHeight, type, id,
        newMatrix, toI420Handler, yuvConverter, new RefCountMonitor() {
          @Override
          public void onRetain(TextureBufferImpl textureBuffer) {
            refCountMonitor.onRetain(TextureBufferImpl.this);
          }

          @Override
          public void onRelease(TextureBufferImpl textureBuffer) {
            refCountMonitor.onRelease(TextureBufferImpl.this);
          }

          @Override
          public void onDestroy(TextureBufferImpl textureBuffer) {
            release();
          }
        });
  }
}
