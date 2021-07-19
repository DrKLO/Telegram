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

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import org.webrtc.VideoFrame.I420Buffer;

/** Implementation of VideoFrame.I420Buffer backed by Java direct byte buffers. */
public class JavaI420Buffer implements VideoFrame.I420Buffer {
  private final int width;
  private final int height;
  private final ByteBuffer dataY;
  private final ByteBuffer dataU;
  private final ByteBuffer dataV;
  private final int strideY;
  private final int strideU;
  private final int strideV;
  private final RefCountDelegate refCountDelegate;

  private JavaI420Buffer(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU,
      int strideU, ByteBuffer dataV, int strideV, @Nullable Runnable releaseCallback) {
    this.width = width;
    this.height = height;
    this.dataY = dataY;
    this.dataU = dataU;
    this.dataV = dataV;
    this.strideY = strideY;
    this.strideU = strideU;
    this.strideV = strideV;
    this.refCountDelegate = new RefCountDelegate(releaseCallback);
  }

  private static void checkCapacity(ByteBuffer data, int width, int height, int stride) {
    // The last row does not necessarily need padding.
    final int minCapacity = stride * (height - 1) + width;
    if (data.capacity() < minCapacity) {
      throw new IllegalArgumentException(
          "Buffer must be at least " + minCapacity + " bytes, but was " + data.capacity());
    }
  }

  /** Wraps existing ByteBuffers into JavaI420Buffer object without copying the contents. */
  public static JavaI420Buffer wrap(int width, int height, ByteBuffer dataY, int strideY,
      ByteBuffer dataU, int strideU, ByteBuffer dataV, int strideV,
      @Nullable Runnable releaseCallback) {
    if (dataY == null || dataU == null || dataV == null) {
      throw new IllegalArgumentException("Data buffers cannot be null.");
    }
    if (!dataY.isDirect() || !dataU.isDirect() || !dataV.isDirect()) {
      throw new IllegalArgumentException("Data buffers must be direct byte buffers.");
    }

    // Slice the buffers to prevent external modifications to the position / limit of the buffer.
    // Note that this doesn't protect the contents of the buffers from modifications.
    dataY = dataY.slice();
    dataU = dataU.slice();
    dataV = dataV.slice();

    final int chromaWidth = (width + 1) / 2;
    final int chromaHeight = (height + 1) / 2;
    checkCapacity(dataY, width, height, strideY);
    checkCapacity(dataU, chromaWidth, chromaHeight, strideU);
    checkCapacity(dataV, chromaWidth, chromaHeight, strideV);

    return new JavaI420Buffer(
        width, height, dataY, strideY, dataU, strideU, dataV, strideV, releaseCallback);
  }

  /** Allocates an empty I420Buffer suitable for an image of the given dimensions. */
  public static JavaI420Buffer allocate(int width, int height) {
    int chromaHeight = (height + 1) / 2;
    int strideUV = (width + 1) / 2;
    int yPos = 0;
    int uPos = yPos + width * height;
    int vPos = uPos + strideUV * chromaHeight;

    ByteBuffer buffer =
        JniCommon.nativeAllocateByteBuffer(width * height + 2 * strideUV * chromaHeight);

    buffer.position(yPos);
    buffer.limit(uPos);
    ByteBuffer dataY = buffer.slice();

    buffer.position(uPos);
    buffer.limit(vPos);
    ByteBuffer dataU = buffer.slice();

    buffer.position(vPos);
    buffer.limit(vPos + strideUV * chromaHeight);
    ByteBuffer dataV = buffer.slice();

    return new JavaI420Buffer(width, height, dataY, width, dataU, strideUV, dataV, strideUV,
        () -> { JniCommon.nativeFreeByteBuffer(buffer); });
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
  public ByteBuffer getDataY() {
    // Return a slice to prevent relative reads from changing the position.
    return dataY.slice();
  }

  @Override
  public ByteBuffer getDataU() {
    // Return a slice to prevent relative reads from changing the position.
    return dataU.slice();
  }

  @Override
  public ByteBuffer getDataV() {
    // Return a slice to prevent relative reads from changing the position.
    return dataV.slice();
  }

  @Override
  public int getStrideY() {
    return strideY;
  }

  @Override
  public int getStrideU() {
    return strideU;
  }

  @Override
  public int getStrideV() {
    return strideV;
  }

  @Override
  public I420Buffer toI420() {
    retain();
    return this;
  }

  @Override
  public void retain() {
    refCountDelegate.retain();
  }

  @Override
  public void release() {
    refCountDelegate.release();
  }

  @Override
  public VideoFrame.Buffer cropAndScale(
      int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
    return cropAndScaleI420(this, cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight);
  }

  public static VideoFrame.Buffer cropAndScaleI420(final I420Buffer buffer, int cropX, int cropY,
      int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
    if (cropWidth == scaleWidth && cropHeight == scaleHeight) {
      // No scaling.
      ByteBuffer dataY = buffer.getDataY();
      ByteBuffer dataU = buffer.getDataU();
      ByteBuffer dataV = buffer.getDataV();

      dataY.position(cropX + cropY * buffer.getStrideY());
      dataU.position(cropX / 2 + cropY / 2 * buffer.getStrideU());
      dataV.position(cropX / 2 + cropY / 2 * buffer.getStrideV());

      buffer.retain();
      return JavaI420Buffer.wrap(scaleWidth, scaleHeight, dataY.slice(), buffer.getStrideY(),
          dataU.slice(), buffer.getStrideU(), dataV.slice(), buffer.getStrideV(), buffer::release);
    }

    JavaI420Buffer newBuffer = JavaI420Buffer.allocate(scaleWidth, scaleHeight);
    nativeCropAndScaleI420(buffer.getDataY(), buffer.getStrideY(), buffer.getDataU(),
        buffer.getStrideU(), buffer.getDataV(), buffer.getStrideV(), cropX, cropY, cropWidth,
        cropHeight, newBuffer.getDataY(), newBuffer.getStrideY(), newBuffer.getDataU(),
        newBuffer.getStrideU(), newBuffer.getDataV(), newBuffer.getStrideV(), scaleWidth,
        scaleHeight);
    return newBuffer;
  }

  private static native void nativeCropAndScaleI420(ByteBuffer srcY, int srcStrideY,
      ByteBuffer srcU, int srcStrideU, ByteBuffer srcV, int srcStrideV, int cropX, int cropY,
      int cropWidth, int cropHeight, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU,
      int dstStrideU, ByteBuffer dstV, int dstStrideV, int scaleWidth, int scaleHeight);
}
