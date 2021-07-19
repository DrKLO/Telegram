/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.OutputBuffer;
import java.nio.ByteBuffer;

/** Video decoder output buffer containing video frame data. */
public class VideoDecoderOutputBuffer extends OutputBuffer {

  /** Buffer owner. */
  public interface Owner {

    /**
     * Releases the buffer.
     *
     * @param outputBuffer Output buffer.
     */
    void releaseOutputBuffer(VideoDecoderOutputBuffer outputBuffer);
  }

  // LINT.IfChange
  public static final int COLORSPACE_UNKNOWN = 0;
  public static final int COLORSPACE_BT601 = 1;
  public static final int COLORSPACE_BT709 = 2;
  public static final int COLORSPACE_BT2020 = 3;
  // LINT.ThenChange(
  //     ../../../../../../../../../../extensions/av1/src/main/jni/gav1_jni.cc,
  //     ../../../../../../../../../../extensions/vp9/src/main/jni/vpx_jni.cc
  // )

  /** Decoder private data. */
  public int decoderPrivate;

  /** Output mode. */
  @C.VideoOutputMode public int mode;
  /** RGB buffer for RGB mode. */
  @Nullable public ByteBuffer data;

  public int width;
  public int height;
  @Nullable public ColorInfo colorInfo;

  /** YUV planes for YUV mode. */
  @Nullable public ByteBuffer[] yuvPlanes;

  @Nullable public int[] yuvStrides;
  public int colorspace;

  /**
   * Supplemental data related to the output frame, if {@link #hasSupplementalData()} returns true.
   * If present, the buffer is populated with supplemental data from position 0 to its limit.
   */
  @Nullable public ByteBuffer supplementalData;

  private final Owner owner;

  /**
   * Creates VideoDecoderOutputBuffer.
   *
   * @param owner Buffer owner.
   */
  public VideoDecoderOutputBuffer(Owner owner) {
    this.owner = owner;
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

  /**
   * Initializes the buffer.
   *
   * @param timeUs The presentation timestamp for the buffer, in microseconds.
   * @param mode The output mode. One of {@link C#VIDEO_OUTPUT_MODE_NONE}, {@link
   *     C#VIDEO_OUTPUT_MODE_YUV} and {@link C#VIDEO_OUTPUT_MODE_SURFACE_YUV}.
   * @param supplementalData Supplemental data associated with the frame, or {@code null} if not
   *     present. It is safe to reuse the provided buffer after this method returns.
   */
  public void init(
      long timeUs, @C.VideoOutputMode int mode, @Nullable ByteBuffer supplementalData) {
    this.timeUs = timeUs;
    this.mode = mode;
    if (supplementalData != null && supplementalData.hasRemaining()) {
      addFlag(C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA);
      int size = supplementalData.limit();
      if (this.supplementalData == null || this.supplementalData.capacity() < size) {
        this.supplementalData = ByteBuffer.allocate(size);
      } else {
        this.supplementalData.clear();
      }
      this.supplementalData.put(supplementalData);
      this.supplementalData.flip();
      supplementalData.position(0);
    } else {
      this.supplementalData = null;
    }
  }

  /**
   * Resizes the buffer based on the given stride. Called via JNI after decoding completes.
   *
   * @return Whether the buffer was resized successfully.
   */
  public boolean initForYuvFrame(int width, int height, int yStride, int uvStride, int colorspace) {
    this.width = width;
    this.height = height;
    this.colorspace = colorspace;
    int uvHeight = (int) (((long) height + 1) / 2);
    if (!isSafeToMultiply(yStride, height) || !isSafeToMultiply(uvStride, uvHeight)) {
      return false;
    }
    int yLength = yStride * height;
    int uvLength = uvStride * uvHeight;
    int minimumYuvSize = yLength + (uvLength * 2);
    if (!isSafeToMultiply(uvLength, 2) || minimumYuvSize < yLength) {
      return false;
    }

    // Initialize data.
    if (data == null || data.capacity() < minimumYuvSize) {
      data = ByteBuffer.allocateDirect(minimumYuvSize);
    } else {
      data.position(0);
      data.limit(minimumYuvSize);
    }

    if (yuvPlanes == null) {
      yuvPlanes = new ByteBuffer[3];
    }

    ByteBuffer data = this.data;
    ByteBuffer[] yuvPlanes = this.yuvPlanes;

    // Rewrapping has to be done on every frame since the stride might have changed.
    yuvPlanes[0] = data.slice();
    yuvPlanes[0].limit(yLength);
    data.position(yLength);
    yuvPlanes[1] = data.slice();
    yuvPlanes[1].limit(uvLength);
    data.position(yLength + uvLength);
    yuvPlanes[2] = data.slice();
    yuvPlanes[2].limit(uvLength);
    if (yuvStrides == null) {
      yuvStrides = new int[3];
    }
    yuvStrides[0] = yStride;
    yuvStrides[1] = uvStride;
    yuvStrides[2] = uvStride;
    return true;
  }

  /**
   * Configures the buffer for the given frame dimensions when passing actual frame data via {@link
   * #decoderPrivate}. Called via JNI after decoding completes.
   */
  public void initForPrivateFrame(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /**
   * Ensures that the result of multiplying individual numbers can fit into the size limit of an
   * integer.
   */
  private static boolean isSafeToMultiply(int a, int b) {
    return a >= 0 && b >= 0 && !(b > 0 && a >= Integer.MAX_VALUE / b);
  }
}
