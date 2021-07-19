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

/** Wraps libyuv methods to Java. All passed byte buffers must be direct byte buffers. */
public class YuvHelper {
  /** Helper method for copying I420 to tightly packed destination buffer. */
  public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int width, int height) {
    final int chromaHeight = (height + 1) / 2;
    final int chromaWidth = (width + 1) / 2;

    final int minSize = width * height + chromaWidth * chromaHeight * 2;
    if (dst.capacity() < minSize) {
      throw new IllegalArgumentException("Expected destination buffer capacity to be at least "
          + minSize + " was " + dst.capacity());
    }

    final int startY = 0;
    final int startU = height * width;
    final int startV = startU + chromaHeight * chromaWidth;

    dst.position(startY);
    final ByteBuffer dstY = dst.slice();
    dst.position(startU);
    final ByteBuffer dstU = dst.slice();
    dst.position(startV);
    final ByteBuffer dstV = dst.slice();

    nativeI420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, width, dstU,
        chromaWidth, dstV, chromaWidth, width, height);
  }

  /** Helper method for copying I420 to tightly packed NV12 destination buffer. */
  public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int width, int height) {
    final int chromaWidth = (width + 1) / 2;
    final int chromaHeight = (height + 1) / 2;

    final int minSize = width * height + chromaWidth * chromaHeight * 2;
    if (dst.capacity() < minSize) {
      throw new IllegalArgumentException("Expected destination buffer capacity to be at least "
          + minSize + " was " + dst.capacity());
    }

    final int startY = 0;
    final int startUV = height * width;

    dst.position(startY);
    final ByteBuffer dstY = dst.slice();
    dst.position(startUV);
    final ByteBuffer dstUV = dst.slice();

    nativeI420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, width, dstUV,
        chromaWidth * 2, width, height);
  }

  /** Helper method for rotating I420 to tightly packed destination buffer. */
  public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int srcWidth, int srcHeight,
      int rotationMode) {
    final int dstWidth = rotationMode % 180 == 0 ? srcWidth : srcHeight;
    final int dstHeight = rotationMode % 180 == 0 ? srcHeight : srcWidth;

    final int dstChromaHeight = (dstHeight + 1) / 2;
    final int dstChromaWidth = (dstWidth + 1) / 2;

    final int minSize = dstWidth * dstHeight + dstChromaWidth * dstChromaHeight * 2;
    if (dst.capacity() < minSize) {
      throw new IllegalArgumentException("Expected destination buffer capacity to be at least "
          + minSize + " was " + dst.capacity());
    }

    final int startY = 0;
    final int startU = dstHeight * dstWidth;
    final int startV = startU + dstChromaHeight * dstChromaWidth;

    dst.position(startY);
    final ByteBuffer dstY = dst.slice();
    dst.position(startU);
    final ByteBuffer dstU = dst.slice();
    dst.position(startV);
    final ByteBuffer dstV = dst.slice();

    nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstWidth, dstU,
        dstChromaWidth, dstV, dstChromaWidth, srcWidth, srcHeight, rotationMode);
  }

  /** Helper method for copying a single colour plane. */
  public static void copyPlane(
      ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height) {
    nativeCopyPlane(src, srcStride, dst, dstStride, width, height);
  }

  /** Converts ABGR little endian (rgba in memory) to I420. */
  public static void ABGRToI420(ByteBuffer src, int srcStride, ByteBuffer dstY, int dstStrideY,
      ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
    nativeABGRToI420(
        src, srcStride, dstY, dstStrideY, dstU, dstStrideU, dstV, dstStrideV, width, height);
  }

  public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU,
      int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
    nativeI420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU,
        dstStrideU, dstV, dstStrideV, width, height);
  }

  public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstUV,
      int dstStrideUV, int width, int height) {
    nativeI420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstUV,
        dstStrideUV, width, height);
  }

  public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU,
      int dstStrideU, ByteBuffer dstV, int dstStrideV, int srcWidth, int srcHeight,
      int rotationMode) {
    nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU,
        dstStrideU, dstV, dstStrideV, srcWidth, srcHeight, rotationMode);
  }

  private static native void nativeCopyPlane(
      ByteBuffer src, int srcStride, ByteBuffer dst, int dstStride, int width, int height);
  private static native void nativeI420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU,
      int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY,
      ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height);
  private static native void nativeI420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU,
      int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY,
      ByteBuffer dstUV, int dstStrideUV, int width, int height);
  private static native void nativeI420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU,
      int srcStrideU, ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY,
      ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int srcWidth, int srcHeight,
      int rotationMode);
  private static native void nativeABGRToI420(ByteBuffer src, int srcStride, ByteBuffer dstY,
      int dstStrideY, ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width,
      int height);
}
