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
import java.util.concurrent.TimeUnit;

/**
 * An encoded frame from a video stream. Used as an input for decoders and as an output for
 * encoders.
 */
public class EncodedImage implements RefCounted {
  // Must be kept in sync with common_types.h FrameType.
  public enum FrameType {
    EmptyFrame(0),
    VideoFrameKey(3),
    VideoFrameDelta(4);

    private final int nativeIndex;

    private FrameType(int nativeIndex) {
      this.nativeIndex = nativeIndex;
    }

    public int getNative() {
      return nativeIndex;
    }

    @CalledByNative("FrameType")
    static FrameType fromNativeIndex(int nativeIndex) {
      for (FrameType type : FrameType.values()) {
        if (type.getNative() == nativeIndex) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown native frame type: " + nativeIndex);
    }
  }

  private final RefCountDelegate refCountDelegate;
  public final ByteBuffer buffer;
  public final int encodedWidth;
  public final int encodedHeight;
  public final long captureTimeMs; // Deprecated
  public final long captureTimeNs;
  public final FrameType frameType;
  public final int rotation;
  public final @Nullable Integer qp;

  // TODO(bugs.webrtc.org/9378): Use retain and release from jni code.
  @Override
  public void retain() {
    refCountDelegate.retain();
  }

  @Override
  public void release() {
    refCountDelegate.release();
  }

  @CalledByNative
  private EncodedImage(ByteBuffer buffer, @Nullable Runnable releaseCallback, int encodedWidth,
      int encodedHeight, long captureTimeNs, FrameType frameType, int rotation,
      @Nullable Integer qp) {
    this.buffer = buffer;
    this.encodedWidth = encodedWidth;
    this.encodedHeight = encodedHeight;
    this.captureTimeMs = TimeUnit.NANOSECONDS.toMillis(captureTimeNs);
    this.captureTimeNs = captureTimeNs;
    this.frameType = frameType;
    this.rotation = rotation;
    this.qp = qp;
    this.refCountDelegate = new RefCountDelegate(releaseCallback);
  }

  @CalledByNative
  private ByteBuffer getBuffer() {
    return buffer;
  }

  @CalledByNative
  private int getEncodedWidth() {
    return encodedWidth;
  }

  @CalledByNative
  private int getEncodedHeight() {
    return encodedHeight;
  }

  @CalledByNative
  private long getCaptureTimeNs() {
    return captureTimeNs;
  }

  @CalledByNative
  private int getFrameType() {
    return frameType.getNative();
  }

  @CalledByNative
  private int getRotation() {
    return rotation;
  }

  @CalledByNative
  private @Nullable Integer getQp() {
    return qp;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ByteBuffer buffer;
    private @Nullable Runnable releaseCallback;
    private int encodedWidth;
    private int encodedHeight;
    private long captureTimeNs;
    private EncodedImage.FrameType frameType;
    private int rotation;
    private @Nullable Integer qp;

    private Builder() {}

    public Builder setBuffer(ByteBuffer buffer, @Nullable Runnable releaseCallback) {
      this.buffer = buffer;
      this.releaseCallback = releaseCallback;
      return this;
    }

    public Builder setEncodedWidth(int encodedWidth) {
      this.encodedWidth = encodedWidth;
      return this;
    }

    public Builder setEncodedHeight(int encodedHeight) {
      this.encodedHeight = encodedHeight;
      return this;
    }

    @Deprecated
    public Builder setCaptureTimeMs(long captureTimeMs) {
      this.captureTimeNs = TimeUnit.MILLISECONDS.toNanos(captureTimeMs);
      return this;
    }

    public Builder setCaptureTimeNs(long captureTimeNs) {
      this.captureTimeNs = captureTimeNs;
      return this;
    }

    public Builder setFrameType(EncodedImage.FrameType frameType) {
      this.frameType = frameType;
      return this;
    }

    public Builder setRotation(int rotation) {
      this.rotation = rotation;
      return this;
    }

    public Builder setQp(@Nullable Integer qp) {
      this.qp = qp;
      return this;
    }

    public EncodedImage createEncodedImage() {
      return new EncodedImage(buffer, releaseCallback, encodedWidth, encodedHeight, captureTimeNs,
          frameType, rotation, qp);
    }
  }
}
