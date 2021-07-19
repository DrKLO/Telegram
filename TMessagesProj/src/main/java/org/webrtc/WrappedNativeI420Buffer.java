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

import java.nio.ByteBuffer;

/**
 * This class wraps a webrtc::I420BufferInterface into a VideoFrame.I420Buffer.
 */
class WrappedNativeI420Buffer implements VideoFrame.I420Buffer {
  private final int width;
  private final int height;
  private final ByteBuffer dataY;
  private final int strideY;
  private final ByteBuffer dataU;
  private final int strideU;
  private final ByteBuffer dataV;
  private final int strideV;
  private final long nativeBuffer;

  @CalledByNative
  WrappedNativeI420Buffer(int width, int height, ByteBuffer dataY, int strideY, ByteBuffer dataU,
      int strideU, ByteBuffer dataV, int strideV, long nativeBuffer) {
    this.width = width;
    this.height = height;
    this.dataY = dataY;
    this.strideY = strideY;
    this.dataU = dataU;
    this.strideU = strideU;
    this.dataV = dataV;
    this.strideV = strideV;
    this.nativeBuffer = nativeBuffer;

    retain();
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
  public VideoFrame.I420Buffer toI420() {
    retain();
    return this;
  }

  @Override
  public void retain() {
    JniCommon.nativeAddRef(nativeBuffer);
  }

  @Override
  public void release() {
    JniCommon.nativeReleaseRef(nativeBuffer);
  }

  @Override
  public VideoFrame.Buffer cropAndScale(
      int cropX, int cropY, int cropWidth, int cropHeight, int scaleWidth, int scaleHeight) {
    return JavaI420Buffer.cropAndScaleI420(
        this, cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight);
  }
}
