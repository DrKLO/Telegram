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
import java.util.Arrays;
import java.util.List;

public class SoftwareVideoEncoderFactory implements VideoEncoderFactory {
  private static final String TAG = "SoftwareVideoEncoderFactory";

  private final long nativeFactory;

  public SoftwareVideoEncoderFactory() {
    this.nativeFactory = nativeCreateFactory();
  }

  @Nullable
  @Override
  public VideoEncoder createEncoder(VideoCodecInfo info) {
    long nativeEncoder = nativeCreateEncoder(nativeFactory, info);
    if (nativeEncoder == 0) {
      Logging.w(TAG, "Trying to create encoder for unsupported format. " + info);
      return null;
    }

    return new WrappedNativeVideoEncoder() {
      @Override
      public long createNativeVideoEncoder() {
        return nativeEncoder;
      }

      @Override
      public boolean isHardwareEncoder() {
        return false;
      }
    };
  }

  @Override
  public VideoCodecInfo[] getSupportedCodecs() {
    return nativeGetSupportedCodecs(nativeFactory).toArray(new VideoCodecInfo[0]);
  }

  private static native long nativeCreateFactory();

  private static native long nativeCreateEncoder(long factory, VideoCodecInfo videoCodecInfo);

  private static native List<VideoCodecInfo> nativeGetSupportedCodecs(long factory);
}
