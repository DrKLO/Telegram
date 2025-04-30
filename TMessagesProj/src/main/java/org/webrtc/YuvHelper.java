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
  /**
   * Copy I420 Buffer to a contiguously allocated buffer.
   * <p> In Android, MediaCodec can request a buffer of a specific layout with the stride and
   * slice-height (or plane height), and this function is used in this case.
   * <p> For more information, see
   * https://cs.android.com/android/platform/superproject/+/64fea7e5726daebc40f46890100837c01091100d:frameworks/base/media/java/android/media/MediaFormat.java;l=568
   * @param dstStrideY the stride of output buffers' Y plane.
   * @param dstSliceHeightY the slice-height of output buffer's Y plane.
   * @param dstStrideU the stride of output buffers' U (and V) plane.
   * @param dstSliceHeightU the slice-height of output buffer's U (and V) plane
   */
  public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight, int dstStrideY,
      int dstSliceHeightY, int dstStrideU, int dstSliceHeightU) {
    final int chromaWidth = (dstWidth + 1) / 2;
    final int chromaHeight = (dstHeight + 1) / 2;

    final int dstStartY = 0;
    final int dstEndY = dstStartY + dstStrideY * dstHeight;
    final int dstStartU = dstStartY + dstStrideY * dstSliceHeightY;
    final int dstEndU = dstStartU + dstStrideU * chromaHeight;
    final int dstStartV = dstStartU + dstStrideU * dstSliceHeightU;
    // The last line doesn't need any padding, so  use chromaWidth to calculate the exact end
    // position.
    final int dstEndV = dstStartV + dstStrideU * (chromaHeight - 1) + chromaWidth;
    if (dst.capacity() < dstEndV) {
      throw new IllegalArgumentException("Expected destination buffer capacity to be at least "
          + dstEndV + " was " + dst.capacity());
    }

    dst.limit(dstEndY);
    dst.position(dstStartY);
    final ByteBuffer dstY = dst.slice();
    dst.limit(dstEndU);
    dst.position(dstStartU);
    final ByteBuffer dstU = dst.slice();
    dst.limit(dstEndV);
    dst.position(dstStartV);
    final ByteBuffer dstV = dst.slice();

    I420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU,
        dstStrideU, dstV, dstStrideU, dstWidth, dstHeight);
  }

  /** Helper method for copying I420 to tightly packed destination buffer. */
  public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight) {
    I420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dst, dstWidth, dstHeight,
        dstWidth, dstHeight, (dstWidth + 1) / 2, (dstHeight + 1) / 2);
  }

  /** Helper method for copying I420 to buffer with the given stride and slice height. */
  public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight, int dstStride,
      int dstSliceHeight) {
    I420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dst, dstWidth, dstHeight,
        dstStride, dstSliceHeight, (dstStride + 1) / 2, (dstSliceHeight + 1) / 2);
  }

  /**
   * Copy I420 Buffer to a contiguously allocated buffer.
   * @param dstStrideY the stride of output buffers' Y plane.
   * @param dstSliceHeightY the slice-height of output buffer's Y plane.
   */
  public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight, int dstStrideY,
      int dstSliceHeightY) {
    final int chromaHeight = (dstHeight + 1) / 2;
    final int chromaWidth = (dstWidth + 1) / 2;

    final int dstStartY = 0;
    final int dstEndY = dstStartY + dstStrideY * dstHeight;
    final int dstStartUV = dstStartY + dstStrideY * dstSliceHeightY;
    final int dstEndUV = dstStartUV + chromaWidth * chromaHeight * 2;
    if (dst.capacity() < dstEndUV) {
      throw new IllegalArgumentException("Expected destination buffer capacity to be at least "
          + dstEndUV + " was " + dst.capacity());
    }

    dst.limit(dstEndY);
    dst.position(dstStartY);
    final ByteBuffer dstY = dst.slice();
    dst.limit(dstEndUV);
    dst.position(dstStartUV);
    final ByteBuffer dstUV = dst.slice();

    I420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstUV,
        chromaWidth * 2, dstWidth, dstHeight);
  }

  /** Helper method for copying I420 to tightly packed NV12 destination buffer. */
  public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int dstWidth, int dstHeight) {
    I420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dst, dstWidth, dstHeight,
        dstWidth, dstHeight);
  }

  /** Helper method for rotating I420 to tightly packed destination buffer. */
  public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dst, int srcWidth, int srcHeight,
      int rotationMode) {
    checkNotNull(srcY, "srcY");
    checkNotNull(srcU, "srcU");
    checkNotNull(srcV, "srcV");
    checkNotNull(dst, "dst");
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
    nativeCopyPlane(
        checkNotNull(src, "src"), srcStride, checkNotNull(dst, "dst"), dstStride, width, height);
  }

  /** Converts ABGR little endian (rgba in memory) to I420. */
  public static void ABGRToI420(ByteBuffer src, int srcStride, ByteBuffer dstY, int dstStrideY,
      ByteBuffer dstU, int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
    nativeABGRToI420(checkNotNull(src, "src"), srcStride, checkNotNull(dstY, "dstY"), dstStrideY,
        checkNotNull(dstU, "dstU"), dstStrideU, checkNotNull(dstV, "dstV"), dstStrideV, width,
        height);
  }

  /**
   * Copies I420 to the I420 dst buffer.
   * <p> Unlike `libyuv::I420Copy`, this function checks if the height <= 0, so flipping is not
   * supported.
   */
  public static void I420Copy(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU,
      int dstStrideU, ByteBuffer dstV, int dstStrideV, int width, int height) {
    checkNotNull(srcY, "srcY");
    checkNotNull(srcU, "srcU");
    checkNotNull(srcV, "srcV");
    checkNotNull(dstY, "dstY");
    checkNotNull(dstU, "dstU");
    checkNotNull(dstV, "dstV");
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("I420Copy: width and height should not be negative");
    }
    nativeI420Copy(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU,
        dstStrideU, dstV, dstStrideV, width, height);
  }

  public static void I420ToNV12(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstUV,
      int dstStrideUV, int width, int height) {
    checkNotNull(srcY, "srcY");
    checkNotNull(srcU, "srcU");
    checkNotNull(srcV, "srcV");
    checkNotNull(dstY, "dstY");
    checkNotNull(dstUV, "dstUV");
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("I420ToNV12: width and height should not be negative");
    }
    nativeI420ToNV12(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstUV,
        dstStrideUV, width, height);
  }

  public static void I420Rotate(ByteBuffer srcY, int srcStrideY, ByteBuffer srcU, int srcStrideU,
      ByteBuffer srcV, int srcStrideV, ByteBuffer dstY, int dstStrideY, ByteBuffer dstU,
      int dstStrideU, ByteBuffer dstV, int dstStrideV, int srcWidth, int srcHeight,
      int rotationMode) {
    checkNotNull(srcY, "srcY");
    checkNotNull(srcU, "srcU");
    checkNotNull(srcV, "srcV");
    checkNotNull(dstY, "dstY");
    checkNotNull(dstU, "dstU");
    checkNotNull(dstV, "dstV");
    nativeI420Rotate(srcY, srcStrideY, srcU, srcStrideU, srcV, srcStrideV, dstY, dstStrideY, dstU,
        dstStrideU, dstV, dstStrideV, srcWidth, srcHeight, rotationMode);
  }

  private static <T> T checkNotNull(T obj, String description) {
    if (obj == null) {
      throw new NullPointerException(description + " should not be null");
    }
    return obj;
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
