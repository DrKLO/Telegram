/*
 * Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;

public class NV21Buffer implements VideoFrame.Buffer {
  private final byte[] data;
  private final int width;
  private final int height;
  private final RefCountDelegate refCountDelegate;

  public NV21Buffer(byte[] data, int width, int height, @Nullable Runnable releaseCallback) {
    this.data = data;
    this.width = width;
    this.height = height;
    this.refCountDelegate = new RefCountDelegate(releaseCallback);
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
    // Cropping converts the frame to I420. Just crop and scale to the whole image.
    return (VideoFrame.I420Buffer) cropAndScale(0 /* cropX */, 0 /* cropY */, width /* cropWidth */,
        height /* cropHeight */, width /* scaleWidth */, height /* scaleHeight */);
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
    JavaI420Buffer newBuffer = JavaI420Buffer.allocate(scaleWidth, scaleHeight);
    nativeCropAndScale(cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight, data, width,
        height, newBuffer.getDataY(), newBuffer.getStrideY(), newBuffer.getDataU(),
        newBuffer.getStrideU(), newBuffer.getDataV(), newBuffer.getStrideV());
    return newBuffer;
  }

  private static native void nativeCropAndScale(int cropX, int cropY, int cropWidth, int cropHeight,
      int scaleWidth, int scaleHeight, byte[] src, int srcWidth, int srcHeight, ByteBuffer dstY,
      int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV);
}
